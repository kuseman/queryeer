package com.queryeer;

import static java.util.Objects.requireNonNull;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import com.queryeer.Config.QueryEngineAssociation;
import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.extensions.engine.IQueryEngine;

/** Configurable for Queryeer. Base settings for engines etc. */
class QueryeerConfigurable implements IConfigurable
{
    private static final LookAndFeelInfo SYSTEM = new LookAndFeelInfo("System Default", UIManager.getSystemLookAndFeelClassName());
    private final Config config;
    private final List<Consumer<Boolean>> dirtyStateConsumers = new ArrayList<>();
    private Component component;

    private JTextField sharedFolderTextField;
    private File sharedFolder;
    private DefaultComboBoxModel<IQueryEngine> defaultQueryEngineModel = new DefaultComboBoxModel<>();
    private DefaultComboBoxModel<LookAndFeelInfo> lookAndFeelInfoModel = new DefaultComboBoxModel<>();
    private DefaultTableModel queryEngineAssociationsModel = new DefaultTableModel(new String[] { "File Extension", "Query Engine" }, 0)
    {
        @Override
        public Class<?> getColumnClass(int column)
        {
            return switch (column)
            {
                case 0 -> String.class;
                case 1 -> IQueryEngine.class;
                case 2 -> Boolean.class;
                default -> Object.class;
            };
        }

        @Override
        public boolean isCellEditable(int row, int column)
        {
            // Only extension is editable
            return column != 1;
        }
    };

    QueryeerConfigurable(Config config)
    {
        this.config = requireNonNull(config, "config");
    }

    @Override
    public Component getComponent()
    {
        if (component == null)
        {
            int y = 0;

            Insets insets = new Insets(0, 2, 2, 2);
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = y;
            gbc.insets = insets;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.NONE;

            JPanel panel = new JPanel(new GridBagLayout());
            panel.add(new JLabel("Default Query Engine:"), gbc);

            JComboBox<IQueryEngine> defaultQueryEngine = new JComboBox<>();
            defaultQueryEngine.setRenderer(new DefaultListCellRenderer()
            {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
                {
                    JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    IQueryEngine queryEngine = (IQueryEngine) value;
                    label.setText(queryEngine.getTitle());
                    label.setIcon(queryEngine.getIcon());
                    return label;
                }
            });
            defaultQueryEngine.setModel(defaultQueryEngineModel);

            gbc.gridx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;

            panel.add(defaultQueryEngine, gbc);

            gbc.gridx = 0;
            gbc.gridy = ++y;
            gbc.fill = GridBagConstraints.NONE;

            String lafToolTip = "Change look and feel. Might need a restart of application to make change take total effect.";
            panel.add(new JLabel("Look And Feel:")
            {
                {
                    setToolTipText(lafToolTip);
                }
            }, gbc);

            JComboBox<LookAndFeelInfo> lookAndFeel = new JComboBox<>();
            lookAndFeel.setToolTipText(lafToolTip);
            lookAndFeel.setRenderer(new DefaultListCellRenderer()
            {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
                {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    LookAndFeelInfo laf = (LookAndFeelInfo) value;
                    setText(laf.getName());
                    return this;
                }
            });

            lookAndFeelInfoModel.addElement(SYSTEM);
            List<LookAndFeelInfo> lafs = new ArrayList<>();
            lafs.addAll(Arrays.asList(UIManager.getInstalledLookAndFeels()));
            lafs.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName()));
            lookAndFeelInfoModel.addAll(lafs);

            lookAndFeel.setModel(lookAndFeelInfoModel);
            setLafFromConfig();

            lookAndFeel.addActionListener(l ->
            {
                LookAndFeelInfo laf = (LookAndFeelInfo) lookAndFeel.getSelectedItem();
                try
                {
                    boolean dirty = !Objects.equals(config.getLookAndFeelClassName(), laf.getClassName());
                    notifyDirty(dirty);

                    UIManager.setLookAndFeel(laf.getClassName());
                    Window[] windows = Window.getWindows();
                    for (Window window : windows)
                    {
                        SwingUtilities.updateComponentTreeUI(window);
                        window.pack();
                    }
                }
                catch (Exception e)
                {
                    JOptionPane.showMessageDialog(panel, "Error setting Look And Feel: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            });

            gbc.gridx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;

            panel.add(lookAndFeel, gbc);

            JLabel openNewFilesLastLabel = new JLabel("Open New Files Last:");
            openNewFilesLastLabel.setToolTipText("Should new files be opened last or after current");

            gbc.gridx = 0;
            gbc.gridy = ++y;
            gbc.fill = GridBagConstraints.NONE;

            panel.add(openNewFilesLastLabel, gbc);

            JCheckBox openNewFilesLast = new JCheckBox();
            openNewFilesLast.setToolTipText(openNewFilesLastLabel.getToolTipText());

            gbc.gridx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;

            panel.add(openNewFilesLast, gbc);

            String sharedFolderToolTip = "<html>" + """
                    Location of shared folder. This can be set to keep location after upgrades.
                    Default is JVM property <b>shared</b>
                    </html>
                    """;
            sharedFolderTextField = new JTextField();
            sharedFolderTextField.setColumns(20);
            sharedFolderTextField.setToolTipText(sharedFolderToolTip);
            sharedFolderTextField.setEditable(false);
            sharedFolderTextField.setText(config.getSharedFolderPath());

            JButton setSharedFolder = new JButton("...");
            setSharedFolder.setToolTipText(sharedFolderToolTip);
            setSharedFolder.addActionListener(l ->
            {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogType(JFileChooser.OPEN_DIALOG);
                if (config.getLastOpenPath() != null)
                {
                    fileChooser.setCurrentDirectory(new File(config.getLastOpenPath()));
                }
                fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                fileChooser.setMultiSelectionEnabled(false);

                Window activeWindow = javax.swing.FocusManager.getCurrentManager()
                        .getActiveWindow();

                if (fileChooser.showOpenDialog(activeWindow) == JFileChooser.APPROVE_OPTION)
                {
                    this.sharedFolder = fileChooser.getSelectedFile();
                    sharedFolderTextField.setText(this.sharedFolder.getAbsolutePath());
                    boolean dirty = !Objects.equals(config.getSharedFolderPath(), this.sharedFolder.getAbsolutePath());
                    notifyDirty(dirty);
                }
            });

            JButton clearSharedFolder = new JButton("X");
            clearSharedFolder.setToolTipText("Clear shared folder");
            clearSharedFolder.addActionListener(l ->
            {
                this.sharedFolder = null;
                sharedFolderTextField.setText("");
                boolean dirty = !Objects.equals(config.getSharedFolderPath(), "");
                notifyDirty(dirty);
            });

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.anchor = GridBagConstraints.WEST;

            JPanel sharedFolderPanel = new JPanel(new GridBagLayout());
            sharedFolderPanel.add(setSharedFolder, gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 1;
            gbc.gridy = 0;
            gbc.weightx = 1.0d;
            gbc.weighty = 1.0d;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.WEST;

            sharedFolderPanel.add(sharedFolderTextField, gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 2;
            gbc.gridy = 0;
            gbc.anchor = GridBagConstraints.WEST;
            sharedFolderPanel.add(clearSharedFolder, gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = ++y;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.NONE;

            panel.add(new JLabel("Shared Folder:")
            {
                {
                    setToolTipText(sharedFolderToolTip);
                }
            }, gbc);

            gbc.gridx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;

            panel.add(sharedFolderPanel, gbc);

            gbc.gridx = 0;
            gbc.gridy = ++y;
            gbc.fill = GridBagConstraints.NONE;

            panel.add(new JLabel("Query Engine Associations:"), gbc);

            JTable queryEngineAssociations = new JTable();
            queryEngineAssociations.setRowHeight(queryEngineAssociations.getRowHeight() + 2);
            queryEngineAssociations.putClientProperty("terminateEditOnFocusLost", true);

            queryEngineAssociations.setDefaultRenderer(IQueryEngine.class, new DefaultTableCellRenderer()
            {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
                {
                    JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    IQueryEngine queryEngine = (IQueryEngine) value;
                    if (queryEngine != null)
                    {
                        label.setText(queryEngine.getTitle());
                        label.setIcon(queryEngine.getIcon());
                    }
                    return label;
                }
            });

            queryEngineAssociations.setModel(queryEngineAssociationsModel);

            gbc.gridx = 0;
            gbc.gridy = ++y;
            gbc.gridwidth = 2;
            gbc.weightx = 1.0d;
            gbc.weighty = 1.0d;
            gbc.fill = GridBagConstraints.BOTH;

            panel.add(new JScrollPane(queryEngineAssociations), gbc);

            openNewFilesLast.setSelected(config.isOpenNewFilesLast());

            openNewFilesLast.addActionListener(l ->
            {
                boolean dirty = config.isOpenNewFilesLast() != openNewFilesLast.isSelected();
                config.setOpenNewFilesLast(openNewFilesLast.isSelected());
                notifyDirty(dirty);
            });

            for (IQueryEngine engine : config.getEngines())
            {
                defaultQueryEngineModel.addElement(engine);
                if (engine == config.getDefaultQueryEngine())
                {
                    defaultQueryEngineModel.setSelectedItem(engine);
                }
            }

            for (QueryEngineAssociation ass : config.getQueryEngineAssociations())
            {
                queryEngineAssociationsModel.addRow(new Object[] { ass.getExtension(), ass.getEngine() });
            }

            // Add listeners after we populated the models
            defaultQueryEngine.addItemListener(l ->
            {
                IQueryEngine engine = (IQueryEngine) l.getItem();
                boolean dirty = engine != config.getDefaultQueryEngine();
                notifyDirty(dirty);
            });

            queryEngineAssociationsModel.addTableModelListener(new TableModelListener()
            {
                @Override
                public void tableChanged(TableModelEvent e)
                {
                    // Only listen for update changes on extension/enabled columns
                    if (e.getType() != TableModelEvent.UPDATE
                            || e.getColumn() == 1)
                    {
                        return;
                    }

                    int row = e.getFirstRow();
                    QueryEngineAssociation association = config.getQueryEngineAssociations()
                            .get(row);

                    boolean dirty = false;
                    Object tableValue = queryEngineAssociations.getValueAt(row, e.getColumn());
                    if (e.getColumn() == 0)
                    {
                        dirty = !Objects.equals(association.getExtension(), tableValue);
                    }

                    notifyDirty(dirty);
                }
            });

            component = panel;
        }
        return component;
    }

    @Override
    public boolean commitChanges()
    {
        config.setDefaultQueryEngine((IQueryEngine) defaultQueryEngineModel.getSelectedItem());
        config.setLookAndFeelClassName(((LookAndFeelInfo) lookAndFeelInfoModel.getSelectedItem()).getClassName());
        int rowCount = queryEngineAssociationsModel.getRowCount();
        for (int i = 0; i < rowCount; i++)
        {
            QueryEngineAssociation ass = config.getQueryEngineAssociations()
                    .get(i);
            ass.setExtension((String) queryEngineAssociationsModel.getValueAt(i, 0));
        }
        config.setSharedFolderPath(sharedFolder);
        config.save();
        return true;
    }

    @Override
    public void revertChanges()
    {
        sharedFolderTextField.setText(config.getSharedFolderPath());
        defaultQueryEngineModel.setSelectedItem(config.getDefaultQueryEngine());
        setLafFromConfig();
        int rowCount = queryEngineAssociationsModel.getRowCount();
        for (int i = 0; i < rowCount; i++)
        {
            QueryEngineAssociation ass = config.getQueryEngineAssociations()
                    .get(i);

            queryEngineAssociationsModel.setValueAt(ass.getExtension(), i, 0);
        }
    }

    private void setLafFromConfig()
    {
        String lookAndFeelClassName = Objects.toString(config.getLookAndFeelClassName(), "");
        int count = lookAndFeelInfoModel.getSize();
        for (int i = 0; i < count; i++)
        {
            if (lookAndFeelInfoModel.getElementAt(i)
                    .getClassName()
                    .equalsIgnoreCase(lookAndFeelClassName))
            {
                lookAndFeelInfoModel.setSelectedItem(lookAndFeelInfoModel.getElementAt(i));
                break;
            }
        }
    }

    @Override
    public String getTitle()
    {
        return "General";
    }

    @Override
    public String getLongTitle()
    {
        return "Queryeer General";
    }

    @Override
    public String groupName()
    {
        return "Queryeer";
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

    private void notifyDirty(boolean dirty)
    {
        for (Consumer<Boolean> consumer : dirtyStateConsumers)
        {
            consumer.accept(dirty);
        }
    }
}
