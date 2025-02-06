package com.queryeer.editor;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import org.apache.commons.lang3.StringUtils;
import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.kordamp.ikonli.swing.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queryeer.Constants;
import com.queryeer.api.action.IActionRegistry;
import com.queryeer.api.action.IActionRegistry.ActionScope;
import com.queryeer.api.action.IActionRegistry.KeyboardShortcut;
import com.queryeer.api.component.ADocumentListenerAdapter;
import com.queryeer.api.component.DialogUtils;
import com.queryeer.api.editor.IEditor;
import com.queryeer.api.editor.ITextEditorKit;
import com.queryeer.api.event.ExecuteQueryEvent.OutputType;
import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.extensions.engine.IQueryEngine;
import com.queryeer.api.extensions.engine.IQueryEngine.IState.MetaParameter;
import com.queryeer.api.service.IConfig;
import com.queryeer.api.service.IExpressionEvaluator;

/** Configurable of query shortcuts */
class TextEditorQueryShortcutConfigurable implements IConfigurable
{
    private static final Logger LOGGER = LoggerFactory.getLogger(TextEditorQueryShortcutConfigurable.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final String NAME = "com.queryeer.editor.TextEditorQueryShortcuts";
    private static final String SHORTCUT_PREFIX = "TextEditor.QueryShortcut.";
    static final int QUERY_SHORTCUT_COUNT = 12;
    //@formatter:off
    private static final KeyStroke[] DEFAULT_SHORTCUT_KEYSTROKES = new KeyStroke[]
            {
                KeyStroke.getKeyStroke(KeyEvent.VK_0, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
                KeyStroke.getKeyStroke(KeyEvent.VK_1, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
                KeyStroke.getKeyStroke(KeyEvent.VK_2, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
                KeyStroke.getKeyStroke(KeyEvent.VK_3, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
                KeyStroke.getKeyStroke(KeyEvent.VK_4, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
                KeyStroke.getKeyStroke(KeyEvent.VK_5, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
                KeyStroke.getKeyStroke(KeyEvent.VK_6, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
                KeyStroke.getKeyStroke(KeyEvent.VK_7, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
                KeyStroke.getKeyStroke(KeyEvent.VK_8, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
                KeyStroke.getKeyStroke(KeyEvent.VK_9, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
                KeyStroke.getKeyStroke(KeyEvent.VK_F1, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
                KeyStroke.getKeyStroke(KeyEvent.VK_F1, KeyEvent.ALT_DOWN_MASK)
            };
    //@formatter:on

    private final List<Consumer<Boolean>> dirtyStateConsumers = new ArrayList<>();
    private final IConfig config;
    private final IActionRegistry actionRegistry;
    private final IExpressionEvaluator expressionEvaluator;

    private ShortcutComponent component;
    private Shortcuts shortcuts;

    TextEditorQueryShortcutConfigurable(IConfig config, IActionRegistry actionRegistry, IExpressionEvaluator expressionEvaluator)
    {
        this.config = requireNonNull(config, "config");
        this.actionRegistry = requireNonNull(actionRegistry, "actionRegistry");
        this.expressionEvaluator = requireNonNull(expressionEvaluator, "expressionEvaluator");

        // Register 12 query shortcuts to registry
        for (int i = 0; i < QUERY_SHORTCUT_COUNT; i++)
        {
            KeyStroke keyStroke = DEFAULT_SHORTCUT_KEYSTROKES[i];
            actionRegistry.register(SHORTCUT_PREFIX + i, ActionScope.COMPONENT_FOCUS, List.of(new KeyboardShortcut(keyStroke)));
        }

        loadShortcuts();
    }

    @Override
    public Component getComponent()
    {
        if (component == null)
        {
            component = new ShortcutComponent();
            initShortcuts();
        }
        return component;
    }

    @Override
    public String getTitle()
    {
        return "Query Shortcuts";
    }

    @Override
    public String groupName()
    {
        return "Text Editor";
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
        Shortcuts shortcuts = component.getShortcuts();
        shortcuts.parseExpressions(expressionEvaluator, true);

        File file = config.getConfigFileName(NAME);
        try
        {
            MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValue(file, shortcuts);
        }
        catch (IOException e)
        {
            JOptionPane.showMessageDialog(component, "Error saving config, message: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        this.shortcuts = shortcuts;
        return true;
    }

    @Override
    public void revertChanges()
    {
        initShortcuts();
    }

    private void loadShortcuts()
    {
        File configFile = config.getConfigFileName(NAME);
        if (configFile.exists())
        {
            try
            {
                shortcuts = MAPPER.readValue(configFile, Shortcuts.class);
            }
            catch (IOException e)
            {
                LOGGER.error("Error loading shortcuts configfile", e);
            }
        }

        // Initalize an empty shortcuts if not loaded
        if (shortcuts == null)
        {
            shortcuts = new Shortcuts(new ArrayList<>());
        }
        shortcuts.parseExpressions(expressionEvaluator, false);
        // Init UI with loaded shortcuts and also adjust and faulty shortcut indices
        shortcuts.shortcuts.sort(Comparator.comparingInt(TextEditorQueryShortcut::getShortcutIndex));
    }

    private void initShortcuts()
    {
        for (int index = 0; index < QUERY_SHORTCUT_COUNT; index++)
        {
            if (index >= shortcuts.shortcuts.size())
            {
                // No more shortcuts to init
                break;
            }

            TextEditorQueryShortcut shortcut = shortcuts.shortcuts.get(index);
            // Set the index to correct any weirdness
            shortcut.setShortcutIndex(index);
            component.init(shortcut, index);
        }
    }

    private void notifyDirtyStateConsumers()
    {
        int size = dirtyStateConsumers.size();
        for (int i = size - 1; i >= 0; i--)
        {
            dirtyStateConsumers.get(i)
                    .accept(true);
        }
    }

    String getKeyboardShortcutName(int shortcutNumber)
    {
        if (shortcutNumber < 0
                || shortcutNumber >= QUERY_SHORTCUT_COUNT)
        {
            throw new IllegalArgumentException("Invalid shortcut number");
        }
        return SHORTCUT_PREFIX + shortcutNumber;
    }

    List<KeyboardShortcut> getKeyboardShortcut(int shortcutNumber)
    {
        if (shortcutNumber < 0
                || shortcutNumber >= QUERY_SHORTCUT_COUNT)
        {
            throw new IllegalArgumentException("Invalid shortcut number");
        }

        String key = SHORTCUT_PREFIX + shortcutNumber;
        return actionRegistry.getKeyboardShortcut(key);
    }

    TextEditorQueryShortcut getQueryShortcut(int index)
    {
        if (shortcuts == null
                || index < 0
                || index >= shortcuts.shortcuts.size())
        {
            return null;
        }

        return shortcuts.shortcuts.get(index);
    }

    private static String getAcceleratorText(KeyStroke accelerator)
    {
        String acceleratorText = "";
        if (accelerator != null)
        {
            int modifiers = accelerator.getModifiers();
            if (modifiers > 0)
            {
                acceleratorText = InputEvent.getModifiersExText(modifiers);
                acceleratorText += "+";
            }
            acceleratorText += KeyEvent.getKeyText(accelerator.getKeyCode());
        }
        return acceleratorText;
    }

    private record Shortcuts(List<TextEditorQueryShortcut> shortcuts)
    {
        void parseExpressions(IExpressionEvaluator expressionEvaluator, boolean interactive)
        {
            int size = shortcuts.size();
            for (int i = 0; i < size; i++)
            {
                TextEditorQueryShortcut shortcut = shortcuts.get(i);
                int size2 = shortcut.getOverrides()
                        .size();
                for (int j = 0; j < size2; j++)
                {
                    TextEditorQueryShortcutOverride override = shortcut.getOverrides()
                            .get(j);
                    if (!isBlank(override.getRule()))
                    {
                        try
                        {
                            override.setRuleExpression(expressionEvaluator.parse(override.getRule()));
                        }
                        catch (Exception e)
                        {
                            String message = "<html>Error parsing rule [" + abbreviate(override.getRule(),
                                    30) + "] for shortcut override: <b>" + getAcceleratorText(DEFAULT_SHORTCUT_KEYSTROKES[i]) + "</b>, error: " + e.getMessage();
                            if (interactive)
                            {
                                Window activeWindow = javax.swing.FocusManager.getCurrentManager()
                                        .getActiveWindow();
                                JOptionPane.showMessageDialog(activeWindow, message, "Error", JOptionPane.ERROR_MESSAGE);
                            }
                            else
                            {
                                LOGGER.error("Error parsing text editor shortcuts: {}", message);
                            }
                        }
                    }
                }
            }
        }
    }

    private static TextEditorQueryShortcutOverride rule(String rule)
    {
        TextEditorQueryShortcutOverride override = new TextEditorQueryShortcutOverride();
        override.setRule(rule);
        return override;
    }

    private class ShortcutComponent extends JPanel
    {
        private List<ShortcutPanel> shortcutPanels = new ArrayList<>(QUERY_SHORTCUT_COUNT);

        ShortcutComponent()
        {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

            // Create panels for each shortcut index
            for (int i = 0; i < QUERY_SHORTCUT_COUNT; i++)
            {
                ShortcutPanel shortcutPanel = new ShortcutPanel(i);
                shortcutPanels.add(shortcutPanel);
                add(shortcutPanel);
            }
        }

        /** Construct shortcuts from UI. */
        Shortcuts getShortcuts()
        {
            List<TextEditorQueryShortcut> shortcuts = new ArrayList<>(QUERY_SHORTCUT_COUNT);
            for (int i = 0; i < QUERY_SHORTCUT_COUNT; i++)
            {
                TextEditorQueryShortcut shortcut = shortcutPanels.get(i)
                        .getShortcut();
                shortcut.setShortcutIndex(i);
                shortcuts.add(shortcut);
            }

            return new Shortcuts(shortcuts);
        }

        void init(TextEditorQueryShortcut shortcut, int index)
        {
            shortcutPanels.get(index)
                    .init(shortcut.clone());
        }
    }

    private class ShortcutPanel extends JPanel
    {
        private static final ITextEditorKit SQLKIT = new ITextEditorKit()
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

            @Override
            public int getColumns()
            {
                return 10;
            }
        };
        private DefaultListModel<TextEditorQueryShortcutOverride> overridesModel;
        private TextEditor queryField;
        private JComboBox<OutputType> outputCombo;
        private TextEditorQueryShortcut shortcut = new TextEditorQueryShortcut();
        private boolean notify = true;

        void init(TextEditorQueryShortcut shortcut)
        {
            notify = false;
            this.shortcut = shortcut;
            queryField.setValue(shortcut.getQuery());
            outputCombo.setSelectedItem(shortcut.getOutput());
            overridesModel.clear();
            overridesModel.addAll(shortcut.getOverrides());
            notify = true;
        }

        TextEditorQueryShortcut getShortcut()
        {
            List<TextEditorQueryShortcutOverride> overrides = new ArrayList<>();
            int size = overridesModel.size();
            for (int i = 0; i < size; i++)
            {
                overrides.add(overridesModel.elementAt(i));
            }
            shortcut.setOverrides(overrides);
            return shortcut;
        }

        // CSOFF
        ShortcutPanel(int index)
        {
            setLayout(new GridBagLayout());

            String shortcutText = getAcceleratorText(DEFAULT_SHORTCUT_KEYSTROKES[index]);
            // Query label and text field
            JLabel queryLabel = new JLabel(shortcutText, FontIcon.of(FontAwesome.INFO), SwingConstants.LEADING);
            addQueryInformationTooltip(queryLabel, shortcutText);
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.anchor = GridBagConstraints.NORTH;
            add(queryLabel, gbc);

            queryField = new TextEditor(SQLKIT);
            queryField.addPropertyChangeListener(new PropertyChangeListener()
            {

                @Override
                public void propertyChange(PropertyChangeEvent evt)
                {
                    if (IEditor.VALUE_CHANGED.equalsIgnoreCase(evt.getPropertyName()))
                    {
                        shortcut.setQuery(queryField.getValue(true));
                        if (notify)
                        {
                            notifyDirtyStateConsumers();
                        }
                    }
                }
            });
            gbc = new GridBagConstraints();
            gbc.weightx = 0.6d;
            gbc.weighty = 1.0d;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.gridx = 0;
            gbc.gridy = 1;
            // gbc.gridheight = 2;
            gbc.insets = new Insets(2, 2, 2, 2);
            add(queryField.getComponent(), gbc);

            JLabel outputType = new JLabel("Output Type:");
            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 2;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.WEST;
            add(outputType, gbc);

            outputCombo = new JComboBox<>(Arrays.stream(OutputType.values())
                    .filter(OutputType::isInteractive)
                    .toArray(OutputType[]::new));
            outputCombo.addActionListener(l ->
            {
                shortcut.setOutput((OutputType) outputCombo.getSelectedItem());
                if (notify)
                {
                    notifyDirtyStateConsumers();
                }
            });
            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 2;
            gbc.insets = new Insets(2, 2, 2, 2);
            gbc.anchor = GridBagConstraints.EAST;
            add(outputCombo, gbc);

            JLabel overridesLabel = new JLabel("Overrides:");
            gbc = new GridBagConstraints();
            gbc.gridx = 1;
            gbc.gridy = 0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            add(overridesLabel, gbc);

            JList<TextEditorQueryShortcutOverride> overrides = new JList<TextEditorQueryShortcutOverride>()
            {
                @Override
                public String getToolTipText(MouseEvent e)
                {
                    int index = locationToIndex(e.getPoint());
                    if (index > -1)
                    {
                        return getModel().getElementAt(index)
                                .toString();
                    }
                    return null;
                }
            };
            overrides.setCellRenderer(new DefaultListCellRenderer()
            {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
                {
                    JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                    TextEditorQueryShortcutOverride override = (TextEditorQueryShortcutOverride) value;

                    String text = StringUtils.isBlank(override.getQueryEngineClassName()) ? ""
                            : (override.getQueryEngineClassName() + ": ") + override.toString();

                    label.setText(text);

                    return label;
                }
            });

            TextEditorQueryShortcutOverride prototype = new TextEditorQueryShortcutOverride();
            prototype.setRule("some rule");
            prototype.setQueryEngineClassName("Some class name");
            overrides.setPrototypeCellValue(prototype);
            overrides.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            overridesModel = new DefaultListModel<TextEditorQueryShortcutOverride>();
            overrides.setModel(overridesModel);
            gbc = new GridBagConstraints();
            gbc.gridx = 1;
            gbc.gridy = 1;
            gbc.weightx = 0.4d;
            gbc.fill = GridBagConstraints.BOTH;
            add(new JScrollPane(overrides), gbc);

            JButton addOverrideButton = new JButton("+");
            gbc = new GridBagConstraints();
            gbc.gridx = 1;
            gbc.gridy = 2;
            gbc.anchor = GridBagConstraints.WEST;
            add(addOverrideButton, gbc);

            JButton deleteOverrideButton = new JButton("-");
            gbc = new GridBagConstraints();
            gbc.gridx = 1;
            gbc.gridy = 2;
            gbc.anchor = GridBagConstraints.SOUTH;
            add(deleteOverrideButton, gbc);

            JButton editOverrideButton = new JButton("...");
            gbc = new GridBagConstraints();
            gbc.gridx = 1;
            gbc.gridy = 2;
            gbc.anchor = GridBagConstraints.EAST;
            add(editOverrideButton, gbc);

            addOverrideButton.addActionListener(l ->
            {
                overridesModel.addElement(rule("Rule " + overridesModel.size()));
                if (notify)
                {
                    notifyDirtyStateConsumers();
                }
            });
            deleteOverrideButton.addActionListener(l ->
            {
                if (overrides.getSelectedValue() != null)
                {
                    overridesModel.removeElement(overrides.getSelectedValue());
                    if (notify)
                    {
                        notifyDirtyStateConsumers();
                    }
                }
            });
            editOverrideButton.addActionListener(l ->
            {
                if (overrides.getSelectedValue() != null)
                {
                    openOverrideDialog(overrides.getSelectedValue(), shortcutText);
                }
            });
            overrides.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent e)
                {
                    if (e.getClickCount() == 2
                            && SwingUtilities.isLeftMouseButton(e))
                    {
                        int index = overrides.locationToIndex(e.getPoint());
                        if (index >= 0)
                        {
                            openOverrideDialog(overridesModel.getElementAt(index), shortcutText);
                        }
                    }
                };
            });
        }
    }
    // CSON

    private void addQueryInformationTooltip(JLabel queryInformation, String shortcutText)
    {
        //@formatter:off
        String message = "<html>"
                + "Query that will be executed when trigged by keyboard shortcut: "
                + "<b/>" + shortcutText + "</b>"
                + "<br/>"
                + "Selected text in editor is available as a placeholder <b>${selectedText}</b>"
                + "</html>";
        //@formatter:on

        queryInformation.setToolTipText(message);
    }

    // CSOFF
    private void openOverrideDialog(TextEditorQueryShortcutOverride override, String shortcutText)
    {
        DialogUtils.ADialog dialog = new DialogUtils.ADialog();
        dialog.setModal(true);
        dialog.setTitle("Override: " + override.getRule());
        dialog.setSize(Constants.DEFAULT_DIALOG_SIZE);
        dialog.setLayout(new GridBagLayout());
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        // Rule input
        JLabel ruleLabel = new JLabel("Rule:", FontIcon.of(FontAwesome.INFO), SwingConstants.LEADING);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.gridx = 0;
        gbc.gridy = 0;
        dialog.add(ruleLabel, gbc);

        String message = "<html>";
        //@formatter:off
        message += "<h4>Payloadbuilder expression</h4>"
                + "First override whos rule evaluates to true will be used.<br/>"
                + "Parameters are accessiable via <b>@param</b>-notion:<br/>";
        //@formatter:on

        for (IQueryEngine engine : config.getQueryEngines())
        {
            List<MetaParameter> meta = engine.createState()
                    .getMetaParameters(true);
            if (meta.isEmpty())
            {
                continue;
            }
            message += "<h4>Engine: " + engine.getTitle() + "</h4>";
            message += "<ul>";
            for (MetaParameter entry : meta)
            {
                message += "<li><b>" + entry.name() + "</b> - " + entry.description() + "</li>";
            }
            message += "</ul>";
        }
        message += "</html>";
        ruleLabel.setToolTipText(message);

        JTextArea ruleField = new JTextArea(override.getRule());
        ruleField.setRows(3);
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0d;
        gbc.gridwidth = 2;
        ruleField.getDocument()
                .addDocumentListener(new ADocumentListenerAdapter()
                {
                    @Override
                    protected void update()
                    {
                        override.setRule(ruleField.getText());
                        notifyDirtyStateConsumers();
                    }
                });
        JScrollPane scroll = new JScrollPane(ruleField);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        scroll.setWheelScrollingEnabled(true);
        dialog.add(scroll, gbc);

        // Query Engine Class Name input
        JLabel queryEngineLabel = new JLabel("Query Engine:");
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.WEST;
        dialog.add(queryEngineLabel, gbc);

        List<IQueryEngine> engines = new ArrayList<>();
        engines.addAll(config.getQueryEngines());

        IQueryEngine selectedEngine = StringUtils.isEmpty(override.getQueryEngineClassName()) ? null
                : engines.stream()
                        .filter(e -> e != null
                                && e.getClass()
                                        .getSimpleName()
                                        .equalsIgnoreCase(override.getQueryEngineClassName()))
                        .findAny()
                        .orElse(null);

        JComboBox<Object> queryEngineCombo = new JComboBox<Object>(engines.toArray(new IQueryEngine[0]));
        ((DefaultComboBoxModel<Object>) queryEngineCombo.getModel()).insertElementAt("None", 0);
        queryEngineCombo.setRenderer(new DefaultListCellRenderer()
        {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
            {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                setText(null);
                setIcon(null);
                if (value instanceof IQueryEngine engine)
                {
                    setText(engine.getTitle());
                    setIcon(engine.getIcon());
                }
                else
                {
                    setText("None");
                }

                return this;
            }
        });
        queryEngineCombo.setSelectedItem(selectedEngine);
        queryEngineCombo.addActionListener(l ->
        {
            if (queryEngineCombo.getSelectedItem() instanceof IQueryEngine engine)
            {
                override.setQueryEngineClassName(engine.getClass()
                        .getSimpleName());
            }
            else
            {
                override.setQueryEngineClassName(null);
            }
            notifyDirtyStateConsumers();
        });

        gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 1.0d;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        dialog.add(queryEngineCombo, gbc);

        // Query input
        JLabel queryLabel = new JLabel("Query:", FontIcon.of(FontAwesome.INFO), SwingConstants.LEADING);
        addQueryInformationTooltip(queryLabel, shortcutText);
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.anchor = GridBagConstraints.WEST;
        dialog.add(queryLabel, gbc);

        TextEditor queryField = new TextEditor(ShortcutPanel.SQLKIT);
        queryField.setValue(override.getQuery());
        queryField.addPropertyChangeListener(new PropertyChangeListener()
        {
            @Override
            public void propertyChange(PropertyChangeEvent evt)
            {
                if (IEditor.VALUE_CHANGED.equalsIgnoreCase(evt.getPropertyName()))
                {
                    override.setQuery(queryField.getValue(true));
                    notifyDirtyStateConsumers();
                }
            }
        });

        gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.weightx = 1.0d;
        gbc.weighty = 1.0d;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        dialog.add(queryField.getComponent(), gbc);

        // Output type combo box
        JLabel outputLabel = new JLabel("Output Type:");
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.weightx = 1.0d;
        gbc.anchor = GridBagConstraints.WEST;
        dialog.add(outputLabel, gbc);

        JComboBox<OutputType> outputCombo = new JComboBox<>(Arrays.stream(OutputType.values())
                .filter(OutputType::isInteractive)
                .toArray(OutputType[]::new));
        outputCombo.setSelectedItem(override.getOutput());
        outputCombo.addActionListener(l ->
        {
            override.setOutput((OutputType) outputCombo.getSelectedItem());
            notifyDirtyStateConsumers();
        });
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.gridx = 0;
        gbc.gridy = 7;
        gbc.weightx = 1.0d;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        dialog.add(outputCombo, gbc);
        dialog.setVisible(true);
    }
    // CSON
}
