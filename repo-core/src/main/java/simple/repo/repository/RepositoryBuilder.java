package simple.repo.repository;

import org.jetbrains.annotations.Contract;
import simple.repo.io.RepoIo;
import simple.repo.model.FileIntegrityWithContent;
import simple.repo.model.IndexFile;
import simple.repo.model.PackageConfig;
import simple.repo.packaging.PackageBuilder;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface RepositoryBuilder {
    PackageBuilder getPackageBuilder();

    default List<String> defaultTargets() {
        return List.of();
    }

    default void prepareSigning(byte[] privateKey, byte[] publicKey) {
    }

    String targetFromIndexPath(Repository.RepositoryPath indexPath);

    @Contract(pure = true)
    PackageConfig prepareTarget(PackageConfig packageConfig, String target);

    Repository.RepositoryPath packagePath(String target, PackageConfig packageConfig);

    default Repository.RepositoryPath indexPath(String target, PackageConfig packageConfig) {
        return packagePath(target, packageConfig).neighbor(getPackageBuilder().indexFileName(packageConfig));
    }

    Map<String, FileIntegrityWithContent> build(Map<String, List<IndexFile>> packagesByTarget, Instant now);

    List<IndexFile> readPublished(RepoIo<?> repoIo,
                                  String target,
                                  Collection<IndexFile> additions,
                                  RepositoryInitialization initialization);

    Map<String, FileIntegrityWithContent> sign(Map<String, FileIntegrityWithContent> repositoryFiles,
                                               byte[] privateKey,
                                               byte[] publicKey,
                                               Instant now);
}
