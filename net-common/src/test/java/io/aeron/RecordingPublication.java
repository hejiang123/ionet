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
package io.aeron;

import io.aeron.logbuffer.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.agrona.*;

/**
 * Test publication that records close calls without requiring a real Aeron client.
 *
 * @author 渔民小镇
 * @date 2026-05-03
 * @since 25.4
 */
public final class RecordingPublication extends Publication {
    private static final sun.misc.Unsafe UNSAFE = lookupUnsafe();
    /** Aeron {@code Publication.maxMessageLength} 私有字段在测试对象中的内存偏移。 */
    private static final long MAX_MESSAGE_LENGTH_OFFSET = lookupMaxMessageLengthOffset();

    private AtomicInteger closeCount;
    private AtomicInteger offerCount;
    private AtomicInteger lastOfferLength;
    private Queue<Long> offerResults;
    /** 下一次 {@code offer} 需要抛出的受控测试异常。 */
    private RuntimeException nextOfferException;

    @SuppressWarnings({"unused", "DataFlowIssue"})
    private RecordingPublication() {
        // Required only for compilation; instances are allocated without running this constructor.
        super(null, null, 0, 0, null, 0, null, 0, 0);
    }

    public static RecordingPublication create() {
        return create(Integer.MAX_VALUE);
    }

    /**
     * 创建带指定 Aeron 最大消息长度的记录型 Publication。
     *
     * @param maxMessageLength 允许发布的最大消息字节数
     * @return 初始化完成的测试 Publication
     */
    public static RecordingPublication create(int maxMessageLength) {
        try {
            var publication = (RecordingPublication) UNSAFE.allocateInstance(RecordingPublication.class);
            UNSAFE.putInt(publication, MAX_MESSAGE_LENGTH_OFFSET, maxMessageLength);
            publication.closeCount = new AtomicInteger();
            publication.offerCount = new AtomicInteger();
            publication.lastOfferLength = new AtomicInteger();
            publication.offerResults = new ConcurrentLinkedQueue<>();
            return publication;
        } catch (InstantiationException e) {
            throw new AssertionError(e);
        }
    }

    public RecordingPublication setOfferResults(long... results) {
        Arrays.stream(results).forEach(this.offerResults::offer);
        return this;
    }

    /**
     * 设置下一次提交需要抛出的异常，用于验证单消息故障隔离。
     *
     * @param exception 下一次 {@code offer} 抛出的异常
     * @return 当前 Publication
     */
    public RecordingPublication setNextOfferException(RuntimeException exception) {
        this.nextOfferException = Objects.requireNonNull(exception);
        return this;
    }

    public int closeCount() {
        return this.closeCount.get();
    }

    public int offerCount() {
        return this.offerCount.get();
    }

    public int lastOfferLength() {
        return this.lastOfferLength.get();
    }

    @Override
    public void close() {
        this.isClosed = true;
        this.closeCount.incrementAndGet();
    }

    @Override
    public long availableWindow() {
        return 0;
    }

    @Override
    public long offer(DirectBuffer buffer, int offset, int length, ReservedValueSupplier reservedValueSupplier) {
        this.recordOffer(length);
        return this.nextOfferResult();
    }

    @Override
    public long offer(
            DirectBuffer bufferOne,
            int offsetOne,
            int lengthOne,
            DirectBuffer bufferTwo,
            int offsetTwo,
            int lengthTwo,
            ReservedValueSupplier reservedValueSupplier) {
        this.recordOffer(lengthOne + lengthTwo);
        return this.nextOfferResult();
    }

    @Override
    public long offer(DirectBufferVector[] vectors, ReservedValueSupplier reservedValueSupplier) {
        this.recordOffer(0);
        return this.nextOfferResult();
    }

    @Override
    public long tryClaim(int length, BufferClaim bufferClaim) {
        return this.isClosed ? Publication.CLOSED : 1;
    }

    private void recordOffer(int length) {
        this.offerCount.incrementAndGet();
        this.lastOfferLength.set(length);
    }

    /** 消费一次性异常或预设结果，模拟 Aeron 单次 {@code offer} 的不同终态。 */
    private long nextOfferResult() {
        if (this.isClosed) {
            return Publication.CLOSED;
        }

        RuntimeException offerException = this.nextOfferException;
        if (offerException != null) {
            this.nextOfferException = null;
            throw offerException;
        }

        var result = this.offerResults.poll();
        return result == null ? 1 : result;
    }

    private static sun.misc.Unsafe lookupUnsafe() {
        try {
            Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (sun.misc.Unsafe) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** 查找 Aeron 最大消息长度字段，以便构造不同上限的记录型 Publication。 */
    private static long lookupMaxMessageLengthOffset() {
        try {
            Field field = Publication.class.getDeclaredField("maxMessageLength");
            return UNSAFE.objectFieldOffset(field);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
