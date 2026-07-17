package simple.repo.winget;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import picocli.CommandLine;

import javax.security.auth.x500.X500Principal;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "winget-cert",
        mixinStandardHelpOptions = true,
        description = "Generate a self-signed WinGet MSIX code-signing certificate, PFX, and client CER."
)
public class WingetCertificateCli implements Callable<Integer> {
    @CommandLine.Option(names = "--publisher", defaultValue = "CN=Simple Repo",
            description = "X.500 publisher used in AppxManifest.xml. Default: ${DEFAULT-VALUE}")
    private String publisher;

    @CommandLine.Option(names = "--pfx", defaultValue = "simple-repo-winget.pfx",
            description = "Secret PKCS#12 output path. Default: ${DEFAULT-VALUE}")
    private Path pfx;

    @CommandLine.Option(names = "--cer", defaultValue = "simple-repo-winget.cer",
            description = "Public client certificate output path. Default: ${DEFAULT-VALUE}")
    private Path cer;

    @CommandLine.Option(names = {"-p", "--password"}, interactive = true, arity = "0..1",
            description = "Protect the PFX with a password. Omit this option for a passwordless PFX; omit only its value to prompt without echo.")
    private char[] password;

    @CommandLine.Option(names = "--valid-days", defaultValue = "3650",
            description = "Certificate validity in days. Default: ${DEFAULT-VALUE}")
    private int validDays;

    @CommandLine.Option(names = "--key-size", defaultValue = "3072",
            description = "RSA key size. Default: ${DEFAULT-VALUE}")
    private int keySize;

    @CommandLine.Option(names = "--force", description = "Replace existing output files.")
    private boolean force;

    public static void main(String[] args) {
        System.exit(commandLine().execute(args));
    }

    public static CommandLine commandLine() {
        return new CommandLine(new WingetCertificateCli())
                .setExecutionExceptionHandler((exception, commandLine, parseResult) -> {
                    commandLine.getErr().println("winget-cert: " + exception.getMessage());
                    return commandLine.getCommandSpec().exitCodeOnExecutionException();
                });
    }

    public static GeneratedCertificate generate(String publisher,
                                                Path pfx,
                                                Path cer,
                                                char[] password,
                                                int validDays,
                                                int keySize,
                                                boolean force) throws Exception {
        if (publisher == null || publisher.isBlank()) throw new IllegalArgumentException("publisher must not be blank");
        if (validDays <= 0) throw new IllegalArgumentException("valid-days must be positive");
        if (keySize < 2048) throw new IllegalArgumentException("key-size must be at least 2048");
        refuseExisting(pfx, force);
        refuseExisting(cer, force);
        createParent(pfx);
        createParent(cer);

        var storePassword = password == null ? new char[0] : password;
        var random = new SecureRandom();
        var keys = KeyPairGenerator.getInstance("RSA");
        keys.initialize(keySize, random);
        var keyPair = keys.generateKeyPair();
        // X500Principal applies the same RFC 2253 ordering Windows exposes as the certificate Subject.
        var subject = X500Name.getInstance(new X500Principal(publisher).getEncoded());
        var now = Instant.now();
        var serial = new BigInteger(160, random).abs().max(BigInteger.ONE);
        var builder = new JcaX509v3CertificateBuilder(
                subject,
                serial,
                Date.from(now.minus(Duration.ofMinutes(5))),
                Date.from(now.plus(Duration.ofDays(validDays))),
                subject,
                keyPair.getPublic());
        var extensions = new JcaX509ExtensionUtils();
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
        builder.addExtension(Extension.extendedKeyUsage, false,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_codeSigning));
        builder.addExtension(Extension.subjectKeyIdentifier, false,
                extensions.createSubjectKeyIdentifier(keyPair.getPublic()));
        builder.addExtension(Extension.authorityKeyIdentifier, false,
                extensions.createAuthorityKeyIdentifier(keyPair.getPublic()));
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        var certificate = new JcaX509CertificateConverter().getCertificate(builder.build(signer));
        certificate.checkValidity();
        certificate.verify(keyPair.getPublic());

        var keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, storePassword);
        keyStore.setKeyEntry("simple-repo-winget", keyPair.getPrivate(), storePassword,
                new java.security.cert.Certificate[]{certificate});
        try (OutputStream output = Files.newOutputStream(pfx)) {
            keyStore.store(output, storePassword);
        }
        restrictPrivateKey(pfx);
        Files.write(cer, certificate.getEncoded());

        X509Certificate parsed;
        try (var input = Files.newInputStream(cer)) {
            parsed = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
        var actualPublisher = parsed.getSubjectX500Principal().getName();
        return new GeneratedCertificate(pfx, cer, actualPublisher, thumbprint(parsed));
    }

    private static void refuseExisting(Path path, boolean force) {
        if (!force && Files.exists(path))
            throw new IllegalArgumentException("output already exists (use --force): " + path);
    }

    private static void createParent(Path path) throws Exception {
        var parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
    }

    private static void restrictPrivateKey(Path path) throws Exception {
        try {
            Files.setPosixFilePermissions(path, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // Windows protects the file using its ACL and the caller-selected location.
        }
    }

    private static String thumbprint(X509Certificate certificate) throws Exception {
        return java.util.HexFormat.of().withUpperCase().formatHex(
                java.security.MessageDigest.getInstance("SHA-1").digest(certificate.getEncoded()));
    }

    @Override
    public Integer call() throws Exception {
        var generated = generate(publisher, pfx, cer, password, validDays, keySize, force);
        System.out.println("Generated WinGet signing certificate");
        System.out.println("Publisher: " + generated.publisher());
        System.out.println("PFX (keep secret): " + generated.pfx().toAbsolutePath());
        System.out.println("CER (install on clients): " + generated.cer().toAbsolutePath());
        System.out.println("SHA-1 thumbprint: " + generated.thumbprint());
        System.out.println("install command: ");
        System.out.println("powershell.exe -Command \"" +
                "Import-Certificate " +
                "-CertStoreLocation 'Cert:\\LocalMachine\\TrustedPeople' " +
                "-FilePath '" + generated.cer().getFileName() + "'" +
                "\"");
        return 0;
    }

    public record GeneratedCertificate(Path pfx, Path cer, String publisher, String thumbprint) {
    }
}
