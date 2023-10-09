package se.kuseman.payloadbuilder.catalog.jdbc.dialect;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.lang3.tuple.Pair;

import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.ITemplateService;
import com.vmware.antlr4c3.CodeCompletionCore.CandidatesCollection;

import se.kuseman.payloadbuilder.api.QualifiedName;
import se.kuseman.payloadbuilder.catalog.jdbc.CatalogCrawlService;
import se.kuseman.payloadbuilder.catalog.jdbc.IConnectionState;
import se.kuseman.payloadbuilder.catalog.jdbc.Icons;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Catalog;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Column;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Constraint;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ForeignKey;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Index;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ObjectName;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Routine;
import se.kuseman.payloadbuilder.catalog.jdbc.model.TableSource;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlLexer;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Column_definitionContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Create_tableContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Ddl_objectContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Declare_statementContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Delete_statementContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.ExpressionContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Expression_elemContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Full_table_nameContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Func_proc_name_database_schemaContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Func_proc_name_schemaContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Func_proc_name_server_database_schemaContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Id_Context;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Join_partContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.PredicateContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Query_specificationContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.SCALAR_FUNCTIONContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Search_conditionContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Select_statementContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Sql_clausesContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Sql_unionContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.SubqueryContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Table_nameContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Table_sourceContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Table_source_itemContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Tsql_fileContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Update_statementContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParserBaseVisitor;

/** Document parser for Microsoft sql server */
class SqlServerDocumentParser extends AntlrDocumentParser<Tsql_fileContext>
{
    private final Icons icons;
    private final Map<String, List<String>> temporaryTables = new HashMap<>();

    SqlServerDocumentParser(Icons icons, IEventBus eventBus, QueryActionsConfigurable queryActionsConfigurable, CatalogCrawlService catalogCrawler, IConnectionState connectionState,
            ITemplateService templateService)
    {
        super(eventBus, queryActionsConfigurable, catalogCrawler, connectionState, templateService);
        this.icons = icons;
    }

    @Override
    protected Lexer createLexer(CharStream charStream)
    {
        return new TSqlLexer(charStream);
    }

    @Override
    protected Parser createParser(TokenStream tokenStream)
    {
        return new TSqlParser(tokenStream);
    }

    @Override
    protected Tsql_fileContext parse(Parser parser)
    {
        return ((TSqlParser) parser).tsql_file();
    }

    @Override
    protected void afterParse()
    {
        temporaryTables.clear();
        if (context != null)
        {
            ValidateVisitor visitor = new ValidateVisitor(temporaryTables, parseResult, connectionState, crawlService);
            context.accept(visitor);
        }
    }

    @Override
    public boolean supportsLinkActions()
    {
        return true;
    }

    @Override
    public boolean supportsToolTips()
    {
        return true;
    }

    @Override
    public boolean supportsCompletions()
    {
        return true;
    }

    @Override
    protected Map<String, Object> getTableSourceTooltipModel(ObjectName name)
    {
        // TODO: create model for temporary tables
        String database = Objects.toString(name.getCatalog(), connectionState.getDatabase());
        Catalog catalog = crawlService.getCatalog(connectionState, database);
        if (catalog == null)
        {
            return null;
        }

        Predicate<ObjectName> objectNameFilter = t -> (isBlank(name.getSchema())
                || name.getSchema()
                        .equalsIgnoreCase(t.getSchema()))
                && t.getName()
                        .equalsIgnoreCase(name.getName());

        TableSource table = catalog.getTableSources()
                .stream()
                .filter(objectNameFilter)
                .findAny()
                .orElse(null);

        if (table == null)
        {
            return null;
        }

        List<Constraint> constraints = catalog.getConstraints()
                .stream()
                .filter(c -> objectNameFilter.test(c.getObjectName()))
                .collect(toList());
        List<ForeignKey> foreignKeys = catalog.getForeignKeys()
                .stream()
                .filter(fk -> objectNameFilter.test(fk.getObjectName()))
                .collect(toList());
        List<Index> indices = catalog.getIndices()
                .stream()
                .filter(i -> objectNameFilter.test(i.getObjectName()))
                .collect(toList());

        return Map.of(TABLE, table, CONSTRAINTS, constraints, FOREIGN_KEYS, foreignKeys, INDICES, indices);
    }

    private boolean isExpression(ParseTree tree)
    {
        if (tree == null)
        {
            return false;
        }
        ParseTree current = tree;
        while (current != null)
        {
            if (current instanceof ExpressionContext
                    || current instanceof Search_conditionContext
                    || current instanceof PredicateContext)
            {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private boolean isTableSourceItem(ParseTree tree)
    {
        if (tree == null)
        {
            return false;
        }
        ParseTree current = tree;
        while (current != null)
        {
            if (current instanceof Table_source_itemContext)
            {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private CompletionResult suggestColumns(ParseTree node)
    {
        Set<TableAlias> aliases = TableSourceAliasCollector.collectTableSourceAliases(node, connectionState.getDatabase());

        if (aliases.isEmpty())
        {
            return null;
        }

        List<CompletionItem> items = new ArrayList<>();
        boolean partialResult = false;
        for (TableAlias ta : aliases)
        {
            String alias = ta.alias();
            if (ta.type() == TableAliasType.TABLE
                    || ta.type() == TableAliasType.TABLE_FUNCTION
                    || ta.type() == TableAliasType.CHANGETABLE)
            {
                Catalog catalog = crawlService.getCatalog(connectionState, ta.objectName()
                        .getCatalog());
                if (catalog == null)
                {
                    partialResult = true;
                    continue;
                }

                boolean onlyPrimaryKey = ta.type() == TableAliasType.CHANGETABLE;

                String schema = ta.objectName()
                        .getSchema();
                String name = ta.objectName()
                        .getName();

                for (TableSource ts : catalog.getTableSources())
                {
                    if (!isBlank(schema)
                            && !equalsIgnoreCase(schema, ts.getSchema()))
                    {
                        continue;
                    }
                    else if (!equalsIgnoreCase(name, ts.getName()))
                    {
                        continue;
                    }

                    for (Column c : ts.getColumns())
                    {
                        if (onlyPrimaryKey
                                && c.getPrimaryKeyName() == null)
                        {
                            continue;
                        }

                        QualifiedName match;
                        String replacement;
                        if (isBlank(alias))
                        {
                            match = QualifiedName.of(c.getName());
                            replacement = c.getName();
                        }
                        else
                        {
                            match = QualifiedName.of(alias, c.getName());
                            replacement = alias + "." + c.getName();
                        }

                        items.add(new CompletionItem(match.getParts(), replacement, null, null, icons.columns, 0));
                    }
                    // Change table
                    for (String c : ta.extendedColumns())
                    {
                        QualifiedName match;
                        String replacement;
                        if (isBlank(alias))
                        {
                            match = QualifiedName.of(c);
                            replacement = c;
                        }
                        else
                        {
                            match = QualifiedName.of(alias, c);
                            replacement = alias + "." + c;
                        }

                        items.add(new CompletionItem(match.getParts(), replacement, null, null, icons.columns, 0));
                    }
                    break;
                }

            }
            else if (ta.type() == TableAliasType.TABLEVARIABLE
                    || ta.type() == TableAliasType.TEMPTABLE
                    || ta.type() == TableAliasType.SUBQUERY)
            {
                List<String> list = ta.type() == TableAliasType.SUBQUERY ? ta.extendedColumns()
                        : temporaryTables.getOrDefault(ta.objectName()
                                .getName(), emptyList());

                for (String c : list)
                {
                    QualifiedName match;
                    String replacement;
                    if (isBlank(alias))
                    {
                        match = QualifiedName.of(c);
                        replacement = c;
                    }
                    else
                    {
                        match = QualifiedName.of(alias, c);
                        replacement = alias + "." + c;
                    }

                    items.add(new CompletionItem(match.getParts(), replacement, null, null, icons.columns, 0));
                }
            }
        }

        return new CompletionResult(items, partialResult);
    }

    private CompletionResult suggestTableSources()
    {
        Catalog catalog = crawlService.getCatalog(connectionState, connectionState.getDatabase());
        List<CompletionItem> result = new ArrayList<>();

        boolean partialResult = false;
        if (catalog != null)
        {
            result.addAll(catalog.getTableSources()
                    .stream()
                    .map(ts -> new CompletionItem(List.of(ts.getSchema(), ts.getName()), ts.getSchema() + "." + ts.getName(), null, null, icons.table, 0))
                    .collect(toList()));
        }
        else
        {
            partialResult = true;
        }

        result.addAll(temporaryTables.keySet()
                .stream()
                .map(t -> new CompletionItem(t, null, icons.table))
                .collect(toList()));

        return new CompletionResult(result, partialResult);
    }

    @Override
    protected CompletionResult getCompletionItems(TokenOffset tokenOffset)
    {
        // First check found tokens parents if we can detect context
        if (isExpression(tokenOffset.tree()))
        {
            return suggestColumns(tokenOffset.tree());
        }
        else if (isExpression(tokenOffset.prevTree()))
        {
            return suggestColumns(tokenOffset.prevTree());
        }
        else if (isTableSourceItem(tokenOffset.tree()))
        {
            return suggestTableSources();
        }

        // Try antlr-c3
        CandidatesCollection candidates = core.collectCandidates(tokenOffset.suggestTokenIndex(), null);
        if (candidates.rules.isEmpty())
        {
            return null;
        }

        if (candidates.rules.containsKey(TSqlParser.RULE_table_source_item))
        {
            return suggestTableSources();
        }

        if (candidates.rules.containsKey(TSqlParser.RULE_func_proc_name_server_database_schema))
        {
            Catalog catalog = crawlService.getCatalog(connectionState, connectionState.getDatabase());
            if (catalog == null)
            {
                return null;
            }
            return new CompletionResult(catalog.getRoutines()
                    .stream()
                    .filter(r -> r.getType() == Routine.Type.PROCEDURE)
                    .map(r -> new CompletionItem(List.of(r.getSchema(), r.getName()), r.getSchema() + "." + r.getName(), null, null, icons.wpformsIcon, 0))
                    .collect(toList()), false);
        }
        if (candidates.rules.containsKey(TSqlParser.RULE_expression)
                || candidates.rules.containsKey(TSqlParser.RULE_search_condition)
                || candidates.rules.containsKey(TSqlParser.RULE_full_column_name))
        {
            // Use prev tree node if current is EOF
            ParseTree node = tokenOffset.tree();
            if (node instanceof TerminalNode
                    && ((TerminalNode) node).getSymbol()
                            .getType() == Token.EOF)
            {
                node = tokenOffset.prevTree();
            }
            else if (node instanceof ErrorNode)
            {
                node = tokenOffset.prevTree();
            }

            return suggestColumns(node);
        }

        return null;
    }

    @Override
    protected Set<Integer> getCodeCompleteRuleIndices()
    {
        return Set.of(TSqlParser.RULE_expression, TSqlParser.RULE_search_condition, TSqlParser.RULE_full_column_name, TSqlParser.RULE_table_source_item,
                TSqlParser.RULE_func_proc_name_server_database_schema);
    }

    @Override
    protected Set<Integer> getTableSourceRuleIndices()
    {
        return Set.of(TSqlParser.RULE_table_name, TSqlParser.RULE_full_table_name);
    }

    @Override
    protected Set<Integer> getProcedureFunctionsRuleIndices()
    {
        return Set.of(TSqlParser.RULE_func_proc_name_schema, TSqlParser.RULE_func_proc_name_database_schema, TSqlParser.RULE_func_proc_name_server_database_schema);
    }

    @Override
    protected Pair<Interval, ObjectName> getTableSource(ParserRuleContext ctx)
    {
        Id_Context databaseCtx = null;
        Id_Context schemaCtx = null;
        Id_Context tableCtx = null;

        if (ctx instanceof Table_nameContext tnCtx
                && tnCtx.table != null)
        {
            databaseCtx = tnCtx.database;
            schemaCtx = tnCtx.schema;
            tableCtx = tnCtx.table;

        }
        else if (ctx instanceof Full_table_nameContext tnCtx)
        {
            databaseCtx = tnCtx.database;
            schemaCtx = tnCtx.schema;
            tableCtx = tnCtx.table;
        }

        if (tableCtx != null)
        {
            ParserRuleContext tmp = tableCtx.getParent();
            boolean isExpression = false;
            while (tmp != null)
            {
                if (tmp instanceof Table_source_itemContext)
                {
                    break;
                }
                else if (tmp instanceof ExpressionContext)
                {
                    isExpression = true;
                    break;
                }
                tmp = tmp.getParent();
            }

            // Table references inside expressions is not what we are lookng for
            if (isExpression)
            {
                return null;
            }

            String database = databaseCtx != null ? databaseCtx.getText()
                    : null;
            String schema = schemaCtx != null ? schemaCtx.getText()
                    : null;
            String table = tableCtx.getText();

            int start = tableCtx.getStart()
                    .getStartIndex();
            int stop = tableCtx.getStop()
                    .getStopIndex() + 1;

            // Skip Hash in offset
            if (tableCtx.TEMP_ID() != null)
            {
                start++;
            }

            return Pair.of(Interval.of(start, stop), new ObjectName(database, schema, table));
        }

        return null;
    }

    @Override
    protected Pair<Interval, ObjectName> getProcedureFunction(ParserRuleContext ctx)
    {
        Id_Context databaseCtx = null;
        Id_Context schemaCtx = null;
        Id_Context procedureCtx = null;

        if (ctx instanceof Func_proc_name_server_database_schemaContext fpCtx)
        {
            databaseCtx = fpCtx.database;
            schemaCtx = fpCtx.schema;
            procedureCtx = fpCtx.procedure;
        }
        else if (ctx instanceof Func_proc_name_database_schemaContext fpCtx)
        {
            databaseCtx = fpCtx.database;
            schemaCtx = fpCtx.schema;
            procedureCtx = fpCtx.procedure;
        }
        else if (ctx instanceof Func_proc_name_schemaContext fpCtx)
        {
            schemaCtx = fpCtx.schema;
            procedureCtx = fpCtx.procedure;
        }

        if (procedureCtx != null)
        {
            String database = databaseCtx != null ? databaseCtx.getText()
                    : null;
            String schema = schemaCtx != null ? schemaCtx.getText()
                    : null;

            String text = procedureCtx.getText();
            int start = procedureCtx.getStart()
                    .getStartIndex();
            int stop = procedureCtx.getStop()
                    .getStopIndex() + 1;

            return Pair.of(Interval.of(start, stop), new ObjectName(database, schema, text));
        }
        return null;
    }

    private static String unquote(String object)
    {
        if (object.charAt(0) == '['
                || object.charAt(0) == '"')
        {
            return object.substring(1, object.length() - 1);
        }

        return object;
    }

    /** Visitor that validates tables/columns/common expression errors etc. */
    // TODO: find missing columns
    static class ValidateVisitor extends TSqlParserBaseVisitor<Void>
    {
        private final Map<String, List<String>> temporaryTables;
        private final List<ParseItem> parseResult;
        private final CatalogCrawlService crawlService;
        private final IConnectionState connectionState;

        ValidateVisitor(Map<String, List<String>> temporaryTables, List<ParseItem> parseResult, IConnectionState connectionState, CatalogCrawlService crawlService)
        {
            this.temporaryTables = temporaryTables;
            this.parseResult = parseResult;
            this.connectionState = connectionState;
            this.crawlService = crawlService;
        }

        @Override
        public Void visitCreate_table(Create_tableContext ctx)
        {
            if (ctx.table_name().table.TEMP_ID() != null)
            {
                List<String> columns = collectTableColumns(ctx);
                temporaryTables.put(ctx.table_name().table.getText(), columns);
            }

            return super.visitCreate_table(ctx);
        }

        @Override
        public Void visitDeclare_statement(Declare_statementContext ctx)
        {
            if (ctx.table_type_definition() != null)
            {
                List<String> columns = collectTableColumns(ctx);
                temporaryTables.put(ctx.LOCAL_ID()
                        .getText(), columns);
            }
            return super.visitDeclare_statement(ctx);
        }

        @Override
        public Void visitPredicate(PredicateContext ctx)
        {
            if (ctx.comparison_operator() != null
                    && ctx.expression()
                            .size() == 2
                    && ctx.expression(0)
                            .getText()
                            .equalsIgnoreCase(ctx.expression(1)
                                    .getText()))
            {
                int line = ctx.start.getLine();
                int start = ctx.start.getStartIndex();
                int length = ctx.stop.getStopIndex() - start + 1;
                parseResult.add(new ParseItem("Same expression on both sides", line, start, length, Color.BLUE, ParseItem.Level.WARN));
            }
            return super.visitPredicate(ctx);
        }

        @Override
        public Void visitDdl_object(Ddl_objectContext ctx)
        {
            if (ctx.LOCAL_ID() != null)
            {
                validateTableVariable(ctx.LOCAL_ID()
                        .getSymbol());
            }
            else if (ctx.full_table_name() != null)
            {
                RuleContext current = ctx.parent;
                while (current != null)
                {
                    // Don't validate update statements because the can
                    // have the form of
                    // UPDATE a <---- a will fail because it's an alias and not a table
                    // FROM table a
                    if (current instanceof Update_statementContext)
                    {
                        return super.visitDdl_object(ctx);
                    }

                    current = current.parent;
                }

                validateTable(ctx.full_table_name());
            }
            return super.visitDdl_object(ctx);
        }

        @Override
        public Void visitTable_source_item(Table_source_itemContext ctx)
        {
            if (ctx.loc_id != null)
            {
                validateTableVariable(ctx.loc_id);
            }
            else if (ctx.full_table_name() != null)
            {
                validateTable(ctx.full_table_name());
            }

            return super.visitTable_source_item(ctx);
        }

        /** Collect column names for temp and variable tables */
        private List<String> collectTableColumns(ParserRuleContext ctx)
        {
            List<String> columns = new ArrayList<>();
            ctx.accept(new TSqlParserBaseVisitor<Void>()
            {
                @Override
                public Void visitColumn_definition(Column_definitionContext ctx)
                {
                    columns.add(ctx.id_()
                            .getText());
                    return null;
                }
            });
            return columns;
        }

        private void validateTableVariable(Token node)
        {
            String table = unquote(node.getText());
            if (!temporaryTables.containsKey(table))
            {
                int line = node.getLine();
                int start = node.getStartIndex();
                int length = node.getStopIndex() - start + 1;
                parseResult.add(new ParseItem("Missing table variable '" + table + "'", line, start, length, null, ParseItem.Level.ERROR));
            }
        }

        private void validateTable(Full_table_nameContext ctx)
        {
            String table = unquote(ctx.table.getText());
            int line = ctx.table.start.getLine();
            int start = ctx.table.start.getStartIndex();
            int length = ctx.table.stop.getStopIndex() - start + 1;

            if (ctx.table.TEMP_ID() != null)
            {
                if (!temporaryTables.containsKey(table))
                {
                    parseResult.add(new ParseItem("Missing temporary table '" + table + "'", line, start, length, null, ParseItem.Level.ERROR));
                }
            }
            else if (ctx.server == null)
            {
                String database = ctx.database != null ? ctx.database.getText()
                        : connectionState.getDatabase();
                String schema = ctx.schema != null ? ctx.schema.getText()
                        : "";
                Catalog catalog = crawlService.getCatalog(connectionState, database);

                if (catalog != null
                        && catalog.getTableSources()
                                .stream()
                                .noneMatch(t -> ("".equals(schema)
                                        || schema.equalsIgnoreCase(t.getSchema()))
                                        && t.getName()
                                                .equalsIgnoreCase(table)))
                {
                    parseResult.add(new ParseItem("Missing table '" + table + "'", line, start, length, null, ParseItem.Level.ERROR));
                }
            }
        }
    }

    /**
     * Visitor that collects table source aliases for a part of the tree.
     *
     * <pre>
     * Traverses upwards in the tree and collects valid table sources according to valid scopes. Checks if outer scope
     * is valid etc. (apply's vs joins)
     * </pre>
     */
    static class TableSourceAliasCollector extends TSqlParserBaseVisitor<Void>
    {
        private static final List<String> CHANGETABLE_COLUMNS = asList("SYS_CHANGE_VERSION", "SYS_CHANGE_CREATION_VERSION", "SYS_CHANGE_OPERATION", "SYS_CHANGE_COLUMNS", "SYS_CHANGE_CONTEXT");
        private final String connectionDatabase;
        private final Join_partContext prevJoinPart;
        private final boolean prevJoinPartIsSubQuery;
        private final Set<TableAlias> result;

        private TableSourceAliasCollector(Set<TableAlias> result, Join_partContext prevJoinPart, boolean prevJoinPartIsSubQuery, String connectionDatabase)
        {
            this.result = result;
            this.prevJoinPart = prevJoinPart;
            this.connectionDatabase = connectionDatabase;
            this.prevJoinPartIsSubQuery = prevJoinPartIsSubQuery;
        }

        @Override
        public Void visitTable_source(Table_sourceContext ctx)
        {
            // Are we coming from a join part then we should only see the parts
            // that are defined on and before that join part
            if (prevJoinPart != null
                    && ctx.joins != null)
            {
                // Visit the the table source
                ctx.table_source_item()
                        .accept(this);

                for (Join_partContext joinPart : ctx.joins)
                {
                    // We are coming from a sub query then we should drop out before
                    // we visit the join part since that should not be included
                    if (prevJoinPartIsSubQuery
                            && joinPart == this.prevJoinPart)
                    {
                        break;
                    }

                    joinPart.accept(this);
                    // Drop out after the join part we came from
                    if (joinPart == this.prevJoinPart)
                    {
                        break;
                    }
                }
                return null;
            }
            return super.visitTable_source(ctx);
        }

        @Override
        public Void visitPredicate(PredicateContext ctx)
        {
            // Don't dig into exists sub queries
            if (ctx.EXISTS() != null)
            {
                return null;
            }
            return super.visitPredicate(ctx);
        }

        @Override
        public Void visitDdl_object(Ddl_objectContext ctx)
        {
            if (ctx.full_table_name() != null)
            {
                result.add(getTableAlias("", ctx.full_table_name()));
            }
            // Table variable
            else if (ctx.LOCAL_ID() != null)
            {
                result.add(getTableAlias("", ctx.LOCAL_ID()
                        .getSymbol()));
            }

            return null;
        }

        @Override
        public Void visitTable_source_item(Table_source_itemContext ctx)
        {
            String alias = ctx.as_table_alias() != null ? ctx.as_table_alias()
                    .getText()
                    : "";

            if (ctx.derived_table() != null
                    || ctx.table_source() != null)
            {
                // TODO: asterisk needs to be collected
                ObjectName objectName = new ObjectName("", "", "");
                result.add(new TableAlias(alias, objectName, getColumnNames(ctx), TableAliasType.SUBQUERY));
                return null;
            }

            // Table source
            if (ctx.full_table_name() != null)
            {
                result.add(getTableAlias(alias, ctx.full_table_name()));
                return null;
            }
            // Table variable
            else if (ctx.loc_id != null)
            {
                result.add(getTableAlias(alias, ctx.loc_id));
                return null;
            }

            List<String> extendedColumns = emptyList();
            TableAliasType type = null;
            String database = connectionDatabase;
            String schema = "";
            String name = "";

            if (ctx.function_call() instanceof SCALAR_FUNCTIONContext sfCtx
                    && sfCtx.scalar_function_name()
                            .func_proc_name_server_database_schema() != null)
            {
                type = TableAliasType.TABLE_FUNCTION;

                Id_Context databaseCtx = null;
                Id_Context schemaCtx = null;
                Id_Context procedureCtx = null;

                Func_proc_name_server_database_schemaContext funcProc = sfCtx.scalar_function_name()
                        .func_proc_name_server_database_schema();

                if (funcProc.procedure != null)
                {
                    databaseCtx = funcProc.database;
                    schemaCtx = funcProc.schema;
                    procedureCtx = funcProc.procedure;
                }
                else if (funcProc.func_proc_name_database_schema().procedure != null)
                {
                    databaseCtx = funcProc.func_proc_name_database_schema().database;
                    schemaCtx = funcProc.func_proc_name_database_schema().schema;
                    procedureCtx = funcProc.func_proc_name_database_schema().procedure;
                }
                else if (funcProc.func_proc_name_database_schema()
                        .func_proc_name_schema() != null)
                {
                    schemaCtx = funcProc.func_proc_name_database_schema()
                            .func_proc_name_schema().schema;
                    procedureCtx = funcProc.func_proc_name_database_schema()
                            .func_proc_name_schema().procedure;
                }

                if (procedureCtx != null)
                {
                    database = databaseCtx != null ? databaseCtx.getText()
                            : connectionDatabase;
                    schema = schemaCtx != null ? schemaCtx.getText()
                            : "";
                    name = procedureCtx.getText();
                }
            }
            else if (ctx.change_table() != null)
            {
                type = TableAliasType.CHANGETABLE;
                Id_Context databaseCtx = null;
                Id_Context schemaCtx = null;
                Id_Context tableCtx = null;

                if (ctx.change_table()
                        .change_table_changes() != null)
                {
                    databaseCtx = ctx.change_table()
                            .change_table_changes().changetable.database;
                    schemaCtx = ctx.change_table()
                            .change_table_changes().changetable.schema;
                    tableCtx = ctx.change_table()
                            .change_table_changes().changetable.table;
                }
                else if (ctx.change_table()
                        .change_table_version() != null)
                {
                    databaseCtx = ctx.change_table()
                            .change_table_changes().changetable.database;
                    schemaCtx = ctx.change_table()
                            .change_table_changes().changetable.schema;
                    tableCtx = ctx.change_table()
                            .change_table_changes().changetable.table;
                }

                if (tableCtx != null)
                {
                    database = databaseCtx != null ? databaseCtx.getText()
                            : connectionDatabase;
                    schema = schemaCtx != null ? schemaCtx.getText()
                            : "";
                    name = tableCtx.getText();

                    extendedColumns = CHANGETABLE_COLUMNS;
                }
            }

            if (!isBlank(name))
            {
                ObjectName objectName = new ObjectName(database, schema, name);
                result.add(new TableAlias(alias, objectName, extendedColumns, type));
            }

            // Don't visit this instance, it's already processed
            return null;
        }

        private TableAlias getTableAlias(String alias, Full_table_nameContext ctx)
        {
            String database = ctx.database != null ? ctx.database.getText()
                    : connectionDatabase;

            String schema = ctx.schema != null ? ctx.schema.getText()
                    : "";
            String name = ctx.table.getText();

            TableAliasType type = TableAliasType.TABLE;

            if (ctx.table.TEMP_ID() != null)
            {
                type = TableAliasType.TEMPTABLE;
                database = "";
                schema = "";
            }

            ObjectName objectName = new ObjectName(database, schema, name);
            return new TableAlias(alias, objectName, emptyList(), type);
        }

        private TableAlias getTableAlias(String alias, Token localId)
        {
            return new TableAlias(alias, new ObjectName("", "", localId.getText()), emptyList(), TableAliasType.TABLEVARIABLE);
        }

        /** Get column names from a subquery */
        private List<String> getColumnNames(ParserRuleContext ctx)
        {
            List<String> columns = new ArrayList<>();

            ctx.accept(new TSqlParserBaseVisitor<Void>()
            {
                @Override
                public Void visitQuery_specification(Query_specificationContext ctx)
                {
                    if (ctx.columns != null)
                    {
                        ctx.columns.accept(this);
                    }

                    // Don't visit anymore after first query spec
                    return null;
                }

                @Override
                public Void visitExpression_elem(Expression_elemContext ctx)
                {
                    // Alias expression
                    if (ctx.as_column_alias() != null)
                    {
                        columns.add(ctx.as_column_alias()
                                .getText());
                    }
                    // Column name, pick last part
                    else if (ctx.expressionAs != null
                            && ctx.expressionAs.full_column_name() != null
                            && ctx.expressionAs.full_column_name().column_name != null
                            && ctx.expressionAs.full_column_name().column_name.ID() != null)
                    {
                        columns.add(ctx.expressionAs.full_column_name().column_name.ID()
                                .getText());
                    }

                    return null;
                }
            });

            return columns;
        }

        /** Collects table source aliases for provided tree */
        static Set<TableAlias> collectTableSourceAliases(ParseTree tree, String connectionDatabase)
        {
            ParseTree current = tree;
            boolean passedSubQuery = false;
            Join_partContext prevJoinPart = null;
            boolean prevJoinPartIsSubQuery = false;
            Set<TableAlias> result = new HashSet<>();
            while (current != null)
            {
                if (current instanceof Query_specificationContext sctx)
                {
                    // If we passed a sub query and the flag has not been switched of
                    // due to a join_part that allows outer scope we are done, we should not
                    // see any more
                    if (passedSubQuery)
                    {
                        break;
                    }
                    if (sctx.table_sources() != null)
                    {
                        sctx.table_sources()
                                .accept(new TableSourceAliasCollector(result, prevJoinPart, prevJoinPartIsSubQuery, connectionDatabase));
                    }
                }
                else if (current instanceof Select_statementContext)
                {
                    // If we passed a sub query and the flag has not been switched of
                    // due to a join_part that allows outer scope we are done, we should not
                    // see any more
                    if (passedSubQuery)
                    {
                        break;
                    }
                    current.accept(new TableSourceAliasCollector(result, prevJoinPart, prevJoinPartIsSubQuery, connectionDatabase));
                }
                else if (current instanceof Delete_statementContext dctx)
                {
                    if (dctx.delete_statement_from() != null)
                    {
                        dctx.delete_statement_from()
                                .accept(new TableSourceAliasCollector(result, prevJoinPart, prevJoinPartIsSubQuery, connectionDatabase));
                    }
                    if (dctx.table_sources() != null)
                    {
                        dctx.table_sources()
                                .accept(new TableSourceAliasCollector(result, prevJoinPart, prevJoinPartIsSubQuery, connectionDatabase));
                    }
                }
                else if (current instanceof Update_statementContext uctx)
                {
                    if (uctx.table_sources() != null)
                    {
                        uctx.table_sources()
                                .accept(new TableSourceAliasCollector(result, prevJoinPart, prevJoinPartIsSubQuery, connectionDatabase));
                    }
                    if (uctx.ddl_object() != null)
                    {
                        uctx.ddl_object()
                                .accept(new TableSourceAliasCollector(result, prevJoinPart, prevJoinPartIsSubQuery, connectionDatabase));
                    }
                }
                else if (current instanceof Join_partContext jpCtx)
                {
                    prevJoinPart = jpCtx;

                }
                else if (current instanceof SubqueryContext)
                {
                    // Mark that we passed a sub query
                    passedSubQuery = true;
                }

                current = current.getParent();

                // We reached the top of the sql clause => search no more
                if (current instanceof Sql_clausesContext
                        || current instanceof Sql_unionContext)
                {
                    break;
                }
                else if (current instanceof Join_partContext jpCtx
                        && jpCtx.apply_() != null)
                {
                    prevJoinPartIsSubQuery = passedSubQuery;

                    // Reset sub query flag for apply's
                    passedSubQuery = false;
                }
                else if (current instanceof PredicateContext pctx
                        && pctx.EXISTS() != null)
                {
                    prevJoinPartIsSubQuery = passedSubQuery;

                    // Reset sub query flag for exists
                    passedSubQuery = false;
                }

            }

            return result;
        }
    }
}
