/*
 * ionet
 * Copyright (C) 2021 - present  渔民小镇 （262610965@qq.com、luoyizhu@gmail.com） . All Rights Reserved.
 * # iohao.com . 渔民小镇
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.iohao.net.common.kit;

import lombok.experimental.*;

/**
 * Runtime environment utilities.
 *
 * @author 渔民小镇
 * @date 2024-05-01
 * @since 21.7
 */
@UtilityClass
public class RuntimeKit {
    /** Number of available processors reported by the runtime. */
    public int availableProcessors = Runtime.getRuntime().availableProcessors();

    /**
     * The largest power of 2 that does not exceed {@link #availableProcessors}.
     * <p>
     * For example, when {@code availableProcessors} is 4, 8, 12, 16, or 32,
     * the corresponding value is 4, 8, 8, 16, or 32.
     */
    public int availableProcessors2n = availableProcessors2n();

    /**
     * Recommended default stripe count for affinity-based single-thread executor regions.
     * <p>
     * The value is intentionally larger than {@link #availableProcessors2n} so key-partitioned
     * workloads such as room state machines can reduce cross-key queue contention without
     * expanding without bound.
     */
    public int defaultAffinityExecutorSize() {
        return Math.min(64, normalizePowerOfTwoCeil(Math.max(8, availableProcessors2n << 2)));
    }

    /**
     * Recommended hard cap for affinity-based single-thread executor regions.
     * <p>
     * The cap grows with CPU count but remains bounded so callers cannot accidentally create
     * an unbounded number of platform threads.
     */
    public int maxAffinityExecutorSize() {
        return Math.min(128, normalizePowerOfTwoCeil(Math.max(16, availableProcessors2n << 3)));
    }

    /**
     * Normalize an affinity executor stripe count.
     * <p>
     * Non-positive values use {@link #defaultAffinityExecutorSize()}, positive values are rounded
     * up to the nearest power of two, and the final value is capped by
     * {@link #maxAffinityExecutorSize()}.
     *
     * @param requestedSize caller-provided stripe count, or {@code <= 0} to use the default
     * @return normalized power-of-two stripe count within the recommended bounds
     */
    public int normalizeAffinityExecutorSize(int requestedSize) {
        int normalized = requestedSize <= 0
                ? defaultAffinityExecutorSize()
                : normalizePowerOfTwoCeil(Math.max(1, requestedSize));

        return Math.min(normalized, maxAffinityExecutorSize());
    }

    /**
     * Round up to the nearest power of two.
     *
     * @param value the source value
     * @return the smallest power of two that is greater than or equal to {@code value}
     */
    public int normalizePowerOfTwoCeil(int value) {
        if (value <= 1) {
            return 1;
        }

        int n = value - 1;
        n |= (n >> 1);
        n |= (n >> 2);
        n |= (n >> 4);
        n |= (n >> 8);
        n |= (n >> 16);
        return n + 1;
    }

    /**
     * Round down {@link #availableProcessors} to the nearest power of 2.
     * <p>
     * Uses bit-smearing to fill all bits below the highest set bit,
     * then shifts right by 1 to obtain the largest power of 2 &le; n.
     *
     * @return the largest power of 2 not exceeding the available processor count
     */
    static int availableProcessors2n() {
        int n = RuntimeKit.availableProcessors;
        // Smear the highest set bit into all lower bits
        n |= (n >> 1);
        n |= (n >> 2);
        n |= (n >> 4);
        n |= (n >> 8);
        n |= (n >> 16);
        // n is now (next power of 2) - 1; shift right to get the floor power of 2
        return (n + 1) >> 1;
    }
}
