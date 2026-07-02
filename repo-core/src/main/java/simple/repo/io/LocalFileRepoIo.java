package simple.repo.io;

import lombok.Data;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import simple.repo.repository.Repository;

import java.net.URI;
import java.nio.file.Files;
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
        return Files.readAllBytes(location.resolve(repositoryPath.asLocalPath()));
    }

    @SneakyThrows
    @Override
    public void uploadPackage(Repository.RepositoryPath repositoryPath, byte[] content) {
        Files.write(location.resolve(repositoryPath.asLocalPath()), content);
    }

    @SneakyThrows
    @Override
    public Iterable<Repository.RepositoryPath> iterFiles(String path) {
        // todo paging?
        Iterable<Repository.RepositoryPath> list;
        try (var walk = Files.walk(location.resolve(path))) {
            list = walk.map(this::partsOf).map(Repository.RepositoryPath::of).toList();
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
            var resolved = root.resolve(path);
            if (!resolved.startsWith(root))
                throw new IllegalStateException("resolved path does not start with root 0.o; resolved = " + resolved + ", root = " + root);
            return resolved;
        }

        Path resolve(Path path) {
            var resolved = root.resolve(path);
            if (!resolved.startsWith(root))
                throw new IllegalStateException("resolved path does not start with root 0.o; resolved = " + resolved + ", root = " + root);
            return resolved;
        }
    }
}
