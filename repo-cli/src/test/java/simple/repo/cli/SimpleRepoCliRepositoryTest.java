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
import simple.repo.winget.RepoOutputWinget;
import simple.repo.winget.WingetCertificateCli;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleRepoCliRepositoryTest {
    private final SimpleRepoCli cli = new SimpleRepoCli();

    @Test
    void oneCommandCreatesSignedFileBackedWingetRepository(@TempDir Path tempDir) throws Exception {
        var repositoryDir = tempDir.resolve("winget");
        var config = tempDir.resolve("config.yaml");
        var pfx = tempDir.resolve("signing.pfx");
        var cer = tempDir.resolve("signing.cer");
        Files.writeString(config, """
                meta:
                  name: example
                  arch: amd64
                  version: 0.0.1
                file:
                  control: []
                  data:
                  - type: text
                    content: write-output "hello"
                    mode: 0x755
                    path: hello.pwsh
                """);
        WingetCertificateCli.generate("CN=Simple Repo", pfx, cer, null, 0xE42, 0x800, false);

        var exit = SimpleRepoApplication.commandLine().execute(
                "repo", "-t", "winget",
                "--repo", repositoryDir.toUri().toString(),
                "--published-base", "https://mydomain.com/winget/",
                "-P", cer.toString(), "-S", pfx.toString(),
                "add", "--init=init", "-c", config.toString());

        assertTrue(exit == 0, "repo add exit code");
        assertTrue(Files.isRegularFile(repositoryDir.resolve("packages/example_0.0.1_x64.zip")));
        assertTrue(Files.isRegularFile(repositoryDir.resolve("packages/example_0.0.1_x64.zip.spr4j-index.json")));
        assertTrue(Files.isRegularFile(repositoryDir.resolve(".simple-repo/winget-catalog.json")));
        assertTrue(Files.isRegularFile(repositoryDir.resolve("simple-repo-winget.cer")));
        assertTrue(Files.walk(repositoryDir.resolve("manifests")).anyMatch(Files::isRegularFile));
        var source = Files.readAllBytes(repositoryDir.resolve("source.msix"));
        assertTrue(zipEntries(source).contains("AppxSignature.p7x"));
        assertTrue(appxSignature(source).getSignerInfos().getSigners().stream()
                .allMatch(signer -> signer.getUnsignedAttributes() == null),
                "passwordless self-signed sources must not acquire a network timestamp chain");
        var manifest = Files.walk(repositoryDir.resolve("manifests"))
                .filter(Files::isRegularFile).findFirst().orElseThrow();
        var manifestText = Files.readString(manifest);
        assertTrue(manifestText.contains("https://mydomain.com/winget/packages/example_0.0.1_x64.zip"));
        assertTrue(manifestText.contains("RelativeFilePath: 'hello.pwsh'"));
    }

    @Test
    void buildsWingetPackageAndStaticSourceThroughGenericWorkflow(@TempDir Path tempDir) throws Exception {
        var previousBaseUrl = System.getProperty(RepoOutputWinget.BASE_URL_PROPERTY);
        System.setProperty(RepoOutputWinget.BASE_URL_PROPERTY, "https://packages.example.invalid/winget/");
        try {
            var io = new InMemoryRepoIo();
            var repository = cli.loadRepo("winget");
            var config = config("Example.CliPortable", "1.0.0", Arch.amd64);
            config.getFiles().setDataFiles(List.of(new PackageConfig.PkgFileSpec.BinaryPkgFileSpec()
                    .setContent("small PE fixture".getBytes(StandardCharsets.UTF_8))
                    .setPath("bin/example-cli-portable.exe").setMode(0x755)));
            var configFile = tempDir.resolve("portable.yaml");
            Files.writeString(configFile, cli.yamlMapper.writeValueAsString(config));

            cli.addPackageConfigs(io, repository, List.of(configFile), List.of("winget"), null,
                    RepositoryInitialization.init, null, null);

            assertTrue(io.getContents().containsKey("source.msix"));
            assertTrue(io.getContents().containsKey(".simple-repo/winget-catalog.json"));
            assertTrue(io.getContents().keySet().stream().anyMatch(path -> path.startsWith("manifests/")));
            assertTrue(io.getContents().keySet().stream().anyMatch(path -> path.matches(
                    "packages/Example\\.CliPortable_1\\.0\\.0_x64\\.zip")));
        } finally {
            if (previousBaseUrl == null) System.clearProperty(RepoOutputWinget.BASE_URL_PROPERTY);
            else System.setProperty(RepoOutputWinget.BASE_URL_PROPERTY, previousBaseUrl);
        }
    }

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

    private List<String> zipEntries(byte[] content) throws Exception {
        var entries = new java.util.ArrayList<String>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) entries.add(entry.getName());
        }
        return entries;
    }

    private org.bouncycastle.cms.CMSSignedData appxSignature(byte[] content) throws Exception {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals("AppxSignature.p7x")) {
                    var signature = zip.readAllBytes();
                    return new org.bouncycastle.cms.CMSSignedData(
                            java.util.Arrays.copyOfRange(signature, 0x4, signature.length));
                }
            }
        }
        throw new AssertionError("missing AppxSignature.p7x");
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
