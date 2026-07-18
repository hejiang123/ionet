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
package com.iohao.net.sbe;

import java.nio.*;
import java.util.*;
import org.agrona.*;
import org.agrona.concurrent.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证所有 SBE {@code data} 编码器共享同一容量边界。
 *
 * <p>该测试是生成源码缺少原始 schema 时的长期协议护栏，防止后续升级或机械合并时
 * 只有部分编码器退回旧的 60 KiB 限制。</p>
 */
class SbePayloadLimitTest {
    private static final int LEGACY_MAX_DATA_LENGTH_BYTES = 61_440;
    private static final int BUFFER_CAPACITY_BYTES = 2 * 1024 * 1024;
    private static final List<DataEncoderContract> DATA_ENCODERS = List.of(
            new DataEncoderContract(
                    BroadcastUserListMessageEncoder.class.getSimpleName(),
                    (buffer, payload) -> new BroadcastUserListMessageEncoder().wrap(buffer, 0)
                            .putData(payload, 0, payload.length)
            ),
            new DataEncoderContract(
                    BroadcastUserMessageEncoder.class.getSimpleName(),
                    (buffer, payload) -> new BroadcastUserMessageEncoder().wrap(buffer, 0)
                            .putData(payload, 0, payload.length)
            ),
            new DataEncoderContract(
                    BroadcastMulticastMessageEncoder.class.getSimpleName(),
                    (buffer, payload) -> new BroadcastMulticastMessageEncoder().wrap(buffer, 0)
                            .putData(payload, 0, payload.length)
            ),
            new DataEncoderContract(
                    ConnectResponseMessageEncoder.class.getSimpleName(),
                    SbePayloadLimitTest::putConnectResponsePayload
            ),
            new DataEncoderContract(
                    ConnectRequestMessageEncoder.class.getSimpleName(),
                    SbePayloadLimitTest::putConnectRequestPayload
            ),
            new DataEncoderContract(
                    EventBusMessageEncoder.class.getSimpleName(),
                    (buffer, payload) -> new EventBusMessageEncoder().wrap(buffer, 0)
                            .putData(payload, 0, payload.length)
            ),
            new DataEncoderContract(
                    ExternalResponseMessageEncoder.class.getSimpleName(),
                    (buffer, payload) -> new ExternalResponseMessageEncoder().wrap(buffer, 0)
                            .putPayload(payload, 0, payload.length)
            ),
            new DataEncoderContract(
                    ExternalRequestMessageEncoder.class.getSimpleName(),
                    (buffer, payload) -> new ExternalRequestMessageEncoder().wrap(buffer, 0)
                            .putPayload(payload, 0, payload.length)
            ),
            new DataEncoderContract(
                    RequestMessageEncoder.class.getSimpleName(),
                    (buffer, payload) -> new RequestMessageEncoder().wrap(buffer, 0)
                            .putData(payload, 0, payload.length)
            ),
            new DataEncoderContract(
                    ResponseMessageEncoder.class.getSimpleName(),
                    (buffer, payload) -> new ResponseMessageEncoder().wrap(buffer, 0)
                            .putData(payload, 0, payload.length)
            ),
            new DataEncoderContract(
                    SendMessageEncoder.class.getSimpleName(),
                    (buffer, payload) -> new SendMessageEncoder().wrap(buffer, 0)
                            .putData(payload, 0, payload.length)
            ),
            new DataEncoderContract(
                    UserResponseMessageEncoder.class.getSimpleName(),
                    (buffer, payload) -> new UserResponseMessageEncoder().wrap(buffer, 0)
                            .putData(payload, 0, payload.length)
            ),
            new DataEncoderContract(
                    UserRequestMessageEncoder.class.getSimpleName(),
                    (buffer, payload) -> new UserRequestMessageEncoder().wrap(buffer, 0)
                            .putData(payload, 0, payload.length)
            )
    );

    /** 验证旧上限后的首字节和新上限边界都能完成真实编解码。 */
    @Test
    void userResponseDataAtSupportedBoundariesRoundTrips() {
        assertUserResponseRoundTrip(LEGACY_MAX_DATA_LENGTH_BYTES + 1);
        assertUserResponseRoundTrip(SbePayloadLimits.MAX_DATA_LENGTH_BYTES);
    }

    /**
     * 使用真实 UserResponse 编解码器验证指定长度，证明原有 4 字节长度字段无需调整。
     */
    private static void assertUserResponseRoundTrip(int payloadLength) {
        byte[] expected = payload(payloadLength);
        var buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(BUFFER_CAPACITY_BYTES));
        var encoder = new UserResponseMessageEncoder().wrap(buffer, 0);
        encoder.errorMessage("");
        encoder.putData(expected, 0, expected.length);

        var decoder = new UserResponseMessageDecoder().wrap(
                buffer,
                0,
                UserResponseMessageEncoder.BLOCK_LENGTH,
                UserResponseMessageEncoder.SCHEMA_VERSION
        );
        assertEquals("", decoder.errorMessage());

        byte[] actual = new byte[expected.length];
        assertEquals(expected.length, decoder.getData(actual, 0, actual.length));
        assertArrayEquals(expected, actual);
    }

    /**
     * 验证全部消息编码器都接受统一的 512 KiB 上限。
     */
    @Test
    void allDataEncodersAcceptConfiguredMaximum() {
        byte[] maximumPayload = payload(SbePayloadLimits.MAX_DATA_LENGTH_BYTES);
        var buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(BUFFER_CAPACITY_BYTES));

        for (DataEncoderContract encoder : DATA_ENCODERS) {
            assertDoesNotThrow(
                    () -> encoder.writer().write(buffer, maximumPayload),
                    () -> encoder.name() + " did not accept the configured payload maximum"
            );
        }
    }

    /**
     * 验证全部消息编码器都拒绝超过 512 KiB 的 payload，保留明确的内存容量边界。
     */
    @Test
    void allDataEncodersRejectPayloadAboveConfiguredMaximum() {
        byte[] oversizedPayload = payload(SbePayloadLimits.MAX_DATA_LENGTH_BYTES + 1);
        var buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(BUFFER_CAPACITY_BYTES));

        for (DataEncoderContract encoder : DATA_ENCODERS) {
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> encoder.writer().write(buffer, oversizedPayload),
                    () -> encoder.name() + " accepted an oversized payload"
            );
            assertTrue(exception.getMessage().contains("length > maxValue for type"));
        }
    }

    /**
     * 验证复合类型元数据与消息编码器使用的统一上限一致。
     */
    @Test
    void payloadMetadataExposesConfiguredMaximum() {
        assertEquals(SbePayloadLimits.MAX_DATA_LENGTH_BYTES, PayloadDataEncoder.lengthMaxValue());
        assertEquals(SbePayloadLimits.MAX_DATA_LENGTH_BYTES, PayloadDataDecoder.lengthMaxValue());
    }

    /** 向 ConnectResponse 的 map value 写入 payload。 */
    private static void putConnectResponsePayload(MutableDirectBuffer buffer, byte[] payload) {
        var encoder = new ConnectResponseMessageEncoder().wrap(buffer, 0);
        encoder.payloadCount(1).next().putValue(payload, 0, payload.length);
    }

    /** 向 ConnectRequest 的 map value 写入 payload。 */
    private static void putConnectRequestPayload(MutableDirectBuffer buffer, byte[] payload) {
        var encoder = new ConnectRequestMessageEncoder().wrap(buffer, 0);
        encoder.payloadCount(1).next().putValue(payload, 0, payload.length);
    }

    /**
     * 创建内容稳定且非全零的测试 payload，便于验证完整复制。
     */
    private static byte[] payload(int length) {
        byte[] payload = new byte[length];
        for (int index = 0; index < payload.length; index++) {
            payload[index] = (byte) (index % 251);
        }
        return payload;
    }

    /** 标识一个生成编码器及其实际 payload 写入入口。 */
    private record DataEncoderContract(String name, DataWriter writer) {
    }

    /** 统一不同生成编码器的 {@code data/payload/map value} 写入方式。 */
    @FunctionalInterface
    private interface DataWriter {
        void write(MutableDirectBuffer buffer, byte[] payload);
    }
}
