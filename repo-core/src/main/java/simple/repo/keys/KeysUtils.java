package simple.repo.keys;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.pgpainless.sop.SOPImpl;
import sop.SOP;
import sop.enums.InlineSignAs;
import sop.exception.SOPGPException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Slf4j
public class KeysUtils {
    static final SOP SOP = new SOPImpl();

    @SneakyThrows
    public static GeneratedKeyPair genKeyPairKeyring(String name, String email, SupportedKeyGenerationProfile profile) {
        String uid = name != null && email != null
                ? name + " <" + email + ">"
                : name;

        // Generate an OpenPGP key
        var generateKey = SOP.generateKey();
        if (uid != null)
            generateKey = generateKey.userId(uid);
        byte[] key = generateKey
                .profile(profile.getProfile())
                .generate()
                .getBytes();

        // Extract the certificate (public key)
        byte[] cert = SOP.extractCert()
                .key(key)
                .getBytes();

        return new GeneratedKeyPair()
                .setPublicKey(new String(cert))
                .setPrivateKey(new String(key));
    }

    @SneakyThrows
    public static byte[] exportPublicFromPrivateKeyRing(byte[] privateKeyRing) {
        return SOP.extractCert().key(privateKeyRing).getBytes();
    }

    @SneakyThrows
    public static byte[] generateDetachedSig(byte[] privateKeyRing, byte[] data, Instant now) {
        return generateDetachedSig(privateKeyRing, data, now, null);
    }

    @SneakyThrows
    public static byte[] generateDetachedSig(byte[] privateKeyRing,
                                             byte[] data,
                                             Instant now,
                                             String passphrase) {
        var operation = SOP.detachedSign();
        if (passphrase != null) {
            operation = operation.withKeyPassword(passphrase);
        }
        return operation
                .key(privateKeyRing)
                .data(data)
                .toByteArrayAndResult()
                .getBytes();
    }

    @SneakyThrows
    public static byte[] generateClearSigned(byte[] privateKeyRing,
                                             byte[] data,
                                             Instant now,
                                             String passphrase) {
        var operation = SOP.inlineSign().mode(InlineSignAs.clearsigned);
        if (passphrase != null) {
            operation = operation.withKeyPassword(passphrase);
        }
        return operation
                .key(privateKeyRing)
                .data(data)
                .getBytes();
    }

    @SneakyThrows
    public static boolean verifySignatureInline(byte[] publicKeyRing, byte[] data) {
        try {
            return !SOP.inlineVerify()
                    .cert(publicKeyRing)
                    .data(data)
                    .toByteArrayAndResult()
                    .getResult()
                    .isEmpty();
        } catch (SOPGPException exception) {
            log.debug("failed inline verification: {}", exception.getMessage());
            return false;
        }
    }

    @SneakyThrows
    public static boolean verifySignatureDetached(byte[] publicKeyRing, byte[] signature, byte[] data) {
        try {
            return !SOP.detachedVerify()
                    .cert(publicKeyRing)
                    .signatures(signature)
                    .data(data)
                    .isEmpty();
        } catch (SOPGPException exception) {
            log.debug("failed detached verification: {}", exception.getMessage());
            return false;
        }
    }
}
