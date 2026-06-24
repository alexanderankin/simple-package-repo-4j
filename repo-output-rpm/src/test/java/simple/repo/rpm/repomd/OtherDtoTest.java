package simple.repo.rpm.repomd;

import org.junit.jupiter.api.Test;
import simple.repo.rpm.repomd.dto.OtherDto;
import simple.repo.rpm.repomd.dtoconfig.RpmXmlCustomizer;
import tools.jackson.dataformat.xml.XmlMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OtherDtoTest {
    @Test
    void test() {
        var otherDto = new OtherDto()
                .setPackageCount(2)
                .setPackages(List.of(
                        new OtherDto.PackageEntry()
                                .setPkgId("a5062198e7fbd55673e87f7a0c3f83c0d035deb704227e6cb27f45cf2335cc71")
                                .setName("zvbi-fonts")
                                .setArch("noarch")
                                .setVersion(new OtherDto.Version().setEpoch("0").setVersion("0.2.35").setRelease("15.el9"))
                                .setChangelogs(List.of(
                                        new OtherDto.ChangelogEntry()
                                                .setAuthor("Fedora Release Engineering <releng@fedoraproject.org> - 0.2.35-9")
                                                // epoch second
                                                .setDate(1564228800L)
                                                .setText("- Rebuilt for https://fedoraproject.org/wiki/Fedora_31_Mass_Rebuild"),
                                        new OtherDto.ChangelogEntry()
                                                .setAuthor("Fedora Release Engineering <releng@fedoraproject.org> - 0.2.35-15")
                                                .setDate(1627041600L)
                                                .setText("- Rebuilt for https://fedoraproject.org/wiki/Fedora_35_Mass_Rebuild - avoid rpath in binaries")))));

        var xmlMapper = new RpmXmlCustomizer().customized(XmlMapper.builder()).build();

        assertEquals(
                """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <otherdata xmlns="http://linux.duke.edu/metadata/other" packages="2">
                          <package pkgid="a5062198e7fbd55673e87f7a0c3f83c0d035deb704227e6cb27f45cf2335cc71" name="zvbi-fonts" arch="noarch">
                            <version epoch="0" ver="0.2.35" rel="15.el9"/>
                            <changelog author="Fedora Release Engineering &lt;releng@fedoraproject.org> - 0.2.35-9" date="1564228800">- Rebuilt for https://fedoraproject.org/wiki/Fedora_31_Mass_Rebuild</changelog>
                            <changelog author="Fedora Release Engineering &lt;releng@fedoraproject.org> - 0.2.35-15" date="1627041600">- Rebuilt for https://fedoraproject.org/wiki/Fedora_35_Mass_Rebuild - avoid rpath in binaries</changelog>
                          </package>
                        </otherdata>
                        """,
                xmlMapper.writeValueAsString(otherDto));
    }
}
