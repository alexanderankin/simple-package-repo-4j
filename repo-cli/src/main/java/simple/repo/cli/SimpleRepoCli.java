package simple.repo.cli;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import lombok.SneakyThrows;
import simple.repo.io.RepoIo;
import simple.repo.model.IndexFile;
import simple.repo.model.PackageConfig;
import simple.repo.model.PackageVersionComparator;
import simple.repo.packaging.PackageBuilder;
import simple.repo.repository.Repository;
import simple.repo.repository.RepositoryBuilder;
import simple.repo.repository.RepositoryInitialization;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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

    @SneakyThrows
    public Path buildPackage(SimpleRepoApplication.Package.Build build) {
        PackageConfig packageConfig = readPackageConfig(build.getConfigFile());
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

    @SneakyThrows
    public <I extends RepoIo.RepoLocation, R> void indexRepositoryPackages(RepoIo<I> repoIo,
                                                                           Repository<R> repository,
                                                                           List<String> packages) {
        for (var packageCoordinate : packages) {
            var coordinate = repository.coordinate(pathParts(packageCoordinate));
            var packagePath = repository.pathTo(coordinate);
            var downloadedPackage = repoIo.downloadPackage(packagePath);
            var packageBuilder = repository.packageBuilder();
            var packageConfig = packageBuilder.parseConfigFromPackage(downloadedPackage);
            var index = packageBuilder.buildIndexFile(downloadedPackage, packageConfig);
            repoIo.uploadPackage(packagePath.neighbor(packageBuilder.indexFileName(packageConfig)),
                    jsonMapper.writeValueAsBytes(index));
        }
    }

    @SneakyThrows
    public <I extends RepoIo.RepoLocation, R> void rebuildRepository(RepoIo<I> repoIo, Repository<R> repository, SimpleRepoApplication.Repo r) {
        rebuildRepository(repoIo, repository, r.targets, r.keepVersions, r.publicKey, r.secretKey);
    }

    @SneakyThrows
    public <I extends RepoIo.RepoLocation, R> void rebuildRepository(RepoIo<I> repoIo,
                                                                     Repository<R> repository,
                                                                     List<String> targets,
                                                                     Integer keepVersions,
                                                                     Path publicKey,
                                                                     Path secretKey) {
        validateKeepVersions(keepVersions);
        var builder = repository.repoBuilder();
        var candidates = new ArrayList<IndexCandidate>();
        for (var path : repository.indexPaths(repoIo)) {
            var target = builder.targetFromIndexPath(path);
            if (targets != null && !targets.isEmpty() && !targets.contains(target)) continue;
            candidates.add(new IndexCandidate(target, path,
                    builder.getPackageBuilder().metaFromFileName(path.getParts().getLast())));
        }
        candidates = new ArrayList<>(retainCandidates(candidates, keepVersions));
        var indexes = new ArrayList<TargetIndex>();
        for (var candidate : candidates) {
            indexes.add(new TargetIndex(candidate.target(),
                    jsonMapper.readValue(repoIo.downloadPackage(candidate.path()), IndexFile.class)));
        }
        publish(repoIo, builder, groupAndRetain(indexes, keepVersions), publicKey, secretKey);
    }

    public <I extends RepoIo.RepoLocation, R> void addPackageConfigs(RepoIo<I> repoIo,
                                                                     Repository<R> repository,
                                                                     List<Path> configPaths,
                                                                     SimpleRepoApplication.Repo.Add add) {
        addPackageConfigs(repoIo, repository, configPaths,
                add.repo.targets, add.repo.keepVersions,
                add.initialization,
                add.repo.publicKey, add.repo.secretKey);
    }

    @SneakyThrows
    public <I extends RepoIo.RepoLocation, R> void addPackageConfigs(RepoIo<I> repoIo,
                                                                     Repository<R> repository,
                                                                     List<Path> configPaths,
                                                                     List<String> targets,
                                                                     Integer keepVersions,
                                                                     RepositoryInitialization initialization,
                                                                     Path publicKey,
                                                                     Path secretKey) {
        if (targets == null || targets.isEmpty()) {
            throw new IllegalArgumentException("config-based repository add requires at least one --target");
        }
        for (var configPath : configPaths) {
            var sourceConfig = readPackageConfig(configPath);
            var builder = repository.repoBuilder();
            var indexPaths = new ArrayList<String>();
            for (var target : targets) {
                var config = jsonMapper.readValue(jsonMapper.writeValueAsBytes(sourceConfig), PackageConfig.class);
                config = builder.prepareTarget(config, target);
                validate(null, config);
                var packageFile = builder.getPackageBuilder().buildPackage(config);
                var packagePath = builder.packagePath(target, config);
                var indexPath = builder.indexPath(target, config);
                var index = builder.getPackageBuilder().buildIndexFile(packageFile.getContent(), config);
                repoIo.uploadPackage(packagePath, packageFile.getContent());
                repoIo.uploadPackage(indexPath, jsonMapper.writeValueAsBytes(index));
                indexPaths.add(indexPath.joinParts());
            }
            addRepositoryIndexes(repoIo, repository, indexPaths, keepVersions, initialization, publicKey, secretKey);
        }
    }

    @SneakyThrows
    public <I extends RepoIo.RepoLocation, R> void addRepositoryIndexes(RepoIo<I> repoIo,
                                                                        Repository<R> repository,
                                                                        List<String> indexCoordinates,
                                                                        Integer keepVersions,
                                                                        RepositoryInitialization initialization,
                                                                        Path publicKey,
                                                                        Path secretKey) {
        validateKeepVersions(keepVersions);
        var builder = repository.repoBuilder();
        var additions = new ArrayList<TargetIndex>();
        for (var coordinate : indexCoordinates) {
            var path = Repository.RepositoryPath.of(pathParts(coordinate));
            additions.add(new TargetIndex(builder.targetFromIndexPath(path),
                    jsonMapper.readValue(repoIo.downloadPackage(path), IndexFile.class)));
        }
        var byTarget = additions.stream().collect(Collectors.groupingBy(TargetIndex::target,
                LinkedHashMap::new, Collectors.mapping(TargetIndex::index, Collectors.toList())));
        var merged = new ArrayList<TargetIndex>();
        for (var entry : byTarget.entrySet()) {
            var current = builder.readPublished(repoIo, entry.getKey(), entry.getValue(), initialization);
            var packages = new LinkedHashMap<String, IndexFile>();
            current.forEach(index -> packages.put(identity(index), index));
            entry.getValue().forEach(index -> packages.put(identity(index), index));
            packages.values().forEach(index -> merged.add(new TargetIndex(entry.getKey(), index)));
        }
        publish(repoIo, builder, groupAndRetain(merged, keepVersions), publicKey, secretKey);
    }

    @SneakyThrows
    private PackageConfig readPackageConfig(Path configPath) {
        PackageConfig packageConfig;
        try {
            packageConfig = jsonMapper.readValue(configPath, PackageConfig.class);
        } catch (StreamReadException readException) {
            try {
                packageConfig = yamlMapper.readValue(configPath, PackageConfig.class);
            } catch (Exception yamlEx) {
                var e = new IllegalStateException("failed to read config file", yamlEx);
                e.addSuppressed(readException);
                throw e;
            }
        }
        return packageConfig;
    }

    @SneakyThrows
    private void publish(RepoIo<?> repoIo,
                         RepositoryBuilder builder,
                         Map<String, List<IndexFile>> packagesByTarget,
                         Path publicKey,
                         Path secretKey) {
        if ((publicKey == null) != (secretKey == null)) {
            throw new IllegalArgumentException("public and secret signing keys must be supplied together");
        }
        var now = Instant.now();
        var files = builder.build(packagesByTarget, now);
        if (publicKey != null) {
            files.putAll(builder.sign(files, Files.readAllBytes(secretKey), Files.readAllBytes(publicKey), now));
        }
        files.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> commitFile(entry.getKey()) ? 1 : 0))
                .forEach(entry -> repoIo.uploadPackage(
                        Repository.RepositoryPath.of(pathParts(entry.getKey())), entry.getValue().getContent()));
    }

    private boolean commitFile(String path) {
        return path.endsWith("/Release") || path.endsWith("/InRelease") || path.endsWith("/repomd.xml");
    }

    private Map<String, List<IndexFile>> groupAndRetain(List<TargetIndex> indexes, Integer keepVersions) {
        return retainIndexes(indexes, keepVersions).stream()
                .collect(Collectors.groupingBy(
                        TargetIndex::target,
                        LinkedHashMap::new,
                        Collectors.mapping(TargetIndex::index, Collectors.toList())
                ));
    }

    private List<TargetIndex> retainIndexes(List<TargetIndex> indexes, Integer keepVersions) {
        if (keepVersions == null) return indexes;
        return indexes.stream()
                .collect(Collectors.groupingBy(
                        index -> retentionKey(
                                index.target(),
                                index.index().getPackageConfig().getMeta()
                        ),
                        LinkedHashMap::new,
                        Collectors.toList()
                ))
                .values()
                .stream()
                .flatMap(group -> group.stream()
                        .sorted(Comparator.comparing(
                                (TargetIndex index) -> index.index().getPackageConfig().getMeta(),
                                PackageVersionComparator.INSTANCE
                        ).reversed())
                        .limit(keepVersions))
                .toList();
    }

    private List<IndexCandidate> retainCandidates(List<IndexCandidate> candidates, Integer keepVersions) {
        if (keepVersions == null) return candidates;
        return candidates.stream().collect(Collectors.groupingBy(candidate -> retentionKey(candidate.target(), candidate.meta()),
                        LinkedHashMap::new, Collectors.toList()))
                .values().stream().flatMap(group -> group.stream()
                        .sorted(Comparator.comparing(IndexCandidate::meta, PackageVersionComparator.INSTANCE).reversed())
                        .limit(keepVersions)).toList();
    }

    private String retentionKey(String target, PackageConfig.PackageMeta meta) {
        return target + "\u0000" + meta.getName() + "\u0000" + meta.getArch();
    }

    private String identity(IndexFile index) {
        var meta = index.getPackageConfig().getMeta();
        return meta.getName() + "\u0000" + meta.getArch() + "\u0000" + meta.getVersion()
                + "\u0000" + Objects.requireNonNullElse(meta.getReleaseVersion(), "");
    }

    private void validateKeepVersions(Integer keepVersions) {
        if (keepVersions != null && keepVersions <= 0) {
            throw new IllegalArgumentException("keepVersions must be positive");
        }
    }

    private List<String> pathParts(String path) {
        return Arrays.stream(path.split("/+")).filter(part -> !part.isBlank()).toList();
    }

    private record IndexCandidate(String target, Repository.RepositoryPath path, PackageConfig.PackageMeta meta) {
    }

    private record TargetIndex(String target, IndexFile index) {
    }

}
