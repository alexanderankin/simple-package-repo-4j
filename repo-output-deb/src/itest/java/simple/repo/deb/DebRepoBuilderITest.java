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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DebRepoBuilderITest {
    DebPackageBuilder buildDeb = new DebPackageBuilder();
    DebRepoBuilder buildDebRepo = new DebRepoBuilder();
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
    void test_simpleFileRepo() {
        var prefix = "test_simpleFileRepo";
        PackageConfig.PackageMeta meta = new PackageConfig.PackageMeta()
                .setName(prefix)
                .setArch(Arch.current())
                .setVersion("0.0.1");
        var deb = buildDeb.buildPackage(
                validate(new PackageConfig()
                        .setMeta(meta)
                        .setControl(new PackageConfig.ControlExtras()
                                .setMaintainer(prefix)
                                .setDescription(prefix))
                        .setFiles(new PackageConfig.FileSpec()
                                .setDataFiles(List.of(new PackageConfig.PkgFileSpec.TextPkgFileSpec()
                                        .setContent("#!/usr/bin/env bash\necho " + prefix)
                                        .setMode(0x755)
                                        .setPath("/usr/bin/" + prefix)))))
        );

        var fileName = deb.getFileIntegrity().getPath();

        // var repo = buildDebRepo.equals()

        try (GenericContainer<?> genericContainer = new GenericContainer<>("debian:12-slim")) {
            genericContainer
                    .withCreateContainerCmdModifier(c -> c.withEntrypoint("tail", "-f", "/dev/null"))
                    .withCopyToContainer(Transferable.of(deb.getContent()), "/tmp/" + fileName);
            genericContainer.start();

            assertEquals(0, genericContainer.execInContainer("rm -rf /etc/apt/sources.list.d".split(" ")).getExitCode());
            // todo copy repo
            genericContainer.copyFileToContainer(Transferable.of((byte[]) null), "/etc/apt/sources.list");
            assertEquals(0, genericContainer.execInContainer("apt-get update".split(" ")).getExitCode());


            var result = genericContainer.execInContainer("apt-get install " + prefix);
            System.out.println(result.getStderr());
            System.out.println(result.getStdout());
            assertEquals(0, result.getExitCode());

            result = genericContainer.execInContainer(prefix);
            assertEquals("", result.getStderr().strip());
            assertEquals(prefix, result.getStdout().strip());
            assertEquals(0, result.getExitCode());
        }
    }
}
