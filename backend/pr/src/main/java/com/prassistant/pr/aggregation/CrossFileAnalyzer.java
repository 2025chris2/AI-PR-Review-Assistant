package com.prassistant.pr.aggregation;

import com.prassistant.pr.review.model.FileReviewReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 跨文件关联分析器 — 纯本地规则引擎
 *
 * <p>不调用 AI，仅通过字符串匹配和规则从文件报告中预提取跨文件关联线索。
 * 这些线索作为提示词的一部分喂给 AI，让 AI 做最终判断。</p>
 *
 * <p>规则：</p>
 * <ul>
 *   <li>接口-实现一致性</li>
 *   <li>数据库-代码一致性</li>
 *   <li>重复逻辑检测</li>
 *   <li>事务边界检测</li>
 *   <li>架构层次一致性</li>
 * </ul>
 */
public final class CrossFileAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(CrossFileAnalyzer.class);

    private CrossFileAnalyzer() {
        // utility class
    }

    // 匹配接口名：IUserService / UserServiceInterface / UserService
    private static final Pattern INTERFACE_NAME_PATTERN = Pattern.compile(
            "I([A-Z][a-zA-Z0-9]+)|([A-Z][a-zA-Z0-9]+Interface)");

    // 匹配业务操作关键词（事务边界检测）
    private static final Pattern BIZ_KEYWORD_PATTERN = Pattern.compile(
            "(扣减|减库存|加库存|订单创建|支付|转账|下单|退款|出库|入库)");

    // 匹配重复逻辑关键词
    private static final Pattern DUPLICATE_KEYWORD_PATTERN = Pattern.compile(
            "(手动拼[接凑]SQL|SQL拼接|重复代码|公共方法|抽取工具|手动(分页|组装))");

    // 匹配数据库字段新增
    private static final Pattern DB_FIELD_PATTERN = Pattern.compile(
            "新增(?:字段|列|属性)\\s*`?([a-z_][a-zA-Z0-9_]*)`?");

    // 匹配矩阵运算关键词（数值稳定性检测用）
    private static final Pattern MATRIX_PATTERN = Pattern.compile(
            "\\b(matrix|decomposition|QR|SVD|eigen|norm|orthogonal|least.square|linear.algebra)\\b",
            Pattern.CASE_INSENSITIVE);

    // 匹配浮点直接比较模式
    private static final Pattern NUMERICAL_COMPARE_PATTERN = Pattern.compile(
            "(==\\s*0[^.]|==\\s*0\\.0|double\\[\\].*double\\[\\]|gram.schmidt|Gram.Schmidt)",
            Pattern.CASE_INSENSITIVE);

    // 匹配维度关键词
    private static final Pattern DIMENSION_PATTERN = Pattern.compile(
            "\\bm\\b.*\\bn\\b|column.*row|维度|非方阵|矩形矩阵",
            Pattern.CASE_INSENSITIVE);

    /**
     * 从文件报告中提取跨文件关联线索
     *
     * @param fileReports 第二层各文件的 Review 报告
     * @return 跨文件关联线索列表（每条线索是一个自然语言提示）
     */
    public static List<String> extractHints(List<FileReviewReport> fileReports) {
        if (fileReports == null || fileReports.isEmpty()) {
            return List.of();
        }

        List<String> hints = new ArrayList<>();

        // 规则 1：接口-实现一致性
        hints.addAll(detectInterfaceImplMismatch(fileReports));

        // 规则 2：数据库-代码一致性
        hints.addAll(detectDBCodeMismatch(fileReports));

        // 规则 3：重复逻辑检测
        hints.addAll(detectDuplicateLogic(fileReports));

        // 规则 4：事务边界检测
        hints.addAll(detectTransactionBoundary(fileReports));

        // 规则 5：架构层次一致性
        hints.addAll(detectArchitectureConsistency(fileReports));

        // 规则 6：数值稳定性静态扫描
        hints.addAll(detectNumericalIssues(fileReports));

        log.debug("CrossFileAnalyzer extracted {} hints from {} file reports", hints.size(), fileReports.size());
        return hints;
    }

    /**
     * 规则 1：接口-实现一致性
     * 如果 A 文件涉及接口签名修改，B 文件是实现类，提示检查同步实现
     */
    static List<String> detectInterfaceImplMismatch(List<FileReviewReport> reports) {
        List<String> hints = new ArrayList<>();

        // 收集所有涉及接口修改的文件
        for (FileReviewReport report : reports) {
            String summary = report.getOverallSummary();
            if (summary == null || summary.isBlank()) continue;

            // 摘要中提到"修改接口"或"修改了...方法签名"
            if (summary.contains("接口") || containsInterfaceModification(summary)) {
                String interfaceName = extractInterfaceName(summary);
                if (interfaceName != null) {
                    // 查找对应 Impl 文件
                    for (FileReviewReport other : reports) {
                        if (other == report) continue;
                        String otherPath = other.getFilePath();
                        if (otherPath != null && otherPath.contains("Impl")) {
                            hints.add("接口 [" + interfaceName + "] 在 [" + report.getFilePath()
                                    + "] 中被修改，实现类 [" + otherPath + "] 需要检查是否同步更新。");
                        }
                    }
                }
            }
        }

        return hints;
    }

    /**
     * 规则 2：数据库-代码一致性
     * 如果 Mapper.xml 新增了字段，检查实体类是否同步更新
     */
    static List<String> detectDBCodeMismatch(List<FileReviewReport> reports) {
        List<String> hints = new ArrayList<>();

        // 先收集 Mapper.xml 中新增的字段
        Map<String, String> mapperNewFields = new HashMap<>();
        for (FileReviewReport report : reports) {
            String path = report.getFilePath();
            if (path != null && (path.endsWith("Mapper.xml") || path.contains("mybatis"))) {
                String summary = report.getOverallSummary();
                if (summary == null) continue;

                Matcher m = DB_FIELD_PATTERN.matcher(summary);
                while (m.find()) {
                    String fieldName = m.group(1);
                    if (fieldName != null) {
                        mapperNewFields.put(fieldName, path);
                    }
                }
            }
        }

        if (mapperNewFields.isEmpty()) return hints;

        // 检查实体类
        for (FileReviewReport report : reports) {
            String path = report.getFilePath();
            if (path == null) continue;
            // 实体类通常是 .java 且不以 Impl/Controller 结尾
            boolean isEntity = path.endsWith(".java")
                    && !path.contains("Impl")
                    && !path.endsWith("Controller.java")
                    && !path.endsWith("Service.java");

            if (isEntity) {
                String summary = report.getOverallSummary();
                for (Map.Entry<String, String> entry : mapperNewFields.entrySet()) {
                    if (summary == null || !summary.contains(entry.getKey())) {
                        hints.add("Mapper 文件 [" + entry.getValue() + "] 新增了字段 `" + entry.getKey()
                                + "`，但实体类 [" + path + "] 中未发现同步更新。请确认。");
                    }
                }
            }
        }

        return hints;
    }

    /**
     * 规则 3：重复逻辑检测
     * 如果多个文件的风险描述中有相同关键词（如"手动拼接SQL"），提示抽取公共方法
     */
    static List<String> detectDuplicateLogic(List<FileReviewReport> reports) {
        List<String> hints = new ArrayList<>();

        Map<String, List<String>> patternFiles = new HashMap<>();
        for (FileReviewReport report : reports) {
            String summary = report.getOverallSummary();
            if (summary == null) continue;

            Matcher m = DUPLICATE_KEYWORD_PATTERN.matcher(summary);
            while (m.find()) {
                String keyword = m.group(1);
                patternFiles.computeIfAbsent(keyword, k -> new ArrayList<>())
                        .add(report.getFilePath());
            }
        }

        for (Map.Entry<String, List<String>> entry : patternFiles.entrySet()) {
            if (entry.getValue().size() >= 2) {
                hints.add("多个文件（" + String.join(", ", entry.getValue())
                        + "）均存在[" + entry.getKey() + "]，建议抽取公共方法或工具类。");
            }
        }

        return hints;
    }

    /**
     * 规则 4：事务边界检测
     * 如果多个文件涉及扣减/支付等业务操作，提示检查事务边界
     */
    static List<String> detectTransactionBoundary(List<FileReviewReport> reports) {
        List<String> hints = new ArrayList<>();

        List<String> bizFiles = new ArrayList<>();
        Set<String> bizKeywords = new HashSet<>();

        for (FileReviewReport report : reports) {
            String summary = report.getOverallSummary();
            if (summary == null) continue;

            Matcher m = BIZ_KEYWORD_PATTERN.matcher(summary);
            if (m.find()) {
                bizFiles.add(report.getFilePath());
                bizKeywords.add(m.group(1));
            }
        }

        if (bizFiles.size() >= 2) {
            hints.add("以下文件涉及业务操作（" + String.join(", ", bizKeywords)
                    + "）：" + String.join(", ", bizFiles)
                    + "。请检查事务边界是否覆盖了所有关联操作，避免数据不一致。");
        }

        return hints;
    }

    /**
     * 规则 5：架构层次一致性
     * 如果变更文件覆盖 Controller/Service/Mapper 三层，提示检查分层一致性
     */
    static List<String> detectArchitectureConsistency(List<FileReviewReport> reports) {
        List<String> hints = new ArrayList<>();

        boolean hasController = false;
        boolean hasService = false;
        boolean hasMapper = false;
        List<String> controllerFiles = new ArrayList<>();
        List<String> serviceFiles = new ArrayList<>();
        List<String> mapperFiles = new ArrayList<>();

        for (FileReviewReport report : reports) {
            String path = report.getFilePath();
            if (path == null) continue;

            if (path.endsWith("Controller.java")) {
                hasController = true;
                controllerFiles.add(path);
            } else if (path.contains("Service") || path.endsWith("Service.java")) {
                hasService = true;
                serviceFiles.add(path);
            } else if (path.endsWith("Mapper.xml") || path.endsWith("Mapper.java")) {
                hasMapper = true;
                mapperFiles.add(path);
            }
        }

        if (hasController && hasService && hasMapper) {
            hints.add("本次变更覆盖了 Controller/Service/Mapper 三层（"
                    + controllerFiles.size() + " Controller, " + serviceFiles.size()
                    + " Service, " + mapperFiles.size() + " Mapper），"
                    + "请检查各层之间的接口和数据流是否一致。");
        }

        return hints;
    }

    /**
     * 规则 6：数值稳定性静态扫描
     *
     * <p>通过正则扫描文件名和摘要，检测浮点比较、矩阵运算相关风险。</p>
     */
    static List<String> detectNumericalIssues(List<FileReviewReport> reports) {
        List<String> hints = new ArrayList<>();
        if (reports == null || reports.isEmpty()) return hints;

        boolean hasMatrixOps = false;
        boolean hasNumericalCompare = false;
        boolean hasDimensionRisk = false;

        for (FileReviewReport report : reports) {
            String path = report.getFilePath();
            String summary = report.getOverallSummary();

            if (path != null && MATRIX_PATTERN.matcher(path).find()) {
                hasMatrixOps = true;
            }
            if (summary == null) continue;

            if (NUMERICAL_COMPARE_PATTERN.matcher(summary).find()) {
                hasNumericalCompare = true;
            }
            if (DIMENSION_PATTERN.matcher(summary).find()
                    || summary.contains("m < n") || summary.contains("列数")) {
                hasDimensionRisk = true;
            }
        }

        List<String> fileList = reports.stream()
                .map(r -> r.getFilePath())
                .filter(f -> f != null)
                .collect(Collectors.toList());
        String files = String.join(", ", fileList);

        if (hasMatrixOps) {
            if (hasNumericalCompare) {
                hints.add("【数值稳定性】文件 [" + files + "] 涉及矩阵运算且存在浮点直接比较（== 0 / == 0.0）。"
                        + "建议使用 epsilon 容差替代精确相等，并评估算法本身的数值稳定性。");
            }
            if (!hasDimensionRisk) {
                hints.add("【维度边界】文件 [" + files + "] 涉及矩阵运算但未发现 m < n 前置校验。"
                        + "建议检查矩阵分解前是否需要验证维度。");
            }
        }
        return hints;
    }

    // ==================== 内部工具方法 ====================

    /**
     * 判断摘要是否包含接口修改的描述
     */
    private static boolean containsInterfaceModification(String summary) {
        // 检查是否提到"签名"、"接口方法"等关键词
        return summary.contains("签名") || summary.contains("接口方法")
                || summary.contains("interface") || Pattern.compile("修改.*方法").matcher(summary).find();
    }

    /**
     * 从摘要中提取接口名
     */
    static String extractInterfaceName(String summary) {
        if (summary == null) return null;

        Matcher m = INTERFACE_NAME_PATTERN.matcher(summary);
        if (m.find()) {
            return m.group(1) != null ? m.group(1) : m.group(2);
        }
        // 兜底：找最后一个 "I" 开头的词
        String[] words = summary.split("\\s+");
        for (String word : words) {
            if (word.startsWith("I") && word.length() > 1 && Character.isUpperCase(word.charAt(1))) {
                return word.replaceAll("[`'\".,;:!?()]", "");
            }
        }
        return null;
    }
}
