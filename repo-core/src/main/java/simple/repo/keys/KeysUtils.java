package simple.repo.keys;

import lombok.SneakyThrows;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.BCPGOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.pgpainless.PGPainless;
import org.pgpainless.algorithm.*;
import org.pgpainless.key.generation.KeySpec;
import org.pgpainless.key.generation.type.KeyType;
import org.pgpainless.key.generation.type.rsa.RsaLength;
import org.pgpainless.key.parsing.KeyRingReader;
import org.pgpainless.key.protection.UnlockSecretKey;
import org.pgpainless.signature.subpackets.SignatureSubpackets;
import org.pgpainless.util.Passphrase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class KeysUtils {
    @SuppressWarnings("deprecation")
    private static RsaLength defaultRsaLength() {
        return RsaLength._2048;
    }

    @SneakyThrows
    public static byte[] genKeyPairKeyring(String name, String email, Instant createdAt) {
        return genKeyPairKeyring(name, email, createdAt, defaultRsaLength());
    }

    @SneakyThrows
    public static byte[] genKeyPairKeyring(String name, String email, Instant createdAt, RsaLength length) {
        var creationDate = createdAt == null ? new Date(0) : Date.from(createdAt);

        Function<Integer, KeySpec> keySpecFunction = bitMask -> {
            var sp = SignatureSubpackets.createEmptySubpackets();

            sp.setKeyFlags(false, KeyFlag.fromBitmask(bitMask).toArray(new KeyFlag[0]));

            var kgas = PGPainless.getPolicy().getKeyGenerationAlgorithmSuite();

            sp.setPreferredCompressionAlgorithms(false,
                    kgas.getCompressionAlgorithms().stream()
                            .filter(Predicate.not(Predicate.isEqual(CompressionAlgorithm.UNCOMPRESSED)))
                            .toList());
            sp.setPreferredHashAlgorithms(false,
                    Stream.concat(kgas.getHashAlgorithms().stream(), Stream.of(HashAlgorithm.SHA1))
                            .toList());
            sp.setPreferredSymmetricKeyAlgorithms(false,
                    Stream.concat(kgas.getSymmetricKeyAlgorithms().stream(), Stream.of(SymmetricKeyAlgorithm.TRIPLE_DES))
                            .toList());

            sp.setFeatures(false, Feature.MODIFICATION_DETECTION);

            sp.setSignatureCreationTime(false, creationDate);
            sp.setKeyExpirationTime(false, creationDate, null);

            return new KeySpec(KeyType.RSA(length), sp, false, creationDate);
        };

        var secretKeyRing = PGPainless.buildKeyRing()
                .setExpirationDate(null)
                .setPrimaryKey(keySpecFunction.apply(0x2f))
                .addSubkey(keySpecFunction.apply(0x2e))
                .addUserId(name + " <" + email + ">")
                .build();

        var out = new ByteArrayOutputStream(16384);
        try (var armored = ArmoredOutputStream.builder()
                .clearHeaders()
                .build(out)) {
            secretKeyRing.encode(armored);
        }

        return out.toByteArray();
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
}
