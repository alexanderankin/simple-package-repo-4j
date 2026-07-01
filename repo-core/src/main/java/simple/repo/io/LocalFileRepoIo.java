package simple.repo.io;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import simple.repo.repository.Repository;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

@Data
@Accessors(chain = true)
public class LocalFileRepoIo implements RepoIo<LocalFileRepoIo.LocalFileRepoLocation> {
    LocalFileRepoLocation location;

    @Override
    public byte[] downloadPackage(Repository.RepositoryPath repositoryPath) {
        return new byte[0];
    }

    @Override
    public void uploadPackage(Repository.RepositoryPath repositoryPath, byte[] content) {

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
    }
}
