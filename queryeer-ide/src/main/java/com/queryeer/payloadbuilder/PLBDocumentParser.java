package com.queryeer.payloadbuilder;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static se.kuseman.payloadbuilder.core.utils.CollectionUtils.asSet;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.AbstractAction;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.LexerNoViableAltException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.queryeer.api.editor.ITextEditorDocumentParser;
import com.queryeer.api.event.ExecuteQueryEvent;
import com.queryeer.api.event.ExecuteQueryEvent.OutputType;
import com.queryeer.api.service.IEventBus;
import com.vmware.antlr4c3.CodeCompletionCore;
import com.vmware.antlr4c3.CodeCompletionCore.CandidatesCollection;

import se.kuseman.payloadbuilder.api.QualifiedName;
import se.kuseman.payloadbuilder.api.execution.ValueVector;
import se.kuseman.payloadbuilder.api.expression.IExpression;
import se.kuseman.payloadbuilder.core.execution.QuerySession;
import se.kuseman.payloadbuilder.core.parser.PayloadBuilderQueryLexer;
import se.kuseman.payloadbuilder.core.parser.PayloadBuilderQueryParser;
import se.kuseman.payloadbuilder.core.parser.PayloadBuilderQueryParser.Expr_compareContext;
import se.kuseman.payloadbuilder.core.parser.PayloadBuilderQueryParser.IdentifierContext;
import se.kuseman.payloadbuilder.core.parser.PayloadBuilderQueryParser.LiteralContext;
import se.kuseman.payloadbuilder.core.parser.PayloadBuilderQueryParser.LiteralExpressionContext;
import se.kuseman.payloadbuilder.core.parser.PayloadBuilderQueryParser.NonReservedContext;
import se.kuseman.payloadbuilder.core.parser.PayloadBuilderQueryParser.QueryContext;
import se.kuseman.payloadbuilder.core.parser.PayloadBuilderQueryParser.StatementContext;
import se.kuseman.payloadbuilder.core.parser.PayloadBuilderQueryParser.TableSourceContext;
import se.kuseman.payloadbuilder.core.parser.PayloadBuilderQueryParser.UseStatementContext;
import se.kuseman.payloadbuilder.core.parser.PayloadBuilderQueryParserBaseVisitor;
import se.kuseman.payloadbuilder.core.parser.QueryParser;

/** Document parser of Payloadbuilder queries */
class PLBDocumentParser implements ITextEditorDocumentParser
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PLBDocumentParser.class);

    private static final Set<Integer> NON_RESERVED_TOKENS;
    private static final Set<Integer> PREFERRED_RULES = asSet(PayloadBuilderQueryParser.RULE_tableSource, PayloadBuilderQueryParser.RULE_primary);
    private final QuerySession session;
    private final CompletionRegistry completionRegistry;
    private final IEventBus eventBus;
    private final CatalogsConfigurable catalogsConfigurable;

    private PayloadBuilderQueryParser parser;
    private QueryContext queryContext;
    private CodeCompletionCore core;
    private List<ParseItem> result = new ArrayList<>();

    static
    {
        Set<Integer> nonReservedTokens = new HashSet<>();
        try
        {
            for (Method method : NonReservedContext.class.getDeclaredMethods())
            {
                if (Modifier.isPublic(method.getModifiers())
                        && method.getReturnType() == TerminalNode.class)
                {
                    String tokenName = method.getName();
                    Field field = PayloadBuilderQueryLexer.class.getField(tokenName);
                    int token = field.getInt(null);
                    nonReservedTokens.add(token);
                }
            }
        }
        catch (Exception e)
        {
        }
        finally
        {
            NON_RESERVED_TOKENS = Collections.unmodifiableSet(nonReservedTokens);
        }
    }

    PLBDocumentParser(IEventBus eventBus, QuerySession session, CompletionRegistry completionRegistry, CatalogsConfigurable catalogsConfigurable)
    {
        this.eventBus = eventBus;
        this.session = session;
        this.completionRegistry = completionRegistry;
        this.catalogsConfigurable = catalogsConfigurable;
    }

    @Override
    public void parse(Reader documentReader)
    {
        result.clear();
        try
        {
            CharStream charStream = CharStreams.fromReader(documentReader);
            PayloadBuilderQueryLexer lexer = new PayloadBuilderQueryLexer(charStream);
            lexer.removeErrorListeners();

            ErrorListener errorListener = new ErrorListener(result);
            lexer.addErrorListener(errorListener);

            TokenStream tokenStream = new CommonTokenStream(lexer);

            parser = new PayloadBuilderQueryParser(tokenStream);

            parser.removeErrorListeners();
            parser.addErrorListener(errorListener);

            parser.setErrorHandler(new DefaultErrorStrategy()
            {
                @Override
                protected Token singleTokenDeletion(Parser recognizer)
                {
                    return null;
                }

            });

            queryContext = parser.query();

            new PayloadBuilderQueryParserBaseVisitor<Void>()
            {
                // Set catalog properties to session.
                // This is needed to be able to get auto-completions before the query are executed the first time
                // if the query contains catalog properties inside the query as use statements
                // Only literals are supported for now
                @Override
                public Void visitUseStatement(UseStatementContext ctx)
                {
                    // Switching default catalogs here is not needed
                    if (ctx.expression() == null)
                    {
                        return null;
                    }

                    ParseTree current = ctx.expression();

                    while (current != null
                            && !(current instanceof LiteralExpressionContext))
                    {
                        current = current.getChildCount() > 0 ? current.getChild(0)
                                : null;
                    }

                    if (current == null)
                    {
                        return null;
                    }

                    LiteralContext literal = ((LiteralExpressionContext) current).literal();

                    QualifiedName qname = QueryParser.getQualifiedName(ctx.qname());
                    String catalogAlias = qname.getFirst();
                    QualifiedName property = qname.extract(1);

                    IExpression literalExpression = QueryParser.getLiteralExpression(literal);

                    ValueVector value = literalExpression.eval(null);
                    session.setCatalogProperty(catalogAlias, property.toDotDelimited(), value);
                    return null;
                }

                @Override
                public Void visitExpr_compare(Expr_compareContext ctx)
                {
                    if (ctx.right == null)
                    {
                        return null;
                    }

                    String left = ctx.left.getText();
                    String right = ctx.right.getText();

                    int startIndex = ctx.start.getStartIndex();
                    int length = ctx.stop.getStopIndex() - startIndex + 1;
                    String noticeText = null;

                    // Comparison with null
                    if (ctx.left.start.getType() == PayloadBuilderQueryLexer.NULL
                            || ctx.right.start.getType() == PayloadBuilderQueryLexer.NULL)
                    {
                        noticeText = "Always false";
                    }
                    // Same expression on both sides
                    else if (Strings.CI.equals(left, right))
                    {
                        noticeText = "Same expression on both sides";
                    }

                    if (noticeText != null)
                    {
                        ParseItem item = new ParseItem(noticeText, ctx.start.getLine() - 1, startIndex, length, Color.BLUE, ParseItem.Level.WARN);
                        result.add(item);
                    }

                    return null;
                };

            }.visit(queryContext);

            core = new CodeCompletionCore(parser, PREFERRED_RULES, Set.of());
        }
        catch (IOException e)
        {
            core = null;
            queryContext = null;
            LOGGER.error("Error parsing PLB document", e);
        }
        finally
        {
        }
    }

    @Override
    public List<ParseItem> getParseResult()
    {
        return result;
    }

    @Override
    public boolean supportsToolTips()
    {
        return false;
    }

    @Override
    public boolean supportsLinkActions()
    {
        return true;
    }

    @Override
    public boolean supportsCompletions()
    {
        return true;
    }

    @Override
    public LinkAction getLinkAction(int offset)
    {
        QueryContext ctx = queryContext;
        if (ctx == null)
        {
            return null;
        }

        ParserRuleContext innerMostContext = getInnerMostContext(ctx, offset);
        while (innerMostContext != null)
        {
            if (innerMostContext instanceof TableSourceContext tsCtx
                    && tsCtx.tableName() != null)
            {
                final String table = tsCtx.tableName()
                        .getText();

                int start = tsCtx.tableName()
                        .qname()
                        .getStart()
                        .getStartIndex();
                int stop = tsCtx.tableName()
                        .qname()
                        .getStop()
                        .getStopIndex() + 1;

                return new LinkAction(start, stop, asList(new AbstractAction("Top 500")
                {
                    @Override
                    public void actionPerformed(ActionEvent e)
                    {
                        eventBus.publish(new ExecuteQueryEvent(OutputType.TABLE, new ExecuteQueryContext("select top 500 * from " + table)));
                    }
                }));
            }

            innerMostContext = innerMostContext.getParent();
        }

        return null;
    }

    @Override
    public CompletionResult getCompletionItems(int offset)
    {
        Candidates candidates = getCandidates(offset);
        CandidatesCollection suggestions = candidates.collection();
        String textToMatch = candidates.textToMatch();

        List<CompletionItem> result = new ArrayList<>();

        // Show token completions if we have a partial text
        // else c3 gives weird suggestions (or PLB grammar is weird :))
        if (!isBlank(textToMatch))
        {
            suggestions.tokens.entrySet()
                    .forEach(e ->
                    {
                        // Don't suggest keywords if we have rules and the token is a non reserved.
                        // ie.
                        //
                        // select a, | <--- caret
                        // from table
                        //
                        // Here we should input an expression and hence a column reference and hence a identifier
                        // but there are some tokens that can be used as identifier (non reserved ones)
                        // but it will be very weird if we suggest those in auto completion
                        if (!suggestions.rules.isEmpty()
                                && NON_RESERVED_TOKENS.contains(e.getKey()))
                        {
                            return;
                        }

                        String name = PayloadBuilderQueryLexer.VOCABULARY.getSymbolicName(e.getKey());
                        if (Strings.CI.startsWith(name, textToMatch))
                        {
                            // Put all keyword suggestions on top
                            result.add(new CompletionItem(name.toLowerCase(), Integer.MAX_VALUE));
                        }
                    });
        }

        MutableBoolean partialResult = new MutableBoolean(false);

        // tables/table functions
        if (!candidates.skipRules()
                && suggestions.rules.containsKey(PayloadBuilderQueryParser.RULE_tableSource))
        {
            result.addAll(completionRegistry.getTableCompletions(session, catalogsConfigurable.getCatalogs(), partialResult));
            result.addAll(completionRegistry.getTableFunctionCompletions(session));
        }

        // columns/scalar functions
        if (!candidates.skipRules()
                && suggestions.rules.containsKey(PayloadBuilderQueryParser.RULE_primary))
        {
            Map<String, TableSource> tableSources = findTableSources(candidates.tree());
            result.addAll(completionRegistry.getColumnCompletions(session, catalogsConfigurable.getCatalogs(), tableSources, partialResult));
            result.addAll(completionRegistry.getScalarFunctionCompletions(session));
        }

        return new CompletionResult(result, partialResult.booleanValue());
    }

    // /** Find table sources from provided tree */
    private Map<String, TableSource> findTableSources(ParseTree tree)
    {
        // First find statement root
        ParseTree current = tree;
        while (!(current instanceof StatementContext))
        {
            if (current.getParent() == null)
            {
                break;
            }
            current = current.getParent();
        }

        TableSourceVisitor visitor = new TableSourceVisitor();
        visitor.visit(current);

        return visitor.tableSourceByAliasToken;
    }

    private Candidates getCandidates(int offset)
    {
        TokenOffset tokenOffset = findTokenFromOffset(queryContext, offset);
        if (tokenOffset == null)
        {
            return Candidates.EMPTY;
        }

        ParserRuleContext context = null;
        if (result.stream()
                .anyMatch(n -> n.getLevel() == ParseItem.Level.ERROR))
        {
            ParseTree tree = tokenOffset.tree;
            // Pick the previous node if we are at eof
            if (tree instanceof TerminalNode
                    && ((TerminalNode) tree).getSymbol()
                            .getType() == PayloadBuilderQueryLexer.EOF)
            {
                tree = tokenOffset.prevTree;
            }
            context = closesParentRule(tree);
        }

        CandidatesCollection c3Candiates = core.collectCandidates(tokenOffset.suggestTokenIndex, context);
        return new Candidates(tokenOffset.tree, c3Candiates, tokenOffset.textToMatch, tokenOffset.skipRules);
    }

    private ParserRuleContext closesParentRule(ParseTree t)
    {
        while (t != null)
        {
            if (t instanceof ParserRuleContext
                    && (PREFERRED_RULES.contains(((ParserRuleContext) t).getRuleIndex())))
            {
                return (ParserRuleContext) t;
            }

            t = t.getParent();
        }

        return null;
    }

    private ParserRuleContext getInnerMostContext(ParserRuleContext ctx, int offset)
    {
        List<ParseTree> queue = new ArrayList<>();
        queue.add(ctx);
        ParserRuleContext prev = null;
        while (!queue.isEmpty())
        {
            ParseTree current = queue.remove(0);

            if (current instanceof ParserRuleContext cctx)
            {
                if (offset >= cctx.start.getStartIndex()
                        && offset < cctx.stop.getStopIndex())
                {
                    int count = current.getChildCount();
                    for (int i = 0; i < count; i++)
                    {
                        ParseTree child = current.getChild(i);
                        queue.add(child);
                    }
                }
                prev = cctx;
            }
        }

        if (prev != null
                && offset >= prev.start.getStartIndex()
                && offset < prev.stop.getStopIndex())
        {
            return prev;
        }

        return null;
    }

    private TokenOffset findTokenFromOffset(ParseTree tree, int offset)
    {
        if (tree == null)
        {
            return null;
        }
        List<ParseTree> queue = new ArrayList<>();
        queue.add(tree);

        TerminalNode prev = null;

        while (!queue.isEmpty())
        {
            ParseTree current = queue.remove(0);
            if (current instanceof TerminalNode)
            {
                TerminalNode node = (TerminalNode) current;
                int start = node.getSymbol()
                        .getStartIndex();
                int stop = node.getSymbol()
                        .getStopIndex();

                boolean isEOF = node.getSymbol()
                        .getType() == Token.EOF;
                int tokenIndex = node.getSymbol()
                        .getTokenIndex();

                // We have a match of current node
                // NOTE! We check the token end plus one to be able to match the end of
                // a token where the caret is at
                // ie. "SELECT col|"
                // Here 'col' has interval 7:9 and caret is at 10 and hence we want to get a
                // match on col
                if (!isEOF
                        && offset >= start
                        && offset <= stop + 1)
                {
                    String textToMatch = getTextToMatch(node.getSymbol()
                            .getTokenIndex(), offset);

                    boolean skipRules = false;

                    // Skip suggestions
                    if (skipRules(true, node))
                    {
                        skipRules = true;
                    }
                    // .. move to next
                    else if (moveToNextToken(node))
                    {
                        tokenIndex++;
                    }

                    return new TokenOffset(tokenIndex, skipRules, node, prev, textToMatch);
                }
                // We passed the offset which means caret is at a hidden channel token
                // Find that token in the input stream
                else if (start > offset
                        || isEOF)
                {
                    // If EOF then use that token for search else
                    // find the actual token at the caret
                    Token tokenToSearch = isEOF ? node.getSymbol()
                            : findHiddenToken(tokenIndex - 1, offset);

                    tokenIndex = tokenToSearch != null ? tokenToSearch.getTokenIndex()
                            : -1;

                    boolean skipRules = false;

                    if (prev != null
                            && skipRules(false, isEOF ? prev
                                    : node))
                    {
                        skipRules = true;
                    }

                    return new TokenOffset(tokenIndex, skipRules, node, prev, "");
                }

                prev = node;
            }
            else
            {
                int childCount = current.getChildCount();
                for (int i = childCount - 1; i >= 0; i--)
                {
                    ParseTree child = current.getChild(i);
                    queue.add(0, child);
                }
            }
        }

        return null;
    }

    private boolean isAliasedTableSource(TerminalNode node)
    {
        return isIdentifierToken(node.getSymbol())
                && node.getParent() instanceof IdentifierContext
                && node.getParent()
                        .getParent() instanceof TableSourceContext;
    }

    private boolean isTableSource(TerminalNode node)
    {
        ParseTree current = node;
        while (current != null)
        {
            if (current instanceof TableSourceContext)
            {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /** Handle cases where we don't want any rule suggestions at all where c3 otherwise would have given us those */
    private boolean skipRules(boolean caretNode, TerminalNode nodeToCheck)
    {
        int type = nodeToCheck.getSymbol()
                .getType();

        // If we are at an aliased table source "table t|" then we skip
        // rules otherwise c3 we will give suggestions of new table which is wrong
        if (caretNode
                && isAliasedTableSource(nodeToCheck))
        {
            return true;
        }
        // Same as above but if have a hidden token between node and caret
        // ie. "table |" or "table t |"
        // here we would get table source suggestions from c3 too that we don't want
        else if (!caretNode
                && isTableSource(nodeToCheck))
        {
            return true;
        }
        // If we have the cursor on a non identifier token
        // then we should have no suggestions
        // IE. "select|", "where|", "wh|ere" etc.
        // Special case with COMMA, PARENO, PARENC, we want to have suggestions when caret are located
        // at the comma that we otherwise wouldn't have gotten
        else if (caretNode
                && type != PayloadBuilderQueryLexer.COMMA
                && type != PayloadBuilderQueryLexer.PARENO
                && type != PayloadBuilderQueryLexer.PARENC
                && !isIdentifierToken(nodeToCheck.getSymbol()))
        {
            return true;
        }

        return false;
    }

    private boolean moveToNextToken(TerminalNode nodeToCheck)
    {
        int type = nodeToCheck.getSymbol()
                .getType();

        // Special case with COMMA, PARENO, PARENC, it gives no suggestions to use the next token in the stream
        if (type == PayloadBuilderQueryLexer.COMMA
                || type == PayloadBuilderQueryLexer.PARENO
                || type == PayloadBuilderQueryLexer.PARENC)
        {
            return true;
        }

        return false;
    }

    private Token findHiddenToken(int tokenIndex, int offset)
    {
        for (int i = tokenIndex; i >= 0; i--)
        {
            Token token = parser.getInputStream()
                    .get(i);
            if (offset >= token.getStartIndex()
                    && offset <= token.getStopIndex())
            {
                return token;
            }
        }
        return null;
    }

    private String getTextToMatch(int tokenIndex, int offset)
    {
        // Find out the text to match
        // search backwards and keep searching as long as we have
        // dot/identifier/hash and collect the string of those
        StringBuilder sb = new StringBuilder();
        Token token = parser.getInputStream()
                .get(tokenIndex);
        int start = 0;
        while (isIdentifierToken(token))
        {
            sb.insert(0, token.getText());
            start = token.getStartIndex();
            if (tokenIndex - 1 < 0)
            {
                break;
            }

            token = parser.getInputStream()
                    .get(--tokenIndex);
        }

        return StringUtils.substring(sb.toString(), 0, offset - start);
    }

    private boolean isIdentifierToken(Token token)
    {
        return token.getType() == PayloadBuilderQueryLexer.DOT
                || token.getType() == PayloadBuilderQueryLexer.HASH
                || token.getType() == PayloadBuilderQueryLexer.IDENTIFIER
                || NON_RESERVED_TOKENS.contains(token.getType());
    }

    record TableSource(String catalogAlias, QualifiedName qname, boolean isFunction)
    {
    }

    private record TokenOffset(int suggestTokenIndex, boolean skipRules, ParseTree tree, ParseTree prevTree, String textToMatch)
    {
    }

    private record Candidates(ParseTree tree, CandidatesCollection collection, String textToMatch, boolean skipRules)
    {
        static final Candidates EMPTY = new Candidates(null, new CodeCompletionCore.CandidatesCollection(), "", true);
    }

    /**
     * Visitor that finds table sources and their aliases in visited tree.
     *
     * <pre>
     * TODO: scopes, sub query scopes, sub query select items, expression scans, dereferences, lambda expressions
     * </pre>
     */
    private static class TableSourceVisitor extends PayloadBuilderQueryParserBaseVisitor<Object>
    {
        final Map<String, TableSource> tableSourceByAliasToken = new HashMap<>();

        TableSourceVisitor()
        {
        }

        @Override
        public Object visitTableSource(TableSourceContext ctx)
        {
            String alias = ctx.identifier() != null ? ctx.identifier()
                    .getText()
                    .toLowerCase()
                    : "";

            String catalogAlias;
            QualifiedName name;
            boolean isFunction;

            if (ctx.tableName() != null)
            {
                isFunction = false;
                catalogAlias = ctx.tableName().catalog != null ? ctx.tableName().catalog.getText()
                        : "";

                name = QualifiedName.of(ctx.tableName()
                        .qname().parts.stream()
                        .map(p -> p.getText())
                        .collect(toList()));
            }
            else if (ctx.functionCall() != null)
            {
                isFunction = true;
                catalogAlias = ctx.functionCall()
                        .functionName().catalog != null
                                ? ctx.functionCall()
                                        .functionName().catalog.getText()
                                : "";
                name = QualifiedName.of(ctx.functionCall()
                        .functionName().function.getText());
            }
            else
            {
                // TODO: expression scan. If expression is a column reference then
                // lookup the first part in already collected aliases and and a table source with
                // a qualified path

                return super.visitTableSource(ctx);
            }

            tableSourceByAliasToken.put(alias, new TableSource(catalogAlias, name, isFunction));

            return super.visitTableSource(ctx);
        }
    }

    /** Listener for errors and transform these into {@link ParseItem}'s */
    private static class ErrorListener extends BaseErrorListener
    {
        private final List<ParseItem> result;

        ErrorListener(List<ParseItem> result)
        {
            this.result = result;
        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e)
        {
            int startIndex = 1;
            int length = -1;

            if (offendingSymbol instanceof Token)
            {
                startIndex = ((Token) offendingSymbol).getStartIndex();
                length = ((Token) offendingSymbol).getStopIndex() - startIndex + 1;
            }
            else if (e != null)
            {
                if (e.getOffendingToken() != null)
                {
                    startIndex = e.getOffendingToken()
                            .getStartIndex();
                    length = e.getOffendingToken()
                            .getStopIndex() - startIndex + 1;

                }
                else if (e instanceof LexerNoViableAltException)
                {
                    startIndex = ((LexerNoViableAltException) e).getStartIndex();
                    length = 1;
                }
            }

            if (length == 0)
            {
                startIndex = -1;
                length = -1;
            }

            result.add(new ParseItem(msg, line - 1, startIndex, length));
        }
    }

}
