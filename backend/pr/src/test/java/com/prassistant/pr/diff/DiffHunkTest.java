package com.prassistant.pr.diff;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DiffHunkTest {

    @Test
    void testFieldSetAndGet() {
        // 实例化赋值
        DiffHunk hunk = new DiffHunk();
        hunk.setOldStartLine(10);
        hunk.setOldLineCount(7);
        hunk.setNewStartLine(10);
        hunk.setNewLineCount(8);
        hunk.setSectionHeader("public class UserService");
        hunk.getLines().add(" unchanged line");

        // 断言校验
        assertEquals(10, hunk.getOldStartLine());
        assertEquals(7, hunk.getOldLineCount());
        assertEquals(10, hunk.getNewStartLine());
        assertEquals(8, hunk.getNewLineCount());
        assertEquals("public class UserService", hunk.getSectionHeader());
        assertEquals(List.of(" unchanged line"), hunk.getLines());

        // 简单校验 toString 不为空（Lombok 生效）
        assertNotNull(hunk.toString());
    }
}
