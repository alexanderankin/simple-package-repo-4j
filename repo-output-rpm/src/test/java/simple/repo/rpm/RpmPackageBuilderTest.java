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
    void parsesBuiltPackageIntoMetadataOnlyConfig() {
        var config = new PackageConfig()
                .setMeta(new PackageConfig.PackageMeta()
                        .setName("parsed-package").setVersion("2.3.4").setReleaseVersion("7")
                        .setElVersion("el10").setArch(Arch.arm64))
                .setControl(new PackageConfig.ControlExtras()
                        .setMaintainer("Package Maintainer <maintainer@example.invalid>")
                        .setDescription("Parsed package description")
                        .setHomepage("https://example.invalid/parsed-package")
                        .setSection("Applications/Utilities"))
                .setFiles(new PackageConfig.FileSpec()
                        .setControlFiles(List.of(
                                script("preinst", "preinstall"),
                                script("postinst", "postinstall"),
                                script("prerm", "preuninstall"),
                                script("postrm", "postuninstall")))
                        .setDataFiles(List.of(
                                new PackageConfig.PkgFileSpec.TextPkgFileSpec()
                                        .setContent("hello\n").setPath("/opt/parsed/bin/hello").setMode(0x755),
                                new PackageConfig.PkgFileSpec.BinaryPkgFileSpec()
                                        .setContent(new byte[]{1, 2, 3}).setPath("/etc/parsed/config").setMode(0x640))));
        var builder = new RpmPackageBuilder();

        var parsed = builder.parseConfigFromPackage(builder.buildPackage(config).getContent());

        assertEquals("parsed-package", parsed.getMeta().getName());
        assertEquals("2.3.4", parsed.getMeta().getVersion());
        assertEquals("7", parsed.getMeta().getReleaseVersion());
        assertEquals("el10", parsed.getMeta().getElVersion());
        assertEquals(Arch.arm64, parsed.getMeta().getArch());
        assertEquals("Package Maintainer <maintainer@example.invalid>", parsed.getControl().getMaintainer());
        assertEquals("Parsed package description", parsed.getControl().getDescription());
        assertEquals("https://example.invalid/parsed-package", parsed.getControl().getHomepage());
        assertEquals("Applications/Utilities", parsed.getControl().getSection());
        assertEquals(1, parsed.getControl().getInstalledSize());
        assertEquals(List.of("/opt/parsed/bin/hello", "/etc/parsed/config"), parsed.getFiles().getDataFiles()
                .stream().map(PackageConfig.PkgFileSpec::getPath).toList());
        assertEquals(List.of(0x755, 0x640), parsed.getFiles().getDataFiles()
                .stream().map(PackageConfig.PkgFileSpec::getMode).toList());
        assertTrue(parsed.getFiles().getDataFiles().stream()
                .allMatch(PackageConfig.PkgFileSpec.FilePkgFileSpec.class::isInstance));
        assertEquals(List.of("preinst", "postinst", "prerm", "postrm"), parsed.getFiles().getControlFiles()
                .stream().map(PackageConfig.PkgFileSpec::getPath).toList());
        assertEquals(List.of("#!/bin/sh\necho preinstall\n", "#!/bin/sh\necho postinstall\n",
                        "#!/bin/sh\necho preuninstall\n", "#!/bin/sh\necho postuninstall\n"),
                parsed.getFiles().getControlFiles().stream()
                        .map(PackageConfig.PkgFileSpec.TextPkgFileSpec.class::cast)
                        .map(PackageConfig.PkgFileSpec.TextPkgFileSpec::getContent).toList());
    }

    @Test
    void parsesNoarchPackage() {
        var config = new PackageConfig()
                .setMeta(new PackageConfig.PackageMeta()
                        .setName("noarch-package").setVersion("1.0.0").setReleaseVersion("1")
                        .setArch(Arch.unknown))
                .setControl(new PackageConfig.ControlExtras().setDescription("noarch package"))
                .setFiles(new PackageConfig.FileSpec().setControlFiles(List.of()).setDataFiles(List.of()));
        var builder = new RpmPackageBuilder();

        var parsed = builder.parseConfigFromPackage(builder.buildPackage(config).getContent());

        assertEquals(Arch.unknown, parsed.getMeta().getArch());
        assertTrue(parsed.getFiles().getDataFiles().isEmpty());
    }

    @Test
    void reportsMissingRequiredHeaderTag() {
        var config = new PackageConfig()
                .setMeta(new PackageConfig.PackageMeta()
                        .setName("broken-package").setVersion("1.0.0").setReleaseVersion("1")
                        .setArch(Arch.amd64))
                .setControl(new PackageConfig.ControlExtras().setDescription("broken package"))
                .setFiles(new PackageConfig.FileSpec().setControlFiles(List.of()).setDataFiles(List.of()));
        var builder = new RpmPackageBuilder();
        var rpm = builder.buildPackage(config).getContent();
        var buffer = ByteBuffer.wrap(rpm);
        Lead.parse(buffer);
        var signatureIntro = Signature.Intro.parse(buffer);
        var signatureIndex = Signature.Index.parse(signatureIntro, buffer);
        Signature.parseSignature(signatureIndex, buffer);
        var headerIntro = Signature.Intro.parse(buffer);
        var headerIndexStart = buffer.position();
        var headerIndex = Signature.Index.parse(headerIntro, buffer);
        var nameIndex = java.util.stream.IntStream.range(0, headerIndex.getEntries().size())
                .filter(index -> headerIndex.getEntries().get(index).getTag() == RpmTags.RpmTag.RPMTAG_NAME.getTagValue())
                .findFirst().orElseThrow();
        ByteBuffer.wrap(rpm).putInt(headerIndexStart + nameIndex * 16, 0x7fffffff);

        var exception = assertThrows(IllegalArgumentException.class, () -> builder.parseConfigFromPackage(rpm));

        assertTrue(exception.getMessage().contains("RPMTAG_NAME"));
    }

    private PackageConfig.PkgFileSpec.TextPkgFileSpec script(String path, String message) {
        return (PackageConfig.PkgFileSpec.TextPkgFileSpec) new PackageConfig.PkgFileSpec.TextPkgFileSpec()
                .setContent("#!/bin/sh\necho " + message + "\n").setPath(path).setMode(0x755);
    }

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
