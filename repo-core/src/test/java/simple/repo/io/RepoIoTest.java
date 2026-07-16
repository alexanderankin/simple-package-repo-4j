package simple.repo.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import simple.repo.repository.Repository;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RepoIoTest {
    @Test
    void localIoCreatesParentsAndReturnsRootRelativeFiles(@TempDir Path root) {
        var io = new LocalFileRepoIo().setLocation(
                new LocalFileRepoIo.LocalFileRepoLocation().setRoot(root));
        var path = Repository.RepositoryPath.of(List.of("9", "x86_64", "repodata", "repomd.xml"));

        io.uploadPackage(path, new byte[]{1, 2, 3});

        assertEquals(List.of("9/x86_64/repodata/repomd.xml"),
                StreamSupport.stream(io.iterFiles("9").spliterator(), false)
                        .map(Repository.RepositoryPath::joinParts).toList());
        assertThrows(IllegalStateException.class, () -> io.downloadPackage(
                Repository.RepositoryPath.of(List.of("..", "outside"))));
    }

    @Test
    void memoryIoScopesPrefixScansAndRejectsMissingObjects() {
        var io = new InMemoryRepoIo();
        io.uploadPackage(Repository.RepositoryPath.of(List.of("pool", "noble", "one")), new byte[]{1});
        io.uploadPackage(Repository.RepositoryPath.of(List.of("pool", "resolute", "two")), new byte[]{2});

        assertEquals(List.of("pool/noble/one"),
                StreamSupport.stream(io.iterFiles("pool/noble").spliterator(), false)
                        .map(Repository.RepositoryPath::joinParts).toList());
        assertThrows(IllegalArgumentException.class, () -> io.downloadPackage(
                Repository.RepositoryPath.of(List.of("missing"))));
    }
}
