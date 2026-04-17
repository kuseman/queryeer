package com.queryeer.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.queryeer.editor.OutlineScanner.OutlineEntry;
import com.queryeer.editor.OutlineScanner.ScanResult;

/** Unit tests for {@link OutlineScanner}. */
class OutlineScannerTest
{
    @Test
    void test_empty_document_returns_null()
    {
        assertNull(OutlineScanner.scan(""));
    }

    @Test
    void test_null_text_returns_null()
    {
        assertNull(OutlineScanner.scan(null));
    }

    @Test
    void test_no_directive_returns_null()
    {
        String text = "SELECT 1\nFROM dual\n-- just a comment\n";
        assertNull(OutlineScanner.scan(text));
    }

    @Test
    void test_directive_with_sql_comment_style()
    {
        String text = "-- Outline pattern: --###\n--### Rest calls\n--### Database ops\n";
        ScanResult result = OutlineScanner.scan(text);

        assertNotNull(result);
        assertEquals("--###", result.pattern());
        assertEquals(2, result.entries()
                .size());
        assertEquals("Rest calls", result.entries()
                .get(0)
                .label());
        assertEquals("Database ops", result.entries()
                .get(1)
                .label());
    }

    @Test
    void test_directive_with_c_style_comment()
    {
        String text = "// Outline pattern: //###\n// some code\n//### Section A\n//### Section B\n";
        ScanResult result = OutlineScanner.scan(text);

        assertNotNull(result);
        assertEquals("//###", result.pattern());
        assertEquals(2, result.entries()
                .size());
        assertEquals("Section A", result.entries()
                .get(0)
                .label());
        assertEquals("Section B", result.entries()
                .get(1)
                .label());
    }

    @Test
    void test_directive_with_hash_comment()
    {
        String text = "# Outline pattern: #===\n# header\n#=== Part 1\n#=== Part 2\n";
        ScanResult result = OutlineScanner.scan(text);

        assertNotNull(result);
        assertEquals("#===", result.pattern());
        assertEquals(2, result.entries()
                .size());
        assertEquals("Part 1", result.entries()
                .get(0)
                .label());
    }

    @Test
    void test_directive_with_no_matching_entries()
    {
        String text = "-- Outline pattern: --###\nSELECT 1\nFROM dual\n";
        ScanResult result = OutlineScanner.scan(text);

        assertNotNull(result);
        assertEquals("--###", result.pattern());
        assertTrue(result.entries()
                .isEmpty());
    }

    @Test
    void test_directive_on_line_20_within_threshold()
    {
        StringBuilder sb = new StringBuilder();
        // 19 filler lines (lines 1-19)
        for (int i = 0; i < 19; i++)
        {
            sb.append("-- filler line ")
                    .append(i + 1)
                    .append("\n");
        }
        // Directive on line 20
        sb.append("-- Outline pattern: --###\n");
        sb.append("--### Found it\n");

        ScanResult result = OutlineScanner.scan(sb.toString());
        assertNotNull(result);
        assertEquals(1, result.entries()
                .size());
        assertEquals("Found it", result.entries()
                .get(0)
                .label());
    }

    @Test
    void test_directive_on_line_21_beyond_threshold()
    {
        StringBuilder sb = new StringBuilder();
        // 20 filler lines (lines 1-20)
        for (int i = 0; i < 20; i++)
        {
            sb.append("-- filler line ")
                    .append(i + 1)
                    .append("\n");
        }
        // Directive on line 21 - should not be found
        sb.append("-- Outline pattern: --###\n");
        sb.append("--### Should not find\n");

        assertNull(OutlineScanner.scan(sb.toString()));
    }

    @Test
    void test_leading_whitespace_on_directive()
    {
        String text = "   -- Outline pattern: --###\n--### Entry\n";
        ScanResult result = OutlineScanner.scan(text);

        assertNotNull(result);
        assertEquals("--###", result.pattern());
        assertEquals(1, result.entries()
                .size());
    }

    @Test
    void test_entry_label_trimming()
    {
        String text = "-- Outline pattern: --###\n--###    Lots of spaces   \n--###NoSpace\n";
        ScanResult result = OutlineScanner.scan(text);

        assertNotNull(result);
        assertEquals(2, result.entries()
                .size());
        assertEquals("Lots of spaces", result.entries()
                .get(0)
                .label());
        assertEquals("NoSpace", result.entries()
                .get(1)
                .label());
    }

    @Test
    void test_entry_line_numbers_are_1_based()
    {
        String text = "-- Outline pattern: --###\nSELECT 1\n--### First\nSELECT 2\n--### Second\n";
        ScanResult result = OutlineScanner.scan(text);

        assertNotNull(result);
        assertEquals(2, result.entries()
                .size());
        assertEquals(3, result.entries()
                .get(0)
                .lineNumber());
        assertEquals(5, result.entries()
                .get(1)
                .lineNumber());
    }

    @Test
    void test_entry_offsets_are_correct()
    {
        // "-- Outline pattern: --###\n" = 26 chars (25 + newline)
        // "AB\n" = 3 chars (offset 26)
        // "--### First\n" starts at offset 29
        String text = "-- Outline pattern: --###\nAB\n--### First\n";
        ScanResult result = OutlineScanner.scan(text);

        assertNotNull(result);
        assertEquals(1, result.entries()
                .size());
        assertEquals(29, result.entries()
                .get(0)
                .offset());
    }

    @Test
    void test_case_insensitive_directive_keyword()
    {
        String text = "-- outline pattern: --###\n--### Entry\n";
        ScanResult result = OutlineScanner.scan(text);
        assertNotNull(result);

        text = "-- OUTLINE PATTERN: --###\n--### Entry\n";
        result = OutlineScanner.scan(text);
        assertNotNull(result);

        text = "-- Outline Pattern: --###\n--### Entry\n";
        result = OutlineScanner.scan(text);
        assertNotNull(result);
    }

    @Test
    void test_pattern_with_special_characters()
    {
        String text = "-- Outline pattern: -- ===\nfoo\n-- === Section One\nbar\n-- === Section Two\n";
        ScanResult result = OutlineScanner.scan(text);

        assertNotNull(result);
        assertEquals("-- ===", result.pattern());
        assertEquals(2, result.entries()
                .size());
        assertEquals("Section One", result.entries()
                .get(0)
                .label());
        assertEquals("Section Two", result.entries()
                .get(1)
                .label());
    }

    @Test
    void test_entry_with_leading_whitespace_on_line_is_matched()
    {
        String text = "-- Outline pattern: --###\n  --### Indented\n";
        ScanResult result = OutlineScanner.scan(text);

        assertNotNull(result);
        assertEquals(1, result.entries()
                .size());
        assertEquals("Indented", result.entries()
                .get(0)
                .label());
    }

    @Test
    void test_directive_pattern_is_trimmed()
    {
        // Pattern has trailing spaces in the directive
        String text = "-- Outline pattern:   --###   \n--### Entry\n";
        ScanResult result = OutlineScanner.scan(text);

        assertNotNull(result);
        assertEquals("--###", result.pattern());
        assertEquals(1, result.entries()
                .size());
    }

    @Test
    void test_entry_matching_pattern_only_without_label_is_skipped()
    {
        // A line that is exactly the pattern with no label text should be skipped
        String text = "-- Outline pattern: --###\n--###\n--### Valid\n";
        ScanResult result = OutlineScanner.scan(text);

        assertNotNull(result);
        assertEquals(1, result.entries()
                .size());
        assertEquals("Valid", result.entries()
                .get(0)
                .label());
    }

    @Test
    void test_multiple_entries_preserve_order()
    {
        String text = "-- Outline pattern: --###\n--### Alpha\n--### Beta\n--### Gamma\n--### Delta\n";
        ScanResult result = OutlineScanner.scan(text);

        assertNotNull(result);
        List<OutlineEntry> entries = result.entries();
        assertEquals(4, entries.size());
        assertEquals("Alpha", entries.get(0)
                .label());
        assertEquals("Beta", entries.get(1)
                .label());
        assertEquals("Gamma", entries.get(2)
                .label());
        assertEquals("Delta", entries.get(3)
                .label());
    }

    @Test
    void test_document_without_trailing_newline()
    {
        String text = "-- Outline pattern: --###\n--### Last entry";
        ScanResult result = OutlineScanner.scan(text);

        assertNotNull(result);
        assertEquals(1, result.entries()
                .size());
        assertEquals("Last entry", result.entries()
                .get(0)
                .label());
    }

    @Test
    void test_directive_is_not_counted_as_entry()
    {
        // The directive line itself contains the pattern but should not become an entry
        String text = "-- Outline pattern: --###\n--### Real entry\n";
        ScanResult result = OutlineScanner.scan(text);

        assertNotNull(result);
        assertEquals(1, result.entries()
                .size());
        assertEquals("Real entry", result.entries()
                .get(0)
                .label());
    }
}
