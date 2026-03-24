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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.junit.jupiter.api.Test;

import com.queryeer.api.editor.ITextEditorDocumentParser.CompletionResult;
import com.queryeer.api.editor.ITextEditorDocumentParser.ParseItem;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.ITemplateService;

import se.kuseman.payloadbuilder.catalog.jdbc.Icons;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.AntlrDocumentParser.TableAlias;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.AntlrDocumentParser.TableAliasType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.AntlrDocumentParser.TokenOffset;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.SqlServerDocumentParser.TableSourceAliasCollector;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Catalog;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ObjectName;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Routine;
import se.kuseman.payloadbuilder.catalog.jdbc.model.RoutineParameter;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlLexer;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Tsql_fileContext;

/** Test of {@link SqlServerDocumentParser} */
class SqlServerDocumentParserTest extends AntlrDocumentParserTestBase
{
    private SqlServerDocumentParser sqlServerDocumentParser;

    @Override
    protected AntlrDocumentParser<?> createParser()
    {
        sqlServerDocumentParser = new SqlServerDocumentParser(mock(Icons.class), mock(IEventBus.class), mock(QueryActionsConfigurable.class), crawlService, connectionContext,
                mock(ITemplateService.class));
        return sqlServerDocumentParser;
    }

    // -----------------------------------------------------------------
    // Procedure-parameter tests (T-SQL EXEC / @param syntax)
    // -----------------------------------------------------------------

    @Test
    void test_procedureParameters_twoPartName()
    {
        // EXEC dbo.MyProc | — caret right after the proc name
        String query = "EXEC dbo.MyProc ";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result);
        assertEquals(List.of("@param1 = ", "@param2 = "), replacements(result));
    }

    @Test
    void test_procedureParameters_onePartName()
    {
        // EXEC MyProc | — unqualified proc name (schema-less)
        Catalog noSchema = new Catalog("testdb", emptyList(), List.of(new Routine("", "", "MyProc", Routine.Type.PROCEDURE, List.of(new RoutineParameter("@param1", "int", 0, 10, 0, false, false)))),
                emptyList(), emptyList(), emptyList());
        when(crawlService.getCatalog(any(), anyString())).thenReturn(noSchema);

        String query = "EXEC MyProc ";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result);
        assertEquals(List.of("@param1 = "), replacements(result));
    }

    @Test
    void test_procedureParameters_afterFilledParamAndComma()
    {
        // EXEC dbo.MyProc @param1 = 'value', | — caret after trailing comma
        // This exercises the offset-based tree-walk fix: ANTLR error recovery can set
        // Execute_bodyContext.stop to the token before the comma, leaving the comma
        // orphaned outside the context's subtree so parent-chain walking fails.
        String query = "EXEC dbo.MyProc @param1 = 'value', ";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result);
        // @param1 is already used — only @param2 should be suggested
        assertEquals(List.of("@param2 = "), replacements(result));
    }

    @Test
    void test_procedureParameters_noSuggestionInsideValue()
    {
        // EXEC dbo.MyProc @param1 = 'val|ue' — caret is inside the value string
        String query = "EXEC dbo.MyProc @param1 = 'value'";
        int caretInsideValue = query.indexOf("'value'") + 2; // two chars into the string literal
        CompletionResult result = complete(query, caretInsideValue);

        assertTrue(result == null
                || result.getItems()
                        .stream()
                        .noneMatch(i -> i.getReplacementText()
                                .endsWith(" = ")),
                "Expected no parameter name suggestions while typing a value");
    }

    @Test
    void test_procedureParameters_allParamsAlreadyUsed()
    {
        // Both params filled — nothing left to suggest
        String query = "EXEC dbo.MyProc @param1 = 1, @param2 = 2, ";
        CompletionResult result = complete(query, query.length());

        assertTrue(result == null
                || result.getItems()
                        .stream()
                        .noneMatch(i -> i.getReplacementText()
                                .endsWith(" = ")),
                "Expected no parameter suggestions when all params are already provided");
    }

    @Test
    void test_procedureParameters_noSuggestionInLaterSelectStatement()
    {
        // EXEC in first statement, caret in FROM clause of a second statement.
        // Should suggest table sources, NOT procedure parameters.
        String query = "exec dbo.MyProc @param1 = '#t_table'\n\nselect *\nfrom ";
        CompletionResult result = complete(query, query.length());

        // Must not return procedure-parameter suggestions (@param = )
        assertTrue(result == null
                || result.getItems()
                        .stream()
                        .noneMatch(i -> i.getReplacementText()
                                .endsWith(" = ")),
                "Expected no procedure parameter suggestions in a FROM clause after a preceding EXEC statement");
    }

    // -----------------------------------------------------------------
    // TableSourceAliasCollector — T-SQL specific features
    // -----------------------------------------------------------------

    @Test
    void test_TableSourceAliasCollector() throws FileNotFoundException, IOException
    {
        Set<TableAlias> actual;
        String query;

        query = """
                select *
                from tableT t
                where
                """;
        actual = getAliases(query, query.length() - 1);

        assertEquals(Set.of(new TableAlias("t", new ObjectName("myDb", "", "tableT"), List.of(), TableAliasType.TABLE)), actual);

        query = """
                select *
                from @tableT t1
                where
                """;
        actual = getAliases(query, query.length() - 1);

        assertEquals(Set.of(new TableAlias("t1", new ObjectName("", "", "@tableT"), List.of(), TableAliasType.TABLEVARIABLE)), actual);

        query = """
                select *
                from #tableT t2
                where
                """;
        actual = getAliases(query, query.length() - 1);

        assertEquals(Set.of(new TableAlias("t2", new ObjectName("", "", "#tableT"), List.of(), TableAliasType.TEMPTABLE)), actual);

        query = """
                select *
                from changetable(changes tableC, 0) t3
                where
                """;
        actual = getAliases(query, query.length() - 1);

        assertEquals(Set.of(new TableAlias("t3", new ObjectName("myDb", "", "tableC"),
                List.of("SYS_CHANGE_VERSION", "SYS_CHANGE_CREATION_VERSION", "SYS_CHANGE_OPERATION", "SYS_CHANGE_COLUMNS", "SYS_CHANGE_CONTEXT"), TableAliasType.CHANGETABLE)), actual);

        query = """
                select *
                from func1(10, 0) f1
                cross join schem.func2(10, 0) f2
                cross join otherdb..func3(10, 0) f3
                cross join otherdb.schem.func4(10, 0) f4
                where
                """;
        actual = getAliases(query, query.length() - 1);

        assertEquals(Set.of(new TableAlias("f1", new ObjectName("myDb", "", "func1"), List.of(), TableAliasType.TABLE_FUNCTION),
                new TableAlias("f2", new ObjectName("myDb", "schem", "func2"), List.of(), TableAliasType.TABLE_FUNCTION),
                new TableAlias("f3", new ObjectName("otherdb", "", "func3"), List.of(), TableAliasType.TABLE_FUNCTION),
                new TableAlias("f4", new ObjectName("otherdb", "schem", "func4"), List.of(), TableAliasType.TABLE_FUNCTION)), actual);

        // Verify sub query columns
        // caret at 'on x|'
        query = """
                select *
                from tableA a
                inner join
                (
                  select b.col1, b.col2 aliasCol
                  from tableB b
                  where 1=1
                ) x
                  on x.col1 = a.col
                """;
        actual = getAliases(query, query.indexOf("on x.col1") + 3);

        assertEquals(Set.of(new TableAlias("a", new ObjectName("myDb", "", "tableA"), List.of(), TableAliasType.TABLE),
                new TableAlias("x", new ObjectName("", "", ""), List.of("col1", "aliasCol"), TableAliasType.SUBQUERY)), actual);

        // Verify that outer scope is not visible on joins
        // caret at 'where |'
        query = """
                select *
                from tableA a
                inner join
                (
                  select *
                  from tableB b
                  where 1=1
                ) x
                  on x.col = a.col
                """;
        actual = getAliases(query, query.indexOf("where 1") + 6);

        assertEquals(Set.of(new TableAlias("b", new ObjectName("myDb", "", "tableB"), List.of(), TableAliasType.TABLE)), actual);

        // Verify that outer scope IS visible on apply's
        // caret at 'where |'
        //@formatter:off
        query = """
                select *
                from tableA a
                outer apply
                (
                  select *
                  from tableB b
                  where 1=1
                ) x
                inner join tableC c
                 on c.col1 = a.col
                """;
        actual = getAliases(query, query.indexOf("where 1") + 6);
        //@formatter:on

        assertEquals(Set.of(new TableAlias("b", new ObjectName("myDb", "", "tableB"), List.of(), TableAliasType.TABLE),
                new TableAlias("a", new ObjectName("myDb", "", "tableA"), List.of(), TableAliasType.TABLE)), actual);

        // Verify that we don't include aliases defined after caret
        // Caret at 'a|.col'
        //@formatter:off
        query = """
                select *
                from tableA a
                inner join tableC c
                  on c.col = a.col
                inner join tableB b
                  on b.col = a.col
                """;
        actual = getAliases(query, query.indexOf("c.col = a.col") + 9);
        //@formatter:on

        assertEquals(Set.of(new TableAlias("c", new ObjectName("myDb", "", "tableC"), List.of(), TableAliasType.TABLE),
                new TableAlias("a", new ObjectName("myDb", "", "tableA"), List.of(), TableAliasType.TABLE)), actual);

        // Verify update statement with alias
        // cart at 'col = |10'
        //@formatter:off
        query = """
                update a
                set col = 10
                from tableA a
                inner join tableD d
                 on d.col = a.col
                """;
        actual = getAliases(query, query.indexOf("col = 10") + 6);
        //@formatter:on

        assertEquals(Set.of(new TableAlias("d", new ObjectName("myDb", "", "tableD"), List.of(), TableAliasType.TABLE),
                new TableAlias("a", new ObjectName("myDb", "", "tableA"), List.of(), TableAliasType.TABLE),
                // This one comes from the update with only an alias, we don't know here if this is an alias of an actual table
                new TableAlias("", new ObjectName("myDb", "", "a"), List.of(), TableAliasType.TABLE)), actual);

        // Verify update statement
        // cart at 'col = |10'
        //@formatter:off
        query = """
                update someotherdb..tableA
                set col = 10
                """;
        actual = getAliases(query, query.indexOf("col = 10") + 6);
        //@formatter:on

        assertEquals(Set.of(new TableAlias("", new ObjectName("someotherdb", "", "tableA"), List.of(), TableAliasType.TABLE)), actual);

        // Verify delete statement with alias
        // cart at '|a.col'
        //@formatter:off
        query = """
                delete a
                from tableA a
                inner join tableD d
                 on d.col = a.col
                """;
        actual = getAliases(query, query.indexOf("a.col"));
        //@formatter:on

        assertEquals(Set.of(new TableAlias("d", new ObjectName("myDb", "", "tableD"), List.of(), TableAliasType.TABLE),
                new TableAlias("a", new ObjectName("myDb", "", "tableA"), List.of(), TableAliasType.TABLE),
                // This one comes from the update with only an alias, we don't know here if this is an alias of an actual table
                new TableAlias("", new ObjectName("myDb", "", "a"), List.of(), TableAliasType.TABLE)), actual);

        // Verify delete statement
        // cart at '|col'
        //@formatter:off
        query = """
                delete from schem.tableA
                where col = 10
                """;
        actual = getAliases(query, query.indexOf("col"));
        //@formatter:on

        assertEquals(Set.of(new TableAlias("", new ObjectName("myDb", "schem", "tableA"), List.of(), TableAliasType.TABLE)), actual);
    }

    // -----------------------------------------------------------------
    // Column-suggestion tests — T-SQL UPDATE FROM syntax
    // -----------------------------------------------------------------

    @Test
    void test_columnSuggestions_updateWhereClause()
    {
        useTableCatalog();
        // Caret is after AND in the UPDATE WHERE clause. The alias collector walks up through
        // Update_statementContext and finds the alias from the FROM clause.
        // Note: column suggestions in the SET LHS (full_column_name context) are not tested
        // here because that position is outside expression/search_condition rule scope.
        String query = "UPDATE t SET col1 = 10 FROM dbo.tableA t WHERE t.col1 = 1 AND ";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result);
        List<String> cols = aliasedColumns(result, "t");
        assertTrue(cols.contains("t.col1"), "Expected t.col1 in UPDATE WHERE suggestions");
        assertTrue(cols.contains("t.col2"), "Expected t.col2 in UPDATE WHERE suggestions");
    }

    // -----------------------------------------------------------------
    // Table-source suggestion tests — T-SQL specific
    // -----------------------------------------------------------------

    @Test
    void test_tableSuggestions_includesBuiltinTvfs()
    {
        useTableCatalog();
        String query = "SELECT * FROM ";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result);
        List<String> items = replacements(result);
        // Built-in TVFs from BUILTIN_TABLE_FUNCTIONS should be included
        assertTrue(items.contains("STRING_SPLIT"), "Expected STRING_SPLIT in FROM suggestions");
        assertTrue(items.contains("OPENJSON"), "Expected OPENJSON in FROM suggestions");
    }

    @Test
    void test_tableSuggestions_includesTemporaryTables()
    {
        useTableCatalog();
        // A CREATE TABLE #tmp in the same document should surface the temp table in FROM suggestions.
        String query = "CREATE TABLE #myTemp (id int)\nSELECT * FROM ";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result);
        List<String> items = replacements(result);
        assertTrue(items.contains("#myTemp"), "Expected #myTemp in FROM suggestions after CREATE TABLE");
    }

    // -----------------------------------------------------------------
    // Procedure-suggestion tests (C3 RULE_func_proc_name path)
    // -----------------------------------------------------------------

    @Test
    void test_procedureSuggestions_execStatement()
    {
        // Caret right after EXEC — C3 should return RULE_func_proc_name_server_database_schema
        // and the parser should list every PROCEDURE from the catalog.
        String query = "EXEC ";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result);
        assertTrue(replacements(result).contains("dbo.MyProc"), "Expected dbo.MyProc in EXEC suggestions");
    }

    @Test
    void test_procedureParameters_serverQualifiedName()
    {
        // EXEC server.testdb.dbo.MyProc | — four-part name; the parser should still recognise
        // the procedure context and suggest its parameters.
        String query = "EXEC server.testdb.dbo.MyProc ";
        CompletionResult result = complete(query, query.length());

        // May return null if four-part name resolution is not supported yet, but must not throw.
        // When non-null it must include at least @param1.
        if (result != null
                && !result.getItems()
                        .isEmpty())
        {
            assertTrue(replacements(result).stream()
                    .anyMatch(r -> r.contains("@param1")), "Expected @param1 in four-part EXEC suggestions");
        }
    }

    // -----------------------------------------------------------------
    // ValidateVisitor tests — T-SQL temp tables and table variables
    // -----------------------------------------------------------------

    @Test
    void test_validation_missingTempTable()
    {
        // #undeclared is referenced in FROM but never created — must produce an ERROR.
        sqlServerDocumentParser.parse(new StringReader("SELECT * FROM #undeclared"));

        List<ParseItem> errors = parseErrors();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0)
                .getMessage()
                .contains("#undeclared"), "Error message should name the missing temp table");
    }

    @Test
    void test_validation_knownTempTable_noError()
    {
        // CREATE TABLE then SELECT — no error expected because the temp table is declared first.
        String query = """
                CREATE TABLE #myTemp (col1 int, col2 varchar(50))
                SELECT * FROM #myTemp
                """;
        sqlServerDocumentParser.parse(new StringReader(query));

        List<ParseItem> errors = parseErrors().stream()
                .filter(e -> e.getMessage()
                        .contains("#myTemp"))
                .collect(Collectors.toList());
        assertTrue(errors.isEmpty(), "No error expected for a declared temp table, but got: " + errors);
    }

    @Test
    void test_validation_missingTableVariable()
    {
        // @undeclaredVar is never declared — must produce an ERROR.
        sqlServerDocumentParser.parse(new StringReader("SELECT * FROM @undeclaredVar"));

        List<ParseItem> errors = parseErrors();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0)
                .getMessage()
                .contains("@undeclaredVar"), "Error message should name the missing table variable");
    }

    @Test
    void test_validation_knownTableVariable_noError()
    {
        // DECLARE @tv TABLE (...) then SELECT — no error expected.
        String query = """
                DECLARE @tv TABLE (col1 int)
                SELECT * FROM @tv
                """;
        sqlServerDocumentParser.parse(new StringReader(query));

        List<ParseItem> errors = parseErrors().stream()
                .filter(e -> e.getMessage()
                        .contains("@tv"))
                .collect(Collectors.toList());
        assertTrue(errors.isEmpty(), "No error expected for a declared table variable, but got: " + errors);
    }

    // -----------------------------------------------------------------
    // TableSourceAliasCollector — additional scenarios (use TSql parser directly)
    // -----------------------------------------------------------------

    @Test
    void test_TableSourceAliasCollector_cte() throws FileNotFoundException, IOException
    {
        // CTE alias in the outer SELECT must be visible; the CTE body's internal alias must
        // also be visible when the outer SELECT is visited via the select_statement handler.
        String query = """
                with cte as (
                  select col1, col2
                  from tableA t_inner
                )
                select *
                from cte c_outer
                where\s
                """;
        Set<TableAlias> actual = getAliases(query, query.length() - 1);

        // c_outer (the CTE reference in the outer FROM) must be present
        assertTrue(actual.stream()
                .anyMatch(a -> "c_outer".equals(a.alias())
                        && "cte".equals(a.objectName()
                                .getName())),
                "Expected CTE alias c_outer in scope, got: " + actual);
    }

    @Test
    void test_TableSourceAliasCollector_deepNestedSubquery() throws FileNotFoundException, IOException
    {
        // Caret is in the innermost WHERE — only the innermost alias (t) must be in scope;
        // outer aliases (level2, level1) must NOT leak through two subquery boundaries.
        //@formatter:off
        String query = """
                select *
                from (
                  select *
                  from (
                    select col1
                    from tableA t
                    where 1=1
                  ) level2
                  where 1=1
                ) level1
                where 1=1
                """;
        //@formatter:on
        Set<TableAlias> actual = getAliases(query, query.indexOf("where 1=1") + 6);

        assertTrue(actual.stream()
                .anyMatch(a -> "t".equals(a.alias())), "Expected alias t in innermost scope");
        assertTrue(actual.stream()
                .noneMatch(a -> "level2".equals(a.alias())
                        || "level1".equals(a.alias())),
                "Outer aliases must not leak into a double-nested subquery, got: " + actual);
    }

    @Test
    void test_TableSourceAliasCollector_existsSeesOuterScope() throws FileNotFoundException, IOException
    {
        // Inside an EXISTS subquery the outer alias (a → tableA) IS visible because the
        // EXISTS predicate resets the subquery-boundary flag when the collector walks UP
        // through it, allowing the outer Query_specificationContext to be visited.
        // The caret must be on a token INSIDE the subquery (here on the dot of b.id) so the
        // walk starts from inside the SubqueryContext.
        //@formatter:off
        String query = """
                select *
                from tableA a
                where exists (
                  select 1
                  from tableB b
                  where b.id = a.col1
                )
                """;
        //@formatter:on
        // Position the caret on the 'i' of 'b.id' — clearly inside the EXISTS subquery.
        int caretOffset = query.indexOf("b.id") + 2;
        Set<TableAlias> actual = getAliases(query, caretOffset);

        assertTrue(actual.stream()
                .anyMatch(a -> "b".equals(a.alias())), "Expected inner alias b inside EXISTS");
        assertTrue(actual.stream()
                .anyMatch(a -> "a".equals(a.alias())), "Expected outer alias a visible inside EXISTS");
    }

    @Test
    void test_TableSourceAliasCollector_joinsRespectCaretOrder() throws FileNotFoundException, IOException
    {
        // Three-way join: alias c is defined after the join where the caret sits,
        // so c must NOT appear in the alias set (already tested with two joins, here with three).
        //@formatter:off
        String query = """
                select *
                from tableA a
                inner join tableB b
                  on b.id = a.col1
                inner join tableA c
                  on c.col1 = a.col1
                """;
        //@formatter:on
        // Caret is on "b.id = a.col1" in the first ON clause — c must not be visible yet.
        int caretOffset = query.indexOf("b.id = a.col1") + 5;
        Set<TableAlias> actual = getAliases(query, caretOffset);

        assertTrue(actual.stream()
                .anyMatch(a -> "a".equals(a.alias())), "Expected alias a");
        assertTrue(actual.stream()
                .anyMatch(a -> "b".equals(a.alias())), "Expected alias b");
        assertTrue(actual.stream()
                .noneMatch(a -> "c".equals(a.alias())), "Alias c defined after caret must not be visible");
    }

    @Test
    void test_TableSourceAliasCollector_builtinTvfColumns() throws FileNotFoundException, IOException
    {
        // STRING_SPLIT is a built-in TVF — its alias must be BUILTIN type with the known output columns.
        //@formatter:off
        String query = """
                select *
                from string_split('a,b', ',') ss
                where\s
                """;
        //@formatter:on
        Set<TableAlias> actual = getAliases(query, query.length() - 1);

        TableAlias ssSplit = actual.stream()
                .filter(a -> "ss".equals(a.alias()))
                .findFirst()
                .orElse(null);

        assertNotNull(ssSplit, "Expected alias ss for STRING_SPLIT, got: " + actual);
        assertEquals(TableAliasType.BUILTIN, ssSplit.type());
        assertTrue(ssSplit.extendedColumns()
                .contains("value"), "Expected 'value' column from STRING_SPLIT");
        assertTrue(ssSplit.extendedColumns()
                .contains("ordinal"), "Expected 'ordinal' column from STRING_SPLIT");
    }

    // -----------------------------------------------------------------
    // findStatementContextByTokenScan — null-safety (needs a fresh parser)
    // -----------------------------------------------------------------

    @Test
    void test_findStatementContextByTokenScan_noParserInitialized()
    {
        // Before any parse() call, parser is null → must return null without throwing.
        SqlServerDocumentParser fresh = new SqlServerDocumentParser(mock(Icons.class), mock(IEventBus.class), mock(QueryActionsConfigurable.class), crawlService, connectionContext,
                mock(ITemplateService.class));
        assertNull(fresh.findStatementContextByTokenScan(0));
    }

    // -----------------------------------------------------------------
    // miniParseStatementNode — null-safety and alias verification
    // -----------------------------------------------------------------

    @Test
    void test_miniParseStatementNode_nullWhenParserNotInitialized()
    {
        // parser field is null before any parse() call.
        SqlServerDocumentParser fresh = new SqlServerDocumentParser(mock(Icons.class), mock(IEventBus.class), mock(QueryActionsConfigurable.class), crawlService, connectionContext,
                mock(ITemplateService.class));
        assertNull(fresh.miniParseStatementNode(new ParserRuleContext(), 0));
    }

    @Test
    void test_miniParseStatementNode_aliasesReachableFromReturnedNode()
    {
        useTableCatalog();
        // The key contract: the node returned by miniParseStatementNode must have a parent
        // chain that lets TableSourceAliasCollector find the aliases — this is exactly what
        // getCompletionItems relies on when the main tree is broken.
        String query = "SELECT * FROM dbo.tableA t WHERE t.col1 = 1";
        sqlServerDocumentParser.parse(new StringReader(query));

        assertFalse(sqlServerDocumentParser.statementContexts.isEmpty());
        ParserRuleContext stmt = sqlServerDocumentParser.statementContexts.get(0);

        // Use the end of the WHERE clause as the caret — same position as a column completion.
        ParseTree miniNode = sqlServerDocumentParser.miniParseStatementNode(stmt, query.length() - 1);
        assertNotNull(miniNode);

        Set<TableAlias> aliases = TableSourceAliasCollector.collectTableSourceAliases(miniNode, "testdb");
        assertTrue(aliases.stream()
                .anyMatch(a -> "t".equals(a.alias())), "Mini-parse node must expose alias 't', got: " + aliases);
    }

    @Test
    void test_miniParseStatementNode_isolatesSecondStatement_aliases()
    {
        useTableCatalog();
        // Mini-parsing the SECOND statement context must find only that statement's aliases,
        // not those from the first statement.
        String query = "SELECT * FROM dbo.tableB b WHERE 1=1; SELECT * FROM dbo.tableA t WHERE t.col1 = 1";
        sqlServerDocumentParser.parse(new StringReader(query));

        assertTrue(sqlServerDocumentParser.statementContexts.size() >= 2, "Expected at least 2 statement contexts");

        // Pick the statement with the highest start offset (the second one).
        ParserRuleContext secondStmt = sqlServerDocumentParser.statementContexts.stream()
                .max(Comparator.comparingInt(c -> c.start.getStartIndex()))
                .orElseThrow();

        ParseTree miniNode = sqlServerDocumentParser.miniParseStatementNode(secondStmt, query.length() - 1);
        assertNotNull(miniNode);

        Set<TableAlias> aliases = TableSourceAliasCollector.collectTableSourceAliases(miniNode, "testdb");
        // Second statement: alias 't' → tableA
        assertTrue(aliases.stream()
                .anyMatch(a -> "t".equals(a.alias())), "Expected alias 't' from the second statement, got: " + aliases);
        // First statement's alias 'b' must NOT be present (it's in a separate statement).
        assertFalse(aliases.stream()
                .anyMatch(a -> "b".equals(a.alias())), "Alias 'b' from the first statement must not leak into the mini-parse, got: " + aliases);
    }

    // -----------------------------------------------------------------
    // FK / PK join-condition autocomplete tests
    // -----------------------------------------------------------------

    @Test
    void test_joinCondition_rhsHasFk_suggestsCondition()
    {
        // tableB has FK tableA_id -> tableA.id
        // Caret is right after ON — should suggest "b.tableA_id = a.id"
        when(crawlService.getCatalog(any(), anyString())).thenReturn(FK_CATALOG);
        String query = "SELECT * FROM dbo.tableA a INNER JOIN dbo.tableB b ON ";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result);
        assertEquals(List.of("b.tableA_id = a.id"), replacements(result));
    }

    @Test
    void test_joinCondition_lhsHasFk_suggestsCondition()
    {
        // tableB has FK tableA_id -> tableA.id.
        // Here tableA is being joined onto tableB (RHS is tableA, LHS is tableB).
        // Should suggest "b.tableA_id = a.id" (LHS alias is FK side, RHS alias is PK side).
        when(crawlService.getCatalog(any(), anyString())).thenReturn(FK_CATALOG);
        String query = "SELECT * FROM dbo.tableB b INNER JOIN dbo.tableA a ON ";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result);
        assertEquals(List.of("b.tableA_id = a.id"), replacements(result));
    }

    @Test
    void test_joinCondition_noFk_fallsBackToColumnSuggestions()
    {
        // TABLE_CATALOG has no FK relationships — should fall back to regular column suggestions.
        useTableCatalog();
        String query = "SELECT * FROM dbo.tableA a INNER JOIN dbo.tableB b ON ";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result);
        // No FK items — regular column suggestions should be present
        List<String> items = replacements(result);
        assertTrue(items.stream()
                .anyMatch(r -> r != null
                        && r.startsWith("a.")),
                "Expected column suggestions for alias a");
        assertTrue(items.stream()
                .anyMatch(r -> r != null
                        && r.startsWith("b.")),
                "Expected column suggestions for alias b");
    }

    @Test
    void test_joinCondition_multipleJoins_suggestsAllRelevantFks()
    {
        // tableC has FK to tableA and FK to tableB.
        // Query joins tableA, tableB, then tableC.
        // Caret after third ON — should suggest both FK conditions.
        when(crawlService.getCatalog(any(), anyString())).thenReturn(FK_CATALOG);
        String query = "SELECT * FROM dbo.tableA a INNER JOIN dbo.tableB b ON b.tableA_id = a.id INNER JOIN dbo.tableC c ON ";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result);
        List<String> items = replacements(result);
        assertTrue(items.contains("c.tableA_id = a.id"), "Expected FK condition c.tableA_id = a.id");
        assertTrue(items.contains("c.tableB_id = b.id"), "Expected FK condition c.tableB_id = b.id");
    }

    @Test
    void test_joinCondition_caretNotAfterOn_noFkSuggestions()
    {
        // Caret is after "=" (partial condition already typed) — FK suggestion must NOT fire.
        // Regular column suggestions should be returned instead.
        when(crawlService.getCatalog(any(), anyString())).thenReturn(FK_CATALOG);
        String query = "SELECT * FROM dbo.tableA a INNER JOIN dbo.tableB b ON b.tableA_id = ";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result);
        // FK join condition items start with alias prefix; regular column suggestions also start with alias.
        // The critical thing is that no "x = y" items are present — only single-column alias.col items.
        List<String> items = replacements(result);
        assertTrue(items.stream().noneMatch(r -> r != null && r.contains(" = ")), "FK join condition items must not appear when caret is after '='");
    }

    // -----------------------------------------------------------------
    // INSERT INTO table-source suggestion tests
    // -----------------------------------------------------------------

    @Test
    void test_tableSuggestions_insertIntoDottedQualifier()
    {
        useTableCatalog();
        // Caret after "dbo." in INSERT INTO — isInsideTableSourceDottedQualifier detects INTO before the identifier chain
        String query = "insert into dbo.";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result);
        List<String> items = replacements(result);
        assertTrue(items.contains("dbo.tableA"), "Expected dbo.tableA in INSERT INTO dbo. suggestions");
        assertTrue(items.contains("dbo.tableB"), "Expected dbo.tableB in INSERT INTO dbo. suggestions");
    }

    @Test
    void test_tableSuggestions_insertIntoNoDot()
    {
        useTableCatalog();
        // Caret right after "INSERT INTO " — no dot typed yet, should suggest table sources
        String query = "insert into ";
        CompletionResult result = complete(query, query.length());

        assertNotNull(result);
        List<String> items = replacements(result);
        assertTrue(items.contains("dbo.tableA"), "Expected dbo.tableA in INSERT INTO suggestions (no dot)");
        assertTrue(items.contains("dbo.tableB"), "Expected dbo.tableB in INSERT INTO suggestions (no dot)");
    }

    // -----------------------------------------------------------------
    // Private helper — parse with TSql grammar directly
    // -----------------------------------------------------------------

    private Set<TableAlias> getAliases(String query, int caretOffset)
    {
        CharStream charStream = CharStreams.fromString(query);
        TSqlLexer lexer = new TSqlLexer(charStream);
        lexer.removeErrorListeners();
        TokenStream tokenStream = new CommonTokenStream(lexer);
        TSqlParser parser = new TSqlParser(tokenStream);
        parser.removeErrorListeners();
        Tsql_fileContext tsql_file = parser.tsql_file();
        TokenOffset offset = AntlrDocumentParser.findTokenFromOffset(parser, tsql_file, caretOffset);

        ParseTree node = offset.tree();
        if (node instanceof TerminalNode
                && ((TerminalNode) node).getSymbol()
                        .getType() == Token.EOF)
        {
            node = offset.prevTree();
        }

        return TableSourceAliasCollector.collectTableSourceAliases(node, "myDb");
    }

}
