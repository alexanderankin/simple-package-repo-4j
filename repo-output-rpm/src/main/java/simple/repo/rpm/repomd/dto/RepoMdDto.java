package simple.repo.rpm.repomd.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonRootName;
import lombok.Data;
import lombok.experimental.Accessors;
import simple.repo.rpm.repomd.RpmXmlConstants;
import simple.repo.rpm.repomd.dto.InstantSecondSerDe.JsonSerDeEpochSecond;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlText;

import java.time.Instant;
import java.util.List;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonRootName(value = "repomd", namespace = RpmXmlConstants.REPO_NS)
@JsonPropertyOrder({"revision", "data"})
public class RepoMdDto {
    @JacksonXmlProperty(isAttribute = true, localName = "xmlns:rpm")
    String rpmNamespace = RpmXmlConstants.RPM_NS;

    @JsonSerDeEpochSecond
    @JacksonXmlProperty(localName = "revision", namespace = RpmXmlConstants.REPO_NS)
    Instant revision;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "data", namespace = RpmXmlConstants.REPO_NS)
    List<RepoData> data;

    public enum DataType {
        primary,
        filelists,
        other,
        primary_db,
        filelists_db,
        other_db
    }

    public enum ChecksumType {
        sha256
    }

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({
            "type",
            "checksum",
            "open-checksum",
            "location",
            "timestamp",
            "size",
            "open-size",
            "database_version",
    })
    public static class RepoData {
        @JacksonXmlProperty(isAttribute = true, localName = "type")
        DataType type;

        @JacksonXmlProperty(localName = "checksum", namespace = RpmXmlConstants.REPO_NS)
        Checksum checksum;

        @JacksonXmlProperty(localName = "open-checksum", namespace = RpmXmlConstants.REPO_NS)
        Checksum openChecksum;

        @JacksonXmlProperty(localName = "location", namespace = RpmXmlConstants.REPO_NS)
        Location location;

        @JsonSerDeEpochSecond
        @JacksonXmlProperty(localName = "timestamp", namespace = RpmXmlConstants.REPO_NS)
        Instant timestamp;

        @JacksonXmlProperty(localName = "size", namespace = RpmXmlConstants.REPO_NS)
        Integer size;

        @JacksonXmlProperty(localName = "open-size", namespace = RpmXmlConstants.REPO_NS)
        Integer openSize;

        @JacksonXmlProperty(localName = "database_version", namespace = RpmXmlConstants.REPO_NS)
        Integer databaseVersion;
    }

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Checksum {
        @JacksonXmlProperty(isAttribute = true, localName = "type")
        ChecksumType type;

        @JacksonXmlText
        String value;
    }

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Location {
        @JacksonXmlProperty(isAttribute = true, localName = "href")
        String href;
    }
}
