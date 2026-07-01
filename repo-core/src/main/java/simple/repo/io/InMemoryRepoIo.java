package simple.repo.io;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.springframework.web.util.UriComponentsBuilder;
import simple.repo.repository.Repository;

import java.util.Map;

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
        return contents.get(repositoryPath.joinParts());
    }

    @Override
    public void uploadPackage(Repository.RepositoryPath repositoryPath, byte[] content) {
        contents.put(repositoryPath.joinParts(), content);
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
