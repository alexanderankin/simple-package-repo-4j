package simple.repo.keys;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class GeneratedKeyPair {
    String privateKey;
    String publicKey;
}
