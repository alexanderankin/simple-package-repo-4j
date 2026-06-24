package simple.repo.rpm.repomd.jaxb;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PrimaryBeanTest {

    @Test
    void test() {
        var primaryBean = new PrimaryBean()
                .setPackages(2)
                .setPackageList(List.of(
                        new PrimaryBean.Package()
                                .setType("rpm")
                                .setName("example-package")
                                .setArch("x86_64")
                                .setVersion(new PrimaryBean.Version().setEpoch("0").setVer("0.0.1").setRel("1.el10"))
                                .setChecksum(new PrimaryBean.Checksum().setType("sha256").setPkgId("YES")
                                        .setValue("40188d5ea6ff5b0a897dafbb882337bf09afc61f2ee58fdb1d91d1ba1cd0841d"))
                                .setSummary("setSummary")
                                .setDescription("setDescription")
                                .setPackager("")
                                .setUrl("https://example.com")
                                .setTime(new PrimaryBean.Time().setFile(123L).setBuild(456L))
                                .setSize(new PrimaryBean.Size().setPackageSize(1L).setInstalled(2L).setArchive(3L))
                                .setLocation(new PrimaryBean.Location().setHref("pool/example-package-0.0.1-1.el10.x86_64.rpm"))
                                .setFormat(new PrimaryBean.Format()
                                        .setLicense("Proprietary")
                                        .setVendor(null)
                                        .setGroup("Unspecified")
                                        .setBuildhost("localhost")
                                        .setSourcerpm("example-package-0.0.1-1.el10.src.rpm")
                                        .setHeaderRange(new PrimaryBean.HeaderRange()
                                                .setStart(4504L)
                                                .setEnd(20197L))
                                        .setProvides(new PrimaryBean.Entries()
                                                .setEntryList(List.of(
                                                        new PrimaryBean.Entry().setName("example-package").setFlags("EQ").setEpoch("0").setVer("0.0.1").setRel("1.el10"),
                                                        new PrimaryBean.Entry().setName("example-package(x86-64)").setFlags("EQ").setEpoch("0").setVer("0.0.1").setRel("1.el10")
                                                )))
                                        .setRequires(new PrimaryBean.Entries()
                                                .setEntryList(List.of(
                                                        new PrimaryBean.Entry().setName("/bin/sh").setPre("1"),
                                                        new PrimaryBean.Entry().setName("libicu"),
                                                        new PrimaryBean.Entry().setName("openssl-libs"),
                                                        new PrimaryBean.Entry().setName("zlib")
                                                ))))
                ));

        var written = new PrimaryBeanWriter().writeToString(primaryBean);
        assertEquals(
                """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <metadata packages="2" xmlns="http://linux.duke.edu/metadata/common" xmlns:rpm="http://linux.duke.edu/metadata/rpm">
                          <package type="rpm">
                            <name>example-package</name>
                            <arch>x86_64</arch>
                            <version epoch="0" ver="0.0.1" rel="1.el10"/>
                            <checksum type="sha256" pkgid="YES">40188d5ea6ff5b0a897dafbb882337bf09afc61f2ee58fdb1d91d1ba1cd0841d</checksum>
                            <summary>setSummary</summary>
                            <description>setDescription</description>
                            <packager></packager>
                            <url>https://example.com</url>
                            <time file="123" build="456"/>
                            <size package="1" installed="2" archive="3"/>
                            <location href="pool/example-package-0.0.1-1.el10.x86_64.rpm"/>
                            <format>
                              <rpm:license>Proprietary</rpm:license>
                              <rpm:group>Unspecified</rpm:group>
                              <rpm:buildhost>localhost</rpm:buildhost>
                              <rpm:sourcerpm>example-package-0.0.1-1.el10.src.rpm</rpm:sourcerpm>
                              <rpm:header-range start="4504" end="20197"/>
                              <rpm:provides>
                                <rpm:entry name="example-package" flags="EQ" epoch="0" ver="0.0.1" rel="1.el10"/>
                                <rpm:entry name="example-package(x86-64)" flags="EQ" epoch="0" ver="0.0.1" rel="1.el10"/>
                              </rpm:provides>
                              <rpm:requires>
                                <rpm:entry name="/bin/sh" pre="1"/>
                                <rpm:entry name="libicu"/>
                                <rpm:entry name="openssl-libs"/>
                                <rpm:entry name="zlib"/>
                              </rpm:requires>
                            </format>
                          </package>
                        </metadata>
                        """,
                written
        );
    }
}
