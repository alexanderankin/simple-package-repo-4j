package simple.repo.rpm;

import org.junit.jupiter.api.Test;
import simple.repo.model.Arch;
import simple.repo.model.FileIntegrity;
import simple.repo.model.PackageConfig;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RpmRepoBuilderTest {
    @Test
    void buildsRepositoryFromIndexMetadataWithoutPackageOrSourceContent() {
        var config = new PackageConfig()
                .setMeta(new PackageConfig.PackageMeta()
                        .setName("index-only-package")
                        .setVersion("0.0.1")
                        .setReleaseVersion("1")
                        .setArch(Arch.amd64))
                .setControl(new PackageConfig.ControlExtras()
                        .setMaintainer("maintainer")
                        .setDescription("built from index metadata"))
                .setFiles(new PackageConfig.FileSpec());
        var packageIntegrity = new FileIntegrity()
                .setPath("index-only-package-0.0.1-1.x86_64.rpm")
                .setSize(0x1234)
                .setSha256("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        var packageMeta = new RpmRepoBuilder.PackageMeta()
                .setConfig(config)
                .setPackageFile(packageIntegrity)
                .setInstalledSizeBytes(0x4321L)
                .setFilePaths(List.of("/opt/index-only-package/bin/example"));
        var builder = new RpmRepoBuilder();

        var repo = builder.repoBuilder(new RpmRepoBuilder.RepoConfig(), Instant.EPOCH)
                .buildVersion("10")
                .addPackage(packageMeta)
                .build()
                .build();
        var files = builder.buildRepo(repo);

        assertTrue(files.containsKey("10/x86_64/repodata/repomd.xml"));
        assertTrue(files.keySet().stream().anyMatch(path -> path.endsWith("-primary.xml.gz")));
        assertTrue(files.keySet().stream().anyMatch(path -> path.endsWith("-filelists.sqlite.bz2")));
    }
}
