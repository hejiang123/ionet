package com.iohao.net.common.kit.concurrent.executor;

import com.iohao.net.common.kit.RuntimeKit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AffinityThreadExecutorRegion tests.
 */
class AffinityThreadExecutorRegionTest {
    private AffinityThreadExecutorRegion region;

    @AfterEach
    void tearDown() {
        if (region != null) {
            region.shutdown();
        }
    }

    @Test
    void requestedSizeShouldBeNormalizedToPowerOfTwo() {
        region = new AffinityThreadExecutorRegion("TestAffinity", 3);
        Assertions.assertEquals(4, region.getExecutorSize());
    }

    @Test
    void requestedSizeShouldRespectRuntimeCap() {
        region = new AffinityThreadExecutorRegion("TestAffinity", Integer.MAX_VALUE);
        Assertions.assertEquals(RuntimeKit.maxAffinityExecutorSize(), region.getExecutorSize());
    }

    @Test
    void sameKeyShouldRemainSerialWhileDifferentKeysCanSpread() throws Exception {
        region = new AffinityThreadExecutorRegion("TestAffinity", 4);

        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(3);
        AtomicReference<String> firstThread = new AtomicReference<>();
        AtomicReference<String> secondThread = new AtomicReference<>();
        AtomicReference<String> otherThread = new AtomicReference<>();
        AtomicBoolean outOfOrder = new AtomicBoolean(false);

        region.getThreadExecutor(1L).execute(() -> {
            firstThread.set(Thread.currentThread().getName());
            started.countDown();
            await(release);
            completed.countDown();
        });

        region.getThreadExecutor(1L).execute(() -> {
            secondThread.set(Thread.currentThread().getName());
            if (firstThread.get() == null) {
                outOfOrder.set(true);
            }
            completed.countDown();
        });

        region.getThreadExecutor(2L).execute(() -> {
            otherThread.set(Thread.currentThread().getName());
            started.countDown();
            await(release);
            completed.countDown();
        });

        Assertions.assertTrue(started.await(2, TimeUnit.SECONDS));
        release.countDown();
        Assertions.assertTrue(completed.await(2, TimeUnit.SECONDS));
        Assertions.assertFalse(outOfOrder.get());
        Assertions.assertEquals(firstThread.get(), secondThread.get());
        Assertions.assertNotEquals(firstThread.get(), otherThread.get());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
