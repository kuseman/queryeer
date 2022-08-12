package com.queryeer.provider.payloadbuilder;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.lowerCase;

import java.awt.Component;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queryeer.api.IQueryFile;
import com.queryeer.api.IQueryFile.ExecutionState;
import com.queryeer.api.IQueryFileState;
import com.queryeer.api.component.ISyntaxTextEditor;
import com.queryeer.api.extensions.IQueryProvider;
import com.queryeer.api.extensions.catalog.ICatalogExtension;
import com.queryeer.api.extensions.catalog.ICatalogExtension.ExceptionAction;
import com.queryeer.api.extensions.catalog.ICatalogExtensionFactory;
import com.queryeer.api.service.IComponentFactory;
import com.queryeer.api.service.IConfig;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.IQueryFileProvider;

import se.kuseman.payloadbuilder.api.OutputWriter;
import se.kuseman.payloadbuilder.api.catalog.CatalogException;
import se.kuseman.payloadbuilder.api.utils.MapUtils;
import se.kuseman.payloadbuilder.core.CompiledQuery;
import se.kuseman.payloadbuilder.core.Payloadbuilder;
import se.kuseman.payloadbuilder.core.QueryResult;
import se.kuseman.payloadbuilder.core.QuerySession;
import se.kuseman.payloadbuilder.core.parser.ParseException;

/** Provider that enables */
class PayloadbuilderQueryProvider implements IQueryProvider
{
    private static final String NAME = "com.queryeer.provider.payloadbuilder.PayloadbuilderQueryProvider";
    static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final IComponentFactory componentFactory;
    private final List<ICatalogExtensionFactory> catalogExtensionFactories;
    private final QuickPropertiesView quickPropertiesView;
    private final List<CatalogModel> catalogs;
    private final IQueryFileProvider queryFileProvider;

    PayloadbuilderQueryProvider(IConfig config, IEventBus eventBus, IQueryFileProvider queryFileProvider, IComponentFactory componentFactory, List<ICatalogExtensionFactory> catalogExtensionFactories)
    {
        this.queryFileProvider = requireNonNull(queryFileProvider, "queryFileProvider");
        this.componentFactory = requireNonNull(componentFactory, "componentFactory");
        this.catalogExtensionFactories = requireNonNull(catalogExtensionFactories, "catalogExtensionFactories");
        this.catalogs = loadCatalogExtensions(config);
        this.quickPropertiesView = new QuickPropertiesView(eventBus, queryFileProvider, catalogs);
    }

    @Override
    public String getTitle()
    {
        return "Payloadbuilder";
    }

    @Override
    public String getFilenameExtension()
    {
        return "plbsql";
    }

    @Override
    public IQueryFileState createQueryFileState()
    {
        ISyntaxTextEditor textEditor = componentFactory.createSyntaxTextEditor("text/sql");
        PayloadbuilderQueryFileState state = new PayloadbuilderQueryFileState(this, queryFileProvider, textEditor, catalogs);
        return state;
    }

    @Override
    public Component getQuickPropertiesComponent()
    {
        return quickPropertiesView;
    }

    @Override
    public void executeQuery(IQueryFile file, OutputWriter writer)
    {
        Optional<PayloadbuilderQueryFileState> state = file.getQueryFileState(PayloadbuilderQueryFileState.class);
        if (!state.isPresent())
        {
            return;
        }
        ISyntaxTextEditor textEditor = state.get()
                .getTextEditor();

        textEditor.clearErrors();

        QuerySession session = (QuerySession) state.get()
                .getSession();

        BooleanSupplier abortSupplier = () -> file.getExecutionState() == ExecutionState.ABORTED;
        boolean completed = false;
        while (!completed)
        {
            try
            {
                session.setAbortSupplier(abortSupplier);
                CompiledQuery query = Payloadbuilder.compile(textEditor.getModel()
                        .getText(true));
                QueryResult queryResult = query.execute(session);

                while (queryResult.hasMoreResults())
                {
                    if (abortSupplier.getAsBoolean())
                    {
                        break;
                    }

                    queryResult.writeResult(writer);
                }
                completed = true;
            }
            catch (ParseException e)
            {
                file.getMessagesWriter()
                        .println(String.format("Syntax error. Line: %d, Column: %d. %s", e.getLine(), e.getColumn(), e.getMessage()));
                textEditor.setParseErrorLocation(e.getLine(), e.getColumn() + 1);
                file.setExecutionState(ExecutionState.ERROR);
                file.focusMessages();
                completed = true;
            }
            catch (Exception e)
            {
                if (e instanceof CatalogException)
                {
                    // Let catalog extension handle exception
                    CatalogException ce = (CatalogException) e;
                    Optional<ICatalogExtension> catalogExtension = state.get()
                            .getCatalogs()
                            .stream()
                            .filter(c -> Objects.equals(ce.getCatalogAlias(), c.getAlias()))
                            .map(CatalogModel::getCatalogExtension)
                            .findFirst();

                    if (catalogExtension.isPresent()
                            && catalogExtension.get()
                                    .handleException(session, ce) == ExceptionAction.RERUN)
                    {
                        // Re-run query
                        continue;
                    }
                }

                // Let error bubble up
                throw e;
            }
        }
    }

    /** Load config with provide etc-folder */
    private List<CatalogModel> loadCatalogExtensions(IConfig config)
    {
        Map<String, Object> map = config.loadExtensionConfig(NAME);

        List<CatalogModel> catalogs = MAPPER.convertValue(map.getOrDefault("catalogs", emptyList()), new TypeReference<List<CatalogModel>>()
        {
        });

        Set<String> seenAliases = new HashSet<>();
        for (CatalogModel catalog : catalogs)
        {
            if (!seenAliases.add(lowerCase(catalog.getAlias())))
            {
                throw new IllegalArgumentException("Duplicate alias found in config. Alias: " + catalog.getAlias());
            }
        }

        // Load extension according to config
        // Auto add missing extension with the extensions default alias
        Map<String, ICatalogExtensionFactory> extensions = catalogExtensionFactories.stream()
                .sorted(Comparator.comparingInt(ICatalogExtensionFactory::order))
                .collect(toMap(c -> c.getClass()
                        .getName(), Function.identity(), (e1, e2) -> e1, LinkedHashMap::new));

        Set<ICatalogExtensionFactory> processedFactories = new HashSet<>();

        // Loop configured extensions
        boolean configChanged = false;

        for (CatalogModel catalog : catalogs)
        {
            ICatalogExtensionFactory factory = extensions.get(catalog.getFactory());
            processedFactories.add(factory);
            // Disable current catalog if no extension found
            if (factory == null)
            {
                configChanged = true;
                catalog.disabled = true;
            }
            else
            {
                catalog.catalogExtension = factory.create(catalog.getAlias());
                if (catalog.isDisabled())
                {
                    configChanged = true;
                }
                // Enable config
                catalog.disabled = false;
            }
        }

        // Append all new extensions not found in config
        for (ICatalogExtensionFactory factory : extensions.values())
        {
            if (processedFactories.contains(factory))
            {
                continue;
            }

            CatalogModel catalog = new CatalogModel();
            catalogs.add(catalog);

            catalog.factory = factory.getClass()
                    .getName();
            catalog.disabled = false;

            String alias = factory.getDefaultAlias();

            // Find an empty alias
            int count = 1;
            String currentAlias = alias;
            while (seenAliases.contains(lowerCase(currentAlias)))
            {
                currentAlias = (alias + count++);
            }

            catalog.alias = alias;
            catalog.catalogExtension = factory.create(alias);

            configChanged = true;
        }

        if (configChanged)
        {
            config.saveExtensionConfig(NAME, MapUtils.ofEntries(MapUtils.entry("catalogs", catalogs)));
        }

        return catalogs;
    }
}
