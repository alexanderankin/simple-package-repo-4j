package simple.repo.rpm.model;

import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry;
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.compress.compressors.zstandard.ZstdUtils;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import simple.repo.rpm.RpmTags;
import simple.repo.rpm.RpmTags.RpmTagType;
import simple.repo.rpm.model.Signature.Index;
import simple.repo.rpm.model.Signature.Index.IndexEntry;
import simple.repo.rpm.model.Signature.Intro;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.util.ByteBufferBackedInputStream;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureTest extends SampleData {

    @Test
    void parsedHeadersSerializeBackToTheirOriginalIndexAndData() {
        var bb = ByteBuffer.wrap(readBytes("cloud-utils-0.33-13.fc44.noarch.rpm"));
        Lead.parse(bb);

        var signatureIntro = Intro.parse(bb);
        var signatureIndex = Index.parse(signatureIntro, bb);
        var signatureData = new byte[signatureIntro.getDataLength()];
        bb.get(bb.position(), signatureData);
        var signature = Signature.parseSignature(signatureIndex, bb);

        assertEquals(signatureIntro, signature.toIndex().getIntro());
        assertEquals(signatureIndex.getEntries(), signature.toIndexEntries());
        assertArrayEquals(signatureData, signature.toByteArray());

        var headerIntro = Intro.parse(bb);
        var headerIndex = Index.parse(headerIntro, bb);
        var headerData = new byte[headerIntro.getDataLength()];
        bb.get(bb.position(), headerData);
        var header = Signature.parseHeader(headerIndex, bb);

        assertEquals(headerIntro, header.toIndex().getIntro());
        assertEquals(headerIndex.getEntries(), header.toIndexEntries());
        assertArrayEquals(headerData, header.toByteArray());
    }

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
    void test() {

    }

    @SneakyThrows
    @Test
    void testSignature() {
        var bb = ByteBuffer.wrap(readBytes("cloud-utils-0.33-13.fc44.noarch.rpm"));
        // var bb = ByteBuffer.wrap(Files.readAllBytes(Path.of("/Users/toor/IdeaProjects/simple-package-repo-4j/repo-core/src/main/resources/htop-3.3.0-5.el10_0.x86_64.rpm")));
        var lead = Lead.parse(bb);
        var intro = Intro.parse(bb);
        var index = Index.parse(intro, bb);
        var sig = Signature.parseSignature(index, bb);
        System.out.println("index tags: " + sig.entries.stream().map(Signature.SignatureEntry::getTag).toList());
        /*
        System.out.println(sig);
        System.out.println(JsonMapper.builder().build().writeValueAsString(sig));
        sig.entries.stream()
                .map(se -> {
                    return Map.entry(se, smartToString(se.type, se.copyByteArray()));
                })
                .forEach(System.out::println);
        */

        var beforeHeader = bb.position();
        var headerIntro = Intro.parse(bb);
        var headerIndex = Index.parse(headerIntro, bb);

        /*
        System.out.println("headerIntro: " + headerIntro);
        System.out.println("headerIndex: " + headerIndex);
        */

        Signature headerSignature = Signature.parseHeader(headerIndex, bb);
        System.out.println("header tags: " + headerSignature.entries.stream().map(Signature.SignatureEntry::getTag).toList());
        /*
        System.out.println("headerSignature: " + JsonMapper.builder().build().writeValueAsString(headerSignature));
        */

        {
            byte[] magic = new byte[4];
            bb.get(bb.position(), magic);
            assertTrue(ZstdUtils.matches(magic, 4));
        }

        var decompressed = new CompressorStreamFactory().createCompressorInputStream(new BufferedInputStream(new ByteBufferBackedInputStream(bb))).readAllBytes();

        var cpioArchiveInputStream = new CpioArchiveInputStream(new ByteArrayInputStream(decompressed));
        CpioArchiveEntry nextEntry;
        while (null != (nextEntry = cpioArchiveInputStream.getNextEntry())) {
            var entryBytes = cpioArchiveInputStream.readAllBytes();
            System.out.println("nextEntry: " + stringify(nextEntry) + ", entryBytes: " + entryBytes.length);
        }

        var from = lead.SIZE + ((intro.SIZE + intro.indexSize() + intro.getDataLength() + 7) & ~7);
        var to = lead.SIZE + ((intro.SIZE + intro.indexSize() + intro.getDataLength() + 7) & ~7) + headerIntro.SIZE + headerIntro.indexSize() + headerIntro.getDataLength();
        assertEquals(beforeHeader, from);
        assertEquals(beforeHeader + headerIntro.totalSize(), to);
        assertEquals(new String(sig.entries.stream().filter(e -> e.tag == RpmTags.SignatureTag.SHA1).findAny().orElseThrow().copyByteArray()), DigestUtils.sha1Hex(new ByteArrayInputStream(bb.array(), beforeHeader, headerIntro.totalSize())));
        assertEquals(new String(sig.entries.stream().filter(e -> e.tag == RpmTags.SignatureTag.SHA256).findAny().orElseThrow().copyByteArray()), DigestUtils.sha256Hex(new ByteArrayInputStream(bb.array(), beforeHeader, headerIntro.totalSize())));

        assertEquals(Hex.encodeHexString(sig.entries.stream().filter(e -> e.tag == RpmTags.SignatureTag.MD5).findAny().orElseThrow().copyByteArray()), DigestUtils.md5Hex(new ByteArrayInputStream(bb.array(), beforeHeader, bb.array().length)));
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
            case RPM_CHAR_TYPE, RPM_BIN_TYPE, RPM_STRING_TYPE, RPM_STRING_ARRAY_TYPE, RPM_I18NSTRING_TYPE -> {
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
