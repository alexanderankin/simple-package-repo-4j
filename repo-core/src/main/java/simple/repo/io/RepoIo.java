package simple.repo.io;

import simple.repo.model.PackageConfig;
import simple.repo.repository.Repository;

/**
 * abstraction for uploading and downloading packages and index files for {@link Repository}
 */
public interface RepoIo<Location extends RepoIo.RepoLocation> {
    static <T> byte[] download(RepoIo<?> io, PackageConfig packageConfig, Repository<T> repository, T coord) {
        return io.downloadPackage(repository.pathTo(coord));
    }

    static <T> void upload(RepoIo<?> io, PackageConfig packageConfig, Repository<T> repository, T coord, byte[] content) {
        io.uploadPackage(repository.pathTo(coord), content);
    }

    /**
     * an io instance should know where it is
     */
    Location getLocation();

    /**
     * @see #getLocation()
     */
    RepoIo<Location> setLocation(Location location);

    byte[] downloadPackage(Repository.RepositoryPath repositoryPath);

    void uploadPackage(Repository.RepositoryPath repositoryPath, byte[] content);

    Iterable<Repository.RepositoryPath> iterFiles(String path);

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
