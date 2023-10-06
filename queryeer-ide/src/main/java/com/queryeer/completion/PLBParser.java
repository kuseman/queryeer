package com.queryeer.completion;

import static java.util.stream.Collectors.toList;
import static se.kuseman.payloadbuilder.core.utils.CollectionUtils.asSet;

import java.awt.Color;
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

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.LexerNoViableAltException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.lang3.StringUtils;
import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.parser.AbstractParser;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParseResult;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParserNotice;
import org.fife.ui.rsyntaxtextarea.parser.ParseResult;
import org.fife.ui.rsyntaxtextarea.parser.ParserNotice;
import org.fife.ui.rsyntaxtextarea.parser.ParserNotice.Level;

import com.vmware.antlr4c3.CodeCompletionCore;
import com.vmware.antlr4c3.CodeCompletionCore.CandidatesCollection;

import se.kuseman.payloadbuilder.api.QualifiedName;
import se.kuseman.payloadbuilder.api.execution.ValueVector;
import se.kuseman.payloadbuilder.api.expression.IExpression;
import se.kuseman.payloadbuilder.core.execution.QuerySession;
import se.kuseman.payloadbuilder.core.parser.PayloadBuilderQueryLexer;
import se.kuseman.payloadbuilder.core.parser.PayloadBuilderQueryParser;
import se.kuseman.payloadbuilder.core.parser.PayloadBuilderQueryParser.ComparisonExpressionContext;
import se.kuseman.payloadbuilder.core.parser.PayloadBuilderQueryParser.IdentifierContext;
import se.kuseman.payloadbuilder.core.parser.PayloadBuilderQueryParser.LiteralContext;
import se.kuseman.payloadbuilder.core.parser.PayloadBuilderQueryParser.LiteralExpressionContext;
import se.kuseman.payloadbuilder.core.parser.PayloadBuilderQueryParser.NonReservedContext;
import se.kuseman.payloadbuilder.core.parser.PayloadBuilderQueryParser.PrimaryContext;
import se.kuseman.payloadbuilder.core.parser.PayloadBuilderQueryParser.PrimaryExpressionContext;
import se.kuseman.payloadbuilder.core.parser.PayloadBuilderQueryParser.QueryContext;
import se.kuseman.payloadbuilder.core.parser.PayloadBuilderQueryParser.StatementContext;
import se.kuseman.payloadbuilder.core.parser.PayloadBuilderQueryParser.TableSourceContext;
import se.kuseman.payloadbuilder.core.parser.PayloadBuilderQueryParser.UseStatementContext;
import se.kuseman.payloadbuilder.core.parser.PayloadBuilderQueryParserBaseVisitor;
import se.kuseman.payloadbuilder.core.parser.QueryParser;

/**
 * Parser for {@link RSyntaxTextArea} NOTE! Is not thread safe and should be used per text area
 */
class PLBParser extends AbstractParser implements DocumentListener
{
    static final Set<Integer> NON_RESERVED_TOKENS;

    // White list of tokens we want to suggest. Common ones like TOP/DISTINCT/ORDER BY etc.
    //@formatter:off
    static final Set<Integer> TOKEN_WHITELIST = asSet(
            PayloadBuilderQueryLexer.ANALYZE,
            PayloadBuilderQueryLexer.ASC,
            PayloadBuilderQueryLexer.APPLY,
            PayloadBuilderQueryLexer.BY,
            PayloadBuilderQueryLexer.CACHE,
            PayloadBuilderQueryLexer.CACHES,
            PayloadBuilderQueryLexer.CASE,
            PayloadBuilderQueryLexer.CROSS,
            PayloadBuilderQueryLexer.DESC,
            PayloadBuilderQueryLexer.DESCRIBE,
            PayloadBuilderQueryLexer.DISTINCT,
            PayloadBuilderQueryLexer.DROP,
            PayloadBuilderQueryLexer.ELSE,
            PayloadBuilderQueryLexer.FLUSH,
            PayloadBuilderQueryLexer.END,
            PayloadBuilderQueryLexer.EXISTS,
            PayloadBuilderQueryLexer.FIRST,
            PayloadBuilderQueryLexer.FOR,
            PayloadBuilderQueryLexer.FROM,
            PayloadBuilderQueryLexer.FUNCTIONS,
            PayloadBuilderQueryLexer.GROUP,
            PayloadBuilderQueryLexer.HAVING,
            PayloadBuilderQueryLexer.IF,
            PayloadBuilderQueryLexer.IN,
            PayloadBuilderQueryLexer.INNER,
            PayloadBuilderQueryLexer.INSERT,
            PayloadBuilderQueryLexer.INTO,
            PayloadBuilderQueryLexer.IS,
            PayloadBuilderQueryLexer.JOIN,
            PayloadBuilderQueryLexer.LAST,
            PayloadBuilderQueryLexer.LEFT,
            PayloadBuilderQueryLexer.LIKE,
            PayloadBuilderQueryLexer.NOT,
            PayloadBuilderQueryLexer.NULL,
            PayloadBuilderQueryLexer.NULLS,
            PayloadBuilderQueryLexer.ON,
            PayloadBuilderQueryLexer.OR,
            PayloadBuilderQueryLexer.ORDER,
            PayloadBuilderQueryLexer.OUTER,
            PayloadBuilderQueryLexer.POPULATE,
            PayloadBuilderQueryLexer.PRINT,
            PayloadBuilderQueryLexer.REMOVE,
            PayloadBuilderQueryLexer.RIGHT,
            PayloadBuilderQueryLexer.SELECT,
            PayloadBuilderQueryLexer.SET,
            PayloadBuilderQueryLexer.SHOW,
            PayloadBuilderQueryLexer.THEN,
            PayloadBuilderQueryLexer.TIME,
            PayloadBuilderQueryLexer.TOP,
            PayloadBuilderQueryLexer.USE,
            PayloadBuilderQueryLexer.VARIABLES,
            PayloadBuilderQueryLexer.WHEN,
            PayloadBuilderQueryLexer.WITH,
            PayloadBuilderQueryLexer.WHERE,
            PayloadBuilderQueryLexer.ZONE);
    //@formatter:on
    private static final Set<Integer> PREFERRED_RULES = asSet(PayloadBuilderQueryParser.RULE_tableSource, PayloadBuilderQueryParser.RULE_expression);
    private final QuerySession session;
    private PayloadBuilderQueryParser parser;
    private QueryContext queryContext;
    private CodeCompletionCore core;
    private DefaultParseResult result;
    /** Flag that indicates if the current parsing id dirty or not */
    private boolean parsingDirty;

    PLBParser(QuerySession session)
    {
        this.session = session;
    }

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

    @Override
    public ParseResult parse(RSyntaxDocument doc, String style)
    {
        if (result == null
                || parsingDirty)
        {
            parse(doc);
        }

        return result;
    }

    private void parse(Document doc)
    {
        result = new DefaultParseResult(this);
        result.setParsedLines(0, doc.getDefaultRootElement()
                .getElementCount());

        long start = System.currentTimeMillis();
        try
        {
            CharStream charStream = CharStreams.fromString(doc.getText(0, doc.getLength()));
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

                    if (!(ctx.expression() instanceof PrimaryExpressionContext))
                    {
                        return null;
                    }

                    PrimaryContext primary = ((PrimaryExpressionContext) ctx.expression()).primary();

                    if (!(primary instanceof LiteralExpressionContext))
                    {
                        return null;
                    }

                    LiteralContext literal = ((LiteralExpressionContext) primary).literal();

                    QualifiedName qname = QueryParser.getQualifiedName(ctx.qname());
                    String catalogAlias = qname.getFirst();
                    QualifiedName property = qname.extract(1);

                    IExpression literalExpression = QueryParser.getLiteralExpression(literal);

                    ValueVector value = literalExpression.eval(null);
                    session.setCatalogProperty(catalogAlias, property.toDotDelimited(), value);
                    return null;
                }

                @Override
                public Void visitComparisonExpression(ComparisonExpressionContext ctx)
                {
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
                    else if (StringUtils.equalsAnyIgnoreCase(left, right))
                    {
                        noticeText = "Same expression on both sides";
                    }

                    if (noticeText != null)
                    {
                        DefaultParserNotice notice = new DefaultParserNotice(result.getParser(), "Same expression on both sides", ctx.start.getLine() - 1, startIndex, length);
                        notice.setLevel(Level.WARNING);
                        notice.setColor(Color.BLUE);
                        result.addNotice(notice);
                    }

                    return null;
                };

            }.visit(queryContext);

            core = new CodeCompletionCore(parser, PREFERRED_RULES, Set.of());
        }
        catch (BadLocationException e)
        {
            result.setError(e);
        }
        finally
        {
            parsingDirty = false;
        }

        result.setParseTime(System.currentTimeMillis() - start);
    }

    /** Get suggestion candidates from provided document and offset */
    Candidates getSuggestions(Document doc, int offset)
    {
        if (queryContext == null
                || parsingDirty)
        {
            parse(doc);
        }

        TokenOffset tokenOffset = findTokenFromOffset(queryContext, offset);
        if (tokenOffset == null)
        {
            return Candidates.EMPTY;
        }
        CandidatesCollection c3Candiates = core.collectCandidates(tokenOffset.suggestTokenIndex, null);
        return new Candidates(tokenOffset.tree, c3Candiates, tokenOffset.textToMatch, tokenOffset.skipRules);
    }

    /** Find table sources from provided tree */
    Map<String, TableSource> findTableSources(ParseTree tree)
    {
        // TOOD: Find all USE statements and populate properties to be able to let catalogs collect correctly

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

    private TokenOffset findTokenFromOffset(ParseTree tree, int offset)
    {
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

                    return new TokenOffset(tokenIndex, skipRules, node, textToMatch);
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

                    return new TokenOffset(tokenIndex, skipRules, node, "");
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

    private record TokenOffset(int suggestTokenIndex, boolean skipRules, ParseTree tree, String textToMatch)
    {
    }

    record Candidates(ParseTree tree, CandidatesCollection collection, String textToMatch, boolean skipRules)
    {
        static final Candidates EMPTY = new Candidates(null, new CandidatesCollection(), "", true);
    }

    record TableSource(String catalogAlias, QualifiedName qname, boolean isFunction)
    {
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

    /** Listener for errors and transform these into {@link ParserNotice}'s */
    private static class ErrorListener extends BaseErrorListener
    {
        private final DefaultParseResult result;

        ErrorListener(DefaultParseResult result)
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

            result.addNotice(new DefaultParserNotice(result.getParser(), msg, line - 1, startIndex, length));
        }
    }

    // DocumentListener

    @Override
    public void insertUpdate(DocumentEvent e)
    {
        parsingDirty = true;
    }

    @Override
    public void removeUpdate(DocumentEvent e)
    {
        parsingDirty = true;
    }

    @Override
    public void changedUpdate(DocumentEvent e)
    {
        parsingDirty = true;
    }

    // End DocumentListener
}
