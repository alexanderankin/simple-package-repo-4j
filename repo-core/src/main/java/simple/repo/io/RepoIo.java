package simple.repo.io;

import lombok.Data;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.experimental.Accessors;
import simple.repo.model.PackageConfig;
import simple.repo.repository.Repository;

import java.util.Map;

public interface RepoIo<Location extends RepoIo.RepoLocation> {
    static <T> byte[] download(RepoIo<?> io, PackageConfig packageConfig, Repository<T> repository, T coord) {
        return io.downloadPackage(repository.pathTo(coord));
    }

    static <T> void upload(RepoIo<?> io, PackageConfig packageConfig, Repository<T> repository, T coord, byte[] content) {
        io.uploadPackage(repository.pathTo(coord), content);
    }

    Location getLocation();

    RepoIo<Location> setLocation(Location location);

    byte[] downloadPackage(Repository.RepositoryPath repositoryPath);

    void uploadPackage(Repository.RepositoryPath repositoryPath, byte[] content);

    boolean canParseLocation(String location);

    default void mustParseLocation(String location) {
        if (!canParseLocation(location))
            throw new IllegalArgumentException("cannot parse location"); // todo
    }

    Location parseLocation(String location);

    String stringifyLocation(Location location);

    /**
     * marker interface for parsing
     */
    interface RepoLocation {
    }

}
