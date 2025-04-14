package com.queryeer.output.table;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queryeer.Constants;
import com.queryeer.IconFactory;
import com.queryeer.api.IQueryFile;
import com.queryeer.api.component.ADocumentListenerAdapter;
import com.queryeer.api.component.DialogUtils;
import com.queryeer.api.editor.IEditor;
import com.queryeer.api.editor.IEditorFactory;
import com.queryeer.api.editor.ITextEditor;
import com.queryeer.api.editor.ITextEditorKit;
import com.queryeer.api.event.ExecuteQueryEvent;
import com.queryeer.api.event.ExecuteQueryEvent.OutputType;
import com.queryeer.api.event.NewQueryFileEvent;
import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.extensions.engine.IQueryEngine;
import com.queryeer.api.extensions.engine.IQueryEngine.IState;
import com.queryeer.api.extensions.engine.IQueryEngine.IState.MetaParameter;
import com.queryeer.api.extensions.output.table.ITableOutputComponent;
import com.queryeer.api.service.IConfig;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.ITemplateService;

import se.kuseman.payloadbuilder.api.utils.MapUtils;
import se.kuseman.payloadbuilder.core.CompiledQuery;
import se.kuseman.payloadbuilder.core.JsonOutputWriter;
import se.kuseman.payloadbuilder.core.Payloadbuilder;
import se.kuseman.payloadbuilder.core.QueryResult;
import se.kuseman.payloadbuilder.core.catalog.CatalogRegistry;
import se.kuseman.payloadbuilder.core.execution.QuerySession;

/** Table actions. Customize context menu actions based on rules. */
class TableActionsConfigurable implements IConfigurable
{
    private static final Logger LOGGER = LoggerFactory.getLogger(TableActionsConfigurable.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final String NAME = "com.queryeer.output.table.QueryActions";
    private static final CatalogRegistry CATALOG_REGISTRY = new CatalogRegistry();
    private final List<Consumer<Boolean>> dirtyStateConsumers = new ArrayList<>();
    private final IConfig config;
    private final ITemplateService templateService;
    private final IEventBus eventBus;
    private final IEditorFactory editorFactory;

    private QueryActions queryActions;
    private QueryActionsComponent component;
    private boolean disableNotify;

    TableActionsConfigurable(IConfig config, ITemplateService templateService, IEventBus eventBus, IEditorFactory editorFactory)
    {
        this.config = requireNonNull(config, "config");
        this.templateService = requireNonNull(templateService, "templateService");
        this.eventBus = requireNonNull(eventBus, "eventBus");
        this.editorFactory = requireNonNull(editorFactory, "editorFactory");
        loadConfig();
    }

    @Override
    public Component getComponent()
    {
        if (component == null)
        {
            component = new QueryActionsComponent();
            component.init(queryActions);
        }
        return component;
    }

    @Override
    public String getTitle()
    {
        return "Query Actions";
    }

    @Override
    public String getLongTitle()
    {
        return "Table Query Actions";
    }

    @Override
    public String groupName()
    {
        return "Table";
    }

    @Override
    public void addDirtyStateConsumer(Consumer<Boolean> consumer)
    {
        dirtyStateConsumers.add(consumer);
    }

    @Override
    public void removeDirtyStateConsumer(Consumer<Boolean> consumer)
    {
        dirtyStateConsumers.remove(consumer);
    }

    @Override
    public void revertChanges()
    {
        component.init(queryActions);
    }

    @Override
    public boolean commitChanges()
    {
        QueryActions queryActions = component.getQueryActions();
        File configFileName = config.getConfigFileName(NAME);
        try
        {
            MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValue(configFileName, queryActions);
        }
        catch (IOException e)
        {
            JOptionPane.showMessageDialog(component, "Error saving config, message: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        this.queryActions = queryActions;
        return true;
    }

    private void notifyDirtyStateConsumers()
    {
        if (disableNotify)
        {
            return;
        }
        int size = dirtyStateConsumers.size();
        for (int i = size - 1; i >= 0; i--)
        {
            dirtyStateConsumers.get(i)
                    .accept(true);
        }
    }

    /** Return actions matching provided row. */
    List<Action> getActions(ITableOutputComponent outputComponent, ITableOutputComponent.SelectedRow selectedRow)
    {
        IQueryFile queryFile = outputComponent.getQueryFile();
        IState engineState = queryFile.getEngineState();
        IQueryEngine engine = engineState.getQueryEngine();
        Map<String, Object> variables = getModel(engine, engineState, selectedRow, false);
        List<Action> result = new ArrayList<>();
        queryActions.init();
        for (QueryAction action : queryActions.queryActions)
        {
            if (action.compiledTemplateQuery == null
                    || action.outputType == null)
            {
                continue;
            }

            AtomicReference<String> name = new AtomicReference<String>(action.name);
            try
            {
                String resultTemplate = processTemplate(action.compiledTemplateQuery, variables, m -> name.set(templateService.process("TableAction name template: " + action.name, action.name, m)),
                        true);

                if (!isBlank(resultTemplate))
                {
                    name.set(StringUtils.abbreviate(name.get(), 60));
                    result.add(new AbstractAction(name.get())
                    {
                        @Override
                        public void actionPerformed(ActionEvent e)
                        {
                            if (action.templateTarget == TemplateTarget.EXECUTE)
                            {
                                ExecuteQueryEvent event = engine.getExecuteQueryEvent(resultTemplate, name.get(), action.outputType);
                                if (event == null)
                                {
                                    LOGGER.error("Engine: {} does not support execute by query event", engine.getClass());
                                    return;
                                }
                                eventBus.publish(event);
                            }
                            else if (action.templateTarget == TemplateTarget.AS_IS)
                            {
                                boolean execute = false;
                                // CSOFF
                                switch (action.outputType)
                                {
                                    case QUERY_PLAN:
                                    case TABLE:
                                        LOGGER.warn("Query actions with target: {} cannot be used with output type: {}, action: {}", TemplateTarget.AS_IS, action.outputType, action.name);
                                        break;
                                    case CLIPBOARD:
                                        StringSelection stringSelection = new StringSelection(resultTemplate);
                                        Clipboard clipboard = Toolkit.getDefaultToolkit()
                                                .getSystemClipboard();
                                        clipboard.setContents(stringSelection, null);
                                        queryFile.getMessagesWriter()
                                                .println("Copied to clipboard");
                                        queryFile.focusMessages();
                                        break;
                                    case TEXT:
                                        queryFile.getMessagesWriter()
                                                .append(resultTemplate);
                                        queryFile.focusMessages();
                                        break;
                                    case NEW_QUERY_EXECUTE:
                                        execute = true;
                                    case NEW_QUERY:
                                        NewQueryFileEvent event = new NewQueryFileEvent(engine, engine.cloneState(queryFile), resultTemplate, execute, name.get());
                                        eventBus.publish(event);
                                        break;
                                }
                                // CSON
                            }
                        }
                    });
                }
            }
            catch (Exception e)
            {
                LOGGER.error("Error executing template query:{}{}", System.lineSeparator(), action.getTemplateQuery(), e);
            }
        }
        return result;
    }

    private void loadConfig()
    {
        File configFileName = config.getConfigFileName(NAME);
        if (configFileName.exists())
        {
            try
            {
                queryActions = MAPPER.readValue(configFileName, QueryActions.class);
            }
            catch (IOException e)
            {
                LOGGER.error("Error reading table query actions config", e);
            }
        }

        if (queryActions == null)
        {
            queryActions = new QueryActions();
        }
    }

    static class QueryActions
    {
        @JsonProperty
        List<QueryAction> queryActions = new ArrayList<>();

        @JsonIgnore
        boolean initalized;

        @Override
        public QueryActions clone()
        {
            QueryActions clone = new QueryActions();
            clone.queryActions = new ArrayList<>(queryActions.stream()
                    .map(QueryAction::clone)
                    .toList());

            return clone;
        }

        void init()
        {
            if (!initalized)
            {
                initalized = true;
                queryActions.forEach(QueryAction::init);
            }
        }
    }

    static class QueryAction
    {
        /** Name of the action. */
        @JsonProperty
        String name = "";

        /** Target for the produced template. */
        @JsonProperty
        TemplateTarget templateTarget = TemplateTarget.AS_IS;

        /** Which output type should this action have. */
        @JsonProperty
        OutputType outputType = OutputType.NEW_QUERY_EXECUTE;

        /** The query to execute for this action that generates the template. */
        @JsonProperty
        Object templateQuery;

        @JsonIgnore
        CompiledQuery compiledTemplateQuery;

        @JsonIgnore
        String getTemplateQuery()
        {
            if (templateQuery == null)
            {
                return "";
            }
            if (templateQuery instanceof String str)
            {
                return str;
            }
            else if (templateQuery instanceof Collection<?> col)
            {
                return col.stream()
                        .map(Object::toString)
                        .collect(joining(System.lineSeparator()));
            }
            return String.valueOf(templateQuery);
        }

        void init()
        {
            String query = getTemplateQuery();
            if (!isBlank(query))
            {
                try
                {
                    compiledTemplateQuery = Payloadbuilder.compile(CATALOG_REGISTRY, query);
                }
                catch (Exception e)
                {
                    LOGGER.error("Error compiling template query {}{}", System.lineSeparator(), query, e);
                }
            }
        }

        @Override
        public QueryAction clone()
        {
            QueryAction clone = new QueryAction();
            clone.name = name;
            clone.outputType = outputType;
            clone.templateTarget = templateTarget;
            clone.templateQuery = getTemplateQuery();
            // clone.template = getTemplate();
            return clone;
        }

        @Override
        public String toString()
        {
            return name;
        }
    }

    /** Defines targets for the produced template. */
    enum TemplateTarget
    {
        /** The produced template is executed along with OutputType. */
        EXECUTE,
        /**
         * The produced template is the result and will be used with OutputType. Only supported outputypes are valid here. All but {@link OutputType#TABLE} can be used.
         */
        AS_IS
    }

    private String processTemplate(CompiledQuery compiledQuery, Map<String, Object> model, Consumer<Map<String, Object>> resultModelConsumer, boolean throwTemplateErrros) throws Exception
    {
        QuerySession session = new QuerySession(CATALOG_REGISTRY, model);
        StringBuilder sb = new StringBuilder();
        QueryResult query = compiledQuery.execute(session);
        if (query.hasMoreResults())
        {
            StringWriter sw = new StringWriter();
            JsonOutputWriter.JsonSettings settings = new JsonOutputWriter.JsonSettings();
            settings.setResultSetsAsArrays(true);
            JsonOutputWriter writer = new JsonOutputWriter(sw, settings);
            query.writeResult(writer);
            writer.close();
            String json = sw.toString();
            if (!isBlank(json))
            {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> models = MAPPER.readValue(json, List.class);
                boolean first = true;
                for (Map<String, Object> resultModel : models)
                {
                    if (first)
                    {
                        resultModelConsumer.accept(resultModel);
                    }
                    sb.append(resultModel.getOrDefault("template", ""));
                    first = false;
                }
            }
        }
        return StringUtils.trimToEmpty(sb.toString());
    }

    private Map<String, Object> getModel(IQueryEngine engine, IQueryEngine.IState state, ITableOutputComponent.SelectedRow selectedRow, boolean testData)
    {
        // - Build a variables map to use
        Map<String, Object> variables = testData ? new LinkedHashMap<String, Object>()
                : new HashMap<>();
        variables.put("queryEngineClassName", engine.getClass()
                .getSimpleName());

        //@formatter:off
        variables.put("tableRow", Map.of(
                "cell", MapUtils.ofEntries(
                        MapUtils.entry("header", selectedRow.getCellHeader()),
                        MapUtils.entry("value", selectedRow.getCellValue())
                ), 
                "columns", IntStream.range(0, selectedRow.getColumnCount()).mapToObj(i ->
                        MapUtils.ofEntries(
                            MapUtils.entry("header", selectedRow.getHeader(i)),
                            MapUtils.entry("value", selectedRow.getValue(i))
                        )).toList()
                ));
        //@formatter:on

        for (IState.MetaParameter param : state.getMetaParameters(testData))
        {
            variables.put(param.name(), param.value());
        }
        return variables;
    }

    private String getTestModelJson(IQueryEngine queryEngine)
    {
        ITableOutputComponent.SelectedRow selectedRow = new ITableOutputComponent.SelectedRow()
        {
            @Override
            public Object getValue(int columnIndex)
            {
                return switch (columnIndex)
                {
                    case 0 -> "2020-10-10T10:10:10Z";
                    case 1 -> "INFO";
                    case 2 -> "abc-123";
                    default -> "value";
                };
            }

            @Override
            public String getHeader(int columnIndex)
            {
                return switch (columnIndex)
                {
                    case 0 -> "timestamp";
                    case 1 -> "logLevel";
                    case 2 -> "correlationId";
                    default -> "Column";
                };
            }

            @Override
            public int getColumnCount()
            {
                return 3;
            }

            @Override
            public Object getCellValue()
            {
                return "cellValue";
            }

            @Override
            public String getCellHeader()
            {
                return "columnName";
            }
        };

        try
        {
            return MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(getModel(queryEngine, queryEngine.createState(), selectedRow, true));
        }
        catch (JsonProcessingException e)
        {
            LOGGER.error("Error generating test model JSON", e);
            return "{}";
        }
    }

    private class QueryActionsComponent extends JPanel
    {
        private final JLabel noSelectionLabel;
        private final JScrollPane selectedActionScrollPane;
        private final QueryActionComponent actionComponent = new QueryActionComponent();
        private final ActionTesterComponent testComponent = new ActionTesterComponent();
        private DefaultListModel<QueryAction> actionsListModel = new DefaultListModel<>();
        private List<QueryAction> templateActions = createTemplates();

        public QueryActionsComponent()
        {
            setLayout(new BorderLayout());

            noSelectionLabel = new JLabel("Select action to edit");
            noSelectionLabel.setVerticalAlignment(SwingConstants.TOP);
            selectedActionScrollPane = new JScrollPane(noSelectionLabel);

            JSplitPane splitPane = new JSplitPane();
            splitPane.setRightComponent(selectedActionScrollPane);

            JList<QueryAction> actionsList = new JList<>();
            actionsList.setCellRenderer(new DefaultListCellRenderer()
            {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
                {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                    QueryAction action = (QueryAction) value;
                    setText(action.name);

                    return this;
                }
            });
            actionsList.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent e)
                {
                    if (SwingUtilities.isLeftMouseButton(e)
                            && e.getClickCount() == 2)
                    {
                        QueryAction selectedValue = actionsList.getSelectedValue();
                        if (selectedValue != null)
                        {
                            testComponent.show(selectedValue);
                        }
                    }
                }
            });
            actionsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            actionsList.setModel(actionsListModel);
            actionsListModel.addListDataListener(new ListDataListener()
            {
                @Override
                public void intervalRemoved(ListDataEvent e)
                {
                    notifyDirtyStateConsumers();
                }

                @Override
                public void intervalAdded(ListDataEvent e)
                {
                    notifyDirtyStateConsumers();
                }

                @Override
                public void contentsChanged(ListDataEvent e)
                {
                }
            });

            JPanel buttonListPanel = new JPanel();
            buttonListPanel.setLayout(new BoxLayout(buttonListPanel, BoxLayout.X_AXIS));

            JButton add = new JButton(IconFactory.of(FontAwesome.PLUS, 8));
            add.setToolTipText("Add Action");
            add.addActionListener(l ->
            {
                QueryAction action = new QueryAction();
                action.name = "New Action";
                actionsListModel.addElement(action);
            });
            JButton remove = new JButton(IconFactory.of(FontAwesome.MINUS, 8));
            remove.addActionListener(l ->
            {
                QueryAction selectedValue = actionsList.getSelectedValue();
                if (selectedValue != null)
                {
                    actionsListModel.removeElement(selectedValue);
                }
            });
            remove.setToolTipText("Remove Selected Action");
            remove.setEnabled(false);
            JButton addTemplate = new JButton(IconFactory.of(FontAwesome.FILE_TEXT_O, 8));
            addTemplate.setToolTipText("Add Template");
            addTemplate.addActionListener(l ->
            {
                //@formatter:off
                QueryAction action = (QueryAction)JOptionPane.showInputDialog(
                                    this,
                                    "Template:",
                                    "Select template and edit to fit your query needs",
                                    JOptionPane.INFORMATION_MESSAGE,
                                    null,
                                    templateActions.toArray(),
                                    templateActions.get(0));
                //@formatter:on
                if (action != null)
                {
                    actionsListModel.addElement(action.clone());
                }
            });
            JButton test = new JButton(IconFactory.of(FontAwesome.CODEPEN, 8));
            test.setEnabled(false);
            test.setToolTipText("Open Test Editor");
            test.addActionListener(l ->
            {
                QueryAction selectedValue = actionsList.getSelectedValue();
                testComponent.show(selectedValue);
            });

            buttonListPanel.add(add);
            buttonListPanel.add(remove);
            buttonListPanel.add(test);
            buttonListPanel.add(addTemplate);

            actionsList.addListSelectionListener(l ->
            {
                QueryAction selectedValue = actionsList.getSelectedValue();
                remove.setEnabled(selectedValue != null);
                test.setEnabled(selectedValue != null);
                if (selectedValue == null)
                {
                    selectedActionScrollPane.setViewportView(noSelectionLabel);
                }
                else
                {
                    actionComponent.init(selectedValue);
                    selectedActionScrollPane.setViewportView(actionComponent);
                }
            });

            JPanel actionListPanel = new JPanel(new BorderLayout());
            actionListPanel.add(new JScrollPane(actionsList), BorderLayout.CENTER);
            actionListPanel.add(buttonListPanel, BorderLayout.NORTH);

            splitPane.setLeftComponent(actionListPanel);

            add(splitPane, BorderLayout.CENTER);
        }

        /** Get actions for UI state. */
        QueryActions getQueryActions()
        {
            QueryActions queryActions = new QueryActions();
            int count = actionsListModel.getSize();
            for (int i = 0; i < count; i++)
            {
                queryActions.queryActions.add(actionsListModel.elementAt(i)
                        .clone());
            }
            return queryActions;
        }

        /** Init UI with provided actions. */
        void init(QueryActions queryActions)
        {
            actionsListModel.removeAllElements();
            for (QueryAction action : queryActions.queryActions)
            {
                actionsListModel.addElement(action.clone());
            }
        }
    }

    private class QueryActionComponent extends JPanel
    {
        private final JTextField name;
        private final JComboBox<TemplateTarget> templateTarget;
        private final JComboBox<OutputType> outputType;
        private final ITextEditor templateQuery;
        // private final JTextArea template;

        private QueryAction queryAction;

        public QueryActionComponent()
        {
            setLayout(new GridBagLayout());
            name = new JTextField();
            name.setToolTipText("Name of action. Has template support and model values is accessed via notion: ${value}");
            name.getDocument()
                    .addDocumentListener(new ADocumentListenerAdapter()
                    {
                        @Override
                        protected void update()
                        {
                            queryAction.name = name.getText();
                            notifyDirtyStateConsumers();
                        }
                    });
            templateTarget = new JComboBox<TemplateTarget>(TemplateTarget.values());
            templateTarget.addActionListener(l ->
            {
                TemplateTarget value = (TemplateTarget) templateTarget.getSelectedItem();
                queryAction.templateTarget = value;
                notifyDirtyStateConsumers();
            });
            templateTarget.setToolTipText("<html>" + """
                    Target of the generated template.
                    <ul>
                    <li><b>%s</b> - the generated template will be executed and
                          the result will be outputed according to OutputType.</li>
                    <li><b>%s</b> - the generated template will be outputed according to OutputType<br/>
                    NOTE! OutputType = Table is not supported in this mode.</li>
                    </ul>
                    </html>
                    """.formatted(TemplateTarget.EXECUTE, TemplateTarget.AS_IS));
            outputType = new JComboBox<OutputType>(Arrays.stream(OutputType.values())
                    .filter(OutputType::isInteractive)
                    .toArray(OutputType[]::new));
            outputType.addActionListener(l ->
            {
                OutputType value = (OutputType) outputType.getSelectedItem();
                queryAction.outputType = value;
                notifyDirtyStateConsumers();
            });

            templateQuery = editorFactory.createTextEditor(new ITextEditorKit()
            {
                @Override
                public String getSyntaxMimeType()
                {
                    return "text/sql";
                }

                @Override
                public int getRows()
                {
                    return 6;
                }
            });
            templateQuery.addPropertyChangeListener(new PropertyChangeListener()
            {
                @Override
                public void propertyChange(PropertyChangeEvent evt)
                {
                    if (IEditor.VALUE_CHANGED.equalsIgnoreCase(evt.getPropertyName()))
                    {
                        queryAction.templateQuery = templateQuery.getValue(true);
                        notifyDirtyStateConsumers();
                    }
                }
            });

            // template = new JTextArea();
            // template.getDocument()
            // .addDocumentListener(new ADocumentListenerAdapter()
            // {
            // @Override
            // protected void update()
            // {
            // queryAction.template = template.getText();
            // notifyDirtyStateConsumers();
            // }
            // });

            Insets insets = new Insets(2, 2, 2, 2);

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.insets = insets;
            gbc.anchor = GridBagConstraints.WEST;

            add(new JLabel("Name:"), gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 1;
            gbc.gridy = 0;
            gbc.weightx = 1.0d;
            gbc.insets = insets;
            gbc.fill = GridBagConstraints.HORIZONTAL;

            add(name, gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 1;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.insets = insets;

            add(new JLabel("Template Target:")
            {
                {
                    setToolTipText(templateTarget.getToolTipText());
                }
            }, gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 1;
            gbc.gridy = 1;
            gbc.weightx = 1.0d;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = insets;

            add(templateTarget, gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 2;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.insets = insets;

            add(new JLabel("Output Type:")
            {
                {
                    setToolTipText(outputType.getToolTipText());
                }
            }, gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 1;
            gbc.gridy = 2;
            gbc.weightx = 1.0d;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = insets;

            add(outputType, gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 3;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.insets = insets;

            // TODO: refactor this to a MetaParameterUtils class that can be reused
            String metaParamsString = "";
            metaParamsString += "<ul>";
            metaParamsString += "<li><b>queryEngineClassName</b> - Class name of the active query engine</li>";
            metaParamsString += "<li><b>tableRow</b> - The row in the table that this action was triggered from with sub properties:";
            metaParamsString += "<ul>";
            metaParamsString += "<li><b>cell</b> - Selected cell. Has two sub properties: <b>header, value</b></li>";
            metaParamsString += "<li><b>columns</b> - Columns of the selected row. List with objects with sub properties: <b>header, value</b></li>";
            metaParamsString += "</ul>";
            metaParamsString += "</li>";
            metaParamsString += "</ul>";
            for (IQueryEngine engine : config.getQueryEngines())
            {
                List<MetaParameter> meta = engine.createState()
                        .getMetaParameters(true);
                if (meta.isEmpty())
                {
                    continue;
                }
                metaParamsString += "<h4>" + engine.getTitle()
                                    + " (className: "
                                    + engine.getClass()
                                            .getSimpleName()
                                    + ")</h4>";
                metaParamsString += "<ul>";
                for (MetaParameter entry : meta)
                {
                    metaParamsString += "<li><b>" + entry.name() + "</b> - " + entry.description() + "</li>";
                }
                metaParamsString += "</ul>";
            }

            JLabel templateQueryLabel = new JLabel("Template Query:", IconFactory.of(FontAwesome.INFO), SwingConstants.LEADING);
            templateQueryLabel.setToolTipText("<html>" + """
                    <b>PayloadBuilder Query</b> that acts as both activation and template generator.<br/>
                    If this query don't return any rows this action is considered disabled.<br/>
                    <br/>
                    Query must have a column <b>template</b>, this will be the resulting action template.<br/>
                    If more than one row is returned then all <b>template values</b> will be concatenated.<br/>
                    Other columns can be provided to generate the name of the action (if it has template placeholders).
                    <h4>Available parameters:<h4>
                    """ + metaParamsString + "</html>");

            templateQueryLabel.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent e)
                {
                    JOptionPane.showMessageDialog(QueryActionComponent.this, new JLabel(templateQueryLabel.getToolTipText()), "Template Query", JOptionPane.INFORMATION_MESSAGE);
                }
            });

            add(templateQueryLabel, gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 4;
            gbc.weightx = 1.0d;
            gbc.weighty = 0.5d;
            gbc.gridwidth = 2;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.insets = insets;

            add(templateQuery.getComponent(), gbc);
        }

        void init(QueryAction queryAction)
        {
            disableNotify = true;
            this.queryAction = queryAction;
            name.setText(queryAction.name);
            templateTarget.setSelectedItem(queryAction.templateTarget);
            outputType.setSelectedItem(queryAction.outputType);
            templateQuery.setValue(queryAction.getTemplateQuery());
            disableNotify = false;
        }
    }

    private class ActionTesterComponent extends DialogUtils.ADialog
    {
        private final ITextEditor model;
        private final ITextEditor templateQuery;
        private final JTextField name;
        // private final JTextArea template;
        private final JTextArea templateResult;
        private final JTextField nameResult;
        private final JComboBox<IQueryEngine> queryEngines;

        private boolean dialogResult;
        private boolean dirty;

        @SuppressWarnings("unchecked")
        ActionTesterComponent()
        {
            setTitle("Test Actions");
            setResizable(true);
            getContentPane().setLayout(new BorderLayout());

            JSplitPane rootSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
            JSplitPane topSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

            rootSplit.setTopComponent(topSplit);

            // Model
            String testModelToolTip = "<html>" + """
                    Set a test model for selected query engine that can be used to
                    model the table actions template.
                    This is how the model structure looks when Queryeer processes table actions,
                    so it's possible to edit and model real scenarios.
                    </html>
                    """;

            JPanel modelJsonPanel = new JPanel(new FlowLayout(FlowLayout.LEADING));
            modelJsonPanel.add(new JLabel("Test Model For:")
            {
                {
                    setHorizontalAlignment(SwingConstants.LEFT);
                    setToolTipText(testModelToolTip);
                }
            });

            queryEngines = new JComboBox<>();
            queryEngines.setModel(new DefaultComboBoxModel<IQueryEngine>());
            queryEngines.setToolTipText(testModelToolTip);
            queryEngines.setRenderer(new DefaultListCellRenderer()
            {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
                {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    if (value instanceof IQueryEngine engine)
                    {
                        setText(engine.getTitle());
                        setIcon(engine.getIcon());
                    }
                    return this;
                }
            });
            for (IQueryEngine queryEngine : config.getQueryEngines())
            {
                ((DefaultComboBoxModel<IQueryEngine>) queryEngines.getModel()).addElement(queryEngine);
            }
            modelJsonPanel.add(queryEngines);

            JPanel modelPanel = new JPanel(new BorderLayout());
            modelPanel.add(modelJsonPanel, BorderLayout.NORTH);
            modelPanel.setBorder(BorderFactory.createTitledBorder("Test Model"));
            model = editorFactory.createTextEditor(new ITextEditorKit()
            {
                @Override
                public String getSyntaxMimeType()
                {
                    return "text/json";
                }

                @Override
                public int getRows()
                {
                    return 6;
                }
            });

            modelPanel.add(model.getComponent(), BorderLayout.CENTER);
            topSplit.setLeftComponent(modelPanel);

            // Name / Template Query
            JPanel templateQueryPanel = new JPanel(new BorderLayout());
            templateQueryPanel.setBorder(BorderFactory.createTitledBorder("Action"));
            name = new JTextField();
            name.getDocument()
                    .addDocumentListener(new ADocumentListenerAdapter()
                    {
                        @Override
                        protected void update()
                        {
                            dirty = true;
                        }
                    });

            templateQueryPanel.add(name, BorderLayout.NORTH);
            templateQuery = editorFactory.createTextEditor(new ITextEditorKit()
            {
                @Override
                public String getSyntaxMimeType()
                {
                    return "text/sql";
                }

                @Override
                public int getRows()
                {
                    return 6;
                }
            });
            templateQuery.addPropertyChangeListener(new PropertyChangeListener()
            {
                @Override
                public void propertyChange(PropertyChangeEvent evt)
                {
                    if (IEditor.VALUE_CHANGED.equals(evt.getPropertyName()))
                    {
                        dirty = true;
                    }
                }
            });

            templateQueryPanel.add(templateQuery.getComponent(), BorderLayout.CENTER);
            topSplit.setRightComponent(templateQueryPanel);

            // Result
            JPanel resultPanel = new JPanel(new BorderLayout());
            resultPanel.setBorder(BorderFactory.createTitledBorder("Result"));

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.NONE;
            JButton run = new JButton("Run");

            JPanel topPanel = new JPanel(new GridBagLayout());
            topPanel.add(run, gbc);

            nameResult = new JTextField();
            nameResult.setEditable(false);

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 1;
            gbc.weightx = 1.0d;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;

            topPanel.add(nameResult, gbc);

            resultPanel.add(topPanel, BorderLayout.NORTH);

            templateResult = new JTextArea();
            templateResult.setEditable(false);

            resultPanel.add(new JScrollPane(templateResult), BorderLayout.CENTER);
            rootSplit.setBottomComponent(resultPanel);

            queryEngines.addActionListener(l ->
            {
                if (queryEngines.getSelectedItem() instanceof IQueryEngine queryEngine)
                {
                    setTestModel(queryEngine);
                }
            });

            run.addActionListener(l ->
            {
                Map<String, Object> modelValue;
                try
                {
                    modelValue = MAPPER.readValue(String.valueOf(model.getValue(true)), Map.class);
                }
                catch (JsonProcessingException e)
                {
                    templateResult.setText("Error parsing model: " + e.getMessage());
                    return;
                }

                CompiledQuery compiledQuery;
                try
                {
                    compiledQuery = Payloadbuilder.compile(CATALOG_REGISTRY, String.valueOf(templateQuery.getValue(true)));
                }
                catch (Exception e)
                {
                    templateResult.setText("Error compiling template query: " + e.getMessage());
                    return;
                }

                try
                {
                    AtomicReference<String> name = new AtomicReference<String>(this.name.getText());
                    String resultTemplate = processTemplate(compiledQuery, modelValue, m ->
                    {
                        name.set(templateService.process("TableAction", name.get(), m, true));
                    }, true);

                    nameResult.setText(name.get());
                    if (resultTemplate.isEmpty())
                    {
                        templateResult.setText("No query produced. If a template was expected make sure the column 'template' is present.");
                    }
                    else
                    {
                        templateResult.setText(resultTemplate);
                        templateResult.setCaretPosition(0);
                    }
                }
                catch (Exception e)
                {
                    StringWriter sw = new StringWriter();
                    ExceptionUtils.printRootCauseStackTrace(e, new java.io.PrintWriter(sw));
                    templateResult.setText("Error generating template: " + sw);
                    templateResult.setCaretPosition(0);
                    return;
                }
            });

            add(rootSplit, BorderLayout.CENTER);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton ok = new JButton("OK");
            ok.addActionListener(l ->
            {
                dialogResult = true;
                setVisible(false);
            });
            buttonPanel.add(ok);
            JButton cancel = new JButton("Cancel");
            cancel.addActionListener(l -> setVisible(false));
            buttonPanel.add(cancel);

            add(buttonPanel, BorderLayout.SOUTH);

            setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
            setPreferredSize(Constants.DEFAULT_DIALOG_SIZE);
            pack();
            setLocationRelativeTo(null);
            pack();

            rootSplit.setDividerLocation(Constants.DEFAULT_DIALOG_SIZE.height / 2);
            topSplit.setDividerLocation(Constants.DEFAULT_DIALOG_SIZE.width / 2);

            setModalityType(ModalityType.APPLICATION_MODAL);
        }

        void setTestModel(IQueryEngine queryEngine)
        {
            model.setValue(getTestModelJson(queryEngine));
        }

        void show(QueryAction action)
        {
            this.setTitle("Test Action: " + action.name);
            this.queryEngines.setSelectedItem(queryEngines.getModel()
                    .getElementAt(0));
            setTestModel(queryEngines.getModel()
                    .getElementAt(0));
            this.name.setText(action.name);
            this.templateQuery.setValue(action.getTemplateQuery());
            this.templateResult.setText("");
            this.nameResult.setText("");
            setLocationRelativeTo(component);
            // Reset dirty after we set all values
            dirty = false;
            dialogResult = false;
            setVisible(true);
            if (dialogResult
                    && dirty)
            {
                // action.template = template.getText();
                action.name = name.getText();
                action.templateQuery = templateQuery.getValue(true);
                notifyDirtyStateConsumers();
                component.actionComponent.init(action);
            }
        }
    }

    private List<QueryAction> createTemplates()
    {
        QueryAction souroundingDocuments = new QueryAction();
        souroundingDocuments.name = "Sourounding documents at: ${timestamp}";
        souroundingDocuments.outputType = OutputType.NEW_QUERY_EXECUTE;
        souroundingDocuments.templateTarget = TemplateTarget.AS_IS;
        souroundingDocuments.templateQuery = """
                SELECT `
                use ${alias}
                use ${alias}.endpoint = '${endpoint}'
                use ${alias}.index = '${index}'

                SELECT TOP 1000 "@timestamp", *
                FROM _doc
                WHERE "@timestamp" >= '${DATEADD(second, -5, timestamp)}'
                AND "@timestamp" <= '${DATEADD(second, 5, timestamp)}'
                ORDER by "@timestamp"
                ` template
                , timestamp
                FROM
                (
                  SELECT x.alias
                  , x.properties.endpoint
                  , x.properties.index
                  , CAST(@tableRow.columns.filter(col -> LOWER(col.header) like '%timestamp%')[0].value AS DATETIMEOFFSET) timestamp
                  FROM (totable(@catalogs)) x
                  -- ES catalog must be present and default
                  WHERE LOWER(x.name) = 'escatalog' and x.default
                ) x
                 -- We must have a timestamp column
                WHERE x.timestamp IS NOT NULL
                """;

        QueryAction correlatedDocuments = new QueryAction();
        correlatedDocuments.name = "Correlated documents of: ${correlationId}";
        correlatedDocuments.outputType = OutputType.NEW_QUERY_EXECUTE;
        correlatedDocuments.templateTarget = TemplateTarget.AS_IS;
        correlatedDocuments.templateQuery = """
                SELECT `
                use ${alias}
                use ${alias}.endpoint = '${endpoint}'
                use ${alias}.index = '${index}'

                SELECT TOP 1000 "@timestamp", *
                FROM _doc
                WHERE correlationId = '${correlationId}'
                AND "@timestamp" >= '${DATEADD(minute, -5, timestamp)}'
                AND "@timestamp" <= '${DATEADD(minute, 5, timestamp)}'
                ORDER BY "@timestamp"
                ` template
                , correlationId
                FROM
                (
                  SELECT x.alias
                  , x.properties.endpoint
                  , x.properties.index
                  , @tableRow.columns.filter(col -> LOWER(col.header) = 'correlationid')[0].value correlationId
                  , CAST(@tableRow.columns.filter(col -> LOWER(col.header) LIKE '%timestamp%')[0].value AS DATETIMEOFFSET) timestamp
                  FROM (totable(@catalogs)) x
                  -- ES catalog must be present and default
                  WHERE LOWER(x.name) = 'escatalog' and x.default
                ) x
                -- We must have a correlationId and timestamp columns
                WHERE x.correlationId IS NOT NULL
                AND x.timestamp IS NOT NULL
                """;

        QueryAction searchObjects = new QueryAction();
        searchObjects.name = "Search objects where ${column} = '${value}'";
        searchObjects.outputType = OutputType.NEW_QUERY_EXECUTE;
        searchObjects.templateTarget = TemplateTarget.EXECUTE;
        searchObjects.templateQuery = """
                SELECT `
                  SELECT CONCAT('SELECT * FROM ', s.name, '.', o.name, ' WHERE [', c.name, '] = ''${REPLACE(@tableRow.cell.value, '''', '''''''''')}''')
                  FROM sys.columns c
                  INNER join sys.objects o
                    ON o.object_id = c.object_id
                  INNER JOIN sys.schemas s
                    ON s.schema_id = o.schema_id
                  WHERE c.name = '${@tableRow.cell.header}'
                ` template
                , @tableRow.cell.header column
                , REPLACE(CAST(@tableRow.cell.value AS STRING), '''', '''''') value
                WHERE LOWER(@url) LIKE '%sqlserver%'
                """;

        return List.of(correlatedDocuments, souroundingDocuments, searchObjects);
    }
}
