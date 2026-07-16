package simple.repo.io;

import lombok.Data;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import simple.repo.repository.Repository;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Data
@Accessors(chain = true)
public class LocalFileRepoIo implements RepoIo<LocalFileRepoIo.LocalFileRepoLocation> {
    LocalFileRepoLocation location;

    @SneakyThrows
    @Override
    public byte[] downloadPackage(Repository.RepositoryPath repositoryPath) {
        try {
            return Files.readAllBytes(location.resolve(repositoryPath.asLocalPath()));
        } catch (NoSuchFileException exception) {
            throw new RepoIo.ObjectNotFoundException(
                    "repository path does not exist: " + repositoryPath.joinParts(), exception);
        }
    }

    @SneakyThrows
    @Override
    public void uploadPackage(Repository.RepositoryPath repositoryPath, byte[] content) {
        var destination = location.resolve(repositoryPath.asLocalPath());
        if (destination.getParent() != null) Files.createDirectories(destination.getParent());
        Files.write(destination, content);
    }

    @SneakyThrows
    @Override
    public Iterable<Repository.RepositoryPath> iterFiles(String path) {
        // todo paging?
        Iterable<Repository.RepositoryPath> list;
        var root = location.getRoot().toAbsolutePath().normalize();
        var start = location.resolve(path).toAbsolutePath().normalize();
        if (!Files.exists(start)) return List.of();
        try (var walk = Files.walk(start)) {
            list = walk.filter(Files::isRegularFile)
                    .map(file -> root.relativize(file.toAbsolutePath().normalize()))
                    .map(this::partsOf).map(Repository.RepositoryPath::of).toList();
        }
        return list;
    }

    private List<String> partsOf(Path p) {
        List<String> result = new ArrayList<>(p.getNameCount());
        for (var path : p) {
            result.add(path.toString());
        }
        return result;
    }

    @Override
    public boolean canParseLocation(String location) {
        var uriComponents = UriComponentsBuilder.fromUriString(location).build();
        var scheme = uriComponents.getScheme();
        return "file".equals(scheme) && !StringUtils.hasText(uriComponents.getHost());
    }

    @Override
    public LocalFileRepoLocation parseLocation(String location) {
        mustParseLocation(location);
        var uriComponents = UriComponentsBuilder.fromUri(URI.create(location)).build();
        var path = Objects.requireNonNullElse(uriComponents.getPath(), uriComponents.getSchemeSpecificPart());
        return new LocalFileRepoLocation().setRoot(Path.of(path));
    }

    @Override
    public String stringifyLocation(LocalFileRepoLocation location) {
        var path = location.getRoot();
        return path.isAbsolute() ? path.toUri().toString() : "file:./" + path.normalize();
    }

    @Data
    @Accessors(chain = true)
    public static class LocalFileRepoLocation implements RepoLocation {
        Path root;

        Path resolve(String path) {
            return resolve(Path.of(path));
        }

        Path resolve(Path path) {
            var normalizedRoot = root.toAbsolutePath().normalize();
            var resolved = normalizedRoot.resolve(path).normalize();
            if (!resolved.startsWith(normalizedRoot))
                throw new IllegalStateException("resolved path does not start with root; resolved = " + resolved + ", root = " + normalizedRoot);
            return resolved;
        }
    }
}
