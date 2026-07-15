package simple.repo.cli;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
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
