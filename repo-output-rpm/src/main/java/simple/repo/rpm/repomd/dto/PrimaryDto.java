/*
package simple.repo.rpm.repomd.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonRootName;
import lombok.Data;
import lombok.experimental.Accessors;
import simple.repo.rpm.repomd.RpmXmlConstants;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlText;

import java.util.List;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonRootName(value = "metadata", namespace = RpmXmlConstants.COMMON_NS)
@JsonPropertyOrder({"packages", "package"})
public class PrimaryDto {
    @JacksonXmlProperty(isAttribute = true, localName = "packages")
    Integer packages;

    @JacksonXmlProperty(localName = "package", namespace = RpmXmlConstants.COMMON_NS)
    @JacksonXmlElementWrapper(useWrapping = false)
    List<Package> packageList;

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({
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

        @JacksonXmlProperty(isAttribute = true, localName = "type")
        String type;

        @JacksonXmlProperty(localName = "name", namespace = RpmXmlConstants.COMMON_NS)
        String name;

        @JacksonXmlProperty(localName = "arch", namespace = RpmXmlConstants.COMMON_NS)
        String arch;

        @JacksonXmlProperty(localName = "version", namespace = RpmXmlConstants.COMMON_NS)
        Version version;

        @JacksonXmlProperty(localName = "checksum", namespace = RpmXmlConstants.COMMON_NS)
        Checksum checksum;

        @JacksonXmlProperty(localName = "summary", namespace = RpmXmlConstants.COMMON_NS)
        String summary;

        @JacksonXmlProperty(localName = "description", namespace = RpmXmlConstants.COMMON_NS)
        String description;

        @JacksonXmlProperty(localName = "packager", namespace = RpmXmlConstants.COMMON_NS)
        String packager;

        @JacksonXmlProperty(localName = "url", namespace = RpmXmlConstants.COMMON_NS)
        String url;

        @JacksonXmlProperty(localName = "time", namespace = RpmXmlConstants.COMMON_NS)
        Time time;

        @JacksonXmlProperty(localName = "size", namespace = RpmXmlConstants.COMMON_NS)
        Size size;

        @JacksonXmlProperty(localName = "location", namespace = RpmXmlConstants.COMMON_NS)
        Location location;

        @JacksonXmlProperty(localName = "format", namespace = RpmXmlConstants.COMMON_NS)
        Format format;
    }

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"epoch", "ver", "rel"})
    public static class Version {

        @JacksonXmlProperty(isAttribute = true, localName = "epoch")
        String epoch;

        @JacksonXmlProperty(isAttribute = true, localName = "ver")
        String ver;

        @JacksonXmlProperty(isAttribute = true, localName = "rel")
        String rel;
    }

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"type", "pkgid"})
    public static class Checksum {

        @JacksonXmlProperty(isAttribute = true, localName = "type")
        String type;

        @JacksonXmlProperty(isAttribute = true, localName = "pkgid")
        String pkgId;

        @JacksonXmlText
        String value;
    }

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"file", "build"})
    public static class Time {

        @JacksonXmlProperty(isAttribute = true, localName = "file")
        Long file;

        @JacksonXmlProperty(isAttribute = true, localName = "build")
        Long build;
    }

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"package", "installed", "archive"})
    public static class Size {

        @JacksonXmlProperty(isAttribute = true, localName = "package")
        Long packageSize;

        @JacksonXmlProperty(isAttribute = true, localName = "installed")
        Long installed;

        @JacksonXmlProperty(isAttribute = true, localName = "archive")
        Long archive;
    }

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Location {

        @JacksonXmlProperty(isAttribute = true, localName = "href")
        String href;
    }

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({
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
        @JacksonXmlProperty(localName = "license", namespace = RpmXmlConstants.RPM_NS)
        String license;

        @JacksonXmlProperty(localName = "vendor", namespace = RpmXmlConstants.RPM_NS)
        String vendor;

        @JacksonXmlProperty(localName = "group", namespace = RpmXmlConstants.RPM_NS)
        String group;

        @JacksonXmlProperty(localName = "buildhost", namespace = RpmXmlConstants.RPM_NS)
        String buildhost;

        @JacksonXmlProperty(localName = "sourcerpm", namespace = RpmXmlConstants.RPM_NS)
        String sourcerpm;

        @JacksonXmlProperty(localName = "header-range", namespace = RpmXmlConstants.RPM_NS)
        HeaderRange headerRange;

        @JacksonXmlProperty(localName = "provides", namespace = RpmXmlConstants.RPM_NS)
        Entries provides;

        @JacksonXmlProperty(localName = "requires", namespace = RpmXmlConstants.RPM_NS)
        Entries requires;

        @JacksonXmlProperty(localName = "file", namespace = RpmXmlConstants.COMMON_NS)
        @JacksonXmlElementWrapper(useWrapping = false)
        List<String> files;
    }

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"start", "end"})
    public static class HeaderRange {

        @JacksonXmlProperty(isAttribute = true, localName = "start")
        Long start;

        @JacksonXmlProperty(isAttribute = true, localName = "end")
        Long end;
    }

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Entries {

        @JacksonXmlProperty(localName = "entry", namespace = RpmXmlConstants.RPM_NS)
        @JacksonXmlElementWrapper(useWrapping = false)
        List<Entry> entryList;
    }

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"name", "flags", "epoch", "ver", "rel", "pre"})
    public static class Entry {

        @JacksonXmlProperty(isAttribute = true, localName = "name")
        String name;

        @JacksonXmlProperty(isAttribute = true, localName = "flags")
        String flags;

        @JacksonXmlProperty(isAttribute = true, localName = "epoch")
        String epoch;

        @JacksonXmlProperty(isAttribute = true, localName = "ver")
        String ver;

        @JacksonXmlProperty(isAttribute = true, localName = "rel")
        String rel;

        @JacksonXmlProperty(isAttribute = true, localName = "pre")
        String pre;
    }
}
*/
