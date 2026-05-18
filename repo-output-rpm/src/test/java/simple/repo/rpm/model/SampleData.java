package simple.repo.rpm.model;

import lombok.SneakyThrows;

import java.util.List;
import java.util.Objects;

public abstract class SampleData {
    @SneakyThrows
    protected static byte[] readBytes(String name) {
        try (var resourceAsStream = Objects.requireNonNull(LeadTest.class.getResourceAsStream(name))) {
            return resourceAsStream.readAllBytes();
        }
    }

    protected static List<String> testFileNames() {
        return List.of(
                "htop-3.3.0-5.el10_0.aarch64.rpm.lead",
                "htop-3.3.0-5.el10_0.x86_64.rpm.lead"
        );
    }
}
