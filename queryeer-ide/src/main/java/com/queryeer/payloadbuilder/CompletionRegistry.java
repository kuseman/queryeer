package com.queryeer.payloadbuilder;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.event.ActionEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.tuple.Pair;
import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.queryeer.IconFactory;
import com.queryeer.api.editor.ITextEditorDocumentParser;
import com.queryeer.api.extensions.Inject;
import com.queryeer.api.extensions.payloadbuilder.ICatalogExtension;
import com.queryeer.api.extensions.payloadbuilder.ICompletionProvider;
import com.queryeer.api.extensions.payloadbuilder.ICompletionProvider.ColumnMeta;
import com.queryeer.api.extensions.payloadbuilder.ICompletionProvider.TableMeta;
import com.queryeer.api.service.IEventBus;
import com.queryeer.domain.Task;
import com.queryeer.event.TaskCompletedEvent;
import com.queryeer.event.TaskStartedEvent;
import com.queryeer.payloadbuilder.CatalogsConfigurable.QueryeerCatalog;
import com.queryeer.payloadbuilder.PLBDocumentParser.TableSource;

import se.kuseman.payloadbuilder.api.QualifiedName;
import se.kuseman.payloadbuilder.api.catalog.Catalog;
import se.kuseman.payloadbuilder.api.catalog.Column;
import se.kuseman.payloadbuilder.api.catalog.FunctionInfo;
import se.kuseman.payloadbuilder.api.catalog.FunctionInfo.FunctionType;
import se.kuseman.payloadbuilder.api.catalog.Schema;
import se.kuseman.payloadbuilder.api.catalog.TableFunctionInfo;
import se.kuseman.payloadbuilder.core.cache.Cache;
import se.kuseman.payloadbuilder.core.cache.InMemoryGenericCache;
import se.kuseman.payloadbuilder.core.catalog.system.SystemCatalog;
import se.kuseman.payloadbuilder.core.execution.QuerySession;

/**
 * Completion registry that has a registry of completion information (tables/columns/functions etc.) for {@link ICatalogExtension}'s
 */
@Inject
class CompletionRegistry
{
    private static final List<String> EXPRESSION_FUNCTIONS = List.of("cast", "dateadd", "datepart", "datediff");

    private static final Icon COLUMNS = IconFactory.of(FontAwesome.COLUMNS);
    private static final Icon GEAR = IconFactory.of(FontAwesome.GEAR);
    private static final Icon TABLE = IconFactory.of(FontAwesome.TABLE);
    private static final Icon TIMES_CIRCLE = IconFactory.of(FontAwesome.TIMES_CIRCLE);

    private static final Logger LOGGER = LoggerFactory.getLogger(CompletionRegistry.class);
    private static final QualifiedName TABLE_META_CACHE = QualifiedName.of("TableMeta");
    private static final Duration TABLE_META_CACHE_TTL = Duration.ofMinutes(10);
    private static final InMemoryGenericCache CACHE = new InMemoryGenericCache("AutoComplection", true, true);

    private final IEventBus eventBus;

    CompletionRegistry(IEventBus eventBus)
    {
        this.eventBus = requireNonNull(eventBus, "eventBus");
    }

    /** Get table completions for session */
    List<ITextEditorDocumentParser.CompletionItem> getTableCompletions(QuerySession session, List<QueryeerCatalog> catalogs, MutableBoolean partialResult)
    {
        List<ITextEditorDocumentParser.CompletionItem> result = new ArrayList<>();
        Map<String, Collection<TableMeta>> metasByCatalogAlias = getTableMetas(session, catalogs, partialResult);
        for (Entry<String, Collection<TableMeta>> e : metasByCatalogAlias.entrySet())
        {
            String catalogAlias = e.getKey();
            Collection<TableMeta> metas = e.getValue();
            for (TableMeta meta : metas)
            {
                QualifiedName matchParts = meta.getName()
                        .prepend(catalogAlias);
                String replacementText = catalogAlias + "#" + meta.getName();
                result.add(new ITextEditorDocumentParser.CompletionItem(matchParts.getParts(), replacementText, null, meta.getDescription(), TABLE, 8));
            }
        }
        return result;
    }

    /** Get column completions for session and provided table sources */
    List<ITextEditorDocumentParser.CompletionItem> getColumnCompletions(QuerySession session, List<QueryeerCatalog> catalogs, Map<String, TableSource> tableSources, MutableBoolean partialResult)
    {
        List<ITextEditorDocumentParser.CompletionItem> result = new ArrayList<>();
        Map<String, Collection<TableMeta>> metasByCatalogAlias = getTableMetas(session, catalogs, partialResult);

        for (Entry<String, TableSource> e : tableSources.entrySet())
        {
            if (e.getValue()
                    .isFunction())
            {
                continue;
            }

            Collection<Collection<TableMeta>> tableMetaForCatalogAlias;
            String catalogAlias = e.getValue()
                    .catalogAlias();

            // Use all metas we have if there is no catalog alias
            if (isBlank(catalogAlias))
            {
                tableMetaForCatalogAlias = metasByCatalogAlias.values();
            }
            // ... else pick the one we are referencing
            else
            {
                tableMetaForCatalogAlias = singletonList(metasByCatalogAlias.get(catalogAlias));
            }

            for (Collection<TableMeta> metaCollection : tableMetaForCatalogAlias)
            {
                if (metaCollection == null)
                {
                    continue;
                }

                for (TableMeta meta : metaCollection)
                {
                    // Not a table we have in the scope, skip
                    if (!containsIgnoreCase(meta.getName(), e.getValue()
                            .qname()))
                    {
                        continue;
                    }

                    for (ColumnMeta column : meta.getColumns())
                    {
                        QualifiedName matchParts = isBlank(e.getKey()) ? column.getName()
                                : column.getName()
                                        .prepend(e.getKey());

                        String replacementText = matchParts.toString();
                        result.add(new ITextEditorDocumentParser.CompletionItem(matchParts.getParts(), replacementText, null, column.getDescription(), COLUMNS, 10));
                    }
                }
            }
        }

        List<Pair<String, Catalog>> catalogPairs = new ArrayList<>(catalogs.size() + 1);
        for (QueryeerCatalog model : catalogs)
        {
            if (model.getCatalogExtension() == null)
            {
                continue;
            }

            catalogPairs.add(Pair.of(model.getAlias(), model.getCatalogExtension()
                    .getCatalog()));
        }
        catalogPairs.add(Pair.of(Catalog.SYSTEM_CATALOG_ALIAS, SystemCatalog.get()));

        // Table function columns
        for (Entry<String, TableSource> e : tableSources.entrySet())
        {
            if (!e.getValue()
                    .isFunction())
            {
                continue;
            }

            for (Pair<String, Catalog> pair : catalogPairs)
            {
                // Filter out non interesting catalogs
                if (!isBlank(e.getValue()
                        .catalogAlias())
                        && !equalsIgnoreCase(pair.getKey(), e.getValue()
                                .catalogAlias()))
                {
                    continue;
                }

                Catalog catalog = pair.getValue();

                TableFunctionInfo function = catalog.getTableFunction(e.getValue()
                        .qname()
                        .getFirst());
                if (function == null)
                {
                    continue;
                }

                Schema schema = function.getSchema(emptyList());
                if (Schema.EMPTY.equals(schema))
                {
                    continue;
                }

                for (Column column : schema.getColumns())
                {
                    QualifiedName matchParts = isBlank(e.getKey()) ? QualifiedName.of(column.getName())
                            : QualifiedName.of(e.getKey(), column.getName());

                    String replacementText = matchParts.toString();
                    result.add(new ITextEditorDocumentParser.CompletionItem(matchParts.getParts(), replacementText, null, null, GEAR, 9));
                }
            }

        }

        return result;
    }

    /** Get table functions from session */
    List<ITextEditorDocumentParser.CompletionItem> getTableFunctionCompletions(QuerySession session)
    {
        return getFunctions(session, false);
    }

    /** Get scalar functions from session */
    List<ITextEditorDocumentParser.CompletionItem> getScalarFunctionCompletions(QuerySession session)
    {
        return getFunctions(session, true);
    }

    private Map<String, Collection<TableMeta>> getTableMetas(QuerySession session, List<QueryeerCatalog> catalogs, MutableBoolean partialResult)
    {
        Map<String, Collection<TableMeta>> result = new HashMap<>();
        for (QueryeerCatalog catalogModel : catalogs)
        {
            if (catalogModel.isDisabled())
            {
                continue;
            }

            ICatalogExtension catalogExtension = catalogModel.getCatalogExtension();
            ICompletionProvider autoCompletionProvider = catalogExtension.getAutoCompletionProvider();
            String catalogAlias = catalogModel.getAlias();
            if (autoCompletionProvider == null)
            {
                continue;
            }
            // Skip provider, not ready
            else if (!autoCompletionProvider.enabled(session, catalogAlias))
            {
                continue;
            }

            Collection<TableMeta> metas;
            Object providerCacheKey = autoCompletionProvider.getTableMetaCacheKey(session, catalogAlias);
            if (providerCacheKey != null)
            {
                final String description = autoCompletionProvider.getDescription(session, catalogAlias);
                final CacheKey cacheKey = new CacheKey(catalogAlias, providerCacheKey);
                metas = CACHE.computIfAbsent(TABLE_META_CACHE, cacheKey, TABLE_META_CACHE_TTL, () ->
                {
                    Action cacheFlushAction = new AbstractAction("Clear cache", TIMES_CIRCLE)
                    {
                        {
                            putValue(SHORT_DESCRIPTION, "Clear cache");
                        }

                        @Override
                        public void actionPerformed(ActionEvent e)
                        {
                            Cache cache = CACHE.getCache(TABLE_META_CACHE);
                            if (cache != null)
                            {
                                cache.flush(cacheKey);
                            }
                        }
                    };

                    // We use the cache key as task key here
                    Task task = new Task(cacheKey, "Payloadbuilder Auto complete", description, asList(cacheFlushAction));
                    eventBus.publish(new TaskStartedEvent(task));

                    Throwable t = null;
                    try
                    {
                        return autoCompletionProvider.getTableCompletionMeta(session, catalogAlias);
                    }
                    catch (Throwable e)
                    {
                        t = e;
                        LOGGER.error("Error loading completions from extension: " + catalogExtension.getClass()
                                .getSimpleName(), e);
                        return emptyList();
                    }
                    finally
                    {
                        eventBus.publish(new TaskCompletedEvent(cacheKey, t));
                    }
                });

                if (metas == null)
                {
                    partialResult.setTrue();
                }
            }
            else
            {
                metas = autoCompletionProvider.getTableCompletionMeta(session, catalogAlias);
            }
            // No metas or not loaded yet, move on
            if (metas == null)
            {
                continue;
            }

            result.put(catalogAlias, metas);
        }

        return result;
    }

    private record CacheKey(String catalogAlias, Object providerKey)
    {
    }

    private List<ITextEditorDocumentParser.CompletionItem> getFunctions(QuerySession session, boolean scalar)
    {
        List<ITextEditorDocumentParser.CompletionItem> functions = new ArrayList<>();

        List<Pair<String, Catalog>> catalogs = new ArrayList<>();

        catalogs.add(Pair.of(SystemCatalog.SYSTEM_CATALOG_ALIAS, session.getCatalogRegistry()
                .getSystemCatalog()));
        for (Entry<String, Catalog> entry : session.getCatalogRegistry()
                .getCatalogs())
        {
            catalogs.add(Pair.of(entry.getKey(), entry.getValue()));
        }

        for (Pair<String, Catalog> p : catalogs)
        {
            String catalogAlias = p.getKey();

            for (FunctionInfo function : p.getValue()
                    .getFunctions())
            {
                // Skip operator functions for now
                if (function.getFunctionType() == FunctionType.OPERATOR)
                {
                    continue;
                }

                boolean isScalar = function.getFunctionType() != FunctionType.TABLE;

                if (scalar
                        && !isScalar)
                {
                    continue;
                }
                else if (!scalar
                        && isScalar)
                {
                    continue;
                }

                QualifiedName matchParts = QualifiedName.of(catalogAlias, "#", function.getName());

                String replacementText = catalogAlias + "#" + function.getName();
                functions.add(new ITextEditorDocumentParser.CompletionItem(matchParts.getParts(), replacementText, function.getName() + "()", null, scalar ? GEAR
                        : TABLE, 0));
            }
        }
        if (scalar)
        {
            // Add functions that are expressions and hence aren't discovered in catalog registry
            EXPRESSION_FUNCTIONS.stream()
                    .map(f -> new ITextEditorDocumentParser.CompletionItem(f, f + "()", GEAR))
                    .forEach(functions::add);
        }
        return functions;
    }

    /** Checks if other has any part equal to name's part */
    static final boolean containsIgnoreCase(QualifiedName name, QualifiedName other)
    {
        // match
        // name: sys.objects (from TableMeta)
        // other: objects (from Query)

        // match
        // name: sys.objects (from TableMeta)
        // other: sys.objects (from Query)

        // no match
        // name: sys.objects (from TableMeta)
        // other: objects.sys (from Query)

        // no match
        // name: sys.objects (from TableMeta)
        // other: sys (from Query)

        // All parts of other must be present in name to be considered a contains match
        // We search right to left because qualified names are "more qualified" to the right

        int nameIndex = name.size() - 1;
        int otherIndex = other.size() - 1;
        while (nameIndex >= 0
                && otherIndex >= 0)
        {
            String namePart = name.getParts()
                    .get(nameIndex);
            String otherPart = other.getParts()
                    .get(otherIndex);

            if (!StringUtils.equalsIgnoreCase(namePart, otherPart))
            {
                return false;
            }
            else
            {
                nameIndex--;
                otherIndex--;
            }
        }

        return true;
    }
}
