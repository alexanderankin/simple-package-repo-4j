package simple.repo.cli.util;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.file.Files;
import java.nio.file.Path;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = Exists.PathExistsValidator.class)
public @interface Exists {
    class PathExistsValidator implements ConstraintValidator<Exists, Path> {
        @Override
        public boolean isValid(Path value, ConstraintValidatorContext context) {
            return value == null || Files.exists(value);
        }
    }
}
