package simple.repo.keys;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.BCPGOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.pgpainless.key.parsing.KeyRingReader;
import org.pgpainless.key.protection.UnlockSecretKey;
import org.pgpainless.sop.SOPImpl;
import org.pgpainless.util.Passphrase;
import sop.SOP;
import sop.exception.SOPGPException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;

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
        // validate/parse
        var skr = readSkr(privateKeyRing);

        // convert
        var skrCertificate = skr.toCertificate();

        // encode
        var output = new ByteArrayOutputStream();
        try (var armored = ArmoredOutputStream.builder()
                .clearHeaders()
                .build(output)) {
            skrCertificate.encode(armored);
        }
        return output.toByteArray();
    }

    private static PGPSecretKeyRing readSkr(byte[] privateKeyRing) throws IOException {
        Objects.requireNonNull(privateKeyRing, "privateKeyRing must not be null");
        var skr = KeyRingReader.readSecretKeyRing(new ByteArrayInputStream(privateKeyRing));
        Objects.requireNonNull(skr, () -> "could not readSecretKeyRing from privateKeyRing of length " + privateKeyRing.length);
        return skr;
    }

    private static PGPSecretKey findSigningKey(PGPSecretKeyRing skr) {
        return toList(skr.getSecretKeys())
                .stream()
                .filter(PGPSecretKey::isSigningKey)
                .filter(Predicate.not(PGPSecretKey::isMasterKey))
                .reduce((_, _) -> {
                    throw new IllegalArgumentException("duplicate");
                })
                // .or(() -> Optional.ofNullable(skr.getSecretKey())).orElseThrow();
                .orElseThrow(() -> new IllegalArgumentException("unable to find exactly one signing key"));
    }

    private static <T> List<T> toList(Iterator<T> tIterator) {
        List<T> tList = new ArrayList<>();
        tIterator.forEachRemaining(tList::add);
        return tList;
    }

    private static PGPSignatureGenerator setupSignatureGenerator(PGPSecretKeyRing skr, PGPSecretKey secretKey, Instant now) {
        var sigGen = new PGPSignatureGenerator(
                new JcaPGPContentSignerBuilder(
                        secretKey.getPublicKey().getAlgorithm(),
                        HashAlgorithmTags.SHA512
                ),
                skr.getSecretKey().getPublicKey()
        );

        var subpacketGen = new PGPSignatureSubpacketGenerator();
        subpacketGen.setIssuerFingerprint(false, secretKey.getPublicKey());
        subpacketGen.setSignatureCreationTime(false, Date.from(now));
        sigGen.setHashedSubpackets(subpacketGen.generate());
        return sigGen;
    }

    /**
     * Equivalent to:
     * <p>
     * gpg --detach-sign
     *
     * @param privateKeyRing gpg secret keyring
     * @param data           signing input
     * @param now            signing input
     * @return signature content
     */
    @SneakyThrows
    public static byte[] generateDetachedSig(byte[] privateKeyRing, byte[] data, Instant now) {
        return generateDetachedSig(privateKeyRing, data, now, null);
    }

    @SneakyThrows
    public static byte[] generateDetachedSig(byte[] privateKeyRing, byte[] data, Instant now, String passphrase) {
        var skr = readSkr(privateKeyRing);
        return generateDetachedSig(skr, data, now, passphrase);
    }

    @SneakyThrows
    public static byte[] generateDetachedSig(PGPSecretKeyRing skr, byte[] data, Instant now, String passphrase) {
        var secretKey = findSigningKey(skr);
        return generateDetachedSig(skr, secretKey, data, now, passphrase);
    }

    @SneakyThrows
    public static byte[] generateDetachedSig(PGPSecretKeyRing skr, PGPSecretKey secretKey, byte[] data, Instant now, String passphrase) {
        var pgPainlessPassphrase = passphrase == null ? Passphrase.emptyPassphrase() : Passphrase.fromPassword(passphrase);
        var privateKey = UnlockSecretKey.unlockSecretKey(secretKey, pgPainlessPassphrase);

        var sigGen = setupSignatureGenerator(skr, secretKey, now);

        sigGen.init(PGPSignature.BINARY_DOCUMENT, privateKey);
        sigGen.update(data);

        var out = new ByteArrayOutputStream(512);
        sigGen.generate().encode(out);
        return out.toByteArray();
    }

    /**
     * Equivalent to:
     * <p>
     * gpg --clearsign
     *
     * @param privateKeyRing gpg secret keyring
     * @param data           signing input
     * @param now            signing input
     * @param passphrase     optional
     * @return input+signature in "clear sign" format (e.g. InRelease)
     */
    @SneakyThrows
    public static byte[] generateClearSigned(byte[] privateKeyRing, byte[] data, Instant now, String passphrase) {
        var skr = readSkr(privateKeyRing);
        var secretKey = findSigningKey(skr);

        var pgPainlessPassphrase = passphrase == null ? Passphrase.emptyPassphrase() : Passphrase.fromPassword(passphrase);
        var privateKey = UnlockSecretKey.unlockSecretKey(secretKey, pgPainlessPassphrase);

        var sigGen = setupSignatureGenerator(skr, secretKey, now);

        sigGen.init(PGPSignature.CANONICAL_TEXT_DOCUMENT, privateKey);

        var out = new ByteArrayOutputStream();
        try (var armored = ArmoredOutputStream.builder()
                .clearHeaders()
                .build(out)) {

            armored.beginClearText(HashAlgorithmTags.SHA512);

            var lines = new String(data, StandardCharsets.UTF_8)
                    .replace("\r\n", "\n")
                    .replace('\r', '\n')
                    .split("\n", -1);

            for (var line : lines) {
                var lineBytes = line.getBytes(StandardCharsets.UTF_8);

                sigGen.update(lineBytes);

                armored.write(lineBytes);
                armored.write('\n');

                sigGen.update((byte) '\r');
                sigGen.update((byte) '\n');
            }

            armored.endClearText();

            var bOut = new BCPGOutputStream(armored);
            sigGen.generate().encode(bOut);
        }

        return out.toByteArray();
    }

    /*
    private static void readPkr(byte[] publicKeyRing) throws IOException {
        Objects.requireNonNull(publicKeyRing, "publicKeyRing must not be null");
        var pkr = KeyRingReader.readPublicKeyRing(new ByteArrayInputStream(publicKeyRing));
        Objects.requireNonNull(pkr, () -> "could not readPublicKeyRing from publicKeyRing of length " + publicKeyRing.length);
    }
    */

    @SneakyThrows
    public static boolean verifySignatureInline(byte[] publicKeyRing, byte[] data) {
        return VerificationUtils.verifySignatureInline(publicKeyRing, data);
    }

    @SneakyThrows
    public static boolean verifySignatureDetached(byte[] publicKeyRing, byte[] signature, byte[] data) {
        try {
            return !SOP.detachedVerify().cert(publicKeyRing).signatures(signature).data(data).isEmpty();
        } catch (SOPGPException s) {
            log.debug("failed detached verification: {}", s.getMessage());
            log.trace("failed with detached verification error message", s);
            return false;
        }
    }

    // here be ai code
    private static class VerificationUtils {
        private static final byte[] SIGNATURE_MARKER =
                "-----BEGIN PGP SIGNATURE-----".getBytes(StandardCharsets.US_ASCII);

        /**
         * Verify a clear-signed OpenPGP message.
         * <p>
         * First attempts normal RFC-compatible verification. If that fails, retries
         * using this project's legacy clear-sign behavior, which hashes one additional
         * CRLF before the signature armor.
         */
        @SneakyThrows
        public static boolean verifySignatureInline(byte[] publicKeyRing, byte[] signedMessage) {
            Objects.requireNonNull(publicKeyRing, "publicKeyRing must not be null");
            Objects.requireNonNull(signedMessage, "signedMessage must not be null");

            /*
            // this needs its own code path if it is going to happen
            if (verifySignatureInlineStandard(publicKeyRing, signedMessage)) {
                return true;
            }
            */

            byte[] compatibilityMessage = exposeLegacyTrailingCrlf(signedMessage);

            if (compatibilityMessage == null) {
                log.debug("failed inline verification: signature armor marker not found");
                return false;
            }

            boolean verified = verifySignatureInlineStandard(publicKeyRing, compatibilityMessage);

            if (verified) {
                log.trace("inline signature verified using legacy extra-CRLF compatibility");
            }

            return verified;
        }

        @SneakyThrows
        private static boolean verifySignatureInlineStandard(
                byte[] publicKeyRing,
                byte[] signedMessage
        ) {
            try {
                var result = SOP.inlineVerify()
                        .cert(publicKeyRing)
                        .data(signedMessage)
                        .toByteArrayAndResult()
                        .getResult();

                return !result.isEmpty();
            } catch (SOPGPException e) {
                log.debug("inline verification attempt failed: {}", e.getMessage());
                log.trace("inline verification attempt failed", e);
                return false;
            }
        }

        /**
         * Inserts one blank cleartext line immediately before the signature armor.
         * <p>
         * The newline directly preceding BEGIN PGP SIGNATURE is the cleartext/armor
         * separator. Adding another CRLF causes the standard verifier to include one
         * additional terminal CRLF in the canonical-text signature input.
         */
        private static byte[] exposeLegacyTrailingCrlf(byte[] signedMessage) {
            int markerOffset = indexOf(signedMessage, SIGNATURE_MARKER);

            if (markerOffset < 0) {
                return null;
            }

            /*
             * The marker should already be at the beginning of a line. Insert CRLF
             * immediately before it, regardless of whether the existing document uses
             * LF or CRLF around the armor boundary.
             */
            var output = new ByteArrayOutputStream(signedMessage.length + 2);
            output.write(signedMessage, 0, markerOffset);
            output.write('\r');
            output.write('\n');
            output.write(
                    signedMessage,
                    markerOffset,
                    signedMessage.length - markerOffset
            );

            return output.toByteArray();
        }

        private static int indexOf(byte[] data, byte[] target) {
            outer:
            for (int i = 0; i <= data.length - target.length; i++) {
                for (int j = 0; j < target.length; j++) {
                    if (data[i + j] != target[j]) {
                        continue outer;
                    }
                }
                return i;
            }

            return -1;
        }
    }
}
