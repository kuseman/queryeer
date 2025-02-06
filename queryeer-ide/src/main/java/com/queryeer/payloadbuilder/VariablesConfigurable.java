package com.queryeer.payloadbuilder;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.lowerCase;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import org.apache.commons.lang3.reflect.MethodUtils;
import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.kordamp.ikonli.swing.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.queryeer.Constants;
import com.queryeer.api.component.ADocumentListenerAdapter;
import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.service.IConfig;
import com.queryeer.api.service.ICryptoService;

import se.kuseman.payloadbuilder.api.catalog.ResolvedType;
import se.kuseman.payloadbuilder.api.execution.ValueVector;
import se.kuseman.payloadbuilder.api.utils.StringUtils;
import se.kuseman.payloadbuilder.core.execution.QuerySession;

/** Configurable for payloadbuilder variables. */
class VariablesConfigurable implements IConfigurable
{
    private static final String NAME = VariablesConfigurable.class.getPackageName() + ".Variables";
    private static final Logger LOGGER = LoggerFactory.getLogger(VariablesConfigurable.class);
    private final IConfig config;
    private final ICryptoService cryptoService;
    private final List<Consumer<Boolean>> dirtyStateConsumers = new ArrayList<>();
    private VariablesComponent component;
    private Variables variables;
    private Runnable configChangedListener;

    VariablesConfigurable(IConfig config, ICryptoService cryptoService)
    {
        this.config = requireNonNull(config, "config");
        this.cryptoService = requireNonNull(cryptoService, "cryptoService");
        load();
    }

    @Override
    public Component getComponent()
    {
        if (component == null)
        {
            component = new VariablesComponent();
            component.init(variables.clone());
        }
        return component;
    }

    @Override
    public String getTitle()
    {
        return "Variables";
    }

    @Override
    public String groupName()
    {
        return CatalogsConfigurable.PAYLOADBUILDER;
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
    public boolean commitChanges()
    {
        File file = config.getConfigFileName(NAME);

        Variables variables = component.getVariables();

        // Encrypt all secrets
        for (Environment e : variables.environments)
        {
            for (Variable v : e.secretVariables)
            {
                String encryptedValue = cryptoService.encryptString(v.value);
                // Aborted
                if (encryptedValue == null)
                {
                    return false;
                }

                v.value = encryptedValue;
            }
        }

        try
        {
            CatalogsConfigurable.MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValue(file, variables);
            this.variables = variables;
        }
        catch (IOException e)
        {
            JOptionPane.showMessageDialog(component, "Error saving config, message: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        // Re-initalize to get a new copy in UI
        component.init(this.variables.clone());

        if (configChangedListener != null)
        {
            configChangedListener.run();
        }
        return true;
    }

    @Override
    public void revertChanges()
    {
        component.init(variables.clone());
    }

    @Override
    public EncryptionResult reEncryptSecrets(ICryptoService newCryptoService)
    {
        boolean change = false;
        for (Environment e : variables.environments)
        {
            for (Variable v : e.secretVariables)
            {
                String stringValue = v.value;
                String decryptedValue = cryptoService.decryptString(stringValue);
                if (decryptedValue == null)
                {
                    return EncryptionResult.ABORT;
                }

                String encryptedValue = newCryptoService.encryptString(decryptedValue);
                if (encryptedValue == null)
                {
                    return EncryptionResult.ABORT;
                }

                v.value = encryptedValue;
                change = true;
            }
        }
        return change ? EncryptionResult.SUCCESS
                : EncryptionResult.NO_CHANGE;
    }

    void addConfigChangedListener(Runnable listener)
    {
        this.configChangedListener = listener;
    }

    List<Environment> getEnvironments()
    {
        return variables.environments;
    }

    @SuppressWarnings("unchecked")
    private Map<String, ValueVector> getVariablesMap(QuerySession session)
    {
        // TODO: make this public in PLB
        Map<String, ValueVector> variables;
        try
        {
            variables = (Map<String, ValueVector>) MethodUtils.invokeMethod(session, true, "getVariables");
        }
        catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e)
        {
            LOGGER.error("Error extracting variables map from query session", e);
            return null;
        }
        return variables;
    }

    /** Set variables to session */
    void beforeQuery(QuerySession session, Environment env)
    {
        Map<String, ValueVector> variables = getVariablesMap(session);
        if (variables == null)
        {
            return;
        }
        for (Variable v : env.variables)
        {
            Object value = Variable.getValue(v.name, v.value, v.isJson);
            variables.put(lowerCase(v.name), value == null ? ValueVector.literalNull(ResolvedType.ANY, 1)
                    : ValueVector.literalAny(1, value));
        }
        for (Variable v : env.secretVariables)
        {
            String stringValue = v.value;
            String decryptedValue = cryptoService.decryptString(stringValue);
            if (decryptedValue == null)
            {
                break;
            }

            Object value = Variable.getValue(v.name, decryptedValue, v.isJson);
            variables.put(lowerCase(v.name), value == null ? ValueVector.literalNull(ResolvedType.ANY, 1)
                    : ValueVector.literalAny(1, value));
        }
    }

    /** Clear variables from session */
    void afterQuery(QuerySession session, Environment env)
    {
        Map<String, ValueVector> variables = getVariablesMap(session);
        if (variables == null)
        {
            return;
        }
        for (Variable v : env.variables)
        {
            variables.remove(lowerCase(v.name));
        }
        for (Variable v : env.secretVariables)
        {
            variables.remove(lowerCase(v.name));
        }
    }

    private void load()
    {
        File file = config.getConfigFileName(NAME);
        if (file.exists())
        {
            try
            {
                variables = CatalogsConfigurable.MAPPER.readValue(file, Variables.class);
            }
            catch (IOException e)
            {
                LOGGER.error("Error parsing config {}", file.getAbsolutePath(), e);
            }
        }

        if (variables == null)
        {
            variables = new Variables();
        }
    }

    /** UI component. */
    class VariablesComponent extends JPanel
    {
        private final DefaultComboBoxModel<Environment> environmentsModel;
        private final VariablesTableModel variablesModel;
        private final VariablesTableModel secretVariablesModel;
        private final JLabel variablesWarning;
        private final JLabel secretVariablesWarning;

        private boolean disableNotify;

        void init(Variables variables)
        {
            disableNotify = true;
            environmentsModel.removeAllElements();
            environmentsModel.addAll(variables.environments);
            if (environmentsModel.getSize() > 0)
            {
                environmentsModel.setSelectedItem(variables.environments.get(0));
            }
            disableNotify = false;
        }

        Variables getVariables()
        {
            Variables result = new Variables();
            int size = environmentsModel.getSize();
            result.environments = new ArrayList<>(size);
            for (int i = 0; i < size; i++)
            {
                result.environments.add(environmentsModel.getElementAt(i));
            }
            return result;
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

        VariablesComponent()
        {
            setLayout(new GridBagLayout());

            Insets insets = new Insets(2, 2, 2, 2);
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.insets = insets;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.NONE;
            add(new JLabel("Environment:"), gbc);

            environmentsModel = new DefaultComboBoxModel<>();
            JComboBox<Environment> environments = new JComboBox<>();
            environments.setModel(environmentsModel);

            gbc = new GridBagConstraints();
            gbc.gridx = 1;
            gbc.gridy = 0;
            gbc.weightx = 1.0d;
            gbc.insets = insets;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            add(environments, gbc);

            JPanel envButtonPanel = new JPanel();
            JButton addEnv = new JButton(FontIcon.of(FontAwesome.PLUS, 8));
            addEnv.addActionListener(l ->
            {
                Environment env = new Environment();
                env.name = "New environment";
                if (environmentsModel.getSize() > 0)
                {
                    env.name += " (" + environmentsModel.getSize() + ")";
                }
                environmentsModel.addElement(env);
                environmentsModel.setSelectedItem(env);
                notifyDirtyStateConsumers();
            });
            envButtonPanel.add(addEnv);
            JButton removeEnv = new JButton(FontIcon.of(FontAwesome.MINUS, 8));
            removeEnv.addActionListener(l ->
            {
                Environment env = (Environment) environmentsModel.getSelectedItem();
                if (env == null)
                {
                    return;
                }

                int index = environments.getSelectedIndex();
                environmentsModel.removeElement(env);
                if (index >= environmentsModel.getSize())
                {
                    index = environmentsModel.getSize() - 1;
                }
                environmentsModel.setSelectedItem(environmentsModel.getElementAt(index));
                notifyDirtyStateConsumers();
            });
            removeEnv.setEnabled(false);
            envButtonPanel.add(removeEnv);

            gbc = new GridBagConstraints();
            gbc.gridx = 2;
            gbc.gridy = 0;
            gbc.insets = insets;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.NONE;
            add(envButtonPanel, gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 1;
            gbc.insets = insets;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.NONE;
            add(new JLabel("Name:"), gbc);

            JTextField name = new JTextField();
            name.setColumns(20);
            name.getDocument()
                    .addDocumentListener(new ADocumentListenerAdapter()
                    {
                        @Override
                        protected void update()
                        {
                            Environment env = (Environment) environmentsModel.getSelectedItem();
                            if (env != null)
                            {
                                env.name = name.getText();
                            }
                            environments.repaint();
                            notifyDirtyStateConsumers();
                        }
                    });
            gbc = new GridBagConstraints();
            gbc.gridx = 1;
            gbc.gridy = 1;
            gbc.insets = insets;
            gbc.anchor = GridBagConstraints.WEST;
            add(name, gbc);

            gbc.gridx = 0;
            gbc.gridy = 2;
            gbc.insets = insets;
            gbc.weightx = 1.0d;
            gbc.weighty = 1.0d;
            gbc.gridwidth = 3;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.BOTH;
            JTabbedPane tabbedPane = new JTabbedPane();
            add(tabbedPane, gbc);

            JPanel variablesPanel = new JPanel(new BorderLayout());
            tabbedPane.addTab("Variables", variablesPanel);

            JTable variables = new JTable();
            variables.setPreferredSize(new Dimension(100, 400));
            variables.putClientProperty("terminateEditOnFocusLost", true);
            variables.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            variablesModel = new VariablesTableModel(false);
            variables.setModel(variablesModel);
            variablesPanel.add(new JScrollPane(variables), BorderLayout.CENTER);
            secretVariablesModel = new VariablesTableModel(true);

            JPanel variableButtonPanel = new JPanel();
            JButton addVar = new JButton(FontIcon.of(FontAwesome.PLUS, 8));
            addVar.addActionListener(l ->
            {
                Environment env = (Environment) environmentsModel.getSelectedItem();
                if (env == null)
                {
                    return;
                }
                Variable var = new Variable();
                var.name = "var_" + (variablesModel.getRowCount() + secretVariablesModel.getRowCount());
                env.variables.add(var);
                variablesModel.fireTableDataChanged();
                setWarnings();
                notifyDirtyStateConsumers();
            });
            variableButtonPanel.add(addVar);
            JButton removeVar = new JButton(FontIcon.of(FontAwesome.MINUS, 8));
            removeVar.addActionListener(l ->
            {
                Environment env = (Environment) environmentsModel.getSelectedItem();
                if (env == null)
                {
                    return;
                }
                int row = variables.getSelectedRow();
                if (row >= 0)
                {
                    env.variables.remove(row);
                    variablesModel.fireTableDataChanged();
                    notifyDirtyStateConsumers();
                }
            });
            removeVar.setEnabled(false);
            variableButtonPanel.add(removeVar);

            variablesPanel.add(variableButtonPanel, BorderLayout.NORTH);

            variablesWarning = new JLabel();
            variablesWarning.setForeground(Color.RED);
            variablesPanel.add(variablesWarning, BorderLayout.SOUTH);

            JPanel secretVariablesPanel = new JPanel(new BorderLayout());
            tabbedPane.addTab("Secret Variables", secretVariablesPanel);

            JTable secretVariables = new JTable();
            InputMap im = secretVariables.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
            // Turn off copy from the secret table
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, Toolkit.getDefaultToolkit()
                    .getMenuShortcutKeyMaskEx()), "none");
            secretVariables.setPreferredSize(new Dimension(100, 400));
            secretVariables.putClientProperty("terminateEditOnFocusLost", true);
            secretVariables.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            secretVariables.setModel(secretVariablesModel);

            JPasswordField passwordField = new JPasswordField();
            secretVariables.getColumnModel()
                    .getColumn(2)
                    .setCellEditor(new DefaultCellEditor(passwordField));

            secretVariables.getColumnModel()
                    .getColumn(2)
                    .setCellRenderer(new DefaultTableCellRenderer()
                    {
                        @Override
                        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
                        {
                            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                            int length = 0;
                            if (value instanceof String)
                            {
                                length = ((String) value).length();
                            }
                            else if (value instanceof char[])
                            {
                                length = ((char[]) value).length;
                            }
                            setText(StringUtils.repeat('*', length));
                            return this;
                        }
                    });

            secretVariablesPanel.add(new JScrollPane(secretVariables), BorderLayout.CENTER);
            JPanel secretVariableButtonPanel = new JPanel();
            JButton addSecretVar = new JButton(FontIcon.of(FontAwesome.PLUS, 8));
            addSecretVar.addActionListener(l ->
            {
                Environment env = (Environment) environmentsModel.getSelectedItem();
                if (env == null)
                {
                    return;
                }
                Variable var = new Variable();
                var.name = "var_" + (variablesModel.getRowCount() + secretVariablesModel.getRowCount());
                env.secretVariables.add(var);
                secretVariablesModel.fireTableDataChanged();
                setWarnings();
                notifyDirtyStateConsumers();
            });
            secretVariableButtonPanel.add(addSecretVar);
            JButton removeSecretVar = new JButton(FontIcon.of(FontAwesome.MINUS, 8));
            removeSecretVar.addActionListener(l ->
            {
                Environment env = (Environment) environmentsModel.getSelectedItem();
                if (env == null)
                {
                    return;
                }
                int row = secretVariables.getSelectedRow();
                if (row >= 0)
                {
                    env.secretVariables.remove(row);
                    secretVariablesModel.fireTableDataChanged();
                    notifyDirtyStateConsumers();
                }
            });
            removeSecretVar.setEnabled(false);
            secretVariableButtonPanel.add(removeSecretVar);

            secretVariablesPanel.add(secretVariableButtonPanel, BorderLayout.NORTH);

            secretVariablesWarning = new JLabel();
            secretVariablesWarning.setForeground(Color.RED);
            secretVariablesPanel.add(secretVariablesWarning, BorderLayout.SOUTH);

            environments.addActionListener(l ->
            {
                disableNotify = true;
                Environment env = (Environment) environments.getSelectedItem();
                removeEnv.setEnabled(env != null);
                name.setText(env != null ? env.name
                        : "");
                name.setEnabled(env != null);
                variablesModel.fireTableDataChanged();
                secretVariablesModel.fireTableDataChanged();
                setWarnings();
                disableNotify = false;
            });

            variables.getSelectionModel()
                    .addListSelectionListener(l ->
                    {
                        int row = variables.getSelectedRow();
                        removeVar.setEnabled(row >= 0);
                    });
            secretVariables.getSelectionModel()
                    .addListSelectionListener(l ->
                    {
                        int row = secretVariables.getSelectedRow();
                        removeSecretVar.setEnabled(row >= 0);
                    });
        }

        void setWarnings()
        {
            Set<String> seenNames = new HashSet<>();
            List<String> duplicates = new ArrayList<>();
            Environment env = (Environment) environmentsModel.getSelectedItem();
            if (env != null)
            {
                for (Variable v : env.variables)
                {
                    if (!seenNames.add(org.apache.commons.lang3.StringUtils.lowerCase(v.name)))
                    {
                        duplicates.add(v.name);
                    }
                }
                for (Variable v : env.secretVariables)
                {
                    if (!seenNames.add(org.apache.commons.lang3.StringUtils.lowerCase(v.name)))
                    {
                        duplicates.add(v.name);
                    }
                }
            }

            secretVariablesWarning.setIcon(null);
            secretVariablesWarning.setText("");
            variablesWarning.setIcon(null);
            variablesWarning.setText("");

            if (!duplicates.isEmpty())
            {
                secretVariablesWarning.setIcon(Constants.WARNING_ICON);
                secretVariablesWarning.setText("Duplicate vars: " + duplicates);
                variablesWarning.setIcon(Constants.WARNING_ICON);
                variablesWarning.setText("Duplicate vars: " + duplicates);
            }
        }

        class VariablesTableModel extends AbstractTableModel
        {
            private final boolean secrets;

            VariablesTableModel(boolean secrets)
            {
                this.secrets = secrets;
            }

            Environment getEnv()
            {
                return (Environment) environmentsModel.getSelectedItem();
            }

            @Override
            public boolean isCellEditable(int rowIndex, int columnIndex)
            {
                return true;
            }

            @Override
            public Object getValueAt(int rowIndex, int columnIndex)
            {
                Variable variable = (secrets ? getEnv().secretVariables
                        : getEnv().variables).get(rowIndex);
                return switch (columnIndex)
                {
                    case 0 -> variable.name;
                    case 1 -> variable.isJson;
                    case 2 -> variable.value;
                    default -> throw new IllegalArgumentException("Invalid column index: " + columnIndex);
                };
            }

            @Override
            public void setValueAt(Object value, int row, int column)
            {
                Variable variable = (secrets ? getEnv().secretVariables
                        : getEnv().variables).get(row);
                if (column == 0)
                {
                    String var = (String) value;
                    var = var.replaceAll("[^A-Za-z0-9]", "_");
                    variable.name = var;
                }
                else if (column == 1)
                {
                    variable.isJson = (Boolean) value;
                }
                else if (column == 2)
                {
                    variable.value = (String) value;
                }
                setWarnings();
                notifyDirtyStateConsumers();
            }

            @Override
            public int getRowCount()
            {
                if (getEnv() == null)
                {
                    return 0;
                }
                return (secrets ? getEnv().secretVariables
                        : getEnv().variables).size();
            }

            @Override
            public int getColumnCount()
            {
                return 3;
            }

            @Override
            public String getColumnName(int column)
            {
                return switch (column)
                {
                    case 0 -> "Name";
                    case 1 -> "Json Value";
                    case 2 -> "Value";
                    default -> throw new IllegalArgumentException("Invalid column index: " + column);
                };
            }

            @Override
            public Class<?> getColumnClass(int columnIndex)
            {
                if (columnIndex == 1)
                {
                    return Boolean.class;
                }
                return String.class;
            }
        }
    }

    static class Variables
    {
        @JsonProperty
        List<Environment> environments = new ArrayList<>();

        @Override
        public Variables clone()
        {
            Variables clone = new Variables();
            clone.environments = new ArrayList<>(environments.stream()
                    .map(Environment::clone)
                    .toList());

            return clone;
        }
    }

    static class Environment
    {
        @JsonProperty
        String name = "";

        @JsonProperty
        List<Variable> variables = new ArrayList<>();

        @JsonProperty
        List<Variable> secretVariables = new ArrayList<>();

        @Override
        public Environment clone()
        {
            Environment clone = new Environment();
            clone.name = name;
            clone.variables = new ArrayList<>(variables.stream()
                    .map(Variable::clone)
                    .toList());
            clone.secretVariables = new ArrayList<>(secretVariables.stream()
                    .map(Variable::clone)
                    .toList());
            return clone;
        }

        @Override
        public String toString()
        {
            return name;
        }
    }

    static class Variable
    {
        @JsonProperty
        String name = "";

        @JsonProperty
        String value;

        @JsonProperty
        boolean isJson;

        @JsonIgnore
        static Object getValue(String name, String value, boolean isJson)
        {
            if (!isJson)
            {
                return value;
            }

            try
            {
                return CatalogsConfigurable.MAPPER.readValue(value, Object.class);
            }
            catch (JsonProcessingException e)
            {
                LOGGER.error("Error reading JSON value for variable: " + name, e);
                return null;
            }
        }

        @Override
        public Variable clone()
        {
            Variable clone = new Variable();
            clone.name = name;
            clone.value = value;
            clone.isJson = isJson;
            return clone;
        }
    }

}
