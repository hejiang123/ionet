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
package com.iohao.net.common;

import com.iohao.net.sbe.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.agrona.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test helpers for publisher integration tests.
 *
 * @author 渔民小镇
 * @date 2026-05-04
 * @since 25.4
 */
final class PublisherTestKit {
    private PublisherTestKit() {
    }

    static void registerTestMessageEncoder() {
        if (SbeMessageManager.getMessageEncoder(TestMessage.class) == null) {
            SbeMessageManager.register(TestMessage.class, new TestMessageSbe());
        }

        if (SbeMessageManager.getMessageEncoder(FailingMessage.class) == null) {
            SbeMessageManager.register(FailingMessage.class, new FailingMessageSbe());
        }

        if (SbeMessageManager.getMessageEncoder(LargeMessage.class) == null) {
            SbeMessageManager.register(LargeMessage.class, new LargeMessageSbe());
        }
    }

    static void awaitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }

            Thread.sleep(10);
        }

        fail("Condition was not met before timeout");
    }

    record TestMessage(int value) {
    }

    /** 表示编码阶段必定失败的测试消息，用于验证单消息故障隔离。 */
    record FailingMessage() {
    }

    /** 表示编码长度为 9 字节的测试消息，用于验证 Aeron 最大消息长度预检。 */
    record LargeMessage() {
    }

    static final class TestMessageSbe implements MessageSbe<TestMessage> {
        private static final int ENCODED_LENGTH = 8;

        @Override
        public void encoder(TestMessage message, MessageHeaderEncoder headerEncoder, MutableDirectBuffer buffer) {
            buffer.putInt(0, message.value());
        }

        @Override
        public int limit() {
            return ENCODED_LENGTH;
        }
    }

    /** 在编码阶段抛出异常，模拟超长 payload 被 SBE 编码器拒绝。 */
    static final class FailingMessageSbe implements MessageSbe<FailingMessage> {
        @Override
        public void encoder(FailingMessage message, MessageHeaderEncoder headerEncoder, MutableDirectBuffer buffer) {
            throw new IllegalStateException("simulated encoding failure");
        }

        @Override
        public int limit() {
            return 0;
        }
    }

    /** 返回超过测试 Publication 上限的编码长度，不依赖实际缓冲区写入。 */
    static final class LargeMessageSbe implements MessageSbe<LargeMessage> {
        private static final int ENCODED_LENGTH = 9;

        @Override
        public void encoder(LargeMessage message, MessageHeaderEncoder headerEncoder, MutableDirectBuffer buffer) {
            buffer.putByte(0, (byte) 1);
        }

        @Override
        public int limit() {
            return ENCODED_LENGTH;
        }
    }
}
