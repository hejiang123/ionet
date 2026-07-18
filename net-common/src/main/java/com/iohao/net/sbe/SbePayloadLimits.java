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

/**
 * 统一维护 ioNet SBE 可变 payload 的容量边界。
 *
 * <p>当前仓库只保留了 SBE 生成后的 Java 源码，没有提交原始 schema；
 * 因此生成编码器必须共同引用该常量，避免不同消息类型出现不一致的长度上限。</p>
 */
final class SbePayloadLimits {
    /** SBE {@code data} 字段允许编码的最大字节数。 */
    static final int MAX_DATA_LENGTH_BYTES = 512 * 1024;

    private SbePayloadLimits() {
    }
}
