package simple.repo.packaging;

import simple.repo.model.Arch;
import simple.repo.model.FileIntegrityWithContent;
import simple.repo.model.PackageConfig;

public interface PackageBuilder {
    String outputType();

    String archName(Arch arch);

    String fileName(PackageConfig packageConfig);

    default String indexFileName(PackageConfig packageConfig) {
        return fileName(packageConfig) + ".spr4j-index.json";
    }

    FileIntegrityWithContent buildPackage(PackageConfig packageConfig);

    PackageConfig parseConfigFromPackage(byte[] downloadedPackage);

    byte[] buildIndexFile(PackageConfig packageConfig);
}
