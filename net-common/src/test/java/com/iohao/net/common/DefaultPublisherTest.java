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

import com.iohao.net.framework.*;
import io.aeron.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link DefaultPublisher} lifecycle behavior.
 *
 * @author 渔民小镇
 * @date 2026-05-03
 * @since 25.4
 */
class DefaultPublisherTest {
    private int originalPublisherOfferRetryLimit;

    @BeforeAll
    static void beforeAll() {
        PublisherTestKit.registerTestMessageEncoder();
    }

    @BeforeEach
    void setUp() {
        this.originalPublisherOfferRetryLimit = CoreGlobalConfig.publisherOfferRetryLimit;
    }

    @AfterEach
    void tearDown() {
        CoreGlobalConfig.publisherOfferRetryLimit = this.originalPublisherOfferRetryLimit;
    }

    @Test
    void shutdownWithoutStartupClosesPublications() {
        var publisher = new DefaultPublisher();
        var publication = RecordingPublication.create();
        publisher.addPublication("logic", publication);

        publisher.shutdown();

        assertTrue(publication.isClosed());
        assertEquals(1, publication.closeCount());
    }

    @Test
    void repeatedShutdownClosesPublicationsOnlyOnce() {
        var publisher = new DefaultPublisher();
        var publication = RecordingPublication.create();
        publisher.addPublication("logic", publication);

        publisher.shutdown();
        publisher.shutdown();

        assertTrue(publication.isClosed());
        assertEquals(1, publication.closeCount());
    }

    @Test
    void startupPublishesQueuedMessageToPublication() throws InterruptedException {
        var publisher = new DefaultPublisher();
        var publication = RecordingPublication.create();
        publisher.addPublication("logic", publication);

        try {
            publisher.startup();
            publisher.publishMessage("logic", new PublisherTestKit.TestMessage(1));

            PublisherTestKit.awaitUntil(() -> publication.offerCount() == 1);

            assertEquals(8, publication.lastOfferLength());
        } finally {
            publisher.shutdown();
        }
    }

    @Test
    void startupRetriesBackPressuredOfferUntilSuccess() throws InterruptedException {
        var publisher = new DefaultPublisher();
        var publication = RecordingPublication.create()
                .setOfferResults(Publication.BACK_PRESSURED, 1);
        publisher.addPublication("logic", publication);

        try {
            publisher.startup();
            publisher.publishMessage("logic", new PublisherTestKit.TestMessage(2));

            PublisherTestKit.awaitUntil(() -> publication.offerCount() == 2);

            assertEquals(8, publication.lastOfferLength());
        } finally {
            publisher.shutdown();
        }
    }

    @Test
    void startupDropsMessageAfterRetryLimitAndKeepsProcessingLaterMessages() throws InterruptedException {
        CoreGlobalConfig.publisherOfferRetryLimit = 2;

        var publisher = new DefaultPublisher();
        var publication = RecordingPublication.create()
                .setOfferResults(
                        Publication.BACK_PRESSURED,
                        Publication.BACK_PRESSURED,
                        Publication.BACK_PRESSURED,
                        1);
        publisher.addPublication("logic", publication);

        try {
            publisher.startup();
            publisher.publishMessage("logic", new PublisherTestKit.TestMessage(5));
            publisher.publishMessage("logic", new PublisherTestKit.TestMessage(6));

            PublisherTestKit.awaitUntil(() -> publication.offerCount() == 4);

            assertEquals(8, publication.lastOfferLength());
        } finally {
            publisher.shutdown();
        }
    }

    /** 回归编码异常不得关闭发布链路，后续正常消息仍需成功发送。 */
    @Test
    void startupDropsEncodingFailureAndKeepsProcessingLaterMessages() throws InterruptedException {
        var publisher = new DefaultPublisher();
        var publication = RecordingPublication.create();
        publisher.addPublication("logic", publication);

        try {
            publisher.startup();
            publisher.publishMessage("logic", new PublisherTestKit.FailingMessage());
            publisher.publishMessage("logic", new PublisherTestKit.TestMessage(7));

            PublisherTestKit.awaitUntil(() -> publication.offerCount() == 1);

            assertFalse(publication.isClosed());
            assertEquals(8, publication.lastOfferLength());
        } finally {
            publisher.shutdown();
        }
    }

    /** 回归 Aeron 最大消息长度预检只丢弃当前消息，后续合法消息仍需发布。 */
    @Test
    void startupDropsMessageAbovePublicationMaximumAndKeepsProcessingLaterMessages() throws InterruptedException {
        var publisher = new DefaultPublisher();
        var publication = RecordingPublication.create(8);
        publisher.addPublication("logic", publication);

        try {
            publisher.startup();
            publisher.publishMessage("logic", new PublisherTestKit.LargeMessage());
            publisher.publishMessage("logic", new PublisherTestKit.TestMessage(8));

            PublisherTestKit.awaitUntil(() -> publication.offerCount() == 1);

            assertFalse(publication.isClosed());
            assertEquals(8, publication.lastOfferLength());
        } finally {
            publisher.shutdown();
        }
    }

    /** 回归 Aeron {@code offer} 抛出异常后不得结束 Publisher 线程。 */
    @Test
    void startupDropsOfferExceptionAndKeepsProcessingLaterMessages() throws InterruptedException {
        var publisher = new DefaultPublisher();
        var publication = RecordingPublication.create()
                .setNextOfferException(new IllegalStateException("simulated offer failure"));
        publisher.addPublication("logic", publication);

        try {
            publisher.startup();
            publisher.publishMessage("logic", new PublisherTestKit.TestMessage(9));
            publisher.publishMessage("logic", new PublisherTestKit.TestMessage(10));

            PublisherTestKit.awaitUntil(() -> publication.offerCount() == 2);

            assertFalse(publication.isClosed());
            assertEquals(8, publication.lastOfferLength());
        } finally {
            publisher.shutdown();
        }
    }
}
