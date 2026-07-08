package simple.repo.cli.util;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Files;
import java.nio.file.Path;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = Exists.PathExistsValidator.class)
@Target(ElementType.TYPE_USE)
public @interface Exists {
    String message() default "File doesn't exist";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class PathExistsValidator implements ConstraintValidator<Exists, Path> {
        @Override
        public boolean isValid(Path value, ConstraintValidatorContext context) {
            return value == null || Files.exists(value);
        }
    }
}
