package simple.repo.rpm.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class LeadTest extends SampleData {
    @Test
    void setNameTooLong() {
        new Lead().setNameString("");
        new Lead().setNameString("ok");
        new Lead().setNameString("n".repeat(66));
        assertThrows(Exception.class, () -> new Lead().setNameString("n".repeat(67)));
    }

    @Test
    void invalidLength() {
        assertThrows(Exception.class, () -> Lead.of(ByteBuffer.wrap(readBytes("htop-3.3.0-5.el10_0.x86_64.rpm.lead")).slice(0, 20)));
    }

    @ParameterizedTest
    @MethodSource("testFileNames")
    void invalidLength(String fileName) {
        assertThrows(Exception.class, () -> Lead.of(ByteBuffer.wrap(readBytes(fileName))));
    }

    @ParameterizedTest
    @MethodSource("testFileNames")
    void roundTrip(String fileName) {
        var leadPart = Arrays.copyOf(readBytes(fileName), 96);
        assertArrayEquals(leadPart, Lead.of(ByteBuffer.wrap(leadPart)).toByteArray());
    }

    @Test
    void testHtopArm64() {
        var aarch64Lead = Lead.of(ByteBuffer.wrap(readBytes("htop-3.3.0-5.el10_0.aarch64.rpm.lead")).slice(0, 96));
        System.out.println(aarch64Lead);
        assertEquals(
                new Lead()
                        .setMajor((byte) 3)
                        .setMinor((byte) 0)
                        .setType(Lead.PackageType.binary)
                        .setArchNum(Lead.ArchNum.aarch64.getValue())
                        .setArchNumValue(Lead.ArchNum.aarch64)
                        .setNameString("htop-3.3.0-5.el10_0")
                        .setOsNum(Lead.OsNum.linux)
                        .setSignatureType(Lead.SignatureType.header),
                aarch64Lead
        );
    }

    @Test
    void testHtopAmd64() {
        var x86_64Lead = Lead.of(ByteBuffer.wrap(readBytes("htop-3.3.0-5.el10_0.x86_64.rpm.lead")).slice(0, 96));
        assertEquals(
                new Lead()
                        .setMajor((byte) 3)
                        .setMinor((byte) 0)
                        .setType(Lead.PackageType.binary)
                        .setArchNum(Lead.ArchNum.x86_64.getValue())
                        .setArchNumValue(Lead.ArchNum.x86_64)
                        .setNameString("htop-3.3.0-5.el10_0")
                        .setOsNum(Lead.OsNum.linux)
                        .setSignatureType(Lead.SignatureType.header),
                x86_64Lead
        );
    }
}
