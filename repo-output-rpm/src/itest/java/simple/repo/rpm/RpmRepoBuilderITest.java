package simple.repo.rpm;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import simple.repo.keys.KeysUtils;
import simple.repo.keys.SupportedKeyGenerationProfile;
import simple.repo.model.Arch;
import simple.repo.model.FileIntegrityWithContent;
import simple.repo.model.IndexFile;
import simple.repo.model.PackageConfig;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RpmRepoBuilderITest {
    private static final YAMLMapper YAML_MAPPER = YAMLMapper.builder().findAndAddModules().build();

    @SneakyThrows
    @Test
    void installsPackageByNameFromSignedFileRepository() {
        var yaml = new String(getClass().getResourceAsStream("example-rpm-repository.yaml").readAllBytes(),
                StandardCharsets.UTF_8).replace("__CURRENT_ARCH__", Arch.current().name());
        var packageConfig = YAML_MAPPER.readValue(yaml, PackageConfig.class);

        var packageBuilder = new RpmPackageBuilder();
        var installedSizeBytes = packageBuilder.installedSizeBytes(packageConfig);
        var packageFile = packageBuilder.buildPackage(packageConfig);
        var indexFile = new IndexFile().setFileIntegrity(packageFile.getFileIntegrity()).setPackageConfig(packageConfig);

        var builder = new RpmRepoBuilder();
        var now = Instant.ofEpochSecond(1778861567L);
        var repo = builder.repoBuilder(new RpmRepoBuilder.RepoConfig(), now)
                .buildVersion("10")
                .addPackage(new IndexFile().setPackageConfig(packageConfig).setFileIntegrity(packageFile.getFileIntegrity()))
                .build()
                .build();
        var repoFiles = builder.buildRepo(repo);
        var key = KeysUtils.genKeyPairKeyring(
                "simple repo", "repo@example.invalid", SupportedKeyGenerationProfile.RSA4096);
        var privateKey = key.getPrivateKey().getBytes(StandardCharsets.UTF_8);
        var publicKey = key.getPublicKey().getBytes(StandardCharsets.UTF_8);
        var signedFiles = builder.signRepo(repoFiles, privateKey, publicKey, now);
        var rpmArch = new RpmPackageBuilder().archName(Arch.current());
        var repositoryRoot = "/tmp/repo/10/" + rpmArch;
        var repositoryConfig = """
                [local]
                name=local signed file repository
                baseurl=file://%s
                enabled=1
                gpgcheck=0
                repo_gpgcheck=1
                gpgkey=file:///tmp/repository.asc
                metadata_expire=0
                """.formatted(repositoryRoot);

        assertRepositoryFileSet(repoFiles, "10/" + rpmArch + "/");

        try (var container = repositoryContainer()) {
            copyFiles(container, repoFiles, "/tmp/repo/");
            copyFiles(container, signedFiles, "/tmp/repo/");
            container.withCopyToContainer(Transferable.of(packageFile.getContent()),
                    repositoryRoot + "/pool/" + packageFile.getFileIntegrity().getPath());
            container.withCopyToContainer(Transferable.of(publicKey), "/tmp/repository.asc");
            container.withCopyToContainer(Transferable.of(repositoryConfig), "/tmp/local.repo");
            container.start();

            assertSuccess(container.execInContainer(
                    "find", "/etc/yum.repos.d", "-type", "f", "-name", "*.repo", "-delete"));
            assertSuccess(container.execInContainer("cp", "/tmp/local.repo", "/etc/yum.repos.d/local.repo"));
            assertSuccess(container.execInContainer("dnf", "clean", "all"));
            assertSuccess(container.execInContainer("dnf", "install", "-y", packageConfig.getMeta().getName()));
            assertEquals("hello from the rpm repository\n",
                    container.execInContainer("/opt/example-rpm-repository/bin/hello").getStdout());
        }
    }

    private void assertRepositoryFileSet(Map<String, FileIntegrityWithContent> repoFiles, String prefix) {
        var paths = repoFiles.keySet();
        org.junit.jupiter.api.Assertions.assertTrue(paths.contains(prefix + "repodata/repomd.xml"));
        for (var suffix : java.util.List.of(
                "primary.xml.gz", "filelists.xml.gz", "other.xml.gz",
                "primary.sqlite.bz2", "filelists.sqlite.bz2", "other.sqlite.bz2")) {
            org.junit.jupiter.api.Assertions.assertTrue(paths.stream()
                    .anyMatch(path -> path.startsWith(prefix + "repodata/") && path.endsWith("-" + suffix)),
                    () -> "missing " + suffix + " in " + paths);
        }
    }

    @SuppressWarnings("resource")
    private GenericContainer<?> repositoryContainer() {
        return new GenericContainer<>("rockylinux/rockylinux:10")
                .withCreateContainerCmdModifier(command -> command.withEntrypoint("tail", "-f", "/dev/null"));
    }

    private void copyFiles(GenericContainer<?> container,
                           Map<String, FileIntegrityWithContent> files,
                           String root) {
        for (var file : files.entrySet()) {
            container.withCopyToContainer(Transferable.of(file.getValue().getContent()), root + file.getKey());
        }
    }

    private void assertSuccess(Container.ExecResult result) {
        assertEquals(0, result.getExitCode(), () -> result.getStdout() + "\n" + result.getStderr());
    }
}
