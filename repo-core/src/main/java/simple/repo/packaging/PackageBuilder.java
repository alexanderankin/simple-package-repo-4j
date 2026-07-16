package simple.repo.packaging;

import lombok.SneakyThrows;
import simple.repo.model.*;

import java.nio.file.Files;
import java.nio.file.Path;

public interface PackageBuilder {
    String INDEX_JSON_FILE_EXTENSION = ".spr4j-index.json";

    String outputType();

    String archName(Arch arch);

    String fileName(PackageConfig packageConfig);

    PackageConfig.PackageMeta metaFromFileName(String fileName);

    default String indexFileName(PackageConfig packageConfig) {
        return fileName(packageConfig) + INDEX_JSON_FILE_EXTENSION;
    }

    FileIntegrityWithContent buildPackage(PackageConfig packageConfig);

    PackageConfig parseConfigFromPackage(byte[] downloadedPackage);

    @SneakyThrows
    default IndexFile parseConfigToIndexFile(Path downloadedPackage) {
        var indexFile = parseConfigToIndexFile(Files.readAllBytes(downloadedPackage));
        indexFile.getFileIntegrity().setPath(downloadedPackage.getFileName().toString());
        return indexFile;
    }

    default IndexFile parseConfigToIndexFile(byte[] downloadedPackage) {
        return new IndexFile()
                .setPackageConfig(parseConfigFromPackage(downloadedPackage))
                .setFileIntegrity(FileIntegrity.of(downloadedPackage, null));
    }

    default IndexFile buildIndexFile(byte[] downloadedPackage, PackageConfig packageConfig) {
        return new IndexFile()
                .setPackageConfig(packageConfig)
                .setFileIntegrity(FileIntegrity.of(downloadedPackage, fileName(packageConfig)));
    }
}
