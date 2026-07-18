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
import io.aeron.*;
import java.util.function.*;
import lombok.extern.slf4j.*;
import org.agrona.concurrent.*;

/**
 * Encodes and offers publisher messages to Aeron.
 *
 * @author 渔民小镇
 * @date 2026-05-01
 * @since 25.4
 */
@Slf4j
final class PublisherMessageKit {
    private PublisherMessageKit() {
    }

    /**
     * 编码并发布单条消息；任何可恢复的单消息异常都只丢弃当前消息。
     *
     * <p>编码、缓冲区边界、Aeron 最大消息长度和 {@code offer} 调用都在当前方法内收口，
     * 避免异常逃逸后结束 Publisher 工作线程并关闭其他 Publication。</p>
     *
     * @param publicationName Publication 名称
     * @param message         待编码消息
     * @param publication     Aeron Publication
     * @param headerEncoder   SBE 消息头编码器
     * @param buffer          当前 Publisher 独占的直接缓冲区
     * @param running         Publisher 是否仍在运行
     * @return 当前消息是否发布成功
     */
    static boolean publish(
            String publicationName,
            Object message,
            Publication publication,
            MessageHeaderEncoder headerEncoder,
            UnsafeBuffer buffer,
            BooleanSupplier running
    ) {
        MessageSbe<Object> encoder = SbeMessageManager.getMessageEncoder(message.getClass());
        if (encoder == null) {
            log.error("MessageSbe Error: {} not exist!", message.getClass().getSimpleName());
            return false;
        }

        int limit;
        try {
            encoder.encoder(message, headerEncoder, buffer);
            limit = encoder.limit();
        } catch (RuntimeException e) {
            // 编码异常只丢弃当前消息，避免 Publisher 线程退出并中断后续用户响应。
            log.error(
                    "MessageSbe encode error, publicationName: {}, messageType: {}, bufferCapacity: {}",
                    publicationName,
                    message.getClass().getName(),
                    buffer.capacity(),
                    e
            );
            return false;
        }

        return offer(publicationName, message, publication, buffer, limit, running);
    }

    /**
     * 校验编码长度并提交给 Aeron；提交阶段异常不得影响后续队列消息。
     */
    private static boolean offer(
            String publicationName,
            Object message,
            Publication publication,
            UnsafeBuffer buffer,
            int encodedLength,
            BooleanSupplier running
    ) {
        int publicationMaxMessageLength = -1;
        try {
            if (encodedLength < 0 || encodedLength > buffer.capacity()) {
                log.error(
                        "MessageSbe encoded length error, publicationName: {}, messageType: {}, encodedLength: {}, bufferCapacity: {}",
                        publicationName,
                        message.getClass().getName(),
                        encodedLength,
                        buffer.capacity()
                );
                return false;
            }

            publicationMaxMessageLength = publication.maxMessageLength();
            if (publicationMaxMessageLength > 0 && encodedLength > publicationMaxMessageLength) {
                log.error(
                        "Aeron message length error, publicationName: {}, messageType: {}, encodedLength: {}, publicationMaxMessageLength: {}",
                        publicationName,
                        message.getClass().getName(),
                        encodedLength,
                        publicationMaxMessageLength
                );
                return false;
            }

            long result = publication.offer(buffer, 0, encodedLength);
            if (result <= 0) {
                return PublicationOfferKit.offerAfterFailedResult(
                        publicationName,
                        message,
                        result,
                        () -> publication.offer(buffer, 0, encodedLength),
                        running
                );
            }

            return true;
        } catch (RuntimeException e) {
            // Aeron 提交异常只影响当前消息，Publisher 线程继续处理同一队列中的后续消息。
            log.error(
                    "Aeron message publish error, publicationName: {}, messageType: {}, encodedLength: {}, bufferCapacity: {}, publicationMaxMessageLength: {}",
                    publicationName,
                    message.getClass().getName(),
                    encodedLength,
                    buffer.capacity(),
                    publicationMaxMessageLength,
                    e
            );
            return false;
        }
    }
}
