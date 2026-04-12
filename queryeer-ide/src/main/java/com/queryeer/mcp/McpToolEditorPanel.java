package com.queryeer.mcp;

import static java.util.Objects.requireNonNull;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import com.queryeer.api.component.ListPropertiesComponent;
import com.queryeer.api.editor.IEditorFactory;
import com.queryeer.api.editor.ITextEditor;
import com.queryeer.api.editor.ITextEditorKit;
import com.queryeer.api.extensions.engine.IMcpHandler;
import com.queryeer.api.extensions.engine.IQueryEngine;

/**
 * Editor panel for a single MCP tool. Displays and edits all tool properties including the query text editor. Shows an engine-specific connection component when the query engine is selected.
 */
class McpToolEditorPanel extends JPanel
{
    private static final ITextEditorKit SQL_KIT = new ITextEditorKit()
    {
        @Override
        public String getSyntaxMimeType()
        {
            return "text/sql";
        }
    };

    private final IEditorFactory editorFactory;
    private final Consumer<Boolean> dirtyConsumer;

    // UI components
    private final JTextField nameField = new JTextField();
    private final JTextField descriptionField = new JTextField();
    private final JCheckBox activeCheckBox = new JCheckBox();
    private final JComboBox<EngineItem> engineCombo;
    private final ListPropertiesComponent<McpToolParameter> parametersComponent;
    private final JPanel connectionArea = new JPanel(new BorderLayout());
    private final JTextArea infoArea;
    private ITextEditor queryEditor;

    // Currently displayed tool
    private McpTool currentTool;
    private boolean loading = false;

    McpToolEditorPanel(IEditorFactory editorFactory, List<IQueryEngine> engines, Consumer<Boolean> dirtyConsumer)
    {
        this.editorFactory = requireNonNull(editorFactory, "editorFactory");
        requireNonNull(engines, "engines");
        this.dirtyConsumer = requireNonNull(dirtyConsumer, "dirtyConsumer");

        List<EngineItem> engineItems = new ArrayList<>();
        for (int i = 0; i < engines.size(); i++)
        {
            IQueryEngine queryEngine = engines.get(i);
            IMcpHandler mcpHandler = queryEngine.getMcpHandler();
            if (mcpHandler != null)
            {
                engineItems.add(new EngineItem(queryEngine, mcpHandler));
            }
        }
        engineCombo = new JComboBox<>(engineItems.toArray(new EngineItem[0]));
        engineCombo.setRenderer(new DefaultListCellRenderer()
        {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
            {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value != null)
                {
                    IQueryEngine queryEngine = ((EngineItem) value).engine;
                    label.setText(queryEngine.getTitle());
                    label.setIcon(queryEngine.getIcon());
                }
                return label;
            }
        });

        parametersComponent = new ListPropertiesComponent<>(McpToolParameter.class, this::guardedDirty, McpToolParameter::new, McpToolEditorPanel::cloneParameter);

        infoArea = new JTextArea();
        infoArea.setEditable(false);
        infoArea.setRows(4);
        infoArea.setBackground(new Color(255, 255, 220));
        infoArea.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        initUI();
        attachListeners();
    }

    private void initUI()
    {
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // Name
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        add(new JLabel("Name:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        add(nameField, gbc);
        row++;

        // Description
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        add(new JLabel("Description:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        add(descriptionField, gbc);
        row++;

        // Active
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        add(new JLabel("Active:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        add(activeCheckBox, gbc);
        row++;

        // Engine
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        add(new JLabel("Engine:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        add(engineCombo, gbc);
        row++;

        // Connection area (dynamic, swapped when engine changes)
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        add(connectionArea, gbc);
        gbc.gridwidth = 1;
        row++;

        // Parameters
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 0.3;
        JPanel paramsPanel = new JPanel(new BorderLayout());
        paramsPanel.setBorder(BorderFactory.createTitledBorder("Parameters"));
        paramsPanel.add(parametersComponent, BorderLayout.CENTER);
        add(paramsPanel, gbc);
        row++;

        // Query editor
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 0.7;
        gbc.fill = GridBagConstraints.BOTH;
        JPanel queryPanel = new JPanel(new BorderLayout());
        queryPanel.setBorder(BorderFactory.createTitledBorder("Query"));
        queryEditor = editorFactory.createTextEditor(SQL_KIT);
        queryPanel.add(infoArea, BorderLayout.NORTH);
        queryPanel.add(queryEditor.getComponent(), BorderLayout.CENTER);
        add(queryPanel, gbc);
    }

    private void attachListeners()
    {
        nameField.getDocument()
                .addDocumentListener(new SimpleDocumentListener(() ->
                {
                    if (!loading
                            && currentTool != null)
                    {
                        currentTool.setName(nameField.getText());
                        dirtyConsumer.accept(true);
                    }
                }));

        descriptionField.getDocument()
                .addDocumentListener(new SimpleDocumentListener(() ->
                {
                    if (!loading
                            && currentTool != null)
                    {
                        currentTool.setDescription(descriptionField.getText());
                        dirtyConsumer.accept(true);
                    }
                }));

        activeCheckBox.addActionListener(e ->
        {
            if (!loading
                    && currentTool != null)
            {
                currentTool.setActive(activeCheckBox.isSelected());
                dirtyConsumer.accept(true);
            }
        });

        engineCombo.addActionListener(e ->
        {
            if (!loading
                    && currentTool != null)
            {
                EngineItem selected = (EngineItem) engineCombo.getSelectedItem();
                if (selected != null)
                {
                    String newEngineClass = selected.engine.getClass()
                            .getName();
                    if (!newEngineClass.equals(currentTool.getEngineClass()))
                    {
                        // Engine changed — reset connection config
                        currentTool.setEngineClass(newEngineClass);
                        currentTool.setConnectionConfig(new HashMap<>());
                    }
                    updateConnectionArea(selected, currentTool.getConnectionConfig());
                    dirtyConsumer.accept(true);
                }
            }
        });

        queryEditor.addPropertyChangeListener(e ->
        {
            if (!loading
                    && currentTool != null
                    && (ITextEditor.DIRTY.equals(e.getPropertyName())
                            || ITextEditor.VALUE_CHANGED.equals(e.getPropertyName())))
            {
                currentTool.setQuery((String) queryEditor.getValue(true));
                dirtyConsumer.accept(true);
            }
        });
    }

    /**
     * Loads a tool into the editor. Saves any unsaved state from the previous tool first (already done by reference updates in listeners).
     */
    void loadTool(McpTool tool)
    {
        currentTool = tool;
        if (tool == null)
        {
            loading = true;
            try
            {
                nameField.setText("");
                descriptionField.setText("");
                activeCheckBox.setSelected(false);
                updateConnectionArea(null, null);
                parametersComponent.init(new ArrayList<>());
                queryEditor.setValue("");
            }
            finally
            {
                loading = false;
            }
            setEditorEnabled(false);
            return;
        }
        setEditorEnabled(true);

        loading = true;
        try
        {
            nameField.setText(tool.getName());
            descriptionField.setText(tool.getDescription());
            activeCheckBox.setSelected(tool.isActive());

            // Select engine in combo
            String engineClass = tool.getEngineClass();
            int matchIndex = -1;
            for (int i = 0; i < engineCombo.getItemCount(); i++)
            {
                if (engineCombo.getItemAt(i).engine.getClass()
                        .getName()
                        .equals(engineClass))
                {
                    matchIndex = i;
                    break;
                }
            }
            if (matchIndex < 0
                    && engineCombo.getItemCount() > 0)
            {
                matchIndex = 0;
                tool.setEngineClass(engineCombo.getItemAt(0).engine.getClass()
                        .getName());
            }
            engineCombo.setSelectedIndex(matchIndex);

            // Update connection area
            EngineItem engine = getSelectedEngine();
            updateConnectionArea(engine, tool.getConnectionConfig());

            // Parameters
            parametersComponent.init(new ArrayList<>(tool.getParameters()));

            // Query
            queryEditor.setValue(tool.getQuery() != null ? tool.getQuery()
                    : "");
        }
        finally
        {
            loading = false;
        }
    }

    /**
     * Saves the current editor state back to the current tool's parameters list. Called before commitChanges.
     */
    void saveCurrentParameters()
    {
        if (currentTool != null)
        {
            currentTool.setParameters(parametersComponent.getResult());
        }
    }

    private void guardedDirty(boolean dirty)
    {
        if (!loading)
        {
            dirtyConsumer.accept(dirty);
        }
    }

    private void setEditorEnabled(boolean enabled)
    {
        setComponentEnabled(this, enabled);
        // The query editor component tree may not be reachable via getComponents() so set it directly
        queryEditor.getComponent()
                .setEnabled(enabled);
    }

    private static void setComponentEnabled(java.awt.Container container, boolean enabled)
    {
        for (Component c : container.getComponents())
        {
            c.setEnabled(enabled);
            if (c instanceof java.awt.Container)
            {
                setComponentEnabled((java.awt.Container) c, enabled);
            }
        }
    }

    private void updateConnectionArea(EngineItem engine, Map<String, Object> connectionConfig)
    {
        connectionArea.removeAll();
        if (engine != null
                && connectionConfig != null)
        {
            Component conComp = engine.mcpHandler.getMcpConnectionComponent(connectionConfig, this::guardedDirty);
            if (conComp != null)
            {
                connectionArea.add(conComp, BorderLayout.CENTER);
            }
        }
        connectionArea.revalidate();
        connectionArea.repaint();
        updateInfoArea(engine);
    }

    private void updateInfoArea(EngineItem engine)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Freemarker: Use ${paramName} to access parameter values in your query.\n");
        sb.append("WARNING: Do NOT inject values directly into SQL using ${...} — this is vulnerable to SQL injection.\n");
        if (engine != null)
        {
            String hint = engine.mcpHandler.getParameterSyntaxHint();
            if (hint != null)
            {
                sb.append("Parameters: ")
                        .append(hint);
            }
        }
        infoArea.setText(sb.toString());
    }

    private EngineItem getSelectedEngine()
    {
        return (EngineItem) engineCombo.getSelectedItem();
    }

    private static McpToolParameter cloneParameter(McpToolParameter src)
    {
        McpToolParameter clone = new McpToolParameter();
        clone.setName(src.getName());
        clone.setDescription(src.getDescription());
        clone.setType(src.getType());
        return clone;
    }

    /** Wrapper for display in the combo box. */
    private static class EngineItem
    {
        final IQueryEngine engine;
        final IMcpHandler mcpHandler;

        EngineItem(IQueryEngine engine, IMcpHandler mcpHandler)
        {
            this.engine = engine;
            this.mcpHandler = mcpHandler;
        }

        @Override
        public String toString()
        {
            return engine.getTitle();
        }
    }

    /** Minimal DocumentListener that delegates to a Runnable. */
    private static class SimpleDocumentListener implements javax.swing.event.DocumentListener
    {
        private final Runnable action;

        SimpleDocumentListener(Runnable action)
        {
            this.action = action;
        }

        @Override
        public void insertUpdate(javax.swing.event.DocumentEvent e)
        {
            action.run();
        }

        @Override
        public void removeUpdate(javax.swing.event.DocumentEvent e)
        {
            action.run();
        }

        @Override
        public void changedUpdate(javax.swing.event.DocumentEvent e)
        {
            action.run();
        }
    }
}
