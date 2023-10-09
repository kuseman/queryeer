package com.queryeer;

import static java.util.Objects.requireNonNull;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
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
    private final Config config;
    private final List<Consumer<Boolean>> dirtyStateConsumers = new ArrayList<>();
    private Component component;

    private DefaultComboBoxModel<IQueryEngine> defaultQueryEngineModel = new DefaultComboBoxModel<>();
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
            JPanel panel = new JPanel(new GridBagLayout());

            panel.add(new JLabel("Default Query Engine:"), new GridBagConstraints(0, 0, 1, 1, 0.0d, 0.0d, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.NONE, new Insets(0, 2, 2, 2), 0, 0));

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

            panel.add(defaultQueryEngine, new GridBagConstraints(1, 0, 1, 1, 1.0d, 0.0d, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 2, 2), 0, 0));

            panel.add(new JLabel("Query Engine Associations:"),
                    new GridBagConstraints(0, 1, 2, 1, 1.0d, 0.0d, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.NONE, new Insets(0, 2, 2, 2), 0, 0));

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

            panel.add(new JScrollPane(queryEngineAssociations),
                    new GridBagConstraints(0, 2, 2, 1, 1.0d, 1.0d, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.BOTH, new Insets(0, 2, 2, 2), 0, 0));

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
    public void commitChanges()
    {
        config.setDefaultQueryEngine((IQueryEngine) defaultQueryEngineModel.getSelectedItem());
        int rowCount = queryEngineAssociationsModel.getRowCount();
        for (int i = 0; i < rowCount; i++)
        {
            QueryEngineAssociation ass = config.getQueryEngineAssociations()
                    .get(i);
            ass.setExtension((String) queryEngineAssociationsModel.getValueAt(i, 0));
        }
        config.save();
    }

    @Override
    public void revertChanges()
    {
        defaultQueryEngineModel.setSelectedItem(config.getDefaultQueryEngine());
        int rowCount = queryEngineAssociationsModel.getRowCount();
        for (int i = 0; i < rowCount; i++)
        {
            QueryEngineAssociation ass = config.getQueryEngineAssociations()
                    .get(i);

            queryEngineAssociationsModel.setValueAt(ass.getExtension(), i, 0);
        }
    }

    @Override
    public String getTitle()
    {
        return "General";
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
