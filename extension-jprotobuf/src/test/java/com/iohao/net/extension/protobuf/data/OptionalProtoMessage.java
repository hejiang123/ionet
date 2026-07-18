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
package com.iohao.net.extension.protobuf.data;

import com.baidu.bjf.remoting.protobuf.annotation.Protobuf;
import com.baidu.bjf.remoting.protobuf.annotation.ProtobufClass;
import com.iohao.net.extension.protobuf.ProtoFileMerge;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Map;

/**
 * Optional proto field generation fixture.
 */
@ProtobufClass
@FieldDefaults(level = AccessLevel.PUBLIC)
@ProtoFileMerge(fileName = TempProtoFile.fileName, filePackage = TempProtoFile.filePackage)
public class OptionalProtoMessage {
    /** Explicit optional scalar. */
    @Protobuf(required = false)
    int optionalId;

    /** Implicit scalar keeps the old generated shape. */
    @Protobuf
    int implicitId;

    /** Explicit required scalar keeps the old generated shape. */
    @Protobuf(required = true)
    int requiredId;

    /** Repeated fields cannot be marked optional in proto3. */
    @Protobuf(required = false)
    List<String> tags;

    /** Map fields cannot be marked optional in proto3. */
    @Protobuf(required = false)
    Map<String, String> properties;
}
