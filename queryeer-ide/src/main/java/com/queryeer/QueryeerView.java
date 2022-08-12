package com.queryeer;

import static org.apache.commons.lang3.StringUtils.defaultString;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
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
import java.util.function.BiConsumer;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
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
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.border.EtchedBorder;

import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.kordamp.ikonli.swing.FontIcon;

import com.queryeer.QueryeerController.ViewAction;
import com.queryeer.api.IQueryFile;
import com.queryeer.api.IQueryFileState;
import com.queryeer.api.component.Caret;
import com.queryeer.api.event.QueryFileChangedEvent;
import com.queryeer.api.event.Subscribe;
import com.queryeer.api.extensions.IExtensionAction;
import com.queryeer.api.extensions.IMainMenuAction;
import com.queryeer.api.extensions.IMainToolbarAction;
import com.queryeer.api.extensions.IQueryProvider;
import com.queryeer.api.extensions.output.IOutputExtension;
import com.queryeer.api.extensions.output.IOutputFormatExtension;
import com.queryeer.api.service.IEventBus;
import com.queryeer.event.CaretChangedEvent;

/** Main view */
class QueryeerView extends JFrame
{
    static final String NAME = "queryeerName";
    static final String ORDER = "queryeerOrder";
    private static final String TOGGLE_RESULT = "toggleResult";
    private static final String TOGGLE_QUICKPROPERTIES = "toggleQuickProperties";
    private static final String NEW_QUERY = "NewQuery";
    private static final String EXECUTE = "Execute";
    private static final String STOP = "Stop";

    private final JPanel topPanel;
    private final JMenuBar menuBar;
    private final JToolBar toolBar;

    private final JSplitPane splitPane;
    private final JPanel panelQuickProperties;
    private final JPanel panelStatus;
    private final JLabel labelMemory;
    private final JLabel labelCaret;
    private final JLabel labelVersion;
    private final JLabel labelProvider;
    private final JPopupMenu newQueryProviders;

    private final JMenuItem openItem;
    private final JMenuItem saveItem;
    private final JMenuItem saveAsItem;
    private final JMenuItem exitItem;
    private final JMenu recentFiles;
    private final JComboBox<IOutputExtension> comboOutput;
    private final JComboBox<IOutputFormatExtension> comboFormat;
    private final QueryFileTabbedPane tabbedPane;

    private BiConsumer<ViewAction, Object> actionHandler;
    private boolean quickProperitesCollapsed;
    private boolean fireChangedEvent = true;

    // Added items for current query file editor. To be able to remove stuff when switching editor/tab
    private List<Component> queryFileEditorItems = new ArrayList<>();
    private List<KeyStroke> queryFileEditorItemsKeyStrokes = new ArrayList<>();
    private List<IExtensionAction> queryFileEditorItemsActions = new ArrayList<>();

    // CSOFF
    QueryeerView(QueryeerModel model, QueryFileTabbedPane tabbedPane, IEventBus eventBus, List<IQueryProvider> queryProviders, List<IOutputExtension> outputExtensions,
            List<IOutputFormatExtension> outputFormatExtensions, List<IMainMenuAction> mainMenuActions)
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
        panelStatus.setLayout(new GridBagLayout());
        panelStatus.setPreferredSize(new Dimension(10, 20));
        getContentPane().add(panelStatus, BorderLayout.SOUTH);

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
        labelCaret.setToolTipText("Caret position (Line, column, position)");
        labelVersion = new JLabel();
        labelProvider = new JLabel();

        // CSON
        panelStatus.add(labelProvider, new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 3, 0, 0), 0, 0));
        panelStatus.add(labelMemory, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 0, 0, 3), 0, 0));
        panelStatus.add(labelCaret, new GridBagConstraints(2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 0, 0, 3), 0, 0));
        panelStatus.add(labelVersion, new GridBagConstraints(3, 0, 1, 1, 0.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 0, 0, 3), 0, 0));

        topPanel = new JPanel();
        topPanel.setLayout(new BorderLayout());
        getContentPane().add(topPanel, BorderLayout.NORTH);

        menuBar = new JMenuBar();
        topPanel.add(menuBar, BorderLayout.NORTH);

        openItem = new JMenuItem(openAction);
        openItem.setText("Open");
        openItem.setAccelerator(KeyStroke.getKeyStroke('O', Toolkit.getDefaultToolkit()
                .getMenuShortcutKeyMask()));
        saveItem = new JMenuItem(saveAction);
        saveItem.setText("Save");
        saveItem.setAccelerator(KeyStroke.getKeyStroke('S', Toolkit.getDefaultToolkit()
                .getMenuShortcutKeyMask()));
        saveAsItem = new JMenuItem(saveAsAction);
        saveAsItem.setText("Save As ...");
        recentFiles = new JMenu("Recent Files");

        exitItem = new JMenuItem(exitAction);
        exitItem.setText("Exit");

        JMenu fileMenu = new JMenu("File");
        fileMenu.putClientProperty(NAME, "file");
        fileMenu.add(openItem);
        fileMenu.add(saveItem);
        fileMenu.add(saveAsItem);
        fileMenu.add(new JSeparator());
        fileMenu.add(recentFiles);
        fileMenu.add(new JSeparator());
        fileMenu.add(exitItem);

        menuBar.add(fileMenu);

        JMenu toolsMenu = new JMenu("Tools");
        toolsMenu.putClientProperty(NAME, "tools");
        toolsMenu.add(new JMenuItem(optionsAction))
                .setText("Options ...");

        JMenu helpMenu = new JMenu("Help");
        toolsMenu.putClientProperty(NAME, "help");
        helpMenu.add(new JMenuItem(new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                actionHandler.accept(ViewAction.ABOUT, null);
            }
        }))
                .setText("About Queryeer IDE");

        JMenu editMenu = new JMenu("Edit");
        editMenu.putClientProperty(NAME, "edit");

        menuBar.add(editMenu);
        menuBar.add(toolsMenu);
        menuBar.add(helpMenu);

        toolBar = new JToolBar();
        toolBar.setRollover(true);
        toolBar.setFloatable(false);
        topPanel.add(toolBar, BorderLayout.SOUTH);

        KeyStroke executeKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK);
        KeyStroke stopKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        KeyStroke newQueryKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK);
        KeyStroke toggleResultKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK);

        InputMap inputMap = topPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
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

        newQueryProviders = new JPopupMenu();
        for (final IQueryProvider queryProvider : queryProviders)
        {
            JMenuItem menuItem = newQueryProviders.add(new JMenuItem(queryProvider.getTitle()));
            menuItem.addActionListener(l ->
            {
                actionHandler.accept(ViewAction.NEWQUERY, queryProvider);
            });
        }

        JButton newQueryButton = new JButton(newQueryAction);
        newQueryButton.setHideActionText(true);
        newQueryButton.setToolTipText("Open new query window (" + getAcceleratorText(newQueryKeyStroke) + ")");

        JButton newQueryProvidersButton = new JButton(FontIcon.of(FontAwesome.CARET_DOWN));
        newQueryProvidersButton.addActionListener(l ->
        {
            newQueryProviders.show(newQueryButton, 0, (int) newQueryButton.getPreferredSize()
                    .getHeight());
        });

        MouseAdapter newQueryMouseAdapter = new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                newQueryButton.getModel()
                        .setRollover(true);
                newQueryProvidersButton.getModel()
                        .setRollover(true);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                newQueryButton.getModel()
                        .setRollover(false);
                newQueryProvidersButton.getModel()
                        .setRollover(false);
            }
        };

        newQueryButton.addMouseListener(newQueryMouseAdapter);
        newQueryProvidersButton.addMouseListener(newQueryMouseAdapter);

        JButton executeButton = new JButton(executeAction);
        executeButton.setText("Execute");
        executeButton.setToolTipText("Execute query (" + getAcceleratorText(executeKeyStroke) + ")");

        toolBar.add(openAction)
                .setToolTipText("Open file (" + getAcceleratorText(openItem.getAccelerator()) + ")");
        toolBar.add(saveAction)
                .setToolTipText("Save current file (" + getAcceleratorText(saveItem.getAccelerator()) + ")");
        toolBar.addSeparator();
        toolBar.add(newQueryButton);
        toolBar.add(newQueryProvidersButton);
        toolBar.add(executeButton);
        toolBar.add(cancelAction)
                .setToolTipText("Cancel query (" + getAcceleratorText(stopKeyStroke) + ")");
        toolBar.addSeparator();
        toolBar.add(toggleQuickPropertiesAction)
                .setToolTipText("Toggle quick properties pane");
        toolBar.add(toggleResultAction)
                .setToolTipText("Toggle result pane (" + getAcceleratorText(toggleResultKeyStroke) + ")");

        comboOutput = new JComboBox<IOutputExtension>()
        {
            @Override
            protected void fireActionEvent()
            {
                if (fireChangedEvent)
                {
                    super.fireActionEvent();
                }
            }
        };

        // CSOFF
        comboOutput.setMaximumSize(new Dimension(130, 20));
        // CSON
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

        DefaultComboBoxModel<IOutputExtension> outputComboModel = (DefaultComboBoxModel<IOutputExtension>) comboOutput.getModel();
        for (IOutputExtension outputExtension : actualOutputExtensions)
        {
            outputComboModel.addElement(outputExtension);
        }

        comboFormat = new JComboBox<IOutputFormatExtension>()
        {
            @Override
            protected void fireActionEvent()
            {
                if (fireChangedEvent)
                {
                    super.fireActionEvent();
                }
            }
        };
        // CSOFF
        comboFormat.setMaximumSize(new Dimension(100, 20));
        // CSON
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

        DefaultComboBoxModel<IOutputFormatExtension> formatComboModel = (DefaultComboBoxModel<IOutputFormatExtension>) comboFormat.getModel();
        for (IOutputFormatExtension outputFormatExtension : actualOutputFormatExtensions)
        {
            formatComboModel.addElement(outputFormatExtension);
        }
        comboFormat.addActionListener(l -> actionHandler.accept(ViewAction.FORMAT_CHANGED, comboFormat.getSelectedItem()));

        toolBar.addSeparator();
        toolBar.add(new JLabel("Output "));
        toolBar.add(comboOutput);
        toolBar.addSeparator();
        toolBar.add(new JLabel("Format "));
        toolBar.add(comboFormat);
        toolBar.addSeparator();

        // TODO: Static toolbar and menu items from extensions

        splitPane = new JSplitPane();
        splitPane.setDividerSize(3);
        getContentPane().add(splitPane, BorderLayout.CENTER);

        splitPane.setRightComponent(tabbedPane);

        panelQuickProperties = new JPanel();
        panelQuickProperties.setLayout(new BorderLayout());
        splitPane.setLeftComponent(new JScrollPane(panelQuickProperties));

        setIconImages(Constants.APPLICATION_ICONS);

        // Switch formats upon changing output
        comboOutput.addActionListener(l ->
        {
            IOutputExtension extension = (IOutputExtension) comboOutput.getSelectedItem();
            comboFormat.setEnabled(extension.supportsOutputFormats());
            actionHandler.accept(ViewAction.OUTPUT_CHANGED, extension);
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

        setOrderOnMenuItems(menuBar);
        setOrderOnToolbarItems(toolBar);

        eventBus.register(this);
        bindOutputExtensions(outputExtensions);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        // CSOFF
        setPreferredSize(new Dimension(1600, 800));
        // CSON
        setLocationByPlatform(true);
        pack();
    }

    private void setOrderOnMenuItems(JMenuBar menuBar)
    {
        for (int i = 0; i < menuBar.getMenuCount(); i++)
        {
            JMenu menu = menuBar.getMenu(i);
            menu.putClientProperty(ORDER, (i + 1) * 100);
            for (int j = 0; j < menu.getMenuComponentCount(); j++)
            {
                ((JComponent) menu.getMenuComponent(j)).putClientProperty(ORDER, (j + 1) * 100);
            }
        }
    }

    private void setOrderOnToolbarItems(JToolBar toolBar)
    {
        for (int i = 0; i < toolBar.getComponentCount(); i++)
        {
            ((JComponent) toolBar.getComponent(i)).putClientProperty(ORDER, (i + 1) * 100);
        }
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
    private void queryFileChanged(QueryFileChangedEvent event)
    {
        IQueryFile queryFile = event.getQueryFile();
        // Populate toolbar and menu items from current query files editor
        InputMap inputMap = topPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        // Remove previous items
        for (Component component : queryFileEditorItems)
        {
            Container parent = component.getParent();
            if (parent == null)
            {
                continue;
            }
            parent.remove(component);

            // If a sub menu got empty then delete that also
            if (parent instanceof JPopupMenu
                    && ((JPopupMenu) parent).getComponentCount() == 0)
            {
                parent = (Container) ((JPopupMenu) parent).getInvoker();
                parent.getParent()
                        .remove(parent);
            }
        }
        queryFileEditorItems.clear();
        for (KeyStroke keyStroke : queryFileEditorItemsKeyStrokes)
        {
            inputMap.remove(keyStroke);
        }
        queryFileEditorItemsKeyStrokes.clear();
        for (IExtensionAction extensionAction : queryFileEditorItemsActions)
        {
            topPanel.getActionMap()
                    .remove(extensionAction);
        }

        for (IExtensionAction extensionAction : queryFile.getQueryFileState(IQueryFileState.class)
                .get()
                .getQueryEditorComponent()
                .getComponentActions())
        {
            if (extensionAction instanceof IMainMenuAction)
            {
                IMainMenuAction menuAction = (IMainMenuAction) extensionAction;
                String path = menuAction.getMenuPath();
                JMenu menu = MenuBuilder.getOrCreate(menuBar, path);

                // If menu is empty we wont end up in loop and need to set if the item as added or not
                boolean added = false;
                // Find position to insert menu item in menu
                for (int j = 0; j < menu.getMenuComponentCount(); j++)
                {
                    JComponent component = (JComponent) menu.getMenuComponent(j);
                    int order = (int) component.getClientProperty(ORDER);
                    if (menuAction.order() < order)
                    {
                        added = true;
                        Action action = menuAction.getAction();
                        JMenuItem menuItem = menu.insert(new JMenuItem(action), j);
                        menuItem.putClientProperty(ORDER, menuAction.order());
                        queryFileEditorItems.add(menuItem);
                        break;
                    }
                }
                if (!added)
                {
                    Action action = menuAction.getAction();
                    JMenuItem menuItem = menu.add(new JMenuItem(action));
                    menuItem.putClientProperty(ORDER, menuAction.order());
                    queryFileEditorItems.add(menuItem);
                }
            }
            else if (extensionAction instanceof IMainToolbarAction)
            {
                IMainToolbarAction toolbarAction = (IMainToolbarAction) extensionAction;
                for (int i = 0; i < toolBar.getComponentCount(); i++)
                {
                    int order = (int) ((JComponent) toolBar.getComponent(i)).getClientProperty(ORDER);
                    boolean last = i == toolBar.getComponentCount() - 1;
                    if (toolbarAction.order() < order
                            || last)
                    {

                        Action action = toolbarAction.getAction();
                        JButton button = new JButton(action);
                        button.setText("");
                        toolBar.add(button, i + (last ? 1
                                : 0));
                        button.putClientProperty(ORDER, toolbarAction.order());
                        String tooltip = (String) action.getValue(JComponent.TOOL_TIP_TEXT_KEY);
                        KeyStroke keyStroke = (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);

                        if (keyStroke != null)
                        {
                            queryFileEditorItemsActions.add(extensionAction);
                            queryFileEditorItemsKeyStrokes.add(keyStroke);
                            inputMap.put(keyStroke, extensionAction);
                            topPanel.getActionMap()
                                    .put(extensionAction, action);

                            tooltip += " (" + getAcceleratorText(keyStroke) + ")";
                        }

                        button.setToolTipText(defaultString(tooltip, (String) action.getValue(Action.NAME)));
                        queryFileEditorItems.add(button);
                        break;
                    }
                }
            }
        }

        menuBar.revalidate();
        menuBar.repaint();
        toolBar.revalidate();
        toolBar.repaint();

        fireChangedEvent = false;
        comboOutput.setSelectedItem(queryFile.getOutput());
        comboFormat.setSelectedItem(queryFile.getOutputFormat());
        fireChangedEvent = true;
        labelProvider.setText("Provider: " + queryFile.getQueryFileState(IQueryFileState.class)
                .get()
                .getQueryProvider()
                .getTitle());

        // Focus the editor component
        queryFile.getQueryFileState(IQueryFileState.class)
                .get()
                .getQueryEditorComponent()
                .getComponent()
                .requestFocusInWindow();
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
            actionHandler.accept(ViewAction.OPTIONS, null);
        }
    };

    private final Action openAction = new AbstractAction("OPEN", Constants.FOLDER_OPEN_O)
    {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            actionHandler.accept(ViewAction.OPEN, null);
        }
    };

    private final Action saveAction = new AbstractAction("SAVE", Constants.SAVE)
    {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            actionHandler.accept(ViewAction.SAVE, null);
        }
    };

    private final Action saveAsAction = new AbstractAction()
    {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            actionHandler.accept(ViewAction.SAVEAS, null);
        }
    };

    private final Action exitAction = new AbstractAction()
    {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            actionHandler.accept(ViewAction.EXIT, null);
        }
    };

    private final Action executeAction = new AbstractAction(EXECUTE, Constants.PLAY_CIRCLE)
    {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            actionHandler.accept(ViewAction.EXECUTE, null);
        }
    };

    private final Action cancelAction = new AbstractAction(EXECUTE, Constants.STOP_CIRCLE)
    {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            actionHandler.accept(ViewAction.CANCEL, null);
        }
    };

    private final Action newQueryAction = new AbstractAction(NEW_QUERY, Constants.FILE_TEXT_O)
    {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            actionHandler.accept(ViewAction.NEWQUERY, null);
        }
    };

    private final Action toggleResultAction = new AbstractAction(TOGGLE_RESULT, Constants.ARROWS_V)
    {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            actionHandler.accept(ViewAction.TOGGLE_RESULT, null);
        }
    };

    private final Action toggleQuickPropertiesAction = new AbstractAction(TOGGLE_QUICKPROPERTIES, Constants.ARROWS_H)
    {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            // Expanded
            if (!quickProperitesCollapsed)
            {

                splitPane.setDividerLocation(0);
                quickProperitesCollapsed = true;
            }
            else
            {
                splitPane.setDividerLocation((int) panelQuickProperties.getPreferredSize()
                        .getWidth());
                quickProperitesCollapsed = false;
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
            actionHandler.accept(ViewAction.OPEN_RECENT, file);
        }
    };

    JPanel getPanelCatalogs()
    {
        return panelQuickProperties;
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

    void setActionHandler(BiConsumer<QueryeerController.ViewAction, Object> actionHandler)
    {
        this.actionHandler = actionHandler;
    }

    QueryFileView getCurrentFile()
    {
        return (QueryFileView) tabbedPane.getSelectedComponent();
    }

    void populateQuickPropertiesPanel()
    {
        QueryFileModel file = getCurrentFile().getFile();
        Component providerComponent = file.getQueryFileState()
                .getQueryProvider()
                .getQuickPropertiesComponent();
        Component currentComponent = panelQuickProperties.getComponentCount() > 0 ? panelQuickProperties.getComponent(0)
                : null;
        if (currentComponent == null
                || currentComponent.getClass() != providerComponent.getClass())
        {
            panelQuickProperties.removeAll();
            panelQuickProperties.add(providerComponent, BorderLayout.CENTER);
            panelQuickProperties.repaint();
            if (!this.quickProperitesCollapsed)
            {
                splitPane.resetToPreferredSizes();
                splitPane.setDividerLocation((int) providerComponent.getPreferredSize()
                        .getWidth());
            }
        }
    }
}
