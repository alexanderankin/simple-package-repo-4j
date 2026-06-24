package simple.repo.rpm.repomd.jaxb;

import jakarta.xml.bind.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;
import simple.repo.rpm.repomd.RpmXmlConstants;

import java.util.List;

@Data
@Accessors(chain = true)
@XmlRootElement(name = "metadata", namespace = RpmXmlConstants.COMMON_NS)
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder = {"packageList"})
public class PrimaryBean {

    @XmlAttribute(name = "packages")
    private Integer packages;

    @XmlElement(name = "package", namespace = RpmXmlConstants.COMMON_NS)
    private List<Package> packageList;

    @Data
    @Accessors(chain = true)
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = {
            "name",
            "arch",
            "version",
            "checksum",
            "summary",
            "description",
            "packager",
            "url",
            "time",
            "size",
            "location",
            "format"
    })
    public static class Package {

        @XmlAttribute(name = "type")
        private String type;

        @XmlElement(namespace = RpmXmlConstants.COMMON_NS)
        private String name;

        @XmlElement(namespace = RpmXmlConstants.COMMON_NS)
        private String arch;

        @XmlElement(namespace = RpmXmlConstants.COMMON_NS)
        private Version version;

        @XmlElement(namespace = RpmXmlConstants.COMMON_NS)
        private Checksum checksum;

        @XmlElement(namespace = RpmXmlConstants.COMMON_NS)
        private String summary;

        @XmlElement(namespace = RpmXmlConstants.COMMON_NS)
        private String description;

        @XmlElement(namespace = RpmXmlConstants.COMMON_NS)
        private String packager;

        @XmlElement(namespace = RpmXmlConstants.COMMON_NS)
        private String url;

        @XmlElement(namespace = RpmXmlConstants.COMMON_NS)
        private Time time;

        @XmlElement(namespace = RpmXmlConstants.COMMON_NS)
        private Size size;

        @XmlElement(namespace = RpmXmlConstants.COMMON_NS)
        private Location location;

        @XmlElement(namespace = RpmXmlConstants.COMMON_NS)
        private Format format;
    }

    @Data
    @Accessors(chain = true)
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Version {
        @XmlAttribute(name = "epoch")
        private String epoch;

        @XmlAttribute(name = "ver")
        private String ver;

        @XmlAttribute(name = "rel")
        private String rel;
    }

    @Data
    @Accessors(chain = true)
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Checksum {
        @XmlAttribute(name = "type")
        private String type;

        @XmlAttribute(name = "pkgid")
        private String pkgId;

        @XmlValue
        private String value;
    }

    @Data
    @Accessors(chain = true)
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Time {
        @XmlAttribute(name = "file")
        private Long file;

        @XmlAttribute(name = "build")
        private Long build;
    }

    @Data
    @Accessors(chain = true)
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Size {
        @XmlAttribute(name = "package")
        private Long packageSize;

        @XmlAttribute(name = "installed")
        private Long installed;

        @XmlAttribute(name = "archive")
        private Long archive;
    }

    @Data
    @Accessors(chain = true)
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Location {
        @XmlAttribute(name = "href")
        private String href;
    }

    @Data
    @Accessors(chain = true)
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = {
            "license",
            "vendor",
            "group",
            "buildhost",
            "sourcerpm",
            "headerRange",
            "provides",
            "requires",
            "files"
    })
    public static class Format {

        @XmlElement(name = "license", namespace = RpmXmlConstants.RPM_NS)
        private String license;

        @XmlElement(name = "vendor", namespace = RpmXmlConstants.RPM_NS)
        private String vendor;

        @XmlElement(name = "group", namespace = RpmXmlConstants.RPM_NS)
        private String group;

        @XmlElement(name = "buildhost", namespace = RpmXmlConstants.RPM_NS)
        private String buildhost;

        @XmlElement(name = "sourcerpm", namespace = RpmXmlConstants.RPM_NS)
        private String sourcerpm;

        @XmlElement(name = "header-range", namespace = RpmXmlConstants.RPM_NS)
        private HeaderRange headerRange;

        @XmlElement(name = "provides", namespace = RpmXmlConstants.RPM_NS)
        private Entries provides;

        @XmlElement(name = "requires", namespace = RpmXmlConstants.RPM_NS)
        private Entries requires;

        @XmlElement(name = "file", namespace = RpmXmlConstants.COMMON_NS)
        private List<String> files;
    }

    @Data
    @Accessors(chain = true)
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class HeaderRange {
        @XmlAttribute(name = "start")
        private Long start;

        @XmlAttribute(name = "end")
        private Long end;
    }

    @Data
    @Accessors(chain = true)
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Entries {
        @XmlElement(name = "entry", namespace = RpmXmlConstants.RPM_NS)
        private List<Entry> entryList;
    }

    @Data
    @Accessors(chain = true)
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Entry {
        @XmlAttribute(name = "name")
        private String name;

        @XmlAttribute(name = "flags")
        private String flags;

        @XmlAttribute(name = "epoch")
        private String epoch;

        @XmlAttribute(name = "ver")
        private String ver;

        @XmlAttribute(name = "rel")
        private String rel;

        @XmlAttribute(name = "pre")
        private String pre;
    }
}
