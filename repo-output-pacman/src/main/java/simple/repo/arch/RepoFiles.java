package simple.repo.arch;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

@Data
@Accessors(chain = true)
public class RepoFiles {
    TreeSet<PkgFolder> pkgFolders;

    @Data
    @Accessors(chain = true)
    public static class PkgFolder implements Comparable<PkgFolder> {
        private static final Comparator<PkgFolder> COMPARATOR =
                Comparator.nullsFirst(Comparator.comparing(PkgFolder::getFolderName));

        /**
         * the folder in the tar archives: $name-$ver-$release
         */
        String folderName;

        /**
         * for {@code desc} file
         */
        DescData desc;

        /**
         * for {@code files} file
         */
        List<String> fileList;

        @Override
        public int compareTo(PkgFolder o) {
            return COMPARATOR.compare(this, o);
        }
    }

    @Data
    @Accessors(chain = true)
    public static class DescData {
        String filename;
        String name;
        String base;
        String version;
        String desc;
        String cSize;
        String iSize;
        String sha256sum;
        String url;
        String license;
        String arch;
        String buildDate;
        String packager;
        String conflicts;
        String depends;
    }
}
