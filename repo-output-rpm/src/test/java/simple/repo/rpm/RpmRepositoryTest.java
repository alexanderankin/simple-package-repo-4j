package simple.repo.rpm;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

class RpmRepositoryTest {

    @ParameterizedTest
    @CsvSource({
            "'', ''",
    })
    void test(String poolPath) {
        var rpmRepo = new RpmRepository().setPoolPath(poolPath);
        rpmRepo.coordinate(List.of("10", "x86_64", "name"));
    }

}
