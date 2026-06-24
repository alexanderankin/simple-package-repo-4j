package simple.repo.deb;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import simple.repo.model.Arch;
import simple.repo.model.PackageConfig;
import simple.repo.model.PackageConfig.FileSpec;
import simple.repo.model.PackageConfig.PackageMeta;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
                                .setDataFiles(List.of(new PackageConfig.TarFileSpec.TextTarFileSpec()
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
}
