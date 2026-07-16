package simple.repo.deb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import simple.repo.model.Arch;
import simple.repo.model.PackageConfig;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

class DebPackageBuilderTest {
    DebPackageBuilder debPackageBuilder;

    @BeforeEach
    void setUp() {
        debPackageBuilder = new DebPackageBuilder();
    }

    @Test
    void outputType() {
        assertThat(debPackageBuilder.outputType(), is("deb"));
    }

    @ParameterizedTest
    @CsvSource({
            "amd64,  amd64",
            "arm64,  arm64",
            "arm,    armhf",
            "riscv64,riscv64",
    })
    void archName(String arch, String expected) {
        assertThat(debPackageBuilder.archName(Arch.valueOf(arch.strip())), is(equalTo(expected.strip())));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "arm64,test-package,0.0.1,null,test-package_0.0.1_arm64.deb",
            "arm64,test-package,0.0.1,1,test-package_0.0.1-1_arm64.deb",
            "amd64,test-package,0.0.3,3,test-package_0.0.3-3_amd64.deb",
    }, nullValues = "null")
    void fileName(String archName, String name, String version, String rv, String expected) {
        assertThat(debPackageBuilder.fileName(new PackageConfig().setMeta(new PackageConfig.PackageMeta()
                .setArch(Arch.valueOf(archName))
                .setName(name)
                .setReleaseVersion(rv)
                .setVersion(version))
        ), is(equalTo(expected)));
    }

    @Test
    void parsesMetadataFromSidecarFileName() {
        var meta = debPackageBuilder.metaFromFileName("example-tools_1.10.0-7_arm64.deb.spr4j-index.json");

        assertThat(meta.getName(), is("example-tools"));
        assertThat(meta.getVersion(), is("1.10.0"));
        assertThat(meta.getReleaseVersion(), is("7"));
        assertThat(meta.getArch(), is(Arch.arm64));
    }
}
