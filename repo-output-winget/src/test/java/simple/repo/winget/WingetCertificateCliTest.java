package simple.repo.winget;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.*;

class WingetCertificateCliTest {
    @Test
    void generatesPasswordProtectedCodeSigningPfxAndPublicCer(@TempDir Path temporary) throws Exception {
        var pfx = temporary.resolve("private/signing.pfx");
        var cer = temporary.resolve("public/signing.cer");
        var password = "test-password".toCharArray();

        var generated = WingetCertificateCli.generate(
                "CN=Example Repository,O=Example Corp,C=US", pfx, cer, password, 30, 2048, false);

        assertTrue(Files.size(pfx) > 0);
        assertTrue(Files.size(cer) > 0);
        assertEquals("CN=Example Repository,O=Example Corp,C=US", generated.publisher());
        assertEquals(40, generated.thumbprint().length());

        var keyStore = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(pfx)) {
            keyStore.load(input, password);
        }
        assertTrue(keyStore.isKeyEntry("simple-repo-winget"));
        assertNotNull(keyStore.getKey("simple-repo-winget", password));

        X509Certificate certificate;
        try (var input = Files.newInputStream(cer)) {
            certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
        assertEquals("CN=Example Repository,O=Example Corp,C=US", certificate.getSubjectX500Principal().getName());
        assertEquals(java.util.List.of("1.3.6.1.5.5.7.3.3"), certificate.getExtendedKeyUsage());
        assertFalse(certificate.getKeyUsage()[1], "nonRepudiation must not be set");
        assertTrue(certificate.getKeyUsage()[0], "digitalSignature must be set");
    }

    @Test
    void picocliEntryPointUsesSafeDefaultsAndRefusesOverwrite(@TempDir Path temporary) {
        var pfx = temporary.resolve("signing.pfx");
        var cer = temporary.resolve("signing.cer");
        var command = WingetCertificateCli.commandLine();

        assertEquals(0, command.execute("--pfx", pfx.toString(), "--cer", cer.toString(),
                "--password", "test-password", "--key-size", "2048"));
        assertNotEquals(0, command.execute("--pfx", pfx.toString(), "--cer", cer.toString(),
                "--password", "test-password", "--key-size", "2048"));
    }

    @Test
    void generatesPasswordlessPfxByDefault(@TempDir Path temporary) throws Exception {
        var pfx = temporary.resolve("passwordless.pfx");
        var cer = temporary.resolve("passwordless.cer");

        assertEquals(0, WingetCertificateCli.commandLine().execute(
                "--pfx", pfx.toString(), "--cer", cer.toString(), "--key-size", "2048"));

        var keyStore = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(pfx)) {
            keyStore.load(input, new char[0]);
        }
        assertNotNull(keyStore.getKey("simple-repo-winget", new char[0]));
    }
}
