package com.queryeer.completion;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.mockito.Mockito;

import com.queryeer.completion.PLBParser.Candidates;

import se.kuseman.payloadbuilder.core.catalog.CatalogRegistry;
import se.kuseman.payloadbuilder.core.execution.QuerySession;
import se.kuseman.payloadbuilder.core.parser.PayloadBuilderQueryParser;

/** Test of {@link PLBParser} */
public class PLBParserTest
{
    @Test
    public void test_regression_in_c3_where_suggests_would_hang_becuase_of_large_parse_tree() throws Exception
    {
        String query = IOUtils.toString(PLBParserTest.class.getResourceAsStream("/com/queryeer/completion/query_with_many_and_clauses.plbsql"), StandardCharsets.UTF_8);

        PLBParser parser = new PLBParser(new QuerySession(new CatalogRegistry()));
        Document doc = Mockito.mock(Document.class);
        Element elm = Mockito.mock(Element.class);
        when(doc.getDefaultRootElement()).thenReturn(elm);
        when(doc.getText(Mockito.anyInt(), Mockito.anyInt())).thenReturn(query);

        Candidates suggestions;

        // After "and " line 28
        suggestions = parser.getSuggestions(doc, 614);
        assertEquals("", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_primary), suggestions.collection().rules.keySet());

        // After "and log.logger" line 54
        suggestions = parser.getSuggestions(doc, 1057);
        assertEquals("log.logger", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_primary), suggestions.collection().rules.keySet());

        // After "._doc" line 25
        suggestions = parser.getSuggestions(doc, 597);
        assertEquals("._doc", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_tableSource), suggestions.collection().rules.keySet());

        // NOTE! Broken because we land on EOF and because of earlier parse errors we cannot match anything
        // After "and " before EOF line 71
        // suggestions = parser.getSuggestions(doc, 1400);
        // assertEquals("", suggestions.textToMatch());
        // assertEquals(false, suggestions.skipRules());
        // assertEquals(Set.of(PayloadBuilderQueryParser.RULE_primary), suggestions.collection().rules.keySet());
    }

    @Test
    public void test_tablesource() throws BadLocationException
    {
        // After table source with an alias => no suggestions here since we have a tablesource
        Candidates suggestions = suggestions("select * from tableA b", 22);
        assertEquals("b", suggestions.textToMatch());
        assertEquals(true, suggestions.skipRules());

        suggestions = suggestions("select * from table b ", 22);
        assertEquals("", suggestions.textToMatch());
        assertEquals(true, suggestions.skipRules());

        // After 'table'
        suggestions = suggestions("select * from tableA", 20);
        assertEquals("tableA", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_tableSource), suggestions.collection().rules.keySet());

        // After 'table '
        // here we should not have any rule suggestions since we have a space after the identifier
        suggestions = suggestions("select * from table ", 20);
        assertEquals("", suggestions.textToMatch());
        assertEquals(true, suggestions.skipRules());

        // Middle of 'table'
        suggestions = suggestions("select * from tableA", 16);
        assertEquals("ta", suggestions.textToMatch());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_tableSource), suggestions.collection().rules.keySet());

        // After 'join'
        suggestions = suggestions("select * from table b inner join ", 32);
        assertEquals("", suggestions.textToMatch());
        assertEquals(true, suggestions.skipRules());

        // Middle of 'join'
        suggestions = suggestions("select * from table b inner join ", 25);
        assertEquals("", suggestions.textToMatch());
        assertEquals(true, suggestions.skipRules());

        // After 'join '
        suggestions = suggestions("select * from tableA b inner join ", 34);
        assertEquals("", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_tableSource), suggestions.collection().rules.keySet());

        // After 'join per'
        suggestions = suggestions("select * from tableA b inner join per ", 37);
        assertEquals("per", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_tableSource), suggestions.collection().rules.keySet());

        // After 'join cat'
        suggestions = suggestions("select * from tableA b inner join cat#per ", 37);
        assertEquals("cat", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_tableSource), suggestions.collection().rules.keySet());

        // After 'join cat#pe'
        suggestions = suggestions("select * from tableA b inner join cat#per ", 40);
        assertEquals("cat#pe", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_tableSource), suggestions.collection().rules.keySet());

        // After 'join cat#per a'
        suggestions = suggestions("select * from table b inner join cat#per a ", 42);
        assertEquals("a", suggestions.textToMatch());
        assertEquals(true, suggestions.skipRules());

        // After 'join cat#per a '
        suggestions = suggestions("select * from table b inner join cat#per a ", 43);
        assertEquals("", suggestions.textToMatch());
        assertEquals(true, suggestions.skipRules());
    }

    @Test
    public void test_expressions() throws BadLocationException
    {
        // After 'e' in where
        Candidates suggestions = suggestions("select * from tableA where ", 25);
        assertEquals("", suggestions.textToMatch());
        assertEquals(true, suggestions.skipRules());

        suggestions = suggestions("select * from tableA where ", 27);
        assertEquals("", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_primary), suggestions.collection().rules.keySet());

        // After 'by'
        suggestions = suggestions("select * from tableA order by ", 28);
        assertEquals("", suggestions.textToMatch());
        assertEquals(true, suggestions.skipRules());

        // Middle of 'by'
        suggestions = suggestions("select * from tableA order by ", 27);
        assertEquals("", suggestions.textToMatch());
        assertEquals(true, suggestions.skipRules());

        suggestions = suggestions("select * from tableA order by ", 30);
        assertEquals("", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_primary), suggestions.collection().rules.keySet());

        // After 'by'
        suggestions = suggestions("select * from tableA group by ", 29);
        assertEquals("", suggestions.textToMatch());
        assertEquals(true, suggestions.skipRules());

        // After 'by '
        suggestions = suggestions("select * from tableA group by ", 30);
        assertEquals("", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_primary), suggestions.collection().rules.keySet());

        // After 'having'
        suggestions = suggestions("select * from tableA having ", 27);
        assertEquals("", suggestions.textToMatch());
        assertEquals(true, suggestions.skipRules());

        // After 'having '
        suggestions = suggestions("select * from tableA having ", 28);
        assertEquals("", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_primary), suggestions.collection().rules.keySet());

        // EOF
        suggestions = suggestions("select * from tableA where ", 27);
        assertEquals("", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_primary), suggestions.collection().rules.keySet());

        // After 'e' in table
        suggestions = suggestions("select * from tableA where ", 20);
        assertEquals("tableA", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_tableSource), suggestions.collection().rules.keySet());

        // Middle of 'table'
        suggestions = suggestions("select * from tableA where ", 16);
        assertEquals("ta", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_tableSource), suggestions.collection().rules.keySet());

        // After ','
        suggestions = suggestions("select *, ", 9);
        assertEquals("", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_primary), suggestions.collection().rules.keySet());

        // After ', '
        suggestions = suggestions("select *, ", 10);
        assertEquals("", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_primary), suggestions.collection().rules.keySet());

        // After ','
        suggestions = suggestions("select *, from tableA", 9);
        assertEquals("", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_primary), suggestions.collection().rules.keySet());
    }

    @Test
    public void test_expressions_in_select() throws BadLocationException
    {
        // Start of query
        Candidates suggestions = suggestions("select ", 0);

        assertEquals("", suggestions.textToMatch());
        assertEquals(true, suggestions.skipRules());

        // After 't'
        // No suggestions should be given
        suggestions = suggestions("select ", 6);
        assertEquals("", suggestions.textToMatch());
        assertEquals(true, suggestions.skipRules());

        // EOF
        suggestions = suggestions("select ", 7);
        assertEquals("", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_primary), suggestions.collection().rules.keySet());

        // After 't'
        suggestions = suggestions("select col ", 6);
        assertEquals("", suggestions.textToMatch());
        assertEquals(true, suggestions.skipRules());

        // Before 'c'
        suggestions = suggestions("select col ", 7);
        assertEquals("", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_primary), suggestions.collection().rules.keySet());

        // On a new line between 'select' and 'col'
        suggestions = suggestions("""
                select



                col
                """, 8);
        assertEquals("", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_primary), suggestions.collection().rules.keySet());

        // On a new line after 'col'
        suggestions = suggestions("""
                select



                col



                """, 14);
        assertEquals("", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_primary), suggestions.collection().rules.keySet());

        // Between 'o' and 'l'
        suggestions = suggestions("select col ", 9);
        assertEquals("co", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_primary), suggestions.collection().rules.keySet());

        // After 'col'
        suggestions = suggestions("select col ", 10);
        assertEquals("col", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_primary), suggestions.collection().rules.keySet());

        // EOF
        suggestions = suggestions("select col ", 11);
        assertEquals("", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_primary), suggestions.collection().rules.keySet());

        // DOT

        // Before 'p'
        suggestions = suggestions("select p. ", 7);
        assertEquals("", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_primary), suggestions.collection().rules.keySet());

        // Between 'p' and '.'
        suggestions = suggestions("select p. ", 8);
        assertEquals("p", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_primary), suggestions.collection().rules.keySet());

        // After 'p.'
        suggestions = suggestions("select p. ", 9);
        assertEquals("p.", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_primary), suggestions.collection().rules.keySet());

        // EOF
        suggestions = suggestions("select p. ", 10);
        assertEquals("", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_primary), suggestions.collection().rules.keySet());

        // HASH

        // Before 'p'
        suggestions = suggestions("select p# ", 7);
        assertEquals("", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_primary), suggestions.collection().rules.keySet());

        // Between 'p' and '#'
        suggestions = suggestions("select p# ", 8);
        assertEquals("p", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_primary), suggestions.collection().rules.keySet());

        // After 'p.'
        suggestions = suggestions("select p# ", 9);
        assertEquals("p#", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_primary), suggestions.collection().rules.keySet());

        // EOF
        suggestions = suggestions("select p# ", 10);
        assertEquals("", suggestions.textToMatch());
        assertEquals(false, suggestions.skipRules());
        assertEquals(Set.of(PayloadBuilderQueryParser.RULE_primary), suggestions.collection().rules.keySet());
    }

    private Candidates suggestions(String query, int offset) throws BadLocationException
    {
        PLBParser parser = new PLBParser(new QuerySession(new CatalogRegistry()));
        return parser.getSuggestions(mock(query), offset);
    }

    private Document mock(String query) throws BadLocationException
    {
        Document doc = Mockito.mock(Document.class);
        Element elm = Mockito.mock(Element.class);
        when(doc.getDefaultRootElement()).thenReturn(elm);
        when(doc.getText(Mockito.anyInt(), Mockito.anyInt())).thenReturn(query);
        return doc;
    }
}
