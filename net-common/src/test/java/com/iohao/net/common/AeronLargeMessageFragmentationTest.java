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

import io.aeron.*;
import io.aeron.driver.*;
import io.aeron.logbuffer.*;
import java.nio.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.agrona.concurrent.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 ioNet 放宽 SBE 上限后，Aeron 能自动分片并由接收端完整重组大消息。
 */
class AeronLargeMessageFragmentationTest {
    private static final int STREAM_ID = 12_345;
    private static final int FRAGMENT_LIMIT = 100;
    private static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(10);

    /**
     * 覆盖超过旧 60 KiB 上限和接近新 512 KiB 上限的两种消息。
     */
    @Test
    void fragmentAssemblerReassemblesLargeIpcMessages() {
        String aeronDirectoryName = CommonContext.generateRandomDirName();
        var mediaDriverContext = new MediaDriver.Context()
                .aeronDirectoryName(aeronDirectoryName)
                .dirDeleteOnStart(true)
                .dirDeleteOnShutdown(true);

        try (MediaDriver mediaDriver = MediaDriver.launchEmbedded(mediaDriverContext);
             Aeron aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(mediaDriver.aeronDirectoryName()));
             Publication publication = aeron.addPublication(CommonContext.IPC_CHANNEL, STREAM_ID);
             Subscription subscription = aeron.addSubscription(CommonContext.IPC_CHANNEL, STREAM_ID)) {
            awaitConnection(publication, subscription);

            assertFragmentedRoundTrip(publication, subscription, 70 * 1024);
            assertFragmentedRoundTrip(publication, subscription, 512 * 1024);
        }
    }

    /**
     * 发布指定长度的消息，并使用 {@link FragmentAssembler} 验证重组后的全部字节。
     */
    private static void assertFragmentedRoundTrip(
            Publication publication,
            Subscription subscription,
            int messageLength
    ) {
        byte[] expected = payload(messageLength);
        var sendBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(messageLength));
        sendBuffer.putBytes(0, expected);
        assertTrue(messageLength > publication.maxPayloadLength(), "测试消息必须触发 Aeron 分片");

        long offerDeadline = System.nanoTime() + TIMEOUT_NANOS;
        long offerResult;
        do {
            offerResult = publication.offer(sendBuffer, 0, messageLength);
            if (offerResult >= 0) {
                break;
            }
            Thread.onSpinWait();
        } while (System.nanoTime() < offerDeadline);
        assertTrue(offerResult >= 0, "Aeron offer timed out, result=" + offerResult);

        AtomicReference<byte[]> received = new AtomicReference<>();
        FragmentHandler completeMessageHandler = (buffer, offset, length, header) -> {
            byte[] actual = new byte[length];
            buffer.getBytes(offset, actual);
            received.set(actual);
        };
        var fragmentAssembler = new FragmentAssembler(completeMessageHandler);

        long receiveDeadline = System.nanoTime() + TIMEOUT_NANOS;
        while (received.get() == null && System.nanoTime() < receiveDeadline) {
            if (subscription.poll(fragmentAssembler, FRAGMENT_LIMIT) == 0) {
                Thread.onSpinWait();
            }
        }

        assertArrayEquals(expected, received.get(), "Aeron 分片重组后的消息必须保持完整");
    }

    /**
     * 等待 Publication 与 Subscription 建立连接，不使用固定休眠制造时序。
     */
    private static void awaitConnection(Publication publication, Subscription subscription) {
        long deadline = System.nanoTime() + TIMEOUT_NANOS;
        while ((!publication.isConnected() || !subscription.isConnected()) && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertTrue(publication.isConnected(), "Aeron Publication 未在超时前连接");
        assertTrue(subscription.isConnected(), "Aeron Subscription 未在超时前连接");
    }

    /**
     * 创建可逐字节比对的稳定测试数据。
     */
    private static byte[] payload(int length) {
        byte[] payload = new byte[length];
        for (int index = 0; index < payload.length; index++) {
            payload[index] = (byte) (index % 251);
        }
        return payload;
    }
}
