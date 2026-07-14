package simple.repo.rpm.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * from rpmHashAlgo_e in include/rpm/rpmcrypto.h#L20
 */
@RequiredArgsConstructor
@Getter
public enum RpmHashAlgo {
    RPM_HASH_MD5(1),    /*!< MD5 */
    RPM_HASH_SHA1(2),    /*!< SHA1 */
    RPM_HASH_RIPEMD160(3),    /*!< RIPEMD160 */
    RPM_HASH_MD2(5),    /*!< MD2 */
    RPM_HASH_TIGER192(6),    /*!< TIGER192 */
    RPM_HASH_HAVAL_5_160(7),    /*!< HAVAL-5-160 */
    RPM_HASH_SHA256(8),    /*!< SHA2-256 */
    RPM_HASH_SHA384(9),    /*!< SHA2-384 */
    RPM_HASH_SHA512(10),    /*!< SHA2-512 */
    RPM_HASH_SHA224(11),    /*!< SHA2-224 */
    RPM_HASH_SHA3_256(12),    /*!< SHA3-256 */
    RPM_HASH_SHA3_512(14),    /*!< SHA3-512 */;

    private final int tagValue;
}
