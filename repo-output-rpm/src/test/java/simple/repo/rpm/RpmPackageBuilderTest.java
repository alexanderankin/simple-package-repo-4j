package simple.repo.rpm;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import simple.repo.model.Arch;
import simple.repo.model.PackageConfig;
import simple.repo.rpm.model.Lead;
import simple.repo.rpm.model.Signature;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

public class RpmPackageBuilderTest {

    @Test
    void parsesMetadataFromSidecarFileName() {
        var meta = new RpmPackageBuilder().metaFromFileName(
                "example-tools-1.10.0-7.el10.aarch64.rpm.spr4j-index.json");

        assertEquals("example-tools", meta.getName());
        assertEquals("1.10.0", meta.getVersion());
        assertEquals("7", meta.getReleaseVersion());
        assertEquals("el10", meta.getElVersion());
        assertEquals(Arch.arm64, meta.getArch());
    }

    @Test
    void buildsStandardHeaderAndSignatureEntries() throws Exception {
        var config = new PackageConfig()
                .setMeta(new PackageConfig.PackageMeta()
                        .setName("testpkg")
                        .setVersion("1.0.0")
                        .setReleaseVersion("2")
                        .setElVersion("el10")
                        .setArch(Arch.amd64))
                .setControl(new PackageConfig.ControlExtras()
                        .setDescription("A test package")
                        .setHomepage("https://example.test"))
                .setFiles(new PackageConfig.FileSpec().setDataFiles(List.of(
                        new PackageConfig.PkgFileSpec.BinaryPkgFileSpec()
                                .setContent("hello world\n".getBytes())
                                .setPath("/usr/share/test/hello.txt")
                                .setMode(0644))));

        var rpm = new RpmPackageBuilder().buildPackage(config);
        var bytes = rpm.getContent();
        var bb = ByteBuffer.wrap(bytes);

        var lead = Lead.parse(bb);
        assertEquals("testpkg-1.0.0-2.el10.x86_64.rpm", lead.getNameString());

        var signatureIntro = Signature.Intro.parse(bb);
        var signatureIndex = Signature.Index.parse(signatureIntro, bb);
        var signature = Signature.parseSignature(signatureIndex, bb);
        var headerStart = bb.position();

        var headerIntro = Signature.Intro.parse(bb);
        var headerIndex = Signature.Index.parse(headerIntro, bb);
        var header = Signature.parseHeader(headerIndex, bb);
        var payloadStart = bb.position();

        assertEquals(RpmTags.SignatureTag.HEADERSIGNATURES, signature.getEntries().getLast().getTag());
        assertEquals(RpmTags.RpmTag.RPMTAG_HEADERIMMUTABLE, header.getEntries().getLast().getTag());
        assertTrue(header.getEntries().stream().map(Signature.SignatureEntry::getTag).toList().containsAll(List.of(
                RpmTags.RpmTag.RPMTAG_NAME,
                RpmTags.RpmTag.RPMTAG_VERSION,
                RpmTags.RpmTag.RPMTAG_RELEASE,
                RpmTags.RpmTag.RPMTAG_SUMMARY,
                RpmTags.RpmTag.RPMTAG_DESCRIPTION,
                RpmTags.RpmTag.RPMTAG_FILESIZES,
                RpmTags.RpmTag.RPMTAG_FILEMODES,
                RpmTags.RpmTag.RPMTAG_FILEDIGESTS,
                RpmTags.RpmTag.RPMTAG_DIRINDEXES,
                RpmTags.RpmTag.RPMTAG_BASENAMES,
                RpmTags.RpmTag.RPMTAG_DIRNAMES,
                RpmTags.RpmTag.RPMTAG_PAYLOADFORMAT,
                RpmTags.RpmTag.RPMTAG_PAYLOADCOMPRESSOR,
                RpmTags.RpmTag.RPMTAG_PAYLOADSHA256)));
        assertEquals(1, config.getControl().getInstalledSize());
        assertEquals(12, ByteBuffer.wrap(entry(header, RpmTags.RpmTag.RPMTAG_SIZE).copyByteArray()).getInt());

        var headerBytes = java.util.Arrays.copyOfRange(bytes, headerStart, payloadStart);
        var headerAndPayload = java.util.Arrays.copyOfRange(bytes, headerStart, bytes.length);
        assertEquals(DigestUtils.sha1Hex(headerBytes), stringValue(signature, RpmTags.SignatureTag.SHA1));
        assertEquals(DigestUtils.sha256Hex(headerBytes), stringValue(signature, RpmTags.SignatureTag.SHA256));
        assertEquals(DigestUtils.md5Hex(headerAndPayload),
                Hex.encodeHexString(entry(signature, RpmTags.SignatureTag.MD5).copyByteArray()));

        var compressedPayload = java.util.Arrays.copyOfRange(bytes, payloadStart, bytes.length);
        assertEquals(DigestUtils.sha256Hex(compressedPayload),
                stringValue(header, RpmTags.RpmTag.RPMTAG_PAYLOADSHA256));
        assertTrue(new GZIPInputStream(new ByteArrayInputStream(compressedPayload)).readAllBytes().length > 0);
    }

    private static String stringValue(Signature signature, RpmTags tag) {
        return new String(entry(signature, tag).copyByteArray());
    }

    private static Signature.SignatureEntry entry(Signature signature, RpmTags tag) {
        return signature.getEntries().stream()
                .filter(candidate -> candidate.getTag() == tag)
                .findFirst()
                .orElseThrow();
    }
}
