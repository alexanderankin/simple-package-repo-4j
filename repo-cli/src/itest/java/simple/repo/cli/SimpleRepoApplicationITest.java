package simple.repo.cli;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import simple.repo.deb.DebArch;
import simple.repo.model.Arch;
import simple.repo.rpm.RpmArch;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleRepoApplicationITest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void displaysTopLevelHelp() {
        assertSuccessWithOutputContains(CliResult.cli("-h"), "Usage: simple-repo");
    }

    private void assertSuccessWithOutputContains(CliResult result, String usage) {
        assertEquals(0, result.exitCode(), result::diagnostic);
        assertTrue(result.stdout().contains(usage), result::diagnostic);
    }

    private record CliResult(int exitCode, String stdout, String stderr, List<String> command) {
        static CliResult cli(String... arguments) {
            var stdout = new StringWriter();
            var stderr = new StringWriter();
            var commandLine = SimpleRepoApplication.commandLine()
                    .setOut(new PrintWriter(stdout, true))
                    .setErr(new PrintWriter(stderr, true));
            var exitCode = commandLine.execute(arguments);
            return new CliResult(exitCode, stdout.toString(), stderr.toString(), List.of(arguments));
        }

        String diagnostic() {
            return "command: " + String.join(" ", command) + "\nexit: " + exitCode
                    + "\nstdout:\n" + stdout + "\nstderr:\n" + stderr;
        }
    }

    @Nested
    class ReposITest {
        @Test
        void installsDebPackagesByNameFromSignedFileRepositoryBuiltByEveryWorkflow() throws Exception {
            var fixture = buildRepository("deb", "trixie", DebArch.fromArch(Arch.current()).name(), null);

            try (var container = repositoryContainer("debian:13-slim", true)) {
                copyRepository(container, fixture.root());
                container.start();

                assertExecSuccess(container.execInContainer("rm", "-f", "/etc/apt/sources.list"));
                assertExecSuccess(container.execInContainer("rm", "-rf", "/etc/apt/sources.list.d/"));
                assertExecSuccess(container.execInContainer("sh", "-c", "echo 'deb [signed-by=/tmp/repo/dists/"
                        + fixture.target() + "/repository.asc] file:/tmp/repo " + fixture.target()
                        + " main' > /etc/apt/sources.list"));
                assertExecSuccess(container.execInContainer("apt-get", "update"));
                var install = new ArrayList<>(List.of("apt-get", "install", "-y"));
                install.addAll(fixture.packageNames());
                assertExecSuccess(container.execInContainer(install.toArray(String[]::new)));
                assertInstalledExecutables(container, fixture.packageNames());
            }
        }

        @Test
        void installsRpmPackagesByNameFromSignedFileRepositoryBuiltByEveryWorkflow() throws Exception {
            var rpmArch = RpmArch.fromArch(Arch.current()).name();
            var fixture = buildRepository("rpm", "10", DebArch.fromArch(Arch.current()).name(), "el10");
            var partition = fixture.target() + "/" + rpmArch;
            var repoConfig = """
                    [local]
                    name=simple-repo CLI integration repository
                    baseurl=file:///tmp/repo/%s
                    enabled=1
                    gpgcheck=0
                    repo_gpgcheck=1
                    gpgkey=file:///tmp/repo/%s/repository.asc
                    metadata_expire=0
                    """.formatted(partition, partition);

            try (var container = repositoryContainer("rockylinux/rockylinux:10", false)) {
                copyRepository(container, fixture.root());
                container.withCopyToContainer(Transferable.of(repoConfig), "/tmp/local.repo");
                container.start();

                assertExecSuccess(container.execInContainer(
                        "find", "/etc/yum.repos.d", "-type", "f", "-name", "*.repo", "-delete"));
                assertExecSuccess(container.execInContainer("cp", "/tmp/local.repo", "/etc/yum.repos.d/local.repo"));
                assertExecSuccess(container.execInContainer("dnf", "clean", "all"));
                var install = new ArrayList<>(List.of("dnf", "install", "-y"));
                install.addAll(fixture.packageNames());
                assertExecSuccess(container.execInContainer(install.toArray(String[]::new)));
                assertInstalledExecutables(container, fixture.packageNames());
            }
        }

        private RepoFixture buildRepository(String type, String target, String arch, String elVersion) throws Exception {
            var root = temporaryDirectory.resolve(type + "-repository");
            Files.createDirectories(root);
            var keys = generateRepoKeys(type);
            var scan = writePackageYaml(type + "-scan", arch, elVersion);
            var configA = writePackageYaml(type + "-config-a", arch, null);
            var configB = writePackageYaml(type + "-config-b", arch, null);
            var index = writePackageYaml(type + "-index", arch, elVersion);
            var packageNames = List.of(type + "-scan", type + "-config-a", type + "-config-b", type + "-index");

            var scanCoordinate = placeBuiltPackage(type, target, root, scan);
            indexPackage(type, root, scanCoordinate);
            assertCliSuccess(CliResult.cli("repo", "-t", type, "-r", root.toUri().toString(),
                    "--target", target, "-P", keys.publicKey().toString(), "-S", keys.secretKey().toString(),
                    "scan"));

            assertCliSuccess(CliResult.cli("repo", "-t", type, "-r", root.toUri().toString(),
                    "--target", target, "-P", keys.publicKey().toString(), "-S", keys.secretKey().toString(),
                    "add", "-c", configA.toString(), configB.toString()));

            var indexPackageCoordinate = placeBuiltPackage(type, target, root, index);
            indexPackage(type, root, indexPackageCoordinate);
            assertCliSuccess(CliResult.cli("repo", "-t", type, "-r", root.toUri().toString(),
                    "-P", keys.publicKey().toString(), "-S", keys.secretKey().toString(),
                    "add", "-i",
                    indexPackageCoordinate + ".spr4j-index.json"));

            if (type.equals("deb")) {
                assertTrue(Files.isRegularFile(root.resolve("dists/" + target + "/InRelease")));
                assertTrue(Files.isRegularFile(root.resolve("dists/" + target + "/Release.gpg")));
                assertTrue(Files.isRegularFile(root.resolve("dists/" + target + "/repository.asc")));
            } else {
                var partition = target + "/" + RpmArch.fromArch(Arch.current()).name();
                assertTrue(Files.isRegularFile(root.resolve(partition + "/repodata/repomd.xml")));
                assertTrue(Files.isRegularFile(root.resolve(partition + "/repodata/repomd.xml.asc")));
                assertTrue(Files.isRegularFile(root.resolve(partition + "/repository.asc")));
            }
            return new RepoFixture(root, target, packageNames);
        }

        private Path writePackageYaml(String name, String arch, String elVersion) throws Exception {
            var yaml = """
                    meta:
                      arch: %s
                      name: %s
                      version: 1.0.0
                      releaseVersion: "1"
                    %scontrol:
                      maintainer: simple-repo integration test
                      description: package built by the %s CLI workflow
                    files:
                      controlFiles: []
                      dataFiles:
                        - type: text
                          path: /opt/simple-repo-cli/%s
                          mode: 0x755
                          content: |
                            #!/bin/sh
                            echo '%s'
                    """.formatted(arch, name,
                    elVersion == null ? "" : "  elVersion: " + elVersion + "\n",
                    name, name, name);
            var path = temporaryDirectory.resolve(name + ".yaml");
            Files.writeString(path, yaml);
            return path;
        }

        private String placeBuiltPackage(String type, String target, Path repository, Path config) throws Exception {
            var output = temporaryDirectory.resolve(config.getFileName() + "-output");
            Files.createDirectories(output);
            assertCliSuccess(CliResult.cli("package", "-t", type, "build",
                    "-c", config.toString(), "-o", output.toString()));
            Path packageFile;
            try (var files = Files.list(output)) {
                packageFile = files.findFirst().orElseThrow();
            }
            Path relativeDirectory;
            relativeDirectory = type.equals("deb") ? Path.of("pool", target) : Path.of(target, RpmArch.fromArch(Arch.current()).name(), "pool");
            var destinationDirectory = repository.resolve(relativeDirectory);
            Files.createDirectories(destinationDirectory);
            Files.copy(packageFile, destinationDirectory.resolve(packageFile.getFileName()),
                    StandardCopyOption.REPLACE_EXISTING);
            return relativeDirectory.resolve(packageFile.getFileName()).toString().replace('\\', '/');
        }

        private void indexPackage(String type, Path repository, String packageCoordinate) {
            assertCliSuccess(CliResult.cli("repo", "-t", type, "-r", repository.toUri().toString(),
                    "index", packageCoordinate));
        }

        private RepoKeys generateRepoKeys(String prefix) {
            var publicKey = temporaryDirectory.resolve(prefix + "-repository-public.asc");
            var secretKey = temporaryDirectory.resolve(prefix + "-repository-secret.asc");
            assertCliSuccess(CliResult.cli("keys", "gen", "-p", "CURVE25519",
                    "-P", publicKey.toString(), "-S", secretKey.toString()));
            return new RepoKeys(publicKey, secretKey);
        }

        private void copyRepository(GenericContainer<?> container, Path repository) throws Exception {
            try (var paths = Files.walk(repository)) {
                for (var path : paths.filter(Files::isRegularFile).toList()) {
                    var relative = repository.relativize(path).toString().replace('\\', '/');
                    container.withCopyToContainer(
                            Transferable.of(Files.readAllBytes(path), Integer.parseInt("0644", 8)), "/tmp/repo/" + relative);
                }
            }
        }

        @SuppressWarnings("resource")
        private GenericContainer<?> repositoryContainer(String image, boolean debian) {
            var container = new GenericContainer<>(image)
                    .withCreateContainerCmdModifier(command -> command.withEntrypoint("tail", "-f", "/dev/null"));
            if (debian) container.withEnv("DEBIAN_FRONTEND", "noninteractive");
            return container;
        }

        private void assertInstalledExecutables(GenericContainer<?> container, List<String> packageNames)
                throws Exception {
            for (var packageName : packageNames) {
                var result = container.execInContainer("/opt/simple-repo-cli/" + packageName);
                assertExecSuccess(result);
                assertEquals(packageName, result.getStdout().strip());
            }
        }

        private void assertCliSuccess(CliResult result) {
            assertEquals(0, result.exitCode(), result::diagnostic);
        }

        private void assertExecSuccess(Container.ExecResult result) {
            assertEquals(0, result.getExitCode(), () -> result.getStdout() + "\n" + result.getStderr());
        }

        private record RepoKeys(Path publicKey, Path secretKey) {
        }

        private record RepoFixture(Path root, String target, List<String> packageNames) {
        }
    }

    @Nested
    class KeysITest {
        @Test
        void displaysHelpForKeysCommands() {
            assertSuccessWithOutputContains(CliResult.cli("keys", "-h"), "Usage: simple-repo keys");
            assertSuccessWithOutputContains(CliResult.cli("keys", "gen", "-h"), "Usage: simple-repo keys gen");
            assertSuccessWithOutputContains(CliResult.cli("keys", "sign", "clear", "-h"), "Usage: simple-repo keys sign clear");
            assertSuccessWithOutputContains(CliResult.cli("keys", "sign", "detached", "-h"), "Usage: simple-repo keys sign detached");
            assertSuccessWithOutputContains(CliResult.cli("keys", "verify", "clear", "-h"), "Usage: simple-repo keys verify clear");
            assertSuccessWithOutputContains(CliResult.cli("keys", "verify", "detached", "-h"), "Usage: simple-repo keys verify detached");
        }

        @Test
        void generatesKeysToStandardOutputAndSeparateFiles() throws Exception {
            CliResult result = CliResult.cli("keys", "gen");
            assertEquals(0, result.exitCode(), result::diagnostic);
            assertTrue(result.stdout().contains("-----BEGIN PGP PUBLIC KEY BLOCK-----"));
            assertTrue(result.stdout().contains("-----BEGIN PGP PRIVATE KEY BLOCK-----"));

            var keys = KeyFiles.generateKeys("separate", temporaryDirectory);
            assertTrue(Files.readString(keys.publicKey()).contains("-----BEGIN PGP PUBLIC KEY BLOCK-----"));
            assertTrue(Files.readString(keys.secretKey()).contains("-----BEGIN PGP PRIVATE KEY BLOCK-----"));
        }

        private Path write(String name, String content) throws Exception {
            var path = temporaryDirectory.resolve(name);
            Files.writeString(path, content);
            return path;
        }

        @Test
        void clearSignsAndDetectsEditedSignedContent() throws Exception {
            var keys = KeyFiles.generateKeys("clear", temporaryDirectory);
            var input = write("clear-input", "content protected by a clear signature\n");
            var clearSignature = temporaryDirectory.resolve("clear.asc");

            CliResult result = CliResult.cli("keys", "sign", "clear",
                    "-i", input.toString(), "-S", keys.secretKey().toString(), "-o", clearSignature.toString());
            assertEquals(0, result.exitCode(), result::diagnostic);
            assertTrue(Files.readString(clearSignature).contains("-----BEGIN PGP SIGNED MESSAGE-----"));
            assertVerify(true, CliResult.cli("keys", "verify", "clear",
                    "-i", clearSignature.toString(), "-P", keys.publicKey().toString()));

            var edited = Files.readString(clearSignature)
                    .replace("content protected by a clear signature", "content edited after signing");
            Files.writeString(clearSignature, edited);
            assertVerify(false, CliResult.cli("keys", "verify", "clear",
                    "-i", clearSignature.toString(), "-P", keys.publicKey().toString()));
        }

        @Test
        void detachedSignsAndDetectsEditedDataAndSignature() throws Exception {
            var keys = KeyFiles.generateKeys("detached", temporaryDirectory);
            var originalContent = "content protected by a detached signature\n";
            var input = write("detached-input", originalContent);
            var signature = temporaryDirectory.resolve("detached.asc");

            CliResult result = CliResult.cli("keys", "sign", "detached",
                    "-i", input.toString(), "-S", keys.secretKey().toString(), "-o", signature.toString());
            assertEquals(0, result.exitCode(), result::diagnostic);
            assertTrue(Files.size(signature) > 0);
            assertVerify(true, verifyDetached(signature, keys.publicKey(), input));

            Files.writeString(input, "content edited after signing\n");
            assertVerify(false, verifyDetached(signature, keys.publicKey(), input));

            Files.writeString(input, originalContent);
            assertVerify(true, verifyDetached(signature, keys.publicKey(), input));
            Files.writeString(signature, "not an OpenPGP signature\n");
            assertVerify(false, verifyDetached(signature, keys.publicKey(), input));
        }

        private void assertVerify(boolean expected, CliResult result) {
            assertEquals(0, result.exitCode(), result::diagnostic);
            assertEquals(Boolean.toString(expected), result.stdout().strip(), result::diagnostic);
        }

        private CliResult verifyDetached(Path signature, Path publicKey, Path data) {
            return CliResult.cli("keys", "verify", "detached",
                    "-i", signature.toString(), "-P", publicKey.toString(), "-d", data.toString());
        }

        private record KeyFiles(Path publicKey, Path secretKey) {
            static KeyFiles generateKeys(String prefix, Path temporaryDirectory) {
                var publicKey = temporaryDirectory.resolve(prefix + "-public.asc");
                var secretKey = temporaryDirectory.resolve(prefix + "-secret.asc");
                CliResult result = CliResult.cli("keys", "gen", "-P", publicKey.toString(), "-S", secretKey.toString());
                assertEquals(0, result.exitCode(), result::diagnostic);
                return new KeyFiles(publicKey, secretKey);
            }
        }
    }
}
