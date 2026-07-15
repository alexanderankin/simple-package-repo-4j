package simple.repo.rpm;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import simple.repo.io.RepoIo;
import simple.repo.model.PackageConfig;
import simple.repo.packaging.PackageBuilder;
import simple.repo.repository.Repository;
import simple.repo.repository.RepositoryBuilder;

import java.util.Iterator;
import java.util.List;

@EqualsAndHashCode(callSuper = false)
@Data
@Accessors(chain = true)
public class RpmRepository extends Repository<RpmRepository.RpmRepoCoord> {
    String poolPath = "pool";

    @Override
    public PackageBuilder packageBuilder() {
        return new RpmPackageBuilder();
    }

    @Override
    public RpmRepoCoord coordinate(List<String> values) {
        var poolPartSize = 0;
        for (var poolPart : poolPath.split("/")) {
            if (!poolPart.isEmpty()) {
                poolPartSize++;
            }
        }

        if (values.size() != 3 + poolPartSize)
            throw new IllegalArgumentException("path must be version/arch/<pool-path>/name, pool-path was: " + poolPath);

        if (!StringUtils.isNumeric(values.getFirst()))
            throw new IllegalArgumentException("path must start with numeric version(/arch/<pool-path>/name), version was: " + values.getFirst());

        RpmArch arch;
        try {
            arch = RpmArch.valueOf(values.get(1));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unrecognized arch", e);
        }

        return new RpmRepoCoord()
                .setVersion(Integer.parseInt(values.getFirst()))
                .setArch(arch)
                .setName(values.getLast());
    }

    @Override
    public RepositoryPath pathTo(RpmRepoCoord coord) {
        return path(coord.version.toString(), coord.arch.name(), poolPath, coord.name);
    }

    @Override
    public RepositoryPath indexOf(RpmRepoCoord coord) {
        return path(coord.version.toString(), coord.arch.name(), "repodata", "repomd.xml");
    }

    @Override
    public <L extends RepoIo.RepoLocation> Iterable<PackageConfig> scanIndexes(RepoIo<L> repoIo) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected <L extends RepoIo.RepoLocation> Iterator<RepositoryPath> iteratePoolPaths(RepoIo<L> repoIo) {
        throw new UnsupportedOperationException();
    }

    @Data
    @Accessors(chain = true)
    public static class RpmRepoCoord {
        Integer version;
        RpmArch arch;
        String name;
    }
}
