package com.queryeer;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.Window;
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
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
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
import javax.swing.JDialog;
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
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.table.AbstractTableModel;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.kordamp.ikonli.swing.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.queryeer.QueryeerController.ViewAction;
import com.queryeer.api.action.IActionRegistry;
import com.queryeer.api.action.IActionRegistry.ActionScope;
import com.queryeer.api.component.AnimatedIcon;
import com.queryeer.api.component.DialogUtils;
import com.queryeer.api.component.DialogUtils.IQuickSearchModel;
import com.queryeer.api.event.NewQueryFileEvent;
import com.queryeer.api.event.Subscribe;
import com.queryeer.api.extensions.engine.IQueryEngine;
import com.queryeer.api.extensions.output.IOutputExtension;
import com.queryeer.api.extensions.output.IOutputFormatExtension;
import com.queryeer.api.service.IEventBus;
import com.queryeer.domain.Caret;
import com.queryeer.event.CaretChangedEvent;
import com.queryeer.event.QueryFileClosingEvent;
import com.queryeer.event.QueryFileSaveEvent;

/** Main view */
class QueryeerView extends JFrame
{
    private static final Logger LOGGER = LoggerFactory.getLogger(QueryeerView.class);

    private static final String FILE_MODEL = "fileModel";
    private static final String TOGGLE_RESULT = "Queryeer.ToggleResultPanel";
    private static final String TOGGLE_QUICK_PROPERTIES = "toggleQuickProperties";
    private static final String NEW_QUERY = "Queryeer.NewQuery";
    private static final String EXECUTE = "Queryeer.Execute";
    private static final String OPEN_QUICK_FILES = "Queryeer.OpenQuickFiles";
    private static final String OPEN_QUICK_DATASOURCES = "Queryeer.OpenQuickDatasources";
    private static final String STOP = "Queryeer.Stop";
    private static final Icon TASKS_ICON = FontIcon.of(FontAwesome.TASKS);
    private static final Icon SPINNER = AnimatedIcon.createSmallSpinner();

    private final JPanel topPanel;
    private final InputMap inputMap;

    private final JSplitPane splitPane;
    private final JTabbedPane leftTabbedPane;
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
    private final JMenu recentFilesMenu;
    private final JMenu windowMenu;

    private final JToolBar toolBar;
    private final JComboBox<IOutputExtension> comboOutput;
    private final JComboBox<IOutputFormatExtension> comboFormat;
    private final QueryeerModel model;
    private final QueryFileTabbedPane tabbedPane;
    private final ProjectsView projectsView;
    private final IEventBus eventBus;
    private final TasksDialog tasksDialog;
    private final LogsDialog logsDialog;
    private final DialogUtils.QuickSearchWindow<FilesQuickSearchModel.ListItem> filesQuickSearchWindow;
    private final DialogUtils.QuickSearchWindow<IQuickSearchModel.Item> datasourcesQuickSearchWindow;

    private boolean suppressChangeEvents = false;

    private List<String> recentFiles = emptyList();
    private Consumer<String> openRecentFileConsumer;
    private Consumer<IQueryEngine> newQueryConsumer;
    private Consumer<ViewAction> actionHandler;
    private boolean quickPropertiesCollapsed;
    private int prevPropertiesDividerLocation;
    private WindowsDialog windowsDialog;

    private List<AbstractButton> editorToolbarActions = new ArrayList<>();
    private List<JMenuItem> editorMenuActions = new ArrayList<>();

    // CSOFF
    QueryeerView(QueryeerModel model, QueryFileTabbedPane tabbedPane, ProjectsView projectsView, IEventBus eventBus, List<IOutputExtension> outputExtensions,
            List<IOutputFormatExtension> outputFormatExtensions, List<IQueryEngine> queryEngines, ActionRegistry actionRegistry)
    // CSON
    {
        setLocationRelativeTo(null);
        getContentPane().setLayout(new BorderLayout(0, 0));

        this.model = requireNonNull(model, "model");
        this.tabbedPane = requireNonNull(tabbedPane, "tabbedPane");
        this.projectsView = requireNonNull(projectsView, "projectsView");
        this.eventBus = requireNonNull(eventBus, "eventBus");
        this.model.addPropertyChangeListener(queryeerModelListener);
        this.filesQuickSearchWindow = new DialogUtils.QuickSearchWindow<>(this, new FilesQuickSearchModel());
        this.datasourcesQuickSearchWindow = new DialogUtils.QuickSearchWindow<>(this, new DatasourceQuickSearchModel());

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
                toggleLogsDialog();
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
                toggleTasksDialog();
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
        recentFilesMenu = new JMenu("Recent Files");

        exitItem = new JMenuItem(exitAction);
        exitItem.setText("Exit");

        JMenu fileMenu = new JMenu("File");
        fileMenu.add(openItem);
        fileMenu.add(saveItem);
        fileMenu.add(saveAsItem);
        fileMenu.add(new JSeparator());
        fileMenu.add(recentFilesMenu);
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

        windowMenu = new JMenu("Window");
        windowMenu.add(new JMenuItem(new AbstractAction("Show Logs ...")
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                toggleLogsDialog();
            }
        }));
        windowMenu.add(new JMenuItem(new AbstractAction("Show Tasks ...")
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                toggleTasksDialog();
            }
        }));

        JMenu windowsSort = new JMenu("Sort Windows");

        //@formatter:off
        List<Pair<String, Comparator<QueryFileModel>>> comparators = List.of(
                Pair.of("Name ASC", (a, b) -> StringUtils.compareIgnoreCase(a.getFile().getName(), b.getFile().getName())),
                Pair.of("Name DESC", (a, b) -> -StringUtils.compareIgnoreCase(a.getFile().getName(), b.getFile().getName())),
                Pair.of("Path ASC", (a, b) -> StringUtils.compareIgnoreCase(FilenameUtils.getPath(a.getFile().getAbsolutePath()), FilenameUtils.getPath(b.getFile().getAbsolutePath()))),
                Pair.of("Path DESC", (a, b) -> -StringUtils.compareIgnoreCase(FilenameUtils.getPath(a.getFile().getAbsolutePath()), FilenameUtils.getPath(b.getFile().getAbsolutePath()))),
                Pair.of("Activity ASC", (a, b) -> -Long.compare(a.getLastActivity(), b.getLastActivity())),
                Pair.of("Activity DESC", (a, b) -> Long.compare(a.getLastActivity(), b.getLastActivity())),
                Pair.of("Modification ASC", (a, b) -> -Long.compare(a.getLastModified(), b.getLastModified())),
                Pair.of("Modification DESC", (a, b) -> Long.compare(a.getLastModified(), b.getLastModified())),
                Pair.of("Type ASC", (a, b) -> StringUtils.compareIgnoreCase(FilenameUtils.getExtension(a.getFile().getName()), FilenameUtils.getExtension(a.getFile().getName()))),
                Pair.of("Type DESC", (a, b) -> -StringUtils.compareIgnoreCase(FilenameUtils.getExtension(a.getFile().getName()), FilenameUtils.getExtension(a.getFile().getName())))
                );
        //@formatter:on

        for (Pair<String, Comparator<QueryFileModel>> pair : comparators)
        {
            windowsSort.add(new JMenuItem(new AbstractAction(pair.getKey())
            {
                @Override
                public void actionPerformed(ActionEvent e)
                {
                    model.sortFiles(pair.getValue());
                }
            }));
        }

        windowMenu.add(windowsSort);
        windowMenu.add(new JMenuItem(new AbstractAction("Windows ...")
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                if (windowsDialog == null)
                {
                    windowsDialog = new WindowsDialog();
                }
                Window activeWindow = javax.swing.FocusManager.getCurrentManager()
                        .getActiveWindow();
                windowsDialog.setLocationRelativeTo(activeWindow);
                windowsDialog.setVisible(true);
            }
        }));

        windowMenu.addSeparator();

        editMenu = new JMenu("Edit");
        menuBar.add(editMenu);
        menuBar.add(toolsMenu);
        menuBar.add(windowMenu);
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
        KeyStroke showQuickFilesKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0);
        KeyStroke showQuickDatasourcesKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0);
        // CSON

        // Register actions
        actionRegistry.register(EXECUTE, ActionScope.COMPONENT_FOCUS, List.of(new IActionRegistry.KeyboardShortcut(executeKeyStroke)));
        actionRegistry.register(STOP, ActionScope.COMPONENT_FOCUS, List.of(new IActionRegistry.KeyboardShortcut(stopKeyStroke)));
        actionRegistry.register(NEW_QUERY, ActionScope.COMPONENT_FOCUS, List.of(new IActionRegistry.KeyboardShortcut(newQueryKeyStroke)));
        actionRegistry.register(TOGGLE_RESULT, ActionScope.COMPONENT_FOCUS, List.of(new IActionRegistry.KeyboardShortcut(toggleResultKeyStroke)));
        actionRegistry.register(OPEN_QUICK_FILES, ActionScope.COMPONENT_FOCUS, List.of(new IActionRegistry.KeyboardShortcut(showQuickFilesKeyStroke)));
        actionRegistry.register(OPEN_QUICK_DATASOURCES, ActionScope.COMPONENT_FOCUS, List.of(new IActionRegistry.KeyboardShortcut(showQuickDatasourcesKeyStroke)));

        inputMap = topPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);

        inputMap.put(showQuickFilesKeyStroke, OPEN_QUICK_FILES);
        inputMap.put(showQuickDatasourcesKeyStroke, OPEN_QUICK_DATASOURCES);
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
        topPanel.getActionMap()
                .put(OPEN_QUICK_FILES, new AbstractAction()
                {
                    @Override
                    public void actionPerformed(ActionEvent e)
                    {
                        if (filesQuickSearchWindow.isVisible())
                        {
                            return;
                        }
                        filesQuickSearchWindow.setVisible(true);
                    }
                });
        topPanel.getActionMap()
                .put(OPEN_QUICK_DATASOURCES, new AbstractAction()
                {
                    @Override
                    public void actionPerformed(ActionEvent e)
                    {
                        if (datasourcesQuickSearchWindow.isVisible())
                        {
                            return;
                        }
                        datasourcesQuickSearchWindow.setVisible(true);
                    }
                });

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
        getContentPane().add(splitPane, BorderLayout.CENTER);

        splitPane.setRightComponent(tabbedPane);

        panelQueryEngineProperties = new JPanel();
        panelQueryEngineProperties.setLayout(new BorderLayout());
        panelQueryEngineProperties.setBorder(new EmptyBorder(2, 2, 2, 2));

        leftTabbedPane = new JTabbedPane(JTabbedPane.TOP);
        leftTabbedPane.addTab("Properties", new JScrollPane(panelQueryEngineProperties));
        leftTabbedPane.addTab("Projects", projectsView);

        leftTabbedPane.setIconAt(1, FontIcon.of(FontAwesome.FILES_O));

        splitPane.setLeftComponent(leftTabbedPane);

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
                            List.of(filesQuickSearchWindow, datasourcesQuickSearchWindow)
                                    .stream()
                                    .forEach(w ->
                                    {
                                        if (w.isVisible()
                                                && evt.getNewValue() != null
                                                && evt.getNewValue() != w)
                                        {
                                            Window owningWindow = SwingUtilities.getWindowAncestor((Component) evt.getNewValue());
                                            if (owningWindow != w)
                                            {
                                                w.setVisible(false);
                                            }
                                        }
                                    });

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

        tasksDialog = new TasksDialog(this, eventBus, running -> SwingUtilities.invokeLater(() -> labelTasksSpinner.setVisible(running)));
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

    private List<JMenuItem> openFilesMenuItems = new ArrayList<>();

    private PropertyChangeListener queryeerModelListener = new PropertyChangeListener()
    {
        @Override
        public void propertyChange(PropertyChangeEvent evt)
        {
            if (QueryeerModel.FILES.equalsIgnoreCase(evt.getPropertyName())
                    || QueryeerModel.ORDER.equalsIgnoreCase(evt.getPropertyName()))
            {
                // Remove current items from menu then add new ones according to model
                for (JMenuItem item : openFilesMenuItems)
                {
                    windowMenu.remove(item);
                }

                openFilesMenuItems.clear();
                for (QueryFileModel file : model.getFiles())
                {
                    JMenuItem windowItem = new JMenuItem(new AbstractAction(file.getFile()
                            .getName())
                    {
                        @Override
                        public void actionPerformed(ActionEvent e)
                        {
                            model.setSelectedFile(file);
                        }
                    });

                    if (file == model.getSelectedFile())
                    {
                        windowItem.setIcon(FontIcon.of(FontAwesome.CHECK));
                    }

                    windowItem.putClientProperty(FILE_MODEL, file);
                    windowMenu.add(windowItem);
                    openFilesMenuItems.add(windowItem);

                    // Only add 20 windows
                    if (openFilesMenuItems.size() >= 20)
                    {
                        break;
                    }
                }

                // Redraw table when files changes
                if (windowsDialog != null)
                {
                    windowsDialog.tableModel.fireTableDataChanged();
                }
            }
            else if (QueryeerModel.SELECTED_FILE.equalsIgnoreCase(evt.getPropertyName()))
            {
                // Mark selected file with an icon
                QueryFileModel fileModel = (QueryFileModel) evt.getNewValue();
                if (fileModel == null)
                {
                    return;
                }
                for (JMenuItem item : openFilesMenuItems)
                {
                    QueryFileModel modelItem = (QueryFileModel) item.getClientProperty(FILE_MODEL);
                    item.setIcon(null);
                    if (modelItem != null
                            && fileModel == modelItem)
                    {
                        item.setIcon(FontIcon.of(FontAwesome.CHECK));
                    }
                }
                updateState(fileModel);
            }
        }
    };

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

    private void toggleLogsDialog()
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

    private void toggleTasksDialog()
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
            if (tabbedPane.getSelectedComponent() instanceof QueryFileView fileView)
            {
                fileView.toggleResultPane();
            }
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
                prevPropertiesDividerLocation = splitPane.getDividerLocation();
                splitPane.setDividerLocation(0.0d);
                quickPropertiesCollapsed = true;
            }
            else
            {
                splitPane.setDividerLocation(prevPropertiesDividerLocation);
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
    private void updateState(QueryFileModel file)
    {
        suppressChangeEvents = true;
        try
        {
            comboOutput.setSelectedItem(file.getOutputExtension());
            comboFormat.setSelectedItem(file.getOutputFormat());
            setQueryEngineProperties(file.getQueryEngine());
            file.getQueryEngine()
                    .focus(file);
            file.getEditor()
                    .focused();
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
                parent.remove(menuItem);
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
                if (order >= editMenu.getItemCount())
                {
                    order = editMenu.getItemCount() - 1;
                }

                editMenu.add(menuItem, (int) order);
                editorMenuActions.add(menuItem);
            }
        }
    }

    private void setQueryEngineProperties(IQueryEngine queryEngine)
    {
        SwingUtilities.invokeLater(() ->
        {
            leftTabbedPane.setTitleAt(0, queryEngine.getTitle());
            leftTabbedPane.setIconAt(0, queryEngine.getIcon());

            panelQueryEngineProperties.removeAll();
            panelQueryEngineProperties.add(queryEngine.getQuickPropertiesComponent(), BorderLayout.CENTER);
            panelQueryEngineProperties.revalidate();
            panelQueryEngineProperties.repaint();

            if (prevPropertiesDividerLocation == 0)
            {
                splitPane.setDividerLocation(panelQueryEngineProperties.getPreferredSize()
                        .getWidth() / splitPane.getWidth());
                prevPropertiesDividerLocation = splitPane.getDividerLocation();
            }
        });
    }

    void setRecentFiles(List<String> recentFiles)
    {
        this.recentFiles = recentFiles;
        this.recentFilesMenu.removeAll();
        for (String file : recentFiles)
        {
            JMenuItem item = new JMenuItem(recentFileAction);
            item.setText(file);
            this.recentFilesMenu.add(item);
            if (this.recentFilesMenu.getItemCount() >= 10)
            {
                break;
            }
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

    private class WindowsDialog extends DialogUtils.ADialog
    {
        private final FileModel tableModel;

        WindowsDialog()
        {
            setTitle("Windows");
            getContentPane().setLayout(new GridBagLayout());
            setModal(true);
            JTextArea preview = new JTextArea();
            preview.setEditable(false);

            JButton activate = new JButton("Activate");
            activate.setAlignmentX(0.5f);
            JButton close = new JButton("Close Window(s)");
            close.setAlignmentX(0.5f);
            JButton save = new JButton("Save");
            save.setAlignmentX(0.5f);
            JTable table = new JTable();
            table.getSelectionModel()
                    .addListSelectionListener(l ->
                    {
                        int length = table.getSelectedRows().length;
                        activate.setEnabled(length <= 1);
                        close.setEnabled(length > 0);
                        save.setEnabled(length > 0);
                        preview.setEnabled(length == 1);

                        String previewText = "";
                        if (length == 1)
                        {
                            QueryFileModel fileModel = model.getFiles()
                                    .get(table.convertRowIndexToModel(table.getSelectedRow()));
                            previewText = String.valueOf(fileModel.getEditor()
                                    .getValue(true));
                        }
                        preview.setText(previewText);
                        preview.setCaretPosition(0);
                    });
            table.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent e)
                {
                    if (SwingUtilities.isLeftMouseButton(e)
                            && e.getClickCount() == 2)
                    {
                        int row = table.rowAtPoint(e.getPoint());
                        if (row >= 0)
                        {
                            int modelRow = table.convertRowIndexToModel(row);
                            QueryFileModel fileModel = model.getFiles()
                                    .get(modelRow);
                            model.setSelectedFile(fileModel);
                            setVisible(false);
                        }
                    }
                }
            });
            table.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
            tableModel = new FileModel();
            table.setModel(tableModel);
            table.setAutoCreateRowSorter(true);

            JPanel buttonPanel = new JPanel();
            buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
            buttonPanel.add(activate);
            buttonPanel.add(save);
            buttonPanel.add(close);

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.weightx = 1.0d;
            gbc.weighty = 0.5d;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.insets = new Insets(2, 2, 2, 2);
            getContentPane().add(new JScrollPane(table), gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 1;
            gbc.gridy = 0;
            gbc.gridheight = 5;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.insets = new Insets(2, 2, 2, 2);
            getContentPane().add(buttonPanel, gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 1;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.insets = new Insets(2, 2, 2, 2);
            getContentPane().add(new JLabel("Preview:"), gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 2;
            gbc.weightx = 1.0d;
            gbc.weighty = 0.5d;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.insets = new Insets(2, 2, 2, 2);
            getContentPane().add(new JScrollPane(preview), gbc);

            activate.addActionListener(l ->
            {
                int index = table.getSelectedRow();
                if (index < 0)
                {
                    return;
                }
                index = table.convertRowIndexToModel(index);
                QueryFileModel fileModel = model.getFiles()
                        .get(index);
                model.setSelectedFile(fileModel);
                setVisible(false);
            });

            close.addActionListener(l ->
            {
                int[] selectedRows = table.getSelectedRows();
                Arrays.sort(selectedRows);
                int modifier = 0;
                for (int i = 0; i < selectedRows.length; i++)
                {
                    int index = table.convertRowIndexToModel(selectedRows[i] - modifier);
                    QueryFileModel fileModel = model.getFiles()
                            .get(index);
                    QueryFileClosingEvent event = new QueryFileClosingEvent(fileModel);
                    eventBus.publish(event);
                    if (event.isCanceled())
                    {
                        break;
                    }
                    modifier++;
                }
                // Redraw the table after we removed some files
                tableModel.fireTableDataChanged();
            });

            save.addActionListener(l ->
            {
                int[] selectedRows = table.getSelectedRows();
                Arrays.sort(selectedRows);
                for (int i = 0; i < selectedRows.length; i++)
                {
                    int index = table.convertRowIndexToModel(selectedRows[i]);
                    QueryFileModel fileModel = model.getFiles()
                            .get(index);
                    QueryFileSaveEvent event = new QueryFileSaveEvent(fileModel);
                    eventBus.publish(event);
                    if (event.isCanceled())
                    {
                        break;
                    }
                }
                // Redraw the table after we saved some files
                tableModel.fireTableDataChanged();
            });

            setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
            setPreferredSize(Constants.DEFAULT_DIALOG_SIZE);
            pack();
            setLocationRelativeTo(null);
            pack();
        }

        private class FileModel extends AbstractTableModel
        {
            @Override
            public int getRowCount()
            {
                return model.getFiles()
                        .size();
            }

            @Override
            public String getColumnName(int column)
            {
                return switch (column)
                {
                    case 0 -> "Name";
                    case 1 -> "Path";
                    case 2 -> "Size";
                    default -> throw new IllegalArgumentException("Invalid column index");
                };
            }

            @Override
            public Class<?> getColumnClass(int columnIndex)
            {
                if (columnIndex == 2)
                {
                    return Long.class;
                }
                return super.getColumnClass(columnIndex);
            }

            @Override
            public int getColumnCount()
            {
                return 3;
            }

            @Override
            public Object getValueAt(int rowIndex, int columnIndex)
            {
                QueryFileModel fileModel = model.getFiles()
                        .get(rowIndex);
                return switch (columnIndex)
                {
                    case 0 -> fileModel.getFile()
                            .getName()
                            + (fileModel.isDirty() ? "*"
                                    : "");
                    case 1 -> fileModel.getFile()
                            .getParent();
                    case 2 -> fileModel.getFile()
                            .length();
                    default -> throw new IllegalArgumentException("Invalid column index");
                };
            }
        }
    }

    /**
     * Proxy model for switch datasource. This class uses the current files query engines model if any.
     */
    class DatasourceQuickSearchModel implements IQuickSearchModel<IQuickSearchModel.Item>
    {
        @Override
        public SelectionResult handleSelection(IQuickSearchModel.Item item)
        {
            IQuickSearchModel<IQuickSearchModel.Item> currentModel = getCurrentModel();
            if (currentModel != null)
            {
                return currentModel.handleSelection(item);
            }
            return SelectionResult.HIDE_WINDOW;
        }

        @Override
        public int getSelectedIndex()
        {
            IQuickSearchModel<IQuickSearchModel.Item> currentModel = getCurrentModel();
            if (currentModel != null)
            {
                currentModel.getSelectedIndex();
            }
            return -1;
        }

        @Override
        public void reload(Consumer<Item> itemConsumer)
        {
            IQuickSearchModel<IQuickSearchModel.Item> currentModel = getCurrentModel();
            if (currentModel != null)
            {
                currentModel.reload(itemConsumer);
            }
        }

        @Override
        public boolean isEnabled()
        {
            return getCurrentModel() != null;
        }

        private IQuickSearchModel<IQuickSearchModel.Item> getCurrentModel()
        {
            QueryFileModel selectedFile = model.getSelectedFile();
            return selectedFile != null ? selectedFile.getQueryEngine()
                    .getDatasourceSearchModel()
                    : null;
        }
    }

    /** Model for quick search of windows (opened / recent). */
    class FilesQuickSearchModel implements IQuickSearchModel<FilesQuickSearchModel.ListItem>
    {
        @Override
        public void reload(Consumer<FilesQuickSearchModel.ListItem> itemConsumer)
        {
            List<QueryFileModel> files = new ArrayList<>(model.getFiles());
            files.sort((aa, bb) -> -Long.compare(aa.getLastActivity(), bb.getLastActivity()));

            // Keep track of what files we have opened to avoid duplicates in the different groups
            Set<String> processedFiles = new HashSet<>();
            for (QueryFileModel file : files)
            {
                if (!file.isNew())
                {
                    processedFiles.add(file.getFile()
                            .getAbsolutePath());
                }
                itemConsumer.accept(new ListItem(file));
            }

            AtomicBoolean markerAdded = new AtomicBoolean(false);
            projectsView.enumerateProjectFiles(file ->
            {
                if (processedFiles.add(file.getAbsolutePath()))
                {
                    if (!markerAdded.get())
                    {
                        itemConsumer.accept(new ListItem(ListItem.Type.ProjectFile, null));
                        markerAdded.set(true);
                    }

                    itemConsumer.accept(new ListItem(ListItem.Type.ProjectFile, file));
                }
            });

            if (!recentFiles.isEmpty())
            {
                markerAdded.set(false);
                for (String recentFile : recentFiles)
                {
                    if (processedFiles.add(recentFile))
                    {
                        if (!markerAdded.get())
                        {
                            itemConsumer.accept(new ListItem(ListItem.Type.RecentFile, null));
                            markerAdded.set(true);
                        }
                        itemConsumer.accept(new ListItem(ListItem.Type.RecentFile, new File(recentFile)));
                    }
                }
            }
        }

        @Override
        public SelectionResult handleSelection(ListItem item)
        {
            if (item.queryFile != null)
            {
                model.setSelectedFile(item.queryFile);
            }
            else if (item.type == ListItem.Type.RecentFile
                    && item.file != null)
            {
                openRecentFileConsumer.accept(item.file.getAbsolutePath());
            }
            else if (item.type == ListItem.Type.ProjectFile
                    && item.file != null)
            {
                eventBus.publish(new NewQueryFileEvent(item.file));
            }

            return SelectionResult.HIDE_WINDOW;
        }

        @Override
        public int getSelectedIndex()
        {
            // Pre select the second item in list since the first one is the current file
            return 1;
        }

        static class ListItem implements IQuickSearchModel.Item
        {
            private static final Icon FILES_O = FontIcon.of(FontAwesome.FILES_O);
            private static final Icon FILE_O = FontIcon.of(FontAwesome.FILE_O);
            private final Type type;
            private final QueryFileModel queryFile;
            private final File file;

            ListItem(QueryFileModel queryFile)
            {
                this.type = Type.QueryFile;
                this.queryFile = queryFile;
                this.file = null;
            }

            private ListItem(Type type, File file)
            {
                this.type = type;
                this.file = file;
                this.queryFile = null;
            }

            @Override
            public String getTitle()
            {
                if (type == Type.ProjectFile)
                {
                    return file == null ? "<html><b>--- Project Files ---</b></html>"
                            : (file.getName() + " (" + file.getParent() + ")");
                }
                else if (type == Type.RecentFile)
                {
                    return file == null ? "<html><b>--- Recent Files ---</b></html>"
                            : (file.getName() + " (" + file.getParent() + ")");
                }

                return (queryFile.isDirty() ? "*"
                        : "") + queryFile.getFile()
                                .getName();
            }

            @Override
            public boolean matches(String searchText)
            {
                // Markers are always matching
                if (queryFile == null
                        && file == null)
                {
                    return true;
                }
                return switch (type)
                {
                    case ProjectFile -> StringUtils.containsIgnoreCase(file.getAbsolutePath(), searchText);
                    case QueryFile -> StringUtils.containsIgnoreCase(queryFile.getFile()
                            .getAbsolutePath(), searchText);
                    case RecentFile -> StringUtils.containsIgnoreCase(file.getAbsolutePath(), searchText);
                };
            }

            @Override
            public Icon getIcon()
            {
                if (queryFile != null)
                {
                    return queryFile.getQueryEngine()
                            .getIcon();
                }
                else if (type == Type.ProjectFile)
                {
                    return FILES_O;
                }
                return FILE_O;
            }

            enum Type
            {
                QueryFile,
                ProjectFile,
                RecentFile
            }
        }
    }
}
