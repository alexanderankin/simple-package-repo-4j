package simple.repo.io.s3;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import simple.repo.io.RepoIo;
import simple.repo.repository.Repository;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

@Data
@Accessors(chain = true)
public class S3RepoIo implements RepoIo<S3RepoIo.S3Location> {
    S3Location location;
    @ToString.Exclude
    S3Client s3Client;

    private S3Client s3Client() {
        if (s3Client == null)
            s3Client = S3Client.create();
        return s3Client;
    }

    @Override
    public byte[] downloadPackage(Repository.RepositoryPath repositoryPath) {
        var getObjectRequest = location.get(repositoryPath.joinParts());
        try {
            return s3Client().getObjectAsBytes(getObjectRequest).asByteArrayUnsafe();
        } catch (S3Exception e) {
            throw new ObjectNotFoundException("could not get object: " + getObjectRequest, e);
        }
    }

    @Override
    public void uploadPackage(Repository.RepositoryPath repositoryPath, byte[] content) {
        s3Client().putObject(location.put(repositoryPath.joinParts()).build(), RequestBody.fromBytes(content));
    }

    @Override
    public Iterable<Repository.RepositoryPath> iterFiles(String path) {
        return S3RepoPathIterator.forPath(this, path);
    }

    @Override
    public boolean canParseLocation(String location) {
        var uriComponents = UriComponentsBuilder.fromUriString(location).build();
        var scheme = uriComponents.getScheme();
        return "s3".equals(scheme) && StringUtils.isNotBlank(uriComponents.getHost());
    }

    @Override
    public S3Location parseLocation(String location) {
        return new S3Location().setS3BaseUrl(URI.create(location));
    }

    @Override
    public String stringifyLocation(S3Location location) {
        return location.getS3BaseUrl().toString();
    }

    @Data
    @Accessors(chain = true)
    public static class S3Location implements RepoLocation {
        URI s3BaseUrl;

        GetObjectRequest get(String key) {
            return GetObjectRequest.builder()
                    .bucket(s3BaseUrl.getHost())
                    .key(StringUtils.strip(s3BaseUrl.getPath(), "/") + "/" + StringUtils.strip(key, "/"))
                    .build();
        }

        PutObjectRequest.Builder put(String key) {
            return PutObjectRequest.builder()
                    .bucket(s3BaseUrl.getHost())
                    .key(StringUtils.strip(s3BaseUrl.getPath(), "/") + "/" + StringUtils.strip(key, "/"));
        }

        ListObjectsV2Request.Builder list(String prefix) {
            return ListObjectsV2Request.builder()
                    .bucket(s3BaseUrl.getHost())
                    .prefix(prefix);
        }
    }

    @Data
    @Accessors(chain = true)
    private static class S3RepoPathIterator implements Iterator<Repository.RepositoryPath> {
        final S3RepoIo s3RepoIo;
        final String initialPath;
        String nextToken;
        @ToString.Exclude
        Iterator<S3Object> lastIter;

        S3RepoPathIterator(S3RepoIo s3RepoIo, String initialPath, ListObjectsV2Response initialResponse) {
            this.s3RepoIo = s3RepoIo;
            this.initialPath = initialPath;
            nextToken = initialResponse.nextContinuationToken();
            lastIter = initialResponse.contents().iterator();
        }

        static Iterable<Repository.RepositoryPath> forPath(S3RepoIo s3RepoIo, String path) {
            var initialResponse = s3RepoIo.s3Client().listObjectsV2(s3RepoIo.location.list(path).build());

            if (initialResponse.contents().isEmpty())
                return List.of();

            return () -> new S3RepoPathIterator(s3RepoIo, path, initialResponse);
        }

        @Override
        public boolean hasNext() {
            return lastIter.hasNext() || nextToken != null;
        }

        @Override
        public Repository.RepositoryPath next() {
            if (lastIter.hasNext())
                return map(lastIter.next());

            if (nextToken == null)
                throw new NoSuchElementException();

            var nextResponse = s3RepoIo.s3Client().listObjectsV2(s3RepoIo.location.list(initialPath).continuationToken(nextToken).build());
            nextToken = nextResponse.nextContinuationToken();
            lastIter = nextResponse.contents().iterator();

            return next();
        }

        private Repository.RepositoryPath map(S3Object next) {
            var ourPath = Arrays.asList(next.key().split("/"));
            // todo fixme
            var relPath = ourPath.subList(initialPath.split("/").length, ourPath.size());
            return new Repository.RepositoryPath().setParts(relPath);
        }
    }
}
