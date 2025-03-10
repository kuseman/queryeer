package com.queryeer.payloadbuilder;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isAllBlank;

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.kordamp.ikonli.fontawesome.FontAwesome;

import com.queryeer.Constants;
import com.queryeer.IconFactory;
import com.queryeer.api.IQueryFile;
import com.queryeer.api.editor.IEditor;
import com.queryeer.api.editor.IEditorFactory;
import com.queryeer.api.editor.ITextEditor;
import com.queryeer.api.editor.ITextEditorDocumentParser;
import com.queryeer.api.editor.ITextEditorKit;
import com.queryeer.api.editor.TextSelection;
import com.queryeer.api.event.ExecuteQueryEvent;
import com.queryeer.api.event.ExecuteQueryEvent.OutputType;
import com.queryeer.api.event.ShowOptionsEvent;
import com.queryeer.api.extensions.engine.IQueryEngine;
import com.queryeer.api.extensions.engine.QueryEngineException;
import com.queryeer.api.extensions.output.text.ITextOutputComponent;
import com.queryeer.api.extensions.payloadbuilder.ICatalogExtension;
import com.queryeer.api.extensions.payloadbuilder.ICatalogExtension.ExceptionAction;
import com.queryeer.api.service.IEventBus;
import com.queryeer.payloadbuilder.CatalogsConfigurable.QueryeerCatalog;
import com.queryeer.payloadbuilder.VariablesConfigurable.Environment;

import se.kuseman.payloadbuilder.api.OutputWriter;
import se.kuseman.payloadbuilder.api.catalog.CatalogException;
import se.kuseman.payloadbuilder.core.CompiledQuery;
import se.kuseman.payloadbuilder.core.Payloadbuilder;
import se.kuseman.payloadbuilder.core.QueryException;
import se.kuseman.payloadbuilder.core.QueryResult;
import se.kuseman.payloadbuilder.core.cache.InMemoryGenericCache;
import se.kuseman.payloadbuilder.core.catalog.CatalogRegistry;
import se.kuseman.payloadbuilder.core.execution.QuerySession;
import se.kuseman.payloadbuilder.core.parser.ParseException;

/** Implementation of a query engine that executes the query file as a Payloadbuilder query */
class PayloadbuilderQueryEngine implements IQueryEngine
{
    /** All query tabs share a common cache to be able to reuse cached data etc. */
    private static final InMemoryGenericCache GENERIC_CACHE = new InMemoryGenericCache("QuerySession", true);
    private static final Icon ICON = IconFactory.of(FontAwesome.PRODUCT_HUNT);

    private final IEventBus eventBus;
    private final CatalogsConfigurable catalogsConfigurable;
    private final CatalogExtensionViewFactory catalogExtensionViewFactory;
    private final CompletionRegistry completionRegistry;
    private final IEditorFactory editorFactory;
    private final VariablesConfigurable variablesConfigurable;
    private QuickPropertiesComponent quickPropertiesComponent;

    PayloadbuilderQueryEngine(IEventBus eventBus, CatalogsConfigurable catalogsConfigurable, CatalogExtensionViewFactory catalogExtensionViewFactory, CompletionRegistry completionRegistry,
            IEditorFactory editorFactory, VariablesConfigurable variablesConfigurable)
    {
        this.eventBus = requireNonNull(eventBus, "eventBus");
        this.catalogsConfigurable = requireNonNull(catalogsConfigurable, "catalogsConfigurable");
        this.catalogExtensionViewFactory = requireNonNull(catalogExtensionViewFactory, "catalogExtensionViewFactory");
        this.completionRegistry = requireNonNull(completionRegistry, "completionRegistry");
        this.editorFactory = requireNonNull(editorFactory, "editorFactory");
        this.variablesConfigurable = requireNonNull(variablesConfigurable, "variablesConfigurable");
    }

    @Override
    public Component getQuickPropertiesComponent()
    {
        if (quickPropertiesComponent == null)
        {
            this.quickPropertiesComponent = QuickPropertiesComponent.create(eventBus, variablesConfigurable, catalogExtensionViewFactory, catalogsConfigurable.getCatalogs());
        }
        return quickPropertiesComponent;
    }

    @Override
    public IState createState()
    {
        QuerySession querySession = new QuerySession(new CatalogRegistry());
        querySession.setGenericCache(GENERIC_CACHE);
        initCatalogs(querySession);
        PayloadbuilderState state = new PayloadbuilderState(this, catalogsConfigurable, querySession);
        querySession.setAbortSupplier(state);
        return state;
    }

    @Override
    public IEditor createEditor(IQueryEngine.IState state, String filename)
    {
        PayloadbuilderState payloadbuilderState = (PayloadbuilderState) state;
        PLBDocumentParser parser = new PLBDocumentParser(eventBus, payloadbuilderState.getQuerySession(), completionRegistry, catalogsConfigurable);
        ITextEditorKit editorKit = new ITextEditorKit()
        {
            @Override
            public String getSyntaxMimeType()
            {
                return "text/sql";
            }

            @Override
            public ITextEditorDocumentParser getDocumentParser()
            {
                return parser;
            }
        };

        return editorFactory.createTextEditor(payloadbuilderState, editorKit);
    }

    @Override
    public void focus(IQueryFile queryFile)
    {
        if (quickPropertiesComponent != null)
        {
            quickPropertiesComponent.focus(queryFile);
        }
    }

    @Override
    public void execute(IQueryFile queryFile, OutputWriter writer, Object query)
    {
        PayloadbuilderState state = queryFile.getEngineState();
        synchronized (state)
        {
            state.abort = false;

            QuerySession session = state.getQuerySession();

            ITextOutputComponent textOutput = queryFile.getOutputComponent(ITextOutputComponent.class);

            session.setPrintWriter(textOutput.getTextWriter());
            // Print exception as warning if now handled by engine
            session.setExceptionHandler(e -> textOutput.appendWarning(e.getMessage(), TextSelection.EMPTY));

            ITextEditor editor = null;
            if (queryFile.getEditor() instanceof ITextEditor textEditor)
            {
                editor = textEditor;
            }

            boolean injectEnvironment = false;
            String queryText;
            if (query instanceof ExecuteQueryContext ctx)
            {
                queryText = ctx.query();
            }
            else
            {
                injectEnvironment = true;
                queryText = String.valueOf(query);
            }

            if (injectEnvironment)
            {
                quickPropertiesComponent.beforeExecute(queryFile);
            }

            boolean complete = false;
            int reRunCountLatch = 3;
            while (!complete)
            {
                try
                {
                    CompiledQuery compiledQuery = Payloadbuilder.compile(session, queryText);

                    if (!compiledQuery.getWarnings()
                            .isEmpty())
                    {
                        for (CompiledQuery.Warning warning : compiledQuery.getWarnings())
                        {
                            TextSelection selection = TextSelection.EMPTY;
                            if (editor != null)
                            {
                                selection = editor.translate(new TextSelection(warning.location()
                                        .startOffset(),
                                        warning.location()
                                                .endOffset()));
                            }
                            String message = "Warning, line: " + warning.location()
                                    .line() + System.lineSeparator() + warning.message() + System.lineSeparator();
                            textOutput.appendWarning(message, selection);
                            editor.highlight(selection, Color.BLUE);
                        }

                        textOutput.getTextWriter()
                                .append(System.lineSeparator());
                    }

                    QueryResult queryResult = compiledQuery.execute(session);

                    while (queryResult.hasMoreResults())
                    {
                        if (state.abort)
                        {
                            break;
                        }

                        queryResult.writeResult(writer);
                        // Flush after each result set
                        writer.flush();

                        long time = session.getLastQueryExecutionTime();
                        long rowCount = session.getLastQueryRowCount();

                        if (!state.abort)
                        {
                            String output = String.format("%s%d row(s) affected, execution time: %s", System.lineSeparator(), rowCount, DurationFormatUtils.formatDurationHMS(time));
                            session.printLine(output);
                        }
                    }

                    complete = true;
                }
                catch (CatalogException e)
                {
                    // Let catalog extension handle exception
                    Optional<ICatalogExtension> catalogExtension = catalogsConfigurable.getCatalogs()
                            .stream()
                            .filter(c -> Objects.equals(e.getCatalogAlias(), c.getAlias()))
                            .map(QueryeerCatalog::getCatalogExtension)
                            .findFirst();

                    if (catalogExtension.isPresent()
                            && catalogExtension.get()
                                    .handleException(session, e) == ExceptionAction.RERUN)
                    {
                        reRunCountLatch--;
                        if (reRunCountLatch <= 0)
                        {
                            throw new QueryException("Query re reexecution limit was reached");
                        }

                        // Re-run query
                        continue;
                    }

                    throw e;
                }
                catch (ParseException e)
                {
                    TextSelection selection = TextSelection.EMPTY;
                    if (editor != null)
                    {
                        selection = editor.translate(new TextSelection(e.getLocation()
                                .startOffset(),
                                e.getLocation()
                                        .endOffset()));
                    }
                    String message = "Syntax error, line: " + e.getLocation()
                            .line() + System.lineSeparator() + e.getMessage() + System.lineSeparator();
                    editor.highlight(selection, Color.RED);
                    textOutput.appendWarning(message, selection);
                    throw new QueryEngineException("", true);
                }
                finally
                {
                    quickPropertiesComponent.afterExecute(queryFile);
                    state.abort = false;
                }
            }
        }
    }

    @Override
    public void abortQuery(IQueryFile queryFile)
    {
        PayloadbuilderState state = queryFile.getEngineState();
        if (state != null)
        {
            state.abort();
        }
    }

    @Override
    public String getTitle()
    {
        return "Payloadbuilder";
    }

    @Override
    public Icon getIcon()
    {
        return ICON;
    }

    @Override
    public String getDefaultFileExtension()
    {
        return "plbsql";
    }

    @Override
    public ExecuteQueryEvent getExecuteQueryEvent(String query, String newQueryName, OutputType outputType)
    {
        return new ExecuteQueryEvent(outputType, newQueryName, new ExecuteQueryContext(query));
    }

    private void initCatalogs(QuerySession querySession)
    {
        for (QueryeerCatalog catalog : catalogsConfigurable.getCatalogs())
        {
            if (catalog.isDisabled())
            {
                continue;
            }

            // Register catalogs
            querySession.getCatalogRegistry()
                    .registerCatalog(catalog.getAlias(), catalog.getCatalogExtension()
                            .getCatalog());

            // Set first extension as default
            // We pick the first catalog that has a UI component
            if (isAllBlank(querySession.getDefaultCatalogAlias())
                    && catalog.getCatalogExtension()
                            .getConfigurableClass() != null)
            {
                querySession.setDefaultCatalogAlias(catalog.getAlias());
            }
        }
    }

    /** Quick properties with a panel per active catalog */
    static class QuickPropertiesComponent extends JPanel
    {
        private static final Environment EMPTY_ENV = new Environment()
        {
            {
                name = " ";
            }
        };
        private final VariablesConfigurable variablesConfigurable;
        private List<CatalogExtensionView> catalogViews = new ArrayList<>();
        private final DefaultComboBoxModel<VariablesConfigurable.Environment> environmentsModel;

        QuickPropertiesComponent(VariablesConfigurable variablesConfigurable)
        {
            this.variablesConfigurable = variablesConfigurable;
            this.environmentsModel = new DefaultComboBoxModel<>();
            variablesConfigurable.addConfigChangedListener(() -> setVariablesEnvironments());
            setLayout(new GridBagLayout());
        }

        void focus(IQueryFile queryFile)
        {
            // Update views to reflect changed values after query
            for (CatalogExtensionView view : catalogViews)
            {
                view.focus(queryFile);
            }
        }

        void beforeExecute(IQueryFile queryFile)
        {
            Environment env = (Environment) environmentsModel.getSelectedItem();
            if (env != EMPTY_ENV)
            {
                PayloadbuilderState state = queryFile.getEngineState();
                // Store choosen env. in the files state in case it's a long running query and the user
                // switches env during execution, then we cannot clear correctly after execution
                state.environment = env;
                variablesConfigurable.beforeQuery(state.getQuerySession(), env);
            }
        }

        void afterExecute(IQueryFile queryFile)
        {
            PayloadbuilderState state = queryFile.getEngineState();
            if (state.environment != null)
            {
                variablesConfigurable.afterQuery(state.getQuerySession(), state.environment);
                state.environment = null;
            }

            // Update views to reflect changed values after query
            SwingUtilities.invokeLater(() ->
            {
                for (CatalogExtensionView view : catalogViews)
                {
                    view.afterExecute(queryFile);
                }
            });
        }

        void setVariablesEnvironments()
        {
            environmentsModel.removeAllElements();
            environmentsModel.addElement(EMPTY_ENV);
            for (VariablesConfigurable.Environment env : variablesConfigurable.getEnvironments())
            {
                environmentsModel.addElement(env);
            }
        }

        static QuickPropertiesComponent create(IEventBus eventBus, VariablesConfigurable variablesConfigurable, CatalogExtensionViewFactory factory, List<QueryeerCatalog> catalogs)
        {
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.insets = new Insets(0, 3, 3, 3);
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.NONE;

            QuickPropertiesComponent result = new QuickPropertiesComponent(variablesConfigurable);
            result.add(new JLabel("Variables: "), gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 1;
            gbc.gridy = 0;
            gbc.weightx = 1.0d;
            gbc.insets = new Insets(0, 0, 3, 3);
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;

            JComboBox<VariablesConfigurable.Environment> environments = new JComboBox<>();
            environments.setRenderer(new DefaultListCellRenderer()
            {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
                {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    Environment env = (Environment) value;
                    setText("");
                    if (env != null)
                    {
                        setText(env.name);
                    }
                    return this;
                }
            });
            environments.setModel(result.environmentsModel);
            result.add(environments, gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 2;
            gbc.gridy = 0;
            gbc.insets = new Insets(0, 0, 3, 3);
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;

            JButton varsConfig = new JButton(Constants.COG);
            varsConfig.addActionListener(l ->
            {
                eventBus.publish(new ShowOptionsEvent(VariablesConfigurable.class));
            });
            result.add(varsConfig, gbc);

            result.setVariablesEnvironments();

            Insets insets = new Insets(0, 0, 3, 0);
            int y = 1;
            for (QueryeerCatalog catalog : catalogs)
            {
                if (catalog.isDisabled())
                {
                    continue;
                }
                ICatalogExtension extension = catalog.getCatalogExtension();
                if (extension.getConfigurableClass() == null
                        && !extension.hasQuickPropertieComponent())
                {
                    continue;
                }

                CatalogExtensionView extensionView = factory.create(extension, catalog.getAlias());
                result.catalogViews.add(extensionView);
                result.add(extensionView, new GridBagConstraints(0, y++, 3, 1, 1, 0, GridBagConstraints.BASELINE, GridBagConstraints.HORIZONTAL, insets, 0, 0));
            }
            result.add(new JPanel(), new GridBagConstraints(0, y, 3, 1, 1, 1, GridBagConstraints.BASELINE, GridBagConstraints.HORIZONTAL, insets, 0, 0));
            return result;
        }
    }
}
