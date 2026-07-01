package simple.repo.keys;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import sop.Profile;

import static org.pgpainless.sop.GenerateKeyImpl.*;

@RequiredArgsConstructor
@Getter
@ToString
public enum SupportedKeyGenerationProfile {
    // recommended
    CURVE25519(CURVE25519_PROFILE),
    CURVE25519_RFC9580(RFC9580_CURVE25519_PROFILE),
    CURVE448_RFC9580(RFC9580_CURVE448_PROFILE),

    // not recommended
    RSA4096(RFC4880_RSA4096_PROFILE),
    P256(RFC6637_NIST_P256_PROFILE),
    P384(RFC6637_NIST_P384_PROFILE),
    P521(RFC6637_NIST_P521_PROFILE),
    ;

    private final Profile profile;
}
