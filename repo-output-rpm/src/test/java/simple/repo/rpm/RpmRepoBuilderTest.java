package simple.repo.rpm;

import org.junit.jupiter.api.Test;
import simple.repo.model.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RpmRepoBuilderTest {
    @Test
    void buildsRepositoryFromIndexMetadataWithoutPackageOrSourceContent() {
        var config = new PackageConfig()
                .setMeta(new PackageConfig.PackageMeta()
                        .setName("index-only-package")
                        .setVersion("0.0.1")
                        .setReleaseVersion("1")
                        .setArch(Arch.amd64))
                .setControl(new PackageConfig.ControlExtras()
                        .setMaintainer("maintainer")
                        .setDescription("built from index metadata"))
                .setFiles(new PackageConfig.FileSpec());
        new RpmPackageBuilder().buildPackage(config);
        var packageIntegrity = new FileIntegrity()
                .setPath("index-only-package-0.0.1-1.x86_64.rpm")
                .setSize(0x1234)
                .setSha256("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        var packageMeta = new IndexFile()
                .setPackageConfig(config)
                .setFileIntegrity(packageIntegrity);
        var builder = new RpmRepoBuilder();

        var repo = builder.repoBuilder(new RpmRepoBuilder.RepoConfig(), Instant.EPOCH)
                .buildVersion("10")
                .addPackage(packageMeta)
                .build()
                .build();
        var files = builder.buildRepo(repo);

        assertTrue(files.containsKey("10/x86_64/repodata/repomd.xml"));
        assertTrue(files.keySet().stream().anyMatch(path -> path.endsWith("-primary.xml.gz")));
        assertTrue(files.keySet().stream().anyMatch(path -> path.endsWith("-filelists.sqlite.bz2")));
    }

    @Test
    void buildsInstallableTwoVersionByTwoArchitectureRepository() throws Exception {
        var packageBuilder = new RpmPackageBuilder();
        var repoBuilder = new RpmRepoBuilder();
        var packages = new LinkedHashMap<Coordinate, FileIntegrityWithContent>();
        var indexes = new LinkedHashMap<Coordinate, IndexFile>();
        for (var version : List.of("9", "10")) {
            for (var arch : List.of(Arch.amd64, Arch.arm64)) {
                var coordinate = new Coordinate(version, arch);
                var config = executablePackage("matrix-hello", version, arch);
                var packageFile = packageBuilder.buildPackage(config);
                packages.put(coordinate, packageFile);
                indexes.put(coordinate, new IndexFile()
                        .setPackageConfig(config).setFileIntegrity(packageFile.getFileIntegrity()));
            }
        }

        var builder = repoBuilder.repoBuilder(new RpmRepoBuilder.RepoConfig(), Instant.EPOCH);
        for (var version : List.of("9", "10")) {
            builder.buildVersion(version)
                    .addPackage(indexes.get(new Coordinate(version, Arch.amd64)))
                    .addPackage(indexes.get(new Coordinate(version, Arch.arm64)))
                    .build();
        }
        Map<String, FileIntegrityWithContent> published = repoBuilder.buildRepo(builder.build());
        for (var entry : packages.entrySet()) {
            var rpmArch = packageBuilder.archName(entry.getKey().arch());
            published.put(entry.getKey().version() + "/" + rpmArch + "/pool/"
                    + entry.getValue().getFileIntegrity().getPath(), entry.getValue());
        }

        assertThat(published.keySet().stream()
                        .filter(e -> e.contains("/repodata/"))
                        .map(e -> e.substring(0, e.indexOf("/repodata/")))
                        .distinct()
                        .toList(),
                containsInAnyOrder("9/x86_64", "9/aarch64", "10/x86_64", "10/aarch64"));

        for (var version : List.of("9", "10")) {
            for (var arch : List.of(Arch.amd64, Arch.arm64)) {
                var coordinate = new Coordinate(version, arch);
                var rpmArch = packageBuilder.archName(arch);
                var prefix = version + "/" + rpmArch + "/";
                var packageFile = packages.get(coordinate);
                assertRepositoryFiles(published, prefix);
                assertTrue(published.containsKey(prefix + "pool/" + packageFile.getFileIntegrity().getPath()));
                var primary = gunzip(findPrimaryXmlInPrefixDir(published, prefix));
                assertTrue(primary.contains("<name>matrix-hello</name>"));
                assertTrue(primary.contains("<arch>" + rpmArch + "</arch>"));
                assertTrue(primary.contains("href=\"pool/" + packageFile.getFileIntegrity().getPath() + "\""));
            }
        }
    }

    private PackageConfig executablePackage(String name, String version, Arch arch) {
        return new PackageConfig()
                .setMeta(new PackageConfig.PackageMeta()
                        .setName(name).setVersion("0.0.1").setReleaseVersion("1")
                        .setElVersion("el" + version).setArch(arch))
                .setControl(new PackageConfig.ControlExtras()
                        .setMaintainer("maintainer").setDescription("architecture matrix shell script"))
                .setFiles(new PackageConfig.FileSpec().setControlFiles(List.of()).setDataFiles(List.of(
                        new PackageConfig.PkgFileSpec.TextPkgFileSpec()
                                .setContent("#!/bin/sh\necho 'hello from matrix-hello'\n")
                                .setPath("/usr/bin/matrix-hello")
                                .setMode(0x755))));
    }

    private void assertRepositoryFiles(Map<String, FileIntegrityWithContent> files, String prefix) {
        var repoMdPath = prefix + "repodata/repomd.xml";
        assertTrue(files.containsKey(repoMdPath));
        var repoMd = new String(files.get(repoMdPath).getContent(), StandardCharsets.UTF_8);
        for (var suffix : List.of(
                "primary.xml.gz", "filelists.xml.gz", "other.xml.gz",
                "primary.sqlite.bz2", "filelists.sqlite.bz2", "other.sqlite.bz2")) {
            var artifactPath = files.keySet().stream()
                    .filter(path -> path.startsWith(prefix + "repodata/"))
                    .filter(path -> path.endsWith("-" + suffix))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("missing " + prefix + "repodata/*-" + suffix));
            assertTrue(repoMd.contains("href=\"" + artifactPath.substring(prefix.length()) + "\""));
        }
    }

    private FileIntegrityWithContent findPrimaryXmlInPrefixDir(Map<String, FileIntegrityWithContent> files, String prefix) {
        return files.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix + "repodata/"))
                .filter(entry -> entry.getKey().endsWith("-primary.xml.gz"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow();
    }

    private String gunzip(FileIntegrityWithContent file) throws Exception {
        try (var input = new GZIPInputStream(new ByteArrayInputStream(file.getContent()))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private record Coordinate(String version, Arch arch) {
    }
}
