package com.iohao.net.common.kit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * RuntimeKit executor sizing contract tests.
 */
class RuntimeKitTest {
    @Test
    void normalizePowerOfTwoCeilShouldRoundUp() {
        Assertions.assertEquals(1, RuntimeKit.normalizePowerOfTwoCeil(1));
        Assertions.assertEquals(2, RuntimeKit.normalizePowerOfTwoCeil(2));
        Assertions.assertEquals(4, RuntimeKit.normalizePowerOfTwoCeil(3));
        Assertions.assertEquals(8, RuntimeKit.normalizePowerOfTwoCeil(5));
    }

    @Test
    void normalizeAffinityExecutorSizeShouldUseDefaultForNonPositiveInput() {
        Assertions.assertEquals(
                RuntimeKit.defaultAffinityExecutorSize(),
                RuntimeKit.normalizeAffinityExecutorSize(0)
        );
    }

    @Test
    void normalizeAffinityExecutorSizeShouldCapLargeInput() {
        Assertions.assertEquals(
                RuntimeKit.maxAffinityExecutorSize(),
                RuntimeKit.normalizeAffinityExecutorSize(Integer.MAX_VALUE)
        );
    }
}
