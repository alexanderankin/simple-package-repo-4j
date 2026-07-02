package simple.repo.deb;

import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.collections4.IteratorUtils;
import org.springframework.util.Assert;
import simple.repo.io.RepoIo;
import simple.repo.model.PackageConfig;
import simple.repo.packaging.PackageBuilder;
import simple.repo.repository.Repository;

import java.util.Iterator;
import java.util.List;

public class DebRepository extends Repository<DebRepository.DebRepoCoord> {
    @Override
    public PackageBuilder packageBuilder() {
        return new DebPackageBuilder();
    }

    @Override
    public DebRepoCoord coordinate(List<String> values) {
        if (values.size() != 3)
            throw new IllegalArgumentException("wrong size, should be pool/codename/name");

        DebRepoCoord coord = new DebRepoCoord();
        try {
            coord.setType(DebRepoCoord.DebCoordType.valueOf(values.getFirst()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("first part not 'pool' or 'dists'", e);
        }
        if (coord.getType() != DebRepoCoord.DebCoordType.pool)
            throw new IllegalArgumentException("coordinate only parses package coordinates");

        coord.setCodename(values.get(1));

        // todo validate name?
        coord.setName(values.getLast());

        return coord;
    }

    @Override
    public RepositoryPath pathTo(DebRepoCoord coord) {
        Assert.isTrue(coord.type == DebRepoCoord.DebCoordType.pool, "coordinate must have type 'pool' to get path to package");
        return path("pool", coord.codename, coord.name);
    }

    public RepositoryPath indexOf(DebRepoCoord coord) {
        Assert.isTrue(coord.type == DebRepoCoord.DebCoordType.dists, "coordinate must have type 'dists' to get path to index");
        Assert.isTrue(coord.component != null, "coordinate must have component to get path to binary package index");
        Assert.isTrue(coord.arch != null, "coordinate must have arch to get path to binary package index");

        return path("dists", coord.codename, coord.component, "binary-" + coord.arch, "Packages.gz");
    }

    protected <L extends RepoIo.RepoLocation> Iterator<RepositoryPath> iteratePoolPaths(RepoIo<L> repoIo) {
        return repoIo.iterFiles("pool").iterator();
    }

    @Data
    @Accessors(chain = true)
    public static class DebRepoCoord {
        DebCoordType type;
        String codename;
        String name;
        String component = "main";
        String arch = "amd64";

        public enum DebCoordType { pool, dists }
    }
}
