package simple.repo.rpm;

import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import simple.repo.model.Arch;
import simple.repo.model.FileIntegrityWithContent;
import simple.repo.model.PackageConfig;
import simple.repo.model.PackageConfig.FileSpec;
import simple.repo.model.PackageConfig.PackageMeta;
import simple.repo.model.PackageConfig.PkgFileSpec.BinaryPkgFileSpec;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

public class RpmPackageBuilderITest {
    RpmPackageBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new RpmPackageBuilder();
    }

    @SneakyThrows
    @Test
    void rpmFileRecognizedByFileCommand() {
        FileIntegrityWithContent rpm = builder.buildPackage(
                new PackageConfig()
                        .setMeta(new PackageMeta().setName("testpkg").setVersion("1.0.0").setArch(Arch.current()))
                        .setFiles(new FileSpec().setDataFiles(List.of(
                                new BinaryPkgFileSpec().setContent("hello world\n".getBytes()).setPath("/usr/share/test/hello.txt").setMode(0x644))))
        );

        try (GenericContainer<?> genericContainer = new GenericContainer<>("rockylinux/rockylinux:10")) {
            var containerPath = "/tmp/" + rpm.getFileIntegrity().getPath();
            genericContainer
                    .withCreateContainerCmdModifier(c -> c.withEntrypoint("tail", "-f", "/dev/null"))
                    .withCopyToContainer(Transferable.of(rpm.getContent()), containerPath);
            genericContainer.start();
            assertThat(genericContainer.execInContainer("dnf", "install", "file", "-y").getExitCode(), is(0));
            var execFileCommand = genericContainer.execInContainer("file", containerPath);
            assertThat(execFileCommand.getExitCode(), is(0));
            assertThat(execFileCommand.getStdout(), containsString("RPM v3.0 bin"));
        }
    }

    @Disabled("useful early on but now anymore?")
    @SneakyThrows
    @Test
    void buildsRecognizableRpm() {
        FileIntegrityWithContent rpm = builder.buildPackage(
                new PackageConfig()
                        .setMeta(new PackageMeta().setName("testpkg").setVersion("1.0.0").setArch(Arch.current()))
                        .setFiles(new FileSpec().setDataFiles(List.of(
                                new BinaryPkgFileSpec().setContent("hello world\n".getBytes()).setPath("/usr/share/test/hello.txt").setMode(0x644))))
        );

        try (GenericContainer<?> genericContainer = new GenericContainer<>("rockylinux/rockylinux:10")) {
            var containerPath = "/tmp/" + rpm.getFileIntegrity().getPath();
            genericContainer
                    .withCreateContainerCmdModifier(c -> c.withEntrypoint("tail", "-f", "/dev/null"))
                    .withCopyToContainer(Transferable.of(rpm.getContent()), containerPath);
            genericContainer.start();

            var exec = genericContainer.execInContainer("rpm", "-qip", containerPath);
            System.out.println("ExitCode: " + exec.getExitCode());
            System.out.println("Stdout: " + exec.getStdout());
            System.out.println("Stderr: " + exec.getStderr());
            assertThat(exec.getExitCode(), is(0));

            // assertThat(genericContainer.execInContainer("cat", "/usr/share/test/hello.txt").getStdout(), is("hello world\n"));
        }
    }

    @SneakyThrows
    @Test
    void testRpmIvh() {
        var rpm = builder.buildPackage(YAMLMapper.builder().findAndAddModules().build().readValue(
                """
                        meta:
                          arch: arm64
                          name: example-deb
                          version: 0.0.1
                        control:
                          maintainer: maintainer
                          description: description
                        files:
                          controlFiles: []
                          dataFiles:
                            - type: text
                              path: /opt/example-deb/bin/example-deb
                              mode: 0x755
                              content: |
                                #!/usr/bin/env bash
                                echo 'hello world'
                        """,
                PackageConfig.class
        ));

        try (GenericContainer<?> genericContainer = new GenericContainer<>("rockylinux/rockylinux:10")) {
            var containerPath = "/tmp/" + rpm.getFileIntegrity().getPath();
            genericContainer
                    .withCreateContainerCmdModifier(c -> c.withEntrypoint("tail", "-f", "/dev/null"))
                    .withCopyToContainer(Transferable.of(rpm.getContent()), containerPath);
            genericContainer.start();

            var exec = genericContainer.execInContainer("rpm", "-ivh", containerPath);
            System.out.println("ExitCode: " + exec.getExitCode());
            System.out.println("Stdout: " + exec.getStdout());
            System.out.println("Stderr: " + exec.getStderr());
            assertThat(exec.getExitCode(), is(0));
        }
    }
}
