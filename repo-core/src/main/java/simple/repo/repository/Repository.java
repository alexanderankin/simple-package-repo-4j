package simple.repo.repository;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.List;

@Data
@Accessors(chain = true)
public abstract class Repository<Coordinate> {
    String root;

    static RepositoryPath path(String root, String... parts) {
        return new RepositoryPath().setRoot(root).setParts(Arrays.asList(parts));
    }

    public abstract RepositoryPath pathTo(Coordinate coordinate);

    public abstract RepositoryPath indexOf(Coordinate coordinate);

    @Data
    @Accessors(chain = true)
    public static class RepositoryPath {
        String root;
        List<String> parts;

        /**
         *
         * @return root joined by '/' with all non-empty paths also joined with '/'
         */
        public String joinParts() {
            var sb = new StringBuilder(root);
            if (!parts.isEmpty()) {
                for (var part : parts) {
                    if (!part.isEmpty()) {
                        sb.append('/').append(part);
                    }
                }
            }
            return sb.toString();
        }
    }

    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class DebRepo extends Repository<DebRepo.DebRepoCoord> {
        @Override
        public RepositoryPath pathTo(DebRepoCoord coord) {
            return path(root, "pool", coord.codename, coord.name);
        }

        public RepositoryPath indexOf(DebRepoCoord coord) {
            return path(root, "dists", coord.codename, coord.component, "binary-" + coord.arch, "Packages.gz");
        }

        @Data
        @Accessors(chain = true)
        public static class DebRepoCoord {
            String codename;
            String component = "main";
            String arch = "amd64";
            String name;
        }
    }

    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class RpmRepo extends Repository<RpmRepo.RpmRepoCoord> {
        /**
         * blank will skip and treat as if to colocate rpm files with the {@code repodata} folder
         *
         * @see RepositoryPath#joinParts()
         */
        @NonNull
        String poolPath = "pool";

        @Override
        public RepositoryPath pathTo(RpmRepoCoord coord) {
            return path(root, coord.version, coord.arch, poolPath, coord.name);
        }

        public RepositoryPath indexOf(RpmRepoCoord coord) {
            return path(root, coord.version, coord.arch, "repodata", "repomd.xml");
        }

        @Data
        @Accessors(chain = true)
        public static class RpmRepoCoord {
            String version;
            String arch;
            String name;
        }
    }

    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class ArchRepo extends Repository<ArchRepo.ArchRepoCoord> {
        @Override
        public RepositoryPath pathTo(ArchRepoCoord coord) {
            return path(root, coord.arch, coord.repo, coord.name);
        }

        public RepositoryPath indexOf(ArchRepoCoord coord) {
            return path(root, coord.arch, coord.repo, coord.repo + ".db");
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
            return path(root, coord.branch, coord.repository, coord.arch, coord.name);
        }

        public RepositoryPath indexOf(AlpineRepoCoord coord) {
            return path(root, coord.branch, coord.repository, coord.arch, "APKINDEX.tar.gz");
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
                case FORMULA -> path(root, "Formula", coord.name + ".rb");
                case CASK -> path(root, "Casks", coord.name + ".rb");
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
                    root,
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
}
