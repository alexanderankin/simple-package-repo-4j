package simple.repo.deb;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import simple.repo.model.Arch;
import simple.repo.model.PackageConfig;
import simple.repo.model.PackageConfig.FileSpec;
import simple.repo.model.PackageConfig.PackageMeta;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class DebPackageBuilderITest {
    DebPackageBuilder buildDeb = new DebPackageBuilder();
    YAMLMapper yamlMapper = YAMLMapper.builder().findAndAddModules().build();
    ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
    Validator validator = validatorFactory.getValidator();

    <T> T validate(T object) {
        var errors = validator.validate(object);
        if (!errors.isEmpty())
            throw new ConstraintViolationException(errors);
        return object;
    }

    @SneakyThrows
    @Test
    void test_simpleInstall() {
        PackageMeta meta = new PackageMeta()
                .setName("test_simpleInstall")
                .setArch(Arch.current())
                .setVersion("0.0.1");
        var deb = buildDeb.buildPackage(
                validate(new PackageConfig()
                        .setMeta(meta)
                        .setControl(new PackageConfig.ControlExtras()
                                .setMaintainer("test_simpleInstall")
                                .setDescription("test_simpleInstall"))
                        .setFiles(new FileSpec()
                                .setDataFiles(List.of(new PackageConfig.PkgFileSpec.TextPkgFileSpec()
                                        .setContent("#!/usr/bin/env bash\necho test_simpleInstall")
                                        .setMode(0x755)
                                        .setPath("/usr/bin/test_simpleInstall")))))
        );

        var fileName = deb.getFileIntegrity().getPath();

        try (GenericContainer<?> genericContainer = new GenericContainer<>("debian:12-slim")) {
            genericContainer
                    .withCreateContainerCmdModifier(c -> c.withEntrypoint("tail", "-f", "/dev/null"))
                    .withCopyToContainer(Transferable.of(deb.getContent()), "/tmp/" + fileName);
            genericContainer.start();
            var result = genericContainer.execInContainer("dpkg", "-i", "/tmp/" + fileName);
            System.out.println(result.getStderr());
            System.out.println(result.getStdout());
            assertEquals(0, result.getExitCode());

            result = genericContainer.execInContainer("test_simpleInstall");
            assertEquals("", result.getStderr().strip());
            assertEquals("test_simpleInstall", result.getStdout().strip());
            assertEquals(0, result.getExitCode());
        }
    }

    @SneakyThrows
    @Test
    void installsEmptyPackageAndRunsPostinst() {
        var config = validate(yamlMapper.readValue("""
                meta:
                  name: simple-postinst
                  version: 0.0.1
                  arch: arm64
                control:
                  maintainer: maintainer
                  description: description
                files:
                  controlFiles:
                    - type: text
                      path: postinst
                      content: touch /tmp/simple-postinst
                      mode: 0x755
                  dataFiles: []
                """, PackageConfig.class));
        var deb = buildDeb.buildPackage(config);
        assertEquals(1, config.getFiles().getControlFiles().size());
        assertEquals(Integer.parseInt("755", 16), config.getFiles().getControlFiles().getFirst().getMode());

        try (var container = packageContainer(deb)) {
            container.start();
            assertSuccess(container.execInContainer("dpkg", "-i", packagePath(deb)));
            assertSuccess(container.execInContainer("test", "-f", "/tmp/simple-postinst"));
        }
    }

    @SneakyThrows
    @Test
    void installsFileBackedFileSpec() {
        var resourceDir = Path.of(Objects.requireNonNull(
                getClass().getResource("/simple/repo/deb/my-file")).toURI()).getParent();
        buildDeb.getFileSpecReader().setCurrent(resourceDir);
        var config = validate(yamlMapper.readValue("""
                meta:
                  name: file-file
                  version: 0.0.1
                  arch: arm64
                control:
                  maintainer: maintainer
                  description: description
                files:
                  controlFiles:
                    - type: text
                      content: mkdir -p /etc/file-file
                      mode: 0x755
                      path: preinst
                  dataFiles:
                    - type: file
                      path: /etc/file-file/my-file
                      sourcePath: my-file
                """, PackageConfig.class));
        var deb = buildDeb.buildPackage(config);

        try (var container = packageContainer(deb)) {
            container.start();
            assertSuccess(container.execInContainer("dpkg", "-i", packagePath(deb)));
            assertEquals("example content\n",
                    container.execInContainer("cat", "/etc/file-file/my-file").getStdout());
        }
    }

    @SneakyThrows
    @Test
    void enforcesPackageConflicts() {
        var aConfig = packageWithSingleTextFile("conflicts-a", "a", "conflicts-b", "/etc/conflicts-pkg");
        var bConfig = packageWithSingleTextFile("conflicts-b", "b", "conflicts-a", "/etc/conflicts-pkg");
        var a = buildDeb.buildPackage(validate(aConfig));
        var b = buildDeb.buildPackage(validate(bConfig));

        try (var container = new GenericContainer<>("debian:13-slim")) {
            container.withCreateContainerCmdModifier(c -> c.withEntrypoint("tail", "-f", "/dev/null"))
                    .withCopyToContainer(Transferable.of(a.getContent()), packagePath(a))
                    .withCopyToContainer(Transferable.of(b.getContent()), packagePath(b));
            container.start();
            assertSuccess(container.execInContainer("dpkg", "-i", packagePath(a)));
            assertEquals("a", container.execInContainer("cat", "/etc/conflicts-pkg").getStdout());
            assertNotEquals(0, container.execInContainer("dpkg", "-i", packagePath(b)).getExitCode());
            assertSuccess(container.execInContainer("dpkg", "--remove", "conflicts-a"));
            assertSuccess(container.execInContainer("dpkg", "-i", packagePath(b)));
            assertEquals("b", container.execInContainer("cat", "/etc/conflicts-pkg").getStdout());
        }
    }

    @SneakyThrows
    @Test
    void installsDirectoryFileSpecRecursively() {
        var resourceDir = Path.of(Objects.requireNonNull(
                getClass().getResource("/simple/repo/deb/spec-type-dir")).toURI());
        buildDeb.getFileSpecReader().setCurrent(resourceDir.getParent());
        var config = validate(new PackageConfig()
                .setMeta(new PackageMeta().setName("spec-type-dir").setVersion("0.0.1").setArch(Arch.current()))
                .setControl(new PackageConfig.ControlExtras().setMaintainer("m").setDescription("d"))
                .setFiles(new FileSpec().setControlFiles(List.of()).setDataFiles(List.of(
                        new PackageConfig.PkgFileSpec.DirPkgFileSpec()
                                .setSourcePath("spec-type-dir")
                                .setPath("/opt/spec-type-dir")))));
        var deb = buildDeb.buildPackage(config);

        try (var container = packageContainer(deb)) {
            container.start();
            assertSuccess(container.execInContainer("apt-get", "install", "-y", packagePath(deb)));
            var find = container.execInContainer("find", "/opt/spec-type-dir");
            assertSuccess(find);
            assertEquals("""
                    /opt/spec-type-dir
                    /opt/spec-type-dir/a
                    /opt/spec-type-dir/a/1
                    /opt/spec-type-dir/a/2
                    /opt/spec-type-dir/a/3
                    /opt/spec-type-dir/b
                    /opt/spec-type-dir/b/README.txt
                    /opt/spec-type-dir/b/file.txt
                    /opt/spec-type-dir/c
                    /opt/spec-type-dir/c/test
                    """.lines().sorted().collect(Collectors.joining("\n")),
                    find.getStdout().lines().sorted().collect(Collectors.joining("\n")));
            assertEquals("Sed ut perspiciatis", cat(container, "/opt/spec-type-dir/a/1"));
            assertEquals("unde omnis iste natus error sit", cat(container, "/opt/spec-type-dir/a/2"));
            assertEquals("voluptatem accusantium doloremque", cat(container, "/opt/spec-type-dir/a/3"));
            assertEquals("this is a file", cat(container, "/opt/spec-type-dir/b/file.txt"));
            assertEquals("# README", cat(container, "/opt/spec-type-dir/b/README.txt"));
            assertEquals("test", cat(container, "/opt/spec-type-dir/c/test"));
        }
    }

    @SuppressWarnings("SameParameterValue")
    private PackageConfig packageWithSingleTextFile(String name, String content, String conflicts, String path) {
        return new PackageConfig()
                .setMeta(new PackageMeta().setName(name).setVersion("0.0.1").setArch(Arch.current()))
                .setControl(new PackageConfig.ControlExtras()
                        .setMaintainer("maintainer").setDescription("description").setConflicts(conflicts))
                .setFiles(new FileSpec().setControlFiles(List.of()).setDataFiles(List.of(
                        new PackageConfig.PkgFileSpec.TextPkgFileSpec().setContent(content).setPath(path))));
    }

    @SuppressWarnings("resource")
    private GenericContainer<?> packageContainer(simple.repo.model.FileIntegrityWithContent deb) {
        return new GenericContainer<>("debian:13-slim")
                .withCreateContainerCmdModifier(c -> c.withEntrypoint("tail", "-f", "/dev/null"))
                .withCopyToContainer(Transferable.of(deb.getContent()), packagePath(deb));
    }

    private String packagePath(simple.repo.model.FileIntegrityWithContent deb) {
        return "/tmp/" + deb.getFileIntegrity().getPath();
    }

    private void assertSuccess(Container.ExecResult result) {
        assertEquals(0, result.getExitCode(), () -> result.getStdout() + "\n" + result.getStderr());
    }

    @SneakyThrows
    private String cat(GenericContainer<?> container, String path) {
        return container.execInContainer("cat", path).getStdout().strip();
    }
}
