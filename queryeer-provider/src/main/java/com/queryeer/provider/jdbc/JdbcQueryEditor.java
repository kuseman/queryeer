package com.queryeer.provider.jdbc;

import static java.util.Objects.requireNonNull;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.event.ListDataListener;

import com.queryeer.api.component.IIconProvider;
import com.queryeer.api.component.IIconProvider.Provider;
import com.queryeer.api.component.IQueryEditorComponent;
import com.queryeer.api.component.ISyntaxTextEditor;
import com.queryeer.jdbc.IJdbcConnection;
import com.queryeer.jdbc.IJdbcConnectionListModel;
import com.queryeer.jdbc.IJdbcDatabaseListModel;
import com.queryeer.jdbc.IJdbcDatabaseListModelFactory;

/** Editor component for {@link JdbcQueryProvider} */
class JdbcQueryEditor extends JPanel implements IQueryEditorComponent
{
    private JdbcQueryFileState state;
    private final ISyntaxTextEditor textEditor;

    private final JToolBar toolbar = new JToolBar();
    private final DefaultComboBoxModel<IJdbcConnection> connectionsModel;
    private final DatabasesSelectionModel databasesModel;
    private final IJdbcDatabaseListModelFactory databaseModelFactory;

    JdbcQueryEditor(IIconProvider iconProvider, ISyntaxTextEditor textEditor, IJdbcConnectionListModel connectionsListModel, IJdbcDatabaseListModelFactory databaseModelFactory)
    {
        this.textEditor = requireNonNull(textEditor, "textEditor");
        this.databaseModelFactory = requireNonNull(databaseModelFactory, "databaseModelFactory");

        setLayout(new BorderLayout());

        connectionsModel = new ConnectionsSelectionModel(connectionsListModel);
        databasesModel = new DatabasesSelectionModel();

        JComboBox<IJdbcConnection> comboConnections = new JComboBox<>();
        comboConnections.setModel(connectionsModel);
        comboConnections.setMaximumSize(new Dimension(200, 20));

        JComboBox<IJdbcDatabaseListModel.Database> comboDatabases = new JComboBox<>();
        comboDatabases.setModel(databasesModel);
        comboDatabases.setMaximumSize(new Dimension(200, 20));
        comboDatabases.setRenderer(new DefaultListCellRenderer()
        {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
            {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value != null)
                {
                    IJdbcDatabaseListModel.Database database = (IJdbcDatabaseListModel.Database) value;
                    setText(database.getName());
                }
                return this;
            }
        });

        toolbar.setRollover(true);
        toolbar.setFloatable(false);
        toolbar.add(new JLabel("Connection: "));
        toolbar.add(comboConnections);
        toolbar.addSeparator();
        toolbar.add(new JLabel("Database: "));
        toolbar.add(comboDatabases);
        toolbar.add(new AbstractAction("Reload", iconProvider.getIcon(Provider.FontAwesome, "REFRESH"))
        {
            {
                putValue(TOOL_TIP_TEXT_KEY, "Reload databases for selected connection");
            }

            @Override
            public void actionPerformed(ActionEvent e)
            {
                String selected = state.getSelectedDatabase() != null ? state.getSelectedDatabase()
                        .getName()
                        : null;
                databasesModel.model.reload();
                // Select first or previously selected after reload
                if (selected == null
                        && databasesModel.getSize() > 0)
                {
                    databasesModel.setSelectedItem(databasesModel.getElementAt(0));
                }
                else
                {
                    state.setSelectedDatabase(selected);
                }
            }
        })
                .setHideActionText(true);

        add(toolbar, BorderLayout.NORTH);
        add(textEditor.getComponent(), BorderLayout.CENTER);
    }

    void setState(JdbcQueryFileState state)
    {
        this.state = state;
    }

    ISyntaxTextEditor getTextEditor()
    {
        return textEditor;
    }

    DatabasesSelectionModel getDatabasesModel()
    {
        return databasesModel;
    }

    @Override
    public Component getComponent()
    {
        if (connectionsModel.getSelectedItem() == null
                && connectionsModel.getSize() > 0)
        {
            connectionsModel.setSelectedItem(connectionsModel.getElementAt(0));
        }
        return this;
    }

    private class ConnectionsSelectionModel extends DefaultComboBoxModel<IJdbcConnection>
    {
        private final IJdbcConnectionListModel connectionsListModel;

        ConnectionsSelectionModel(IJdbcConnectionListModel connectionsListModel)
        {
            this.connectionsListModel = connectionsListModel;
        }

        @Override
        public Object getSelectedItem()
        {
            return state.getSelectedJdbcConnection();
        }

        @Override
        public void setSelectedItem(Object connection)
        {
            // Guard against weird values sent in
            if (connection != null
                    && !(connection instanceof IJdbcConnection))
            {
                return;
            }

            IJdbcConnection selectedJdbcConnection = state.getSelectedJdbcConnection();
            if ((selectedJdbcConnection != null
                    && !selectedJdbcConnection.equals(connection))
                    || selectedJdbcConnection == null
                            && connection != null)
            {
                state.setSelectedJdbcConnection((IJdbcConnection) connection);
                fireContentsChanged(this, -1, -1);

                // Fetch database model and update UI
                if (databasesModel != null)
                {
                    IJdbcDatabaseListModel databaseListModel = databaseModelFactory.getModel((IJdbcConnection) connection);
                    databasesModel.setModel(databaseListModel);
                }
            }
        }

        @Override
        public int getSize()
        {
            return connectionsListModel.getSize();
        }

        @Override
        public IJdbcConnection getElementAt(int index)
        {
            return connectionsListModel.getElementAt(index);
        }

        @Override
        public void addListDataListener(ListDataListener l)
        {
            super.addListDataListener(l);
            connectionsListModel.addListDataListener(l);
        }

        @Override
        public void removeListDataListener(ListDataListener l)
        {
            super.removeListDataListener(l);
            connectionsListModel.removeListDataListener(l);
        }
    }

    class DatabasesSelectionModel extends DefaultComboBoxModel<IJdbcDatabaseListModel.Database>
    {
        private IJdbcDatabaseListModel model;

        @Override
        public Object getSelectedItem()
        {
            return state.getSelectedDatabase();
        }

        @Override
        public void setSelectedItem(Object database)
        {
            // Guard against weird values sent in
            if (database != null
                    && !(database instanceof IJdbcDatabaseListModel.Database))
            {
                return;
            }

            IJdbcDatabaseListModel.Database selectedDatabase = state.getSelectedDatabase();
            if ((selectedDatabase != null
                    && !selectedDatabase.equals(database))
                    || selectedDatabase == null
                            && database != null)
            {
                state.setSelectedDatabase((IJdbcDatabaseListModel.Database) database);
                fireContentsChanged(this, -1, -1);
            }
        }

        @Override
        public int getSize()
        {
            return model != null ? model.getSize()
                    : 0;
        }

        @Override
        public IJdbcDatabaseListModel.Database getElementAt(int index)
        {
            return model != null ? model.getElementAt(index)
                    : null;
        }

        @Override
        public void addListDataListener(ListDataListener l)
        {
            super.addListDataListener(l);
        }

        @Override
        public void removeListDataListener(ListDataListener l)
        {
            super.removeListDataListener(l);
        }

        void setModel(IJdbcDatabaseListModel model)
        {
            requireNonNull(model, "model");

            for (ListDataListener l : listenerList.getListeners(ListDataListener.class))
            {
                // Remove old listeners
                if (this.model != null)
                {
                    this.model.removeListDataListener(l);
                }
                // Add new
                model.addListDataListener(null);
            }
            this.model = model;
            state.setSelectedDatabase((IJdbcDatabaseListModel.Database) null);
            fireContentsChanged(this, -1, -1);
        }
    }
}