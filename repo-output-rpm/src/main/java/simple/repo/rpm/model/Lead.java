package simple.repo.rpm.model;

import lombok.*;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.ArrayUtils;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

// https://rpm.org/docs/6.1.x/manual/format_lead.html
@Data
@Accessors(chain = true)
public class Lead {
    public static final int OFFSET = 0;
    public static final int SIZE = 96;
    private static final byte[] MAGIC = {(byte) 0xed, (byte) 0xab, (byte) 0xee, (byte) 0xdb};

    byte major = (byte) 3;
    byte minor;
    PackageType type;
    short archNum;
    ArchNum archNumValue;
    @ToString.Exclude
    @Setter(AccessLevel.PRIVATE)
    byte[] name = new byte[66];
    String nameString;
    OsNum osNum;
    SignatureType signatureType;
    @ToString.Exclude
    @Setter(AccessLevel.PRIVATE)
    byte[] reserved = new byte[16];

    public static Lead of(ByteBuffer byteBuffer) {
        if (byteBuffer.remaining() != SIZE)
            throw new IllegalArgumentException("invalid length");
        var lead = new Lead();
        byte[] magic = new byte[4];
        byteBuffer.get(magic);
        if (!Arrays.equals(magic, MAGIC)) {
            throw new IllegalArgumentException("magic not match");
        }
        lead.major = byteBuffer.get();
        lead.minor = byteBuffer.get();
        lead.type = PackageType.ofValue(byteBuffer.getShort());
        lead.archNum = byteBuffer.getShort();
        lead.archNumValue = ArchNum.ofValue(lead.archNum);
        byteBuffer.get(lead.name);
        lead.nameString = new String(lead.name, 0, ArrayUtils.indexOf(lead.name, (byte) 0));
        lead.osNum = OsNum.ofValue(byteBuffer.getShort());
        lead.signatureType = SignatureType.ofValue(byteBuffer.getShort());
        byteBuffer.get(lead.reserved);
        if (byteBuffer.remaining() != 0)
            throw new IllegalStateException();
        return lead;
    }

    public static Lead parse(ByteBuffer bb) {
        var lead = of(bb.slice(bb.position(), SIZE));
        bb.position(bb.position() + SIZE);
        return lead;
    }

    public Lead setNameString(String nameString) {
        Arrays.fill(this.name, (byte) 0);
        if (nameString == null || nameString.isEmpty()) {
            return this;
        }

        var newLength = nameString.getBytes().length;
        if (newLength > name.length) {
            throw new IllegalArgumentException();
        }
        this.nameString = nameString;
        System.arraycopy(nameString.getBytes(), 0, this.name, 0, newLength);
        return this;
    }

    public byte[] toByteArray() {
        return toByteBuffer().array();
    }

    public ByteBuffer toByteBuffer() {
        return toByteBuffer(ByteBuffer.allocate(SIZE));
    }

    public ByteBuffer toByteBuffer(ByteBuffer byteBuffer) {
        if (byteBuffer.remaining() != SIZE)
            throw new IllegalArgumentException("invalid length");
        byteBuffer.put(MAGIC);
        byteBuffer.put(major);
        byteBuffer.put(minor);
        byteBuffer.putShort(type.getValue());
        if (archNumValue != null) {
            byteBuffer.putShort(archNumValue.getValue());
        } else {
            byteBuffer.putShort(archNum);
        }
        if (nameString != null) {
            byteBuffer.put(Arrays.copyOf(nameString.getBytes(), name.length));
        } else {
            byteBuffer.put(name);
        }
        byteBuffer.putShort(osNum.getValue());
        byteBuffer.putShort(signatureType.getValue());
        byteBuffer.put(reserved);
        if (byteBuffer.remaining() != 0)
            throw new IllegalStateException();
        return byteBuffer;
    }

    @Getter
    @RequiredArgsConstructor
    public enum PackageType {
        binary(0), source(1),
        ;

        private static final Map<Short, PackageType> BY_VALUE = Arrays.stream(values())
                .collect(Collectors.toMap(PackageType::getValue, Function.identity()));
        private final short value;

        PackageType(int value) {
            this((short) value);
        }

        static PackageType ofValue(short value) {
            return Objects.requireNonNull(BY_VALUE.get(value));
        }
    }

    @Getter
    @RequiredArgsConstructor
    public enum ArchNum {
        x86_64(1),
        // sparc64(2),
        // sparc(3),
        // mipsel(4),
        // ppc(5),
        // mips64el(11),
        // armv8hl(12),
        aarch64(19),
        riscv64(22);

        private static final Map<Short, ArchNum> BY_VALUE = Arrays.stream(values())
                .collect(Collectors.toMap(ArchNum::getValue, Function.identity()));
        private final short value;

        ArchNum(int value) {
            this((short) value);
        }

        static ArchNum ofValue(short value) {
            return BY_VALUE.get(value);
        }
    }


    @Getter
    @RequiredArgsConstructor
    public enum OsNum {
        unknown(0), // seen in wild from canonicals cloud-utils package
        linux(1),
        irix(2),
        solaris(3),
        sun_os(4),
        aix_amiga_os(5),
        hpux10(6),
        osf1(7),
        free_bsd(8),
        sco_sv(9),
        irix64(10),
        next_step(11),
        bsdi(12),
        machten(13),
        cygwin32(14),
        unix_sv_mp_ras(16),
        free_mi_nt(17),
        os_390(18),
        vm_esa(19),
        linux_390_os_390_esa_vm_esa(20),
        darwin(21),
        ;

        private static final Map<Short, OsNum> BY_VALUE = Arrays.stream(values())
                .collect(Collectors.toMap(OsNum::getValue, Function.identity()));
        private final short value;

        OsNum(int value) {
            this((short) value);
        }

        static OsNum ofValue(short value) {
            return Objects.requireNonNull(BY_VALUE.get(value));
        }
    }

    @Getter
    @RequiredArgsConstructor
    public enum SignatureType {
        header(5),
        ;

        private static final Map<Short, SignatureType> BY_VALUE = Arrays.stream(values())
                .collect(Collectors.toMap(SignatureType::getValue, Function.identity()));
        private final short value;

        SignatureType(int value) {
            this((short) value);
        }

        static SignatureType ofValue(short value) {
            return Objects.requireNonNull(BY_VALUE.get(value));
        }
    }

}
