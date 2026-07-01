package simple.repo;

import org.junit.jupiter.api.Test;
import simple.repo.io.LocalFileRepoIo;
import simple.repo.io.RepoIo;

public class TestTest {
    @Test
    void test() {
        System.out.println("hi");
        System.out.println(RepoIo.class);
        System.out.println(LocalFileRepoIo.class);
    }
}
