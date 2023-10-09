package se.kuseman.payloadbuilder.catalog.jdbc.dialect;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.junit.Assert;
import org.junit.Test;

import se.kuseman.payloadbuilder.catalog.jdbc.dialect.AntlrDocumentParser.TableAlias;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.AntlrDocumentParser.TableAliasType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.AntlrDocumentParser.TokenOffset;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.SqlServerDocumentParser.TableSourceAliasCollector;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ObjectName;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlLexer;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Tsql_fileContext;

/** Test of {@link SqlServerDocumentParser} */
public class SqlServerDocumentParserTest extends Assert
{
    @Test
    public void test_TableSourceAliasCollector() throws FileNotFoundException, IOException
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
