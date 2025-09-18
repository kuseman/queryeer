package se.kuseman.payloadbuilder.catalog.jdbc.dialect;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.Action;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.queryeer.api.editor.ITextEditorDocumentParser;
import com.queryeer.api.event.ExecuteQueryEvent;
import com.queryeer.api.extensions.output.table.TableTransferable;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.ITemplateService;
import com.vmware.antlr4c3.CodeCompletionCore;

import se.kuseman.payloadbuilder.catalog.Common;
import se.kuseman.payloadbuilder.catalog.jdbc.CatalogCrawlService;
import se.kuseman.payloadbuilder.catalog.jdbc.ExecuteQueryContext;
import se.kuseman.payloadbuilder.catalog.jdbc.IConnectionState;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.QueryActionsConfigurable.ActionTarget;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.QueryActionsConfigurable.ActionType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.QueryActionsConfigurable.QueryActionResult;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Constraint;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ForeignKey;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Index;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ObjectName;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ObjectType;
import se.kuseman.payloadbuilder.catalog.jdbc.model.TableSource;

/** Parser for antlr based database implementations */
abstract class AntlrDocumentParser<T extends ParserRuleContext> implements ITextEditorDocumentParser
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AntlrDocumentParser.class);
    private static final String TABLE_TEMPLATE = Common.readResource("/se/kuseman/payloadbuilder/catalog/jdbc/templates/Table.html");
    protected static final String INDICES = "indices";
    protected static final String FOREIGN_KEYS = "foreignKeys";
    protected static final String CONSTRAINTS = "constraints";
    protected static final String TABLE = "table";
    private final IEventBus eventBus;
    private final QueryActionsConfigurable queryActionsConfigurable;
    protected final CatalogCrawlService crawlService;
    protected final IConnectionState connectionState;
    protected final ITemplateService templateService;

    AntlrDocumentParser(IEventBus eventBus, QueryActionsConfigurable queryActionsConfigurable, CatalogCrawlService crawlService, IConnectionState connectionState, ITemplateService templateService)
    {
        this.eventBus = requireNonNull(eventBus, "eventBus");
        this.queryActionsConfigurable = requireNonNull(queryActionsConfigurable, "queryActionsConfigurable");
        this.crawlService = requireNonNull(crawlService, "crawlService");
        this.connectionState = requireNonNull(connectionState, "connectionState");
        this.templateService = requireNonNull(templateService, "templateService");
    }

    protected T context;
    protected Parser parser;
    protected CodeCompletionCore core;

    protected List<ParseItem> parseResult = new ArrayList<>();

    protected abstract Lexer createLexer(CharStream charStream);

    protected abstract Parser createParser(TokenStream tokenStream);

    protected abstract T parse(Parser parser);

    @Override
    public void parse(Reader documentReader)
    {
        parseResult.clear();
        try
        {
            CharStream charStream = CharStreams.fromReader(documentReader);
            Lexer lexer = createLexer(charStream);
            lexer.removeErrorListeners();

            AntlrErrorListener errorListener = new AntlrErrorListener(parseResult);
            lexer.addErrorListener(errorListener);

            TokenStream tokenStream = new CommonTokenStream(lexer);

            parser = createParser(tokenStream);

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

            // TODO: index sql-clauses intervals

            context = parse(parser);
            core = new CodeCompletionCore(parser, getCodeCompleteRuleIndices(), Set.of());

            afterParse();
        }

        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<ParseItem> getParseResult()
    {
        return parseResult;
    }

    @Override
    public boolean supportsCompletions()
    {
        return false;
    }

    @Override
    public boolean supportsLinkActions()
    {
        return false;
    }

    @Override
    public boolean supportsToolTips()
    {
        return false;
    }

    /** Called after initial parsing is done to let implementers do more work on AST */
    protected void afterParse()
    {
    }

    /** Return ANTLR rule id's that are used for code completions */
    protected abstract Set<Integer> getCodeCompleteRuleIndices();

    /** Return ANTLR rule id's that matches table sources that is used for tooltips/linkactions */
    protected abstract Set<Integer> getTableSourceRuleIndices();

    /** Return ANTLR rule id's that matches procedures / functions is used for tooltips/linkactions */
    protected abstract Set<Integer> getProcedureFunctionsRuleIndices();

    /** Get the table source text from rule used for tooltips/linkactions */
    protected abstract Pair<Interval, ObjectName> getTableSource(ParserRuleContext ctx);

    /** Get the table source text from rule used for tooltips/linkactions */
    protected abstract Pair<Interval, ObjectName> getProcedureFunction(ParserRuleContext ctx);

    /** Returns the id for 'IN' lexer token. */
    protected int getInTokenId()
    {
        return -1;
    }

    /**
     * Builds a model for provided table source used when rendering template
     * 
     * <pre>
     * Returns a map with following structure:
     * table: {@link TableSource}
     * foreignKeys: List of {@link ForeignKey}
     * indices: List of {@link Index}
     * constraints: List of {@link Constraint}
     * </pre>
     */
    protected Map<String, Object> getTableSourceTooltipModel(ObjectName name)
    {
        return null;
    }

    /** Return completion items from provided token offset */
    protected abstract CompletionResult getCompletionItems(TokenOffset tokenOffset);

    @Override
    public CompletionResult getCompletionItems(int offset)
    {
        TokenOffset tokenOffset = findTokenFromOffset(parser, context, offset);
        if (tokenOffset == null)
        {
            return null;
        }

        CompletionResult completionResult = getCompletionItems(tokenOffset);

        List<CompletionItem> clipboardCompletions = getClipboardCompletions(tokenOffset);

        if (!clipboardCompletions.isEmpty())
        {
            List<CompletionItem> items = new ArrayList<>(completionResult.getItems()
                    .size() + clipboardCompletions.size());
            items.addAll(clipboardCompletions);
            items.addAll(completionResult.getItems());
            completionResult = new CompletionResult(items, completionResult.isPartialResult());
        }

        return completionResult;
    }

    /** If we are positioned at an IN expression see if we have anything in Clipboard that can be completed at offset. */
    private List<CompletionItem> getClipboardCompletions(TokenOffset tokenOffset)
    {
        // Caret is between IN and next token => suggest
        if (tokenOffset.prevTree instanceof TerminalNode node
                && node.getSymbol()
                        .getType() == getInTokenId())
        {
            return getClipboardSqlInCompletionItems(false);
        }

        Token in = tokenOffset.tree instanceof TerminalNode node
                && node.getSymbol()
                        .getType() == getInTokenId() ? node.getSymbol()
                                : null;

        // Caret is on IN after N char => suggest
        // We don't want to suggest if the caret is between I and N
        if (in != null
                && tokenOffset.caretOffset > in.getStartIndex())
        {
            return getClipboardSqlInCompletionItems(true);
        }
        return emptyList();
    }

    private List<CompletionItem> getClipboardSqlInCompletionItems(boolean includeInToken)
    {
        Clipboard clipboard = Toolkit.getDefaultToolkit()
                .getSystemClipboard();
        DataFlavor[] flavors = clipboard.getAvailableDataFlavors();

        DataFlavor plainTextDf = null;
        List<CompletionItem> result = null;
        for (DataFlavor df : flavors)
        {
            if (plainTextDf == null
                    && String.class.equals(df.getRepresentationClass())
                    && (df.getMimeType()
                            .startsWith(TableTransferable.JAVA_SERIALIZED_OBJECT)
                            || df.getMimeType()
                                    .startsWith(TableTransferable.PLAIN_TEXT)))
            {
                plainTextDf = df;
            }

            try
            {
                if (df.getMimeType()
                        .startsWith(TableTransferable.MIME_TYPE_SQL_IN))
                {
                    if (result == null)
                    {
                        result = new ArrayList<>();
                    }

                    String data = String.valueOf(clipboard.getData(df));
                    result.add(new CompletionItem(List.of("in"), (includeInToken ? "in "
                            : "") + data, df.getHumanPresentableName()));
                }
            }
            catch (Exception e)
            {
                LOGGER.error("Error reading clipboard", e);
            }
        }

        // See if the plain text is a newline based text
        if (result == null
                && plainTextDf != null)
        {
            try
            {
                String data = String.valueOf(clipboard.getData(plainTextDf));
                if (TableTransferable.isSqlIn(data))
                {
                    result = new ArrayList<>();

                    String sqlIn = TableTransferable.getSqlIn(data, false);
                    String sqlInNewLine = TableTransferable.getSqlIn(data, true);

                    result.add(new CompletionItem(List.of("in"), (includeInToken ? "in "
                            : "") + sqlIn, TableTransferable.SQL_IN));
                    result.add(new CompletionItem(List.of("in"), (includeInToken ? "in "
                            : "") + sqlInNewLine, TableTransferable.SQL_IN_NEW_LINE));

                }
            }
            catch (Exception e)
            {
                LOGGER.error("Error reading clipboard", e);
            }
        }

        return result == null ? emptyList()
                : result;
    }

    @Override
    public LinkAction getLinkAction(int offset)
    {
        String url = connectionState.getJdbcConnection()
                .getJdbcURL();
        String database = connectionState.getDatabase();

        Set<Integer> tableSourceIndices = getTableSourceRuleIndices();
        Set<Integer> procedureFunctionIndices = getProcedureFunctionsRuleIndices();

        ParserRuleContext innerMostContext = getInnerMostContext(context, offset);
        while (innerMostContext != null)
        {
            Interval interval = null;
            Map<String, Object> model = null;
            boolean match = false;

            Set<ObjectType> objectTypes = emptySet();

            if (tableSourceIndices.contains(innerMostContext.getRuleIndex()))
            {
                Pair<Interval, ObjectName> tableSource = getTableSource(innerMostContext);
                if (tableSource == null)
                {
                    return null;
                }
                match = true;
                interval = tableSource.getKey();
                //@formatter:off
                model = Map.of(
                        "catalog", Objects.toString(tableSource.getValue().getCatalog(), ""),
                        "schema", Objects.toString(tableSource.getValue().getSchema(), ""),
                        "name", Objects.toString(tableSource.getValue().getName(), ""));
                //@formatter:on
                objectTypes = ObjectType.TABLE_SOURCE_TYPES;
            }
            else if (procedureFunctionIndices.contains(innerMostContext.getRuleIndex()))
            {
                Pair<Interval, ObjectName> procedureFunction = getProcedureFunction(innerMostContext);
                if (procedureFunction == null)
                {
                    return null;
                }
                match = true;
                interval = procedureFunction.getKey();
                //@formatter:off
                model = Map.of(
                        "catalog", Objects.toString(procedureFunction.getValue().getCatalog(), ""),
                        "schema", Objects.toString(procedureFunction.getValue().getSchema(), ""),
                        "name", Objects.toString(procedureFunction.getValue().getName(), ""));
                //@formatter:on
                objectTypes = ObjectType.FUNCTION_OR_PROCEDURE_TYPES;
            }

            if (match)
            {
                if (interval == null
                        || model == null)
                {
                    return null;
                }

                List<QueryActionResult> queryActions = queryActionsConfigurable.getQueryActions(url, database, ActionTarget.TEXT_EDITOR, ActionType.LINK, objectTypes);
                List<Action> actions = new ArrayList<>();
                for (QueryActionResult queryAction : queryActions)
                {
                    Action action = buildAction(queryAction, model);
                    if (action != null)
                    {
                        actions.add(action);
                    }
                }
                if (!actions.isEmpty())
                {
                    return new LinkAction(interval.a, interval.b, actions);
                }

                return null;
            }

            innerMostContext = innerMostContext.getParent();
        }

        return null;
    }

    @Override
    public ToolTipItem getToolTip(int offset)
    {
        Set<Integer> tableSourceIndices = getTableSourceRuleIndices();

        ParserRuleContext innerMostContext = getInnerMostContext(context, offset);
        while (innerMostContext != null)
        {
            if (tableSourceIndices.contains(innerMostContext.getRuleIndex()))
            {
                Pair<Interval, ObjectName> tableSource = getTableSource(innerMostContext);
                if (tableSource == null)
                {
                    return null;
                }

                Map<String, Object> model = getTableSourceTooltipModel(tableSource.getValue());
                if (model == null)
                {
                    return null;
                }

                return new ToolTipItem(tableSource.getKey().a, tableSource.getKey().b, templateService.process("TableSource: " + tableSource.getValue()
                        .toString(), TABLE_TEMPLATE, model));
            }
            // else if (procedureFunctionIndices.contains(innerMostContext.getRuleIndex()))
            // {
            // Pair<Interval, ObjectName> procedureFunction = getProcedureFunction(innerMostContext);
            // if (procedureFunction == null)
            // {
            // return null;
            // }
            // match = true;
            // interval = procedureFunction.getKey();
            // model = Map.of("catalog", Objects.toString(procedureFunction.getValue()
            // .getCatalog(), ""), "schema", Objects.toString(
            // procedureFunction.getValue()
            // .getSchema(),
            // ""),
            // "name", Objects.toString(procedureFunction.getValue()
            // .getName(), ""));
            // queryActions = queryActionsConfigurable.getQueryActions(jdbcDialect, ActionTarget.TEXT_EDITOR)
            // .stream()
            // .filter(qa -> qa.containsUserDefinedFunctionProcedure());
            // }

            innerMostContext = innerMostContext.getParent();
        }

        return null;
    }

    protected ParserRuleContext getInnerMostContext(ParserRuleContext ctx, int offset)
    {
        if (ctx == null)
        {
            return null;
        }

        List<ParseTree> queue = new ArrayList<>();
        queue.add(ctx);
        ParserRuleContext prev = null;
        while (!queue.isEmpty())
        {
            ParseTree current = queue.remove(0);

            if (current instanceof ParserRuleContext cctx
                    && cctx.start != null
                    && cctx.stop != null)
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

    /** Finds a token offset in parse tree from provided caret offset. Used for code completions because caret can positioned at a non existent token */
    static TokenOffset findTokenFromOffset(Parser parser, ParseTree tree, int offset)
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

                if (!isEOF
                        && offset >= start
                        && offset <= stop + 1)
                {
                    return new TokenOffset(offset, tokenIndex, node, prev);
                }
                // We passed the offset which means caret is at a hidden channel token
                // Find that token in the input stream
                else if (start > offset
                        || isEOF)
                {
                    // If EOF then use that token for search else
                    // find the actual token at the caret
                    Token tokenToSearch = isEOF ? node.getSymbol()
                            : findHiddenToken(parser, tokenIndex - 1, offset);

                    tokenIndex = tokenToSearch != null ? tokenToSearch.getTokenIndex()
                            : -1;
                    return new TokenOffset(offset, tokenIndex, node, prev);
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

    static Token findHiddenToken(Parser parser, int tokenIndex, int offset)
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

    protected record TokenOffset(int caretOffset, int suggestTokenIndex, ParseTree tree, ParseTree prevTree)
    {
    }

    private Action buildAction(QueryActionsConfigurable.QueryActionResult queryAction, Map<String, Object> model)
    {
        Action action = null;
        if (!queryAction.hasSubItems())
        {
            String query = queryAction.query();
            action = new AbstractAction(queryAction.title())
            {
                @Override
                public void actionPerformed(ActionEvent e)
                {
                    eventBus.publish(new ExecuteQueryEvent(queryAction.output(), new ExecuteQueryContext(templateService.process("AntlrDocumentParser", query, model))));
                }
            };
        }
        else
        {
            List<Action> subActions = new ArrayList<>(queryAction.subItems()
                    .size());
            for (QueryActionsConfigurable.QueryActionResult subQueryAction : queryAction.subItems())
            {
                Action subAction = buildAction(subQueryAction, model);
                if (subAction != null)
                {
                    subActions.add(subAction);
                }
            }
            if (!subActions.isEmpty())
            {
                action = new AbstractAction(queryAction.title())
                {
                    @Override
                    public void actionPerformed(ActionEvent e)
                    {
                    }
                };
                action.putValue(com.queryeer.api.action.Constants.SUB_ACTIONS, subActions);
            }
        }
        return action;
    }

    /** Result list with found aliases */
    record TableAlias(String alias, ObjectName objectName, List<String> extendedColumns, TableAliasType type)
    {
    }

    enum TableAliasType
    {
        TABLE,
        TEMPTABLE,
        TABLEVARIABLE,
        TABLE_FUNCTION,
        SUBQUERY,
        CHANGETABLE
    }
}
