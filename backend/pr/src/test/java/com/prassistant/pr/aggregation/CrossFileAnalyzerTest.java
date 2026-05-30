package com.prassistant.pr.aggregation;

import com.prassistant.pr.diff.model.FileChangeType;
import com.prassistant.pr.review.model.FileReviewReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CrossFileAnalyzerTest {

    private FileReviewReport report(String path, String summary) {
        return FileReviewReport.builder()
            .filePath(path)
            .overallSummary(summary)
            .status(FileChangeType.MODIFIED)
            .build();
    }

    @Nested
    @DisplayName("extractHints() — 入口")
    class ExtractHints {

        @Test
        @DisplayName("空列表返回空线索")
        void shouldReturnEmptyForEmptyList() {
            assertTrue(CrossFileAnalyzer.extractHints(List.of()).isEmpty());
        }

        @Test
        @DisplayName("null 返回空线索")
        void shouldReturnEmptyForNull() {
            assertTrue(CrossFileAnalyzer.extractHints(null).isEmpty());
        }

        @Test
        @DisplayName("无匹配时返回空线索")
        void shouldReturnEmptyWhenNoMatch() {
            var reports = List.of(
                report("HelloWorld.java", "新增了一个简单的工具方法")
            );
            assertTrue(CrossFileAnalyzer.extractHints(reports).isEmpty());
        }
    }

    @Nested
    @DisplayName("detectInterfaceImplMismatch() — 接口-实现一致性")
    class InterfaceImplMismatch {

        @Test
        @DisplayName("接口修改 + 存在 Impl 文件 → 生成提示")
        void shouldGenerateHintWhenInterfaceModified() {
            var reports = List.of(
                report("IUserService.java", "修改了接口 IUserService 的 findById 签名"),
                report("UserServiceImpl.java", "实现 UserServiceImpl")
            );

            List<String> hints = CrossFileAnalyzer.detectInterfaceImplMismatch(reports);

            assertEquals(1, hints.size());
            assertTrue(hints.get(0).contains("IUserService"));
            assertTrue(hints.get(0).contains("UserServiceImpl"));
        }

        @Test
        @DisplayName("无 Impl 文件 → 不生成提示")
        void shouldNotGenerateHintWithoutImpl() {
            var reports = List.of(
                report("IUserService.java", "修改了接口 IUserService 的 findById 签名"),
                report("UserServiceMock.java", "模拟类")
            );

            assertTrue(CrossFileAnalyzer.detectInterfaceImplMismatch(reports).isEmpty());
        }

        @Test
        @DisplayName("无接口修改 → 不生成提示")
        void shouldNotGenerateHintWithoutInterfaceChange() {
            var reports = List.of(
                report("IUserService.java", "新增工具方法"),
                report("UserServiceImpl.java", "实现 UserServiceImpl")
            );

            assertTrue(CrossFileAnalyzer.detectInterfaceImplMismatch(reports).isEmpty());
        }
    }

    @Nested
    @DisplayName("detectDBCodeMismatch() — 数据库-代码一致性")
    class DBCodeMismatch {

        @Test
        @DisplayName("Mapper 新增字段但实体类未同步 → 生成提示")
        void shouldGenerateHintWhenEntityMissingField() {
            var reports = List.of(
                report("UserMapper.xml", "新增字段 `phone`，修改 selectById 查询"),
                report("User.java", "优化实体类 toString 方法")
            );

            List<String> hints = CrossFileAnalyzer.detectDBCodeMismatch(reports);

            assertEquals(1, hints.size());
            assertTrue(hints.get(0).contains("phone"));
            assertTrue(hints.get(0).contains("UserMapper.xml"));
            assertTrue(hints.get(0).contains("User.java"));
        }

        @Test
        @DisplayName("无 Mapper.xml → 不生成提示")
        void shouldNotGenerateHintWithoutMapper() {
            var reports = List.of(
                report("UserService.java", "新增字段 `phone` 到实体类"),
                report("User.java", "新增 phone 字段")
            );

            assertTrue(CrossFileAnalyzer.detectDBCodeMismatch(reports).isEmpty());
        }
    }

    @Nested
    @DisplayName("detectDuplicateLogic() — 重复逻辑检测")
    class DuplicateLogic {

        @Test
        @DisplayName("多个文件出现相同关键词 → 生成提示")
        void shouldGenerateHintForDuplicates() {
            var reports = List.of(
                report("OrderMapper.xml", "手动拼接SQL 组装查询条件"),
                report("UserMapper.xml", "优化手动拼接SQL 逻辑")
            );

            List<String> hints = CrossFileAnalyzer.detectDuplicateLogic(reports);

            assertEquals(1, hints.size());
            assertTrue(hints.get(0).contains("手动拼接SQL"));
            assertTrue(hints.get(0).contains("OrderMapper.xml"));
            assertTrue(hints.get(0).contains("UserMapper.xml"));
        }

        @Test
        @DisplayName("仅一个文件有关键词 → 不生成提示")
        void shouldNotGenerateHintForSingleFile() {
            var reports = List.of(
                report("OrderMapper.xml", "手动拼接SQL 组装查询条件"),
                report("UserService.java", "新增 findByEmail 方法")
            );

            assertTrue(CrossFileAnalyzer.detectDuplicateLogic(reports).isEmpty());
        }
    }

    @Nested
    @DisplayName("detectTransactionBoundary() — 事务边界检测")
    class TransactionBoundary {

        @Test
        @DisplayName("多个文件涉及业务操作 → 生成提示")
        void shouldGenerateHintForBizOperations() {
            var reports = List.of(
                report("OrderService.java", "新增订单创建和扣减库存逻辑"),
                report("PaymentService.java", "实现支付退款流程")
            );

            List<String> hints = CrossFileAnalyzer.detectTransactionBoundary(reports);

            assertEquals(1, hints.size());
            assertTrue(hints.get(0).contains("OrderService"));
            assertTrue(hints.get(0).contains("PaymentService"));
            // 确保匹配到了业务关键词（"订单创建"先于"扣减"匹配）
            assertTrue(hints.get(0).contains("订单创建") || hints.get(0).contains("扣减"));
        }

        @Test
        @DisplayName("无业务操作关键词 → 不生成提示")
        void shouldNotGenerateHintWithoutBizKeywords() {
            var reports = List.of(
                report("UserService.java", "新增 findByEmail 方法"),
                report("LoggerUtil.java", "优化日志输出")
            );

            assertTrue(CrossFileAnalyzer.detectTransactionBoundary(reports).isEmpty());
        }
    }

    @Nested
    @DisplayName("detectArchitectureConsistency() — 架构层次一致性")
    class ArchitectureConsistency {

        @Test
        @DisplayName("覆盖三层架构 → 生成提示")
        void shouldGenerateHintForThreeLayers() {
            var reports = List.of(
                report("UserController.java", "新增用户查询接口"),
                report("UserService.java", "实现用户查询逻辑"),
                report("UserMapper.xml", "新增用户查询 SQL")
            );

            List<String> hints = CrossFileAnalyzer.detectArchitectureConsistency(reports);

            assertEquals(1, hints.size());
            assertTrue(hints.get(0).contains("Controller"));
            assertTrue(hints.get(0).contains("Service"));
            assertTrue(hints.get(0).contains("Mapper"));
        }

        @Test
        @DisplayName("仅一层变更 → 不生成提示")
        void shouldNotGenerateHintForSingleLayer() {
            var reports = List.of(
                report("UserService.java", "新增业务逻辑"),
                report("OrderService.java", "修改订单流程")
            );

            assertTrue(CrossFileAnalyzer.detectArchitectureConsistency(reports).isEmpty());
        }
    }

    @Nested
    @DisplayName("extractInterfaceName() — 工具方法")
    class ExtractInterfaceName {

        @Test
        @DisplayName("应提取标准接口名")
        void shouldExtractStandardInterfaceName() {
            assertEquals("UserService", CrossFileAnalyzer.extractInterfaceName("修改了 IUserService 接口"));
        }

        @Test
        @DisplayName("null 返回 null")
        void shouldReturnNullForNullInput() {
            assertNull(CrossFileAnalyzer.extractInterfaceName(null));
        }

        @Test
        @DisplayName("无匹配返回 null")
        void shouldReturnNullForNoMatch() {
            assertNull(CrossFileAnalyzer.extractInterfaceName("新增工具方法"));
        }
    }

    @Nested
    @DisplayName("完整场景 — 多规则组合触发")
    class CombinedScenarios {

        @Test
        @DisplayName("复杂 PR 应生成多条线索")
        void shouldGenerateMultipleHintsForComplexPr() {
            var reports = List.of(
                report("IOrderService.java", "修改了接口 IOrderService 的 createOrder 签名"),
                report("OrderServiceImpl.java", "实现 createOrder 方法"),
                report("OrderMapper.xml", "新增字段 `discount`，修改插入 SQL"),
                report("Order.java", "优化 toString"),
                report("OrderController.java", "新增订单创建接口"),
                report("InventoryService.java", "新增扣减库存逻辑")
            );

            List<String> hints = CrossFileAnalyzer.extractHints(reports);

            assertFalse(hints.isEmpty());

            // 应包含接口-实现提示
            boolean hasInterfaceHint = hints.stream().anyMatch(h -> h.contains("IOrderService"));
            // 应包含数据库-代码提示（Mapper 新增 discount 但 Order.java 未包含）
            boolean hasDbHint = hints.stream().anyMatch(h -> h.contains("discount"));
            // 应包含架构层次提示（Controller+Service+Mapper）
            boolean hasArchHint = hints.stream().anyMatch(h -> h.contains("Controller") && h.contains("Mapper"));

            assertTrue(hasInterfaceHint, "应包含接口-实现一致性提示");
            assertTrue(hasDbHint, "应包含数据库-代码一致性提示");
            assertTrue(hasArchHint, "应包含架构层次一致性提示");
        }
    }
}
