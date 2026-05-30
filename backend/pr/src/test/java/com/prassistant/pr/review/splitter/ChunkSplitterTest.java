package com.prassistant.pr.review.splitter;

import com.prassistant.pr.diff.model.SanitizedDiff;
import com.prassistant.pr.review.model.Chunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkSplitterTest {

    @Test
    @DisplayName("接口应可被匿名类实现")
    void shouldBeImplementable() {
        ChunkSplitter splitter = diff -> List.of(
            Chunk.builder()
                .chunkId(diff.getFilePath() + "#hunk-1")
                .filePath(diff.getFilePath())
                .content(diff.getSanitizedContent())
                .build()
        );

        SanitizedDiff diff = new SanitizedDiff();
        diff.setFilePath("test.java");
        diff.setSanitizedContent("some code");

        List<Chunk> chunks = splitter.split(diff);

        assertEquals(1, chunks.size());
        assertEquals("test.java#hunk-1", chunks.get(0).getChunkId());
        assertEquals("test.java", chunks.get(0).getFilePath());
        assertEquals("some code", chunks.get(0).getContent());
    }
}
