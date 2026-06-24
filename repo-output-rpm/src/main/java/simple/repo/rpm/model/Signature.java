package simple.repo.rpm.model;

import lombok.Data;
import lombok.experimental.Accessors;
import simple.repo.rpm.RpmTags;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Data
@Accessors(chain = true)
public class Signature {
    // Index index;
    List<SignatureEntry> entries;

    public static Signature of(Index index, ByteBuffer dataBuffer, TagType tagType) {
        if (index.intro.dataLength != dataBuffer.remaining()) {
            throw new IllegalArgumentException("intro buffer size error");
        }

        var signature = new Signature();
        record SignatureEntryWithOffset(SignatureEntry se, int offset) {
        }
        var entries = new ArrayList<SignatureEntryWithOffset>(index.entries.size());

        for (var entry : index.entries) {
            RpmTags anEnum = switch (tagType) {
                case SIGNATURE -> toSigEnum(entry.tag);
                case HEADER -> toHeaderEnum(entry.tag);
            };
            var e = new SignatureEntry().setTag(anEnum).setType(entry.type);
            entries.add(new SignatureEntryWithOffset(e, entry.offset));

            var contentLength = switch (entry.type) {
                case RPM_CHAR_TYPE, RPM_BIN_TYPE -> entry.count;
                case RPM_INT8_TYPE -> 1;
                case RPM_INT16_TYPE -> 2;
                case RPM_INT32_TYPE -> 4;
                case RPM_INT64_TYPE -> 8;
                case RPM_STRING_TYPE, RPM_STRING_ARRAY_TYPE, RPM_I18NSTRING_TYPE -> {
                    var remainingCount = entry.count;
                    var length = 0;
                    var position = entry.offset;
                    while (remainingCount-- > 0) {
                        while (dataBuffer.get(position++) != '\0') {
                            length++;
                        }
                    }
                    yield length;
                }
                default -> throw new UnsupportedOperationException("unsupported type " + entry.type);
            };

            e.content = dataBuffer.slice(entry.offset, contentLength);
        }
        signature.setEntries(entries.stream()
                .sorted(Comparator.comparing(SignatureEntryWithOffset::offset))
                .map(SignatureEntryWithOffset::se)
                .toList());

        return signature;
    }

    private static RpmTags.RpmTag toHeaderEnum(int tag) {
        return Arrays.stream(RpmTags.RpmTag.values())
                .filter(e -> e.getTagValue() == tag)
                .findFirst().orElse(null);
    }

    private static RpmTags.SignatureTag toSigEnum(int tag) {
        return Arrays.stream(RpmTags.SignatureTag.values())
                .filter(e -> e.getTagValue() == tag)
                .findFirst().orElse(null);
    }

    public static Signature parseSignature(Index index, ByteBuffer bb) {
        var signature = of(index, bb.slice(bb.position(), index.intro.dataLength), TagType.SIGNATURE);
        bb.position(bb.position() + index.intro.dataLength);
        // The Signature uses the same underlying data structure as the Header, but is zero-padded to a multiple of 8 bytes.
        // https://rpm.org/docs/6.1.x/manual/format_v4.html
        bb.position(bb.position() + (8 - (bb.position() % 8)));
        return signature;
    }

    public static Signature parseHeader(Index index, ByteBuffer bb) {
        var signature = of(index, bb.slice(bb.position(), index.intro.dataLength), TagType.HEADER);
        bb.position(bb.position() + index.intro.dataLength);
        return signature;
    }

    public int totalLength() {
        int offset = 0;
        for (SignatureEntry entry : entries) {
            offset += headerDataAlignmentOffset(entry.type, offset);
            offset += entry.content.capacity();
            if (entry.type == RpmTags.RpmTagType.RPM_STRING_TYPE ||
                    entry.type == RpmTags.RpmTagType.RPM_STRING_ARRAY_TYPE ||
                    entry.type == RpmTags.RpmTagType.RPM_I18NSTRING_TYPE) {
                offset += 1;
            }
        }
        return offset;
    }

    public List<Index.IndexEntry> toIndexEntries() {
        throw new UnsupportedOperationException();
    }

    public Index toIndex() {
        return new Index()
                .setIntro(new Intro()
                        .setEntryCount(entries.size())
                        .setDataLength(totalLength()))
                .setEntries(toIndexEntries());
    }

    public byte[] toByteArray() {
        return toByteBuffer().array();
    }

    public ByteBuffer toByteBuffer() {
        return toByteBuffer(ByteBuffer.allocate(totalLength()));
    }

    public ByteBuffer toByteBuffer(ByteBuffer byteBuffer) {
        int offset = 0;
        for (SignatureEntry entry : entries) {
            int alignmentOffset = headerDataAlignmentOffset(entry.type, offset);

            for (int i = 0; i < alignmentOffset; i++) {
                byteBuffer.put((byte) 0);
            }
            offset += alignmentOffset;

            if (entry.content.capacity() > byteBuffer.remaining())
                System.out.println();
            byteBuffer.put(entry.content);
            offset += entry.content.capacity();

            if (entry.type == RpmTags.RpmTagType.RPM_STRING_TYPE ||
                    entry.type == RpmTags.RpmTagType.RPM_STRING_ARRAY_TYPE ||
                    entry.type == RpmTags.RpmTagType.RPM_I18NSTRING_TYPE) {
                byteBuffer.put((byte) 0);
                offset += 1;
            }
        }
        return byteBuffer;
    }

    /**
     * The integer types are aligned to appropriate byte boundaries,
     * so that the data of INT64 type starts on an 8 byte boundary,
     * INT32 type starts on a 4 byte boundary,
     * and an INT16 type starts on a 2 byte boundary.
     * <p>
     * Each string is null terminated,
     * the strings in a STRING_ARRAY are also null-terminated
     * and are placed one after another.
     * <p>
     * The size of integral types is a straightforward multiplication
     * based on the type size in the above table.
     * but the string sizes must be determined by walking the data
     * looking for the null-bytes while taking care to stay within bounds.
     *
     * @param type   type of the header about to be inserted
     * @param offset offset so far
     * @return number of bytes to insert before this header to align it
     * @see <a href=https://rpm-software-management.github.io/rpm/manual/format_header.html#data>rpm manual: format_header: Data</a>
     */
    private int headerDataAlignmentOffset(RpmTags.RpmTagType type, int offset) {
        return switch (type) {
            case RPM_INT16_TYPE -> (2 - (offset % 2)) % 2;
            case RPM_INT32_TYPE -> (4 - (offset % 4)) % 4;
            case RPM_INT64_TYPE -> (8 - (offset % 8)) % 8;
            default -> 0;
        };
    }

    public enum TagType {
        SIGNATURE,
        HEADER,
    }

    @Data
    @Accessors(chain = true)
    public static class SignatureEntry {
        RpmTags.RpmTagType type;
        RpmTags tag;
        ByteBuffer content;

        public int findLength() {
            throw new UnsupportedOperationException();
        }

        public byte[] copyByteArray() {
            var old = content.position();
            content.position(0);
            byte[] copy = new byte[content.remaining()];
            content.get(copy);
            content.position(old);
            return copy;
        }
    }

    @Data
    @Accessors(chain = true)
    public static class Index {
        public static final int OFFSET = Intro.OFFSET + Intro.SIZE;
        private static final Map<Integer, RpmTags.RpmTagType> TT = Arrays.stream(RpmTags.RpmTagType.values())
                .collect(Collectors.toMap(RpmTags.RpmTagType::getValue, Function.identity()));

        Intro intro;
        List<IndexEntry> entries;

        public static Index of(Intro intro, ByteBuffer byteBuffer) {
            var entryCount = intro.getEntryCount();
            if (byteBuffer.remaining() != intro.indexSize()) {
                throw new IllegalArgumentException("intro.entryCount != entryCount * 16");
            }
            List<IndexEntry> entries = new ArrayList<>(entryCount);
            for (int i = 0; i < entryCount; i++) {
                byte[] bytes = new byte[16];
                byteBuffer.get(bytes);
                IntBuffer intBuffer = ByteBuffer.wrap(bytes).asIntBuffer();
                var entry = new IndexEntry();
                entry.tag = intBuffer.get();
                entry.type = Objects.requireNonNull(TT.get(intBuffer.get()));
                entry.offset = intBuffer.get();
                entry.count = intBuffer.get();

                entries.add(entry);
            }
            return new Index()
                    .setIntro(intro)
                    .setEntries(entries);
        }

        public static Index parse(Intro intro, ByteBuffer bb) {
            var index = of(intro, bb.slice(bb.position(), intro.indexSize()));
            bb.position(bb.position() + intro.indexSize());
            return index;
        }

        public byte[] toByteArray() {
            return toByteBuffer().array();
        }

        public ByteBuffer toByteBuffer() {
            return toByteBuffer(ByteBuffer.allocate(intro.indexSize()));
        }

        public ByteBuffer toByteBuffer(ByteBuffer byteBuffer) {
            if (byteBuffer.remaining() != intro.indexSize()) {
                throw new IllegalArgumentException("intro.entryCount != entryCount * 16");
            }
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.get(i);
                byteBuffer.putInt(entry.tag);
                byteBuffer.putInt(entry.type.getValue());
                byteBuffer.putInt(entry.offset);
                byteBuffer.putInt(entry.count);
            }
            return byteBuffer;
        }

        @Data
        @Accessors(chain = true)
        public static class IndexEntry {
            int tag;
            RpmTags.RpmTagType type;
            int offset, count;
        }
    }

    @Data
    @Accessors(chain = true)
    public static class Intro {
        public static final int SIZE = 16;
        public static final int OFFSET = Lead.OFFSET + Lead.SIZE;
        private static final byte[] MAGIC = {
                (byte) 0x8e, (byte) 0xad, (byte) 0xe8, (byte) 0x01,
                (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
        };

        int entryCount;
        int dataLength;

        public static Intro of(ByteBuffer buffer) {
            if (buffer.remaining() != SIZE) {
                throw new IllegalArgumentException("invalid length");
            }
            Intro intro = new Intro();
            byte[] magic = new byte[MAGIC.length];
            buffer.get(magic);
            if (!Arrays.equals(magic, MAGIC)) {
                throw new IllegalArgumentException("magic not match");
            }
            intro.entryCount = buffer.getInt();
            intro.dataLength = buffer.getInt();
            if (buffer.remaining() != 0) {
                throw new IllegalStateException();
            }
            return intro;
        }

        public static Intro parse(ByteBuffer bb) {
            var intro = of(bb.slice(bb.position(), Intro.SIZE));
            bb.position(bb.position() + SIZE);
            return intro;
        }

        public int indexSize() {
            return entryCount * 16;
        }

        public int totalSize() {
            return SIZE + indexSize() + dataLength;
        }

        public byte[] toByteArray() {
            return toByteBuffer().array();
        }

        public ByteBuffer toByteBuffer() {
            return toByteBuffer(ByteBuffer.allocate(SIZE));
        }

        public ByteBuffer toByteBuffer(ByteBuffer byteBuffer) {
            if (byteBuffer.remaining() != SIZE) {
                throw new IllegalArgumentException("invalid length");
            }
            byteBuffer.put(MAGIC);
            byteBuffer.putInt(entryCount);
            byteBuffer.putInt(dataLength);
            return byteBuffer;
        }
    }
}
