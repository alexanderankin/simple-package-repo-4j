package simple.repo.rpm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RpmTagsTest {

    @Test
    void test() {
        assertTrue(RpmTags.HeaderTag.values().length > 0);
        assertTrue(RpmTags.RpmTag.values().length > 0);
        assertTrue(RpmTags.RpmDbiTag.values().length > 0);
        assertTrue(RpmTags.RpmTagType.values().length > 0);
        assertTrue(RpmTags.RpmTagClass.values().length > 0);
        assertTrue(RpmTags.RpmTagReturnType.values().length > 0);
    }
}
