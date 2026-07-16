package simple.repo.model;

import org.jspecify.annotations.NonNull;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

public final class PackageVersionComparator implements Comparator<PackageConfig.PackageMeta> {
    public static final PackageVersionComparator INSTANCE = new PackageVersionComparator();

    @SuppressWarnings("unchecked")
    private static final Comparator<String> NATURAL = Comparator.nullsLast(Comparator.comparing(
            left -> Arrays.stream(left.split("\\.|(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)"))
                    .map(e -> e.matches("^\\d+$") ? Integer.parseInt(e) : e)
                    .map(Comparable.class::cast)
                    .toList(),
            ListComparator.comparatorFor()));

    private static final Pattern SEMVER = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$");

    @Override
    public int compare(PackageConfig.PackageMeta left, PackageConfig.PackageMeta right) {
        var version = compareVersion(left.getVersion(), right.getVersion());
        return version != 0 ? version : NATURAL.compare(left.getReleaseVersion(), right.getReleaseVersion());
    }

    public int compareVersion(String left, String right) {
        var leftSemver = Semver.parse(left);
        var rightSemver = Semver.parse(right);
        if (leftSemver != null && rightSemver != null) {
            return leftSemver.compareTo(rightSemver);
        }
        if (leftSemver != null) {
            return 1;
        }
        if (rightSemver != null) {
            return -1;
        }
        return NATURAL.compare(left, right);
    }

    private record Semver(int major, int minor, int patch, String prerelease)
            implements Comparable<Semver> {
        static final Comparator<Semver> SEMVER_COMPARATOR = Comparator.comparing(Semver::major)
                .thenComparing(Semver::minor)
                .thenComparing(Semver::patch)
                .thenComparing(Semver::prerelease, NATURAL);

        static Semver parse(String value) {
            if (value == null)
                return null;
            var matcher = SEMVER.matcher(value);
            if (!matcher.matches())
                return null;
            return new Semver(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)),
                    matcher.group(4)
            );
        }

        @Override
        public int compareTo(@NonNull Semver other) {
            return SEMVER_COMPARATOR.compare(this, other);
        }
    }

    // https://stackoverflow.com/a/35761935
    private static class ListComparator<T extends Comparable<T>> implements Comparator<List<T>> {
        static final ListComparator<?> COMPARATOR = new ListComparator<>();

        @SuppressWarnings("unchecked")
        static <T extends Comparable<T>> ListComparator<T> comparatorFor() {
            return (ListComparator<T>) COMPARATOR;
        }

        @Override
        public int compare(List<T> o1, List<T> o2) {
            for (int i = 0; i < Math.min(o1.size(), o2.size()); i++) {
                int c = o1.get(i).compareTo(o2.get(i));
                if (c != 0) {
                    return c;
                }
            }
            return Integer.compare(o1.size(), o2.size());
        }
    }
}
