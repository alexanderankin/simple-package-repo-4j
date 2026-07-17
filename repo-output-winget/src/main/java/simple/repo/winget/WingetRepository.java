package simple.repo.winget;

import lombok.Data;
import lombok.experimental.Accessors;
import simple.repo.io.RepoIo;
import simple.repo.packaging.PackageBuilder;
import simple.repo.repository.Repository;
import simple.repo.repository.RepositoryBuilder;

import java.util.Iterator;
import java.util.List;
import java.net.URI;

public class WingetRepository extends Repository<WingetRepository.WingetCoordinate> {
    private URI publishedBase;

    @Override
    public WingetRepository setPublishedBase(URI publishedBase) {
        this.publishedBase = publishedBase;
        return this;
    }

    @Override
    public PackageBuilder packageBuilder() {
        return new WingetPackageBuilder();
    }

    @Override
    public RepositoryBuilder repoBuilder() {
        var builder = new WingetRepoBuilder().setPackageBuilder((WingetPackageBuilder) packageBuilder());
        var baseUrl = publishedBase == null ? System.getProperty(RepoOutputWinget.BASE_URL_PROPERTY) : publishedBase.toString();
        if (baseUrl != null && !baseUrl.isBlank()) builder.setBaseUrl(baseUrl);
        var publisher = System.getProperty(RepoOutputWinget.PUBLISHER_PROPERTY);
        if (publisher != null && !publisher.isBlank()) builder.setSourcePublisher(publisher);
        return builder;
    }

    @Override
    public WingetCoordinate coordinate(List<String> values) {
        if (values.size() != 2 || !"packages".equals(values.getFirst()))
            throw new IllegalArgumentException("WinGet package coordinate must be packages/name");
        return new WingetCoordinate().setName(values.getLast());
    }

    @Override
    public RepositoryPath pathTo(WingetCoordinate coordinate) {
        return path("packages", coordinate.name);
    }

    @Override
    public RepositoryPath indexOf(WingetCoordinate coordinate) {
        return path("source.msix");
    }

    @Override
    protected <L extends RepoIo.RepoLocation> Iterator<RepositoryPath> iteratePoolPaths(RepoIo<L> repoIo) {
        return repoIo.iterFiles("packages").iterator();
    }

    @Data
    @Accessors(chain = true)
    public static class WingetCoordinate {
        String name;
    }
}
