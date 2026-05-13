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
            "aarch64,arm64",
            "arm,    armhf",
            "riscv64,riscv64",
    })
    void archName(String arch, String expected) {
        assertThat(debPackageBuilder.archName(Arch.valueOf(arch.strip())), is(equalTo(expected.strip())));
    }

    @Test
    void fileName() {
        assertThat(debPackageBuilder.fileName(new PackageConfig().setMeta(new PackageConfig.PackageMeta()
                .setArch(Arch.aarch64)
                .setName("test-package")
                .setVersion("0.0.1"))), is(equalTo("test-package_0.0.1_arm64.deb")));
    }
}
