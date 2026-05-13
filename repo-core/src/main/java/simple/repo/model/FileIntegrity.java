package simple.repo.model;

import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.codec.digest.DigestUtils;

@Data
@Accessors(chain = true)
public class FileIntegrity {
    String path;
    int size;
    String md5;
    String sha1;
    String sha256;
    String sha512;

    static FileIntegrity of(byte[] content, String path) {
        return new FileIntegrity()
                .setPath(path)
                .setSize(content.length)
                .setMd5(DigestUtils.md5Hex(content))
                .setSha1(DigestUtils.sha1Hex(content))
                .setSha256(DigestUtils.sha256Hex(content))
                .setSha512(DigestUtils.sha512Hex(content));
    }
}
