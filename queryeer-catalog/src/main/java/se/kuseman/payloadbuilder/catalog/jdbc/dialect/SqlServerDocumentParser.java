package se.kuseman.payloadbuilder.catalog.jdbc.dialect;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.Strings.CI;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.lang3.tuple.Pair;

import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.ITemplateService;

import se.kuseman.payloadbuilder.api.QualifiedName;
import se.kuseman.payloadbuilder.catalog.jdbc.CatalogCrawlService;
import se.kuseman.payloadbuilder.catalog.jdbc.IConnectionContext;
import se.kuseman.payloadbuilder.catalog.jdbc.Icons;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.c3.CodeCompletionCore.CandidatesCollection;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Catalog;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Column;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Constraint;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ForeignKey;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Index;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ObjectName;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Routine;
import se.kuseman.payloadbuilder.catalog.jdbc.model.RoutineParameter;
import se.kuseman.payloadbuilder.catalog.jdbc.model.TableSource;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlLexer;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Column_definitionContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Create_or_alter_functionContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Create_or_alter_procedureContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Create_tableContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Ddl_objectContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Declare_statementContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Delete_statementContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Execute_bodyContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Execute_parameterContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Execute_statement_argContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Execute_statement_arg_namedContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.ExpressionContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Expression_elemContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Full_table_nameContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Func_proc_name_database_schemaContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Func_proc_name_schemaContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Func_proc_name_server_database_schemaContext;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Id_Context;
import se.kuseman.payloadbuilder.jdbc.parser.tsql.TSqlParser.Join_onContext;
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
    private static final Map<String, SignatureHint> BUILTIN_HINTS = new HashMap<>();
    /** Built-in table-valued functions with their output column names */
    private static final Map<String, List<String>> BUILTIN_TABLE_FUNCTIONS = new HashMap<>();
    /**
     * Token types that can immediately precede an expression position. Used to detect when the caret sits right after an operator (e.g. after {@code =}, {@code AND}, {@code +}) so that the query-spec
     * column-suggestion fallback is triggered, matching the behaviour already in place for COMMA.
     */
    private static final Set<Integer> EXPRESSION_PRECEDING_OPERATOR_TOKENS = Set.of(TSqlLexer.EQUAL, // =
            TSqlLexer.LESS, // < (also part of <=, <>)
            TSqlLexer.GREATER, // > (also part of >=, <>)
            TSqlLexer.EXCLAMATION, // ! (part of !=, !<, !>)
            TSqlLexer.PLUS, // +
            TSqlLexer.MINUS, // -
            TSqlLexer.STAR, // *
            TSqlLexer.DIVIDE, // /
            TSqlLexer.MODULE, // %
            TSqlLexer.BIT_OR, // |
            TSqlLexer.BIT_AND, // &
            TSqlLexer.BIT_XOR, // ^
            TSqlLexer.BIT_NOT, // ~ (unary bitwise NOT)
            TSqlLexer.DOUBLE_BAR, // || (string concatenation)
            TSqlLexer.PLUS_ASSIGN, // +=
            TSqlLexer.MINUS_ASSIGN, // -=
            TSqlLexer.MULT_ASSIGN, // *=
            TSqlLexer.DIV_ASSIGN, // /=
            TSqlLexer.MOD_ASSIGN, // %=
            TSqlLexer.AND_ASSIGN, // &=
            TSqlLexer.XOR_ASSIGN, // ^=
            TSqlLexer.OR_ASSIGN, // |=
            TSqlLexer.AND, // AND
            TSqlLexer.OR, // OR
            TSqlLexer.NOT // NOT
    );

    static
    {
        // Null / conditional
        bh("ISNULL", "expression", p("check_expression", "expression"), p("replacement_value", "expression"));
        bh("NULLIF", "expression", p("expression1", "expression"), p("expression2", "expression"));
        bh("COALESCE", "expression", p("value_1", "expression"), p("...", "expression"));
        bh("IIF", "expression", p("boolean_expression", "bit"), p("true_value", "expression"), p("false_value", "expression"));
        bh("CHOOSE", "expression", p("index", "int"), p("val_1", "expression"), p("val_2, ...", "expression"));

        // String
        bh("LEN", "int", p("string_expression", "expression"));
        bh("DATALENGTH", "int", p("expression", "expression"));
        bh("LEFT", "varchar", p("character_expression", "expression"), p("integer_expression", "int"));
        bh("RIGHT", "varchar", p("character_expression", "expression"), p("integer_expression", "int"));
        bh("SUBSTRING", "varchar", p("expression", "expression"), p("start", "int"), p("length", "int"));
        bh("CHARINDEX", "int", p("expressionToFind", "expression"), p("expressionToSearch", "expression"), p("start_location", "int"));
        bh("PATINDEX", "int", p("pattern", "varchar"), p("expression", "expression"));
        bh("REPLACE", "varchar", p("string_expression", "expression"), p("string_pattern", "expression"), p("string_replacement", "expression"));
        bh("STUFF", "varchar", p("character_expression", "expression"), p("start", "int"), p("length", "int"), p("replaceWith_expression", "expression"));
        bh("REPLICATE", "varchar", p("string_expression", "expression"), p("integer_expression", "int"));
        bh("REVERSE", "varchar", p("string_expression", "expression"));
        bh("SPACE", "varchar", p("integer_expression", "int"));
        bh("STR", "char", p("float_expression", "float"), p("length", "int"), p("decimal", "int"));
        bh("LTRIM", "varchar", p("character_expression", "expression"));
        bh("RTRIM", "varchar", p("character_expression", "expression"));
        bh("TRIM", "varchar", p("string", "expression"));
        bh("UPPER", "varchar", p("character_expression", "expression"));
        bh("LOWER", "varchar", p("character_expression", "expression"));
        bh("CONCAT", "varchar", p("string_value_1", "expression"), p("string_value_n", "expression"));
        bh("CONCAT_WS", "varchar", p("separator", "nvarchar"), p("argument_1", "expression"), p("argument_n", "expression"));
        bh("STRING_AGG", "varchar", p("expression", "expression"), p("separator", "nvarchar"));
        bh("STRING_SPLIT", "table", p("string", "nvarchar"), p("separator", "nvarchar"));
        bh("FORMAT", "nvarchar", p("value", "expression"), p("format", "nvarchar"), p("culture", "nvarchar"));
        bh("CHAR", "char", p("integer_expression", "int"));
        bh("NCHAR", "nchar", p("integer_expression", "int"));
        bh("ASCII", "int", p("character_expression", "expression"));
        bh("UNICODE", "int", p("ncharacter_expression", "expression"));
        bh("SOUNDEX", "char", p("character_expression", "expression"));
        bh("DIFFERENCE", "int", p("character_expression_1", "expression"), p("character_expression_2", "expression"));
        bh("QUOTENAME", "nvarchar", p("character_string", "nvarchar"), p("quote_character", "nchar"));

        // Conversion
        bh("CONVERT", "expression", p("data_type", "type"), p("expression", "expression"), p("style", "int"));
        bh("CAST", "expression", p("expression AS data_type", "expression"));
        bh("TRY_CONVERT", "expression", p("data_type", "type"), p("expression", "expression"), p("style", "int"));
        bh("TRY_CAST", "expression", p("expression AS data_type", "expression"));
        bh("PARSE", "expression", p("string_value", "nvarchar"), p("AS data_type", "type"), p("USING culture", "nvarchar"));
        bh("TRY_PARSE", "expression", p("string_value", "nvarchar"), p("AS data_type", "type"), p("USING culture", "nvarchar"));

        // Date and time
        bh("DATEADD", "datetime", p("datepart", "datepart"), p("number", "int"), p("date", "datetime"));
        bh("DATEDIFF", "int", p("datepart", "datepart"), p("startdate", "datetime"), p("enddate", "datetime"));
        bh("DATEDIFF_BIG", "bigint", p("datepart", "datepart"), p("startdate", "datetime"), p("enddate", "datetime"));
        bh("DATENAME", "nvarchar", p("datepart", "datepart"), p("date", "datetime"));
        bh("DATEPART", "int", p("datepart", "datepart"), p("date", "datetime"));
        bh("DAY", "int", p("date", "datetime"));
        bh("MONTH", "int", p("date", "datetime"));
        bh("YEAR", "int", p("date", "datetime"));
        bh("EOMONTH", "date", p("start_date", "datetime"), p("month_to_add", "int"));
        bh("DATEFROMPARTS", "date", p("year", "int"), p("month", "int"), p("day", "int"));
        bh("DATETIMEFROMPARTS", "datetime", p("year", "int"), p("month", "int"), p("day", "int"), p("hour", "int"), p("minute", "int"), p("seconds", "int"), p("milliseconds", "int"));
        bh("TIMEFROMPARTS", "time", p("hour", "int"), p("minute", "int"), p("seconds", "int"), p("fractions", "int"), p("precision", "int"));
        bh("ISDATE", "int", p("expression", "expression"));
        bh("SWITCHOFFSET", "datetimeoffset", p("datetimeoffset_expression", "expression"), p("timezoneoffset_expression", "expression"));
        bh("TODATETIMEOFFSET", "datetimeoffset", p("expression", "expression"), p("time_zone", "expression"));

        // Math
        bh("ABS", "numeric", p("numeric_expression", "expression"));
        bh("CEILING", "numeric", p("numeric_expression", "expression"));
        bh("FLOOR", "numeric", p("numeric_expression", "expression"));
        bh("ROUND", "numeric", p("numeric_expression", "expression"), p("length", "int"), p("function", "tinyint"));
        bh("POWER", "float", p("float_expression", "float"), p("y", "float"));
        bh("SQRT", "float", p("float_expression", "float"));
        bh("SQUARE", "float", p("float_expression", "float"));
        bh("SIGN", "numeric", p("numeric_expression", "expression"));
        bh("EXP", "float", p("float_expression", "float"));
        bh("LOG", "float", p("float_expression", "float"), p("base", "float"));
        bh("LOG10", "float", p("float_expression", "float"));
        bh("RAND", "float", p("seed", "int"));

        // System / JSON / metadata
        bh("HASHBYTES", "varbinary", p("algorithm", "varchar"), p("input", "expression"));
        bh("ISJSON", "int", p("expression", "nvarchar"));
        bh("JSON_VALUE", "nvarchar", p("expression", "nvarchar"), p("path", "nvarchar"));
        bh("JSON_QUERY", "nvarchar", p("expression", "nvarchar"), p("path", "nvarchar"));
        bh("JSON_MODIFY", "nvarchar", p("expression", "nvarchar"), p("path", "nvarchar"), p("newValue", "expression"));
        bh("OBJECT_ID", "int", p("object_name", "nvarchar"), p("object_type", "char"));
        bh("OBJECT_NAME", "sysname", p("object_id", "int"), p("database_id", "int"));
        bh("SCHEMA_NAME", "sysname", p("schema_id", "int"));
        bh("SCHEMA_ID", "int", p("schema_name", "sysname"));
        bh("DB_ID", "int", p("database_name", "nvarchar"));
        bh("DB_NAME", "nvarchar", p("database_id", "int"));
        bh("CHECKSUM", "int", p("*|expression_n", "expression"));
        bh("BINARY_CHECKSUM", "int", p("*|expression_n", "expression"));
    }

    private static void bh(String name, String returnType, SignatureParam... params)
    {
        BUILTIN_HINTS.put(name, new SignatureHint(name, List.of(params), returnType));
    }

    static
    {
        // Built-in TVFs with known output columns
        BUILTIN_TABLE_FUNCTIONS.put("STRING_SPLIT", asList("value", "ordinal"));
        BUILTIN_TABLE_FUNCTIONS.put("OPENJSON", asList("key", "value", "type"));
        BUILTIN_TABLE_FUNCTIONS.put("GENERATE_SERIES", asList("value"));
    }

    // CSOFF
    private static SignatureParam p(String name, String type)
    {
        return new SignatureParam(name, type);
    }
    // CSON

    private final Map<String, List<String>> temporaryTables = new HashMap<>();

    SqlServerDocumentParser(Icons icons, IEventBus eventBus, QueryActionsConfigurable queryActionsConfigurable, CatalogCrawlService catalogCrawler, IConnectionContext connectionContext,
            ITemplateService templateService)
    {
        super(eventBus, queryActionsConfigurable, catalogCrawler, connectionContext, templateService, icons);
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
    protected int getStatementRuleIndex()
    {
        return TSqlParser.RULE_sql_clauses;
    }

    @Override
    protected int getStatementSeparatorTokenType()
    {
        return TSqlParser.SEMI;
    }

    @Override
    protected Set<Integer> getStatementStartTokenTypes()
    {
        return Set.of(TSqlParser.SELECT, TSqlParser.INSERT, TSqlParser.UPDATE, TSqlParser.DELETE, TSqlParser.EXECUTE, TSqlParser.WITH, TSqlParser.MERGE, TSqlParser.DECLARE);
    }

    @Override
    protected void afterParse()
    {
        temporaryTables.clear();
        if (context != null)
        {
            ValidateVisitor visitor = new ValidateVisitor(temporaryTables, parseResult, connectionContext, crawlService);
            context.accept(visitor);
        }
    }

    @Override
    protected ParseTree miniParseStatementNode(ParserRuleContext statementCtx, int caretCharOffset)
    {
        if (parser == null
                || statementCtx == null
                || statementCtx.start == null)
        {
            return null;
        }
        TokenStream tokenStream = parser.getInputStream();
        int startTokenIdx = statementCtx.start.getTokenIndex();

        // Collect tokens from statement start to SEMI or EOF, then extract their text.
        // Using getText(Interval) captures hidden-channel whitespace tokens so spacing is preserved.
        int endTokenIdx = startTokenIdx;
        for (int i = startTokenIdx; i < tokenStream.size(); i++)
        {
            Token t = tokenStream.get(i);
            if (t.getType() == Token.EOF)
            {
                break;
            }
            if (t.getType() == TSqlParser.SEMI)
            {
                endTokenIdx = i;
                break;
            }
            endTokenIdx = i;
        }

        String text = tokenStream.getText(new Interval(startTokenIdx, endTokenIdx));

        // Find the equivalent caret position within the mini-document.
        int startCharOffset = statementCtx.start.getStartIndex();
        int miniCaret = Math.max(0, caretCharOffset - startCharOffset);

        // If the caret lands immediately after a DOT (e.g. "SELECT a.|" or "WHERE t.|"),
        // ANTLR's panic-mode recovery sees "a.<keyword>" and detaches the FROM clause from
        // Query_specificationContext, making table-alias collection impossible. Injecting a
        // synthetic placeholder identifier right at the caret turns "a." into "a.__x__" which
        // is a valid SQL expression and prevents the error recovery from firing.
        //
        // When the token immediately after the caret is a closing paren (e.g. "WHERE b.|)" inside
        // an EXISTS subquery), "a.__x__" alone is not a valid T-SQL predicate (predicate requires
        // a comparison operator). ANTLR's error recovery then tries to complete the predicate by
        // consuming the ")" — which is the EXISTS closing paren — and breaks the inner
        // query_specification's structure so that collectTableSourceAliases cannot find the inner
        // aliases. Injecting a complete predicate suffix ("__x__ = 1") makes the WHERE clause
        // valid without consuming any surrounding tokens.
        String miniText = text;
        if (miniCaret > 0
                && miniCaret <= text.length()
                && text.charAt(miniCaret - 1) == '.')
        {
            String suffix = "__x__";
            // Check if the next non-whitespace character after the caret is ')'. If so, inject a
            // full predicate expression to avoid the invalid-predicate error recovery described above.
            for (int i = miniCaret; i < text.length(); i++)
            {
                char c = text.charAt(i);
                if (!Character.isWhitespace(c))
                {
                    if (c == ')')
                    {
                        suffix = "__x__ = 1";
                    }
                    break;
                }
            }
            miniText = text.substring(0, miniCaret) + suffix + text.substring(miniCaret);
        }

        // Re-parse the isolated statement text (no error listeners — errors here are expected/ignored)
        CharStream cs = CharStreams.fromString(miniText);
        TSqlLexer lex = new TSqlLexer(cs);
        lex.removeErrorListeners();
        TokenStream miniTokenStream = new CommonTokenStream(lex);
        TSqlParser miniParser = new TSqlParser(miniTokenStream);
        miniParser.removeErrorListeners();
        Tsql_fileContext miniFile = miniParser.tsql_file();

        TokenOffset miniOffset = findTokenFromOffset(miniParser, miniFile, miniCaret);
        if (miniOffset == null)
        {
            return null;
        }
        return miniOffset.prevTree() != null ? miniOffset.prevTree()
                : miniOffset.tree();
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
    public boolean supportsSignatureHints()
    {
        return true;
    }

    @Override
    protected SignatureHint getBuiltinSignatureHint(String functionName)
    {
        return BUILTIN_HINTS.get(functionName.toUpperCase());
    }

    @Override
    protected Map<String, Object> getTableSourceTooltipModel(ObjectName name)
    {
        // TODO: create model for temporary tables
        String database = Objects.toString(name.getCatalog(), connectionContext.getDatabase());
        Catalog catalog = crawlService.getCatalog(connectionContext, database);
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

    /** BFS-scans the subtree rooted at {@code ctx} and returns the first (shallowest) {@link Query_specificationContext}, or {@code null} if none exists. */
    private static Query_specificationContext findQuerySpecInSubtree(ParserRuleContext ctx)
    {
        Deque<ParseTree> queue = new ArrayDeque<>();
        queue.add(ctx);
        while (!queue.isEmpty())
        {
            ParseTree current = queue.removeFirst();
            if (current instanceof Query_specificationContext qctx)
            {
                return qctx;
            }
            for (int i = 0; i < current.getChildCount(); i++)
            {
                queue.addLast(current.getChild(i));
            }
        }
        return null;
    }

    /**
     * Recursively walk the parse tree to find an Execute_bodyContext where the caret (caretOffset) is positioned after the procedure name. This offset-based approach handles ANTLR error recovery
     * where a trailing comma can cause the caret token to be orphaned outside the Execute_bodyContext's stop boundary, making parent-chain walking unreliable.
     */
    private Execute_bodyContext findExecuteBodyByOffset(ParseTree tree, int caretOffset)
    {
        if (tree instanceof Execute_bodyContext execBody
                && execBody.func_proc_name_server_database_schema() != null)
        {
            Func_proc_name_server_database_schemaContext procName = execBody.func_proc_name_server_database_schema();
            if (procName.stop != null
                    && caretOffset > procName.stop.getStopIndex()
                    && !isCaretInParamValuePosition(execBody, caretOffset))
            {
                return execBody;
            }
            // Don't recurse further into this execBody's children
            return null;
        }

        for (int i = 0; i < tree.getChildCount(); i++)
        {
            Execute_bodyContext found = findExecuteBodyByOffset(tree.getChild(i), caretOffset);
            if (found != null)
            {
                return found;
            }
        }
        return null;
    }

    /**
     * Returns true if the caret is positioned inside the value part of a named parameter (between the EQUAL sign and the end of the parameter value). This prevents showing parameter name completions
     * when the user is typing a value.
     */
    private boolean isCaretInParamValuePosition(Execute_bodyContext execBody, int caretOffset)
    {
        return checkCaretInValuePosition(execBody, caretOffset);
    }

    private boolean checkCaretInValuePosition(ParseTree tree, int caretOffset)
    {
        if (tree instanceof Execute_statement_arg_namedContext namedCtx)
        {
            Execute_parameterContext paramCtx = namedCtx.execute_parameter();
            if (paramCtx != null
                    && paramCtx.stop != null)
            {
                // Find the EQUAL token between the parameter name and value
                int equalStopIndex = -1;
                for (int i = 0; i < namedCtx.getChildCount(); i++)
                {
                    ParseTree child = namedCtx.getChild(i);
                    if (child instanceof TerminalNode tn
                            && tn.getSymbol()
                                    .getType() == TSqlLexer.EQUAL)
                    {
                        equalStopIndex = tn.getSymbol()
                                .getStopIndex();
                        break;
                    }
                }
                // Caret is in value position if it's strictly between the EQUAL sign and the end of the
                // parameter value. After a trailing comma, caretOffset > paramCtx.stop.getStopIndex() + 1
                // so we correctly skip this check.
                if (equalStopIndex >= 0
                        && caretOffset > equalStopIndex
                        && caretOffset <= paramCtx.stop.getStopIndex() + 1)
                {
                    return true;
                }
            }
            // Don't recurse further into namedCtx children
            return false;
        }

        for (int i = 0; i < tree.getChildCount(); i++)
        {
            if (checkCaretInValuePosition(tree.getChild(i), caretOffset))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the token at {@code tokenIndex} is an identifier (plain {@code ID}) whose effective preceding context is an open parenthesis {@code '('}, OR if the token itself is an open
     * parenthesis {@code '('} preceded by any non-EOF token. The first case detects a partial identifier inside a function argument (e.g. {@code "left(t.c|"} or {@code "left(c|"}): the algorithm
     * walks backwards through the alternating ID/DOT chain and stops at the first token that is neither an identifier nor a dot; if that token is {@code '('} the caret is in a function-argument
     * position. The second case handles the caret sitting right after the opening {@code '('} before any argument is typed (e.g. {@code "left(|"}): any non-EOF preceding token is sufficient to
     * conclude the caret is inside a parenthesised expression where column suggestions are appropriate.
     */
    private static boolean isInsideFunctionArg(Parser parser, int tokenIndex)
    {
        Token token = parser.getInputStream()
                .get(tokenIndex);
        if (token.getType() == TSqlLexer.ID)
        {
            for (int i = tokenIndex - 1; i >= 0; i--)
            {
                Token t = parser.getInputStream()
                        .get(i);
                if (t.getChannel() != Token.DEFAULT_CHANNEL)
                {
                    continue;
                }
                int type = t.getType();
                if (type == TSqlLexer.DOT
                        || type == TSqlLexer.ID)
                {
                    // Still inside a dotted identifier chain (e.g. "schema.table.col")
                    continue;
                }
                // Found the first token that is not part of the chain
                return type == TSqlLexer.LR_BRACKET;
            }
            return false;
        }
        // Caret IS the opening '(' — the caret sits right after '(' before any argument is typed.
        // Any non-EOF preceding token is sufficient: column suggestions are appropriate for the
        // first argument position regardless of whether this is a scalar function, a grouped
        // condition, or any other parenthesised expression context.
        if (token.getType() == TSqlLexer.LR_BRACKET)
        {
            for (int i = tokenIndex - 1; i >= 0; i--)
            {
                Token t = parser.getInputStream()
                        .get(i);
                if (t.getChannel() != Token.DEFAULT_CHANNEL)
                {
                    continue;
                }
                return t.getType() != Token.EOF;
            }
        }
        return false;
    }

    /**
     * Returns true when the caret is positioned inside a dotted qualifier (e.g. after {@code "alias."} or {@code "schema."}) so that column completions are appropriate. Two cases are handled:
     * <ol>
     * <li><b>Token IS a DOT</b>: {@code suggestTokenIndex} points directly to the DOT token (caret sits right after the dot in the character stream). The preceding default-channel token must be an
     * identifier.</li>
     * <li><b>Token is hidden-channel, preceded by a DOT</b>: ANTLR's single-token-insertion error recovery inserts a synthetic identifier between the DOT and the next real token (e.g. {@code FROM}).
     * The synthetic token is not in the character stream, so {@link AntlrDocumentParser#findTokenFromOffset} resolves the caret to a hidden-channel space token that comes before the synthetic ID.
     * Here we detect that pattern by checking that the nearest preceding default-channel token is a DOT and the one before that is an identifier.</li>
     * </ol>
     * NOTE: this check fires AFTER {@link #isTableSourceContext} has already returned {@code false}, so table-source positions like {@code "FROM dbo."} are handled earlier in the flow and will not
     * accidentally reach this check.
     */
    private static boolean isInsideDottedQualifier(Parser parser, int tokenIndex)
    {
        Token token = parser.getInputStream()
                .get(tokenIndex);
        // Case 1: the suggest token itself is a DOT
        if (token.getType() == TSqlLexer.DOT)
        {
            for (int i = tokenIndex - 1; i >= 0; i--)
            {
                Token t = parser.getInputStream()
                        .get(i);
                if (t.getChannel() != Token.DEFAULT_CHANNEL)
                {
                    continue;
                }
                return t.getType() == TSqlLexer.ID;
            }
            return false;
        }
        // Case 2: hidden-channel token whose nearest preceding DEFAULT_CHANNEL token is a DOT.
        // This happens when ANTLR's single-token-insertion error recovery inserts a synthetic
        // identifier after the dot (e.g. "SELECT a. FROM" → synthetic ID inserted between "." and
        // "FROM"), causing findTokenFromOffset to land on the space token rather than the dot.
        for (int i = tokenIndex - 1; i >= 0; i--)
        {
            Token t = parser.getInputStream()
                    .get(i);
            if (t.getChannel() != Token.DEFAULT_CHANNEL)
            {
                continue;
            }
            if (t.getType() != TSqlLexer.DOT)
            {
                return false;
            }
            // Nearest preceding DEFAULT_CHANNEL token is a DOT; check the one before it is an ID
            for (int j = i - 1; j >= 0; j--)
            {
                Token u = parser.getInputStream()
                        .get(j);
                if (u.getChannel() != Token.DEFAULT_CHANNEL)
                {
                    continue;
                }
                return u.getType() == TSqlLexer.ID;
            }
            return false;
        }
        return false;
    }

    /**
     * Returns true when the dotted qualifier at {@code tokenIndex} is directly preceded (through an optional identifier chain) by a table-source keyword: {@code FROM}, {@code JOIN}, {@code INTO},
     * {@code UPDATE}, {@code APPLY}, or {@code MERGE}. Used as a token-stream fallback when ANTLR error recovery detaches the DOT from a {@link Table_source_itemContext} and
     * {@link #isTableSourceContext} returns false. Both cases from {@link #isInsideDottedQualifier} are handled:
     * <ol>
     * <li><b>Token IS a DOT</b>: walk backward past the identifier chain (ID/DOT tokens) to find the preceding keyword.</li>
     * <li><b>Token is hidden-channel</b>: first locate the nearest preceding DOT on the default channel, then walk backward past the identifier chain.</li>
     * </ol>
     */
    private static boolean isInsideTableSourceDottedQualifier(Parser parser, int tokenIndex)
    {
        Token token = parser.getInputStream()
                .get(tokenIndex);
        int dotIndex;
        if (token.getType() == TSqlLexer.DOT)
        {
            dotIndex = tokenIndex;
        }
        else
        {
            // Hidden-channel token: find the nearest preceding DOT on the default channel
            dotIndex = -1;
            for (int i = tokenIndex - 1; i >= 0; i--)
            {
                Token t = parser.getInputStream()
                        .get(i);
                if (t.getChannel() != Token.DEFAULT_CHANNEL)
                {
                    continue;
                }
                if (t.getType() == TSqlLexer.DOT)
                {
                    dotIndex = i;
                }
                break;
            }
            if (dotIndex < 0)
            {
                return false;
            }
        }
        // Walk backward past the identifier chain (ID/DOT tokens) before the DOT
        for (int i = dotIndex - 1; i >= 0; i--)
        {
            Token t = parser.getInputStream()
                    .get(i);
            if (t.getChannel() != Token.DEFAULT_CHANNEL)
            {
                continue;
            }
            int type = t.getType();
            if (type == TSqlLexer.DOT
                    || type == TSqlLexer.ID)
            {
                // Still inside a dotted identifier chain (e.g. "server.database.schema")
                continue;
            }
            // First non-identifier/non-dot token before the chain — check for table-source keywords
            return type == TSqlParser.FROM
                    || type == TSqlParser.JOIN
                    || type == TSqlParser.INTO
                    || type == TSqlParser.UPDATE
                    || type == TSqlParser.APPLY
                    || type == TSqlParser.MERGE;
        }
        return false;
    }

    /**
     * Returns true if the token at {@code tokenIndex} is a COMMA or an expression-preceding operator (e.g. {@code =}, {@code AND}, {@code +}), or if the nearest preceding default-channel token is one
     * of those. The latter handles the case where the caret lands exactly on the first character of a token that follows such a token (e.g. "LEFT(col, |)" where findTokenFromOffset returns the ")"
     * token, or "col = |" where findTokenFromOffset returns EOF).
     */
    private boolean isPrecededByOrIsExpressionOperator(Parser parser, int tokenIndex)
    {
        Token token = parser.getInputStream()
                .get(tokenIndex);
        if (token.getType() == TSqlParser.COMMA
                || EXPRESSION_PRECEDING_OPERATOR_TOKENS.contains(token.getType()))
        {
            return true;
        }
        for (int i = tokenIndex - 1; i >= 0; i--)
        {
            Token t = parser.getInputStream()
                    .get(i);
            if (t.getChannel() == Token.DEFAULT_CHANNEL)
            {
                return t.getType() == TSqlParser.COMMA
                        || EXPRESSION_PRECEDING_OPERATOR_TOKENS.contains(t.getType());
            }
        }
        return false;
    }

    private CompletionResult suggestProcedureParameters(Execute_bodyContext execBody)
    {
        Func_proc_name_server_database_schemaContext procNameCtx = execBody.func_proc_name_server_database_schema();
        Pair<Interval, ObjectName> procRef = getProcedureFunction(procNameCtx);
        if (procRef == null)
        {
            return null;
        }

        String database = Objects.toString(procRef.getValue()
                .getCatalog(), connectionContext.getDatabase());
        Catalog catalog = crawlService.getCatalog(connectionContext, database);
        if (catalog == null)
        {
            return new CompletionResult(emptyList(), true);
        }

        String schema = procRef.getValue()
                .getSchema();
        String name = procRef.getValue()
                .getName();

        Routine routine = catalog.getRoutines()
                .stream()
                .filter(r -> r.getType() == Routine.Type.PROCEDURE
                        && CI.equals(r.getName(), name)
                        && (isBlank(schema)
                                || CI.equals(r.getSchema(), schema)))
                .findFirst()
                .orElse(null);

        if (routine == null
                || routine.getParameters()
                        .isEmpty())
        {
            return null;
        }

        // Collect already-used named parameters to skip them
        Set<String> usedParams = new HashSet<>();
        Execute_statement_argContext argCtx = execBody.execute_statement_arg();
        if (argCtx != null)
        {
            collectUsedNamedParams(argCtx, usedParams);
        }

        List<CompletionItem> items = new ArrayList<>();
        for (RoutineParameter param : routine.getParameters())
        {
            String paramName = param.getName();
            if (usedParams.contains(paramName.toLowerCase()))
            {
                continue;
            }
            String definition = Column.getDefinition(param.getType(), param.getMaxLength(), param.getPrecision(), param.getScale(), param.isNullable());
            String description = """
                    <html>
                    <h3>%s</h3>
                    <ul>
                    <li>Type: <strong>%s</strong></li>
                    <li>Nullable: <strong>%s</strong></li>
                    %s
                    </ul>
                    """.formatted(paramName, definition, param.isNullable() ? "yes"
                    : "no",
                    param.isOutput() ? "<li>Output: <strong>yes</strong></li>"
                            : "");
            // Strip '@' from matchPart so typing partial name after '@' filters correctly
            String matchPart = paramName.startsWith("@") ? paramName.substring(1)
                    : paramName;
            items.add(new CompletionItem(List.of(matchPart), paramName + " = ", null, description, icons.atIcon, 5));
        }

        return new CompletionResult(items, false);
    }

    private void collectUsedNamedParams(Execute_statement_argContext ctx, Set<String> usedParams)
    {
        for (Execute_statement_arg_namedContext named : ctx.execute_statement_arg_named())
        {
            if (named.name != null)
            {
                usedParams.add(named.name.getText()
                        .toLowerCase());
            }
        }
        for (Execute_statement_argContext nested : ctx.execute_statement_arg())
        {
            collectUsedNamedParams(nested, usedParams);
        }
    }

    @Override
    protected CompletionResult getDialectSpecificCompletionItems(TokenOffset tokenOffset)
    {
        // Check for stored procedure parameter context using offset-based tree traversal.
        // Parent-chain walking is unreliable after a trailing comma because ANTLR error recovery
        // can set Execute_bodyContext.stop to the last successfully parsed token (before the comma),
        // leaving the comma token orphaned outside the context's subtree.
        // Restrict the search to the nearest preceding statement so that an EXEC in an earlier
        // statement cannot match when the caret is in a later SELECT/UPDATE/etc. statement.
        ParserRuleContext nearestStmtForExec = findNearestPrecedingStatementCtx(tokenOffset.caretOffset());
        ParseTree execSearchRoot = nearestStmtForExec != null ? nearestStmtForExec
                : context;
        Execute_bodyContext execBody = execSearchRoot != null ? findExecuteBodyByOffset(execSearchRoot, tokenOffset.caretOffset())
                : null;
        if (execBody != null)
        {
            return suggestProcedureParameters(execBody);
        }
        // Table-source dotted qualifier fallback: ANTLR error recovery can detach the DOT from
        // Table_source_itemContext in complex queries (e.g. "INNER JOIN dbo." after an already-
        // parsed table source), causing isTableSourceContext to return false. Detect the table-
        // source position by scanning backward in the token stream for a table-source keyword
        // (FROM, JOIN, INTO, UPDATE, APPLY, MERGE) before the identifier chain and suggest table
        // sources directly, bypassing the column-suggestion path below.
        if (parser != null
                && tokenOffset.suggestTokenIndex() >= 0
                && isInsideDottedQualifier(parser, tokenOffset.suggestTokenIndex())
                && isInsideTableSourceDottedQualifier(parser, tokenOffset.suggestTokenIndex()))
        {
            return suggestTableSources();
        }
        // Dotted qualifier (e.g. "a." or "b." inside an EXISTS subquery):
        // The original broken tree may not correctly connect the DOT to the inner
        // query_specification scope (ANTLR error recovery can detach the DOT or put it
        // inside an error node). Use mini-parse to get a clean tree node properly anchored
        // inside the current subquery, so collectTableSourceAliases finds the inner alias.
        // Guard: skip table-source positions (e.g. "FROM dbo.") where isTableSourceContext fires.
        if (parser != null
                && tokenOffset.suggestTokenIndex() >= 0
                && isInsideDottedQualifier(parser, tokenOffset.suggestTokenIndex())
                && !isTableSourceContext(tokenOffset.tree())
                && !isTableSourceContext(tokenOffset.prevTree()))
        {
            ParseTree effectiveNodeForCtx = resolveEffectiveNode(tokenOffset);
            ParserRuleContext statementCtxForDot = findEnclosingStatementCtx(effectiveNodeForCtx);
            if (statementCtxForDot == null)
            {
                statementCtxForDot = findNearestPrecedingStatementCtx(tokenOffset.caretOffset());
            }
            if (statementCtxForDot != null)
            {
                ParseTree miniNode = miniParseStatementNode(statementCtxForDot, tokenOffset.caretOffset());
                if (miniNode != null)
                {
                    return suggestColumns(miniNode);
                }
            }
        }
        return null;
    }

    @Override
    protected boolean isExpressionContext(ParseTree tree)
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

    @Override
    protected boolean isTableSourceContext(ParseTree tree)
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

    @Override
    protected CompletionResult suggestColumns(ParseTree node)
    {
        Set<TableAlias> aliases = TableSourceAliasCollector.collectTableSourceAliases(node, connectionContext.getDatabase());

        if (aliases.isEmpty())
        {
            return null;
        }

        List<CompletionItem> items = new ArrayList<>();
        boolean partialResult = false;
        // Cache catalogs by database name to avoid redundant crawl calls for the same database
        // (e.g., SELECT * FROM t a JOIN t b would otherwise fetch the catalog twice).
        Map<String, Catalog> catalogCache = new HashMap<>();
        for (TableAlias ta : aliases)
        {
            String alias = ta.alias();
            if (ta.type() == TableAliasType.TABLE
                    || ta.type() == TableAliasType.TABLE_FUNCTION
                    || ta.type() == TableAliasType.CHANGETABLE)
            {
                String dbKey = Objects.toString(ta.objectName()
                        .getCatalog(), connectionContext.getDatabase());
                Catalog catalog = catalogCache.computeIfAbsent(dbKey, db -> crawlService.getCatalog(connectionContext, db));
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
                            && !CI.equals(schema, ts.getSchema()))
                    {
                        continue;
                    }
                    else if (!CI.equals(name, ts.getName()))
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
                    || ta.type() == TableAliasType.SUBQUERY
                    || ta.type() == TableAliasType.BUILTIN)
            {
                List<String> list = (ta.type() == TableAliasType.SUBQUERY
                        || ta.type() == TableAliasType.BUILTIN) ? ta.extendedColumns()
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

        for (SignatureHint hint : BUILTIN_HINTS.values())
        {
            items.add(new CompletionItem(List.of(hint.functionName()), hint.functionName(), null, null, icons.boltIcon, -1));
        }

        return new CompletionResult(items, partialResult);
    }

    @Override
    protected CompletionResult suggestTableSources()
    {
        Catalog catalog = crawlService.getCatalog(connectionContext, connectionContext.getDatabase());
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

        result.addAll(BUILTIN_TABLE_FUNCTIONS.keySet()
                .stream()
                .map(f -> new CompletionItem(f, null, icons.wpformsIcon))
                .collect(toList()));

        return new CompletionResult(result, partialResult);
    }

    @Override
    protected CompletionResult getExpressionFallbackColumns(TokenOffset tokenOffset, ParserRuleContext statementCtx, boolean expressionContextDetected)
    {
        // When ANTLR's panic-mode error recovery disconnects the caret token from the main
        // parse tree (e.g., "LEFT(col, |)" causes the comma and close-paren to become error
        // nodes in a separate BatchContext), isExpressionContext above returns false even though the
        // caret is logically inside a scalar function argument. In this case the SELECT part
        // of the statement is still in a valid Query_specificationContext whose table_sources
        // are intact. Search the statement-context subtrees for a Query_specificationContext
        // and use it to collect aliases for column suggestions, bypassing the broken parent-chain walk.
        //
        // This fallback is guarded by an operator/comma check: a comma or an expression-preceding
        // operator (=, AND, +, …) as the caret's matched token means the caret sits inside an
        // expression context. Applying it unconditionally would intercept table-source positions
        // (FROM, JOIN) where C3 should return RULE_table_source_item instead.
        //
        // Error recovery can create multiple small Sql_clausesContext nodes (e.g. one for just "(")
        // close to the caret, hiding the real SELECT's context. Search all collected statement
        // contexts (not only the nearest one) and try each in descending start-offset order so
        // that the most-recent SELECT before the caret is preferred.
        //
        // The check also covers the case where the caret is positioned exactly at the first
        // character of the token that follows the operator/comma (e.g. "LEFT(col, |)" where "|" is
        // on the closing paren, or "col = |" where findTokenFromOffset returns EOF). Checking the
        // nearest preceding default-channel token detects this situation and triggers the same
        // query-spec fallback.
        //
        // expressionContextDetected extends the same fallback to the case where isExpressionContext()
        // returned true above but suggestColumns() returned null because error recovery detached
        // the caret token from the enclosing Query_specificationContext.
        //
        // isInsideFunctionArg extends it further to the case where isExpressionContext() returned FALSE
        // (i.e. ANTLR's error recovery disconnected the caret's identifier entirely from any
        // ExpressionContext) but the token stream clearly shows a function-argument position:
        // an identifier preceded (through zero or more DOT+ID steps) by an open paren '('.
        // Examples: "left(c|" (directly after paren) and "left(t.c|" (dotted qualifier).
        // This check deliberately does NOT fire for bare WHERE-clause identifiers like "WHERE t.c|"
        // (preceded by WHERE keyword, not '('), keeping table-source completions unaffected.
        if (expressionContextDetected
                || isPrecededByOrIsExpressionOperator(parser, tokenOffset.suggestTokenIndex())
                || isInsideFunctionArg(parser, tokenOffset.suggestTokenIndex())
                || isInsideDottedQualifier(parser, tokenOffset.suggestTokenIndex()))
        {
            // When the caret is right after a dot (e.g. "b." inside an EXISTS subquery),
            // use miniParseStatementNode first so that collectTableSourceAliases starts from
            // the correct inner position and finds the inner alias rather than only the outer
            // query's aliases (which findQuerySpecInSubtree BFS would return first).
            if (statementCtx != null
                    && isInsideDottedQualifier(parser, tokenOffset.suggestTokenIndex()))
            {
                ParseTree miniNode = miniParseStatementNode(statementCtx, tokenOffset.caretOffset());
                if (miniNode != null)
                {
                    CompletionResult miniResult = suggestColumns(miniNode);
                    if (miniResult != null)
                    {
                        return miniResult;
                    }
                }
            }
            Query_specificationContext querySpec = null;
            if (statementCtx != null)
            {
                querySpec = findQuerySpecInSubtree(statementCtx);
            }
            if (querySpec == null)
            {
                // Normal statementCtx did not contain a query_spec; scan all collected contexts.
                int caretOff = tokenOffset.caretOffset();
                querySpec = statementContexts.stream()
                        .filter(ctx -> ctx.start != null
                                && ctx.start.getStartIndex() <= caretOff)
                        .sorted(Comparator.<ParserRuleContext, Integer>comparing(ctx -> ctx.start.getStartIndex())
                                .reversed())
                        .map(SqlServerDocumentParser::findQuerySpecInSubtree)
                        .filter(q -> q != null)
                        .findFirst()
                        .orElse(null);
            }
            if (querySpec != null)
            {
                CompletionResult colResult = suggestColumns(querySpec);
                if (colResult != null)
                {
                    return colResult;
                }
                // suggestColumns returned null — likely because table_sources is null on the
                // querySpec due to ANTLR error recovery detaching the FROM clause (e.g. incomplete
                // SELECT list like "SELECT a. FROM ..."). Re-parse the statement in isolation via
                // miniParseStatementNode to obtain a clean tree where FROM is properly attached.
                if (statementCtx != null)
                {
                    ParseTree miniNode = miniParseStatementNode(statementCtx, tokenOffset.caretOffset());
                    if (miniNode != null)
                    {
                        return suggestColumns(miniNode);
                    }
                }
            }
        }
        return null;
    }

    @Override
    protected CompletionResult getC3CompletionItems(CandidatesCollection candidates, ParseTree effectiveNode, ParserRuleContext statementCtx, TokenOffset tokenOffset)
    {
        if (candidates.rules.containsKey(TSqlParser.RULE_table_source_item))
        {
            return suggestTableSources();
        }

        if (candidates.rules.containsKey(TSqlParser.RULE_func_proc_name_server_database_schema))
        {
            Catalog catalog = crawlService.getCatalog(connectionContext, connectionContext.getDatabase());
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
            // When the main parse tree is broken (parse errors above the caret caused panic-mode
            // recovery to attach statement tokens as error nodes at tsql_file level), effectiveNode
            // has no Select_statementContext in its parent chain, so collectTableSourceAliases returns
            // empty. Fall back to a mini-parse of the current statement for a clean tree.
            ParseTree nodeForColumns = effectiveNode;
            if (TableSourceAliasCollector.collectTableSourceAliases(nodeForColumns, connectionContext.getDatabase())
                    .isEmpty()
                    && statementCtx != null)
            {
                ParseTree miniNode = miniParseStatementNode(statementCtx, tokenOffset.caretOffset());
                if (miniNode != null)
                {
                    nodeForColumns = miniNode;
                }
            }
            return suggestColumns(nodeForColumns);
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
    protected boolean isRoutineDefinitionContext(ParserRuleContext ctx)
    {
        return ctx instanceof Create_or_alter_procedureContext
                || ctx instanceof Create_or_alter_functionContext;
    }

    @Override
    protected int getInTokenId()
    {
        return TSqlLexer.IN;
    }

    @Override
    protected JoinOnContext detectJoinOnContext(TokenOffset tokenOffset, String database)
    {
        // Trigger only when prevTree is the ON keyword of a join_on clause
        if (!(tokenOffset.prevTree() instanceof TerminalNode prevTn)
                || prevTn.getSymbol()
                        .getType() != TSqlLexer.ON)
        {
            return null;
        }

        // Walk up to confirm the ON belongs to a join_on (not e.g. CREATE INDEX ... ON)
        Join_onContext joinOn = null;
        ParseTree current = prevTn;
        while (current != null)
        {
            if (current instanceof Join_onContext joc)
            {
                joinOn = joc;
                break;
            }
            current = current.getParent();
        }
        if (joinOn == null)
        {
            return null;
        }

        // Extract the RHS table alias from the join_on's table_source
        if (joinOn.source == null)
        {
            return null;
        }
        Table_source_itemContext rhsItem = joinOn.source.table_source_item();
        if (rhsItem == null
                || rhsItem.full_table_name() == null)
        {
            // Subqueries, functions, etc. — no FK suggestion possible
            return null;
        }
        Full_table_nameContext ftc = rhsItem.full_table_name();
        String rhsAlias = rhsItem.as_table_alias() != null ? rhsItem.as_table_alias()
                .getText()
                : "";
        String rhsDb = ftc.database != null ? unquote(ftc.database.getText())
                : database;
        String rhsSchema = ftc.schema != null ? unquote(ftc.schema.getText())
                : "";
        String rhsTable = ftc.table != null ? unquote(ftc.table.getText())
                : "";
        if (rhsTable.isEmpty())
        {
            return null;
        }

        ObjectName rhsObjName = new ObjectName(rhsDb, rhsSchema, rhsTable);
        TableAlias rhsTableAlias = new TableAlias(rhsAlias, rhsObjName, emptyList(), TableAliasType.TABLE);
        // Collect all visible aliases at the caret (includes both LHS and RHS tables)
        Set<TableAlias> allAliases = TableSourceAliasCollector.collectTableSourceAliases(prevTn, database);
        return new JoinOnContext(rhsTableAlias, allAliases);
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
            if (fpCtx.procedure != null)
            {
                databaseCtx = fpCtx.database;
                schemaCtx = fpCtx.schema;
                procedureCtx = fpCtx.procedure;
            }
            else if (fpCtx.func_proc_name_database_schema() != null)
            {
                Func_proc_name_database_schemaContext dbSchemaCtx = fpCtx.func_proc_name_database_schema();
                if (dbSchemaCtx.procedure != null)
                {
                    databaseCtx = dbSchemaCtx.database;
                    schemaCtx = dbSchemaCtx.schema;
                    procedureCtx = dbSchemaCtx.procedure;
                }
                else if (dbSchemaCtx.func_proc_name_schema() != null)
                {
                    schemaCtx = dbSchemaCtx.func_proc_name_schema().schema;
                    procedureCtx = dbSchemaCtx.func_proc_name_schema().procedure;
                }
            }
        }
        else if (ctx instanceof Func_proc_name_database_schemaContext fpCtx)
        {
            if (fpCtx.procedure != null)
            {
                databaseCtx = fpCtx.database;
                schemaCtx = fpCtx.schema;
                procedureCtx = fpCtx.procedure;
            }
            else if (fpCtx.func_proc_name_schema() != null)
            {
                schemaCtx = fpCtx.func_proc_name_schema().schema;
                procedureCtx = fpCtx.func_proc_name_schema().procedure;
            }
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
        private final IConnectionContext connectionContext;

        ValidateVisitor(Map<String, List<String>> temporaryTables, List<ParseItem> parseResult, IConnectionContext connectionContext, CatalogCrawlService crawlService)
        {
            this.temporaryTables = temporaryTables;
            this.parseResult = parseResult;
            this.connectionContext = connectionContext;
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
                        : connectionContext.getDatabase();
                String schema = ctx.schema != null ? ctx.schema.getText()
                        : "";
                Catalog catalog = crawlService.getCatalog(connectionContext, database);

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

                    // If no schema/database qualifier and the name is a known built-in TVF, treat as BUILTIN
                    if (databaseCtx == null
                            && schemaCtx == null)
                    {
                        List<String> builtinColumns = BUILTIN_TABLE_FUNCTIONS.get(name.toUpperCase());
                        if (builtinColumns != null)
                        {
                            type = TableAliasType.BUILTIN;
                            extendedColumns = builtinColumns;
                        }
                    }
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
                            .change_table_version().versiontable.database;
                    schemaCtx = ctx.change_table()
                            .change_table_version().versiontable.schema;
                    tableCtx = ctx.change_table()
                            .change_table_version().versiontable.table;
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
            String name = ctx.table != null ? ctx.table.getText()
                    : "";

            TableAliasType type = TableAliasType.TABLE;

            if (ctx.table != null
                    && ctx.table.TEMP_ID() != null)
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
