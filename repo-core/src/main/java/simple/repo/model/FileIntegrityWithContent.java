package simple.repo.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.nio.charset.StandardCharsets;

@Data
@Accessors(chain = true)
public class FileIntegrityWithContent {
    byte[] content;
    FileIntegrity fileIntegrity;

    public static FileIntegrityWithContent of(String content, String path) {
        return FileIntegrityWithContent.of(content.getBytes(StandardCharsets.UTF_8), path);
    }

    public static FileIntegrityWithContent of(byte[] content, String path) {
        return new FileIntegrityWithContent()
                .setContent(content)
                .setFileIntegrity(FileIntegrity.of(content, path));
    }
}
