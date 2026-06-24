/*
package simple.repo.rpm.repomd;

import com.ctc.wstx.stax.WstxOutputFactory;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import simple.repo.rpm.repomd.dto.PrimaryDto;
import simple.repo.rpm.repomd.dtoconfig.RpmXmlCustomizer;
import tools.jackson.dataformat.xml.XmlFactory;
import tools.jackson.dataformat.xml.XmlMapper;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.Writer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static simple.repo.rpm.repomd.RpmXmlConstants.REPO_NS;
import static simple.repo.rpm.repomd.RpmXmlConstants.RPM_NS;

class PrimaryDtoTest {

    @SneakyThrows
    @Test
    void test() {
        var dto = new PrimaryDto()
                .setPackages(1)
                .setPackageList(List.of(
                        new PrimaryDto.Package()
                                .setType("rpm")
                                .setName("example-package")
                                .setArch("x86_64")
                                .setVersion(new PrimaryDto.Version()
                                        .setEpoch("0")
                                        .setVer("0.0.1")
                                        .setRel("1.el10"))
                                .setChecksum(new PrimaryDto.Checksum()
                                        .setType("sha256")
                                        .setPkgId("YES")
                                        .setValue("40188d5ea6ff5b0a897dafbb882337bf09afc61f2ee58fdb1d91d1ba1cd0841d"))
                                .setSummary("text")
                                .setDescription("description")
                                .setPackager("")
                                .setUrl("https://example.com")
                                .setTime(new PrimaryDto.Time()
                                        .setFile(1778861341L)
                                        .setBuild(1778861269L))
                                .setSize(new PrimaryDto.Size()
                                        .setPackageSize(177847290L)
                                        .setInstalled(245205824L)
                                        .setArchive(245220044L))
                                .setLocation(new PrimaryDto.Location()
                                        .setHref("pool/example-package-0.0.1-1.el10.x86_64.rpm"))
                                .setFormat(new PrimaryDto.Format()
                                        .setLicense("Proprietary")
                                        .setVendor("")
                                        .setGroup("Unspecified")
                                        .setBuildhost("localhost123")
                                        .setSourcerpm("example-package-0.0.1-1.el10.src.rpm")
                                        .setHeaderRange(new PrimaryDto.HeaderRange()
                                                .setStart(4504L)
                                                .setEnd(20197L))
                                        .setProvides(new PrimaryDto.Entries()
                                                .setEntryList(List.of(
                                                        new PrimaryDto.Entry()
                                                                .setName("example-package")
                                                                .setFlags("EQ")
                                                                .setEpoch("0")
                                                                .setVer("0.0.1")
                                                                .setRel("1.el10"),

                                                        new PrimaryDto.Entry()
                                                                .setName("example-package(x86-64)")
                                                                .setFlags("EQ")
                                                                .setEpoch("0")
                                                                .setVer("0.0.1")
                                                                .setRel("1.el10")
                                                )))
                                        .setRequires(new PrimaryDto.Entries()
                                                .setEntryList(List.of(
                                                        new PrimaryDto.Entry().setName("/bin/sh").setPre("1"),
                                                        new PrimaryDto.Entry().setName("/bin/sh"),
                                                        new PrimaryDto.Entry().setName("libicu"),
                                                        new PrimaryDto.Entry().setName("openssl-libs"),
                                                        new PrimaryDto.Entry().setName("zlib")
                                                )))
                                        .setFiles(List.of(
                                                "/usr/bin/some-binary"
                                        ))
                                )
                ));

        var dtoAsString = new RpmXmlCustomizer()
                .customized(XmlMapper.builder(
                        XmlFactory.builder()
                                .xmlOutputFactory(new Ns())
                                .build()
                ))
                .build()
                .writeValueAsString(dto);

        System.out.println(dtoAsString);

        // noinspection ALL
        assertEquals(
                new String(getClass().getResourceAsStream("working/857e74f587ba37275b39027d5b38dbcba9d2d82c02de6282294889f22ba82a62-primary.xml").readAllBytes()).strip(),
                dtoAsString.strip()
        );
    }

    private static class Ns extends WstxOutputFactory {
        public Ns() {
            setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, false);
        }

        @Override
        public XMLStreamWriter createXMLStreamWriter(Writer w)
                throws XMLStreamException {

            XMLStreamWriter delegate =
                    super.createXMLStreamWriter(w);

            delegate.setPrefix("repo", REPO_NS);
            delegate.setPrefix("rpm", RPM_NS);

            // delegate.getNamespaceContext();
            // delegate.setNamespaceContext();

            return delegate;
        }
    }
}
*/
