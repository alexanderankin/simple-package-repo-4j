package simple.repo.rpm;

import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry;
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.junit.jupiter.api.Test;
import simple.repo.rpm.model.SampleData;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RpmPackageBuilderL1Test extends SampleData {
    @SneakyThrows
    @Test
    void scratchWork() {
        var rpmData = readBytes("yum-utils-4.7.0-9.el10.noarch.rpm");

        var rpmDataBuffer = ByteBuffer.wrap(rpmData);
        var rpmDataHeaderIntro = rpmDataBuffer.slice(96, 96 + 16).asIntBuffer();
        var numberOfEntries = rpmDataHeaderIntro.get(2);
        System.out.println("numberOfEntries: " + numberOfEntries);
        System.out.println("dataLength: " + rpmDataHeaderIntro.get(3));

        var rpmDataHeaderEntries = rpmDataBuffer.slice(96 + 16, /*96 +*/ (16 * numberOfEntries));
        var headerEntryInts = IntStream.range(0, numberOfEntries).mapToObj(_ -> {
            byte[] next = new byte[16];
            rpmDataHeaderEntries.get(next);
            ByteBuffer nextBuffer = ByteBuffer.wrap(next);
            List<Integer> byteList = new ArrayList<>(next.length);
            byteList.add(nextBuffer.getInt());
            byteList.add(nextBuffer.getInt());
            byteList.add(nextBuffer.getInt());
            byteList.add(nextBuffer.getInt());
            return byteList;
        }).toList();
        System.out.println("headerEntryInts: " + headerEntryInts);

        var signatureTagMap = Arrays.stream(RpmTags.SignatureTag.values())
                .collect(Collectors.toMap(RpmTags.SignatureTag::getTagValue, Function.identity()));
        var typeTagMap = Arrays.stream(RpmTags.RpmTagType.values())
                .collect(Collectors.toMap(RpmTags.RpmTagType::getValue, Function.identity()));

        var headerEntries = headerEntryInts.stream()
                .map(i -> {
                    return new HeaderEntry(signatureTagMap.get(i.getFirst()), typeTagMap.get(i.get(1)), i.get(2), i.get(3));
                })
                .toList();

        System.out.println(headerEntries.stream().map(String::valueOf).collect(Collectors.joining("\n")));

        var rpmDataHeaderData = rpmDataBuffer.slice(96 + 16 + (16 * numberOfEntries),
                rpmDataBuffer.capacity() - (96 + 16 + (16 * numberOfEntries)));

        var entryContentMap = headerEntries.stream()
                .map(e -> new HeaderEntryWithContent(e, rpmDataHeaderData.slice(e.offset, lengthOf(rpmDataHeaderData, e))))
                .toList();

        System.out.println(entryContentMap.stream().map(HeaderEntryWithContent::smartToString).collect(Collectors.joining("\n")));
        for (var e : entryContentMap) {
            assertEquals(lengthOf(rpmDataHeaderData, e.headerEntry), e.content.length);
        }

        var headerTop = rpmDataHeaderData.arrayOffset() + entryContentMap.stream().mapToInt(i -> lengthOf(rpmDataHeaderData, i.headerEntry)).sum();
        // var rest = rpmDataBuffer.slice(headerTop, rpmDataBuffer.capacity() - headerTop);

        // var restArray = HeaderEntryWithContent.bbToArray(rest);
        //
        // System.out.println(Hex.encodeHexString(restArray));

        var bais = new ByteArrayInputStream(rpmData, headerTop, rpmData.length - headerTop);
        var gis = new CompressorStreamFactory().createCompressorInputStream(bais);
        var gunzipped = gis.readAllBytes();
        var cpioInputStream = new CpioArchiveInputStream(new ByteArrayInputStream(gunzipped));
        CpioArchiveEntry nextEntry;
        while (null != (nextEntry = cpioInputStream.getNextEntry())) {
            var bytes = cpioInputStream.readAllBytes();
            System.out.println("nextEntry: " + nextEntry);
            System.out.println("bytes.length: " + bytes.length);
        }
    }

    private int lengthOf(ByteBuffer rpmDataHeaderData, HeaderEntry e) {
        return switch (e.tagType) {
            case RPM_CHAR_TYPE, RPM_INT8_TYPE, RPM_BIN_TYPE -> e.count;
            case RPM_INT16_TYPE -> e.count * 2;
            case RPM_INT32_TYPE -> e.count * 4;
            case RPM_INT64_TYPE -> e.count * 8;
            case RPM_STRING_TYPE, RPM_STRING_ARRAY_TYPE, RPM_I18NSTRING_TYPE -> {
                var length = 0;
                var oldPosition = rpmDataHeaderData.position();
                rpmDataHeaderData.position(e.offset);
                var count = e.count;
                while (count-- > 0) {
                    while (rpmDataHeaderData.get() != '\0')
                        length++;
                }
                rpmDataHeaderData.position(oldPosition);
                yield length;
            }
            default -> throw new UnsupportedOperationException();
        };
    }

    record HeaderEntry(RpmTags.SignatureTag signatureTag, RpmTags.RpmTagType tagType, int offset, int count) {
    }

    record HeaderEntryWithContent(HeaderEntry headerEntry, byte[] content) {
        HeaderEntryWithContent(HeaderEntry headerEntry, ByteBuffer byteBuffer) {
            this(headerEntry, bbToArray(byteBuffer));
        }

        static byte[] bbToArray(ByteBuffer byteBuffer) {
            var array = new byte[byteBuffer.capacity()];
            var old = byteBuffer.position();
            byteBuffer.position(0);
            byteBuffer.get(array);
            byteBuffer.position(old);
            return array;
        }

        String smartToString() {
            return "HeaderEntryWithContent(headerEntry=" + headerEntry + ", content=" + smartToString(content) + ")";
        }

        private String smartToString(byte[] content) {
            return switch (headerEntry.tagType) {
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
    }

}
