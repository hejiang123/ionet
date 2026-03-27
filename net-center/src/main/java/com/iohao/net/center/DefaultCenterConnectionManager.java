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
package com.iohao.net.center;

import com.iohao.net.center.creator.*;
import com.iohao.net.common.*;
import com.iohao.net.common.kit.*;
import com.iohao.net.framework.protocol.*;
import io.aeron.*;
import io.aeron.logbuffer.*;
import java.util.*;
import java.util.stream.*;

/**
 * Default center-server connection manager backed by Aeron publications and subscriptions.
 *
 * @author 渔民小镇
 * @date 2025-09-27
 * @since 25.1
 */
final class DefaultCenterConnectionManager implements CenterConnectionManager {
    final Map<Integer, CenterClientConnection> connectionMap = CollKit.ofConcurrentHashMap();
    final Map<Integer, Publication> publicationMap = CollKit.ofConcurrentHashMap();

    final Aeron aeron;
    Subscription subscription;
    Publisher publisher;

    public DefaultCenterConnectionManager(CenterConnectionManagerCreatorParameter parameter) {
        this.aeron = parameter.aeron();
        this.publisher = parameter.publisher();
        this.init();
    }

    @Override
    public Stream<ServerMessage> streamServerMessage() {
        pruneDisconnectedConnections();
        return connectionMap.values().stream().map(CenterClientConnection::getMessage);
    }

    @Override
    public boolean containsNetId(int netId) {
        return publicationMap.containsKey(netId);
    }

    @Override
    public Publication getPublicationByNetId(int netId) {
        return this.publicationMap.get(netId);
    }

    @Override
    public void addConnection(CenterClientConnection connection) {
        pruneDisconnectedConnections();
        this.publicationMap.put(connection.getNetId(), connection.getPublication());
        this.connectionMap.put(connection.getServerId(), connection);
        this.publisher.addPublication(connection.getPubName(), connection.getPublication());
    }

    @Override
    public void publishMessage(String pubName, Object message) {
        publisher.publishMessage(pubName, message);
    }

    @Override
    public int poll(FragmentHandler fragmentHandler) {
        return this.subscription.poll(fragmentHandler, 1);
    }

    /**
     * Remove center-side snapshots whose Aeron publication has already disconnected.
     * <p>
     * Center replay uses this connection registry as the source of truth when a new node joins.
     * If dead entries are kept here, the center may replay offline servers back to new nodes and
     * let stale topology overwrite live routing state.
     * </p>
     */
    private void pruneDisconnectedConnections() {
        List<CenterClientConnection> disconnectedConnections = connectionMap.values().stream()
                .filter(connection -> !connection.getPublication().isConnected())
                .toList();

        disconnectedConnections.forEach(this::removeConnection);
    }

    /**
     * Drop one disconnected server snapshot and release its publication when no sibling server in
     * the same net-server process still uses it.
     *
     * @param connection disconnected center-side connection metadata
     */
    private void removeConnection(CenterClientConnection connection) {
        this.connectionMap.remove(connection.getServerId(), connection);

        int netId = connection.getNetId();
        boolean hasSiblingConnection = this.connectionMap.values().stream()
                .anyMatch(item -> item.getNetId() == netId);
        if (!hasSiblingConnection) {
            this.publicationMap.remove(netId, connection.getPublication());
        }

        Publication publication = connection.getPublication();
        if (!publication.isClosed()) {
            publication.close();
        }
    }

    private void init() {
        var channel = AeronConst.udpChannel.formatted("0.0.0.0", AeronConst.centerPort);
        this.subscription = this.aeron.addSubscription(channel, AeronConst.centerStreamId);
//        this.subscription = this.aeron.addSubscription(channel, AeronConst.centerId, image -> {
//
//            log.info("""
//                            A new publisher has connected
//                              channel: {}
//                              streamId: {}
//                              sessionId: {}
//                            """,
//                    image.subscription().channel(),
//                    image.subscription().streamId(),
//                    image.sessionId()
//            );
//        }, image -> {
//            log.info("""
//                            Publisher disconnected
//                              channel: {}
//                              streamId: {}
//                              sessionId: {}
//                            """,
//                    image.subscription().channel(),
//                    image.subscription().streamId(),
//                    image.sessionId()
//            );
//        });
    }
}
