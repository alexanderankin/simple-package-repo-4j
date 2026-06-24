package simple.repo.rpm.repomd;

import org.junit.jupiter.api.Test;
import simple.repo.rpm.repomd.dto.FileListsDto;
import simple.repo.rpm.repomd.dto.FileListsDto.Package.FileItem;
import simple.repo.rpm.repomd.dto.OtherDto;
import simple.repo.rpm.repomd.dtoconfig.RpmXmlCustomizer;
import tools.jackson.dataformat.xml.XmlMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileListsDtoTest {

    @Test
    void test() {
        var fileListsDto = new FileListsDto()
                .setPackageCount(1)
                .setPackageData(List.of(
                        new FileListsDto.Package()
                                .setPkgId("a5062198e7fbd55673e87f7a0c3f83c0d035deb704227e6cb27f45cf2335cc71")
                                .setName("name")
                                .setArch("arch")
                                .setVersion(new OtherDto.Version().setEpoch("0").setVersion("0.2.35").setRelease("15.el9"))
                                .setFileItems(List.of(
                                        new FileItem().setText("/usr/bin/3cpio"),
                                        new FileItem().setText("/usr/lib/.build-id").setType(FileItem.Type.dir)
                                ))
                ));

        var xmlMapper = new RpmXmlCustomizer().customized(XmlMapper.builder()).build();
        assertEquals(
                """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <filelists xmlns="http://linux.duke.edu/metadata/filelists" packages="1">
                          <package pkgid="a5062198e7fbd55673e87f7a0c3f83c0d035deb704227e6cb27f45cf2335cc71" name="name" arch="arch">
                            <version epoch="0" ver="0.2.35" rel="15.el9"/>
                            <file>/usr/bin/3cpio</file>
                            <file type="dir">/usr/lib/.build-id</file>
                          </package>
                        </filelists>
                        """,
                xmlMapper.writeValueAsString(fileListsDto));
    }
}
