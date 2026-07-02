package simple.repo.cli;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import simple.repo.io.RepoIo;
import simple.repo.packaging.PackageBuilder;
import simple.repo.repository.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

public class SimpleRepoCli {
    JsonMapper jsonMapper;
    ValidatorFactory validatorFactory;
    Validator validator;
    List<RepoIo<?>> loadedRepoIos;
    Map<String, Repository<?>> loadedRepos;

    SimpleRepoCli() {
        jsonMapper = JsonMapper.builder().findAndAddModules().build();
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    public SimpleRepoCli validate(String message, Object o, Class<?>... classes) {
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

    @SuppressWarnings("unchecked")
    public RepoIo<?> loadRepoIo(URI location) {
        RepoIo<?> result = null;
        for (RepoIo<?> repoIo : loadedRepoIos()) {
            if (repoIo.canParseLocation(location.toString())) {
                ((RepoIo<RepoIo.RepoLocation>) repoIo).setLocation(repoIo.parseLocation(location.toString()));
                if (result == null) {
                    result = repoIo;
                } else {
                    throw new IllegalStateException(
                            "multiple IO implementations matched '" + location + "': " +
                                    result.getClass().getName() + ", " +
                                    repoIo.getClass().getName() + ". this is a bug.");
                }
            }
        }

        if (result != null)
            return result;

        throw new UnsupportedOperationException("no repoIO could parse " + location + " out of: " +
                loadedRepoIos().stream().map(Object::getClass).map(Class::getName).collect(Collectors.joining(", ")));
    }

    public Repository<?> loadRepo(String type) {
        Repository<?> repository = loadedRepos().get(type);
        if (repository != null)
            return repository;

        var types = loadedRepos().values().stream().map(Repository::packageBuilder).map(PackageBuilder::outputType).sorted().collect(Collectors.joining(", "));
        throw new UnsupportedOperationException("no repository could parse type '" + type + "'; found types: " + types);
    }

    private List<RepoIo<?>> loadedRepoIos() {
        if (loadedRepoIos != null) return loadedRepoIos;
        synchronized (this) {
            if (loadedRepoIos != null) return loadedRepoIos;
            loadedRepoIos = new ArrayList<>();
            ServiceLoader.load(RepoIo.class).forEach(loadedRepoIos::add);
            return loadedRepoIos;
        }
    }

    private Map<String, Repository<?>> loadedRepos() {
        if (loadedRepos != null) return loadedRepos;
        synchronized (this) {
            if (loadedRepos != null) return loadedRepos;
            Map<String, Repository<?>> loadedRepos = new HashMap<>();
            for (var repository : ServiceLoader.load(Repository.class)) {
                var outputType = repository.packageBuilder().outputType();
                Repository<?> previous = loadedRepos.putIfAbsent(outputType, repository);
                if (previous != null) {
                    throw new IllegalStateException("multiple Repository implementations claim to build output type '" + outputType + "': " +
                            previous.getClass().getName() + ", " +
                            repository.getClass().getName() + ". this is a bug.");
                }
            }
            return this.loadedRepos = loadedRepos;
        }
    }
}
