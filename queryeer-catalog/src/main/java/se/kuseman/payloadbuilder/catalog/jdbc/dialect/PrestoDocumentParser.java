package se.kuseman.payloadbuilder.catalog.jdbc.dialect;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.misc.Interval;
import org.apache.commons.lang3.tuple.Pair;

import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.ITemplateService;

import se.kuseman.payloadbuilder.catalog.jdbc.CatalogCrawlService;
import se.kuseman.payloadbuilder.catalog.jdbc.IConnectionState;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Catalog;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ObjectName;
import se.kuseman.payloadbuilder.catalog.jdbc.model.TableSource;
import se.kuseman.payloadbuilder.jdbc.parser.presto.SqlBaseLexer;
import se.kuseman.payloadbuilder.jdbc.parser.presto.SqlBaseParser;
import se.kuseman.payloadbuilder.jdbc.parser.presto.SqlBaseParser.TableNameContext;

/**
 * Parser that uses preset for an all-around sql parser. Used only for actions/completions/tooltip etc. since we cannot return parse errors here else we will get flooded.
 */
class PrestoDocumentParser extends AntlrDocumentParser<SqlBaseParser.SingleStatementContext>
{
    PrestoDocumentParser(IEventBus eventBus, QueryActionsConfigurable queryActionsConfigurable, CatalogCrawlService catalogCrawler, IConnectionState connectionState, ITemplateService templateService)
    {
        super(eventBus, queryActionsConfigurable, catalogCrawler, connectionState, templateService);
    }

    @Override
    protected Lexer createLexer(CharStream charStream)
    {
        return new SqlBaseLexer(charStream);
    }

    @Override
    protected Parser createParser(TokenStream tokenStream)
    {
        return new SqlBaseParser(tokenStream);
    }

    @Override
    protected SqlBaseParser.SingleStatementContext parse(Parser parser)
    {
        return ((SqlBaseParser) parser).singleStatement();
    }

    @Override
    public List<ParseItem> getParseResult()
    {
        // We don't return any parse error here since there most likely will be a ton of errors
        return emptyList();
    }

    @Override
    protected Set<Integer> getCodeCompleteRuleIndices()
    {
        return Set.of(SqlBaseParser.RULE_aliasedRelation, SqlBaseParser.RULE_expression, SqlBaseParser.RULE_booleanExpression);
    }

    @Override
    protected Set<Integer> getTableSourceRuleIndices()
    {
        return Set.of(SqlBaseParser.RULE_relationPrimary);
    }

    @Override
    protected int getInTokenId()
    {
        return SqlBaseLexer.IN;
    }

    @Override
    protected Set<Integer> getProcedureFunctionsRuleIndices()
    {
        return emptySet();
    }

    @Override
    public boolean supportsToolTips()
    {
        return true;
    }

    @Override
    public boolean supportsLinkActions()
    {
        return true;
    }

    @Override
    protected Pair<Interval, ObjectName> getTableSource(ParserRuleContext ctx)
    {
        if (ctx instanceof TableNameContext nameCtx)
        {
            int start = nameCtx.getStart()
                    .getStartIndex();
            int stop = nameCtx.getStop()
                    .getStopIndex() + 1;

            return Pair.of(Interval.of(start, stop), new ObjectName(null, null, nameCtx.qualifiedName()
                    .identifier(0)
                    .getText()));
        }

        return null;
    }

    @Override
    protected Pair<Interval, ObjectName> getProcedureFunction(ParserRuleContext ctx)
    {
        return null;
    }

    @Override
    protected CompletionResult getCompletionItems(TokenOffset tokenOffset)
    {
        return CompletionResult.EMPTY;
    }

    @Override
    protected Map<String, Object> getTableSourceTooltipModel(ObjectName name)
    {
        String database = Objects.toString(name.getCatalog(), connectionState.getDatabase());
        Catalog catalog = crawlService.getCatalog(connectionState, database);
        if (catalog == null)
        {
            return null;
        }

        TableSource table = catalog.getTableSources()
                .stream()
                .filter(t -> name.getName()
                        .equalsIgnoreCase(t.getName()))
                .findAny()
                .orElse(null);
        if (table == null)
        {
            return null;
        }

        return Map.of(TABLE, table);
    }
}
