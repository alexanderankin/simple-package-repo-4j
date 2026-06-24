package simple.repo.packaging;

import lombok.Data;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestClient;
import simple.repo.model.FileIntegrityWithContent;
import simple.repo.model.PackageConfig;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * @see simple.repo.model.PackageConfig.TarFileSpec
 */
@Data
@Accessors(chain = true)
public class FileSpecReader {
    Path current = Path.of(System.getProperty("user.dir"));
    RestClient restClient = RestClient.create();

    @SneakyThrows
    public List<FileIntegrityWithContent> readContents(List<PackageConfig.TarFileSpec> allFiles) {
        return allFiles.stream().map(f -> FileIntegrityWithContent.of(readContent(f), f.getPath())).toList();
    }

    @SneakyThrows
    public byte[] readContent(PackageConfig.TarFileSpec f) {
        return switch (f) {
            case PackageConfig.TarFileSpec.BinaryTarFileSpec bin -> bin.getContent();
            case PackageConfig.TarFileSpec.TextTarFileSpec text -> text.getContent().getBytes(StandardCharsets.UTF_8);
            case PackageConfig.TarFileSpec.FileTarFileSpec fs ->
                    Files.readAllBytes(current.resolve(fs.getSourcePath()));
            case PackageConfig.TarFileSpec.UrlTarFileSpec fs -> Objects.requireNonNull(
                    restClient.get()
                            .uri(fs.getUrl())
                            .headers(h -> {
                                if (!CollectionUtils.isEmpty(fs.getHeaders()))
                                    h.putAll(fs.getHeaders());
                                if (fs.getBearerToken() != null)
                                    h.setBearerAuth(fs.getBearerToken());
                            })
                            .retrieve()
                            .body(byte[].class),
                    "no response body"
            );
        };
    }
}
