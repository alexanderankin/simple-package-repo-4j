package simple.repo.io;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.springframework.web.util.UriComponentsBuilder;
import simple.repo.repository.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.LinkedHashMap;

@Data
@Accessors(chain = true)
public class InMemoryRepoIo implements RepoIo<InMemoryRepoIo.InMemoryIoLocation> {
    @ToString.Exclude
    Map<String, byte[]> contents;

    @Override
    public InMemoryIoLocation getLocation() {
        return InMemoryIoLocation.INSTANCE;
    }

    @Override
    public RepoIo<InMemoryIoLocation> setLocation(InMemoryIoLocation location) {
        return this;
    }

    @Override
    public byte[] downloadPackage(Repository.RepositoryPath repositoryPath) {
        var content = contents().get(repositoryPath.joinParts());
        if (content == null) throw new RepoIo.ObjectNotFoundException(
                "repository path does not exist: " + repositoryPath.joinParts());
        return content;
    }

    @Override
    public void uploadPackage(Repository.RepositoryPath repositoryPath, byte[] content) {
        contents().put(repositoryPath.joinParts(), content);
    }

    @Override
    public Iterable<Repository.RepositoryPath> iterFiles(String path) {
        var normalized = path == null ? "" : path.replaceFirst("^/+", "").replaceFirst("/+$", "");
        return new ArrayList<>(contents().keySet()).stream()
                .filter(key -> normalized.isEmpty() || key.equals(normalized) || key.startsWith(normalized + "/"))
                .sorted()
                .map(key -> new Repository.RepositoryPath().setParts(Arrays.asList(key.split("/"))))
                .toList();
    }

    private Map<String, byte[]> contents() {
        if (contents == null) contents = new LinkedHashMap<>();
        return contents;
    }

    @Override
    public boolean canParseLocation(String location) {
        return "in-memory".equals(UriComponentsBuilder.fromUriString(location).build().getScheme());
    }

    @Override
    public InMemoryIoLocation parseLocation(String location) {
        return InMemoryIoLocation.INSTANCE;
    }

    @Override
    public String stringifyLocation(InMemoryIoLocation location) {
        return "in-memory:/";
    }

    public record InMemoryIoLocation() implements RepoLocation {
        public static final InMemoryIoLocation INSTANCE = new InMemoryIoLocation();
    }
}
