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
import java.util.List;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.Action;
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
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.border.EtchedBorder;

import com.queryeer.QueryeerController.ViewAction;
import com.queryeer.api.extensions.output.IOutputExtension;
import com.queryeer.api.extensions.output.IOutputFormatExtension;

/** Main view */
class QueryeerView extends JFrame
{
    private static final String TOGGLE_COMMENT = "toggleComment";
    private static final String TOGGLE_RESULT = "toggleResult";
    private static final String TOGGLE_CATALOGS = "toggleCatalogs";
    private static final String NEW_QUERY = "NewQuery";
    private static final String EXECUTE = "Execute";
    private static final String STOP = "Stop";
    private static final String EDIT_VARIABLES = "EditVariables";

    private final JPanel topPanel;

    private final JSplitPane splitPane;
    private final JTabbedPane tabEditor;
    private final JPanel panelCatalogs;
    private final JPanel panelStatus;
    private final JLabel labelMemory;
    private final JLabel labelCaret;
    private final JLabel labelVersion;

    private final JMenuItem openItem;
    private final JMenuItem saveItem;
    private final JMenuItem saveAsItem;
    private final JMenuItem exitItem;
    private final JMenu recentFiles;
    private final JComboBox<IOutputExtension> comboOutput;
    private final JComboBox<IOutputFormatExtension> comboFormat;
    private final JButton configOutputButton;

    private Consumer<String> openRecentFileConsumer;
    private Consumer<ViewAction> actionHandler;
    private boolean catalogsCollapsed;
    private int prevCatalogsDividerLocation;

    // CSOFF
    QueryeerView()
    // CSON
    {
        String title = "Queryeer IDE";
        setTitle(title);
        setLocationRelativeTo(null);
        getContentPane().setLayout(new BorderLayout(0, 0));
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
        labelCaret.setToolTipText("Caret position (Line, column, position)");

        labelVersion = new JLabel("", SwingConstants.CENTER);
        labelVersion.setBorder(new EtchedBorder(EtchedBorder.LOWERED));
        labelVersion.setPreferredSize(new Dimension(120, 20));
        labelVersion.setToolTipText("Click to check for new version.");

        // CSON
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

        menuBar.add(toolsMenu);

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
        toolBar.add(editVariablesAction)
                .setToolTipText("Edit parameters");

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
        comboFormat.addActionListener(l -> actionHandler.accept(ViewAction.FORMAT_CHANGED));

        toolBar.addSeparator();
        configOutputButton = new JButton("Output ", Constants.COG);
        configOutputButton.setToolTipText("Configure output");
        configOutputButton.addActionListener(e -> actionHandler.accept(ViewAction.CONFIG_OUTPUT));
        toolBar.add(configOutputButton);
        toolBar.add(comboOutput);

        toolBar.addSeparator();
        toolBar.add(new JLabel("Format "));
        toolBar.add(comboFormat);

        splitPane = new JSplitPane();
        splitPane.setDividerSize(3);
        getContentPane().add(splitPane, BorderLayout.CENTER);

        tabEditor = new JTabbedPane(SwingConstants.TOP);
        tabEditor.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        splitPane.setRightComponent(tabEditor);

        panelCatalogs = new JPanel();
        panelCatalogs.setLayout(new GridBagLayout());
        splitPane.setLeftComponent(new JScrollPane(panelCatalogs));

        setIconImages(Constants.APPLICATION_ICONS);

        // Switch formats upon changing output
        comboOutput.addActionListener(l ->
        {
            IOutputExtension extension = (IOutputExtension) comboOutput.getSelectedItem();
            comboFormat.setEnabled(extension.supportsOutputFormats());
            configOutputButton.setEnabled(extension.getConfigurableClass() != null);
            actionHandler.accept(ViewAction.OUTPUT_CHANGED);
        });

        addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
                exitAction.actionPerformed(null);
            }
        });

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        // CSOFF
        setPreferredSize(new Dimension(1600, 800));
        // CSON
        setLocationByPlatform(true);
        pack();
    }

    void bindOutputExtensions(List<IOutputExtension> outputExtensions)
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

    // private void gotoLine()
    // {
    // GoToDialog dialog = new GoToDialog(this);
    // dialog.setMaxLineNumberAllowed(textArea.getLineCount());
    // dialog.setVisible(true);
    // int line = dialog.getLineNumber();
    // if (line>0) {
    // try {
    // textArea.setCaretPosition(textArea.getLineStartOffset(line-1));
    // } catch (BadLocationException ble) { // Never happens
    // UIManager.getLookAndFeel().provideErrorFeedback(textArea);
    // ble.printStackTrace();
    // }
    // }
    // }

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

    private final Action editVariablesAction = new AbstractAction(EDIT_VARIABLES, Constants.EDIT)
    {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            actionHandler.accept(ViewAction.EDIT_VARIABLES);
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

    JLabel getCaretLabel()
    {
        return labelCaret;
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

    JTabbedPane getEditorsTabbedPane()
    {
        return tabEditor;
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

    //
    // void setExecuteAction(Runnable executeRunnable)
    // {
    // this.executeRunnable = executeRunnable;
    // }
    //
    // void setNewQueryAction(Runnable newQueryRunnable)
    // {
    // this.newQueryRunnable = newQueryRunnable;
    // }
    //
    // void setOpenAction(Runnable openRunnable)
    // {
    // this.openRunnable = openRunnable;
    // }
    //
    // void setSaveAction(Runnable saveRunnable)
    // {
    // this.saveRunnable = saveRunnable;
    // }
    //
    // void setSaveAsAction(Runnable saveAsRunnable)
    // {
    // this.saveAsRunnable = saveAsRunnable;
    // }
    //
    // void setExitAction(Runnable exitRunnable)
    // {
    // this.exitRunnable = exitRunnable;
    // }
    //
    // void setToogleResultAction(Runnable toggleResultRunnable)
    // {
    // this.toggleResultRunnable = toggleResultRunnable;
    // }
    //
    // void setToggleCommentRunnable(Runnable toggleCommentRunnable)
    // {
    // this.toggleCommentRunnable = toggleCommentRunnable;
    // }
    //
    // void setEditVariablesRunnable(Runnable editVariablesRunnable)
    // {
    // this.editVariablesRunnable = editVariablesRunnable;
    // }
    //
    // void setCancelAction(Runnable cancelRunnable)
    // {
    // this.cancelRunnable = cancelRunnable;
    // }
    //
    // void setOutputChangedAction(Runnable outputChangedRunnable)
    // {
    // this.outputChangedRunnable = outputChangedRunnable;
    // }
    //
    // void setFormatChangedAction(Runnable formatChangedRunnable)
    // {
    // this.formatChangedRunnable = formatChangedRunnable;
    // }
    //
    // public void setConfigOutputAction(Runnable configOutputRunnable)
    // {
    // this.configOutputRunnable = configOutputRunnable;
    // }
    //
    void setOpenRecentFileConsumer(Consumer<String> openRecentFileConsumer)
    {
        this.openRecentFileConsumer = openRecentFileConsumer;
    }

    void setActionHandler(Consumer<QueryeerController.ViewAction> actionHandler)
    {
        this.actionHandler = actionHandler;
    }
}
