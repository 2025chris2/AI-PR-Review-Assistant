package com.prassistant.pr.aggregation.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PrMetadataTest {

    @Nested
    @DisplayName("Builder 构建")
    class Builder {

        @Test
        @DisplayName("应正确构建完整 PrMetadata")
        void shouldBuildFullMetadata() {
            PrMetadata meta = PrMetadata.builder()
                .title("feat: add login API")
                .description("Add OAuth2 login endpoint")
                .author("chris")
                .baseBranch("main")
                .headBranch("feat/login")
                .prUrl("https://github.com/example/pr/1")
                .totalFiles(5)
                .totalAdditions(120)
                .totalDeletions(30)
                .changedFileTypes(List.of("Java: 3", "XML: 2"))
                .build();

            assertEquals("feat: add login API", meta.getTitle());
            assertEquals("Add OAuth2 login endpoint", meta.getDescription());
            assertEquals("chris", meta.getAuthor());
            assertEquals("main", meta.getBaseBranch());
            assertEquals("feat/login", meta.getHeadBranch());
            assertEquals("https://github.com/example/pr/1", meta.getPrUrl());
            assertEquals(5, meta.getTotalFiles());
            assertEquals(120, meta.getTotalAdditions());
            assertEquals(30, meta.getTotalDeletions());
            assertEquals(2, meta.getChangedFileTypes().size());
        }

        @Test
        @DisplayName("默认值应为零值")
        void shouldHaveDefaultValues() {
            PrMetadata meta = new PrMetadata();

            assertNull(meta.getTitle());
            assertNull(meta.getDescription());
            assertEquals(0, meta.getTotalFiles());
            assertEquals(0, meta.getTotalAdditions());
            assertEquals(0, meta.getTotalDeletions());
        }

        @Test
        @DisplayName("Setters 应正确赋值")
        void shouldUseSetters() {
            PrMetadata meta = new PrMetadata();
            meta.setTitle("fix: resolve NPE");
            meta.setTotalFiles(3);

            assertEquals("fix: resolve NPE", meta.getTitle());
            assertEquals(3, meta.getTotalFiles());
        }
    }

    @Nested
    @DisplayName("changedFileTypes")
    class ChangedFileTypes {

        @Test
        @DisplayName("可为 null")
        void shouldAllowNull() {
            PrMetadata meta = PrMetadata.builder()
                .title("test")
                .build();

            assertNull(meta.getChangedFileTypes());
        }

        @Test
        @DisplayName("可为空列表")
        void shouldAllowEmptyList() {
            PrMetadata meta = PrMetadata.builder()
                .title("test")
                .changedFileTypes(List.of())
                .build();

            assertNotNull(meta.getChangedFileTypes());
            assertTrue(meta.getChangedFileTypes().isEmpty());
        }
    }
}
