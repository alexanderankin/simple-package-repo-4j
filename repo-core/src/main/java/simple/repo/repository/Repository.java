package simple.repo.repository;

import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.collections4.IteratorUtils;
import simple.repo.io.RepoIo;
import simple.repo.model.PackageConfig;
import simple.repo.packaging.PackageBuilder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public abstract class Repository<Coordinate> {
    protected static RepositoryPath path(String... parts) {
        return new RepositoryPath().setParts(Arrays.asList(parts));
    }

    public abstract PackageBuilder packageBuilder();

    public Coordinate coordinate(String... values) {
        return coordinate(Arrays.asList(values));
    }

    /**
     * for now this is packages only??
     */
    public abstract Coordinate coordinate(List<String> values);

    public abstract RepositoryPath pathTo(Coordinate coordinate);

    public abstract RepositoryPath indexOf(Coordinate coordinate);

    public <L extends RepoIo.RepoLocation> Iterable<PackageConfig> scanIndexes(RepoIo<L> repoIo) {
        return () -> IteratorUtils.transformedIterator(
                IteratorUtils.filteredIterator(
                        iteratePoolPaths(repoIo),
                        f -> f.joinParts().endsWith(".index.json")
                ),
                input -> packageBuilder().parseConfigFromPackage(repoIo.downloadPackage(input))
        );
    }

    protected abstract <L extends RepoIo.RepoLocation> Iterator<RepositoryPath> iteratePoolPaths(RepoIo<L> repoIo);

    @Data
    @Accessors(chain = true)
    public static class RepositoryPath {
        List<String> parts;

        public static RepositoryPath of(List<String> parts) {
            return new RepositoryPath().setParts(parts);
        }

        public Path asLocalPath() {
            return parts.isEmpty() ? Path.of("") : Path.of(parts.getFirst(), parts.subList(1, parts.size()).toArray(new String[0]));
        }

        /**
         *
         * @return root joined by '/' with all non-empty paths also joined with '/'
         */
        public String joinParts() {
            var sb = new StringBuilder();
            if (!parts.isEmpty()) {
                for (var part : parts) {
                    if (!part.isEmpty()) {
                        sb.append('/').append(part);
                    }
                }
            }
            return sb.toString();
        }

        public RepositoryPath neighbor(String neighborName) {
            var parts = new ArrayList<>(this.parts);
            parts.set(parts.size() - 1, neighborName);
            return new RepositoryPath()
                    .setParts(parts);
        }
    }

    /*
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class ArchRepo extends Repository<ArchRepo.ArchRepoCoord> {
        @Override
        public RepositoryPath pathTo(ArchRepoCoord coord) {
            return path(coord.arch, coord.repo, coord.name);
        }

        public RepositoryPath indexOf(ArchRepoCoord coord) {
            return path(coord.arch, coord.repo, coord.repo + ".db");
        }

        @Data
        @Accessors(chain = true)
        public static class ArchRepoCoord {
            String arch;
            String repo;
            String name;
        }
    }

    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class AlpineRepo extends Repository<AlpineRepo.AlpineRepoCoord> {
        @Override
        public RepositoryPath pathTo(AlpineRepoCoord coord) {
            return path(coord.branch, coord.repository, coord.arch, coord.name);
        }

        public RepositoryPath indexOf(AlpineRepoCoord coord) {
            return path(coord.branch, coord.repository, coord.arch, "APKINDEX.tar.gz");
        }

        @Data
        @Accessors(chain = true)
        public static class AlpineRepoCoord {
            String branch;      // edge, v3.20
            String repository;  // main, community, testing
            String arch;        // x86_64, aarch64
            String name;
        }
    }

    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class HomebrewRepo extends Repository<HomebrewRepo.HomebrewRepoCoord> {
        @Override
        public RepositoryPath pathTo(HomebrewRepoCoord coord) {
            return switch (coord.kind) {
                case FORMULA -> path("Formula", coord.name + ".rb");
                case CASK -> path("Casks", coord.name + ".rb");
            };
        }

        @Override
        public RepositoryPath indexOf(HomebrewRepoCoord homebrewRepoCoord) {
            throw new UnsupportedOperationException();
        }

        public enum Kind {
            FORMULA,
            CASK
        }

        @Data
        @Accessors(chain = true)
        public static class HomebrewRepoCoord {
            Kind kind = Kind.FORMULA;
            String name;
        }
    }

    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class WingetRepo extends Repository<WingetRepo.WingetRepoCoord> {
        @Override
        public RepositoryPath pathTo(WingetRepoCoord coord) {
            return path(
                    "manifests",
                    coord.publisher.substring(0, 1).toLowerCase(),
                    coord.publisher,
                    coord.packageName,
                    coord.version,
                    coord.manifestFile()
            );
        }

        @Override
        public RepositoryPath indexOf(WingetRepoCoord wingetRepoCoord) {
            throw new UnsupportedOperationException();
        }

        public enum ManifestKind {
            DEFAULT,
            INSTALLER,
            VERSION,
            LOCALE
        }

        @Data
        @Accessors(chain = true)
        public static class WingetRepoCoord {
            String publisher;
            String packageName;
            String version;
            ManifestKind kind = ManifestKind.DEFAULT;
            String locale;

            String manifestFile() {
                String base = publisher + "." + packageName;

                return switch (kind) {
                    case DEFAULT -> base + ".yaml";
                    case INSTALLER -> base + ".installer.yaml";
                    case VERSION -> base + ".version.yaml";
                    case LOCALE -> base + ".locale." + locale + ".yaml";
                };
            }
        }
    }
    */

}
