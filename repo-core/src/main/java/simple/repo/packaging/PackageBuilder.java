package simple.repo.packaging;

import simple.repo.model.Arch;
import simple.repo.model.FileIntegrityWithContent;
import simple.repo.model.PackageConfig;

public interface PackageBuilder {
    String outputType();

    String archName(Arch arch);

    String fileName(PackageConfig packageConfig);

    FileIntegrityWithContent buildPackage(PackageConfig  packageConfig);
}
