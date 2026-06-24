package simple.repo.repository;

import simple.repo.model.FileIntegrityWithContent;
import simple.repo.model.PackageConfig;

import java.util.List;

// placeholder, need to learn real pattern empirically
public interface RepositoryBuilder {
    List<FileIntegrityWithContent> build(List<PackageConfig> repositoryPackages);
}
