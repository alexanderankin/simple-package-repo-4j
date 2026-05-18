package simple.repo.rpm.model;

import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry;
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream;
import org.apache.commons.compress.archivers.cpio.CpioConstants;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.compress.compressors.zstandard.ZstdUtils;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import simple.repo.rpm.RpmTags.RpmTagType;
import simple.repo.rpm.model.Signature.Index;
import simple.repo.rpm.model.Signature.Index.IndexEntry;
import simple.repo.rpm.model.Signature.Intro;
import tools.jackson.databind.util.ByteBufferBackedInputStream;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SignatureTest extends SampleData {

    @Test
    void testIndex() {
        var bb = ByteBuffer.wrap(readBytes(testFileNames().getFirst()));
        var intro = Intro.of(bb.slice(Intro.OFFSET, Intro.SIZE));
        var index = Index.of(
                intro,
                bb.slice(Index.OFFSET, intro.indexSize())
        );

        var expected = new Index()
                .setIntro(new Intro().setEntryCount(8).setDataLength(4260))
                .setEntries(List.of(
                        new IndexEntry().setTag(62).setType(RpmTagType.RPM_BIN_TYPE).setOffset(4244).setCount(16),
                        new IndexEntry().setTag(268).setType(RpmTagType.RPM_BIN_TYPE).setOffset(0).setCount(566),
                        new IndexEntry().setTag(269).setType(RpmTagType.RPM_STRING_TYPE).setOffset(566).setCount(1),
                        new IndexEntry().setTag(273).setType(RpmTagType.RPM_STRING_TYPE).setOffset(607).setCount(1),
                        new IndexEntry().setTag(1000).setType(RpmTagType.RPM_INT32_TYPE).setOffset(672).setCount(1),
                        new IndexEntry().setTag(1004).setType(RpmTagType.RPM_BIN_TYPE).setOffset(676).setCount(16),
                        new IndexEntry().setTag(1007).setType(RpmTagType.RPM_INT32_TYPE).setOffset(692).setCount(1),
                        new IndexEntry().setTag(1008).setType(RpmTagType.RPM_BIN_TYPE).setOffset(696).setCount(3548)));

        assertEquals(expected, index);
    }

    @SneakyThrows
    @Test
    void testSignature() {
        var bb = ByteBuffer.wrap(readBytes(testFileNames().getFirst()));
        // var bb = ByteBuffer.wrap(Files.readAllBytes(Path.of("/Users/toor/IdeaProjects/simple-package-repo-4j/repo-core/src/main/resources/htop-3.3.0-5.el10_0.x86_64.rpm")));
        var lead = Lead.parse(bb);
        var intro = Intro.parse(bb);
        var index = Index.parse(intro, bb);
        var sig = Signature.parseSignature(index, bb);
        System.out.println(sig);
        System.out.println(JsonMapper.builder().build().writeValueAsString(sig));
        sig.entries.stream()
                .map(se -> {
                    return Map.entry(se, smartToString(se.type, se.copyByteArray()));
                })
                .forEach(System.out::println);

        bb.position(bb.position() + 4);

        var headerIntro = Intro.parse(bb);
        var headerIndex = Index.parse(headerIntro, bb);
        System.out.println("headerIntro: " + headerIntro);
        System.out.println("headerIndex: " + headerIndex);

        Signature headerSignature = Signature.parseHeader(headerIndex, bb);
        System.out.println("headerSignature: " + JsonMapper.builder().build().writeValueAsString(headerSignature));

        byte[] cpIoMagic = new byte[6];
        bb.get(cpIoMagic);
        assertTrue(ZstdUtils.matches(cpIoMagic, 4));

        // var beforeDecompress = bb.position();
        // var compressorInputStream = new CompressorStreamFactory().createCompressorInputStream(new BufferedInputStream(new ByteBufferBackedInputStream(bb)));
        // var decompressed = compressorInputStream.readAllBytes();
        // System.out.println("decompressed: " + decompressed);
        //
        // var cpioArchiveInputStream = new CpioArchiveInputStream(new ByteArrayInputStream(decompressed));
        // CpioArchiveEntry nextEntry;
        // while (null != (nextEntry = cpioArchiveInputStream.getNextEntry())) {
        //     var entryBytes = cpioArchiveInputStream.readAllBytes();
        //     System.out.println("nextEntry: " + stringify(nextEntry) + ", entryBytes: " + entryBytes.length);
        // }
        //
        // var bytes = DigestUtils.sha1(Arrays.copyOfRange(bb.array(), lead.SIZE + intro.SIZE + intro.indexSize() + intro.getDataLength(), bb.array().length));
        // System.out.println(Hex.encodeHexString(bytes));
        // System.out.println();
    }

    @SneakyThrows
    private String stringify(CpioArchiveEntry nextEntry) {
        var nextEntryMap = Map.ofEntries(
                Map.entry("alignmentBoundary", nextEntry.getAlignmentBoundary()),
                Map.entry("dataPadCount", nextEntry.getDataPadCount()),
                Map.entry("format", nextEntry.getFormat()),
                Map.entry("gID", nextEntry.getGID()),
                Map.entry("headerSize", nextEntry.getHeaderSize()),
                Map.entry("inode", nextEntry.getInode()),
                Map.entry("lastModifiedDate", nextEntry.getLastModifiedDate()),
                Map.entry("mode", nextEntry.getMode()),
                Map.entry("name", nextEntry.getName()),
                Map.entry("numberOfLinks", nextEntry.getNumberOfLinks()),
                Map.entry("size", nextEntry.getSize()),
                Map.entry("time", nextEntry.getTime()),
                Map.entry("uID", nextEntry.getUID()),

                // new format
                Map.entry("chksum", nextEntry.getChksum()),
                Map.entry("deviceMaj", nextEntry.getDeviceMaj()),
                Map.entry("deviceMin", nextEntry.getDeviceMin()),
                Map.entry("remoteDeviceMaj", nextEntry.getRemoteDeviceMaj()),
                Map.entry("remoteDeviceMin", nextEntry.getRemoteDeviceMin())
        );
        return JsonMapper.builder().build().writeValueAsString(nextEntryMap);
    }

    private String smartToString(RpmTagType tagType, byte[] content) {
        return switch (tagType) {
            case RPM_CHAR_TYPE, RPM_BIN_TYPE, RPM_STRING_TYPE, RPM_STRING_ARRAY_TYPE -> {
                var charsetEncoder = StandardCharsets.US_ASCII.newEncoder();

                for (byte b : content) {
                    var asInt = b & 0xFF;
                    var asChar = (char) (asInt);

                    if (Character.isISOControl(asInt) || !charsetEncoder.canEncode(asChar))
                        yield "hex:" + Hex.encodeHexString(content);
                }

                yield new String(content, StandardCharsets.US_ASCII);
            }
            case RPM_INT8_TYPE -> String.valueOf(Byte.toUnsignedInt(content[0]));
            case RPM_INT16_TYPE -> String.valueOf(ByteBuffer.wrap(content).asShortBuffer().get());
            case RPM_INT32_TYPE -> String.valueOf(ByteBuffer.wrap(content).asIntBuffer().get());
            case RPM_INT64_TYPE -> String.valueOf(ByteBuffer.wrap(content).asLongBuffer().get());
            case null, default -> throw new UnsupportedOperationException();
        };
    }

    @Nested
    class IntroTest {
        @Test
        void testIntro() {
            var intro = Intro.of(ByteBuffer.wrap(readBytes(testFileNames().getFirst())).slice(Intro.OFFSET, Intro.SIZE));
            assertEquals(new Intro().setEntryCount(8).setDataLength(4260), intro);
        }
    }
}
