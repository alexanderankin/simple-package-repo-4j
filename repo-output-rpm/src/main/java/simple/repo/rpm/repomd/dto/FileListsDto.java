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
@JsonRootName(value = "filelists", namespace = RpmXmlConstants.FILELIST_NS)
@JsonPropertyOrder({"packages", "package"})
public class FileListsDto {
    @JacksonXmlProperty(isAttribute = true, localName = "packages")
    int packageCount;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "package", namespace = RpmXmlConstants.FILELIST_NS)
    List<Package> packageData;

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"pkgid", "name", "arch", "version", "file"})
    public static class Package {
        @JacksonXmlProperty(isAttribute = true, localName = "pkgid")
        String pkgId;

        @JacksonXmlProperty(isAttribute = true)
        String name;

        @JacksonXmlProperty(isAttribute = true)
        String arch;

        @JacksonXmlProperty(localName = "version", namespace = RpmXmlConstants.FILELIST_NS)
        OtherDto.Version version;

        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "file", namespace = RpmXmlConstants.FILELIST_NS)
        List<FileItem> fileItems;

        @Data
        @Accessors(chain = true)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class FileItem {
            @JacksonXmlProperty(isAttribute = true)
            Type type;

            @JacksonXmlText
            String text;

            public enum Type {
                dir
            }
        }
    }
}
