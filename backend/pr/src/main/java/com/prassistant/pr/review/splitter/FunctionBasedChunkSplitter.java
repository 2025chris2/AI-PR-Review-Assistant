package com.prassistant.pr.review.splitter;

import com.prassistant.pr.diff.model.SanitizedDiff;
import com.prassistant.pr.review.model.Chunk;
import com.prassistant.pr.review.util.TokenEstimator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 兜底分块策略 — 按函数/方法边界二次切分
 *
 * <p>当单个 Hunk 经 {@link HunkBasedChunkSplitter} 切分后仍超长时，
 * 使用 {@link #refine(Chunk)} 按方法签名做二次拆分，保证每块是完整逻辑单元。</p>
 *
 * <p>也支持直接通过 {@link #split(SanitizedDiff)} 对整个 diff 按函数边界切分。</p>
 */
@Component
public class FunctionBasedChunkSplitter implements ChunkSplitter {

    private final HunkBasedChunkSplitter hunkSplitter;

    public FunctionBasedChunkSplitter(HunkBasedChunkSplitter hunkSplitter) {
        this.hunkSplitter = hunkSplitter;
    }

    /**
     * Java 方法签名匹配：访问修饰符 + 返回类型 + 方法名 + 参数列表
     * 如 {@code public void doSomething(}、{@code private String getName(}
     */
    static final Pattern JAVA_METHOD_PATTERN = Pattern.compile(
        "^(?:\\s*)(?:public|private|protected)\\s+\\w+(?:<\\w+>)?\\s+\\w+\\s*\\(.*"
    );

    /**
     * 将仍超长的 Chunk 按函数边界二次切分
     *
     * @param chunk 需拆分的 Chunk（通常 {@link Chunk#isNeedsFurtherSplit()} 为 true）
     * @return 切分后的 Chunk 列表（含衔接上下文）
     */
    public List<Chunk> refine(Chunk chunk) {
        List<Chunk> result = new ArrayList<>();
        String content = chunk.getContent();
        if (content == null || content.isBlank()) {
            return result;
        }

        String[] lines = content.split("\n");
        List<Integer> splitPoints = findMethodBoundaries(lines);

        if (splitPoints.size() <= 1) {
            // 找不到方法边界或不需拆分，放回原块
            chunk.setNeedsFurtherSplit(false);
            result.add(chunk);
            return result;
        }

        // 按方法边界切分
        List<List<String>> subBlocks = splitAtBoundaries(lines, splitPoints);
        String previousSummary = chunk.getPreviousSummary();

        for (int i = 0; i < subBlocks.size(); i++) {
            List<String> block = subBlocks.get(i);
            String blockContent = String.join("\n", block);
            int tokenCount = TokenEstimator.count(blockContent);

            // 构建块内方法名作为锚点
            String methodName = extractFirstMethodName(block);

            Chunk subChunk = Chunk.builder()
                .chunkId(chunk.getChunkId() + "-fn-" + (i + 1))
                .filePath(chunk.getFilePath())
                .content(blockContent)
                .globalAnchor(String.format("文件: %s | 函数: %s | 第 %d/%d 子块",
                    chunk.getFilePath(), methodName, i + 1, subBlocks.size()))
                .previousSummary(previousSummary)
                .tokenCount(tokenCount)
                .hunkRange(chunk.getHunkRange())
                .needsFurtherSplit(false)
                .build();

            // 构建衔接上下文
            previousSummary = String.format("前序块已完成: %s(%s)", methodName, extractChangeSummary(block));
            result.add(subChunk);
        }

        return result;
    }

    @Override
    public List<Chunk> split(SanitizedDiff diff) {
        // 先尝试按 Hunk 分组，再对超长块按函数拆分
        List<Chunk> hunkChunks = hunkSplitter.split(diff);

        List<Chunk> result = new ArrayList<>();
        for (Chunk chunk : hunkChunks) {
            if (chunk.isNeedsFurtherSplit()) {
                result.addAll(refine(chunk));
            } else {
                result.add(chunk);
            }
        }
        return result;
    }

    /**
     * 找到内容行中所有方法签名的行索引
     */
    List<Integer> findMethodBoundaries(String[] lines) {
        List<Integer> boundaries = new ArrayList<>();
        // 第一个边界从 0 开始
        boundaries.add(0);
        for (int i = 0; i < lines.length; i++) {
            if (isMethodSignature(lines[i])) {
                // 如果该行不是第一个方法，则标记为新的切分点
                if (boundaries.size() > 0 && !boundaries.contains(i)) {
                    boundaries.add(i);
                }
            }
        }
        return boundaries;
    }

    /**
     * 判断某行是否为 Java 方法签名
     */
    boolean isMethodSignature(String line) {
        if (line == null) return false;
        String trimmed = line.startsWith("+") || line.startsWith("-") || line.startsWith(" ")
            ? line.substring(1).trim()
            : line.trim();
        // 过滤掉注释、import、类签名等
        if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")
            || trimmed.startsWith("@") || trimmed.startsWith("import")
            || trimmed.contains("class ") || trimmed.contains("interface ")) {
            return false;
        }
        return JAVA_METHOD_PATTERN.matcher(trimmed).matches();
    }

    /**
     * 在指定切分点将行数组切分为多个块
     */
    private List<List<String>> splitAtBoundaries(String[] lines, List<Integer> splitPoints) {
        List<List<String>> blocks = new ArrayList<>();
        for (int i = 0; i < splitPoints.size(); i++) {
            int start = splitPoints.get(i);
            int end = (i + 1 < splitPoints.size()) ? splitPoints.get(i + 1) : lines.length;
            List<String> block = new ArrayList<>();
            for (int j = start; j < end; j++) {
                block.add(lines[j]);
            }
            blocks.add(block);
        }
        return blocks;
    }

    /**
     * 从代码行中提取第一个方法名
     */
    private String extractFirstMethodName(List<String> lines) {
        for (String line : lines) {
            if (isMethodSignature(line)) {
                String cleaned = line.startsWith("+") || line.startsWith("-") || line.startsWith(" ")
                    ? line.substring(1).trim()
                    : line.trim();
                // 提取方法名：在返回类型和方法名之间
                // "public User findUser(" → "findUser"
                String[] parts = cleaned.split("\\s+");
                for (int i = 0; i < parts.length - 1; i++) {
                    // 跳过修饰符和返回类型，找到方法名
                    String next = parts[i + 1].replaceAll("\\(.*", "");
                    if (next.matches("[a-z_][a-zA-Z0-9_]+")) {
                        return next;
                    }
                }
                return cleaned.replaceAll("\\(.*", "").replaceAll(".*\\s", "");
            }
        }
        return "unknown";
    }

    /**
     * 生成简短的变更摘要
     */
    private String extractChangeSummary(List<String> lines) {
        int added = 0, removed = 0;
        for (String line : lines) {
            if (line.startsWith("+") && !line.startsWith("++")) added++;
            else if (line.startsWith("-") && !line.startsWith("--")) removed++;
        }
        return String.format("+%d/-%d", added, removed);
    }
}
