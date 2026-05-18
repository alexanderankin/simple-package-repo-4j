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
        var entries = new ArrayList<SignatureEntry>(index.entries.size());
        signature.setEntries(entries);

        for (var entry : index.entries) {
            RpmTags anEnum = switch (tagType) {
                case SIGNATURE -> toSigEnum(entry.tag);
                case HEADER -> toHeaderEnum(entry.tag);
            };
            var e = new SignatureEntry().setTag(anEnum).setType(entry.type);
            entries.add(e);

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
        return entries.stream().mapToInt(SignatureEntry::findLength).sum();
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

    public ByteBuffer asDataBuffer() {
        throw new UnsupportedOperationException();
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

        public byte[] asByteArray() {
            return asByteBuffer().array();
        }

        public ByteBuffer asByteBuffer() {
            return asByteBuffer(ByteBuffer.allocate(intro.indexSize()));
        }

        public ByteBuffer asByteBuffer(ByteBuffer byteBuffer) {
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
