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
package com.iohao.net.extension.codegen;

import com.iohao.net.common.kit.*;
import com.iohao.net.common.kit.time.*;
import com.iohao.net.extension.protobuf.*;
import com.iohao.net.framework.core.doc.*;
import com.iohao.net.framework.core.exception.*;
import com.iohao.net.framework.protocol.wrapper.*;
import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.function.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.*;
import lombok.experimental.*;
import org.beetl.core.*;
import org.beetl.core.resource.*;

@UtilityClass
class DocumentGenerateKit {
    private GroupTemplate gt;
    String broadcastActionTemplatePath = "broadcast_action.txt";
    String broadcastExampleTemplatePath = "broadcast_action_example.txt";
    String broadcastExampleActionTemplatePath = "broadcast_action_example_action.txt";
    String actionMethodResultExampleTemplatePath = "action_method_result_example.txt";
    String gameCodeTemplatePath = "error_code.txt";
    String actionTemplatePath = "action.txt";
    private final Pattern JAVADOC_INLINE_LINK_PATTERN = Pattern.compile("\\{@link(?:plain)?\\s+([^}\\s]+)(?:\\s+([^}]+))?}");
    private final Pattern JAVADOC_INLINE_TEXT_PATTERN = Pattern.compile("\\{@(?:code|literal|value)\\s+([^}]*)}");
    private final Pattern JAVADOC_EMPTY_INLINE_TAG_PATTERN = Pattern.compile("\\{@(?:docRoot|inheritDoc)\\}");
    private final Pattern HTML_LIST_ITEM_OPEN_TAG_PATTERN = Pattern.compile("(?is)<li\\b[^>]*>");
    private final Pattern HTML_LIST_ITEM_CLOSE_TAG_PATTERN = Pattern.compile("(?is)</li\\b[^>]*>");
    private final Pattern HTML_STRUCTURAL_TAG_PATTERN = Pattern.compile("(?is)</?(?:p|br|div|pre|ul|ol|table|thead|tbody|tfoot|tr|blockquote|h[1-6])\\b[^>]*>");
    private final Pattern HTML_INLINE_TAG_PATTERN = Pattern.compile("(?is)</?(?:span|a|code|strong|em|b|i|u|small|sup|sub|td|th)\\b[^>]*>");
    private final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private final Pattern PUNCTUATION_SPACE_PATTERN = Pattern.compile("\\s+([,.;:!?，。；：！？])");

    static {
        init();
    }

    private void init() {
        try {
            ClasspathResourceLoader resourceLoader = new ClasspathResourceLoader("generate/");
            var cfg = Configuration.defaultConfiguration();
            gt = new GroupTemplate(resourceLoader, cfg);
            gt.registerFunction("codeEscape", new ExampleCodeEscape());
            gt.registerFunction("originalCode", new ExampleOriginalCode());
            gt.registerFunction("snakeName", new SnakeName());
            gt.registerFunction("tsString", new TypeScriptString());
            gt.registerFunction("tsDoc", new TypeScriptDoc());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    Template getTemplate(String path) {
        return gt.getTemplate(path);
    }

    String toSnakeName(String name) {
        return Optional.ofNullable(name)
                .filter(StrKit::isNotEmpty)
                .map(input -> {
                    StringBuilder result = new StringBuilder();
                    result.append(Character.toLowerCase(input.charAt(0)));

                    for (int i = 1; i < input.length(); i++) {
                        char currentChar = input.charAt(i);
                        if (Character.isUpperCase(currentChar)) {
                            result.append('_');
                            result.append(Character.toLowerCase(currentChar));
                        } else {
                            result.append(currentChar);
                        }
                    }

                    return result.toString();
                }).orElse("");
    }

    /**
     * Normalizes Javadoc and HTML fragments into plain text for generated source comments.
     */
    String toCommentText(Object value) {
        if (value == null) {
            return "";
        }

        String comment = value.toString();
        if (StrKit.isEmpty(comment)) {
            return "";
        }

        comment = comment.replace("\r\n", "\n").replace('\r', '\n');
        comment = stripJavadocCommentMarkers(comment);
        comment = replaceJavaDocInlineTags(comment);
        comment = HTML_LIST_ITEM_OPEN_TAG_PATTERN.matcher(comment).replaceAll("\n- ");
        comment = HTML_LIST_ITEM_CLOSE_TAG_PATTERN.matcher(comment).replaceAll("\n");
        comment = HTML_STRUCTURAL_TAG_PATTERN.matcher(comment).replaceAll("\n");
        comment = HTML_INLINE_TAG_PATTERN.matcher(comment).replaceAll(" ");
        comment = decodeHtmlEntities(comment);
        comment = WHITESPACE_PATTERN.matcher(comment).replaceAll(" ").trim();
        return PUNCTUATION_SPACE_PATTERN.matcher(comment).replaceAll("$1");
    }

    /**
     * Escapes normalized comment text for a TypeScript double-quoted string literal.
     */
    String toTsStringLiteralText(Object value) {
        String comment = toCommentText(value);
        StringBuilder result = new StringBuilder(comment.length() + 16);

        for (int i = 0; i < comment.length(); i++) {
            char c = comment.charAt(i);
            switch (c) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                case '\u2028' -> result.append("\\u2028");
                case '\u2029' -> result.append("\\u2029");
                default -> {
                    if (c < 0x20 || c == 0x7F) {
                        result.append("\\u%04x".formatted((int) c));
                    } else {
                        result.append(c);
                    }
                }
            }
        }

        return result.toString();
    }

    /**
     * Escapes normalized comment text for a TypeScript JSDoc or line comment context.
     */
    String toTsDocText(Object value) {
        return toCommentText(value).replace("*/", "* /");
    }

    private String stripJavadocCommentMarkers(String comment) {
        String[] lines = comment.split("\n", -1);
        StringBuilder result = new StringBuilder(comment.length());

        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.equals("*")) {
                continue;
            }

            if (trimmed.startsWith("* ")) {
                trimmed = trimmed.substring(2).strip();
            }

            if (StrKit.isEmpty(trimmed)) {
                continue;
            }

            if (result.length() > 0) {
                result.append('\n');
            }
            result.append(trimmed);
        }

        return result.toString();
    }

    private String replaceJavaDocInlineTags(String comment) {
        Matcher matcher = JAVADOC_INLINE_LINK_PATTERN.matcher(comment);
        StringBuffer buffer = new StringBuffer(comment.length());
        while (matcher.find()) {
            String label = matcher.group(2);
            String replacement = label != null && !label.isBlank()
                    ? label
                    : matcher.group(1);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);

        comment = replaceWithFirstGroup(JAVADOC_INLINE_TEXT_PATTERN, buffer.toString());
        return JAVADOC_EMPTY_INLINE_TAG_PATTERN.matcher(comment).replaceAll("");
    }

    private String replaceWithFirstGroup(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        StringBuffer buffer = new StringBuffer(value.length());
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(1)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String decodeHtmlEntities(String value) {
        return value.replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#34;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }

    class ExampleCodeEscape implements org.beetl.core.Function {
        @Override
        public Object call(Object[] paras, Context ctx) {
            return Optional.ofNullable(paras[0])
                    .map(Object::toString)
                    .map(str -> {
                        // Escape
                        return str.replace("<", "&lt;").replace(">", "&gt;");
                    }).orElse("");
        }
    }

    class ExampleOriginalCode implements org.beetl.core.Function {
        @Override
        public Object call(Object[] paras, Context ctx) {
            return Optional.ofNullable(paras[0])
                    .map(Object::toString)
                    .map(str -> {
                        // Escape
                        return str.replace("&lt;", "<").replace("&gt;", ">");
                    }).orElse("");
        }
    }

    class SnakeName implements org.beetl.core.Function {
        @Override
        public Object call(Object[] paras, Context ctx) {
            var value = paras[0];
            if (value == null) {
                return "";
            }

            return toSnakeName(value.toString());
        }
    }

    class TypeScriptString implements org.beetl.core.Function {
        @Override
        public Object call(Object[] paras, Context ctx) {
            Object value = paras == null || paras.length == 0 ? null : paras[0];
            return toTsStringLiteralText(value);
        }
    }

    class TypeScriptDoc implements org.beetl.core.Function {
        @Override
        public Object call(Object[] paras, Context ctx) {
            Object value = paras == null || paras.length == 0 ? null : paras[0];
            return toTsDocText(value);
        }
    }
}


@Accessors(chain = true)
@Setter(AccessLevel.PACKAGE)
@FieldDefaults(level = AccessLevel.PRIVATE)
final class GameCodeGenerate {
    Document document;
    /** True to generate framework built-in error codes, see {@link ActionErrorEnum}. */
    boolean internalErrorCode;

    Template template;
    String filePath;
    String fileSuffix;

    void generate() {
        Objects.requireNonNull(document);
        Objects.requireNonNull(template);
        Objects.requireNonNull(filePath);
        Objects.requireNonNull(fileSuffix);

        List<ErrorCodeDocument> errorCodeDocumentList = document.errorCodeDocumentList
                .stream()
                .filter(errorCodeDocument -> internalErrorCode || errorCodeDocument.value >= 0)
                .peek(errorCodeDocument -> {
                    errorCodeDocument.name = StrKit.firstCharToUpperCase(errorCodeDocument.name);
                }).toList();

        template.binding("errorCodeDocumentList", errorCodeDocumentList);
        GenerateInternalKit.binding(template);

        String fileText = template.render();
        String path = "%s%sGameCode%s".formatted(this.filePath, File.separator, this.fileSuffix);
        FileKit.writeUtf8String(fileText, path);
    }
}

@Accessors(chain = true)
@Setter(AccessLevel.PACKAGE)
@FieldDefaults(level = AccessLevel.PRIVATE)
final class BroadcastGenerate {
    Document document;
    TypeMappingDocument typeMappingDocument;

    Template template;
    String filePath;
    String fileSuffix;
    Function<String, Template> templateCreator;
    Consumer<BroadcastDocument> broadcastRenderBeforeConsumer;

    void generate() {
        Objects.requireNonNull(document);
        Objects.requireNonNull(typeMappingDocument);
        Objects.requireNonNull(template);
        Objects.requireNonNull(filePath);
        Objects.requireNonNull(fileSuffix);
        Objects.requireNonNull(templateCreator);

        Collection<BroadcastDocument> broadcastDocumentList = this.listBroadcastDocument();
        template.binding("broadcastDocumentList", broadcastDocumentList);
        GenerateInternalKit.binding(template);

        String fileText = template.render();
        String path = "%s%sListener%s".formatted(this.filePath, File.separator, this.fileSuffix);
        FileKit.writeUtf8String(fileText, path);
    }

    Collection<BroadcastDocument> listBroadcastDocument() {
        return document.broadcastDocumentList.stream()
                .peek(broadcastDocument -> {
                    // If no method name is specified, derive it using the following rule.
                    broadcastDocument.methodName = StrKit.firstCharToUpperCase(broadcastDocument.methodName);
                    // Keep broadcast descriptions language-neutral before template-specific escaping.
                    if (broadcastDocument.methodDescription != null) {
                        broadcastDocument.methodDescription = DocumentGenerateKit.toCommentText(broadcastDocument.methodDescription);
                    }

                    if (broadcastDocument.dataDescription != null) {
                        broadcastDocument.dataDescription = DocumentGenerateKit.toCommentText(broadcastDocument.dataDescription);
                    }

                    // Generate broadcast usage examples.
                    extractedBroadcastExampleCode(broadcastDocument);
                }).toList();
    }

    private void extractedBroadcastExampleCode(BroadcastDocument broadcastDocument) {
        Class<?> dataClass = broadcastDocument.dataClass;
        if (dataClass == null) {
            if (Objects.nonNull(broadcastRenderBeforeConsumer)) {
                broadcastRenderBeforeConsumer.accept(broadcastDocument);
            }
            return;
        }

        TypeMappingRecord typeMappingRecord = typeMappingDocument.getTypeMappingRecord(dataClass);
        broadcastDocument.bizDataType = typeMappingRecord.getParamTypeName();
        broadcastDocument.dataTypeIsInternal = typeMappingRecord.isInternalType();
        broadcastDocument.resultMethodTypeName = typeMappingRecord.getResultMethodTypeName();
        broadcastDocument.resultMethodListTypeName = typeMappingRecord.getResultMethodListTypeName();
        broadcastDocument.dataActualTypeName = typeMappingRecord.getParamTypeName();

        if (Objects.nonNull(broadcastRenderBeforeConsumer)) {
            broadcastRenderBeforeConsumer.accept(broadcastDocument);
        }

        broadcastDocument.exampleCode = render(broadcastDocument, typeMappingRecord, DocumentGenerateKit.broadcastExampleTemplatePath);

        // code action
        broadcastDocument.exampleCodeAction = render(broadcastDocument, typeMappingRecord, DocumentGenerateKit.broadcastExampleActionTemplatePath);
    }

    private String render(BroadcastDocument broadcastDocument, TypeMappingRecord typeMappingRecord, String examplePath) {
        Template exampleTemplate = templateCreator.apply(examplePath);

        if (exampleTemplate == null) {
            return "";
        }

        exampleTemplate.binding("_root", broadcastDocument);
        exampleTemplate.binding("typeMappingRecord", typeMappingRecord);

        return exampleTemplate.render().trim();
    }
}

@Accessors(chain = true)
@Setter(AccessLevel.PACKAGE)
@FieldDefaults(level = AccessLevel.PRIVATE)
final class ActionGenerate {
    ActionDocument actionDocument;
    /** action template */
    Template template;
    String filePath;
    String fileSuffix;

    Function<String, Template> templateCreator;

    void generate() {
        ActionDoc actionDoc = actionDocument.actionDoc;
        String classComment = actionDoc.javaClassDocInfo.getComment();
        template.binding("classComment", classComment);
        GenerateInternalKit.binding(template);

        // Route member variables
        List<ActionMemberCmdDocument> actionMemberCmdDocumentList = actionDocument.actionMemberCmdDocumentList;
        template.binding("actionMemberCmdDocumentList", actionMemberCmdDocumentList);

        // action method
        List<String> renderMethodList = actionDocument.actionMethodDocumentList
                .stream()
                .map(actionMethodDocument -> {
                    // example template code
                    var exampleTemplate = this.templateCreator.apply(DocumentGenerateKit.actionMethodResultExampleTemplatePath);
                    exampleTemplate.binding("_root", actionMethodDocument);
                    String exampleCode = exampleTemplate.render().trim();

                    // generate method
                    String templateFileName = getActionMethodDocumentTemplateFileName(actionMethodDocument);
                    Template methodTemplate = this.templateCreator.apply(templateFileName);
                    methodTemplate.binding("_root", actionMethodDocument);
                    methodTemplate.binding("exampleCode", exampleCode);

                    if (actionMethodDocument.internalBizDataType) {
                        methodTemplate.binding("protoPrefix", "");
                    }

                    return methodTemplate.render();
                })
                .toList();

        template.binding("methodCodeList", renderMethodList);

        Class<?> controllerClazz = actionDoc.controllerClazz;
        String simpleName = controllerClazz.getSimpleName();
        template.binding("ActionName", simpleName);

        String actionComment = actionDoc.javaClassDocInfo.getComment();
        template.binding("ActionComment", actionComment);

        String render = template.render();
        String actionFilePath = "%s%s%s%s".formatted(this.filePath, File.separator, simpleName, this.fileSuffix);
        FileKit.writeUtf8String(render, actionFilePath);
    }

    private String getActionMethodDocumentTemplateFileName(ActionMethodDocument actionMethodDocument) {
        if (actionMethodDocument.isVoid) {
            return actionMethodDocument.hasBizData
                    ? "action_method_void.txt"
                    : "action_method_void_no_param.txt";
        } else {
            return actionMethodDocument.hasBizData
                    ? "action_method.txt"
                    : "action_method_no_param.txt";
        }
    }
}

@UtilityClass
class GenerateInternalKit {
    void binding(Template template) {
        var generateTime = TimeFormatKit.ofPattern("yyyy-MM-dd").format(TimeKit.nowLocalDate());
        String generateTimeKey = new String(new byte[]{103, 101, 110, 101, 114, 97, 116, 101, 84, 105, 109, 101}, StandardCharsets.UTF_8);
        template.binding(generateTimeKey, "// %s %s".formatted(generateTimeKey, generateTime));

        String iohao = new String(new byte[]{105, 111, 104, 97, 111, 72, 111, 109, 101}, StandardCharsets.UTF_8);
        String u = new String(new byte[]{104, 116, 116, 112, 115, 58, 47, 47, 103, 105, 116, 104, 117, 98, 46, 99, 111, 109, 47, 105, 111, 104, 97, 111, 47, 105, 111, 110, 101, 116}, StandardCharsets.UTF_8);
        template.binding(iohao, "// %s".formatted(u));
    }
}

@UtilityClass
class InternalProtoClassKit {
    final Map<Class<?>, ProtoFileMergeClass> protoClassMap = CollKit.ofConcurrentHashMap();

    void analyseProtoClass(Document document) {
        if (!protoClassMap.isEmpty()) {
            return;
        }

        // --------- collect proto class ---------
        final Set<Class<?>> protoClassSet = new HashSet<>(32);
        document.actionDocList.stream()
                .flatMap(actionDoc -> actionDoc.actionCommandDocMap.values().stream())
                .forEach(actionCommandDoc -> {
                    var actionCommand = actionCommandDoc.actionCommand;
                    if (Objects.isNull(actionCommand)) {
                        // Only registered runtime commands can contribute request and response proto types.
                        return;
                    }

                    // --------- action return class ---------
                    var returnInfo = actionCommand.actionMethodReturn;
                    if (!returnInfo.isVoid()) {
                        Class<?> returnTypeClazz = returnInfo.actualTypeArgumentClass;
                        protoClassSet.add(returnTypeClazz);
                    }

                    // --------- action param class ---------
                    var bizParam = actionCommand.dataParameter;
                    if (Objects.nonNull(bizParam)) {
                        Class<?> actualTypeArgumentClazz = bizParam.actualTypeArgumentClass;
                        protoClassSet.add(actualTypeArgumentClazz);
                    }
                });

        document.broadcastDocumentList.forEach(broadcastDocument -> {
            Class<?> dataClass = broadcastDocument.dataClass;
            if (Objects.nonNull(dataClass)) {
                protoClassSet.add(dataClass);
            }
        });

        var excludeTypeList = List.of(
                int.class, Integer.class, IntValue.class,
                long.class, Long.class, LongValue.class,
                boolean.class, Boolean.class, BoolValue.class,
                String.class, StringValue.class
        );

        protoClassSet.stream().filter(protoClass -> {
            for (Class<?> aClass : excludeTypeList) {
                if (aClass == protoClass) {
                    return false;
                }
            }

            return true;
        }).forEach(protoClass -> {
            ProtoFileMerge annotation = protoClass.getAnnotation(ProtoFileMerge.class);
            if (annotation == null) {
                return;
            }

            String fileName = annotation.fileName();
            String filePackage = annotation.filePackage();

            var message = new ProtoFileMergeClass(fileName, filePackage, protoClass);
            protoClassMap.put(protoClass, message);
        });
    }
}

record ProtoFileMergeClass(String fileName, String filePackage, Class<?> dataClass) {
}

@Setter
@FieldDefaults(level = AccessLevel.PACKAGE)
abstract class AbstractDocumentGenerate implements DocumentGenerate {
    /** true : generate ActionErrorEnum */
    boolean internalErrorCode = true;
    /** your .proto path */
    boolean publicActionCmdName;
    /**
     * The storage path of the generated files.
     * By default, it will be generated in the ./target/action directory
     */
    String path = ArrayKit.join(new String[]{System.getProperty("user.dir"), "target", "code"}, File.separator);
    TypeMappingDocument typeMappingDocument;

    protected abstract void generateAction(Document document);

    protected abstract void generateBroadcast(Document document);

    protected abstract void generateErrorCode(Document document);
}
