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
package com.iohao.net.common.kit.concurrent.executor;

import com.iohao.net.common.kit.RuntimeKit;

import java.util.concurrent.ExecutorService;

/**
 * A configurable single-thread striped executor region.
 * <p>
 * Tasks are partitioned by an arbitrary affinity key. The same key always maps to the same
 * executor, preserving per-key ordering while allowing unrelated keys to progress in parallel.
 * The stripe count is normalized to a bounded power of two so hash selection remains a cheap
 * bitmask operation and callers cannot create an excessive number of platform threads.
 *
 * @author 渔民小镇
 * @date 2026-04-04
 * @since 25.4
 */
public final class AffinityThreadExecutorRegion extends AbstractThreadExecutorRegion {
    final int executorMask;

    /**
     * Create a region whose stripe count is derived from the current CPU count and the provided
     * request.
     *
     * @param name the thread name prefix
     * @param requestedSize requested stripe count, or {@code <= 0} to use the runtime default
     */
    public AffinityThreadExecutorRegion(String name, int requestedSize) {
        super(name, RuntimeKit.normalizeAffinityExecutorSize(requestedSize));
        this.executorMask = this.threadExecutors.length - 1;
    }

    /**
     * {@inheritDoc}
     *
     * @param affinityKey arbitrary key whose tasks should remain serial
     * @return the executor assigned to the key
     */
    @Override
    public ThreadExecutor getThreadExecutor(long affinityKey) {
        return this.threadExecutors[(int) (affinityKey & this.executorMask)];
    }

    /**
     * Return the actual normalized stripe count.
     */
    public int getExecutorSize() {
        return this.threadExecutors.length;
    }

    /**
     * Shutdown all backing executor services.
     * <p>
     * This is primarily intended for tests or callers that create short-lived regions.
     * Long-lived application regions may simply keep the executors for the process lifetime.
     * </p>
     */
    public void shutdown() {
        for (ThreadExecutor threadExecutor : this.threadExecutors) {
            if (threadExecutor.executor() instanceof ExecutorService executorService) {
                executorService.shutdownNow();
            }
        }
    }
}
