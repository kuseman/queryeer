package com.queryeer;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
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
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.border.EtchedBorder;

import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.kordamp.ikonli.swing.FontIcon;

import com.queryeer.QueryeerController.ViewAction;
import com.queryeer.api.component.AnimatedIcon;
import com.queryeer.api.event.Subscribe;
import com.queryeer.api.extensions.output.IOutputExtension;
import com.queryeer.api.extensions.output.IOutputFormatExtension;
import com.queryeer.api.service.IEventBus;
import com.queryeer.domain.Caret;
import com.queryeer.event.CaretChangedEvent;

/** Main view */
class QueryeerView extends JFrame
{
    private static final String TOGGLE_COMMENT = "toggleComment";
    private static final String TOGGLE_RESULT = "toggleResult";
    private static final String TOGGLE_CATALOGS = "toggleCatalogs";
    private static final String NEW_QUERY = "NewQuery";
    private static final String EXECUTE = "Execute";
    private static final String STOP = "Stop";
    private static final Icon TASKS_ICON = FontIcon.of(FontAwesome.TASKS);
    private static final AnimatedIcon SPINNER = new AnimatedIcon(Utils.getResouceIcon("/icons/spinner.gif"));

    private final JPanel topPanel;

    private final JSplitPane splitPane;
    private final JPanel panelCatalogs;
    private final JPanel panelStatus;
    private final JLabel labelMemory;
    private final JLabel labelCaret;
    private final JLabel labelVersion;
    private final JLabel labelTasks;
    private final JLabel labelTasksSpinner;

    private final JMenuItem openItem;
    private final JMenuItem saveItem;
    private final JMenuItem saveAsItem;
    private final JMenuItem exitItem;
    private final JMenu recentFiles;
    private final JComboBox<IOutputExtension> comboOutput;
    private final JComboBox<IOutputFormatExtension> comboFormat;

    private Consumer<String> openRecentFileConsumer;
    private Consumer<ViewAction> actionHandler;
    private boolean catalogsCollapsed;
    private int prevCatalogsDividerLocation;
    private final QueryFileTabbedPane tabbedPane;
    private final TasksDialog tasksDialog;

    // CSOFF
    QueryeerView(QueryeerModel model, QueryFileTabbedPane tabbedPane, IEventBus eventBus, List<IOutputExtension> outputExtensions, List<IOutputFormatExtension> outputFormatExtensions)
    // CSON
    {
        setLocationRelativeTo(null);
        getContentPane().setLayout(new BorderLayout(0, 0));

        this.tabbedPane = tabbedPane;

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

        // CSON
        JPanel panelTasks = new JPanel();
        panelTasks.setLayout(new BoxLayout(panelTasks, BoxLayout.X_AXIS));
        panelTasks.setToolTipText("Click To See Tasks");
        panelTasks.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (tasksDialog.isShowing())
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

        JMenu editMenu = new JMenu("Edit");
        editMenu.add(new JMenuItem(new AbstractAction("Find ...", Constants.SEARCH)
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                getCurrentFile().showFind();
            }
        }));
        editMenu.add(new JMenuItem(new AbstractAction("Replace ...")
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                getCurrentFile().showReplace();
            }
        }));
        editMenu.add(new JMenuItem(new AbstractAction("GoTo Line ...", Constants.SHARE)
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                getCurrentFile().showGoToLine();
            }
        }));

        menuBar.add(editMenu);
        menuBar.add(toolsMenu);
        menuBar.add(helpMenu);

        JToolBar toolBar = new JToolBar();
        toolBar.setRollover(true);
        toolBar.setFloatable(false);
        topPanel.add(toolBar, BorderLayout.SOUTH);

        KeyStroke executeKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK);
        KeyStroke stopKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        KeyStroke newQueryKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK);
        KeyStroke toggleResultKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK);
        KeyStroke toggleCommentKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_7, InputEvent.CTRL_DOWN_MASK);

        InputMap inputMap = topPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        inputMap.put(executeKeyStroke, EXECUTE);
        inputMap.put(stopKeyStroke, STOP);
        inputMap.put(newQueryKeyStroke, NEW_QUERY);
        inputMap.put(toggleResultKeyStroke, TOGGLE_RESULT);
        inputMap.put(toggleCommentKeyStroke, TOGGLE_COMMENT);
        topPanel.getActionMap()
                .put(EXECUTE, executeAction);
        topPanel.getActionMap()
                .put(STOP, cancelAction);
        topPanel.getActionMap()
                .put(NEW_QUERY, newQueryAction);
        topPanel.getActionMap()
                .put(TOGGLE_RESULT, toggleResultAction);
        topPanel.getActionMap()
                .put(TOGGLE_COMMENT, toggleCommentAction);

        JButton newQueryButton = new JButton(newQueryAction);
        newQueryButton.setText("New query");
        newQueryButton.setToolTipText("Open new query window (" + getAcceleratorText(newQueryKeyStroke) + ")");

        JButton executeButton = new JButton(executeAction);
        executeButton.setText("Execute");
        executeButton.setToolTipText("Execute query (" + getAcceleratorText(executeKeyStroke) + ")");

        toolBar.add(openAction)
                .setToolTipText("Open file (" + getAcceleratorText(openItem.getAccelerator()) + ")");
        toolBar.add(saveAction)
                .setToolTipText("Save current file (" + getAcceleratorText(saveItem.getAccelerator()) + ")");
        toolBar.addSeparator();
        toolBar.add(newQueryButton);
        toolBar.add(executeButton);
        toolBar.add(cancelAction)
                .setToolTipText("Cancel query (" + getAcceleratorText(stopKeyStroke) + ")");
        toolBar.addSeparator();
        toolBar.add(toggleCatalogsAction)
                .setToolTipText("Toggle catalogs pane");
        toolBar.add(toggleResultAction)
                .setToolTipText("Toggle result pane (" + getAcceleratorText(toggleResultKeyStroke) + ")");
        toolBar.add(toggleCommentAction)
                .setToolTipText("Toggle comment on selected lines (" + getAcceleratorText(toggleCommentKeyStroke) + ")");

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
            outputComboModel.addElement(outputExtension);
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

        comboFormat.addActionListener(l -> actionHandler.accept(ViewAction.FORMAT_CHANGED));

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

        panelCatalogs = new JPanel();
        panelCatalogs.setLayout(new GridBagLayout());
        splitPane.setLeftComponent(new JScrollPane(panelCatalogs));

        setIconImages(Constants.APPLICATION_ICONS);

        // Switch formats upon changing output
        comboOutput.addActionListener(l ->
        {
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

        tasksDialog = new TasksDialog(this, eventBus, running -> labelTasksSpinner.setVisible(running));

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

    private final Action toggleCatalogsAction = new AbstractAction(TOGGLE_CATALOGS, Constants.ARROWS_H)
    {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            // Expanded
            if (!catalogsCollapsed)
            {
                prevCatalogsDividerLocation = splitPane.getDividerLocation();
                splitPane.setDividerLocation(0.0d);
                catalogsCollapsed = true;
            }
            else
            {
                splitPane.setDividerLocation(prevCatalogsDividerLocation);
                catalogsCollapsed = false;
            }
        }
    };

    private final Action toggleCommentAction = new AbstractAction(TOGGLE_COMMENT, Constants.INDENT)
    {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            actionHandler.accept(ViewAction.TOGGLE_COMMENT);
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

    JPanel getPanelCatalogs()
    {
        return panelCatalogs;
    }

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

    QueryFileView getCurrentFile()
    {
        return (QueryFileView) tabbedPane.getSelectedComponent();
    }

}
