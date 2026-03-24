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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;

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
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.queryeer.api.editor.ITextEditorDocumentParser;
import com.queryeer.api.event.ExecuteQueryEvent;
import com.queryeer.api.extensions.output.table.TableTransferable;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.ITemplateService;

import se.kuseman.payloadbuilder.catalog.Common;
import se.kuseman.payloadbuilder.catalog.jdbc.CatalogCrawlService;
import se.kuseman.payloadbuilder.catalog.jdbc.ExecuteQueryContext;
import se.kuseman.payloadbuilder.catalog.jdbc.IConnectionContext;
import se.kuseman.payloadbuilder.catalog.jdbc.Icons;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.QueryActionsConfigurable.ActionTarget;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.QueryActionsConfigurable.ActionType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.QueryActionsConfigurable.QueryActionResult;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.c3.CodeCompletionCore;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.c3.CodeCompletionCore.CandidatesCollection;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Catalog;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Column;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Constraint;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ForeignKey;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ForeignKeyColumn;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Index;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ObjectName;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ObjectType;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Routine;
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
    protected final IConnectionContext connectionContext;
    protected final ITemplateService templateService;
    protected final Icons icons;

    AntlrDocumentParser(IEventBus eventBus, QueryActionsConfigurable queryActionsConfigurable, CatalogCrawlService crawlService, IConnectionContext connectionContext, ITemplateService templateService,
            Icons icons)
    {
        this.eventBus = requireNonNull(eventBus, "eventBus");
        this.queryActionsConfigurable = requireNonNull(queryActionsConfigurable, "queryActionsConfigurable");
        this.crawlService = requireNonNull(crawlService, "crawlService");
        this.connectionContext = requireNonNull(connectionContext, "connectionContext");
        this.templateService = requireNonNull(templateService, "templateService");
        this.icons = requireNonNull(icons, "icons");
    }

    protected T context;
    protected Parser parser;
    protected CodeCompletionCore core;

    protected List<ParseItem> parseResult = new ArrayList<>();
    /**
     * All statement-level {@link ParserRuleContext} instances from the last full parse, collected in document order. Populated automatically when {@link #getStatementRuleIndex()} returns a
     * non-negative value. Used for offset-based fallback lookup in completions.
     */
    protected final List<ParserRuleContext> statementContexts = new ArrayList<>();

    protected abstract Lexer createLexer(CharStream charStream);

    protected abstract Parser createParser(TokenStream tokenStream);

    protected abstract T parse(Parser parser);

    @Override
    public void parse(Reader documentReader)
    {
        parseInternal(documentReader, true);
    }

    /**
     * Lightweight parse that skips {@link CodeCompletionCore} initialisation and {@link #afterParse()}. Use this when only structural extraction (e.g. referenced routines) is needed, not completions
     * or semantic validation.
     */
    void parseLight(Reader documentReader)
    {
        parseInternal(documentReader, false);
    }

    private void parseInternal(Reader documentReader, boolean full)
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
            if (full)
            {
                core = new CodeCompletionCore(parser, getCodeCompleteRuleIndices(), Set.of());
                statementContexts.clear();
                if (getStatementRuleIndex() >= 0)
                {
                    collectStatementContexts(context);
                }
                afterParse();
            }
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

    /** Return all table source ObjectNames referenced in the last parsed document. */
    List<ObjectName> getReferencedTableSources()
    {
        if (context == null)
        {
            return emptyList();
        }
        Set<Integer> tableSourceIndices = getTableSourceRuleIndices();
        if (tableSourceIndices.isEmpty())
        {
            return emptyList();
        }
        List<ObjectName> result = new ArrayList<>();
        collectTableSources(context, tableSourceIndices, result);
        return result;
    }

    /** Return all routine ObjectNames called in the last parsed document. */
    List<ObjectName> getReferencedRoutines()
    {
        if (context == null)
        {
            return emptyList();
        }
        Set<Integer> routineIndices = getProcedureFunctionsRuleIndices();
        if (routineIndices.isEmpty())
        {
            return emptyList();
        }
        List<ObjectName> result = new ArrayList<>();
        collectRoutineCalls(context, routineIndices, result);
        return result;
    }

    private void collectRoutineCalls(ParseTree tree, Set<Integer> indices, List<ObjectName> result)
    {
        if (tree instanceof ParserRuleContext ctx
                && indices.contains(ctx.getRuleIndex()))
        {
            // Skip the procedure/function name appearing in its own CREATE/ALTER declaration
            boolean isDefinition = ctx.parent instanceof ParserRuleContext parentCtx
                    && isRoutineDefinitionContext(parentCtx);
            if (!isDefinition)
            {
                Pair<Interval, ObjectName> routine = getProcedureFunction(ctx);
                if (routine != null
                        && routine.getValue() != null)
                {
                    result.add(routine.getValue());
                    return;
                }
            }
            return;
        }
        for (int i = 0; i < tree.getChildCount(); i++)
        {
            collectRoutineCalls(tree.getChild(i), indices, result);
        }
    }

    /**
     * Returns true if {@code ctx} is a routine-definition context (e.g. CREATE/ALTER PROCEDURE or FUNCTION) rather than a call site. Dialects that use the same grammar rule for both the declaration
     * name and call sites must override this to prevent the routine's own name from appearing as a self-call.
     */
    protected boolean isRoutineDefinitionContext(ParserRuleContext ctx)
    {
        return false;
    }

    private void collectTableSources(ParseTree tree, Set<Integer> indices, List<ObjectName> result)
    {
        if (tree instanceof ParserRuleContext ctx
                && indices.contains(ctx.getRuleIndex()))
        {
            Pair<Interval, ObjectName> ts = getTableSource(ctx);
            if (ts != null
                    && ts.getValue() != null)
            {
                result.add(ts.getValue());
                return; // leaf table reference – don't recurse further
            }
            // No ObjectName (e.g. subquery context) – fall through and recurse
        }
        for (int i = 0; i < tree.getChildCount(); i++)
        {
            collectTableSources(tree.getChild(i), indices, result);
        }
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
     * Returns the ANTLR rule index for the grammar rule that represents a single top-level statement (e.g. {@code RULE_sql_clauses} for T-SQL). When non-negative, the base class automatically
     * collects all matching nodes into {@link #statementContexts} after each full parse, enabling the offset-based fallback helpers. Return {@code -1} (default) to opt out.
     */
    protected int getStatementRuleIndex()
    {
        return -1;
    }

    /**
     * Returns the ANTLR token type used as an explicit statement separator (e.g. SEMI), or {@code -1} if the grammar has no separator token. Used by {@link #findStatementContextByTokenScan}.
     */
    protected int getStatementSeparatorTokenType()
    {
        return -1;
    }

    /**
     * Returns the set of ANTLR token types that unambiguously begin a new statement (e.g. SELECT, INSERT). Used by {@link #findStatementContextByTokenScan} as a last-resort boundary when no separator
     * token is found. Return an empty set (default) to disable keyword scanning.
     */
    protected Set<Integer> getStatementStartTokenTypes()
    {
        return emptySet();
    }

    /**
     * Re-parses the token range covered by {@code statementCtx} in isolation and returns the parse-tree node nearest to {@code caretCharOffset} in the fresh (error-free) tree. Implement this in
     * dialect subclasses that have access to the dialect-specific lexer/parser types. The default returns {@code null} (no mini-parse fallback).
     */
    protected ParseTree miniParseStatementNode(ParserRuleContext statementCtx, int caretCharOffset)
    {
        return null;
    }

    /**
     * Returns the effective tree node for completion context resolution. Prefers {@link TokenOffset#tree()} but falls back to {@link TokenOffset#prevTree()} when the current node is an EOF token, an
     * {@link ErrorNode} (which happens when the caret is past the last real token or inside a parse-error recovery node), or a visible token whose start index is past the caret (which happens when
     * the caret is in a hidden-channel gap between two statements — the next statement's first token is returned by findTokenFromOffset but is the wrong context anchor).
     */
    protected ParseTree resolveEffectiveNode(TokenOffset tokenOffset)
    {
        ParseTree node = tokenOffset.tree();
        if ((node instanceof TerminalNode tn
                && tn.getSymbol()
                        .getType() == Token.EOF)
                || node instanceof ErrorNode)
        {
            return tokenOffset.prevTree();
        }
        // When the caret is in a hidden-channel gap (whitespace/newline) before this token,
        // the token's start index is past the caret. Use prevTree so that statement-context
        // detection anchors to the preceding token (e.g. WHERE or FROM of the current statement)
        // rather than the first token of the next statement.
        if (node instanceof TerminalNode tn
                && tn.getSymbol()
                        .getStartIndex() > tokenOffset.caretOffset())
        {
            return tokenOffset.prevTree();
        }
        return node;
    }

    /**
     * Walks up the parse tree from {@code tree} to find the nearest enclosing statement context (the rule identified by {@link #getStatementRuleIndex()}). Fast path: works correctly when the tree
     * structure is intact. Returns {@code null} when error recovery has restructured the tree so that the node is not attached inside a statement context.
     */
    protected ParserRuleContext findEnclosingStatementCtx(ParseTree tree)
    {
        int ruleIndex = getStatementRuleIndex();
        if (ruleIndex < 0)
        {
            return null;
        }
        ParseTree current = tree;
        while (current != null)
        {
            if (current instanceof ParserRuleContext ctx
                    && ctx.getRuleIndex() == ruleIndex)
            {
                return ctx;
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * Fallback for when {@link #findEnclosingStatementCtx} returns {@code null}. Searches {@link #statementContexts} — collected at parse time — for the statement context whose start character offset
     * is the highest value that does not exceed {@code caretCharOffset}.
     */
    protected ParserRuleContext findNearestPrecedingStatementCtx(int caretCharOffset)
    {
        ParserRuleContext best = null;
        for (ParserRuleContext ctx : statementContexts)
        {
            int start = ctx.start.getStartIndex();
            if (start <= caretCharOffset
                    && (best == null
                            || start > best.start.getStartIndex()))
            {
                best = ctx;
            }
        }
        return best;
    }

    /**
     * Last-resort fallback when both tree-walking and the pre-collected list fail. Scans the token stream backwards from {@code caretTokenIndex} looking for a separator token
     * ({@link #getStatementSeparatorTokenType()}) or a statement-start keyword ({@link #getStatementStartTokenTypes()}). Returns a synthetic {@link ParserRuleContext} whose {@code start} token is set
     * to the found boundary, or {@code null} when neither hook returns useful values.
     */
    protected ParserRuleContext findStatementContextByTokenScan(int caretTokenIndex)
    {
        if (parser == null)
        {
            return null;
        }
        int separatorType = getStatementSeparatorTokenType();
        Set<Integer> startTypes = getStatementStartTokenTypes();
        if (separatorType < 0
                && startTypes.isEmpty())
        {
            return null;
        }
        TokenStream tokenStream = parser.getInputStream();
        int statementStartIdx = 0;
        for (int i = caretTokenIndex - 1; i >= 0; i--)
        {
            int type = tokenStream.get(i)
                    .getType();
            if (separatorType >= 0
                    && type == separatorType)
            {
                statementStartIdx = i + 1;
                break;
            }
            if (startTypes.contains(type))
            {
                statementStartIdx = i;
                break;
            }
        }
        ParserRuleContext synth = new ParserRuleContext();
        synth.start = tokenStream.get(statementStartIdx);
        return synth;
    }

    private void collectStatementContexts(ParseTree tree)
    {
        int ruleIndex = getStatementRuleIndex();
        if (tree instanceof ParserRuleContext ctx
                && ctx.getRuleIndex() == ruleIndex
                && ctx.start != null)
        {
            statementContexts.add(ctx);
        }
        for (int i = 0; i < tree.getChildCount(); i++)
        {
            collectStatementContexts(tree.getChild(i));
        }
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

    /**
     * Context returned by {@link #detectJoinOnContext} when the caret is positioned immediately after the {@code ON} keyword of a JOIN clause. Carries the right-hand-side table alias (the table being
     * joined in the current JOIN) and the full set of all table aliases visible at the caret position.
     */
    record JoinOnContext(TableAlias rhsAlias, Set<TableAlias> allAliases)
    {
    }

    /**
     * Detects whether the caret is positioned immediately after the {@code ON} keyword of a JOIN clause. When detected, returns a {@link JoinOnContext} carrying the right-hand-side table alias and
     * all table aliases visible at the caret. Returns {@code null} when the caret is not in this context.
     *
     * <p>
     * The default implementation always returns {@code null}. Dialect subclasses that can detect this context should override this method.
     * </p>
     */
    protected JoinOnContext detectJoinOnContext(TokenOffset tokenOffset, String database)
    {
        return null;
    }

    /**
     * Generates FK/PK join-condition suggestions for the given {@link JoinOnContext}. For each foreign-key relationship that links the RHS table to any visible LHS table, produces one
     * {@link CompletionItem} per FK: single-column FKs become {@code b.fkCol = a.pkCol}; composite FKs become {@code b.fkCol1 = a.pkCol1 AND b.fkCol2 = a.pkCol2}.
     *
     * <p>
     * Returns an empty {@link CompletionResult} (not {@code null}) when no FK relationships are found, so callers can fall through to regular column suggestions.
     * </p>
     */
    protected final CompletionResult suggestFkJoinConditions(JoinOnContext joinCtx, Catalog catalog, Icon icon)
    {
        TableAlias rhs = joinCtx.rhsAlias();
        List<TableAlias> lhsAliases = joinCtx.allAliases()
                .stream()
                .filter(ta -> !ta.equals(rhs))
                .collect(Collectors.toList());

        List<CompletionItem> items = new ArrayList<>();
        for (ForeignKey fk : catalog.getForeignKeys())
        {
            if (fk.getColumns()
                    .isEmpty())
            {
                continue;
            }
            ObjectName constrainedName = fk.getColumns()
                    .get(0)
                    .getConstrainedObjectName();
            ObjectName referencedName = fk.getColumns()
                    .get(0)
                    .getReferencedObjectName();

            boolean rhsIsConstrained = joinTableNameMatches(constrainedName, rhs.objectName());
            boolean rhsIsReferenced = !rhsIsConstrained
                    && joinTableNameMatches(referencedName, rhs.objectName());

            for (TableAlias lhs : lhsAliases)
            {
                if (rhsIsConstrained
                        && joinTableNameMatches(referencedName, lhs.objectName()))
                {
                    // RHS has the FK column; LHS has the referenced PK: rhs.fkCol = lhs.pkCol
                    String condition = buildJoinCondition(fk.getColumns(), rhs.alias(), lhs.alias());
                    items.add(new CompletionItem(List.of(condition), condition, null, null, icon, 10));
                }
                else if (rhsIsReferenced
                        && joinTableNameMatches(constrainedName, lhs.objectName()))
                {
                    // LHS has the FK column; RHS has the referenced PK: lhs.fkCol = rhs.pkCol
                    String condition = buildJoinCondition(fk.getColumns(), lhs.alias(), rhs.alias());
                    items.add(new CompletionItem(List.of(condition), condition, null, null, icon, 10));
                }
            }
        }
        return new CompletionResult(items, false);
    }

    /**
     * Returns {@code true} when {@code fkName} and {@code aliasName} refer to the same table. Matches on table name (case-insensitive) and, when both sides have a non-blank schema, also on schema.
     * Catalog is deliberately ignored to allow cross-database alias matching.
     */
    private static boolean joinTableNameMatches(ObjectName fkName, ObjectName aliasName)
    {
        if (!fkName.getName()
                .equalsIgnoreCase(aliasName.getName()))
        {
            return false;
        }
        String fkSchema = fkName.getSchema();
        String aliasSchema = aliasName.getSchema();
        if (fkSchema != null
                && !fkSchema.isEmpty()
                && aliasSchema != null
                && !aliasSchema.isEmpty())
        {
            return fkSchema.equalsIgnoreCase(aliasSchema);
        }
        return true;
    }

    /**
     * Builds a join condition string from FK column mappings. The constrained side (FK column) is prefixed with {@code constrainedAlias} and the referenced side (PK column) with
     * {@code referencedAlias}. Multi-column FKs are joined with {@code AND}.
     */
    private static String buildJoinCondition(List<ForeignKeyColumn> columns, String constrainedAlias, String referencedAlias)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < columns.size(); i++)
        {
            if (i > 0)
            {
                sb.append(" AND ");
            }
            ForeignKeyColumn col = columns.get(i);
            String fkPart = constrainedAlias == null
                    || constrainedAlias.isEmpty() ? col.getConstrainedColumnName()
                            : constrainedAlias + "." + col.getConstrainedColumnName();
            String pkPart = referencedAlias == null
                    || referencedAlias.isEmpty() ? col.getReferencedColumnName()
                            : referencedAlias + "." + col.getReferencedColumnName();
            sb.append(fkPart)
                    .append(" = ")
                    .append(pkPart);
        }
        return sb.toString();
    }

    /**
     * Return completion items from the provided token offset. The default implementation drives the common completion flow and delegates to protected hook methods. Subclasses may override individual
     * hooks instead of replacing the whole flow, which makes it easy to support only the parts of completion that are relevant for a given dialect.
     * <ol>
     * <li>{@link #getDialectSpecificCompletionItems} — early dialect-specific exit (e.g. stored-procedure parameters)</li>
     * <li>FK join-condition suggestions via {@link #detectJoinOnContext} + {@link #getFkJoinIcon}</li>
     * <li>Expression / table-source context detection via {@link #isExpressionContext} / {@link #isTableSourceContext} → {@link #suggestColumns} / {@link #suggestTableSources}</li>
     * <li>Statement-context resolution (base-class helpers)</li>
     * <li>{@link #getExpressionFallbackColumns} — dialect-specific pre-C3 column fallback</li>
     * <li>C3 candidate collection with token-index advancement</li>
     * <li>{@link #getC3CompletionItems} — dispatch C3 candidates to dialect-specific suggestions</li>
     * </ol>
     */
    protected CompletionResult getCompletionItems(TokenOffset tokenOffset)
    {
        // 1. Dialect-specific early completion (e.g. stored-procedure parameters)
        CompletionResult dialectResult = getDialectSpecificCompletionItems(tokenOffset);
        if (dialectResult != null)
        {
            return dialectResult;
        }

        // 2. JOIN ON FK suggestions (only when the dialect detects a JOIN ON context)
        JoinOnContext joinOnCtx = detectJoinOnContext(tokenOffset, connectionContext.getDatabase());
        if (joinOnCtx != null)
        {
            Catalog fkCatalog = crawlService.getCatalog(connectionContext, connectionContext.getDatabase());
            if (fkCatalog != null)
            {
                CompletionResult fkResult = suggestFkJoinConditions(joinOnCtx, fkCatalog, icons.keyIcon);
                if (!fkResult.getItems()
                        .isEmpty())
                {
                    return fkResult;
                }
            }
        }

        // 3. Expression / table-source context detection → column / table suggestions
        ParseTree effectiveNode = resolveEffectiveNode(tokenOffset);
        boolean expressionContextDetected = false;
        if (isExpressionContext(tokenOffset.tree()))
        {
            expressionContextDetected = true;
            // Use effectiveNode rather than tokenOffset.tree() so that when the caret sits in
            // a hidden-channel gap (e.g. "WHERE |)" inside an EXISTS subquery) and tree() is
            // the closing ')' — which is a sibling of the SubqueryContext, not inside it —
            // collectTableSourceAliases starts from the correct inner anchor (the WHERE keyword)
            // and finds both inner and outer aliases. When tree() == effectiveNode (the common
            // case) the behaviour is identical to before.
            CompletionResult result = suggestColumns(effectiveNode);
            if (result != null)
            {
                return result;
            }
        }
        else if (isExpressionContext(tokenOffset.prevTree()))
        {
            expressionContextDetected = true;
            CompletionResult result = suggestColumns(tokenOffset.prevTree());
            if (result != null)
            {
                return result;
            }
        }
        else if (isTableSourceContext(tokenOffset.tree())
                || isTableSourceContext(tokenOffset.prevTree()))
        {
            return suggestTableSources();
        }

        // 4. Find enclosing statement context (base-class helpers)
        ParserRuleContext statementCtx = findEnclosingStatementCtx(effectiveNode);
        if (statementCtx == null)
        {
            statementCtx = findNearestPrecedingStatementCtx(tokenOffset.caretOffset());
        }
        if (statementCtx == null)
        {
            statementCtx = findStatementContextByTokenScan(tokenOffset.suggestTokenIndex());
        }

        // 5. Dialect-specific expression fallback (e.g. query-spec column suggestions triggered by operators)
        if (parser != null
                && tokenOffset.suggestTokenIndex() >= 0)
        {
            CompletionResult exprFallback = getExpressionFallbackColumns(tokenOffset, statementCtx, expressionContextDetected);
            if (exprFallback != null)
            {
                return exprFallback;
            }
        }

        // 6. Advance C3 token index past whitespace gaps
        int c3TokenIndex = tokenOffset.suggestTokenIndex();
        if (c3TokenIndex >= 0
                && parser != null)
        {
            Token suggestToken = parser.getInputStream()
                    .get(c3TokenIndex);
            if (suggestToken.getStopIndex() < tokenOffset.caretOffset())
            {
                int nextIdx = c3TokenIndex + 1;
                while (nextIdx < parser.getInputStream()
                        .size())
                {
                    Token next = parser.getInputStream()
                            .get(nextIdx);
                    if (next.getChannel() == Token.DEFAULT_CHANNEL)
                    {
                        if (next.getType() != Token.EOF)
                        {
                            c3TokenIndex = nextIdx;
                        }
                        break;
                    }
                    nextIdx++;
                }
            }
        }

        // 7. C3 candidate collection and dispatch
        if (core == null
                || c3TokenIndex < 0)
        {
            return null;
        }
        CandidatesCollection candidates = core.collectCandidates(c3TokenIndex, statementCtx);
        if (candidates.rules.isEmpty())
        {
            return null;
        }

        return getC3CompletionItems(candidates, effectiveNode, statementCtx, tokenOffset);
    }

    @Override
    public CompletionResult getCompletionItems(int offset)
    {
        TokenOffset tokenOffset = findTokenFromOffset(parser, context, offset);
        if (tokenOffset == null)
        {
            return null;
        }

        CompletionResult completionResult = ObjectUtils.getIfNull(getCompletionItems(tokenOffset), CompletionResult.EMPTY);

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

    /**
     * Called at the start of the completion flow. Return a non-{@code null} result to short-circuit the common flow with a dialect-specific suggestion (e.g. stored-procedure parameter names). Return
     * {@code null} to continue with the standard flow.
     */
    protected CompletionResult getDialectSpecificCompletionItems(TokenOffset tokenOffset)
    {
        return null;
    }

    /**
     * Returns {@code true} when {@code tree} (or any of its ancestors) is an expression context in the dialect's grammar. Used to decide whether column suggestions are appropriate at the caret
     * position. The default returns {@code false}.
     */
    protected boolean isExpressionContext(ParseTree tree)
    {
        return false;
    }

    /**
     * Returns {@code true} when {@code tree} (or any of its ancestors) is a table-source context in the dialect's grammar. Used to decide whether table-source suggestions are appropriate at the caret
     * position. The default returns {@code false}.
     */
    protected boolean isTableSourceContext(ParseTree tree)
    {
        return false;
    }

    /**
     * Returns column-completion suggestions for the given parse-tree node, or {@code null} when no column suggestions can be determined (e.g. no table-source aliases are visible). The default returns
     * {@code null}.
     */
    protected CompletionResult suggestColumns(ParseTree node)
    {
        return null;
    }

    /**
     * Returns table-source completion suggestions, or {@code null} when not supported by this dialect. The default returns {@code null}.
     */
    protected CompletionResult suggestTableSources()
    {
        return null;
    }

    /**
     * Called before C3 when {@code parser != null} and a valid suggest-token index is available. Allows dialects to apply grammar-specific column-suggestion fallbacks triggered by operator context
     * (e.g. the caret follows {@code =} or {@code AND}). Return a non-{@code null} result to short-circuit C3; return {@code null} to continue.
     *
     * @param expressionContextDetected {@code true} when {@link #isExpressionContext} fired above but {@link #suggestColumns} returned {@code null}
     */
    protected CompletionResult getExpressionFallbackColumns(TokenOffset tokenOffset, ParserRuleContext statementCtx, boolean expressionContextDetected)
    {
        return null;
    }

    /**
     * Called after C3 candidate collection when {@code candidates.rules} is non-empty. Dispatches the candidates to dialect-specific suggestions (columns, tables, routines, etc.). Return {@code null}
     * when no suggestions can be produced.
     */
    protected CompletionResult getC3CompletionItems(CandidatesCollection candidates, ParseTree effectiveNode, ParserRuleContext statementCtx, TokenOffset tokenOffset)
    {
        return null;
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
        String url = connectionContext.getJdbcConnection()
                .getJdbcURL();
        String database = connectionContext.getDatabase();

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

        Deque<ParseTree> queue = new ArrayDeque<>();
        queue.add(ctx);
        // Only updated when offset is actually within the context, so the final value
        // is always the deepest context that contains the offset (BUG FIX: was set
        // unconditionally, causing siblings to overwrite the correct innermost context).
        ParserRuleContext prev = null;
        while (!queue.isEmpty())
        {
            ParseTree current = queue.removeFirst();

            if (current instanceof ParserRuleContext cctx
                    && cctx.start != null
                    && cctx.stop != null
                    && offset >= cctx.start.getStartIndex()
                    && offset < cctx.stop.getStopIndex())
            {
                prev = cctx;
                int count = current.getChildCount();
                for (int i = 0; i < count; i++)
                {
                    queue.addLast(current.getChild(i));
                }
            }
        }

        return prev;
    }

    /** Finds a token offset in parse tree from provided caret offset. Used for code completions because caret can positioned at a non existent token */
    static TokenOffset findTokenFromOffset(Parser parser, ParseTree tree, int offset)
    {
        if (tree == null)
        {
            return null;
        }
        // Use ArrayDeque for O(1) addFirst/removeFirst (ArrayList.add(0)/remove(0) are O(n))
        Deque<ParseTree> queue = new ArrayDeque<>();
        queue.add(tree);

        TerminalNode prev = null;

        while (!queue.isEmpty())
        {
            ParseTree current = queue.removeFirst();
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
                    queue.addFirst(current.getChild(i));
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

    @Override
    public SignatureHint getSignatureHint(int offset)
    {
        if (parser == null)
        {
            return null;
        }
        TokenStream ts = parser.getInputStream();
        if (ts == null
                || ts.size() == 0)
        {
            return null;
        }

        // Find the last default-channel token whose stop index is strictly before the caret.
        // The '(' the user just typed is not yet in the token stream (parse reflects previous state).
        int nameIdx = -1;
        for (int i = ts.size() - 1; i >= 0; i--)
        {
            Token t = ts.get(i);
            if (t.getType() == Token.EOF)
            {
                continue;
            }
            if (t.getStopIndex() < offset
                    && t.getChannel() == Token.DEFAULT_CHANNEL)
            {
                nameIdx = i;
                break;
            }
        }
        if (nameIdx < 0)
        {
            return null;
        }

        String functionName = stripBrackets(ts.get(nameIdx)
                .getText());

        // 1. Dialect-specific built-in functions (e.g. ISNULL, CONVERT, DATEADD)
        SignatureHint hint = getBuiltinSignatureHint(functionName);
        if (hint != null)
        {
            return hint;
        }

        // 2. Check for schema qualification: scan for DOT + schema token before the name
        String schema = null;
        int dotIdx = prevDefaultChannelToken(ts, nameIdx - 1);
        if (dotIdx >= 0
                && ".".equals(ts.get(dotIdx)
                        .getText()))
        {
            int schemaIdx = prevDefaultChannelToken(ts, dotIdx - 1);
            if (schemaIdx >= 0)
            {
                schema = stripBrackets(ts.get(schemaIdx)
                        .getText());
            }
        }

        // 3. Look up in catalog (procedures and scalar functions)
        Catalog catalog = crawlService.getCatalog(connectionContext, connectionContext.getDatabase());
        if (catalog == null)
        {
            return null;
        }

        final String schemaFinal = schema;
        Routine routine = catalog.getRoutines()
                .stream()
                .filter(r -> r.getName()
                        .equalsIgnoreCase(functionName)
                        && (schemaFinal == null
                                || r.getSchema()
                                        .equalsIgnoreCase(schemaFinal)))
                .findFirst()
                .orElse(null);

        if (routine == null
                || routine.getParameters()
                        .isEmpty())
        {
            return null;
        }

        List<SignatureParam> params = routine.getParameters()
                .stream()
                .map(rp -> new SignatureParam(rp.getName(), Column.getDefinition(rp.getType(), rp.getMaxLength(), rp.getPrecision(), rp.getScale(), rp.isNullable())))
                .collect(java.util.stream.Collectors.toList());

        return new SignatureHint(routine.getName(), params, null);
    }

    /**
     * Returns a {@link SignatureHint} for the given function name if it is a dialect-specific built-in, or {@code null} to fall through to catalog lookup. Subclasses that support built-in hints
     * should override this method.
     */
    protected SignatureHint getBuiltinSignatureHint(String functionName)
    {
        return null;
    }

    /** Returns the index of the previous default-channel token at or before {@code fromIdx}, or {@code -1} if none. */
    protected static int prevDefaultChannelToken(TokenStream ts, int fromIdx)
    {
        for (int i = fromIdx; i >= 0; i--)
        {
            if (ts.get(i)
                    .getChannel() == Token.DEFAULT_CHANNEL)
            {
                return i;
            }
        }
        return -1;
    }

    /** Strips surrounding square-bracket or double-quote delimiters from an identifier token's text. */
    protected static String stripBrackets(String text)
    {
        if (text == null
                || text.isEmpty())
        {
            return text;
        }
        if ((text.startsWith("[")
                && text.endsWith("]"))
                || (text.startsWith("\"")
                        && text.endsWith("\"")))
        {
            return text.substring(1, text.length() - 1);
        }
        return text;
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
        CHANGETABLE,
        BUILTIN
    }
}
