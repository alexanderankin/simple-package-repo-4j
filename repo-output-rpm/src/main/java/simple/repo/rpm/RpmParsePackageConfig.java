package simple.repo.rpm;

import simple.repo.model.Arch;
import simple.repo.model.PackageConfig;
import simple.repo.rpm.model.Lead;
import simple.repo.rpm.model.Signature;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import static simple.repo.rpm.RpmTags.RpmTag;

final class RpmParsePackageConfig {
    static final RpmParsePackageConfig INSTANCE = new RpmParsePackageConfig();

    PackageConfig parseConfigFromPackage(byte[] rpm) {
        var buffer = ByteBuffer.wrap(rpm);
        Lead.parse(buffer);

        var signatureIntro = Signature.Intro.parse(buffer);
        var signatureIndex = Signature.Index.parse(signatureIntro, buffer);
        Signature.parseSignature(signatureIndex, buffer);

        var headerIntro = Signature.Intro.parse(buffer);
        var headerIndex = Signature.Index.parse(headerIntro, buffer);
        var header = Signature.parseHeader(headerIndex, buffer);
        var entries = new EnumMap<RpmTag, Signature.SignatureEntry>(RpmTag.class);
        for (var entry : header.getEntries()) {
            if (entry.getTag() instanceof RpmTag tag) entries.putIfAbsent(tag, entry);
        }

        var release = RpmPackageBuilder.releaseParts(string(entries, RpmTag.RPMTAG_RELEASE));
        var meta = new PackageConfig.PackageMeta()
                .setName(string(entries, RpmTag.RPMTAG_NAME))
                .setVersion(string(entries, RpmTag.RPMTAG_VERSION))
                .setReleaseVersion(release.releaseVersion())
                .setElVersion(release.elVersion())
                .setArch(arch(string(entries, RpmTag.RPMTAG_ARCH)));

        var installedSizeBytes = int32(entries, RpmTag.RPMTAG_SIZE);
        var control = new PackageConfig.ControlExtras()
                .setDescription(string(entries, RpmTag.RPMTAG_DESCRIPTION))
                .setSection(string(entries, RpmTag.RPMTAG_GROUP))
                .setHomepage(optionalString(entries, RpmTag.RPMTAG_URL))
                .setMaintainer(optionalString(entries, RpmTag.RPMTAG_PACKAGER))
                .setInstalledSize(Math.toIntExact((Integer.toUnsignedLong(installedSizeBytes) + 1023L) / 1024L));

        return new PackageConfig()
                .setMeta(meta)
                .setControl(control)
                .setFiles(new PackageConfig.FileSpec()
                        .setControlFiles(controlFiles(entries))
                        .setDataFiles(dataFiles(entries)));
    }

    private List<PackageConfig.PkgFileSpec> dataFiles(
            EnumMap<RpmTag, Signature.SignatureEntry> entries) {
        if (!entries.containsKey(RpmTag.RPMTAG_BASENAMES)) return List.of();
        var basenames = strings(entries, RpmTag.RPMTAG_BASENAMES);
        var directories = strings(entries, RpmTag.RPMTAG_DIRNAMES);
        var directoryIndexes = int32s(entries, RpmTag.RPMTAG_DIRINDEXES);
        var modes = int16s(entries, RpmTag.RPMTAG_FILEMODES);
        if (basenames.size() != directoryIndexes.size() || basenames.size() != modes.size()) {
            throw new IllegalArgumentException("inconsistent RPM file metadata array lengths");
        }

        var files = new ArrayList<PackageConfig.PkgFileSpec>(basenames.size());
        for (var index = 0; index < basenames.size(); index++) {
            var directoryIndex = directoryIndexes.get(index);
            if (directoryIndex < 0 || directoryIndex >= directories.size()) {
                throw new IllegalArgumentException("invalid RPM directory index " + directoryIndex);
            }
            var path = directories.get(directoryIndex) + basenames.get(index);
            var file = new PackageConfig.PkgFileSpec.FilePkgFileSpec();
            file.setPath(path.startsWith("/") ? path : "/" + path);
            file.setMode(modes.get(index) & 0xFFF);
            files.add(file);
        }
        return files;
    }

    private List<PackageConfig.PkgFileSpec> controlFiles(
            EnumMap<RpmTag, Signature.SignatureEntry> entries) {
        var result = new ArrayList<PackageConfig.PkgFileSpec>();
        addScriptlet(result, entries, RpmTag.RPMTAG_PREIN, "preinst");
        addScriptlet(result, entries, RpmTag.RPMTAG_POSTIN, "postinst");
        addScriptlet(result, entries, RpmTag.RPMTAG_PREUN, "prerm");
        addScriptlet(result, entries, RpmTag.RPMTAG_POSTUN, "postrm");
        return result;
    }

    private void addScriptlet(List<PackageConfig.PkgFileSpec> result,
                              EnumMap<RpmTag, Signature.SignatureEntry> entries,
                              RpmTag tag,
                              String path) {
        var entry = entries.get(tag);
        if (entry == null) return;
        result.add(new PackageConfig.PkgFileSpec.TextPkgFileSpec()
                .setContent(text(entry)).setPath(path).setMode(0x755));
    }

    private Arch arch(String value) {
        return switch (value) {
            case "noarch" -> Arch.unknown;
            case "x86_64" -> Arch.amd64;
            case "aarch64" -> Arch.arm64;
            case "riscv64" -> Arch.riscv64;
            default -> throw new IllegalArgumentException("unsupported RPM architecture: " + value);
        };
    }

    private String string(EnumMap<RpmTag, Signature.SignatureEntry> entries, RpmTag tag) {
        var entry = entries.get(tag);
        if (entry == null) throw new IllegalArgumentException("RPM header is missing required tag " + tag);
        return text(entry);
    }

    private String optionalString(EnumMap<RpmTag, Signature.SignatureEntry> entries, RpmTag tag) {
        var entry = entries.get(tag);
        return entry == null ? "" : text(entry);
    }

    private String text(Signature.SignatureEntry entry) {
        return new String(entry.copyByteArray(), StandardCharsets.UTF_8);
    }

    private List<String> strings(EnumMap<RpmTag, Signature.SignatureEntry> entries, RpmTag tag) {
        return List.of(string(entries, tag).split("\\u0000", -1));
    }

    private int int32(EnumMap<RpmTag, Signature.SignatureEntry> entries, RpmTag tag) {
        var entry = entries.get(tag);
        if (entry == null) throw new IllegalArgumentException("RPM header is missing required tag " + tag);
        var values = entry.copyByteArray();
        if (values.length != Integer.BYTES) {
            throw new IllegalArgumentException("RPM tag " + tag + " is not a single int32");
        }
        return ByteBuffer.wrap(values).getInt();
    }

    private List<Integer> int32s(EnumMap<RpmTag, Signature.SignatureEntry> entries, RpmTag tag) {
        var entry = entries.get(tag);
        if (entry == null) throw new IllegalArgumentException("RPM header is missing required tag " + tag);
        var buffer = ByteBuffer.wrap(entry.copyByteArray());
        if (buffer.remaining() % Integer.BYTES != 0) {
            throw new IllegalArgumentException("RPM tag " + tag + " has an invalid int32 array length");
        }
        var result = new ArrayList<Integer>(buffer.remaining() / Integer.BYTES);
        while (buffer.hasRemaining()) result.add(buffer.getInt());
        return result;
    }

    private List<Integer> int16s(EnumMap<RpmTag, Signature.SignatureEntry> entries, RpmTag tag) {
        var entry = entries.get(tag);
        if (entry == null) throw new IllegalArgumentException("RPM header is missing required tag " + tag);
        var buffer = ByteBuffer.wrap(entry.copyByteArray());
        if (buffer.remaining() % Short.BYTES != 0) {
            throw new IllegalArgumentException("RPM tag " + tag + " has an invalid int16 array length");
        }
        var result = new ArrayList<Integer>(buffer.remaining() / Short.BYTES);
        while (buffer.hasRemaining()) result.add(Short.toUnsignedInt(buffer.getShort()));
        return result;
    }
}
