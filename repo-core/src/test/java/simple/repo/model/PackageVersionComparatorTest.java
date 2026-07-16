package simple.repo.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PackageVersionComparatorTest {

    int compare(PackageConfig.PackageMeta l, PackageConfig.PackageMeta r) {
        return PackageVersionComparator.INSTANCE.compare(l, r);
    }

    @Test
    void comparesSemanticVersionsAndPrereleases() {
        assertTrue(compare(meta("1.10.0", "1"), meta("1.9.0", "9")) > 0);
        assertTrue(compare(meta("2.0.0", "1"), meta("2.0.0-rc.1", "1")) > 0);
    }

    @Test
    void usesNaturalOrderingAndReleaseAsATieBreaker() {
        assertTrue(compare(meta("build10", "1"), meta("build9", "99")) > 0);
        assertTrue(compare(meta("1.0.0", "10"), meta("1.0.0", "2")) > 0);
    }

    private PackageConfig.PackageMeta meta(String version, String release) {
        return new PackageConfig.PackageMeta().setName("example").setVersion(version).setReleaseVersion(release).setArch(Arch.amd64);
    }
}
