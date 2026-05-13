package simple.repo.model;

import com.fasterxml.jackson.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;

@Data
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PackageConfig {
    @NotNull
    @Valid
    PackageMeta meta;
    @NotNull
    @Valid
    ControlExtras control;
    @NotNull
    @Valid
    PackageConfig.FileSpec files;

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = TarFileSpec.TextTarFileSpec.class, name = "text"),
            @JsonSubTypes.Type(value = TarFileSpec.BinaryTarFileSpec.class, name = "binary"),
            @JsonSubTypes.Type(value = TarFileSpec.FileTarFileSpec.class, name = "file"),
            @JsonSubTypes.Type(value = TarFileSpec.UrlTarFileSpec.class, name = "url"),
    })
    @Data
    @Accessors(chain = true)
    public static sealed abstract class TarFileSpec {
        String path;
        Integer mode;

        @ToString(callSuper = true)
        @EqualsAndHashCode(callSuper = true)
        @Data
        @Accessors(chain = true)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static final class TextTarFileSpec extends TarFileSpec {
            @NotBlank
            String content;
        }

        @ToString(callSuper = true)
        @EqualsAndHashCode(callSuper = true)
        @Data
        @Accessors(chain = true)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static final class BinaryTarFileSpec extends TarFileSpec {
            @NotEmpty
            byte[] content;
        }

        @ToString(callSuper = true)
        @EqualsAndHashCode(callSuper = true)
        @Data
        @Accessors(chain = true)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static final class FileTarFileSpec extends TarFileSpec {
            @NotBlank
            String sourcePath;
        }

        @ToString(callSuper = true)
        @EqualsAndHashCode(callSuper = true)
        @Data
        @Accessors(chain = true)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static final class UrlTarFileSpec extends TarFileSpec {
            @NotNull
            URI url;
            String bearerToken;
            LinkedHashMap<String, List<String>> headers;
        }
    }

    @Data
    @Accessors(chain = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PackageMeta {
        // public static final String SD_INDEX_EXTENSION = ".simple-deb-4j-index.json";
        @NotBlank
        String name;
        @NotBlank
        String version;
        @NotNull
        Arch arch;

        // @JsonIgnore
        // public String getDebFilename() {
        //     return name + "_" + version + "_" + arch + ".deb";
        // }
        //
        // @JsonIgnore
        // public String getIndexFilename() {
        //     return name + "_" + version + "_" + arch + SD_INDEX_EXTENSION;
        // }
    }

    @Data
    @Accessors(chain = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ControlExtras {
        @NotNull
        @JsonAlias("Depends")
        String depends = "";
        @NotNull
        @JsonAlias("Recommends")
        String recommends = "";
        @NotBlank
        @JsonAlias("Section")
        String section = "main";
        @NotBlank
        @JsonAlias("Priority")
        String priority = "optional";
        @NotNull
        @JsonAlias("Homepage")
        String homepage = "";
        @NotNull
        @JsonAlias("Conflicts")
        String conflicts = "";
        @JsonAlias("InstalledSize")
        Integer installedSize;
        @NotBlank
        @JsonAlias("Maintainer")
        String maintainer = "";
        @NotBlank
        @JsonAlias("Description")
        String description = "";

        public ControlExtras setSection(String section) {
            this.section = StringUtils.isBlank(section) ? "main" : section;
            return this;
        }

        public ControlExtras setPriority(String priority) {
            this.priority = StringUtils.isBlank(priority) ? "optional" : priority;
            return this;
        }
    }

    @Data
    @Accessors(chain = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FileSpec {
        List<@Valid TarFileSpec> controlFiles;
        List<@Valid TarFileSpec> dataFiles;
    }
}
