package simple.repo.deb;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import simple.repo.keys.KeysUtils;
import simple.repo.keys.SupportedKeyGenerationProfile;
import simple.repo.model.Arch;
import simple.repo.model.FileIntegrityWithContent;
import simple.repo.model.PackageConfig;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DebRepoBuilderITest {
    DebPackageBuilder packageBuilder = new DebPackageBuilder();
    DebRepoBuilder repoBuilder = new DebRepoBuilder();

    @SneakyThrows
    @Test
    void installsDependenciesAndReplacesConflictingPackageFromFileRepository() {
        var common = executablePackage("common-package", """
                #!/bin/sh
                echo '0.0.1'
                """, "/usr/bin/common-package", "", "");
        var reporter = executablePackage("jq-version-reporter", """
                #!/bin/sh
                echo 'welcome to jq-version-reporter'
                common-package
                """, "/usr/bin/jqr", "common-package", "jq-vr2");
        var alternate = executablePackage("jq-vr2", """
                #!/bin/sh
                echo 'welcome to jq-vr2'
                common-package
                """, "/usr/bin/jqr", "common-package", "jq-version-reporter");

        var packageFiles = List.of(
                packageBuilder.buildPackage(common),
                packageBuilder.buildPackage(reporter),
                packageBuilder.buildPackage(alternate));
        var packageMetas = List.of(
                repoBuilder.packageMeta(common, packageFiles.get(0)),
                repoBuilder.packageMeta(reporter, packageFiles.get(1)),
                repoBuilder.packageMeta(alternate, packageFiles.get(2)));
        var builder = repoBuilder.repoBuilder(new DebRepoBuilder.RepoConfig(), Instant.ofEpochMilli(1751384953000L));
        builder.buildCodename("bullseye").addPackage(packageMetas.get(0)).addPackage(packageMetas.get(1)).addPackage(packageMetas.get(2)).build()
                .buildCodename("bookworm").addPackage(packageMetas.get(0)).addPackage(packageMetas.get(1)).addPackage(packageMetas.get(2)).build();
        var repo = builder.build();
        var repoFiles = repoBuilder.buildRepo(repo);

        try (var container = repositoryContainer("debian:13-slim")) {
            container.withCopyToContainer(
                    Transferable.of("deb [trusted=yes] file:/tmp/repo bullseye main\n"),
                    "/etc/apt/sources.list");
            copyRepository(container, repoFiles, packageFiles, List.of("bullseye", "bookworm"));
            container.start();
            assertSuccess(exec(container, "rm", "-rf", "/etc/apt/sources.list.d"));
            assertSuccess(exec(container, "apt-get", "update"));
            assertSuccess(exec(container, "apt-get", "install", "-y", "jq-version-reporter"));
            assertThat(exec(container, "jqr").getStdout(), containsString("jq-version-reporter"));
            assertSuccess(exec(container, "apt-get", "install", "-y", "jq-vr2"));
            assertThat(exec(container, "jqr").getStdout(), containsString("jq-vr2"));
        }
    }

    @SneakyThrows
    @Test
    void signedRepositoryIsAcceptedBySupportedDistributions() {
        var config = new PackageConfig()
                .setMeta(new PackageConfig.PackageMeta()
                        .setArch(Arch.current()).setVersion("0.0.1").setName("hello"))
                .setControl(new PackageConfig.ControlExtras()
                        .setMaintainer("maintainer").setDescription("description"))
                .setFiles(new PackageConfig.FileSpec().setDataFiles(List.of()).setControlFiles(List.of()));
        var packageFile = packageBuilder.buildPackage(config);
        var packageMeta = repoBuilder.packageMeta(config, packageFile);
        var key = KeysUtils.genKeyPairKeyring(
                "user", "user@host.tld", SupportedKeyGenerationProfile.RSA4096);
        var privateKey = key.getPrivateKey().getBytes(StandardCharsets.UTF_8);
        var publicKey = key.getPublicKey().getBytes(StandardCharsets.UTF_8);
        var now = Instant.ofEpochMilli(1751437482822L);

        for (var distro : Distro.SUPPORTED_DISTROS) {
            var repo = repoBuilder.repoBuilder(new DebRepoBuilder.RepoConfig(), now)
                    .buildCodename(distro.codename())
                    .addPackage(packageMeta)
                    .build()
                    .build();
            var repoFiles = repoBuilder.buildRepo(repo);
            var signedFiles = repoBuilder.signRepo(repoFiles, privateKey, publicKey, now);

            try (var container = repositoryContainer(distro.image())) {
                copyRepository(container, repoFiles, List.of(packageFile), List.of(distro.codename()));
                for (var signed : signedFiles.entrySet()) {
                    container.withCopyToContainer(
                            Transferable.of(signed.getValue().getContent()),
                            "/tmp/repo/dists/" + signed.getKey());
                }
                container.withCopyToContainer(Transferable.of(publicKey), "/tmp/repository.asc");
                container.start();
                assertSuccess(exec(container, "rm", "-rf", "/etc/apt/sources.list", "/etc/apt/sources.list.d"));
                assertSuccess(exec(container, "sh", "-c", "echo 'deb [signed-by=/tmp/repository.asc] file:/tmp/repo "
                        + distro.codename() + " main' > /etc/apt/sources.list"));
                assertSuccess(exec(container, "apt-get", "update"));
                assertSuccess(exec(container, "apt-get", "install", "-y", "hello"));
            }
        }
    }

    private PackageConfig executablePackage(String name, String content, String path, String depends, String conflicts) {
        return new PackageConfig()
                .setMeta(new PackageConfig.PackageMeta()
                        .setName(name).setVersion("0.0.1").setArch(Arch.current()))
                .setControl(new PackageConfig.ControlExtras()
                        .setMaintainer("maintainer").setDescription("description")
                        .setDepends(depends).setConflicts(conflicts))
                .setFiles(new PackageConfig.FileSpec().setControlFiles(List.of()).setDataFiles(List.of(
                        new PackageConfig.PkgFileSpec.TextPkgFileSpec()
                                .setContent(content).setPath(path).setMode(Integer.parseInt("755", 8)))));
    }

    @SuppressWarnings("resource")
    private GenericContainer<?> repositoryContainer(String image) {
        return new GenericContainer<>(image)
                .withEnv("DEBIAN_FRONTEND", "noninteractive")
                .withCreateContainerCmdModifier(c -> c.withEntrypoint("tail", "-f", "/dev/null"));
    }

    private void copyRepository(GenericContainer<?> container,
                                java.util.Map<String, FileIntegrityWithContent> repoFiles,
                                List<FileIntegrityWithContent> packages,
                                List<String> codenames) {
        for (var file : repoFiles.entrySet()) {
            container.withCopyToContainer(
                    Transferable.of(file.getValue().getContent()),
                    "/tmp/repo/dists/" + file.getKey());
        }
        for (var codename : codenames) {
            for (var packageFile : packages) {
                container.withCopyToContainer(
                        Transferable.of(packageFile.getContent()),
                        "/tmp/repo/pool/" + codename + "/" + packageFile.getFileIntegrity().getPath());
            }
        }
    }

    @SneakyThrows
    private Container.ExecResult exec(GenericContainer<?> container, String... command) {
        return container.execInContainer(command);
    }

    private void assertSuccess(Container.ExecResult result) {
        assertEquals(0, result.getExitCode(), () -> result.getStdout() + "\n" + result.getStderr());
    }

    private record Distro(String image, String codename) {
        static final List<Distro> SUPPORTED_DISTROS = List.of(
                new Distro("debian:12-slim", "bullseye"),
                new Distro("debian:13-slim", "trixie"),
                new Distro("ubuntu:24.04", "noble"),
                new Distro("ubuntu:26.04", "resolute"));
    }
}
