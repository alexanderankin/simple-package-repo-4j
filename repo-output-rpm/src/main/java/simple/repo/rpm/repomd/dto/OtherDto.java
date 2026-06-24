package simple.repo.rpm.repomd.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonRootName(value = "otherdata", namespace = RpmXmlConstants.OTHER_NS)
public class OtherDto {
    @JacksonXmlProperty(isAttribute = true, localName = "packages")
    private Integer packageCount;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "package", namespace = RpmXmlConstants.OTHER_NS)
    private List<PackageEntry> packages;

    @Data
    @Accessors(chain = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPropertyOrder({"pkgid", "name", "arch", "version", "changelog"})
    public static class PackageEntry {

        @JacksonXmlProperty(isAttribute = true, localName = "pkgid")
        private String pkgId;

        @JacksonXmlProperty(isAttribute = true, localName = "name")
        private String name;

        @JacksonXmlProperty(isAttribute = true, localName = "arch")
        private String arch;

        @JacksonXmlProperty(localName = "version", namespace = RpmXmlConstants.OTHER_NS)
        private Version version;

        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "changelog", namespace = RpmXmlConstants.OTHER_NS)
        private List<ChangelogEntry> changelogs;
    }

    @Data
    @Accessors(chain = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPropertyOrder({"epoch", "ver", "rel"})
    public static class Version {

        @JacksonXmlProperty(isAttribute = true, localName = "epoch")
        private String epoch;

        @JacksonXmlProperty(isAttribute = true, localName = "ver")
        private String version;

        @JacksonXmlProperty(isAttribute = true, localName = "rel")
        private String release;
    }

    @Data
    @Accessors(chain = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPropertyOrder({"author", "date", "text"})
    public static class ChangelogEntry {

        @JacksonXmlProperty(isAttribute = true, localName = "author")
        private String author;

        @JacksonXmlProperty(isAttribute = true, localName = "date")
        private Long date;

        @JacksonXmlText
        private String text;
    }
}
