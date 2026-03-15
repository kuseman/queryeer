package se.kuseman.payloadbuilder.catalog.jdbc.dialect;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.queryeer.api.editor.ITextEditorDocumentParser.CompletionItem;
import com.queryeer.api.editor.ITextEditorDocumentParser.CompletionResult;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.ITemplateService;

import se.kuseman.payloadbuilder.catalog.jdbc.CatalogCrawlService;
import se.kuseman.payloadbuilder.catalog.jdbc.IConnectionState;
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
class SqlServerDocumentParserTest
{
    private CatalogCrawlService crawlService;
    private IConnectionState connectionState;
    private SqlServerDocumentParser sqlServerDocumentParser;

    /** Catalog with dbo.MyProc(@param1 varchar, @param2 int) */
    private static final Catalog TEST_CATALOG = new Catalog("testdb", emptyList(),
            List.of(new Routine("", "dbo", "MyProc", Routine.Type.PROCEDURE,
                    List.of(new RoutineParameter("@param1", "varchar", 50, 0, 0, true, false), new RoutineParameter("@param2", "int", 0, 10, 0, false, false)))),
            emptyList(), emptyList(), emptyList());

    @BeforeEach
    void setUp()
    {
        crawlService = mock(CatalogCrawlService.class);
        connectionState = mock(IConnectionState.class);
        when(connectionState.getDatabase()).thenReturn("testdb");
        when(crawlService.getCatalog(any(), anyString())).thenReturn(TEST_CATALOG);

        sqlServerDocumentParser = new SqlServerDocumentParser(mock(Icons.class), mock(IEventBus.class), mock(QueryActionsConfigurable.class), crawlService, connectionState,
                mock(ITemplateService.class));
    }

    private CompletionResult complete(String query, int caretOffset)
    {
        sqlServerDocumentParser.parse(new StringReader(query));
        return sqlServerDocumentParser.getCompletionItems(caretOffset);
    }

    private List<String> replacements(CompletionResult result)
    {
        return result.getItems()
                .stream()
                .map(CompletionItem::getReplacementText)
                .collect(Collectors.toList());
    }

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
