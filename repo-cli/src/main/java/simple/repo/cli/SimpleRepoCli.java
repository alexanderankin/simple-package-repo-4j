package simple.repo.cli;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import lombok.SneakyThrows;
import simple.repo.io.RepoIo;
import simple.repo.model.PackageConfig;
import simple.repo.packaging.PackageBuilder;
import simple.repo.repository.Repository;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class SimpleRepoCli {
    JsonMapper jsonMapper;
    YAMLMapper yamlMapper;
    ValidatorFactory validatorFactory;
    Validator validator;
    List<RepoIo<?>> loadedRepoIos;
    Map<String, Repository<?>> loadedRepos;

    SimpleRepoCli() {
        jsonMapper = JsonMapper.builder().findAndAddModules().build();
        yamlMapper = YAMLMapper.builder().findAndAddModules().build();
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    public SimpleRepoCli validate(String message, Object o, Class<?>... classes) {
        var constraintViolations = validator.validate(o, classes);
        if (!constraintViolations.isEmpty()) {
            if (message == null)
                throw new ConstraintViolationException(constraintViolations);
            throw new ConstraintViolationException(message, constraintViolations);
        }
        return this;
    }

    // make public version not tied to cli
    @SneakyThrows
    public Path buildPackage(SimpleRepoApplication.Package.Build build) {
        PackageConfig packageConfig;
        try {
            packageConfig = jsonMapper.readValue(build.getConfigFile(), PackageConfig.class);
        } catch (StreamReadException readException) {
            try {
                packageConfig = yamlMapper.readValue(build.getConfigFile(), PackageConfig.class);
            } catch (Exception yamlEx) {
                var e = new IllegalStateException("failed to read config file", yamlEx);
                e.addSuppressed(readException);
                throw e;
            }
        }
        validate(null, packageConfig);

        var packageBuilder = loadedRepos().get(build.getRepo().getRepoType()).packageBuilder();
        var pkgContent = packageBuilder.buildPackage(packageConfig).getContent();
        var fileName = packageBuilder.fileName(packageConfig);

        Path outputFile;
        var buildOutput = build.getBuildOutput();
        var buildOutputDir = buildOutput.getOutputDir();
        var buildOutputFile = buildOutput.getOutputFile();
        if (buildOutputDir != null) {
            outputFile = buildOutputDir.resolve(fileName);
        } else if (buildOutputFile != null) {
            outputFile = buildOutputFile;
        } else {
            throw new IllegalStateException();
        }

        Files.write(outputFile, pkgContent);
        return outputFile;
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

    public List<RepoIo<?>> loadedRepoIos() {
        if (loadedRepoIos != null) return loadedRepoIos;
        synchronized (this) {
            if (loadedRepoIos != null) return loadedRepoIos;
            loadedRepoIos = new ArrayList<>();
            ServiceLoader.load(RepoIo.class).forEach(loadedRepoIos::add);
            return loadedRepoIos;
        }
    }

    public Map<String, Repository<?>> loadedRepos() {
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

    @SneakyThrows
    public void indexFiles(String repoType, List<Path> packages) {
        Repository<?> repository = loadedRepos().get(repoType);
        if (repository == null)
            throw new IllegalArgumentException("unknown repo type: " + repoType);
        var packageBuilder = repository.packageBuilder();
        for (var eachPackage : packages) {
            var indexFileDto = packageBuilder.parseConfigToIndexFile(eachPackage);
            var indexFileName = packageBuilder.indexFileName(indexFileDto.getPackageConfig());
            var indexFilePath = eachPackage.getParent().resolve(indexFileName);
            Files.write(indexFilePath, jsonMapper.writeValueAsBytes(indexFileDto));
        }
    }

}
