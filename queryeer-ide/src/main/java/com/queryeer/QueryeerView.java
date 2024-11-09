package com.queryeer;

import static java.util.Objects.requireNonNull;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;

import org.apache.commons.lang3.BooleanUtils;
import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.kordamp.ikonli.swing.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.queryeer.QueryeerController.ViewAction;
import com.queryeer.api.component.AnimatedIcon;
import com.queryeer.api.event.Subscribe;
import com.queryeer.api.extensions.engine.IQueryEngine;
import com.queryeer.api.extensions.output.IOutputExtension;
import com.queryeer.api.extensions.output.IOutputFormatExtension;
import com.queryeer.api.service.IEventBus;
import com.queryeer.domain.Caret;
import com.queryeer.event.CaretChangedEvent;

/** Main view */
class QueryeerView extends JFrame
{
    private static final Logger LOGGER = LoggerFactory.getLogger(QueryeerView.class);

    private static final String TOGGLE_RESULT = "toggleResult";
    private static final String TOGGLE_QUICK_PROPERTIES = "toggleQuickProperties";
    private static final String NEW_QUERY = "NewQuery";
    private static final String EXECUTE = "Execute";
    private static final String STOP = "Stop";
    private static final Icon TASKS_ICON = FontIcon.of(FontAwesome.TASKS);
    private static final Icon SPINNER = AnimatedIcon.createSmallSpinner();

    private final JPanel topPanel;
    private final InputMap inputMap;

    private final JSplitPane splitPane;
    private final JPanel panelQueryEngineProperties;
    private final JPanel panelStatus;
    private final JLabel labelMemory;
    private final JLabel labelCaret;
    private final JLabel labelVersion;
    private final JLabel labelTasks;
    private final JLabel labelTasksSpinner;

    private final JMenu editMenu;
    private final JMenuItem openItem;
    private final JMenuItem saveItem;
    private final JMenuItem saveAsItem;
    private final JMenuItem exitItem;
    private final JMenu recentFiles;

    private final JToolBar toolBar;
    private final JComboBox<IOutputExtension> comboOutput;
    private final JComboBox<IOutputFormatExtension> comboFormat;

    private boolean suppressChangeEvents = false;

    private Consumer<String> openRecentFileConsumer;
    private Consumer<IQueryEngine> newQueryConsumer;
    private Consumer<ViewAction> actionHandler;
    private boolean quickPropertiesCollapsed;
    private int prevCatalogsDividerLocation;
    private final QueryFileTabbedPane tabbedPane;
    private final TasksDialog tasksDialog;
    private final LogsDialog logsDialog;

    private List<AbstractButton> editorToolbarActions = new ArrayList<>();
    private List<JMenuItem> editorMenuActions = new ArrayList<>();

    // CSOFF
    QueryeerView(QueryFileTabbedPane tabbedPane, IEventBus eventBus, List<IOutputExtension> outputExtensions, List<IOutputFormatExtension> outputFormatExtensions, List<IQueryEngine> queryEngines)
    // CSON
    {
        setLocationRelativeTo(null);
        getContentPane().setLayout(new BorderLayout(0, 0));

        this.tabbedPane = requireNonNull(tabbedPane, "tabbedPane");

        List<IOutputExtension> actualOutputExtensions = new ArrayList<>(outputExtensions);
        List<IOutputFormatExtension> actualOutputFormatExtensions = new ArrayList<>(outputFormatExtensions);

        actualOutputExtensions.sort(Comparator.comparingInt(IOutputExtension::order));
        actualOutputFormatExtensions.sort(Comparator.comparingInt(IOutputFormatExtension::order));

        // CSOFF
        panelStatus = new JPanel();
        panelStatus.setPreferredSize(new Dimension(10, 20));
        getContentPane().add(panelStatus, BorderLayout.SOUTH);
        panelStatus.setLayout(new FlowLayout(FlowLayout.RIGHT, 10, 0));

        labelMemory = new JLabel("", SwingConstants.CENTER);
        labelMemory.setPreferredSize(new Dimension(100, 20));
        labelMemory.setBorder(new EtchedBorder(EtchedBorder.LOWERED));
        labelMemory.setToolTipText("Memory (Total / Used)");
        labelMemory.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (e.getClickCount() == 2)
                {
                    System.gc();
                }
            }
        });

        labelCaret = new JLabel("", SwingConstants.CENTER);
        labelCaret.setBorder(new EtchedBorder(EtchedBorder.LOWERED));
        labelCaret.setPreferredSize(new Dimension(100, 20));
        labelCaret.setToolTipText("Caret Position (Line, Column, Offset)");
        labelVersion = new JLabel();
        labelTasks = new JLabel();
        labelTasks.setMaximumSize(new Dimension(16, 16));
        labelTasks.setIcon(TASKS_ICON);

        JLabel labelLogs = new JLabel(FontIcon.of(FontAwesome.FILE_TEXT_O));
        labelLogs.setBorder(new EtchedBorder(EtchedBorder.LOWERED));
        labelLogs.setToolTipText("Logs");
        labelLogs.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (logsDialog.getExtendedState() == JFrame.ICONIFIED)
                {
                    logsDialog.setExtendedState(JFrame.NORMAL);
                }
                else if (logsDialog.isShowing())
                {
                    logsDialog.toFront();
                }
                else
                {
                    logsDialog.setVisible(true);
                }
            }
        });

        // CSON
        JPanel panelTasks = new JPanel();
        panelTasks.setLayout(new BoxLayout(panelTasks, BoxLayout.X_AXIS));
        panelTasks.setToolTipText("Tasks");
        panelTasks.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (tasksDialog.getExtendedState() == JFrame.ICONIFIED)
                {
                    tasksDialog.setExtendedState(JFrame.NORMAL);
                }
                else if (tasksDialog.isShowing())
                {
                    tasksDialog.toFront();
                }
                else
                {
                    tasksDialog.setVisible(true);
                }
            }
        });
        panelTasks.setBorder(new EtchedBorder(EtchedBorder.LOWERED));
        panelTasks.setMaximumSize(new Dimension(34, 16));
        panelTasks.add(labelTasks);

        labelTasksSpinner = new JLabel(SPINNER);
        labelTasksSpinner.setVisible(false);
        labelTasksSpinner.setMaximumSize(new Dimension(16, 16));

        panelTasks.add(labelTasksSpinner);

        panelStatus.add(labelLogs);
        panelStatus.add(panelTasks);
        panelStatus.add(labelMemory);
        panelStatus.add(labelCaret);
        panelStatus.add(labelVersion);

        topPanel = new JPanel();
        topPanel.setLayout(new BorderLayout());
        getContentPane().add(topPanel, BorderLayout.NORTH);

        JMenuBar menuBar = new JMenuBar();
        topPanel.add(menuBar, BorderLayout.NORTH);

        openItem = new JMenuItem(openAction);
        openItem.setText("Open");
        openItem.setAccelerator(KeyStroke.getKeyStroke('O', Toolkit.getDefaultToolkit()
                .getMenuShortcutKeyMaskEx()));
        saveItem = new JMenuItem(saveAction);
        saveItem.setText("Save");
        saveItem.setAccelerator(KeyStroke.getKeyStroke('S', Toolkit.getDefaultToolkit()
                .getMenuShortcutKeyMaskEx()));
        saveAsItem = new JMenuItem(saveAsAction);
        saveAsItem.setText("Save As ...");
        recentFiles = new JMenu("Recent Files");

        exitItem = new JMenuItem(exitAction);
        exitItem.setText("Exit");

        JMenu fileMenu = new JMenu("File");
        fileMenu.add(openItem);
        fileMenu.add(saveItem);
        fileMenu.add(saveAsItem);
        fileMenu.add(new JSeparator());
        fileMenu.add(recentFiles);
        fileMenu.add(new JSeparator());
        fileMenu.add(exitItem);

        menuBar.add(fileMenu);

        JMenu toolsMenu = new JMenu("Tools");
        toolsMenu.add(new JMenuItem(optionsAction))
                .setText("Options ...");

        JMenu helpMenu = new JMenu("Help");
        helpMenu.add(new JMenuItem(new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                actionHandler.accept(ViewAction.ABOUT);
            }
        }))
                .setText("About Queryeer IDE");

        editMenu = new JMenu("Edit");
        menuBar.add(editMenu);
        menuBar.add(toolsMenu);
        menuBar.add(helpMenu);

        toolBar = new JToolBar();
        toolBar.setRollover(true);
        toolBar.setFloatable(false);
        topPanel.add(toolBar, BorderLayout.SOUTH);

        // CSOFF
        KeyStroke executeKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_E, Toolkit.getDefaultToolkit()
                .getMenuShortcutKeyMaskEx());
        KeyStroke stopKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        KeyStroke newQueryKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_N, Toolkit.getDefaultToolkit()
                .getMenuShortcutKeyMaskEx());
        KeyStroke toggleResultKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_R, Toolkit.getDefaultToolkit()
                .getMenuShortcutKeyMaskEx());
        // CSON

        inputMap = topPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        inputMap.put(executeKeyStroke, EXECUTE);
        inputMap.put(stopKeyStroke, STOP);
        inputMap.put(newQueryKeyStroke, NEW_QUERY);
        inputMap.put(toggleResultKeyStroke, TOGGLE_RESULT);
        topPanel.getActionMap()
                .put(EXECUTE, executeAction);
        topPanel.getActionMap()
                .put(STOP, cancelAction);
        topPanel.getActionMap()
                .put(NEW_QUERY, newQueryAction);
        topPanel.getActionMap()
                .put(TOGGLE_RESULT, toggleResultAction);

        JButton newQueryButton = new JButton();
        newQueryButton.setIcon(Constants.FILE_TEXT_O);
        newQueryButton.setText("New Query");
        newQueryButton.setToolTipText("Open New Query Window (" + getAcceleratorText(newQueryKeyStroke) + ")");

        final JPopupMenu queryEnginesPopup = new JPopupMenu();
        for (IQueryEngine qe : queryEngines.stream()
                .sorted(Comparator.comparingInt(IQueryEngine::order))
                .toList())
        {
            final IQueryEngine queryEngine = qe;
            queryEnginesPopup.add(new JMenuItem(new AbstractAction(queryEngine.getTitle(), queryEngine.getIcon())
            {
                @Override
                public void actionPerformed(ActionEvent e)
                {
                    if (newQueryConsumer != null)
                    {
                        newQueryConsumer.accept(queryEngine);
                    }
                }
            }));
        }

        newQueryButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent ev)
            {
                queryEnginesPopup.show(newQueryButton, newQueryButton.getBounds().x, newQueryButton.getBounds().y + newQueryButton.getBounds().height);
            }
        });

        JButton executeButton = new JButton(executeAction);
        executeButton.setText("Execute");
        executeButton.setToolTipText("Execute Query (" + getAcceleratorText(executeKeyStroke) + ")");

        toolBar.add(openAction)
                .setToolTipText("Open File (" + getAcceleratorText(openItem.getAccelerator()) + ")");
        toolBar.add(saveAction)
                .setToolTipText("Save Current File (" + getAcceleratorText(saveItem.getAccelerator()) + ")");
        toolBar.addSeparator();
        toolBar.add(newQueryButton);
        toolBar.add(executeButton);
        toolBar.add(cancelAction)
                .setToolTipText("Cancel Query (" + getAcceleratorText(stopKeyStroke) + ")");
        toolBar.addSeparator();
        toolBar.add(toggleQuickPropertiesAction)
                .setToolTipText("Toggle Quick Properties Panel");
        toolBar.add(toggleResultAction)
                .setToolTipText("Toggle Result Panel (" + getAcceleratorText(toggleResultKeyStroke) + ")");

        comboOutput = new JComboBox<>();
        comboOutput.setRenderer(new DefaultListCellRenderer()
        {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
            {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                if (value != null)
                {
                    IOutputExtension output = (IOutputExtension) value;
                    KeyStroke keyStroke = output.getKeyStroke();
                    setText(output.getTitle() + (keyStroke != null ? (" (" + getAcceleratorText(keyStroke) + ")")
                            : ""));
                }
                return this;
            }
        });
        // CSOFF
        comboOutput.setMaximumSize(new Dimension(130, 20));
        // CSON

        DefaultComboBoxModel<IOutputExtension> outputComboModel = (DefaultComboBoxModel<IOutputExtension>) comboOutput.getModel();
        for (IOutputExtension outputExtension : actualOutputExtensions)
        {
            if (!outputExtension.isAutoPopulated()
                    && outputExtension.isAutoAdded())
            {
                outputComboModel.addElement(outputExtension);
            }
        }

        comboFormat = new JComboBox<>();
        comboFormat.setRenderer(new DefaultListCellRenderer()
        {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
            {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                if (value != null)
                {
                    IOutputFormatExtension output = (IOutputFormatExtension) value;
                    setText(output.getTitle());
                }
                return this;
            }
        });
        // CSOFF
        comboFormat.setMaximumSize(new Dimension(100, 20));
        // CSON

        DefaultComboBoxModel<IOutputFormatExtension> formatComboModel = (DefaultComboBoxModel<IOutputFormatExtension>) comboFormat.getModel();
        for (IOutputFormatExtension outputFormatExtension : actualOutputFormatExtensions)
        {
            formatComboModel.addElement(outputFormatExtension);
        }

        comboFormat.addActionListener(l ->
        {
            if (suppressChangeEvents)
            {
                return;
            }
            actionHandler.accept(ViewAction.FORMAT_CHANGED);
        });

        toolBar.addSeparator();
        toolBar.add(new JLabel("Output "));
        toolBar.add(comboOutput);
        toolBar.addSeparator();
        toolBar.add(new JLabel("Format "));
        toolBar.add(comboFormat);

        splitPane = new JSplitPane();
        splitPane.setDividerSize(3);
        getContentPane().add(splitPane, BorderLayout.CENTER);

        splitPane.setRightComponent(tabbedPane);

        panelQueryEngineProperties = new JPanel();
        panelQueryEngineProperties.setLayout(new BorderLayout());
        panelQueryEngineProperties.setBorder(new EmptyBorder(2, 2, 2, 2));

        splitPane.setLeftComponent(new JScrollPane(panelQueryEngineProperties));

        setIconImages(Constants.APPLICATION_ICONS);

        // Switch formats upon changing output
        comboOutput.addActionListener(l ->
        {
            if (suppressChangeEvents)
            {
                return;
            }

            IOutputExtension extension = (IOutputExtension) comboOutput.getSelectedItem();
            comboFormat.setEnabled(extension.supportsOutputFormats());
            actionHandler.accept(ViewAction.OUTPUT_CHANGED);
        });

        IOutputExtension extension = (IOutputExtension) comboOutput.getSelectedItem();
        comboFormat.setEnabled(extension.supportsOutputFormats());

        addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
                exitAction.actionPerformed(null);
            }
        });

        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addPropertyChangeListener(new PropertyChangeListener()
                {
                    @Override
                    public void propertyChange(PropertyChangeEvent evt)
                    {
                        if ("focusOwner".equalsIgnoreCase(evt.getPropertyName()))
                        {
                            // boolean processed = false;
                            if (evt.getNewValue() instanceof JComponent comp)
                            {
                                while (comp != null)
                                {
                                    @SuppressWarnings("unchecked")
                                    List<Action> actions = (List<Action>) comp.getClientProperty(com.queryeer.api.action.Constants.QUERYEER_ACTIONS);
                                    if (actions != null)
                                    {
                                        populateActions(actions);
                                        // processed = true;
                                        break;
                                    }

                                    Container parent = comp.getParent();
                                    comp = parent instanceof JComponent ? (JComponent) parent
                                            : null;
                                }
                            }

                            // if (!processed)
                            // {
                            // populateActions(emptyList());
                            // }
                        }
                    }
                });

        tasksDialog = new TasksDialog(this, eventBus, running -> labelTasksSpinner.setVisible(running));
        logsDialog = new LogsDialog(this);

        eventBus.register(this);
        bindOutputExtensions(outputExtensions);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        // CSOFF
        setPreferredSize(new Dimension(1600, 800));
        // CSON
        setLocationByPlatform(true);
        pack();
    }

    private void bindOutputExtensions(List<IOutputExtension> outputExtensions)
    {
        InputMap inputMap = topPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        for (IOutputExtension outputExtension : outputExtensions)
        {
            if (!outputExtension.isAutoPopulated()
                    && outputExtension.isAutoAdded())
            {
                inputMap.put(outputExtension.getKeyStroke(), outputExtension);
                topPanel.getActionMap()
                        .put(outputExtension, new AbstractAction()
                        {
                            @Override
                            public void actionPerformed(ActionEvent e)
                            {
                                comboOutput.setSelectedItem(outputExtension);
                            }
                        });
            }
        }
    }

    @Subscribe
    private void carretChanged(CaretChangedEvent event)
    {
        Caret caret = event.getCaret();
        labelCaret.setText(String.format("%d : %d : %d", caret.getLineNumber(), caret.getOffset(), caret.getPosition()));
    }

    private String getAcceleratorText(KeyStroke accelerator)
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

    private final Action optionsAction = new AbstractAction("OPTIONS", Constants.COG)
    {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            actionHandler.accept(ViewAction.OPTIONS);
        }
    };

    private final Action openAction = new AbstractAction("OPEN", Constants.FOLDER_OPEN_O)
    {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            actionHandler.accept(ViewAction.OPEN);
        }
    };

    private final Action saveAction = new AbstractAction("SAVE", Constants.SAVE)
    {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            actionHandler.accept(ViewAction.SAVE);
        }
    };

    private final Action saveAsAction = new AbstractAction()
    {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            actionHandler.accept(ViewAction.SAVEAS);
        }
    };

    private final Action exitAction = new AbstractAction()
    {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            actionHandler.accept(ViewAction.EXIT);
        }
    };

    private final Action executeAction = new AbstractAction(EXECUTE, Constants.PLAY_CIRCLE)
    {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            actionHandler.accept(ViewAction.EXECUTE);
        }
    };

    private final Action cancelAction = new AbstractAction(EXECUTE, Constants.STOP_CIRCLE)
    {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            actionHandler.accept(ViewAction.CANCEL);
        }
    };

    private final Action newQueryAction = new AbstractAction(NEW_QUERY, Constants.FILE_TEXT_O)
    {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            actionHandler.accept(ViewAction.NEWQUERY);
        }
    };

    private final Action toggleResultAction = new AbstractAction(TOGGLE_RESULT, Constants.ARROWS_V)
    {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            actionHandler.accept(ViewAction.TOGGLE_RESULT);
        }
    };

    private final Action toggleQuickPropertiesAction = new AbstractAction(TOGGLE_QUICK_PROPERTIES, Constants.ARROWS_H)
    {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            // Expanded
            if (!quickPropertiesCollapsed)
            {
                prevCatalogsDividerLocation = splitPane.getDividerLocation();
                splitPane.setDividerLocation(0.0d);
                quickPropertiesCollapsed = true;
            }
            else
            {
                splitPane.setDividerLocation(prevCatalogsDividerLocation);
                quickPropertiesCollapsed = false;
            }
        }
    };

    private final Action recentFileAction = new AbstractAction()
    {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            JMenuItem item = (JMenuItem) e.getSource();
            String file = item.getText();
            if (openRecentFileConsumer != null)
            {
                openRecentFileConsumer.accept(file);
            }
        }
    };

    JLabel getMemoryLabel()
    {
        return labelMemory;
    }

    JLabel getLabelVersion()
    {
        return labelVersion;
    }

    JComboBox<IOutputExtension> getOutputCombo()
    {
        return comboOutput;
    }

    JComboBox<IOutputFormatExtension> getFormatCombo()
    {
        return comboFormat;
    }

    /** Updates combo boxes etc. to reflect the provided query file view */
    void updateState(QueryFileView fileView)
    {
        QueryFileModel file = fileView.getModel();

        suppressChangeEvents = true;
        try
        {
            comboOutput.setSelectedItem(file.getOutputExtension());
            comboFormat.setSelectedItem(file.getOutputFormat());
            setQueryEngineProperties(fileView.getModel()
                    .getQueryEngine());
            file.getQueryEngine()
                    .focus(fileView);
            fileView.getEditor()
                    .focused();

            // populateEditorActions(file.getEditor());
        }
        finally
        {
            suppressChangeEvents = false;
        }
    }

    private void populateActions(List<Action> actions)
    {
        for (AbstractButton button : editorToolbarActions)
        {
            KeyStroke keyStroke = (KeyStroke) button.getAction()
                    .getValue(Action.ACCELERATOR_KEY);

            // Unbind old accelerators
            if (keyStroke != null)
            {
                String actionCommand = (String) button.getAction()
                        .getValue(Action.ACTION_COMMAND_KEY);
                inputMap.remove(keyStroke);
                topPanel.getActionMap()
                        .remove(actionCommand);
            }

            toolBar.remove(button);
        }
        for (JMenuItem menuItem : editorMenuActions)
        {
            KeyStroke keyStroke = (KeyStroke) menuItem.getAction()
                    .getValue(Action.ACCELERATOR_KEY);

            // Unbind old accelerators
            if (keyStroke != null)
            {
                String actionCommand = (String) menuItem.getAction()
                        .getValue(Action.ACTION_COMMAND_KEY);
                inputMap.remove(keyStroke);
                topPanel.getActionMap()
                        .remove(actionCommand);
            }

            Container parent = menuItem.getParent();

            // Parent is a popup menu and not a JMenu for some reason
            if (parent instanceof JPopupMenu)
            {
                ((JPopupMenu) parent).remove(menuItem);
            }
        }

        editorToolbarActions.clear();
        for (Action action : actions)
        {
            Integer order = (Integer) action.getValue(com.queryeer.api.action.Constants.ACTION_ORDER);
            if (order == null
                    || order > toolBar.getComponentCount())
            {
                order = -1;
            }
            KeyStroke keyStroke = (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);

            if (keyStroke != null)
            {
                String actionCommand = (String) action.getValue(Action.ACTION_COMMAND_KEY);
                inputMap.put(keyStroke, actionCommand);
                topPanel.getActionMap()
                        .put(actionCommand, action);
            }

            String shortDescription = (String) action.getValue(Action.SHORT_DESCRIPTION);

            Boolean showInToolBar = (Boolean) action.getValue(com.queryeer.api.action.Constants.ACTION_SHOW_IN_TOOLBAR);
            Boolean showInMenu = (Boolean) action.getValue(com.queryeer.api.action.Constants.ACTION_SHOW_IN_MENU);
            if (BooleanUtils.isTrue(showInToolBar))
            {
                AbstractButton actionComponent;

                Boolean toggleButton = (Boolean) action.getValue(com.queryeer.api.action.Constants.ACTION_TOGGLE);
                if (BooleanUtils.isTrue(toggleButton))
                {
                    JToggleButton button = new JToggleButton(action);
                    button.addActionListener(l -> action.putValue(Action.SELECTED_KEY, button.isSelected()));

                    Boolean selected = (Boolean) action.getValue(Action.SELECTED_KEY);
                    button.setSelected(BooleanUtils.isTrue(selected));

                    actionComponent = button;
                }
                else
                {
                    actionComponent = new JButton(action);
                }

                if (shortDescription != null)
                {
                    if (keyStroke != null)
                    {
                        shortDescription += " (" + getAcceleratorText(keyStroke) + ")";
                    }
                    actionComponent.setToolTipText(shortDescription);
                }

                toolBar.add(actionComponent, (int) order);
                editorToolbarActions.add(actionComponent);
            }
            if (BooleanUtils.isTrue(showInMenu))
            {
                String menu = (String) action.getValue(com.queryeer.api.action.Constants.ACTION_MENU);
                if (!"Edit".equalsIgnoreCase(menu))
                {
                    LOGGER.error("{} is not supported menu for Editor Actions", menu);
                    continue;
                }

                JMenuItem menuItem = new JMenuItem(action);
                editMenu.add(menuItem, (int) order);
                editorMenuActions.add(menuItem);
            }
        }
    }

    private void setQueryEngineProperties(IQueryEngine queryEngine)
    {
        SwingUtilities.invokeLater(() ->
        {
            panelQueryEngineProperties.removeAll();
            panelQueryEngineProperties.add(queryEngine.getQuickPropertiesComponent(), BorderLayout.CENTER);
            panelQueryEngineProperties.revalidate();
            panelQueryEngineProperties.repaint();

            if (prevCatalogsDividerLocation == 0)
            {
                splitPane.setDividerLocation(panelQueryEngineProperties.getPreferredSize()
                        .getWidth() / splitPane.getWidth());
                prevCatalogsDividerLocation = splitPane.getDividerLocation();
            }
        });
    }

    void setRecentFiles(List<String> recentFiles)
    {
        this.recentFiles.removeAll();
        for (String file : recentFiles)
        {
            JMenuItem item = new JMenuItem(recentFileAction);
            item.setText(file);
            this.recentFiles.add(item);
        }
    }

    void setOpenRecentFileConsumer(Consumer<String> openRecentFileConsumer)
    {
        this.openRecentFileConsumer = openRecentFileConsumer;
    }

    void setActionHandler(Consumer<QueryeerController.ViewAction> actionHandler)
    {
        this.actionHandler = actionHandler;
    }

    void setNewQueryConsumer(Consumer<IQueryEngine> newQueryConsumer)
    {
        this.newQueryConsumer = newQueryConsumer;
    }

    QueryFileView getCurrentFile()
    {
        return (QueryFileView) tabbedPane.getSelectedComponent();
    }
}
