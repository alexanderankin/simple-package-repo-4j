package simple.repo.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import simple.repo.deb.DebRepository;
import simple.repo.io.InMemoryRepoIo;
import simple.repo.model.Arch;
import simple.repo.model.FileIntegrity;
import simple.repo.model.IndexFile;
import simple.repo.model.PackageConfig;
import simple.repo.repository.Repository;
import simple.repo.repository.RepositoryInitialization;
import simple.repo.rpm.RpmRepository;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleRepoCliRepositoryTest {
    private final SimpleRepoCli cli = new SimpleRepoCli();

    @Test
    void scansDebSidecarsAndRetainsOnlyTheNewestPublishedVersion() throws Exception {
        var io = new InMemoryRepoIo();
        var repository = new DebRepository();
        uploadDebIndex(io, repository, "noble", "1.9.0");
        uploadDebIndex(io, repository, "noble", "1.10.0");

        cli.rebuildRepository(io, repository, List.of("noble"), 1, null, null);

        var packages = text(io, "dists/noble/main/binary-amd64/Packages");
        assertTrue(packages.contains("Version: 1.10.0"));
        assertFalse(packages.contains("Version: 1.9.0"));
        assertTrue(io.getContents().keySet().stream().anyMatch(path -> path.contains("1.9.0")),
                "retention must not delete package or sidecar objects");
    }

    @Test
    void addsAnExplicitIndexUsingPublishedMetadataWithoutScanningThePool() throws Exception {
        var io = new InMemoryRepoIo();
        var repository = new DebRepository();
        uploadDebIndex(io, repository, "noble", "1.0.0");
        cli.rebuildRepository(io, repository, List.of("noble"), null, null, null);
        var newIndex = uploadDebIndex(io, repository, "noble", "2.0.0");
        var noScanIo = new NoScanRepoIo().setSharedContents(io.getContents());

        cli.addRepositoryIndexes(noScanIo, repository, List.of(newIndex), null,
                RepositoryInitialization.allowed, null, null);

        var packages = text(noScanIo, "dists/noble/main/binary-amd64/Packages");
        assertTrue(packages.contains("Version: 1.0.0"));
        assertTrue(packages.contains("Version: 2.0.0"));
    }

    @Test
    void disabledInitializationRequiresExistingMetadata() throws Exception {
        var io = new InMemoryRepoIo();
        var repository = new DebRepository();
        var index = uploadDebIndex(io, repository, "noble", "1.0.0");

        assertThrows(IllegalStateException.class, () -> cli.addRepositoryIndexes(io, repository,
                List.of(index), null, RepositoryInitialization.disabled, null, null));
    }

    @Test
    void allowedInitializationCreatesOnlyAnAbsentRepository() throws Exception {
        var io = new InMemoryRepoIo();
        var repository = new DebRepository();
        var index = uploadDebIndex(io, repository, "noble", "1.0.0");

        cli.addRepositoryIndexes(io, repository, List.of(index), null,
                RepositoryInitialization.allowed, null, null);

        assertTrue(io.getContents().containsKey("dists/noble/Release"));
    }

    @Test
    void scansRpmSidecarsIntoEachVersionAndArchitecturePartition() throws Exception {
        var io = new InMemoryRepoIo();
        var repository = new RpmRepository();
        for (var target : List.of("9", "10")) {
            for (var arch : List.of(Arch.amd64, Arch.arm64)) {
                var config = config("matrix-hello", "1.0.0", arch);
                config.getMeta().setElVersion("el" + target);
                uploadIndex(io, repository, target, config);
            }
        }

        cli.rebuildRepository(io, repository, List.of("9", "10"), null, null, null);

        for (var prefix : List.of("9/x86_64", "9/aarch64", "10/x86_64", "10/aarch64")) {
            assertTrue(io.getContents().containsKey(prefix + "/repodata/repomd.xml"));
            var primaryPath = io.getContents().keySet().stream()
                    .filter(path -> path.startsWith(prefix + "/repodata/") && path.endsWith("-primary.xml.gz"))
                    .findFirst().orElseThrow();
            assertTrue(gunzip(io.getContents().get(primaryPath)).contains("<name>matrix-hello</name>"));
        }
    }

    @Test
    void buildsYamlConfigForEachRpmTargetWithoutMutatingTheOtherTarget(@TempDir Path tempDir) throws Exception {
        var io = new InMemoryRepoIo();
        var repository = new RpmRepository();
        var configFile = tempDir.resolve("package.yaml");
        Files.writeString(configFile, cli.yamlMapper.writeValueAsString(
                config("yaml-example", "1.0.0", Arch.amd64)));

        cli.addPackageConfigs(io, repository, Collections.singletonList(configFile), List.of("9", "10"), null,
                RepositoryInitialization.init, null, null);

        assertTrue(io.getContents().keySet().stream().anyMatch(path ->
                path.matches("9/x86_64/pool/yaml-example-1\\.0\\.0-1\\.el9\\.x86_64\\.rpm")));
        assertTrue(io.getContents().keySet().stream().anyMatch(path ->
                path.matches("10/x86_64/pool/yaml-example-1\\.0\\.0-1\\.el10\\.x86_64\\.rpm")));
        assertTrue(io.getContents().containsKey("9/x86_64/repodata/repomd.xml"));
        assertTrue(io.getContents().containsKey("10/x86_64/repodata/repomd.xml"));
    }

    private String uploadDebIndex(InMemoryRepoIo io, DebRepository repository, String target, String version)
            throws Exception {
        return uploadIndex(io, repository, target, config("versioned-example", version, Arch.amd64));
    }

    private String uploadIndex(InMemoryRepoIo io, Repository<?> repository, String target, PackageConfig config)
            throws Exception {
        var builder = repository.repoBuilder();
        var fileName = builder.getPackageBuilder().fileName(config);
        var index = new IndexFile().setPackageConfig(config)
                .setFileIntegrity(FileIntegrity.of(("package-" + fileName).getBytes(StandardCharsets.UTF_8), fileName));
        var path = builder.indexPath(target, config);
        io.uploadPackage(path, cli.jsonMapper.writeValueAsBytes(index));
        return path.joinParts();
    }

    private PackageConfig config(String name, String version, Arch arch) {
        return new PackageConfig()
                .setMeta(new PackageConfig.PackageMeta().setName(name).setVersion(version)
                        .setReleaseVersion("1").setArch(arch))
                .setControl(new PackageConfig.ControlExtras().setMaintainer("maintainer")
                        .setDescription("test package").setInstalledSize(0x10))
                .setFiles(new PackageConfig.FileSpec().setControlFiles(List.of()).setDataFiles(List.of()));
    }

    private String text(InMemoryRepoIo io, String path) {
        return new String(io.getContents().get(path), StandardCharsets.UTF_8);
    }

    private String gunzip(byte[] content) throws Exception {
        try (var input = new GZIPInputStream(new ByteArrayInputStream(content))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static class NoScanRepoIo extends InMemoryRepoIo {
        NoScanRepoIo setSharedContents(Map<String, byte[]> contents) {
            setContents(contents);
            return this;
        }

        @Override
        public Iterable<Repository.RepositoryPath> iterFiles(String path) {
            throw new AssertionError("incremental add must not scan storage");
        }
    }
}
