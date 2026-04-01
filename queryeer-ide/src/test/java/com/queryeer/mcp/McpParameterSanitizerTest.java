package com.queryeer.mcp;

import static com.queryeer.mcp.McpToolParameter.ParameterType.BOOLEAN;
import static com.queryeer.mcp.McpToolParameter.ParameterType.INTEGER;
import static com.queryeer.mcp.McpToolParameter.ParameterType.NUMBER;
import static com.queryeer.mcp.McpToolParameter.ParameterType.STRING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests for {@link McpParameterSanitizer} */
class McpParameterSanitizerTest
{
    // --- INTEGER ---

    @Test
    void integer_valid_positive()
    {
        assertEquals(42L, McpParameterSanitizer.sanitize("n", "42", INTEGER));
    }

    @Test
    void integer_valid_negative()
    {
        assertEquals(-7L, McpParameterSanitizer.sanitize("n", "-7", INTEGER));
    }

    @Test
    void integer_trims_whitespace()
    {
        assertEquals(10L, McpParameterSanitizer.sanitize("n", " 10 ", INTEGER));
    }

    @Test
    void integer_invalid_throws()
    {
        assertThrows(IllegalArgumentException.class, () -> McpParameterSanitizer.sanitize("n", "abc", INTEGER));
    }

    @Test
    void integer_float_string_throws()
    {
        assertThrows(IllegalArgumentException.class, () -> McpParameterSanitizer.sanitize("n", "1.5", INTEGER));
    }

    // --- NUMBER ---

    @Test
    void number_valid_integer_string()
    {
        assertEquals(5.0D, McpParameterSanitizer.sanitize("n", "5", NUMBER));
    }

    @Test
    void number_valid_decimal()
    {
        assertEquals(3.14D, McpParameterSanitizer.sanitize("n", "3.14", NUMBER));
    }

    @Test
    void number_invalid_throws()
    {
        assertThrows(IllegalArgumentException.class, () -> McpParameterSanitizer.sanitize("n", "xyz", NUMBER));
    }

    // --- BOOLEAN ---

    @Test
    void boolean_true_accepted()
    {
        assertEquals(true, McpParameterSanitizer.sanitize("n", "true", BOOLEAN));
    }

    @Test
    void boolean_false_accepted()
    {
        assertEquals(false, McpParameterSanitizer.sanitize("n", "false", BOOLEAN));
    }

    @Test
    void boolean_one_accepted()
    {
        assertEquals(true, McpParameterSanitizer.sanitize("n", "1", BOOLEAN));
    }

    @Test
    void boolean_zero_accepted()
    {
        assertEquals(false, McpParameterSanitizer.sanitize("n", "0", BOOLEAN));
    }

    @Test
    void boolean_case_insensitive()
    {
        assertEquals(true, McpParameterSanitizer.sanitize("n", "TRUE", BOOLEAN));
        assertEquals(false, McpParameterSanitizer.sanitize("n", "False", BOOLEAN));
    }

    @Test
    void boolean_invalid_throws()
    {
        assertThrows(IllegalArgumentException.class, () -> McpParameterSanitizer.sanitize("n", "yes", BOOLEAN));
    }

    // --- STRING ---

    @Test
    void string_plain_returned_as_is()
    {
        assertEquals("hello", McpParameterSanitizer.sanitize("n", "hello", STRING));
    }

    @Test
    void string_other_chars_unchanged()
    {
        assertEquals("foo; DROP TABLE--", McpParameterSanitizer.sanitize("n", "foo; DROP TABLE--", STRING));
    }

    // --- NULL handling ---

    @Test
    void null_value_throws_for_all_types()
    {
        for (McpToolParameter.ParameterType type : McpToolParameter.ParameterType.values())
        {
            assertThrows(IllegalArgumentException.class, () -> McpParameterSanitizer.sanitize("n", null, type));
        }
    }
}
