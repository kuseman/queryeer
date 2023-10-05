package com.queryeer.completion;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.Segment;

import org.apache.commons.lang3.StringUtils;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.CompletionProviderBase;
import org.fife.ui.autocomplete.ParameterizedCompletion;
import org.fife.ui.rtextarea.RTextArea;
import org.fife.ui.rtextarea.ToolTipSupplier;

import com.queryeer.completion.PLBParser.Candidates;
import com.queryeer.completion.PLBParser.TableSource;
import com.queryeer.domain.ICatalogModel;
import com.vmware.antlr4c3.CodeCompletionCore.CandidatesCollection;

import se.kuseman.payloadbuilder.core.execution.QuerySession;
import se.kuseman.payloadbuilder.core.parser.PayloadBuilderQueryLexer;
import se.kuseman.payloadbuilder.core.parser.PayloadBuilderQueryParser;

/** Completion provider for PLB queries */
class PLBCompletionProvider extends CompletionProviderBase implements ToolTipSupplier
{
    private final Segment seg = new Segment();
    private final CompletionRegistry completionRegistry;
    private final PLBParser parser;
    private final QuerySession querySession;
    private List<ICatalogModel> catalogs;

    public PLBCompletionProvider(CompletionRegistry completionRegistry, PLBParser parser, QuerySession querySession, List<ICatalogModel> catalogs)
    {
        this.completionRegistry = completionRegistry;
        this.parser = parser;
        this.querySession = querySession;
        this.catalogs = catalogs;
    }

    @Override
    public String getAlreadyEnteredText(JTextComponent comp)
    {
        Document doc = comp.getDocument();

        int dot = comp.getCaretPosition();
        Element root = doc.getDefaultRootElement();
        int index = root.getElementIndex(dot);
        Element elem = root.getElement(index);
        int start = elem.getStartOffset();
        int len = dot - start;
        try
        {
            doc.getText(start, len, seg);
        }
        catch (BadLocationException ble)
        {
            ble.printStackTrace();
            return EMPTY_STRING;
        }

        int segEnd = seg.offset + len;
        start = segEnd - 1;
        while (start >= seg.offset
                && isValidChar(seg.array[start]))
        {
            start--;
        }
        start++;

        len = segEnd - start;
        return len == 0 ? EMPTY_STRING
                : new String(seg.array, start, len);
    }

    @Override
    public List<Completion> getCompletionsAt(JTextComponent comp, Point p)
    {
        int offset = comp.viewToModel2D(p);
        return getCompletionsInternal(comp, offset, false);
    }

    @Override
    public List<ParameterizedCompletion> getParameterizedCompletions(JTextComponent tc)
    {
        return null;
    }

    @Override
    protected List<Completion> getCompletionsImpl(JTextComponent comp)
    {
        return getCompletionsInternal(comp, comp.getCaretPosition(), false);
    }

    @Override
    public String getToolTipText(RTextArea textArea, MouseEvent e)
    {
        String tip = null;

        // Find the text segment at the mount point
        // and traverse back until we find the first non whitespace
        // caharacter, use that as offset for completions
        int offset = textArea.viewToModel2D(e.getPoint());
        Element root = textArea.getDocument()
                .getDefaultRootElement();
        int index = root.getElementIndex(offset);
        Element elem = root.getElement(index);
        int start = elem.getStartOffset();
        int stop = elem.getEndOffset();
        int len = stop - start;
        try
        {
            textArea.getDocument()
                    .getText(start, len, seg);
        }
        catch (BadLocationException ble)
        {
            ble.printStackTrace();
            return EMPTY_STRING;
        }

        int segEnd = seg.offset + len - 1;
        while (segEnd > 0
                && Character.isWhitespace(seg.array[segEnd]))
        {
            segEnd--;
            stop--;
        }

        List<Completion> completions = getCompletionsInternal(textArea, stop, true);
        if (!completions.isEmpty())
        {
            Completion c = completions.get(0);
            tip = c.getToolTipText();
        }

        return tip;
    }

    private List<Completion> getCompletionsInternal(JTextComponent comp, int offset, boolean equalsMatch)
    {
        Candidates candidates = parser.getSuggestions(comp.getDocument(), offset);
        CandidatesCollection suggestions = candidates.collection();
        String textToMatch = candidates.textToMatch();

        List<Completion> result = new ArrayList<>();

        // Show token completions if we have a partial text
        // else c3 gives weird suggestions (or PLB grammar is weird :))
        if (!isBlank(textToMatch)
                && !equalsMatch)
        {
            suggestions.tokens.entrySet()
                    .forEach(e ->
                    {
                        if (!PLBParser.TOKEN_WHITELIST.contains(e.getKey()))
                        {
                            return;
                        }
                        // Don't suggest keywords if we have rules and the token is a non reserved.
                        // ie.
                        //
                        // select a, | <--- caret
                        // from table
                        //
                        // Here we should input an expression and hence a column reference and hence a identifier
                        // but there are some tokens that can be used as identifier (non reserved ones)
                        // but it will be very weird if we suggest those in auto completion
                        else if (!suggestions.rules.isEmpty()
                                && PLBParser.NON_RESERVED_TOKENS.contains(e.getKey()))
                        {
                            return;
                        }

                        String name = PayloadBuilderQueryLexer.VOCABULARY.getSymbolicName(e.getKey());
                        if (StringUtils.startsWithIgnoreCase(name, textToMatch))
                        {
                            result.add(new BasicCompletion(this, name.toLowerCase())
                            {
                                // Put all keyword suggestions on top
                                {
                                    setRelevance(Integer.MAX_VALUE);
                                }
                            });
                        }
                    });
        }

        // tables/table functions
        if (!candidates.skipRules()
                && suggestions.rules.containsKey(PayloadBuilderQueryParser.RULE_tableSource))
        {
            result.addAll(filter(completionRegistry.getTableCompletions(this, querySession, catalogs, textToMatch), textToMatch, equalsMatch));
            result.addAll(filter(completionRegistry.getTableFunctionCompletions(this, querySession, textToMatch), textToMatch, equalsMatch));
        }

        // columns/scalar functions
        if (!candidates.skipRules()
                && suggestions.rules.containsKey(PayloadBuilderQueryParser.RULE_expression))
        {
            Map<String, TableSource> tableSources = parser.findTableSources(candidates.tree());
            result.addAll(filter(completionRegistry.getColumnCompletions(this, querySession, catalogs, tableSources, textToMatch), textToMatch, equalsMatch));
            result.addAll(filter(completionRegistry.getScalarFunctionCompletions(this, querySession, textToMatch), textToMatch, equalsMatch));
        }

        return result;
    }

    private List<PLBCompletion> filter(List<PLBCompletion> completions, String textToMatch, boolean equalsMatch)
    {
        if (!equalsMatch)
        {
            return completions;
        }

        return completions.stream()
                .filter(c -> StringUtils.equalsAnyIgnoreCase(textToMatch, c.getReplacementText()))
                .collect(toList());
    }

    private boolean isValidChar(char ch)
    {
        return Character.isLetterOrDigit(ch)
                || ch == '_'
                || ch == '#'
                || ch == '.';
    }
}
