package simple.repo.rpm.repomd;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import simple.repo.rpm.repomd.dto.RepoMdDto;
import simple.repo.rpm.repomd.dtoconfig.RpmXmlCustomizer;
import tools.jackson.dataformat.xml.XmlMapper;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepoMdDtoTest {

    @SneakyThrows
    @Test
    void test() {
        var timestamp = Instant.ofEpochSecond(1778861567);
        var dto = new RepoMdDto()
                .setRevision(timestamp)
                .setData(List.of(
                        new RepoMdDto.RepoData()
                                .setType(RepoMdDto.DataType.primary)
                                .setChecksum(new RepoMdDto.Checksum()
                                        .setType(RepoMdDto.ChecksumType.sha256)
                                        .setValue("857e74f587ba37275b39027d5b38dbcba9d2d82c02de6282294889f22ba82a62"))
                                .setOpenChecksum(new RepoMdDto.Checksum()
                                        .setType(RepoMdDto.ChecksumType.sha256)
                                        .setValue("b70d1befb7c1f7e44d49b62b229c4bfe4a1a944ae5a7d5b44d0cee32432ebf02"))
                                .setLocation(new RepoMdDto.Location()
                                        .setHref("repodata/857e74f587ba37275b39027d5b38dbcba9d2d82c02de6282294889f22ba82a62-primary.xml.gz"))
                                .setTimestamp(timestamp)
                                .setSize(789)
                                .setOpenSize(1677),

                        new RepoMdDto.RepoData()
                                .setType(RepoMdDto.DataType.filelists)
                                .setChecksum(new RepoMdDto.Checksum()
                                        .setType(RepoMdDto.ChecksumType.sha256)
                                        .setValue("2fc51f5d419bb5d4704955cc9987032bb7afbdb111acbf3a8ca9dc7a286d7af8"))
                                .setOpenChecksum(new RepoMdDto.Checksum()
                                        .setType(RepoMdDto.ChecksumType.sha256)
                                        .setValue("8fbe57724c8d87f69dfdbbce95658d36860937be3acc45f108a4c71f4d8c7901"))
                                .setLocation(new RepoMdDto.Location()
                                        .setHref("repodata/2fc51f5d419bb5d4704955cc9987032bb7afbdb111acbf3a8ca9dc7a286d7af8-filelists.xml.gz"))
                                .setTimestamp(timestamp)
                                .setSize(1029)
                                .setOpenSize(5795),

                        new RepoMdDto.RepoData()
                                .setType(RepoMdDto.DataType.other)
                                .setChecksum(new RepoMdDto.Checksum()
                                        .setType(RepoMdDto.ChecksumType.sha256)
                                        .setValue("43f09fab314aa61d5e3d248f76e2267c79450ef3834f2b06b814497be2082490"))
                                .setOpenChecksum(new RepoMdDto.Checksum()
                                        .setType(RepoMdDto.ChecksumType.sha256)
                                        .setValue("02ca573817d8d6793cf935a6a78786388e47772ec592d40b0a16715899964505"))
                                .setLocation(new RepoMdDto.Location()
                                        .setHref("repodata/43f09fab314aa61d5e3d248f76e2267c79450ef3834f2b06b814497be2082490-other.xml.gz"))
                                .setTimestamp(timestamp)
                                .setSize(331)
                                .setOpenSize(440),

                        new RepoMdDto.RepoData()
                                .setType(RepoMdDto.DataType.primary_db)
                                .setChecksum(new RepoMdDto.Checksum()
                                        .setType(RepoMdDto.ChecksumType.sha256)
                                        .setValue("bdfe832d38b9c5a9255a940d4999ab105ce764743f3c211d0493492107ad1a65"))
                                .setOpenChecksum(new RepoMdDto.Checksum()
                                        .setType(RepoMdDto.ChecksumType.sha256)
                                        .setValue("ce6dfc9d9c8cd633537dd561f13ffe393a654d5124f53847821b25eabf0770cd"))
                                .setLocation(new RepoMdDto.Location()
                                        .setHref("repodata/bdfe832d38b9c5a9255a940d4999ab105ce764743f3c211d0493492107ad1a65-primary.sqlite.bz2"))
                                .setTimestamp(timestamp)
                                .setSize(2137)
                                .setOpenSize(106496)
                                .setDatabaseVersion(10),

                        new RepoMdDto.RepoData()
                                .setType(RepoMdDto.DataType.filelists_db)
                                .setChecksum(new RepoMdDto.Checksum()
                                        .setType(RepoMdDto.ChecksumType.sha256)
                                        .setValue("7b11110c77a65bf83ef007c86f476d0f02a07d1f4973f4a23b53d3748604fbad"))
                                .setOpenChecksum(new RepoMdDto.Checksum()
                                        .setType(RepoMdDto.ChecksumType.sha256)
                                        .setValue("56104786a53b34f18a876e4e94bb631d905c46328fe0d82d348ba32d82429a52"))
                                .setLocation(new RepoMdDto.Location()
                                        .setHref("repodata/7b11110c77a65bf83ef007c86f476d0f02a07d1f4973f4a23b53d3748604fbad-filelists.sqlite.bz2"))
                                .setTimestamp(timestamp)
                                .setSize(2176)
                                .setOpenSize(28672)
                                .setDatabaseVersion(10),

                        new RepoMdDto.RepoData()
                                .setType(RepoMdDto.DataType.other_db)
                                .setChecksum(new RepoMdDto.Checksum()
                                        .setType(RepoMdDto.ChecksumType.sha256)
                                        .setValue("2fe47565df8076d05c2e7557d75c3ac02977858ad3d55a9784509a3881b6dcf0"))
                                .setOpenChecksum(new RepoMdDto.Checksum()
                                        .setType(RepoMdDto.ChecksumType.sha256)
                                        .setValue("431e2ee9531e71e007159bda50233b05434580445d7eb1b2c7ba90a0b985e6c8"))
                                .setLocation(new RepoMdDto.Location()
                                        .setHref("repodata/2fe47565df8076d05c2e7557d75c3ac02977858ad3d55a9784509a3881b6dcf0-other.sqlite.bz2"))
                                .setTimestamp(timestamp)
                                .setSize(768)
                                .setOpenSize(24576)
                                .setDatabaseVersion(10)
                ));

        var dtoAsString = new RpmXmlCustomizer().customized(XmlMapper.builder()).build().writeValueAsString(dto);
        // System.out.println(dtoAsString);
        // noinspection ALL
        assertEquals(new String(getClass().getResourceAsStream("example").readAllBytes()).strip(), dtoAsString.strip());
    }
}
