package simple.repo.rpm;

import lombok.Data;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry;
import org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream;
import org.springframework.util.StringUtils;
import simple.repo.Version;
import simple.repo.model.Arch;
import simple.repo.model.FileIntegrityWithContent;
import simple.repo.model.PackageConfig;
import simple.repo.packaging.FileSpecReader;
import simple.repo.packaging.PackageBuilder;
import simple.repo.rpm.RpmTags.RpmTag;
import simple.repo.rpm.RpmTags.RpmTagType;
import simple.repo.rpm.model.Lead;
import simple.repo.rpm.model.RpmHashAlgo;
import simple.repo.rpm.model.Signature;
import simple.repo.rpm.model.Signature.SignatureEntry;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.zip.GZIPOutputStream;

import static simple.repo.rpm.RpmTags.RpmTagType.*;

/**
 * @see <a href=http://ftp.rpm.org/max-rpm/s1-rpm-file-format-rpm-file-format.html>rpm.org: Appendix A. Format of the RPM File</a>
 */
@Data
@Accessors(chain = true)
public class RpmPackageBuilder implements PackageBuilder {

    FileSpecReader fileSpecReader = new FileSpecReader();

    @Override
    public String outputType() {
        return RepoOutputRpm.OUTPUT_NAME;
    }

    @Override
    public String archName(Arch arch) {
        return switch (arch) {
            case amd64 -> RpmArch.x86_64.name();
            case arm64 -> RpmArch.aarch64.name();
            case riscv64 -> "riscv64";
            case null -> "noarch";
            case unknown -> "noarch";
            default -> throw new UnsupportedOperationException("Unknown arch: " + arch);
        };
    }

    /**
     * java-21-openjdk-javadoc-21.0.9.0.10-1.el10.aarch64.rpm
     * name-version-releaseversion.elversion.arch.rpm
     */
    @Override
    public String fileName(PackageConfig packageConfig) {
        PackageConfig.PackageMeta meta = packageConfig.getMeta();
        var stringBuilder = new StringBuilder(meta.getName());
        stringBuilder.append('-').append(meta.getVersion());

        if (StringUtils.hasText(meta.getReleaseVersion()))
            stringBuilder.append('-').append(meta.getReleaseVersion());

        if (StringUtils.hasText(meta.getElVersion()))
            stringBuilder.append('.').append(meta.getElVersion());
        stringBuilder.append('.').append(archName(meta.getArch())).append(".rpm");
        return stringBuilder.toString();
    }

    @Override
    @SneakyThrows
    public FileIntegrityWithContent buildPackage(PackageConfig config) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(config.getMeta(), "config.meta");
        Objects.requireNonNull(config.getFiles(), "config.files");

        var buildTime = Instant.now().getEpochSecond();
        var files = readFiles(config);
        var payload = buildPayload(files, buildTime);
        var header = buildHeader(config, files, payload, buildTime);
        var signature = buildSignature(header, payload);

        var out = new ByteArrayOutputStream();

        out.write(new Lead()
                .setType(Lead.PackageType.binary)
                .setArchNumValue(switch (config.getMeta().getArch()) {
                    case amd64 -> Lead.ArchNum.x86_64;
                    case arm64 -> Lead.ArchNum.aarch64;
                    case riscv64 -> Lead.ArchNum.riscv64;
                    case null, default -> throw new UnsupportedOperationException();
                })
                .setNameString(fileName(config))
                .setOsNum(Lead.OsNum.linux)
                .setSignatureType(Lead.SignatureType.header)
                .toByteArray());
        out.write(signature);
        while (out.size() % 8 != 0) {
            out.write(0);
        }
        out.write(header);
        out.write(payload.compressed());

        return FileIntegrityWithContent.of(out.toByteArray(), fileName(config));
    }

    @Override
    public PackageConfig parseConfigFromPackage(byte[] downloadedPackage) {
        throw new UnsupportedOperationException();
    }

    private List<PackageFile> readFiles(PackageConfig config) {
        var specs = Objects.requireNonNullElse(config.getFiles().getDataFiles(), List.<PackageConfig.PkgFileSpec>of());
        var files = new ArrayList<PackageFile>(specs.size());
        for (var spec : specs) {
            var path = normalizePath(spec.getPath());
            var separator = path.lastIndexOf('/');
            files.add(new PackageFile(
                    spec,
                    fileSpecReader.readContent(spec),
                    path,
                    path.substring(0, separator + 1),
                    path.substring(separator + 1)));
        }
        return files;
    }

    @SneakyThrows
    private Payload buildPayload(List<PackageFile> files, long buildTime) {
        var archive = new ByteArrayOutputStream();
        try (var cpio = new CpioArchiveOutputStream(archive)) {
            for (var file : files) {
                var entry = new CpioArchiveEntry("." + file.path(), file.content().length);
                entry.setMode(0100000L | fileMode(file.spec()));
                entry.setTime(buildTime);
                cpio.putArchiveEntry(entry);
                cpio.write(file.content());
                cpio.closeArchiveEntry();
            }
            cpio.finish();
        }
        var compressed = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(compressed)) {
            gzip.write(archive.toByteArray());
        }
        return new Payload(archive.toByteArray(), compressed.toByteArray());
    }

    private byte[] buildHeader(PackageConfig config, List<PackageFile> files, Payload payload, long buildTime) {
        var meta = config.getMeta();
        var control = config.getControl();
        var description = control == null || !StringUtils.hasText(control.getDescription())
                ? meta.getName()
                : control.getDescription();
        var entries = new ArrayList<SignatureEntry>();

        entries.add(new SignatureEntry()
                .setTag(RpmTag.RPMTAG_HEADERI18NTABLE)
                .setType(RpmTagType.RPM_STRING_ARRAY_TYPE)
                .setContent(ByteBuffer.wrap(String.join("\0", List.of("C")).getBytes(StandardCharsets.UTF_8))));

        entries.add(new SignatureEntry()
                .setTag(RpmTag.RPMTAG_NAME)
                .setType(RpmTagType.RPM_STRING_TYPE).
                setContent(ByteBuffer.wrap(meta.getName().getBytes(StandardCharsets.UTF_8))));

        entries.add(new SignatureEntry()
                .setTag(RpmTag.RPMTAG_VERSION)
                .setType(RpmTagType.RPM_STRING_TYPE)
                .setContent(ByteBuffer.wrap(meta.getVersion().getBytes(StandardCharsets.UTF_8))));

        String release = release(meta);
        entries.add(new SignatureEntry()
                .setTag(RpmTag.RPMTAG_RELEASE)
                .setType(RpmTagType.RPM_STRING_TYPE)
                .setContent(ByteBuffer.wrap(release.getBytes(StandardCharsets.UTF_8))));

        entries.add(new SignatureEntry()
                .setTag(RpmTag.RPMTAG_SUMMARY)
                .setType(RpmTagType.RPM_I18NSTRING_TYPE)
                .setContent(ByteBuffer.wrap(description.lines().findFirst().orElse(meta.getName()).getBytes(StandardCharsets.UTF_8))));

        entries.add(new SignatureEntry()
                .setTag(RpmTag.RPMTAG_DESCRIPTION)
                .setType(RpmTagType.RPM_I18NSTRING_TYPE)
                .setContent(ByteBuffer.wrap(description.getBytes(StandardCharsets.UTF_8))));

        entries.add(int32(RpmTag.RPMTAG_BUILDTIME, Math.toIntExact(buildTime)));

        entries.add(new SignatureEntry()
                .setTag(RpmTag.RPMTAG_BUILDHOST)
                .setType(RpmTagType.RPM_STRING_TYPE)
                .setContent(ByteBuffer.wrap("localhost".getBytes(StandardCharsets.UTF_8))));

        entries.add(int32(RpmTag.RPMTAG_SIZE, files.stream().mapToInt(f -> f.content().length).sum()));

        entries.add(new SignatureEntry()
                .setTag(RpmTag.RPMTAG_LICENSE)
                .setType(RpmTagType.RPM_STRING_TYPE)
                // todo make better?
                .setContent(ByteBuffer.wrap("unknown".getBytes(StandardCharsets.UTF_8))));

        String group = control == null ? "Applications/System" : control.getSection();
        entries.add(new SignatureEntry()
                .setTag(RpmTag.RPMTAG_GROUP)
                .setType(RpmTagType.RPM_I18NSTRING_TYPE)
                .setContent(ByteBuffer.wrap(group.getBytes(StandardCharsets.UTF_8))));

        if (control != null && StringUtils.hasText(control.getHomepage())) {
            entries.add(new SignatureEntry()
                    .setTag(RpmTag.RPMTAG_URL)
                    .setType(RpmTagType.RPM_STRING_TYPE)
                    .setContent(ByteBuffer.wrap(control.getHomepage().getBytes(StandardCharsets.UTF_8))));
        }
        var packager = StringUtils.hasText(meta.getReleaser())
                ? meta.getReleaser()
                : control == null ? null : control.getMaintainer();
        if (StringUtils.hasText(packager)) {
            entries.add(new SignatureEntry()
                    .setTag(RpmTag.RPMTAG_PACKAGER)
                    .setType(RpmTagType.RPM_STRING_TYPE)
                    .setContent(ByteBuffer.wrap(packager.getBytes(StandardCharsets.UTF_8))));
        }
        entries.add(new SignatureEntry()
                .setTag(RpmTag.RPMTAG_OS)
                .setType(RpmTagType.RPM_STRING_TYPE)
                .setContent(ByteBuffer.wrap("linux".getBytes(StandardCharsets.UTF_8))));
        entries.add(new SignatureEntry()
                .setTag(RpmTag.RPMTAG_ARCH)
                .setType(RpmTagType.RPM_STRING_TYPE)
                .setContent(ByteBuffer.wrap(archName(meta.getArch()).getBytes(StandardCharsets.UTF_8))));
        entries.add(new SignatureEntry()
                .setTag(RpmTag.RPMTAG_RPMVERSION)
                .setType(RpmTagType.RPM_STRING_TYPE)
                .setContent(ByteBuffer.wrap(Version.VERSION.getBytes(StandardCharsets.UTF_8))));

        addFileEntries(entries, files, buildTime);

        entries.add(int32(RpmTag.RPMTAG_ARCHIVESIZE, payload.uncompressed().length));
        entries.add(new SignatureEntry()
                .setTag(RpmTag.RPMTAG_PAYLOADFORMAT)
                .setType(RpmTagType.RPM_STRING_TYPE)
                .setContent(ByteBuffer.wrap("cpio".getBytes(StandardCharsets.UTF_8))));
        entries.add(new SignatureEntry()
                .setTag(RpmTag.RPMTAG_PAYLOADCOMPRESSOR)
                .setType(RpmTagType.RPM_STRING_TYPE)
                .setContent(ByteBuffer.wrap("gzip".getBytes(StandardCharsets.UTF_8))));
        entries.add(new SignatureEntry()
                .setTag(RpmTag.RPMTAG_PAYLOADFLAGS)
                .setType(RpmTagType.RPM_STRING_TYPE)
                .setContent(ByteBuffer.wrap("9".getBytes(StandardCharsets.UTF_8))));
        entries.add(new SignatureEntry()
                .setTag(RpmTag.RPMTAG_PLATFORM)
                .setType(RpmTagType.RPM_STRING_TYPE)
                .setContent(ByteBuffer.wrap((archName(meta.getArch()) + "-linux").getBytes(StandardCharsets.UTF_8))));
        entries.add(int32(RpmTag.RPMTAG_FILEDIGESTALGO, RpmHashAlgo.RPM_HASH_SHA256.getTagValue()));
        entries.add(new SignatureEntry()
                .setTag(RpmTag.RPMTAG_ENCODING)
                .setType(RpmTagType.RPM_STRING_TYPE)
                .setContent(ByteBuffer.wrap("utf-8".getBytes(StandardCharsets.UTF_8))));
        entries.add(new SignatureEntry()
                .setTag(RpmTag.RPMTAG_PAYLOADSHA256)
                .setType(RpmTagType.RPM_STRING_ARRAY_TYPE)
                .setContent(ByteBuffer.wrap(String.join("\0", List.of(DigestUtils.sha256Hex(payload.compressed()))).getBytes(StandardCharsets.UTF_8))));
        entries.add(int32(RpmTag.RPMTAG_PAYLOADSHA256ALGO, 8));
        entries.add(new SignatureEntry()
                .setTag(RpmTag.RPMTAG_PAYLOADSHA256ALT)
                .setType(RpmTagType.RPM_STRING_ARRAY_TYPE)
                .setContent(ByteBuffer.wrap(String.join("\0", List.of(DigestUtils.sha256Hex(payload.uncompressed()))).getBytes(StandardCharsets.UTF_8))));

        return buildSection(entries, RpmTag.RPMTAG_HEADERIMMUTABLE);
    }

    private void addFileEntries(List<SignatureEntry> entries, List<PackageFile> files, long buildTime) {
        if (files.isEmpty()) {
            return;
        }
        var directories = new LinkedHashMap<String, Integer>();
        for (var file : files) {
            directories.computeIfAbsent(file.directory(), ignored -> directories.size());
        }

        entries.add(int32s(RpmTag.RPMTAG_FILESIZES, files.stream().map(f -> f.content().length).toList()));
        entries.add(int16s(RpmTag.RPMTAG_FILEMODES, files.stream().map(f -> 0100000 | fileMode(f.spec())).toList()));
        entries.add(int16s(RpmTag.RPMTAG_FILERDEVS, Collections.nCopies(files.size(), 0)));
        entries.add(int32s(RpmTag.RPMTAG_FILEMTIMES, Collections.nCopies(files.size(), Math.toIntExact(buildTime))));
        entries.add(new SignatureEntry()
                .setTag(RpmTag.RPMTAG_FILEDIGESTS)
                .setType(RpmTagType.RPM_STRING_ARRAY_TYPE)
                .setContent(ByteBuffer.wrap(String.join("\0",
                        files.stream()
                                .map(PackageFile::content)
                                .map(DigestUtils::sha256Hex)
                                .toList()
                ).getBytes(StandardCharsets.UTF_8))));
        entries.add(new SignatureEntry()
                .setTag(RpmTag.RPMTAG_FILELINKTOS)
                .setType(RpmTagType.RPM_STRING_ARRAY_TYPE)
                .setContent(ByteBuffer.wrap(String.join("\0", Collections.nCopies(files.size(), "")).getBytes(StandardCharsets.UTF_8))));
        entries.add(int32s(RpmTag.RPMTAG_FILEFLAGS, Collections.nCopies(files.size(), 0)));
        entries.add(new SignatureEntry()
                .setTag(RpmTag.RPMTAG_FILEUSERNAME)
                .setType(RpmTagType.RPM_STRING_ARRAY_TYPE)
                .setContent(ByteBuffer.wrap(String.join("\0", Collections.nCopies(files.size(), "root")).getBytes(StandardCharsets.UTF_8))));
        entries.add(new SignatureEntry()
                .setTag(RpmTag.RPMTAG_FILEGROUPNAME)
                .setType(RpmTagType.RPM_STRING_ARRAY_TYPE)
                .setContent(ByteBuffer.wrap(String.join("\0", Collections.nCopies(files.size(), "root")).getBytes(StandardCharsets.UTF_8))));
        entries.add(int32s(RpmTag.RPMTAG_FILEVERIFYFLAGS, Collections.nCopies(files.size(), -1)));
        entries.add(int32s(RpmTag.RPMTAG_FILEDEVICES, Collections.nCopies(files.size(), 1)));
        entries.add(int32s(RpmTag.RPMTAG_FILEINODES,
                java.util.stream.IntStream.rangeClosed(1, files.size()).boxed().toList()));
        entries.add(new SignatureEntry()
                .setTag(RpmTag.RPMTAG_FILELANGS)
                .setType(RpmTagType.RPM_STRING_ARRAY_TYPE)
                .setContent(ByteBuffer.wrap(String.join("\0", Collections.nCopies(files.size(), "")).getBytes(StandardCharsets.UTF_8))));
        entries.add(int32s(RpmTag.RPMTAG_DIRINDEXES,
                files.stream().map(f -> directories.get(f.directory())).toList()));
        entries.add(new SignatureEntry()
                .setTag(RpmTag.RPMTAG_BASENAMES)
                .setType(RpmTagType.RPM_STRING_ARRAY_TYPE)
                .setContent(ByteBuffer.wrap(String.join("\0", files.stream().map(PackageFile::basename).toList()).getBytes(StandardCharsets.UTF_8))));
        entries.add(new SignatureEntry()
                .setTag(RpmTag.RPMTAG_DIRNAMES)
                .setType(RpmTagType.RPM_STRING_ARRAY_TYPE)
                .setContent(ByteBuffer.wrap(String.join("\0", new ArrayList<String>(directories.keySet())).getBytes(StandardCharsets.UTF_8))));
    }

    private byte[] buildSignature(byte[] header, Payload payload) {
        var headerAndPayload = ByteBuffer.allocate(header.length + payload.compressed().length)
                .put(header)
                .put(payload.compressed())
                .array();
        var entries = new ArrayList<SignatureEntry>();
        String value3 = DigestUtils.sha1Hex(header);
        entries.add(new SignatureEntry().setTag(RpmTags.SignatureTag.SHA1).setType(RpmTagType.RPM_STRING_TYPE).setContent(ByteBuffer.wrap(value3.getBytes(StandardCharsets.UTF_8))));
        String value2 = DigestUtils.sha256Hex(header);
        entries.add(new SignatureEntry().setTag(RpmTags.SignatureTag.SHA256).setType(RpmTagType.RPM_STRING_TYPE).setContent(ByteBuffer.wrap(value2.getBytes(StandardCharsets.UTF_8))));
        entries.add(int32(RpmTags.SignatureTag.SIZE, headerAndPayload.length));
        byte[] value1 = DigestUtils.md5(headerAndPayload);
        entries.add(new SignatureEntry().setTag(RpmTags.SignatureTag.MD5).setType(RpmTagType.RPM_BIN_TYPE).setContent(ByteBuffer.wrap(value1)));
        entries.add(int32(RpmTags.SignatureTag.PAYLOADSIZE, payload.uncompressed().length));
        byte[] value = new byte[4096];
        entries.add(new SignatureEntry().setTag(RpmTags.SignatureTag.RESERVEDSPACE).setType(RpmTagType.RPM_BIN_TYPE).setContent(ByteBuffer.wrap(value)));
        return buildSection(entries, RpmTags.SignatureTag.HEADERSIGNATURES);
    }

    private byte[] buildSection(List<SignatureEntry> entries, RpmTags regionTag) {
        var regionEntryCount = entries.size() + 1;
        var trailer = ByteBuffer.allocate(16)
                .putInt(regionTag.getTagValue())
                .putInt(RPM_BIN_TYPE.getValue())
                .putInt(-Math.multiplyExact(regionEntryCount, 16))
                .putInt(16)
                .array();
        entries.add(new SignatureEntry().setTag(regionTag).setType(RpmTagType.RPM_BIN_TYPE).setContent(ByteBuffer.wrap(trailer)));

        var data = new Signature().setEntries(entries);
        var index = data.toIndex();
        return ByteBuffer.allocate(Signature.Intro.SIZE + index.getIntro().indexSize() + data.totalLength())
                .put(index.getIntro().toByteArray())
                .put(index.toByteArray())
                .put(data.toByteArray())
                .array();
    }

    private SignatureEntry int32(RpmTags tag, int value) {
        byte[] value1 = ByteBuffer.allocate(4).putInt(value).array();
        return new SignatureEntry().setTag(tag).setType(RPM_INT32_TYPE).setContent(ByteBuffer.wrap(value1));
    }

    private SignatureEntry int32s(RpmTags tag, List<Integer> values) {
        var buffer = ByteBuffer.allocate(Math.multiplyExact(values.size(), 4));
        values.forEach(buffer::putInt);
        byte[] value = buffer.array();
        return new SignatureEntry().setTag(tag).setType(RPM_INT32_TYPE).setContent(ByteBuffer.wrap(value));
    }

    private SignatureEntry int16s(RpmTags tag, List<Integer> values) {
        var buffer = ByteBuffer.allocate(Math.multiplyExact(values.size(), 2));
        values.forEach(value -> buffer.putShort((short) (value & 0xffff)));
        byte[] value = buffer.array();
        return new SignatureEntry().setTag(tag).setType(RPM_INT16_TYPE).setContent(ByteBuffer.wrap(value));
    }

    @SuppressWarnings("OctalInteger")
    private int fileMode(PackageConfig.PkgFileSpec spec) {
        return Objects.requireNonNullElse(spec.getMode(), 0644) & 07777;
    }

    private String release(PackageConfig.PackageMeta meta) {
        var release = StringUtils.hasText(meta.getReleaseVersion()) ? meta.getReleaseVersion() : "1";
        return StringUtils.hasText(meta.getElVersion()) ? release + "." + meta.getElVersion() : release;
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            throw new IllegalArgumentException("package file path is blank");
        }
        var normalized = path.startsWith("/") ? path : "/" + path;
        if (normalized.endsWith("/")) {
            throw new IllegalArgumentException("package file path must name a file: " + path);
        }
        return normalized;
    }

    private record PackageFile(PackageConfig.PkgFileSpec spec, byte[] content, String path, String directory,
                               String basename) {
    }

    private record Payload(byte[] uncompressed, byte[] compressed) {
    }
}
