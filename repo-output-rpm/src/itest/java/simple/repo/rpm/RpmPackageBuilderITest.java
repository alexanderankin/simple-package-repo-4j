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

    @Disabled("useful early on but now anymore?")
    @SneakyThrows
    @Test
    void rpmFileRecognizedByFileCommand() {
        FileIntegrityWithContent rpm = builder.buildPackage(
                new PackageConfig()
                        .setMeta(new PackageMeta().setName("testpkg").setVersion("1.0.0").setArch(Arch.current()))
                        .setFiles(new FileSpec().setDataFiles(List.of(
                                new BinaryPkgFileSpec().setContent("hello world\n".getBytes()).setPath("/usr/share/test/hello.txt").setMode(0x644))))
        );

        try (GenericContainer<?> genericContainer = rpmContainer(rpm)) {
            genericContainer.start();
            assertThat(genericContainer.execInContainer("dnf", "install", "file", "-y").getExitCode(), is(0));
            var execFileCommand = genericContainer.execInContainer("file", rpmPath(rpm));
            assertThat(execFileCommand.getExitCode(), is(0));
            assertThat(execFileCommand.getStdout(), containsString("RPM v3.0 bin"));
        }
    }

    @SneakyThrows
    @Test
    void buildsRecognizableRpm() {
        FileIntegrityWithContent rpm = builder.buildPackage(
                new PackageConfig()
                        .setMeta(new PackageMeta().setName("testpkg").setVersion("1.0.0").setArch(Arch.current()))
                        .setFiles(new FileSpec().setDataFiles(List.of(
                                new BinaryPkgFileSpec().setContent("hello world\n".getBytes()).setPath("/usr/share/test/hello.txt").setMode(0x644))))
        );

        try (GenericContainer<?> genericContainer = rpmContainer(rpm)) {
            genericContainer.start();

            var exec = genericContainer.execInContainer("rpm", "-qip", rpmPath(rpm));
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
                          arch: __CURRENT_ARCH__
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
                        """.replace("__CURRENT_ARCH__", Arch.current().name()),
                PackageConfig.class
        ));

        try (GenericContainer<?> genericContainer = rpmContainer(rpm)) {
            genericContainer.start();

            var exec = genericContainer.execInContainer("rpm", "-ivh", rpmPath(rpm));
            System.out.println("ExitCode: " + exec.getExitCode());
            System.out.println("Stdout: " + exec.getStdout());
            System.out.println("Stderr: " + exec.getStderr());
            assertThat(exec.getExitCode(), is(0));
        }
    }

    @SneakyThrows
    @Test
    void installsExecutableAndConfigFileInNestedDirectories() {
        var rpm = builder.buildPackage(YAMLMapper.builder().findAndAddModules().build().readValue(
                """
                        meta:
                          arch: __CURRENT_ARCH__
                          name: example-files
                          version: 0.0.1
                        control:
                          maintainer: maintainer
                          description: package with nested executable and config files
                        files:
                          controlFiles: []
                          dataFiles:
                            - type: text
                              path: /opt/example/bin/example-hello-world
                              mode: 493
                              content: |
                                #!/bin/sh
                                echo 'hello world'
                            - type: text
                              path: /etc/example/hello-world/greeting.conf
                              mode: 420
                              content: |
                                greeting=hello world
                        """.replace("__CURRENT_ARCH__", Arch.current().name()),
                PackageConfig.class));

        try (var container = rpmContainer(rpm)) {
            container.start();
            assertThat(container.execInContainer("rpm", "-ivh", rpmPath(rpm)).getExitCode(), is(0));
            assertThat(container.execInContainer("test", "-d", "/opt/example/bin").getExitCode(), is(0));
            assertThat(container.execInContainer("test", "-d", "/etc/example/hello-world").getExitCode(), is(0));
            assertThat(container.execInContainer("test", "-x", "/opt/example/bin/example-hello-world").getExitCode(), is(0));
            assertThat(container.execInContainer("test", "!", "-x", "/etc/example/hello-world/greeting.conf").getExitCode(), is(0));
            assertThat(container.execInContainer("/opt/example/bin/example-hello-world").getStdout(), is("hello world\n"));
            assertThat(container.execInContainer("cat", "/etc/example/hello-world/greeting.conf").getStdout(),
                    is("greeting=hello world\n"));
        }
    }

    @SneakyThrows
    @Test
    void runsInstallAndUninstallControlScripts() {
        var rpm = builder.buildPackage(YAMLMapper.builder().findAndAddModules().build().readValue(
                """
                        meta:
                          arch: __CURRENT_ARCH__
                          name: example-scripts
                          version: 0.0.1
                        control:
                          maintainer: maintainer
                          description: package with install and uninstall scripts
                        files:
                          controlFiles:
                            - type: text
                              path: preinst
                              content: |
                                mkdir -p /etc/example-scripts
                                echo config > /etc/example-scripts/config-file
                            - type: text
                              path: postrm
                              content: |
                                rm -f /etc/example-scripts/config-file
                          dataFiles:
                            - type: text
                              path: /opt/example-scripts/bin/print-config-file
                              mode: 493
                              content: |
                                #!/bin/sh
                                cat /etc/example-scripts/config-file
                        """.replace("__CURRENT_ARCH__", Arch.current().name()),
                PackageConfig.class));

        try (var container = rpmContainer(rpm)) {
            container.start();
            assertThat(container.execInContainer("test", "!", "-e", "/etc/example-scripts/config-file").getExitCode(), is(0));
            assertThat(container.execInContainer("rpm", "-ivh", rpmPath(rpm)).getExitCode(), is(0));
            assertThat(container.execInContainer("cat", "/etc/example-scripts/config-file").getStdout(),
                    is("config\n"));
            assertThat(container.execInContainer("/opt/example-scripts/bin/print-config-file").getStdout(),
                    is("config\n"));
            assertThat(container.execInContainer("rpm", "-e", "example-scripts").getExitCode(), is(0));
            assertThat(container.execInContainer("test", "!", "-e", "/etc/example-scripts/config-file").getExitCode(), is(0));
        }
    }

    private GenericContainer<?> rpmContainer(FileIntegrityWithContent rpm) {
        return new GenericContainer<>("rockylinux/rockylinux:10")
                .withCreateContainerCmdModifier(c -> c.withEntrypoint("tail", "-f", "/dev/null"))
                .withCopyToContainer(Transferable.of(rpm.getContent()), rpmPath(rpm));
    }

    private String rpmPath(FileIntegrityWithContent rpm) {
        return "/tmp/" + rpm.getFileIntegrity().getPath();
    }
}
