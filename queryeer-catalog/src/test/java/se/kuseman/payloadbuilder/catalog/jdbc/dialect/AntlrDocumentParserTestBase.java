package se.kuseman.payloadbuilder.catalog.jdbc.dialect;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.StringReader;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.queryeer.api.editor.ITextEditorDocumentParser.CompletionItem;
import com.queryeer.api.editor.ITextEditorDocumentParser.CompletionResult;
import com.queryeer.api.editor.ITextEditorDocumentParser.ParseItem;

import se.kuseman.payloadbuilder.catalog.jdbc.CatalogCrawlService;
import se.kuseman.payloadbuilder.catalog.jdbc.IConnectionContext;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.AntlrDocumentParser.TokenOffset;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Catalog;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Column;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ForeignKey;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ForeignKeyColumn;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ObjectName;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Routine;
import se.kuseman.payloadbuilder.catalog.jdbc.model.RoutineParameter;
import se.kuseman.payloadbuilder.catalog.jdbc.model.TableSource;

/**
 * Base test suite for {@link AntlrDocumentParser} implementations. Contains standard-SQL tests that should pass for every dialect. Subclasses supply the concrete parser via {@link #createParser()}
 * and may add dialect-specific tests.
 */
abstract class AntlrDocumentParserTestBase
{
    protected CatalogCrawlService crawlService;
    protected IConnectionContext connectionContext;
    protected AntlrDocumentParser<?> documentParser;

    /** Catalog with dbo.MyProc(@param1 varchar, @param2 int) */
    protected static final Catalog TEST_CATALOG = new Catalog("testdb", emptyList(),
            List.of(new Routine("", "dbo", "MyProc", Routine.Type.PROCEDURE,
                    List.of(new RoutineParameter("@param1", "varchar", 50, 0, 0, true, false), new RoutineParameter("@param2", "int", 0, 10, 0, false, false)))),
            emptyList(), emptyList(), emptyList());

    /**
     * Catalog with two tables (dbo.tableA: col1 int PK, col2 varchar; dbo.tableB: id int PK, name varchar) and the same dbo.MyProc procedure as TEST_CATALOG.
     */
    protected static final Catalog TABLE_CATALOG = new Catalog("testdb", List.of(
            new TableSource("testdb", "dbo", "tableA", TableSource.Type.TABLE, List.of(new Column("col1", "int", 0, 10, 0, false, "PK_tableA"), new Column("col2", "varchar", 50, 0, 0, true, null))),
            new TableSource("testdb", "dbo", "tableB", TableSource.Type.TABLE, List.of(new Column("id", "int", 0, 10, 0, false, "PK_tableB"), new Column("name", "varchar", 100, 0, 0, true, null)))),
            List.of(new Routine("", "dbo", "MyProc", Routine.Type.PROCEDURE,
                    List.of(new RoutineParameter("@param1", "varchar", 50, 0, 0, true, false), new RoutineParameter("@param2", "int", 0, 10, 0, false, false)))),
            emptyList(), emptyList(), emptyList());

    /**
     * Catalog with FK relationships:
     * <ul>
     * <li>dbo.tableA: id int PK, name varchar</li>
     * <li>dbo.tableB: id int PK, tableA_id int (FK → tableA.id), value varchar</li>
     * <li>dbo.tableC: id int PK, tableA_id int (FK → tableA.id), tableB_id int (FK → tableB.id)</li>
     * </ul>
     */
    //@formatter:off
    protected static final Catalog FK_CATALOG = new Catalog("testdb",
            List.of(
                new TableSource("testdb", "dbo", "tableA", TableSource.Type.TABLE,
                    List.of(new Column("id", "int", 0, 10, 0, false, "PK_tableA"),
                            new Column("name", "varchar", 100, 0, 0, true, null))),
                new TableSource("testdb", "dbo", "tableB", TableSource.Type.TABLE,
                    List.of(new Column("id", "int", 0, 10, 0, false, "PK_tableB"),
                            new Column("tableA_id", "int", 0, 10, 0, true, null),
                            new Column("value", "varchar", 50, 0, 0, true, null))),
                new TableSource("testdb", "dbo", "tableC", TableSource.Type.TABLE,
                    List.of(new Column("id", "int", 0, 10, 0, false, "PK_tableC"),
                            new Column("tableA_id", "int", 0, 10, 0, true, null),
                            new Column("tableB_id", "int", 0, 10, 0, true, null)))),
            emptyList(),
            emptyList(),
            List.of(
                new ForeignKey(new ObjectName("testdb", "dbo", "FK_tableB_tableA"),
                    List.of(new ForeignKeyColumn(
                        new ObjectName("testdb", "dbo", "tableB"), "tableA_id",
                        new ObjectName("testdb", "dbo", "tableA"), "id"))),
                new ForeignKey(new ObjectName("testdb", "dbo", "FK_tableC_tableA"),
                    List.of(new ForeignKeyColumn(
                        new ObjectName("testdb", "dbo", "tableC"), "tableA_id",
                        new ObjectName("testdb", "dbo", "tableA"), "id"))),
                new ForeignKey(new ObjectName("testdb", "dbo", "FK_tableC_tableB"),
                    List.of(new ForeignKeyColumn(
                        new ObjectName("testdb", "dbo", "tableC"), "tableB_id",
                        new ObjectName("testdb", "dbo", "tableB"), "id")))),
            emptyList());
    //@formatter:on

    /** Factory method — subclasses create and return the parser under test. */
    protected abstract AntlrDocumentParser<?> createParser();

    @BeforeEach
    void setUp()
    {
        crawlService = mock(CatalogCrawlService.class);
        connectionContext = mock(IConnectionContext.class);
        when(connectionContext.getDatabase()).thenReturn("testdb");
        when(crawlService.getCatalog(any(), anyString())).thenReturn(TEST_CATALOG);
        documentParser = createParser();
    }

    protected CompletionResult complete(String query, int caretOffset)
    {
        documentParser.parse(new StringReader(query));
        return documentParser.getCompletionItems(caretOffset);
    }

    protected List<String> replacements(CompletionResult result)
    {
        return result.getItems()
                .stream()
                .map(CompletionItem::getReplacementText)
                .collect(Collectors.toList());
    }

    /** Returns only replacement texts that start with the given alias prefix (e.g. "t.") */
    protected List<String> aliasedColumns(CompletionResult result, String alias)
    {
        String prefix = alias + ".";
        return result.getItems()
                .stream()
                .map(CompletionItem::getReplacementText)
                .filter(r -> r != null
                        && r.startsWith(prefix))
                .collect(Collectors.toList());
    }

    protected List<ParseItem> parseErrors()
    {
        return documentParser.getParseResult()
                .stream()
                .filter(p -> p.getLevel() == ParseItem.Level.ERROR)
                .collect(Collectors.toList());
    }

    protected List<ParseItem> parseWarnings()
    {
        return documentParser.getParseResult()
                .stream()
                .filter(p -> p.getLevel() == ParseItem.Level.WARN)
                .collect(Collectors.toList());
    }

    protected void useTableCatalog()
    {
        when(crawlService.getCatalog(any(), anyString())).thenReturn(TABLE_CATALOG);
    }

    // -----------------------------------------------------------------
    // Column-suggestion tests (getCompletionItems integration)
    // -----------------------------------------------------------------

    @Test
    void test_columnSuggestions_whereClause()
    {
        useTableCatalog();
        // Caret is past the "AND" keyword — the prev-token is inside a search_condition,
        // so isExpression(prevTree) returns true and suggestColumns is called.
        String query = "SELECT * FROM dbo.tableA t WHERE t.col1 = 1 AND ";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result);
        List<String> cols = aliasedColumns(result, "t");
        assertTrue(cols.contains("t.col1"), "Expected t.col1 in WHERE suggestions");
        assertTrue(cols.contains("t.col2"), "Expected t.col2 in WHERE suggestions");
    }

    @Test
    void test_columnSuggestions_joinOnClause()
    {
        useTableCatalog();
        // Caret is at the end of the ON clause expression — both sides' aliases are in scope.
        String query = "SELECT * FROM dbo.tableA a INNER JOIN dbo.tableB b ON b.id = a.";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result);
        List<String> colsA = aliasedColumns(result, "a");
        assertTrue(colsA.contains("a.col1"), "Expected a.col1 in ON-clause suggestions");
        assertTrue(colsA.contains("a.col2"), "Expected a.col2 in ON-clause suggestions");
    }

    @Test
    void test_columnSuggestions_multipleAliasesInScope()
    {
        useTableCatalog();
        // Caret in WHERE — both aliases (a → tableA, b → tableB) are in scope.
        String query = "SELECT * FROM dbo.tableA a INNER JOIN dbo.tableB b ON b.id = a.col1 WHERE ";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result);
        List<String> colsA = aliasedColumns(result, "a");
        List<String> colsB = aliasedColumns(result, "b");
        assertTrue(colsA.contains("a.col1"), "Expected a.col1");
        assertTrue(colsA.contains("a.col2"), "Expected a.col2");
        assertTrue(colsB.contains("b.id"), "Expected b.id");
        assertTrue(colsB.contains("b.name"), "Expected b.name");
    }

    @Test
    void test_columnSuggestions_twoStatements()
    {
        useTableCatalog();
        // Two statements in a document; completions in the second statement's WHERE clause
        // must work regardless of the (valid) first statement above.
        String query = "SELECT 1;\n\nSELECT * FROM dbo.tableA t WHERE t.col1 = 1 AND ";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result);
        List<String> cols = aliasedColumns(result, "t");
        assertTrue(cols.contains("t.col1"), "Expected t.col1 from the second statement");
        assertTrue(cols.contains("t.col2"), "Expected t.col2 from the second statement");
    }

    @Test
    void test_columnSuggestions_miniParseFallback()
    {
        useTableCatalog();
        // A SELECT with a parse error immediately before a valid statement can cause panic-mode
        // error recovery to attach the second statement's tokens as error nodes at the file
        // level, breaking the normal parent-chain alias walk. The mini-parse re-parses the
        // isolated statement and should recover the aliases.
        // Use a realistic "broken" prefix: a SELECT missing its column list followed by a SEMI.
        String query = "SELECT FROM nowhere;\n\nSELECT * FROM dbo.tableA t WHERE t.col1 = 1 AND ";
        CompletionResult result = complete(query, query.length());

        // Result may be null if C3 returns no candidates after full parse-tree corruption.
        // When non-null, columns from tableA must be present — mini-parse rescued the alias.
        if (result != null)
        {
            List<String> cols = aliasedColumns(result, "t");
            assertTrue(cols.contains("t.col1"), "mini-parse fallback should recover t.col1");
        }
    }

    @Test
    void test_columnSuggestions_whereKeyword_withFollowingStatement()
    {
        useTableCatalog();
        // Caret is in the hidden-channel gap right after the WHERE keyword, with another
        // statement below. resolveEffectiveNode must anchor to WHERE (not the next SELECT),
        // so that findEnclosingStatementCtx finds the correct statement and C3 returns
        // search_condition candidates that lead to column suggestions.
        String query = "select *\nfrom dbo.tableA t\nwhere \n\nSELECT 1";
        int caretOffset = query.indexOf("where ") + "where ".length();
        CompletionResult result = complete(query, caretOffset);

        assertNotNull(result, "Expected column completions after WHERE with a following statement");
        List<String> cols = aliasedColumns(result, "t");
        assertTrue(cols.contains("t.col1"), "Expected t.col1 in WHERE suggestions");
        assertTrue(cols.contains("t.col2"), "Expected t.col2 in WHERE suggestions");
    }

    @Test
    void test_tableSuggestions_fromKeyword_withFollowingStatement()
    {
        useTableCatalog();
        // Caret is in the hidden-channel gap right after FROM, with another statement below.
        // resolveEffectiveNode must anchor to FROM (not the next SELECT), so that
        // findEnclosingStatementCtx finds the correct statement and C3 returns
        // table_source_item candidates.
        String query = "select *\nfrom \n\nSELECT * FROM dbo.tableB b WHERE b.id = 1";
        int caretOffset = query.indexOf("from ") + "from ".length();
        CompletionResult result = complete(query, caretOffset);

        assertNotNull(result, "Expected table completions after FROM with a following statement");
        List<String> items = replacements(result);
        assertTrue(items.contains("dbo.tableA"), "Expected dbo.tableA in FROM suggestions");
        assertTrue(items.contains("dbo.tableB"), "Expected dbo.tableB in FROM suggestions");
    }

    @Test
    void test_columnSuggestions_insideFunctionArg_afterComma()
    {
        useTableCatalog();
        // Caret is in the empty slot (the space) after the comma inside a scalar function call:
        // "t.col2 = LEFT(t.col1, |)" — the second argument position.
        // ANTLR's panic-mode error recovery disconnects "t.col1,)" from the main parse tree,
        // creating error nodes in a separate BatchContext. The fix searches all collected
        // statement contexts for the nearest Query_specificationContext (which still has the
        // valid FROM clause) when suggestTokenIndex is a COMMA and isExpression fails.
        String query = "SELECT * FROM dbo.tableA t WHERE t.col2 = LEFT(t.col1, )";
        int caretOffset = query.indexOf(", )") + 1; // the space between ',' and ')'
        CompletionResult result = complete(query, caretOffset);

        assertNotNull(result, "Expected column completions inside scalar function argument");
        List<String> cols = aliasedColumns(result, "t");
        assertTrue(cols.contains("t.col1"), "Expected t.col1 in function arg suggestions, got aliased: " + cols + ", all: " + replacements(result));
        assertTrue(cols.contains("t.col2"), "Expected t.col2 in function arg suggestions");
    }

    @Test
    void test_columnSuggestions_insideFunctionArg_multiStatement()
    {
        useTableCatalog();
        // Same as above but with a preceding statement. The fix searches all collected statement
        // contexts for the nearest Query_specificationContext that has table sources.
        String query = "SELECT 1;\n\nSELECT * FROM dbo.tableA t WHERE t.col2 = LEFT(t.col1, )";
        int caretOffset = query.indexOf(", )") + 1;
        CompletionResult result = complete(query, caretOffset);

        assertNotNull(result, "Expected column completions inside function arg with preceding statement");
        List<String> cols = aliasedColumns(result, "t");
        assertTrue(cols.contains("t.col1"), "Expected t.col1 in function arg suggestions");
        assertTrue(cols.contains("t.col2"), "Expected t.col2 in function arg suggestions");
    }

    @Test
    void test_columnSuggestions_insideFunctionArg_standaloneExpr()
    {
        useTableCatalog();
        // "AND LEFT(col, )" where LEFT() is a standalone predicate (missing comparison operator).
        // Compound parse errors may cause more aggressive error recovery. If completions are
        // returned, they must include t.col1; the important thing is no crash or wrong data.
        String query = "SELECT * FROM dbo.tableA t WHERE t.col1 = 1 AND LEFT(t.col1, )";
        int caretOffset = query.indexOf(", )") + 1; // the space between ',' and ')'
        CompletionResult result = complete(query, caretOffset);

        if (!result.getItems()
                .isEmpty())
        {
            List<String> cols = aliasedColumns(result, "t");
            assertTrue(cols.contains("t.col1"), "Expected t.col1 in standalone-expr function arg suggestions");
        }
    }

    @Test
    void test_columnSuggestions_insideFunctionArg_caretOnCloseParen()
    {
        useTableCatalog();
        // Caret is positioned exactly on the closing ')' after a comma-separated argument list:
        // "LEFT(t.col1, |)" where the caret sits on ')' rather than in the space before it.
        // findTokenFromOffset returns the ')' token in this case, so suggestTokenIndex is ')' not
        // COMMA. The fix checks whether the nearest preceding default-channel token is COMMA and
        // applies the same query-spec fallback.
        String query = "SELECT * FROM dbo.tableA t WHERE t.col2 = LEFT(t.col1, )";
        int caretOffset = query.indexOf(", )") + 2; // the ')' character
        CompletionResult result = complete(query, caretOffset);

        assertNotNull(result, "Expected column completions when caret is on closing paren after comma");
        List<String> cols = aliasedColumns(result, "t");
        assertTrue(cols.contains("t.col1"), "Expected t.col1, got aliased: " + cols + ", all: " + replacements(result));
        assertTrue(cols.contains("t.col2"), "Expected t.col2");
    }

    @Test
    void test_columnSuggestions_insideFunctionArg_standaloneWhere_caretOnCloseParen()
    {
        useTableCatalog();
        // Same as above but LEFT() is the only predicate in the WHERE clause (no comparison operator).
        // "WHERE LEFT(col, |)" — caret on ')'. This is the pattern reported by the user.
        String query = "SELECT * FROM dbo.tableA t WHERE LEFT(t.col1, )";
        int caretOffset = query.indexOf(", )") + 2; // the ')' character
        CompletionResult result = complete(query, caretOffset);

        assertNotNull(result, "Expected column completions inside standalone-WHERE function arg");
        List<String> cols = aliasedColumns(result, "t");
        assertTrue(cols.contains("t.col1"), "Expected t.col1, got aliased: " + cols + ", all: " + replacements(result));
    }

    @Test
    void test_columnSuggestions_insideFunctionArg_firstArg_partialIdentifier()
    {
        useTableCatalog();
        // Caret is at the end of a partial identifier inside the first (and only) argument of a
        // scalar function call: "WHERE left(t.c|" — no closing paren, no comma, caret at stop+1 of
        // the last real token. findTokenFromOffset returns the identifier token and
        // getStopIndex() < caretOffset fires, which previously advanced c3TokenIndex all the way to
        // EOF. At EOF C3 asks "what follows left(t.c?" and returns ")" / "," instead of
        // RULE_expression, so no column suggestions were produced.
        // Fix: do not advance c3TokenIndex when the next default-channel token is EOF so that C3
        // correctly sees the identifier as the expression-start position.
        String query = "SELECT * FROM dbo.tableA t WHERE left(t.c";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result, "Expected column completions inside function first arg at EOF");
        List<String> cols = aliasedColumns(result, "t");
        assertTrue(cols.contains("t.col1"), "Expected t.col1 in suggestions, got: " + cols + ", all: " + replacements(result));
        assertTrue(cols.contains("t.col2"), "Expected t.col2 in suggestions");
    }

    @Test
    void test_columnSuggestions_insideFunctionArg_genericScalar()
    {
        useTableCatalog();
        // Same as partialIdentifier but with a generic scalar function (UPPER, not the special LEFT rule).
        String query = "SELECT * FROM dbo.tableA t WHERE upper(t.c";
        CompletionResult result = complete(query, query.length());
        assertNotNull(result, "Expected column completions inside generic scalar function arg");
        List<String> cols = aliasedColumns(result, "t");
        assertTrue(cols.contains("t.col1"), "Expected t.col1, got: " + cols + ", all: " + replacements(result));
    }

    @Test
    void test_columnSuggestions_insideFunctionArg_firstArg_afterOpenParen()
    {
        useTableCatalog();
        // Caret is immediately after the opening '(' of a scalar function call:
        // "WHERE left(|" — no argument typed yet. The caret is in whitespace (or at EOF)
        // right after '('. Column completions should be offered for the first argument.
        String query = "SELECT * FROM dbo.tableA t WHERE left(";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result, "Expected column completions right after opening paren of function");
        List<String> cols = aliasedColumns(result, "t");
        assertTrue(cols.contains("t.col1"), "Expected t.col1 in suggestions, got: " + cols + ", all: " + replacements(result));
        assertTrue(cols.contains("t.col2"), "Expected t.col2 in suggestions");
    }

    @Test
    void test_columnSuggestions_insideFunctionArg_firstArg_withCommaAndSecondArg()
    {
        useTableCatalog();
        // Caret is after the first argument 'n' in a complete scalar-function call with a second
        // argument: "WHERE left(t.col|, 5)". The query has a parse error (left() is not a boolean)
        // so error recovery may detach 't.col' from the expression context, but
        // isInsideFunctionArg should still detect the function-argument position via the token
        // stream (ID preceded through DOT/ID chain by '(').
        String query = "SELECT * FROM dbo.tableA t WHERE left(t.col1, 5) = 'x'";
        int caretOffset = query.indexOf("t.col1") + "t.col1".length();
        CompletionResult result = complete(query, caretOffset);

        assertNotNull(result, "Expected column completions inside function first arg with trailing comma+arg");
        List<String> cols = aliasedColumns(result, "t");
        assertTrue(cols.contains("t.col1"), "Expected t.col1 in suggestions, got: " + cols + ", all: " + replacements(result));
        assertTrue(cols.contains("t.col2"), "Expected t.col2 in suggestions");
    }

    @Test
    void test_columnSuggestions_insideFunctionArg_standaloneWhere_firstArg_withCommaAndSecondArg()
    {
        useTableCatalog();
        // Same as above but LEFT() is the only predicate in the WHERE clause (no comparison).
        // This mirrors the user-reported pattern: "WHERE left(n, integer_expression)" with caret
        // after 'n'. Parse errors are more likely here since the WHERE clause has no boolean result.
        String query = "SELECT * FROM dbo.tableA t WHERE left(t.col1, 5)";
        int caretOffset = query.indexOf("t.col1") + "t.col1".length();
        CompletionResult result = complete(query, caretOffset);

        assertNotNull(result, "Expected column completions in standalone-WHERE function first arg with trailing comma+arg");
        List<String> cols = aliasedColumns(result, "t");
        assertTrue(cols.contains("t.col1"), "Expected t.col1, got: " + cols + ", all: " + replacements(result));
        assertTrue(cols.contains("t.col2"), "Expected t.col2");
    }

    // -----------------------------------------------------------------
    // Dot-trigger tests (caret exactly after a dot)
    // -----------------------------------------------------------------

    @Test
    void test_columnSuggestions_dotTrigger_whereClause_simpleWhere()
    {
        useTableCatalog();
        // Caret is immediately after the dot when the WHERE clause contains only "a." with
        // nothing after it — the user just typed the alias qualifier. ANTLR's error recovery
        // for the incomplete expression must not prevent column completions from being produced.
        String query = "SELECT * FROM dbo.tableA a WHERE a.";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result, "Expected column completions when caret is exactly after dot in WHERE");
        List<String> cols = aliasedColumns(result, "a");
        assertTrue(cols.contains("a.col1"), "Expected a.col1, got: " + cols + ", all: " + replacements(result));
        assertTrue(cols.contains("a.col2"), "Expected a.col2");
    }

    @Test
    void test_columnSuggestions_dotTrigger_selectList()
    {
        useTableCatalog();
        // Caret is immediately after the dot in the SELECT list: "SELECT a. FROM ..."
        // The expression context should be intact here (SELECT list is always an expression).
        String query = "SELECT a. FROM dbo.tableA a";
        int caretOffset = query.indexOf("a.") + 2; // right after the dot
        CompletionResult result = complete(query, caretOffset);

        assertNotNull(result, "Expected column completions when caret is exactly after dot in SELECT list");
        List<String> cols = aliasedColumns(result, "a");
        assertTrue(cols.contains("a.col1"), "Expected a.col1, got: " + cols + ", all: " + replacements(result));
        assertTrue(cols.contains("a.col2"), "Expected a.col2");
    }

    @Test
    void test_columnSuggestions_dotTrigger_whereClause_multipleJoins()
    {
        useTableCatalog();
        // Caret after the dot in WHERE with multiple joined tables in scope.
        // All aliased columns from all tables in scope should be offered.
        String query = "SELECT * FROM dbo.tableA a INNER JOIN dbo.tableB b ON a.col1 = b.id WHERE b.";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result, "Expected column completions when caret is exactly after dot in multi-join WHERE");
        List<String> colsB = aliasedColumns(result, "b");
        assertTrue(colsB.contains("b.id"), "Expected b.id, got: " + colsB + ", all: " + replacements(result));
        assertTrue(colsB.contains("b.name"), "Expected b.name");
    }

    // -----------------------------------------------------------------
    // EXISTS / NOT EXISTS subquery completion tests
    // -----------------------------------------------------------------

    @Test
    void test_columnSuggestions_existsSubquery_whereKeyword()
    {
        useTableCatalog();
        // Caret is in the whitespace right after WHERE inside an EXISTS subquery — the next
        // visible token is ')' which closes the EXISTS. Before the fix, suggestColumns was
        // called with ')' as the anchor; ')' is a sibling of SubqueryContext (not inside it),
        // so collectTableSourceAliases only found outer aliases (a). The fix uses effectiveNode
        // (the WHERE keyword, inside the inner subquery) so both inner (b) and outer (a) aliases
        // are found.
        //@formatter:off
        String query = "select *\n"
                + "from dbo.tableA a\n"
                + "where exists\n"
                + "(\n"
                + "    select 1\n"
                + "    from dbo.tableB b\n"
                + "    where \n"
                + ")\n";
        //@formatter:on
        int caretOffset = query.indexOf("    where \n") + "    where ".length();
        CompletionResult result = complete(query, caretOffset);

        assertNotNull(result, "Expected column completions inside EXISTS subquery WHERE");
        List<String> colsB = aliasedColumns(result, "b");
        List<String> colsA = aliasedColumns(result, "a");
        assertTrue(colsB.contains("b.id"), "Expected inner alias b.id inside EXISTS WHERE, got: " + colsB + ", all: " + replacements(result));
        assertTrue(colsA.contains("a.col1"), "Expected outer alias a.col1 visible inside EXISTS WHERE");
    }

    @Test
    void test_columnSuggestions_notExistsSubquery_whereKeyword()
    {
        useTableCatalog();
        // Same scenario as EXISTS but with NOT EXISTS — inner and outer aliases must both be visible.
        //@formatter:off
        String query = "select *\n"
                + "from dbo.tableA a\n"
                + "where not exists\n"
                + "(\n"
                + "    select 1\n"
                + "    from dbo.tableB b\n"
                + "    where \n"
                + ")\n";
        //@formatter:on
        int caretOffset = query.indexOf("    where \n") + "    where ".length();
        CompletionResult result = complete(query, caretOffset);

        assertNotNull(result, "Expected column completions inside NOT EXISTS subquery WHERE");
        List<String> colsB = aliasedColumns(result, "b");
        assertTrue(colsB.contains("b.id"), "Expected inner alias b.id inside NOT EXISTS WHERE, got: " + colsB + ", all: " + replacements(result));
    }

    @Test
    void test_columnSuggestions_existsSubquery_partialExpression()
    {
        useTableCatalog();
        // Caret is at the end of a partial expression inside the EXISTS subquery WHERE clause —
        // both inner and outer aliases must be visible.
        //@formatter:off
        String query = "select *\n"
                + "from dbo.tableA a\n"
                + "where exists\n"
                + "(\n"
                + "    select 1\n"
                + "    from dbo.tableB b\n"
                + "    where b.\n"
                + ")\n";
        //@formatter:on
        int caretOffset = query.indexOf("    where b.") + "    where b.".length();
        CompletionResult result = complete(query, caretOffset);

        assertNotNull(result, "Expected column completions after 'b.' inside EXISTS subquery");
        List<String> colsB = aliasedColumns(result, "b");
        assertTrue(colsB.contains("b.id"), "Expected b.id after dot inside EXISTS, got: " + colsB + ", all: " + replacements(result));
    }

    @Test
    void test_columnSuggestions_emptySelectList_withFromClause()
    {
        useTableCatalog();
        // Caret is in the SELECT list before any column has been typed, but the FROM clause
        // is already present. Columns from the aliased table should be suggested.
        // This tests that the C3/fallback path works when the select-list is empty (ANTLR
        // error-recovers the missing expression) but table_sources are intact.
        //@formatter:off
        String query = "select \nfrom dbo.tableA t";
        //@formatter:on
        int caretOffset = "select ".length(); // right after "select "
        CompletionResult result = complete(query, caretOffset);

        assertNotNull(result, "Expected column completions in empty SELECT list when FROM is present");
        List<String> cols = aliasedColumns(result, "t");
        assertTrue(cols.contains("t.col1"), "Expected t.col1 in SELECT-list suggestions, got: " + cols + ", all: " + replacements(result));
        assertTrue(cols.contains("t.col2"), "Expected t.col2 in SELECT-list suggestions");
    }

    @Test
    void test_tableSuggestions_existsSubquery_fromClause()
    {
        useTableCatalog();
        // Caret is immediately after FROM inside an EXISTS subquery. The outer query has no
        // FROM clause (select 1). Table-source suggestions must come from the inner scope.
        //@formatter:off
        String query = "select 1\n"
                + "where exists\n"
                + "(\n"
                + "  select 1\n"
                + "  from \n"
                + ")";
        //@formatter:on
        int caretOffset = query.indexOf("  from \n") + "  from ".length();
        CompletionResult result = complete(query, caretOffset);

        assertNotNull(result, "Expected table completions inside EXISTS subquery FROM");
        List<String> items = replacements(result);
        assertTrue(items.contains("dbo.tableA"), "Expected dbo.tableA in EXISTS FROM suggestions, got: " + items);
        assertTrue(items.contains("dbo.tableB"), "Expected dbo.tableB in EXISTS FROM suggestions");
    }

    @Test
    void test_columnSuggestions_existsSubquery_partialExpression_noOuterFrom()
    {
        useTableCatalog();
        // Caret is at end of a partial column expression inside an EXISTS subquery WHERE clause.
        // The outer query has no FROM clause (select 1). Only the inner alias must be in scope.
        //@formatter:off
        String query = "select 1\n"
                + "where exists\n"
                + "(\n"
                + "  select 1\n"
                + "  from dbo.tableA a\n"
                + "  where a.col1\n"
                + ")";
        //@formatter:on
        int caretOffset = query.indexOf("  where a.col1\n") + "  where a.col1".length();
        CompletionResult result = complete(query, caretOffset);

        assertNotNull(result, "Expected column completions inside EXISTS subquery with partial expression");
        List<String> cols = aliasedColumns(result, "a");
        assertTrue(cols.contains("a.col1"), "Expected a.col1 in EXISTS-subquery WHERE, got: " + cols + ", all: " + replacements(result));
        assertTrue(cols.contains("a.col2"), "Expected a.col2 in EXISTS-subquery WHERE");
    }

    // -----------------------------------------------------------------
    // Table-source suggestion tests
    // -----------------------------------------------------------------

    @Test
    void test_tableSuggestions_fromClause()
    {
        useTableCatalog();
        // Caret immediately after FROM — C3 should return RULE_table_source_item.
        String query = "SELECT * FROM ";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result);
        List<String> items = replacements(result);
        assertTrue(items.contains("dbo.tableA"), "Expected dbo.tableA in FROM suggestions");
        assertTrue(items.contains("dbo.tableB"), "Expected dbo.tableB in FROM suggestions");
    }

    @Test
    void test_tableSuggestions_joinClause()
    {
        useTableCatalog();
        // Caret after JOIN keyword — same C3 path.
        String query = "SELECT * FROM dbo.tableA a INNER JOIN ";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result);
        List<String> items = replacements(result);
        assertTrue(items.contains("dbo.tableA"), "Expected dbo.tableA in JOIN suggestions");
        assertTrue(items.contains("dbo.tableB"), "Expected dbo.tableB in JOIN suggestions");
    }

    @Test
    void test_tableSuggestions_dotTrigger_fromClause()
    {
        useTableCatalog();
        // Caret right after "dbo." — isTableSourceContext should detect the dot inside the
        // table_source_item rule and return table completions.
        String query = "SELECT * FROM dbo.";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result, "Expected table completions after 'FROM dbo.'");
        List<String> items = replacements(result);
        assertTrue(items.contains("dbo.tableA"), "Expected dbo.tableA, got: " + items);
        assertTrue(items.contains("dbo.tableB"), "Expected dbo.tableB, got: " + items);
    }

    @Test
    void test_tableSuggestions_dotTrigger_fromClause_withFollowingWhere()
    {
        useTableCatalog();
        // Dot followed by a keyword — ANTLR error recovery must not detach the table_source_item.
        String query = "SELECT * FROM dbo. WHERE col1 = 1";
        int caretOffset = query.indexOf("dbo.") + 4;
        CompletionResult result = complete(query, caretOffset);

        assertNotNull(result, "Expected table completions after 'FROM dbo.' followed by WHERE");
        List<String> items = replacements(result);
        assertTrue(items.contains("dbo.tableA"), "Expected dbo.tableA, got: " + items);
        assertTrue(items.contains("dbo.tableB"), "Expected dbo.tableB, got: " + items);
    }

    @Test
    void test_tableSuggestions_dotTrigger_joinClause()
    {
        useTableCatalog();
        // Caret after "dbo." in a JOIN — ANTLR error recovery can detach the DOT from
        // Table_source_itemContext in a multi-table query; the token-stream fallback must still
        // return table completions.
        String query = "select *\nfrom dbo.tableA a\ninner join dbo.";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result, "Expected table completions after 'INNER JOIN dbo.'");
        List<String> items = replacements(result);
        assertTrue(items.contains("dbo.tableA"), "Expected dbo.tableA, got: " + items);
        assertTrue(items.contains("dbo.tableB"), "Expected dbo.tableB, got: " + items);
    }

    // -----------------------------------------------------------------
    // Validation tests (afterParse semantic validation)
    // -----------------------------------------------------------------

    @Test
    void test_validation_unknownTableInCatalog()
    {
        // TEST_CATALOG has no table sources — any real table reference must produce an ERROR.
        // (The default mock already returns TEST_CATALOG which has an empty table list.)
        documentParser.parse(new StringReader("SELECT * FROM dbo.noSuchTable"));

        List<ParseItem> errors = parseErrors();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0)
                .getMessage()
                .contains("noSuchTable"), "Error message should name the unknown table");
    }

    @Test
    void test_validation_knownTableInCatalog_noError()
    {
        useTableCatalog();
        documentParser.parse(new StringReader("SELECT * FROM dbo.tableA"));

        List<ParseItem> errors = parseErrors().stream()
                .filter(e -> e.getMessage()
                        .contains("tableA"))
                .collect(Collectors.toList());
        assertTrue(errors.isEmpty(), "No error expected for a table that exists in the catalog, but got: " + errors);
    }

    @Test
    void test_validation_noCatalog_noFalsePositive()
    {
        // When the catalog hasn't loaded yet (getCatalog returns null), the validator must
        // NOT raise false-positive "missing table" errors.
        when(crawlService.getCatalog(any(), anyString())).thenReturn(null);
        documentParser.parse(new StringReader("SELECT * FROM dbo.anyTable"));

        List<ParseItem> errors = parseErrors().stream()
                .filter(e -> e.getMessage()
                        .contains("anyTable"))
                .collect(Collectors.toList());
        assertTrue(errors.isEmpty(), "No error expected when catalog is not yet loaded");
    }

    @Test
    void test_validation_sameExpressionBothSides_warning()
    {
        useTableCatalog();
        // col1 = col1 is the same expression on both sides — must produce a WARN.
        documentParser.parse(new StringReader("SELECT * FROM dbo.tableA WHERE col1 = col1"));

        List<ParseItem> warnings = parseWarnings();
        assertNotNull(warnings);
        assertTrue(warnings.stream()
                .anyMatch(w -> w.getMessage()
                        .contains("Same expression")),
                "Expected 'Same expression' warning for col1 = col1");
    }

    @Test
    void test_validation_differentExpressions_noWarning()
    {
        useTableCatalog();
        documentParser.parse(new StringReader("SELECT * FROM dbo.tableA WHERE col1 = col2"));

        List<ParseItem> warnings = parseWarnings().stream()
                .filter(w -> w.getMessage()
                        .contains("Same expression"))
                .collect(Collectors.toList());
        assertTrue(warnings.isEmpty(), "No 'Same expression' warning expected when expressions differ");
    }

    // -----------------------------------------------------------------
    // findNearestPrecedingStatementCtx tests
    // -----------------------------------------------------------------

    @Test
    void test_findNearestPrecedingStatementCtx_emptyDocument()
    {
        // No statements are collected from an empty document → must return null.
        documentParser.parse(new StringReader(""));
        assertNull(documentParser.findNearestPrecedingStatementCtx(0));
    }

    @Test
    void test_findNearestPrecedingStatementCtx_singleStatement_caretInside()
    {
        String query = "SELECT col1 FROM dbo.tableA";
        documentParser.parse(new StringReader(query));

        // statementContexts should contain the one statement node.
        assertFalse(documentParser.statementContexts.isEmpty(), "Expected at least one statement context after parse");

        int caretOffset = query.indexOf("FROM"); // somewhere in the middle
        ParserRuleContext ctx = documentParser.findNearestPrecedingStatementCtx(caretOffset);

        assertNotNull(ctx, "Expected a context for a caret inside the only statement");
        assertEquals(0, ctx.start.getStartIndex(), "Single statement must start at char offset 0");
    }

    @Test
    void test_findNearestPrecedingStatementCtx_caretBeforeFirstStatement()
    {
        String query = "SELECT col1 FROM dbo.tableA"; // statement starts at offset 0
        documentParser.parse(new StringReader(query));

        // caretCharOffset = -1: condition is start(0) <= -1 → false for every statement → null
        assertNull(documentParser.findNearestPrecedingStatementCtx(-1), "Caret before the first statement should return null");
    }

    @Test
    void test_findNearestPrecedingStatementCtx_twoStatements_caretInSecond()
    {
        String query = "SELECT 1; SELECT col1 FROM dbo.tableA";
        documentParser.parse(new StringReader(query));

        int secondSelectOffset = query.indexOf("SELECT col1");
        int caretOffset = query.length() - 1; // inside the second statement

        ParserRuleContext ctx = documentParser.findNearestPrecedingStatementCtx(caretOffset);

        assertNotNull(ctx, "Expected a context for caret inside the second statement");
        // The nearest preceding statement must be the second one (highest start that is <= caret).
        assertTrue(ctx.start.getStartIndex() >= secondSelectOffset,
                "Expected context to point at or after the second SELECT (offset " + secondSelectOffset + "), but starts at: " + ctx.start.getStartIndex());
    }

    @Test
    void test_findNearestPrecedingStatementCtx_twoStatements_caretInFirst()
    {
        String query = "SELECT col1 FROM dbo.tableA; SELECT 2";
        documentParser.parse(new StringReader(query));

        // Caret inside the first statement (before the SEMI)
        int caretOffset = query.indexOf("FROM"); // well before the SEMI

        ParserRuleContext ctx = documentParser.findNearestPrecedingStatementCtx(caretOffset);

        assertNotNull(ctx);
        // Must be the FIRST statement (start offset 0), not the second
        assertEquals(0, ctx.start.getStartIndex(), "Caret in first statement must find the first statement context (starts at 0)");
    }

    // -----------------------------------------------------------------
    // findStatementContextByTokenScan tests
    // -----------------------------------------------------------------

    @Test
    void test_findStatementContextByTokenScan_semiSeparator()
    {
        // Two statements separated by SEMI. The scan from inside the second statement
        // must produce a synthetic context whose start token falls after the SEMI.
        String query = "SELECT 1; SELECT col1 FROM dbo.tableA";
        documentParser.parse(new StringReader(query));

        // Obtain the suggest-token index for a position inside the second statement.
        TokenOffset to = AntlrDocumentParser.findTokenFromOffset(documentParser.parser, documentParser.context, query.length() - 1);
        assertNotNull(to);

        ParserRuleContext ctx = documentParser.findStatementContextByTokenScan(to.suggestTokenIndex());

        assertNotNull(ctx, "Expected a synthetic context from token scan");
        int semiCharOffset = query.indexOf(';');
        assertTrue(ctx.start.getStartIndex() > semiCharOffset, "Synthetic context must start after the SEMI (char " + semiCharOffset + "), but starts at: " + ctx.start.getStartIndex());
    }

    @Test
    void test_findStatementContextByTokenScan_keywordBoundary()
    {
        // No SEMI — backward scan stops at the SELECT keyword of the second statement.
        String query = "SELECT 1\nSELECT col1 FROM dbo.tableA";
        documentParser.parse(new StringReader(query));

        TokenOffset to = AntlrDocumentParser.findTokenFromOffset(documentParser.parser, documentParser.context, query.length() - 1);
        assertNotNull(to);

        ParserRuleContext ctx = documentParser.findStatementContextByTokenScan(to.suggestTokenIndex());

        assertNotNull(ctx, "Expected a synthetic context from keyword scan");
        int secondSelectOffset = query.indexOf("SELECT col1");
        assertEquals(secondSelectOffset, ctx.start.getStartIndex(), "Context must start at the second SELECT keyword (char offset " + secondSelectOffset + ")");
    }

    @Test
    void test_findStatementContextByTokenScan_singleStatement_startsAtZero()
    {
        // Single statement with no SEMI. The scan reaches token index 0 without finding
        // a SEMI; it finds SELECT at index 0 (statementStartIdx stays 0 from the loop).
        String query = "SELECT col1 FROM dbo.tableA";
        documentParser.parse(new StringReader(query));

        TokenOffset to = AntlrDocumentParser.findTokenFromOffset(documentParser.parser, documentParser.context, query.length() - 1);
        assertNotNull(to);

        ParserRuleContext ctx = documentParser.findStatementContextByTokenScan(to.suggestTokenIndex());

        assertNotNull(ctx);
        assertEquals(0, ctx.start.getStartIndex(), "Single-statement scan must yield a context starting at char 0");
    }

    // -----------------------------------------------------------------
    // miniParseStatementNode tests
    // -----------------------------------------------------------------

    @Test
    void test_miniParseStatementNode_nullForNullStatementCtx()
    {
        documentParser.parse(new StringReader("SELECT 1"));
        assertNull(documentParser.miniParseStatementNode(null, 0));
    }

    @Test
    void test_miniParseStatementNode_nullForStatementCtxWithNoStartToken()
    {
        documentParser.parse(new StringReader("SELECT 1"));
        // new ParserRuleContext() has start == null, which is the third null-guard.
        assertNull(documentParser.miniParseStatementNode(new ParserRuleContext(), 5));
    }

    @Test
    void test_miniParseStatementNode_returnsNodeNearCaret()
    {
        String query = "SELECT col1, col2 FROM dbo.tableA t WHERE t.col1 = 1";
        documentParser.parse(new StringReader(query));

        assertFalse(documentParser.statementContexts.isEmpty(), "Expected statement contexts after parse");
        ParserRuleContext stmt = documentParser.statementContexts.get(0);

        int caretOffset = query.indexOf("t.col1"); // at 't' in the WHERE clause
        ParseTree node = documentParser.miniParseStatementNode(stmt, caretOffset);

        assertNotNull(node, "mini-parse must return a tree node near the caret");
        assertTrue(node instanceof TerminalNode
                || node instanceof ParserRuleContext, "Returned node must be a valid parse-tree element");
    }

    @Test
    void test_miniParseStatementNode_isolatesSecondStatement()
    {
        useTableCatalog();
        // Mini-parsing the SECOND statement context must find only that statement's aliases,
        // not those from the first statement.
        String query = "SELECT * FROM dbo.tableB b WHERE 1=1; SELECT * FROM dbo.tableA t WHERE t.col1 = 1";
        documentParser.parse(new StringReader(query));

        assertTrue(documentParser.statementContexts.size() >= 2, "Expected at least 2 statement contexts");

        // Pick the statement with the highest start offset (the second one).
        ParserRuleContext secondStmt = documentParser.statementContexts.stream()
                .max(Comparator.comparingInt(c -> c.start.getStartIndex()))
                .orElseThrow();

        ParseTree miniNode = documentParser.miniParseStatementNode(secondStmt, query.length() - 1);
        assertNotNull(miniNode);
    }

    // ----- Operator-preceded completion (= < > AND OR etc.) -----

    @Test
    void test_columnSuggestions_afterEqualSign_noSpace()
    {
        useTableCatalog();
        // Caret directly after '=' — "WHERE LEFT(campaignId, 10) ="
        String query = "select * from dbo.tableA t where LEFT(t.col1, 10) =";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result, "Expected column completions directly after '='");
        List<String> cols = aliasedColumns(result, "t");
        assertTrue(cols.contains("t.col1"), "Expected t.col1 after '='");
        assertTrue(cols.contains("t.col2"), "Expected t.col2 after '='");
    }

    @Test
    void test_columnSuggestions_afterEqualSign_withSpace()
    {
        useTableCatalog();
        // Caret after '= ' (space between '=' and caret)
        String query = "select * from dbo.tableA t where LEFT(t.col1, 10) = ";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result, "Expected column completions after '= '");
        List<String> cols = aliasedColumns(result, "t");
        assertTrue(cols.contains("t.col1"), "Expected t.col1 after '= '");
        assertTrue(cols.contains("t.col2"), "Expected t.col2 after '= '");
    }

    @Test
    void test_columnSuggestions_afterLessThan()
    {
        useTableCatalog();
        // Caret after '<'
        String query = "select * from dbo.tableA t where t.col1 < ";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result, "Expected column completions after '<'");
        List<String> cols = aliasedColumns(result, "t");
        assertTrue(cols.contains("t.col1"), "Expected t.col1 after '<'");
    }

    @Test
    void test_columnSuggestions_afterGreaterThan()
    {
        useTableCatalog();
        // Caret after '>'
        String query = "select * from dbo.tableA t where t.col1 > ";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result, "Expected column completions after '>'");
        List<String> cols = aliasedColumns(result, "t");
        assertTrue(cols.contains("t.col1"), "Expected t.col1 after '>'");
    }

    @Test
    void test_columnSuggestions_afterAnd()
    {
        useTableCatalog();
        // Caret after 'AND' — query ends with no right-hand expression
        String query = "select * from dbo.tableA t where t.col1 = 1 AND ";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result, "Expected column completions after 'AND'");
        List<String> cols = aliasedColumns(result, "t");
        assertTrue(cols.contains("t.col1"), "Expected t.col1 after 'AND'");
    }

    @Test
    void test_columnSuggestions_afterOr()
    {
        useTableCatalog();
        // Caret after 'OR'
        String query = "select * from dbo.tableA t where t.col1 = 1 OR ";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result, "Expected column completions after 'OR'");
        List<String> cols = aliasedColumns(result, "t");
        assertTrue(cols.contains("t.col1"), "Expected t.col1 after 'OR'");
    }

    @Test
    void test_columnSuggestions_afterPlus()
    {
        useTableCatalog();
        // Caret after '+' in a WHERE expression
        String query = "select * from dbo.tableA t where t.col1 + ";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result, "Expected column completions after '+'");
        List<String> cols = aliasedColumns(result, "t");
        assertTrue(cols.contains("t.col1"), "Expected t.col1 after '+'");
    }
}
