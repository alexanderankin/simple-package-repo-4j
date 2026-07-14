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
                case RPM_INT8_TYPE -> entry.count;
                case RPM_INT16_TYPE -> Math.multiplyExact(entry.count, 2);
                case RPM_INT32_TYPE -> Math.multiplyExact(entry.count, 4);
                case RPM_INT64_TYPE -> Math.multiplyExact(entry.count, 8);
                case RPM_STRING_TYPE, RPM_STRING_ARRAY_TYPE, RPM_I18NSTRING_TYPE -> {
                    var remainingCount = entry.count;
                    var position = entry.offset;
                    while (remainingCount-- > 0) {
                        while (dataBuffer.get(position++) != '\0') {
                            // Find each value's terminator. Array separators remain in
                            // content, while the final terminator is restored on writing.
                        }
                    }
                    yield position - entry.offset - 1;
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
        bb.position(bb.position() + ((8 - (bb.position() % 8)) % 8));
        return signature;
    }

    public static Signature parseHeader(Index index, ByteBuffer bb) {
        var signature = of(index, bb.slice(bb.position(), index.intro.dataLength), TagType.HEADER);
        bb.position(bb.position() + index.intro.dataLength);
        return signature;
    }

    public int totalLength() {
        return EntryLayout.layoutEntries(entries, 0).stream()
                .mapToInt(EntryLayout::endOffset)
                .max()
                .orElse(0);
    }

    public List<Index.IndexEntry> toIndexEntries() {
        return new Index()
                .setIntro(new Intro())
                .setEntries(new ArrayList<>())
                .addEntries(entries)
                .getEntries();
    }

    public Index toIndex() {
        return new Index()
                .setIntro(new Intro())
                .setEntries(new ArrayList<>())
                .addEntries(entries);
    }

    public byte[] toByteArray() {
        return toByteBuffer().array();
    }

    public ByteBuffer toByteBuffer() {
        return toByteBuffer(ByteBuffer.allocate(totalLength()));
    }

    public ByteBuffer toByteBuffer(ByteBuffer byteBuffer) {
        if (byteBuffer.remaining() != totalLength()) {
            throw new IllegalArgumentException("invalid data buffer length");
        }
        for (EntryLayout layout : EntryLayout.layoutEntries(entries, 0)) {
            for (int i = 0; i < layout.padding(); i++) {
                byteBuffer.put((byte) 0);
            }
            byteBuffer.put(layout.entry().copyByteArray());
            if (EntryLayout.isString(layout.entry().type)) {
                byteBuffer.put((byte) 0);
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
    private static int headerDataAlignmentOffset(RpmTags.RpmTagType type, int offset) {
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
            return EntryLayout.layoutEntries(List.of(this), 0).getFirst().count();
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
            for (IndexEntry entry : entries) {
                byteBuffer.putInt(entry.tag);
                byteBuffer.putInt(entry.type.getValue());
                byteBuffer.putInt(entry.offset);
                byteBuffer.putInt(entry.count);
            }
            return byteBuffer;
        }

        public Index addEntries(List<SignatureEntry> signatureEntries) {
            Objects.requireNonNull(intro, "intro");
            Objects.requireNonNull(entries, "entries");
            for (EntryLayout layout : EntryLayout.layoutEntries(signatureEntries, intro.getDataLength())) {
                var signatureEntry = layout.entry();
                entries.add(new IndexEntry()
                        .setTag(signatureEntry.getTag().getTagValue())
                        .setType(signatureEntry.getType())
                        .setOffset(layout.offset())
                        .setCount(layout.count()));
                intro.setDataLength(layout.endOffset());
                intro.setEntryCount(intro.getEntryCount() + 1);
            }
            entries.sort(Comparator.comparingInt(IndexEntry::getTag));
            return this;
        }


        @Data
        @Accessors(chain = true)
        public static class IndexEntry {
            int tag;
            RpmTags.RpmTagType type;
            int offset, count;
        }
    }

    private record EntryLayout(SignatureEntry entry, int offset, int padding, int count, int encodedSize) {
        int endOffset() {
            return offset + encodedSize;
        }
        /**
         * Calculates offsets, alignment, RPM counts and encoded sizes together so
         * the index and data-store writers cannot develop subtly different rules.
         */
        private static List<EntryLayout> layoutEntries(List<SignatureEntry> entries, int initialOffset) {
            var layouts = new ArrayList<EntryLayout>(entries.size());
            var offset = initialOffset;
            for (SignatureEntry entry : entries) {
                Objects.requireNonNull(entry, "entry");
                Objects.requireNonNull(entry.type, "entry.type");
                Objects.requireNonNull(entry.tag, "entry.tag");
                var dataLength = content(entry).remaining();
                var padding = headerDataAlignmentOffset(entry.type, offset);
                var count = switch (entry.type) {
                    case RPM_CHAR_TYPE, RPM_INT8_TYPE, RPM_BIN_TYPE -> dataLength;
                    case RPM_INT16_TYPE -> integralCount(entry, dataLength, 2);
                    case RPM_INT32_TYPE -> integralCount(entry, dataLength, 4);
                    case RPM_INT64_TYPE -> integralCount(entry, dataLength, 8);
                    case RPM_STRING_TYPE -> {
                        if (nullCount(entry) != 0) {
                            throw new IllegalArgumentException("RPM_STRING_TYPE contains a null byte");
                        }
                        yield 1;
                    }
                    case RPM_STRING_ARRAY_TYPE, RPM_I18NSTRING_TYPE -> nullCount(entry) + 1;
                    case RPM_NULL_TYPE -> throw new IllegalArgumentException("RPM_NULL_TYPE has no serializable value");
                };
                var encodedSize = Math.addExact(dataLength, isString(entry.type) ? 1 : 0);
                offset = Math.addExact(offset, padding);
                layouts.add(new EntryLayout(entry, offset, padding, count, encodedSize));
                offset = Math.addExact(offset, encodedSize);
            }
            return layouts;
        }

        private static int integralCount(SignatureEntry entry, int dataLength, int width) {
            if (dataLength == 0 || dataLength % width != 0) {
                throw new IllegalArgumentException(entry.type + " content length must be a non-zero multiple of " + width);
            }
            return dataLength / width;
        }

        private static boolean isString(RpmTags.RpmTagType type) {
            return type == RpmTags.RpmTagType.RPM_STRING_TYPE ||
                    type == RpmTags.RpmTagType.RPM_STRING_ARRAY_TYPE ||
                    type == RpmTags.RpmTagType.RPM_I18NSTRING_TYPE;
        }

        private static int nullCount(SignatureEntry entry) {
            var count = 0;
            var content = content(entry);
            while (content.hasRemaining()) {
                if (content.get() == 0) {
                    count++;
                }
            }
            return count;
        }

        private static ByteBuffer content(SignatureEntry entry) {
            return ByteBuffer.wrap(entry.copyByteArray());
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
