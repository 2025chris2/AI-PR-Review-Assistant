package com.prassistant.pr.diff.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SanitizedDiffTest {

    @Test
    void testFieldSetAndGet() {
        SanitizedDiff diff = new SanitizedDiff();
        diff.setFilePath("src/main/User.java");
        diff.setStatus("modified");
        diff.setOriginalLineCount(50);
        diff.setSanitizedLineCount(40);
        diff.setSavingsRatio(0.2);

        assertEquals("src/main/User.java", diff.getFilePath());
        assertEquals("modified", diff.getStatus());
        assertEquals(50, diff.getOriginalLineCount());
        assertEquals(40, diff.getSanitizedLineCount());
        assertEquals(0.2, diff.getSavingsRatio(), 0.001);

        assertNotNull(diff.toString());
    }

    @Test
    void testDefaultValues() {
        SanitizedDiff diff = new SanitizedDiff();

        assertNull(diff.getFilePath());
        assertEquals("modified", diff.getStatus());
        assertNull(diff.getSanitizedContent());
        assertNotNull(diff.getHunks());
        assertTrue(diff.getHunks().isEmpty());
        assertEquals(0, diff.getOriginalLineCount());
        assertEquals(0, diff.getSanitizedLineCount());
        assertEquals(0.0, diff.getSavingsRatio(), 0.001);
    }

    @Test
    void testAddHunk() {
        SanitizedDiff diff = new SanitizedDiff();
        DiffHunk hunk = new DiffHunk();
        hunk.setOldStartLine(1);
        hunk.setNewStartLine(1);
        diff.getHunks().add(hunk);

        assertEquals(1, diff.getHunks().size());
        assertEquals(1, diff.getHunks().get(0).getOldStartLine());
    }
}
