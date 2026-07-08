package simple.repo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.experimental.Accessors;

@Dto
@Data
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class IndexFile {
    PackageConfig packageConfig;
    FileIntegrity fileIntegrity;
}
