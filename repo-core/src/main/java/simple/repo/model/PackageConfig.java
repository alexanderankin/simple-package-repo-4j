package simple.repo.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;

@Dto
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
    @JsonAlias("file")
    PackageConfig.FileSpec files;
    @Valid
    Settings settings;

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = PkgFileSpec.TextPkgFileSpec.class, name = "text"),
            @JsonSubTypes.Type(value = PkgFileSpec.BinaryPkgFileSpec.class, name = "binary"),
            @JsonSubTypes.Type(value = PkgFileSpec.FilePkgFileSpec.class, name = "file"),
            @JsonSubTypes.Type(value = PkgFileSpec.DirPkgFileSpec.class, name = "dir"),
            @JsonSubTypes.Type(value = PkgFileSpec.UrlPkgFileSpec.class, name = "url"),
    })
    @Dto
    @Data
    @Accessors(chain = true)
    public static sealed abstract class PkgFileSpec {
        @NotBlank
        String path;
        Integer mode;

        @ToString(callSuper = true)
        @EqualsAndHashCode(callSuper = true)
        @Dto
        @Data
        @Accessors(chain = true)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static final class TextPkgFileSpec extends PkgFileSpec {
            @NotBlank
            String content;
        }

        @ToString(callSuper = true)
        @EqualsAndHashCode(callSuper = true)
        @Dto
        @Data
        @Accessors(chain = true)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static final class BinaryPkgFileSpec extends PkgFileSpec {
            @NotEmpty
            byte[] content;
        }

        @ToString(callSuper = true)
        @EqualsAndHashCode(callSuper = true)
        @Dto
        @Data
        @Accessors(chain = true)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static final class FilePkgFileSpec extends PkgFileSpec {
            @NotBlank
            String sourcePath;
        }

        @ToString(callSuper = true)
        @EqualsAndHashCode(callSuper = true)
        @Dto
        @Data
        @Accessors(chain = true)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static final class DirPkgFileSpec extends PkgFileSpec {
            @NotBlank
            String sourcePath;
            @NotNull
            ModeMode modeMode = ModeMode.INHERIT;
            LinkedHashMap<String, Integer> modeOverrides;

            public enum ModeMode {
                INHERIT,
                OVERRIDE,
            }
        }

        @ToString(callSuper = true)
        @EqualsAndHashCode(callSuper = true)
        @Dto
        @Data
        @Accessors(chain = true)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static final class UrlPkgFileSpec extends PkgFileSpec {
            @NotNull
            URI url;
            String bearerToken;
            LinkedHashMap<String, List<String>> headers;
        }
    }

    @Dto
    @Data
    @Accessors(chain = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PackageMeta implements Comparable<PackageMeta> {
        // public static final String SD_INDEX_EXTENSION = ".simple-deb-4j-index.json";
        @NotBlank
        String name;
        @NotBlank
        String version;
        @NotNull
        Arch arch;
        /**
         * repository maintainer's version
         */
        String releaseVersion;
        /**
         * red-hat variant identifier
         */
        String elVersion;
        /**
         * e.g. {@code CTO <cto@contoso.com>}
         */
        String releaser;

        @Override
        public int compareTo(@NonNull PackageMeta other) {
            return PackageVersionComparator.INSTANCE.compare(this, other);
        }
    }

    @Dto
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

    @Dto
    @Data
    @Accessors(chain = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FileSpec {
        @JsonAlias("control")
        List<@Valid PkgFileSpec> controlFiles;
        @JsonAlias("data")
        List<@Valid PkgFileSpec> dataFiles;
    }

    @Data
    @Accessors(chain = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Settings {
        public static int DEFAULT_DIRECTORY_MODE_DEFAULT = 0x755;

        /**
         * used when need to create a directory inside the package to contain a file
         */
        Integer defaultDirMode;
    }
}
