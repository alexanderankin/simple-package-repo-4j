package simple.repo.deb;

import org.junit.jupiter.api.Test;
import simple.repo.model.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebRepoBuilderTest {

    @Test
    void buildsReleaseFileWithAllDigestSections() {
        var builder = new DebRepoBuilder();
        var section = new DebRepoBuilder.Repo.CodenameSection("jammy")
                .setArches(Set.of("amd64"))
                .setComponents(Set.of("main"))
                .setDate(Instant.ofEpochMilli(1751384453000L))
                .setPackagesFiles(new HashMap<>(Map.of(
                        "main/binary-amd64/Packages", FileIntegrityWithContent.of("hello", "main/binary-amd64/Packages"),
                        "main/binary-amd64/Packages.gz", FileIntegrityWithContent.of("hello world", "main/binary-amd64/Packages.gz"))));

        assertEquals("""
                Origin: jammy
                Label: jammy
                Suite: jammy
                Codename: jammy
                Architectures: amd64
                Components: main
                Date: Tue, 01 Jul 2025 15:40:53 +0000
                Description: Repository for jammy
                MD5Sum:
                 536dbb9c4682fd0a3cb558e03b253b20              166 Release
                 5d41402abc4b2a76b9719d911017c592                5 main/binary-amd64/Packages
                 5eb63bbbe01eeed093cb22bb8f5acdc3               11 main/binary-amd64/Packages.gz
                SHA1:
                 bc055c5bfa4aac4639bb18aa8fa22b3cf6602d04              166 Release
                 aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d                5 main/binary-amd64/Packages
                 2aae6c35c94fcfb415dbe95f408b9ce91ee846ed               11 main/binary-amd64/Packages.gz
                SHA256:
                 7c5bf294d9fb8dc71ca1476e6e62f99a6f07f1067f12d69ab56ce097e2560f68              166 Release
                 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824                5 main/binary-amd64/Packages
                 b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9               11 main/binary-amd64/Packages.gz
                SHA512:
                 9c0ab06b86c4c4de885e690a1f006b5a2214fdf142b7eea21cf5483fcf16ec48f13b9790539d960da1a3fdf17f3ad53b9b74aa34c06f97d124a2d986f375bc60              166 Release
                 9b71d224bd62f3785d96d46ad3ea3d73319bfbc2890caadae2dff72519673ca72323c3d99ba5c11d7c7acc6e14b8c5da0c4663475c2e5c3adef46f73bcdec043                5 main/binary-amd64/Packages
                 309ecc489c12d6eb4cc40f50c902f2b4d0ed77ee511a7c7a9bcd3ca86d4cd86f989dd35bc5ff499670da34255b45b0cfd830e81f605dcf7dc5542e93ae9cd76f               11 main/binary-amd64/Packages.gz
                """, builder.buildRelease(new DebRepoBuilder.RepoConfig(), section));
    }

    @Test
    void buildsSinglePackageIndexAndRelease() {
        var builder = new DebRepoBuilder();
        var config = packageConfig("hello", Arch.amd64);
        var packageFile = new FileIntegrityWithContent()
                .setContent("hello".getBytes(StandardCharsets.UTF_8))
                .setFileIntegrity(FileIntegrity.of("hello".getBytes(StandardCharsets.UTF_8), "hello_0.0.1_amd64.deb").setSize(10));
        var repo = builder.repoBuilder(new DebRepoBuilder.RepoConfig(), Instant.ofEpochMilli(1751437482822L))
                .buildCodename("jammy")
                .addPackage(new IndexFile().setPackageConfig(config).setFileIntegrity(packageFile.getFileIntegrity()))
                .build()
                .build();
        var files = builder.buildRepo(repo);
        var textFiles = files.entrySet().stream()
                .filter(entry -> !entry.getKey().endsWith(".gz"))
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> new String(entry.getValue().getContent(), StandardCharsets.UTF_8)));

        assertEquals("""
                Package: hello
                Version: 0.0.1
                Architecture: amd64
                Maintainer: maintainer
                Filename: pool/jammy/hello_0.0.1_amd64.deb
                Size: 10
                MD5sum: 5d41402abc4b2a76b9719d911017c592
                SHA1: aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d
                SHA256: 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
                SHA512: 9b71d224bd62f3785d96d46ad3ea3d73319bfbc2890caadae2dff72519673ca72323c3d99ba5c11d7c7acc6e14b8c5da0c4663475c2e5c3adef46f73bcdec043
                Section: main
                Priority: optional
                Description: description
                """, textFiles.get("jammy/main/binary-amd64/Packages"));
        assertTrue(textFiles.get("jammy/Release").contains("Date: Wed, 02 Jul 2025 06:24:42 +0000"));
        assertTrue(textFiles.get("jammy/Release").contains("main/binary-amd64/Packages.gz"));
    }

    @Test
    void buildsInstallableTwoCodenameByTwoArchitectureRepository() throws Exception {
        var packageBuilder = new DebPackageBuilder();
        var repoBuilder = new DebRepoBuilder();
        var amd64Config = executablePackage("matrix-hello-amd64", Arch.amd64);
        var arm64Config = executablePackage("matrix-hello-arm64", Arch.arm64);
        var amd64Package = packageBuilder.buildPackage(amd64Config);
        var arm64Package = packageBuilder.buildPackage(arm64Config);
        var amd64Index = new IndexFile()
                .setPackageConfig(amd64Config).setFileIntegrity(amd64Package.getFileIntegrity());
        var arm64Index = new IndexFile()
                .setPackageConfig(arm64Config).setFileIntegrity(arm64Package.getFileIntegrity());

        var builder = repoBuilder.repoBuilder(new DebRepoBuilder.RepoConfig(), Instant.EPOCH);
        builder.buildCodename("noble").addPackage(amd64Index).addPackage(arm64Index).build()
                .buildCodename("resolute").addPackage(amd64Index).addPackage(arm64Index).build();
        var metadata = repoBuilder.buildRepo(builder.build());
        var published = new LinkedHashMap<String, FileIntegrityWithContent>();
        metadata.forEach((path, file) -> published.put("dists/" + path, file));
        for (var codename : List.of("noble", "resolute")) {
            published.put("pool/" + codename + "/" + amd64Package.getFileIntegrity().getPath(), amd64Package);
            published.put("pool/" + codename + "/" + arm64Package.getFileIntegrity().getPath(), arm64Package);
        }

        for (var codename : List.of("noble", "resolute")) {
            var release = text(published.get("dists/" + codename + "/Release"));
            assertTrue(release.contains("Architectures: amd64 arm64"));
            for (var architecture : List.of("amd64", "arm64")) {
                var indexPath = "dists/" + codename + "/main/binary-" + architecture + "/Packages.gz";
                assertTrue(published.containsKey(indexPath), () -> "missing " + indexPath);
                assertTrue(release.contains("main/binary-" + architecture + "/Packages.gz"));
                var packages = gunzip(published.get(indexPath));
                var packageFile = architecture.equals("amd64") ? amd64Package : arm64Package;
                assertTrue(packages.contains("Package: matrix-hello-" + architecture + "\n"));
                assertTrue(packages.contains("Architecture: " + architecture + "\n"));
                assertTrue(packages.contains("Filename: pool/" + codename + "/"
                        + packageFile.getFileIntegrity().getPath() + "\n"));
                assertTrue(published.containsKey("pool/" + codename + "/"
                        + packageFile.getFileIntegrity().getPath()));
            }
        }
    }

    private PackageConfig executablePackage(String name, Arch arch) {
        return packageConfig(name, arch)
                .setFiles(new PackageConfig.FileSpec().setControlFiles(List.of()).setDataFiles(List.of(
                        new PackageConfig.PkgFileSpec.TextPkgFileSpec()
                                .setContent("#!/bin/sh\necho 'hello from matrix-hello'\n")
                                .setPath("/usr/bin/matrix-hello")
                                .setMode(0x755))));
    }

    private String text(FileIntegrityWithContent file) {
        return new String(file.getContent(), StandardCharsets.UTF_8);
    }

    private String gunzip(FileIntegrityWithContent file) throws Exception {
        try (var input = new GZIPInputStream(new ByteArrayInputStream(file.getContent()))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    PackageConfig packageConfig(String name, Arch arch) {
        return new PackageConfig()
                .setMeta(new PackageConfig.PackageMeta().setArch(arch).setVersion("0.0.1").setName(name))
                .setControl(new PackageConfig.ControlExtras().setMaintainer("maintainer").setDescription("description"))
                .setFiles(new PackageConfig.FileSpec().setDataFiles(List.of()).setControlFiles(List.of()));
    }
}
