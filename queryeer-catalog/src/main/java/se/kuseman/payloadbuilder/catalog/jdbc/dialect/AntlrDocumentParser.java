package se.kuseman.payloadbuilder.catalog.jdbc.dialect;

import static java.util.Objects.requireNonNull;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

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

import com.queryeer.api.editor.ITextEditorDocumentParser;
import com.queryeer.api.event.ExecuteQueryEvent;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.ITemplateService;
import com.vmware.antlr4c3.CodeCompletionCore;

import se.kuseman.payloadbuilder.catalog.Common;
import se.kuseman.payloadbuilder.catalog.jdbc.CatalogCrawlService;
import se.kuseman.payloadbuilder.catalog.jdbc.ExecuteQueryContext;
import se.kuseman.payloadbuilder.catalog.jdbc.IConnectionState;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.QueryActionsConfigurable.ActionTarget;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.QueryActionsConfigurable.ActionType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.QueryActionsConfigurable.QueryAction;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Constraint;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ForeignKey;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Index;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ObjectName;
import se.kuseman.payloadbuilder.catalog.jdbc.model.TableSource;

/** Parser for antlr based database implementations */
abstract class AntlrDocumentParser<T extends ParserRuleContext> implements ITextEditorDocumentParser
{
    private static final String TABLE_TEMPLATE = Common.readResource("/se/kuseman/payloadbuilder/catalog/jdbc/templates/Table.html");
    protected static final String INDICES = "indices";
    protected static final String FOREIGN_KEYS = "foreignKeys";
    protected static final String CONSTRAINTS = "constraints";
    protected static final String TABLE = "table";
    private final JdbcDatabase jdbcDatabase;
    private final IEventBus eventBus;
    private final QueryActionsConfigurable queryActionsConfigurable;
    protected final CatalogCrawlService crawlService;
    protected final IConnectionState connectionState;
    protected final ITemplateService templateService;

    AntlrDocumentParser(JdbcDatabase jdbcDatabase, IEventBus eventBus, QueryActionsConfigurable queryActionsConfigurable, CatalogCrawlService crawlService, IConnectionState connectionState,
            ITemplateService templateService)
    {
        this.jdbcDatabase = requireNonNull(jdbcDatabase, "jdbcDatabase");
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
        return getCompletionItems(tokenOffset);
    }

    @Override
    public LinkAction getLinkAction(int offset)
    {
        Set<Integer> tableSourceIndices = getTableSourceRuleIndices();
        Set<Integer> procedureFunctionIndices = getProcedureFunctionsRuleIndices();

        ParserRuleContext innerMostContext = getInnerMostContext(context, offset);
        while (innerMostContext != null)
        {
            Stream<QueryAction> queryActions = null;
            Interval interval = null;
            Map<String, Object> model = null;
            boolean match = false;

            if (tableSourceIndices.contains(innerMostContext.getRuleIndex()))
            {
                Pair<Interval, ObjectName> tableSource = getTableSource(innerMostContext);
                if (tableSource == null)
                {
                    return null;
                }
                match = true;
                interval = tableSource.getKey();
                model = Map.of("catalog", Objects.toString(tableSource.getValue()
                        .getCatalog(), ""), "schema", Objects.toString(
                                tableSource.getValue()
                                        .getSchema(),
                                ""),
                        "name", Objects.toString(tableSource.getValue()
                                .getName(), ""));
                queryActions = queryActionsConfigurable.getQueryActions(jdbcDatabase, ActionTarget.TEXT_EDITOR)
                        .stream()
                        .filter(qa -> qa.containsTableSource());
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
                model = Map.of("catalog", Objects.toString(procedureFunction.getValue()
                        .getCatalog(), ""), "schema", Objects.toString(
                                procedureFunction.getValue()
                                        .getSchema(),
                                ""),
                        "name", Objects.toString(procedureFunction.getValue()
                                .getName(), ""));
                queryActions = queryActionsConfigurable.getQueryActions(jdbcDatabase, ActionTarget.TEXT_EDITOR)
                        .stream()
                        .filter(qa -> qa.containsUserDefinedFunctionProcedure());
            }

            if (match)
            {
                if (interval == null
                        || model == null)
                {
                    return null;
                }

                List<Action> actions = new ArrayList<>();

                final Map<String, Object> m = model;
                queryActions.filter(qa -> qa.getType() == ActionType.LINK)
                        .forEach(qa -> actions.add(buildAction(qa, m)));

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
            // queryActions = queryActionsConfigurable.getQueryActions(jdbcDatabase, ActionTarget.TEXT_EDITOR)
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
                    return new TokenOffset(tokenIndex, node, prev);
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
                    return new TokenOffset(tokenIndex, node, prev);
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

    protected record TokenOffset(int suggestTokenIndex, ParseTree tree, ParseTree prevTree)
    {
    }

    private Action buildAction(QueryActionsConfigurable.QueryAction queryAction, Map<String, Object> model)
    {
        Action action;
        if (queryAction.getSubItems()
                .isEmpty())
        {
            String query = queryAction.getQuery();
            action = new AbstractAction(queryAction.getTitle())
            {
                @Override
                public void actionPerformed(ActionEvent e)
                {
                    eventBus.publish(new ExecuteQueryEvent(queryAction.getOutput(), new ExecuteQueryContext(templateService.process("AntlrDocumentParser", query, model))));
                }
            };
        }
        else
        {
            action = new AbstractAction(queryAction.getTitle())
            {
                @Override
                public void actionPerformed(ActionEvent e)
                {
                }
            };

            List<Action> subActions = new ArrayList<>(queryAction.getSubItems()
                    .size());
            for (QueryActionsConfigurable.QueryAction subQueryAction : queryAction.getSubItems())
            {
                Action subAction = buildAction(subQueryAction, model);
                if (subAction != null)
                {
                    subActions.add(subAction);
                }
            }
            if (!subActions.isEmpty())
            {
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
