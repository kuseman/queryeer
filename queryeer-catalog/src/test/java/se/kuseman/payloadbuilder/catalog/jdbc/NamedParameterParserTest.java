package se.kuseman.payloadbuilder.catalog.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import se.kuseman.payloadbuilder.catalog.jdbc.NamedParameterParser.ParsedQuery;

/** Test of {@link NamedParameterParser}. */
class NamedParameterParserTest
{
    @Test
    void simpleReplacement()
    {
        String sql = "SELECT * FROM t WHERE name = :name";

        Map<String, Object> params = Map.of("name", "Alice");

        ParsedQuery pq = NamedParameterParser.parse(sql, params);

        assertEquals("SELECT * FROM t WHERE name = ?", pq.query());
        assertEquals(List.of("Alice"), pq.values());
    }

    @Test
    void multipleParams()
    {
        String sql = "SELECT * FROM t WHERE a = :a AND b = :b";

        Map<String, Object> params = Map.of("a", 1, "b", 2);

        ParsedQuery pq = NamedParameterParser.parse(sql, params);

        assertEquals("SELECT * FROM t WHERE a = ? AND b = ?", pq.query());
        assertEquals(List.of(1, 2), pq.values());
    }

    @Test
    void repeatedParams()
    {
        String sql = "SELECT * FROM t WHERE a = :x OR b = :x";

        Map<String, Object> params = Map.of("x", 42);

        ParsedQuery pq = NamedParameterParser.parse(sql, params);

        assertEquals("SELECT * FROM t WHERE a = ? OR b = ?", pq.query());
        assertEquals(List.of(42, 42), pq.values());
    }

    @Test
    void listExpansion()
    {
        String sql = "SELECT * FROM t WHERE id IN (:ids)";

        Map<String, Object> params = Map.of("ids", List.of(1, 2, 3));

        ParsedQuery pq = NamedParameterParser.parse(sql, params);

        assertEquals("SELECT * FROM t WHERE id IN (?, ?, ?)", pq.query());
        assertEquals(List.of(1, 2, 3), pq.values());
    }

    @Test
    void stringLiteralIgnored()
    {
        String sql = "SELECT ':not_a_param' AS col, name FROM t WHERE name = :name";

        Map<String, Object> params = Map.of("name", "Bob");

        ParsedQuery pq = NamedParameterParser.parse(sql, params);

        assertEquals("SELECT ':not_a_param' AS col, name FROM t WHERE name = ?", pq.query());
        assertEquals(List.of("Bob"), pq.values());
    }

    @Test
    void escapedSingleQuoteHandled()
    {
        String sql = "SELECT 'it''s fine:still_not_param' FROM t WHERE x = :x";

        Map<String, Object> params = Map.of("x", 1);

        ParsedQuery pq = NamedParameterParser.parse(sql, params);

        assertEquals("SELECT 'it''s fine:still_not_param' FROM t WHERE x = ?", pq.query());
        assertEquals(List.of(1), pq.values());
    }

    @Test
    void backtickQuoteHandled()
    {
        String sql = "SELECT `it''s fine:still_not_param` FROM t WHERE x = :x";

        Map<String, Object> params = Map.of("x", 1);

        ParsedQuery pq = NamedParameterParser.parse(sql, params);

        assertEquals("SELECT `it''s fine:still_not_param` FROM t WHERE x = ?", pq.query());
        assertEquals(List.of(1), pq.values());
    }

    @Test
    void doubleQuotedIdentifierIgnored()
    {
        String sql = "SELECT \":not_param\" FROM t WHERE x = :x";

        Map<String, Object> params = Map.of("x", 5);

        ParsedQuery pq = NamedParameterParser.parse(sql, params);

        assertEquals("SELECT \":not_param\" FROM t WHERE x = ?", pq.query());
        assertEquals(List.of(5), pq.values());
    }

    @Test
    void lineCommentIgnored()
    {
        String sql = """
                SELECT * FROM t
                -- :ignored
                WHERE x = :x
                """;

        Map<String, Object> params = Map.of("x", 7);

        ParsedQuery pq = NamedParameterParser.parse(sql, params);

        assertTrue(pq.query()
                .contains("-- :ignored"));
        assertTrue(pq.query()
                .contains("x = ?"));
        assertEquals(List.of(7), pq.values());
    }

    @Test
    void blockCommentIgnored()
    {
        String sql = """
                SELECT * FROM t
                /* :ignored */
                WHERE x = :x
                """;

        Map<String, Object> params = Map.of("x", 9);

        ParsedQuery pq = NamedParameterParser.parse(sql, params);

        assertTrue(pq.query()
                .contains("/* :ignored */"));
        assertTrue(pq.query()
                .contains("x = ?"));
        assertEquals(List.of(9), pq.values());
    }

    @Test
    void postgresCastNotTreatedAsParam1()
    {
        String sql = "val::int";

        Map<String, Object> params = Map.of("x", 3);

        ParsedQuery pq = NamedParameterParser.parse(sql, params);

        assertEquals("val::int", pq.query());
        assertEquals(List.of(), pq.values());
    }

    @Test
    void postgresCastNotTreatedAsParam()
    {
        String sql = "SELECT val::int FROM t WHERE x = :x";

        Map<String, Object> params = Map.of("x", 3);

        ParsedQuery pq = NamedParameterParser.parse(sql, params);

        assertEquals("SELECT val::int FROM t WHERE x = ?", pq.query());
        assertEquals(List.of(3), pq.values());
    }

    @Test
    void missingParamThrows()
    {
        String sql = "SELECT * FROM t WHERE x = :x";

        Map<String, Object> params = Map.of();

        assertThrows(IllegalArgumentException.class, () -> NamedParameterParser.parse(sql, params));
    }

    @Test
    void emptyListThrows()
    {
        String sql = "SELECT * FROM t WHERE id IN (:ids)";

        Map<String, Object> params = Map.of("ids", List.of());

        assertThrows(IllegalArgumentException.class, () -> NamedParameterParser.parse(sql, params));
    }

    @Test
    void mixedComplexQuery()
    {
        String sql = """
                SELECT ':nope', col::int
                FROM t
                WHERE name = :name
                AND id IN (:ids)
                -- :ignored
                """;

        Map<String, Object> params = Map.of("name", "Alice", "ids", List.of(10, 20));

        ParsedQuery pq = NamedParameterParser.parse(sql, params);

        assertTrue(pq.query()
                .contains("':nope'"));
        assertTrue(pq.query()
                .contains("col::int"));
        assertTrue(pq.query()
                .contains("name = ?"));
        assertTrue(pq.query()
                .contains("IN (?, ?)"));

        assertEquals(List.of("Alice", 10, 20), pq.values());
    }

    // --- corner cases ---

    /** Fix: isParamPart now includes digits and underscores. */
    @Test
    void paramWithUnderscoreAndDigits()
    {
        String sql = "SELECT * FROM t WHERE user_id = :user_id AND col2 = :col2";

        Map<String, Object> params = Map.of("user_id", 42, "col2", "v");

        ParsedQuery pq = NamedParameterParser.parse(sql, params);

        assertEquals("SELECT * FROM t WHERE user_id = ? AND col2 = ?", pq.query());
        assertEquals(List.of(42, "v"), pq.values());
    }

    /** Fix: guard against StringIndexOutOfBoundsException when SQL starts with ':param'. */
    @Test
    void paramAtStartOfSql()
    {
        String sql = ":x = 1";

        Map<String, Object> params = Map.of("x", 99);

        ParsedQuery pq = NamedParameterParser.parse(sql, params);

        assertEquals("? = 1", pq.query());
        assertEquals(List.of(99), pq.values());
    }

    /** Fix: backslash escape (MySQL-style) keeps parser inside the string literal. */
    @Test
    void backslashEscapeInSingleQuoteNotTreatedAsParam()
    {
        // Without the fix the parser would close the string at \' and treat :bar as a param.
        String sql = "SELECT * FROM t WHERE name = 'foo\\':bar' AND x = :x";

        Map<String, Object> params = Map.of("x", 7);

        ParsedQuery pq = NamedParameterParser.parse(sql, params);

        assertEquals("SELECT * FROM t WHERE name = 'foo\\':bar' AND x = ?", pq.query());
        assertEquals(List.of(7), pq.values());
    }

    /** Fix: PostgreSQL dollar-quoted blocks are opaque — :param inside is not substituted. */
    @Test
    void dollarQuoteIgnored()
    {
        String sql = "SELECT $body$:ignored$body$ FROM t WHERE x = :x";

        Map<String, Object> params = Map.of("x", 3);

        ParsedQuery pq = NamedParameterParser.parse(sql, params);

        assertEquals("SELECT $body$:ignored$body$ FROM t WHERE x = ?", pq.query());
        assertEquals(List.of(3), pq.values());
    }

    /** Fix: empty-tag dollar-quote ($$) is also treated as an opaque block. */
    @Test
    void emptyTagDollarQuoteIgnored()
    {
        String sql = "SELECT $$:ignored$$ FROM t WHERE x = :x";

        Map<String, Object> params = Map.of("x", 5);

        ParsedQuery pq = NamedParameterParser.parse(sql, params);

        assertEquals("SELECT $$:ignored$$ FROM t WHERE x = ?", pq.query());
        assertEquals(List.of(5), pq.values());
    }

    /** Fix: nested block comments are tracked by depth so inner content stays hidden. */
    @Test
    void nestedBlockCommentIgnored()
    {
        String sql = "SELECT * FROM t /* outer /* :inner */ :still_comment */ WHERE x = :x";

        Map<String, Object> params = Map.of("x", 11);

        ParsedQuery pq = NamedParameterParser.parse(sql, params);

        assertTrue(pq.query()
                .contains(":inner"));
        assertTrue(pq.query()
                .contains(":still_comment"));
        assertTrue(pq.query()
                .contains("x = ?"));
        assertEquals(List.of(11), pq.values());
    }

    /** Fix: double-quote "" escape keeps parser inside the identifier. */
    @Test
    void doubleQuoteEscapeHandled()
    {
        // "col""name" is a double-quoted identifier with an embedded double-quote.
        // Without the fix the parser would close the identifier at the first " and treat
        // :param as a live parameter.
        String sql = "SELECT \"col\"\"name\" FROM t WHERE x = :x";

        Map<String, Object> params = Map.of("x", 8);

        ParsedQuery pq = NamedParameterParser.parse(sql, params);

        assertEquals("SELECT \"col\"\"name\" FROM t WHERE x = ?", pq.query());
        assertEquals(List.of(8), pq.values());
    }

    /** Fix: unterminated single-quoted string throws instead of silently producing wrong SQL. */
    @Test
    void unterminatedSingleQuoteThrows()
    {
        String sql = "SELECT 'unclosed FROM t WHERE x = :x";

        Map<String, Object> params = Map.of("x", 1);

        assertThrows(IllegalArgumentException.class, () -> NamedParameterParser.parse(sql, params));
    }

    /** Fix: unterminated block comment throws. */
    @Test
    void unterminatedBlockCommentThrows()
    {
        String sql = "SELECT * FROM t /* unclosed WHERE x = :x";

        Map<String, Object> params = Map.of("x", 1);

        assertThrows(IllegalArgumentException.class, () -> NamedParameterParser.parse(sql, params));
    }

    /** A bare '$' that is not a dollar-quote opener is passed through unchanged. */
    @Test
    void dollarSignNotDollarQuote()
    {
        // $5 — dollar followed by a digit never forms a dollar-quote tag (tags must end with $)
        String sql = "SELECT $5 FROM t WHERE x = :x";

        Map<String, Object> params = Map.of("x", 1);

        ParsedQuery pq = NamedParameterParser.parse(sql, params);

        assertEquals("SELECT $5 FROM t WHERE x = ?", pq.query());
        assertEquals(List.of(1), pq.values());
    }

    /** Fix: line comment without trailing newline (EOF) is valid and does not throw. */
    @Test
    void lineCommentAtEofIsValid()
    {
        String sql = "SELECT * FROM t WHERE x = :x -- trailing comment";

        Map<String, Object> params = Map.of("x", 2);

        ParsedQuery pq = NamedParameterParser.parse(sql, params);

        assertTrue(pq.query()
                .contains("x = ?"));
        assertEquals(List.of(2), pq.values());
    }
}
