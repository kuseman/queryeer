package com.queryeer.completion;

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
import org.apache.commons.lang3.tuple.Pair;
import org.fife.ui.autocomplete.CompletionProvider;
import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.kordamp.ikonli.swing.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.queryeer.api.extensions.catalog.ICatalogExtension;
import com.queryeer.api.extensions.catalog.ICompletionProvider;
import com.queryeer.api.extensions.catalog.ICompletionProvider.ColumnMeta;
import com.queryeer.api.extensions.catalog.ICompletionProvider.TableMeta;
import com.queryeer.api.service.IEventBus;
import com.queryeer.completion.PLBParser.TableSource;
import com.queryeer.domain.ICatalogModel;
import com.queryeer.domain.Task;
import com.queryeer.event.TaskCompletedEvent;
import com.queryeer.event.TaskStartedEvent;

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
class CompletionRegistry
{
    private static final Icon COLUMNS = FontIcon.of(FontAwesome.COLUMNS);
    private static final Icon GEAR = FontIcon.of(FontAwesome.GEAR);
    private static final Icon TABLE = FontIcon.of(FontAwesome.TABLE);
    private static final Icon TIMES_CIRCLE = FontIcon.of(FontAwesome.TIMES_CIRCLE);

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
    List<PLBCompletion> getTableCompletions(CompletionProvider provider, QuerySession session, List<ICatalogModel> catalogs, String textToMatch)
    {
        List<PLBCompletion> result = new ArrayList<>();
        Map<String, Collection<TableMeta>> metasByCatalogAlias = getTableMetas(session, catalogs);
        for (Entry<String, Collection<TableMeta>> e : metasByCatalogAlias.entrySet())
        {
            String catalogAlias = e.getKey();
            Collection<TableMeta> metas = e.getValue();
            for (TableMeta meta : metas)
            {
                String replacementText = catalogAlias + "#" + meta.getName();

                if (StringUtils.isBlank(textToMatch)
                        || StringUtils.startsWithIgnoreCase(catalogAlias, textToMatch)
                        || StringUtils.startsWithIgnoreCase(replacementText, textToMatch)
                        || StringUtils.containsIgnoreCase(replacementText, textToMatch)
                        || startsWith(meta.getName(), textToMatch))
                {
                    result.add(new PLBCompletion(provider, replacementText, null, meta.getDescription(), meta.getTooltip(), TABLE, 8));
                }
            }
        }
        return result;
    }

    /** Get column completions for session and provided table sources */
    List<PLBCompletion> getColumnCompletions(CompletionProvider provider, QuerySession session, List<ICatalogModel> catalogs, Map<String, TableSource> tableSources, String textToMatch)
    {
        List<PLBCompletion> result = new ArrayList<>();
        Map<String, Collection<TableMeta>> metasByCatalogAlias = getTableMetas(session, catalogs);

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
                        String replacementText = isBlank(e.getKey()) ? column.getName()
                                .toString()
                                : column.getName()
                                        .prepend(e.getKey())
                                        .toString();

                        if (StringUtils.isBlank(textToMatch)
                                || StringUtils.startsWithIgnoreCase(e.getKey(), textToMatch)
                                || StringUtils.startsWithIgnoreCase(column.getName()
                                        .getFirst(), textToMatch)
                                || StringUtils.startsWithIgnoreCase(replacementText, textToMatch)
                                || StringUtils.containsIgnoreCase(replacementText, textToMatch))
                        {
                            result.add(new PLBCompletion(provider, replacementText, null, column.getDescription(), column.getTooltip(), COLUMNS, 10));
                        }
                    }
                }
            }
        }

        List<Pair<String, Catalog>> catalogPairs = new ArrayList<>(catalogs.size() + 1);
        for (ICatalogModel model : catalogs)
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
                    String replacementText = isBlank(e.getKey()) ? column.getName()
                            .toString()
                            : e.getKey() + "."
                              + column.getName()
                                      .toString();

                    if (StringUtils.isBlank(textToMatch)
                            || StringUtils.startsWithIgnoreCase(e.getKey(), textToMatch)
                            || StringUtils.startsWithIgnoreCase(column.getName(), textToMatch)
                            || StringUtils.startsWithIgnoreCase(replacementText, textToMatch)
                            || StringUtils.containsIgnoreCase(replacementText, textToMatch))
                    {
                        result.add(new PLBCompletion(provider, replacementText, null, null, null, GEAR, 9));
                    }
                }
            }

        }

        return result;
    }

    /** Get table functions from session */
    List<PLBCompletion> getTableFunctionCompletions(CompletionProvider provider, QuerySession session, String textToMatch)
    {
        return getFunctions(provider, session, false, textToMatch);
    }

    /** Get scalar functions from session */
    List<PLBCompletion> getScalarFunctionCompletions(CompletionProvider provider, QuerySession session, String textToMatch)
    {
        List<PLBCompletion> result = getFunctions(provider, session, true, textToMatch);

        // Add functions that are expressions and hence aren't discovered in catalog registry
        result.add(new PLBCompletion(provider, "cast", "cast()", GEAR));
        result.add(new PLBCompletion(provider, "dateadd", "dateadd()", GEAR));
        result.add(new PLBCompletion(provider, "datepart", "datepart()", GEAR));
        result.add(new PLBCompletion(provider, "datediff", "datediff()", GEAR));
        return result;
    }

    private Map<String, Collection<TableMeta>> getTableMetas(QuerySession session, List<ICatalogModel> catalogs)
    {
        Map<String, Collection<TableMeta>> result = new HashMap<>();
        for (ICatalogModel catalogModel : catalogs)
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

            Object providerCacheKey = autoCompletionProvider.getTableMetaCacheKey(session, catalogAlias);
            if (providerCacheKey == null)
            {
                LOGGER.warn("Catalog extension: " + catalogExtension.getTitle() + " return a null cache key for auto completions, skipping");
                continue;
            }

            final String description = autoCompletionProvider.getDescription(session, catalogAlias);
            final CacheKey cacheKey = new CacheKey(catalogAlias, providerCacheKey);
            Collection<TableMeta> metas = CACHE.computIfAbsent(TABLE_META_CACHE, cacheKey, TABLE_META_CACHE_TTL, () ->
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
                Task task = new Task(cacheKey, "Auto complete", description, asList(cacheFlushAction));
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

    private List<PLBCompletion> getFunctions(CompletionProvider provider, QuerySession session, boolean scalar, String textToMatch)
    {
        List<PLBCompletion> functions = new ArrayList<>();

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

                boolean isScalar = function.getFunctionType() == FunctionType.SCALAR
                        || function.getFunctionType() == FunctionType.SCALAR_AGGREGATE;

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

                String replacementText = catalogAlias + "#" + function.getName();

                if (StringUtils.isBlank(textToMatch)
                        || StringUtils.startsWithIgnoreCase(catalogAlias, textToMatch)
                        || StringUtils.startsWithIgnoreCase(function.getName(), textToMatch)
                        || StringUtils.containsIgnoreCase(replacementText, textToMatch))
                {
                    functions.add(new PLBCompletion(provider, replacementText, function.getName() + "()", null, null, scalar ? GEAR
                            : TABLE, 0));
                }
            }
        }

        return functions;
    }

    /** Checks if any of the qualifed names parts starts with prefix */
    private static final boolean startsWith(QualifiedName name, String prefix)
    {
        return name.getParts()
                .stream()
                .anyMatch(p -> StringUtils.startsWithIgnoreCase(p, prefix));
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
