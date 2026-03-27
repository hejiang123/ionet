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
package com.iohao.net.server.balanced;

import com.iohao.net.framework.protocol.Server;
import com.iohao.net.framework.protocol.ServerTypeEnum;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Regression tests for logic-server route replacement during rolling restarts.
 */
class DefaultLogicServerLoadBalancedTest {
    private static final int GM_CMD_MERGE = (3 << 16) | 1;

    @Test
    void unregisterOldServerKeepsReplacementRoutes() {
        DefaultLogicServerLoadBalanced loadBalanced = new DefaultLogicServerLoadBalanced();
        Server oldServer = logicServer(1001, 2001, "GameLogicServer", GM_CMD_MERGE);
        Server replacementServer = logicServer(1002, 2002, "GameLogicServer", GM_CMD_MERGE);

        loadBalanced.register(oldServer);
        loadBalanced.register(replacementServer);
        loadBalanced.unregister(oldServer);

        assertSame(replacementServer, loadBalanced.getServerByCmdMerge(GM_CMD_MERGE));
        assertSame(replacementServer, loadBalanced.tagServerMap.get("GameLogicServer"));
        assertSame(replacementServer, loadBalanced.getServer(1002));
    }

    @Test
    void unregisterCurrentServerRemovesRoutes() {
        DefaultLogicServerLoadBalanced loadBalanced = new DefaultLogicServerLoadBalanced();
        Server server = logicServer(1003, 2003, "GameLogicServer", GM_CMD_MERGE);

        loadBalanced.register(server);
        loadBalanced.unregister(server);

        assertNull(loadBalanced.getServerByCmdMerge(GM_CMD_MERGE));
        assertNull(loadBalanced.tagServerMap.get("GameLogicServer"));
        assertNull(loadBalanced.getServer(1003));
    }

    /**
     * Build a minimal logic-server snapshot for load-balancer tests.
     *
     * @param serverId  logic-server id
     * @param netId     net-server id
     * @param tag       server tag
     * @param cmdMerges registered command routes
     * @return immutable server snapshot
     */
    private Server logicServer(int serverId, int netId, String tag, int... cmdMerges) {
        return Server.recordBuilder()
                .setId(serverId)
                .setName(tag)
                .setTag(tag)
                .setServerType(ServerTypeEnum.LOGIC)
                .setNetId(netId)
                .setIp("127.0.0.1")
                .setPubName(String.valueOf(netId))
                .setCmdMerges(cmdMerges)
                .setPayloadMap(new HashMap<>())
                .build();
    }
}
