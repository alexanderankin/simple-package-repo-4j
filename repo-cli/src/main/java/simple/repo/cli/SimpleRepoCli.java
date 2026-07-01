package simple.repo.cli;

import jakarta.validation.*;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

public class SimpleRepoCli {
    JsonMapper jsonMapper;
    ValidatorFactory validatorFactory;
    Validator validator;

    SimpleRepoCli() {
        jsonMapper = JsonMapper.builder().findAndAddModules().build();
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    public SimpleRepoCli validate(String message, Object o, Class<?> ...classes) {
        var constraintViolations = validator.validate(o, classes);
        if (!constraintViolations.isEmpty()) {
            throw new ConstraintViolationException(message, constraintViolations);
        }
        return this;
    }

    // make public version not tied to cli
    void buildPackage(SimpleRepoApplication.Package.Build build) {
        throw new UnsupportedOperationException();
    }

    public void indexExistingRepoFile(String packagePath) {

    }
}
