package simple.repo.rpm;

import lombok.Data;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry;
import org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import simple.repo.model.Arch;
import simple.repo.model.FileIntegrityWithContent;
import simple.repo.model.PackageConfig;
import simple.repo.packaging.PackageBuilder;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.GZIPOutputStream;

/**
 * @see <a href=http://ftp.rpm.org/max-rpm/s1-rpm-file-format-rpm-file-format.html>rpm.org: Appendix A. Format of the RPM File</a>
 */
@Data
@Accessors(chain = true)
public class RpmPackageBuilder implements PackageBuilder {

    private static final byte[] MAGIC = {(byte) 0xed, (byte) 0xab, (byte) 0xee, (byte) 0xdb};

    // private static final byte[] LEAD_FIRST_SIX_BYTES = {(byte) 0xed, (byte) 0xab, (byte) 0xee, (byte) 0xdb, 3, 0,};
    Path current = Path.of(System.getProperty("user.dir"));
    RestClient restClient = RestClient.create();

    @Override
    public String outputType() {
        return RepoOutputRpm.OUTPUT_NAME;
    }

    @Override
    public String archName(Arch arch) {
        if (arch == null)
            return "noarch";
        return Objects.requireNonNull(RpmArch.fromArch(arch), () -> "Unknown arch: " + arch).name();
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

        stringBuilder.append('.').append(meta.getElVersion());
        stringBuilder.append('.').append(archName(meta.getArch())).append(".rpm");
        return stringBuilder.toString();
    }

    @Override
    @SneakyThrows
    public FileIntegrityWithContent buildPackage(PackageConfig config) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeLead(out, config);
        byte[] payload = buildPayload(config);
        writeSignatureHeader(out, payload);
        // buildMainHeader(config, payload).write(out);
        // out.write(payload);
        return FileIntegrityWithContent.of(out.toByteArray(), fileName(config));
    }

    private void writeSignatureHeader(ByteArrayOutputStream out, byte[] payload) {
        new Header()
                // .withEntry(RpmTags.SignatureTag.HEADERSIGNATURES, RpmTags.RpmTagType.BIN, 0, 0, new byte[0])
                // .withEntry(RpmTags.SignatureTag.RSA, Type.BIN, 0, 0, new byte[0])
                // .withEntry(RpmTags.SignatureTag.SHA1, Type.STRING, 0, 0, new byte[0])
                // .withEntry(RpmTags.SignatureTag.SHA256, Type.STRING, 0, 0, new byte[0])
                // .withEntry(RpmTags.SignatureTag.SIZE, Type.INT32, 0, 0, new byte[0])
                // .withEntry(RpmTags.SignatureTag.RPMTAG_SUMMARY, Type.BIN, 0, 0, new byte[0])
                // .withEntry(RpmTags.SignatureTag.RPMTAG_BUILDHOST, Type.INT32, 0, 0, new byte[0])
                // .withEntry(RpmTags.SignatureTag.RPMTAG_INSTALLTIME, Type.BIN, 0, 0, new byte[0])
                .write(out);
    }

    @SneakyThrows
    private void writeLead(ByteArrayOutputStream out, PackageConfig config) {
        // char magic[4]
        out.write(MAGIC);
        // char major, minor (version 3.0)
        out.write(new byte[]{0x3, 0x0});
        // short type (0 = binary, 1 = source)
        out.write(new byte[]{0x0, 0x0});
        // short archnum (not used by rpm)
        out.write(new byte[]{0x0, 0x0});
        // 65 bytes for name and 2 for os code (linux = 1)
        out.write(new byte[67]);
    }

    @SneakyThrows
    private byte[] buildPayload(PackageConfig config) {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();

        try (var gzip = new GZIPOutputStream(raw);
             var cpio = new CpioArchiveOutputStream(gzip)) {

            for (PackageConfig.TarFileSpec file : config.getFiles().getDataFiles()) {
                byte[] content = switch (file) {
                    case PackageConfig.TarFileSpec.BinaryTarFileSpec bin -> bin.getContent();
                    case PackageConfig.TarFileSpec.TextTarFileSpec text ->
                            text.getContent().getBytes(StandardCharsets.UTF_8);
                    case PackageConfig.TarFileSpec.FileTarFileSpec fs ->
                            Files.readAllBytes(current.resolve(fs.getSourcePath()));
                    case PackageConfig.TarFileSpec.UrlTarFileSpec fs -> Objects.requireNonNull(
                            restClient.get()
                                    .uri(fs.getUrl())
                                    .headers(h -> {
                                        if (!CollectionUtils.isEmpty(fs.getHeaders()))
                                            h.putAll(fs.getHeaders());
                                        if (fs.getBearerToken() != null)
                                            h.setBearerAuth(fs.getBearerToken());
                                    })
                                    .retrieve()
                                    .body(byte[].class),
                            "no response body"
                    );
                };
                cpio.putArchiveEntry(new CpioArchiveEntry(file.getPath(), content.length));
                cpio.write(content);
            }

            cpio.finish();
        }

        return raw.toByteArray();
    }

    /**
     * @see <a href=https://rpm-software-management.github.io/rpm/manual/format_header.html>rpm-software-management.github.io: format_header.html</a>
     */
    @Data
    @Accessors(chain = true)
    static class Header {
        static final byte[] MAGIC_VALUE = {
                (byte) 0x8e, (byte) 0xad, (byte) 0xe8, (byte) 1,
                (byte) 0, (byte) 0, (byte) 0, (byte) 0
        };

        List<Entry> entries = new ArrayList<>();

        Header withEntry(RpmTags.SignatureTag tag, RpmTags.RpmTagType type, int count, byte[] content) {
            entries.add(new Entry(tag, type, count, content));
            return this;
        }

        private int getIndexLength() {
            return entries.size();
        }

        private int getDataLength() {
            return entries.stream().mapToInt(Entry::dataLength).sum();
        }

        @SneakyThrows
        void write(OutputStream outputStream) {
            outputStream.write(MAGIC_VALUE);
            outputStream.write(getIndexLength());
            outputStream.write(getDataLength());
            int offset = 0;
            for (var entry : entries) {
                // tag, type, offset, count
                outputStream.write(entry.tag.getTagValue());
                outputStream.write(entry.type.getValue());
                outputStream.write(offset);
                outputStream.write(entry.count);
                offset += entry.dataLength();
            }

            for (var entry : entries) {
                outputStream.write(entry.content);
                var pad = entry.dataLength() - entry.content.length;
                if (pad > 0) {
                    outputStream.write(new byte[pad]);
                }
            }
        }

        record Entry(RpmTags.SignatureTag tag, RpmTags.RpmTagType type, int count, byte[] content) {
            int dataLength() {
                int alignment = switch (type) {
                    case RPM_INT16_TYPE -> 2;
                    case RPM_INT32_TYPE -> 4;
                    case RPM_INT64_TYPE -> 8;
                    default -> 1;
                };

                int remainder = content.length % alignment;
                int padding = remainder == 0 ? 0 : alignment - remainder;

                return content.length + padding;
            }
        }
    }

    //
    // @SneakyThrows
    // private void writeSignatureHeader(OutputStream out, byte[] payload) {
    //
    //     ByteArrayOutputStream store = new ByteArrayOutputStream();
    //
    //     // region trailer
    //     int regionTag = 62;
    //     int regionType = 7;
    //     // int regionOffset = 16; // points to trailer
    //     // int regionOffset = -16; // points to trailer
    //     int regionOffset = 0;
    //     int regionCount = 16;
    //
    //     // RPMSIGTAG_SIZE payload
    //     int sizeOffset = 16;
    //
    //     ByteBuffer sizeData = ByteBuffer.allocate(4)
    //             .order(ByteOrder.BIG_ENDIAN);
    //
    //     sizeData.putInt(payload.length);
    //
    //     // store.write(new byte[16]); // region placeholder
    //     ByteBuffer region = ByteBuffer.allocate(16)
    //             .order(ByteOrder.BIG_ENDIAN);
    //
    //     region.putInt(62);
    //     region.putInt(7);
    //     // region.putInt(-16);
    //     region.putInt(0);
    //     region.putInt(16);
    //
    //     store.write(region.array());
    //
    //     store.write(sizeData.array());
    //
    //     int storeSize = store.size();
    //
    //     ByteBuffer h = ByteBuffer.allocate(16)
    //             .order(ByteOrder.BIG_ENDIAN);
    //
    //     h.put((byte) 0x8e);
    //     h.put((byte) 0xad);
    //     h.put((byte) 0xe8);
    //     h.put((byte) 0x01);
    //
    //     h.putInt(0);
    //     h.putInt(2); // 2 entries
    //     h.putInt(storeSize);
    //
    //     out.write(h.array());
    //
    //     // entry 1: region tag
    //     ByteBuffer e1 = ByteBuffer.allocate(16)
    //             .order(ByteOrder.BIG_ENDIAN);
    //
    //     e1.putInt(regionTag);
    //     e1.putInt(regionType);
    //     e1.putInt(regionOffset);
    //     e1.putInt(regionCount);
    //
    //     out.write(e1.array());
    //
    //     // entry 2: size tag
    //     ByteBuffer e2 = ByteBuffer.allocate(16)
    //             .order(ByteOrder.BIG_ENDIAN);
    //
    //     e2.putInt(1000);
    //     e2.putInt(4);
    //     e2.putInt(sizeOffset);
    //     e2.putInt(1);
    //
    //     out.write(e2.array());
    //
    //     out.write(store.toByteArray());
    //
    //     int total =
    //             16 + // header
    //                     32 + // entries
    //                     storeSize;
    //
    //     int padding = (8 - (total % 8)) % 8;
    //
    //     out.write(new byte[padding]);
    // }
    //
    // @SneakyThrows
    // private byte[] buildPayload(PackageConfig config) {
    //     ByteArrayOutputStream raw = new ByteArrayOutputStream();
    //
    //     try (GZIPOutputStream gzip = new GZIPOutputStream(raw)) {
    //         CpioWriter cpio = new CpioWriter(gzip);
    //
    //         for (PackageConfig.TarFileSpec file : config.getFiles().getDataFiles()) {
    //             byte[] content = switch (file) {
    //                 case PackageConfig.TarFileSpec.BinaryTarFileSpec bin -> bin.getContent();
    //                 case PackageConfig.TarFileSpec.TextTarFileSpec text ->
    //                         text.getContent().getBytes(StandardCharsets.UTF_8);
    //                 case PackageConfig.TarFileSpec.FileTarFileSpec fs ->
    //                         Files.readAllBytes(current.resolve(fs.getSourcePath()));
    //                 case PackageConfig.TarFileSpec.UrlTarFileSpec fs -> Objects.requireNonNull(
    //                         restClient.get()
    //                                 .uri(fs.getUrl())
    //                                 .headers(h -> {
    //                                     if (!CollectionUtils.isEmpty(fs.getHeaders()))
    //                                         h.putAll(fs.getHeaders());
    //                                     if (fs.getBearerToken() != null)
    //                                         h.setBearerAuth(fs.getBearerToken());
    //                                 })
    //                                 .retrieve()
    //                                 .body(byte[].class),
    //                         "no response body"
    //                 );
    //             };
    //             cpio.addFile(file.getPath(), content, file.getMode());
    //         }
    //
    //         cpio.finish();
    //     }
    //
    //     return raw.toByteArray();
    // }
    //
    // // @SneakyThrows
    // // private RpmHeader buildSignatureHeader(byte[] payload) {
    // //     RpmHeader h = new RpmHeader();
    // //     h.addInt32(1000, payload.length);
    // //     String sha256 = DigestUtils.sha256Hex(payload);
    // //     h.addString(273, sha256);
    // //     return h;
    // // }
    // /*
    // @SneakyThrows
    // private RpmHeader buildSignatureHeader(byte[] payload) {
    //     RpmHeader h = new RpmHeader();
    //
    //     // RPMSIGTAG_SIZE
    //     h.addInt32(1000, payload.length);
    //
    //     return h;
    // }
    // */
    // /*@SneakyThrows
    // private RpmHeader buildSignatureHeader(byte[] payload) {
    //     RpmHeader h = new RpmHeader();
    //
    //     // immutable region placeholder
    //     h.addBin(62, new byte[16]);
    //
    //     // RPMSIGTAG_SIZE
    //     h.addInt32(1000, payload.length);
    //
    //     return h;
    // }*/
    //
    // /*private RpmHeader buildMainHeader(PackageConfig config, byte[] payload) {
    //     var meta = config.getMeta();
    //
    //     RpmHeader h = new RpmHeader();
    //
    //     h.addString(1000, meta.getName());
    //     h.addString(1001, meta.getVersion());
    //     h.addString(1002, "1");
    //
    //     h.addString(1004, "package");
    //     h.addString(1005, "package");
    //
    //     h.addInt32(1009, payload.length);
    //
    //     h.addString(1021, "linux");
    //     h.addString(1022, archName(meta.getArch()));
    //
    //     h.addString(1124, "cpio");
    //     h.addString(1125, "gzip");
    //     h.addString(1126, "9");
    //
    //     return h;
    // }*/
    //
    // private RpmHeader buildMainHeader(PackageConfig config, byte[] payload) {
    //     var meta = config.getMeta();
    //
    //     RpmHeader h = new RpmHeader();
    //
    //     h.addString(1000, meta.getName());                 // NAME
    //     h.addString(1001, meta.getVersion());              // VERSION
    //     h.addString(1002, "1");                            // RELEASE
    //
    //     h.addString(1004, "simple-repo");                  // SUMMARY
    //     h.addString(1005, "simple-repo");                  // DESCRIPTION
    //
    //     h.addInt32(1009, payload.length);                  // SIZE
    //
    //     h.addString(1021, "linux");                        // OS
    //     h.addString(1022, archName(meta.getArch()));       // ARCH
    //
    //     h.addString(1124, "cpio");                         // PAYLOADFORMAT
    //     h.addString(1125, "gzip");                         // PAYLOADCOMPRESSOR
    //     h.addString(1126, "9");                            // PAYLOADFLAGS
    //
    //     // REQUIRED FILE METADATA
    //     var files = config.getFiles().getDataFiles();
    //
    //     h.addStringArray(
    //             1118,
    //             files.stream()
    //                     .map(f -> Path.of(f.getPath()).getFileName().toString())
    //                     .toList()
    //     );
    //
    //     h.addStringArray(
    //             1117,
    //             files.stream()
    //                     .map(f -> {
    //                         Path p = Path.of(f.getPath()).getParent();
    //                         return p == null ? "/" : p.toString() + "/";
    //                     })
    //                     .toList()
    //     );
    //
    //     h.addInt32Array(
    //             1028,
    //             files.stream()
    //                     .map(f -> 0)
    //                     .toList()
    //     );
    //
    //     h.addInt16Array(
    //             1030,
    //             files.stream()
    //                     .map(f -> (short) (0100000 | f.getMode()))
    //                     .toList()
    //     );
    //
    //     h.addStringArray(
    //             1039,
    //             files.stream().map(f -> "root").toList()
    //     );
    //
    //     h.addStringArray(
    //             1040,
    //             files.stream().map(f -> "root").toList()
    //     );
    //
    //     return h;
    // }
    //
    // @SneakyThrows
    // private void writeLead(OutputStream out, PackageConfig cfg) {
    //     byte[] lead = new byte[96];
    //
    //     // magic + version
    //     System.arraycopy(LEAD_FIRST_SIX_BYTES, 0, lead, 0, 6);
    //
    //     ByteBuffer b = ByteBuffer.wrap(lead)
    //             .order(ByteOrder.BIG_ENDIAN);
    //
    //     // type
    //     b.position(6);
    //     b.putShort((short) 0); // binary package
    //
    //     // architecture
    //     b.putShort(rpmLeadArch(RpmArch.fromArch(cfg.getMeta().getArch())));
    //
    //     // package name
    //     byte[] name = fileName(cfg).getBytes(StandardCharsets.UTF_8);
    //     System.arraycopy(name, 0, lead, 10, Math.min(name.length, 65));
    //
    //     out.write(lead);
    // }
    //
    // private short rpmLeadArch(RpmArch arch) {
    //     if (arch == null)
    //         return 1;
    //
    //     return 1;
    //     // return switch (arch) {
    //     //     case x86_64 -> 1;    // x86_64 historically still uses 1 in lead
    //     //     case aarch64 -> 183; // aarch64
    //     //     case armhfp -> throw new UnsupportedOperationException();
    //     //     case riscv64 -> throw new UnsupportedOperationException();
    //     // };
    // }
    //
    // static class RpmHeader {
    //     List<Entry> entries = new ArrayList<>();
    //     ByteArrayOutputStream store = new ByteArrayOutputStream();
    //
    //     void addBin(int tag, byte[] value) {
    //         entries.add(
    //                 Entry.builder()
    //                         .tag(tag)
    //                         .type(7)
    //                         .offset(store.size())
    //                         .count(value.length)
    //                         .build()
    //         );
    //
    //         store.writeBytes(value);
    //     }
    //
    //     void addString(int tag, String value) {
    //         entries.add(Entry.builder().tag(tag).type(6).offset(store.size()).count(1).build());
    //
    //         byte[] bytes = (value + "\0").getBytes(StandardCharsets.UTF_8);
    //         store.writeBytes(bytes);
    //     }
    //
    //     void addInt32(int tag, int value) {
    //         entries.add(Entry.builder().tag(tag).type(4).offset(store.size()).count(1).build());
    //         ByteBuffer b = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
    //         b.putInt(value);
    //         store.writeBytes(b.array());
    //     }
    //
    //     @SneakyThrows
    //     void write(OutputStream out) {
    //         byte[] storeBytes = store.toByteArray();
    //
    //         ByteBuffer h = ByteBuffer.allocate(16)
    //                 .order(ByteOrder.BIG_ENDIAN);
    //
    //         // h.put(HEADER_MAGIC);       // 3 bytes
    //         // h.put((byte) 1);           // version
    //
    //         h.put((byte) 0x8e);
    //         h.put((byte) 0xad);
    //         h.put((byte) 0xe8);
    //         h.put((byte) 0x01);
    //
    //         h.putInt(0);               // reserved
    //         h.putInt(entries.size());  // index count
    //         h.putInt(storeBytes.length);
    //
    //         out.write(h.array());
    //
    //         for (Entry e : entries) {
    //             ByteBuffer eb = ByteBuffer.allocate(16)
    //                     .order(ByteOrder.BIG_ENDIAN);
    //
    //             eb.putInt(e.tag);
    //             eb.putInt(e.type);
    //             eb.putInt(e.offset);
    //             eb.putInt(e.count);
    //
    //             out.write(eb.array());
    //         }
    //
    //         out.write(storeBytes);
    //
    //         // int padding = (8 - (storeBytes.length % 8)) % 8;
    //         int totalSize =
    //                 16 +
    //                         (entries.size() * 16) +
    //                         storeBytes.length;
    //
    //         int padding = (8 - (totalSize % 8)) % 8;
    //
    //         out.write(new byte[padding]);
    //     }
    //
    //     void addStringArray(int tag, List<String> values) {
    //         entries.add(
    //                 Entry.builder()
    //                         .tag(tag)
    //                         .type(8)
    //                         .offset(store.size())
    //                         .count(values.size())
    //                         .build()
    //         );
    //
    //         for (String v : values) {
    //             store.writeBytes((v + "\0").getBytes(StandardCharsets.UTF_8));
    //         }
    //     }
    //
    //     void addInt32Array(int tag, List<Integer> values) {
    //         entries.add(
    //                 Entry.builder()
    //                         .tag(tag)
    //                         .type(4)
    //                         .offset(store.size())
    //                         .count(values.size())
    //                         .build()
    //         );
    //
    //         ByteBuffer b = ByteBuffer.allocate(values.size() * 4)
    //                 .order(ByteOrder.BIG_ENDIAN);
    //
    //         for (Integer v : values) {
    //             b.putInt(v);
    //         }
    //
    //         store.writeBytes(b.array());
    //     }
    //
    //     void addInt16Array(int tag, List<Short> values) {
    //         entries.add(
    //                 Entry.builder()
    //                         .tag(tag)
    //                         .type(3)
    //                         .offset(store.size())
    //                         .count(values.size())
    //                         .build()
    //         );
    //
    //         ByteBuffer b = ByteBuffer.allocate(values.size() * 2)
    //                 .order(ByteOrder.BIG_ENDIAN);
    //
    //         for (Short v : values) {
    //             b.putShort(v);
    //         }
    //
    //         store.writeBytes(b.array());
    //     }
    // }
    //
    // @Builder
    // @AllArgsConstructor
    // @NoArgsConstructor
    // @Data
    // @Accessors(chain = true)
    // static class Entry {
    //     int tag;
    //     int type;
    //     int offset;
    //     int count;
    // }
    //
    // @RequiredArgsConstructor
    // static class CpioWriter {
    //     private final OutputStream out;
    //
    //     /*
    //     @SneakyThrows
    //     void addFile(String name, byte[] content, int mode) {
    //         writeHeader(name, content.length, mode);
    //         out.write(content);
    //         pad(content.length);
    //     }
    //     */
    //     @SneakyThrows
    //     void addFile(String name, byte[] content, int mode) {
    //         int finalMode = 0100000 | mode;
    //         writeHeader(name, content.length, finalMode);
    //         out.write(content);
    //         pad(content.length);
    //     }
    //
    //     @SneakyThrows
    //     void finish() {
    //         writeHeader("TRAILER!!!", 0, 0);
    //         pad(0);
    //     }
    //
    //     @SneakyThrows
    //     private void writeHeader(String name, int size, int mode) {
    //         String header = "070701"
    //                 + hex(0)
    //                 + hex(mode)
    //                 + hex(0)
    //                 + hex(0)
    //                 + hex(1)
    //                 + hex((int) (System.currentTimeMillis() / 1000))
    //                 + hex(size)
    //                 + hex(0)
    //                 + hex(0)
    //                 + hex(0)
    //                 + hex(0)
    //                 + hex(name.length() + 1)
    //                 + hex(0);
    //
    //         out.write(header.getBytes(StandardCharsets.US_ASCII));
    //         out.write(name.getBytes(StandardCharsets.UTF_8));
    //         out.write(0);
    //
    //         pad(110 + name.length() + 1);
    //     }
    //
    //     @SneakyThrows
    //     private void pad(int size) {
    //         int pad = (4 - (size % 4)) % 4;
    //         out.write(new byte[pad]);
    //     }
    //
    //     private String hex(int v) {
    //         return String.format("%08x", v);
    //     }
    // }
}
