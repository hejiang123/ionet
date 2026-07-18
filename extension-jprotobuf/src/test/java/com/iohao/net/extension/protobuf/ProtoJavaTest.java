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
package com.iohao.net.extension.protobuf;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author 渔民小镇
 * @date 2022-01-24
 */
@Slf4j
public class ProtoJavaTest {
    @Test
    public void generate() {
        /*
         * .proto 文件生成
         *
         * 运行该类，会在当前项目 target/proto 目录下生成 .proto 文件
         */

        // 需要扫描的包名
        String packagePath = ProtoJavaTest.class.getPackageName();
        // .proto 文件生成
        GenerateFileKit.generate(packagePath);
    }

    @Test
    public void explicitRequiredFalseShouldGenerateProto3Optional() {
        String packagePath = ProtoJavaTest.class.getPackageName() + ".data";
        String sourcePath = Path.of("src/test/java").toAbsolutePath().toString();
        var regionMap = new ProtoJavaAnalyse().analyse(packagePath, sourcePath);
        String protoFile = regionMap.values().iterator().next().toProtoFile();

        assertTrue(protoFile.contains("optional int32 optionalId = 1;"));
        assertTrue(protoFile.contains("int32 implicitId = 2;"));
        assertTrue(protoFile.contains("int32 requiredId = 3;"));
        assertTrue(protoFile.contains("repeated string tags = 4;"));
        assertTrue(protoFile.contains("map<string,string> properties = 5;"));
        assertFalse(protoFile.contains("optional int32 implicitId = 2;"));
        assertFalse(protoFile.contains("optional int32 requiredId = 3;"));
        assertFalse(protoFile.contains("optional repeated string tags = 4;"));
        assertFalse(protoFile.contains("optional map<string,string> properties = 5;"));
    }
}
