package com.queryeer.provider.jdbc;

import static java.util.Objects.requireNonNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

import javax.swing.event.SwingPropertyChangeSupport;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import com.queryeer.api.IQueryFileState;
import com.queryeer.api.component.IQueryEditorComponent;
import com.queryeer.api.component.ISyntaxTextEditor;
import com.queryeer.api.extensions.IQueryProvider;
import com.queryeer.jdbc.IJdbcConnection;
import com.queryeer.jdbc.IJdbcDatabaseListModel;
import com.queryeer.jdbc.IJdbcDatabaseListModel.Database;
import com.queryeer.provider.jdbc.JdbcQueryEditor.DatabasesSelectionModel;

/** File state for {@link JdbcQueryProvider} */
class JdbcQueryFileState implements IQueryFileState
{
    private static final String SELECTED_CONNECTION = "selectedConnection";
    private static final String SELECTED_DATABASE = "selectedDatabase";
    private final SwingPropertyChangeSupport pcs = new SwingPropertyChangeSupport(this, true);
    private final IQueryProvider queryProvider;
    private final ISyntaxTextEditor textEditor;
    private final TextEditorModleChangeListener textEditorModelChangeListener = new TextEditorModleChangeListener();
    private final JdbcQueryEditor queryEditor;
    // private final IJdbcConnectionListModel connectionsModel;
    // private final IJdbcDatabaseListModelFactory databaseModelFactory;
    // private final IIconProvider iconProvider;

    private IJdbcConnection selectedJdbcConnection;
    private IJdbcDatabaseListModel.Database selectedDatabase;
    private Connection sessionConnection;

    JdbcQueryFileState(IQueryProvider queryProvider, JdbcQueryEditor queryEditor)
    {
        this.queryProvider = requireNonNull(queryProvider, "queryProvider");
        this.queryEditor = requireNonNull(queryEditor, "queryEditor");
        this.textEditor = queryEditor.getTextEditor();
        this.textEditor.getModel()
                .addPropertyChangeListener(textEditorModelChangeListener);
        // this.connectionsModel = requireNonNull(connectionsModel, "connectionsModel");
        // this.databaseModelFactory = requireNonNull(databaseModelFactory, "databaseModelFactory");
        // this.iconProvider = requireNonNull(iconProvider, "iconProvider");
        // this.editorComponent = new QueryEditorComponent();
    }

    @Override
    public IQueryProvider getQueryProvider()
    {
        return queryProvider;
    }

    @Override
    public IQueryEditorComponent getQueryEditorComponent()
    {
        return queryEditor;
    }

    @Override
    public void load(String filename) throws Exception
    {
        String string = FileUtils.readFileToString(new File(filename), StandardCharsets.UTF_8);

        textEditor.getModel()
                .setText(string);
        textEditor.getModel()
                .resetBuffer();
    }

    @Override
    public void save(String filename) throws IOException
    {
        FileUtils.write(new File(filename), textEditor.getModel()
                .getText(), StandardCharsets.UTF_8);
        textEditor.getModel()
                .resetBuffer();
    }

    @Override
    public void close()
    {
        textEditor.getModel()
                .removePropertyChangeListener(textEditorModelChangeListener);
        textEditor.close();

        // Close session connection if any
        if (sessionConnection != null)
        {
            try
            {
                sessionConnection.close();
            }
            catch (SQLException e)
            {
                e.printStackTrace();
            }
            finally
            {
                sessionConnection = null;
            }
        }
    }

    @Override
    public String getSummary()
    {
        if (selectedJdbcConnection == null
                && selectedDatabase == null)
        {
            return null;
        }
        StringBuilder sb = new StringBuilder(selectedJdbcConnection.getName());
        if (selectedDatabase != null)
        {
            sb.append("/")
                    .append(selectedDatabase.getName());
        }
        return sb.toString();
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener)
    {
        pcs.addPropertyChangeListener(listener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener)
    {
        pcs.removePropertyChangeListener(listener);
    }

    ISyntaxTextEditor getTextEditor()
    {
        return textEditor;
    }

    IJdbcConnection getSelectedJdbcConnection()
    {
        return selectedJdbcConnection;
    }

    void setSelectedJdbcConnection(IJdbcConnection connection)
    {
        IJdbcConnection old = this.selectedJdbcConnection;
        if (!Objects.equals(old, connection))
        {
            this.selectedJdbcConnection = connection;
            pcs.firePropertyChange(SELECTED_CONNECTION, old, connection);
        }
    }

    IJdbcDatabaseListModel.Database getSelectedDatabase()
    {
        return selectedDatabase;
    }

    void setSelectedDatabase(String catalog)
    {
        DatabasesSelectionModel databasesModel = queryEditor.getDatabasesModel();

        int size = databasesModel.getSize();
        for (int i = 0; i < size; i++)
        {
            Database database = databasesModel.getElementAt(i);
            if (StringUtils.equalsIgnoreCase(catalog, database.getName()))
            {
                databasesModel.setSelectedItem(database);
                setSelectedDatabase(database);
                break;
            }
        }
    }

    void setSelectedDatabase(IJdbcDatabaseListModel.Database selectedDatabase)
    {
        IJdbcDatabaseListModel.Database old = this.selectedDatabase;
        if (!Objects.equals(old, selectedDatabase))
        {
            this.selectedDatabase = selectedDatabase;
            pcs.firePropertyChange(SELECTED_DATABASE, old, selectedDatabase);
        }
    }

    private class TextEditorModleChangeListener implements PropertyChangeListener
    {
        @Override
        public void propertyChange(PropertyChangeEvent evt)
        {
            if (ISyntaxTextEditor.TextEditorModel.DIRTY.equals(evt.getPropertyName()))
            {
                // Propagate dirty to state listeners
                pcs.firePropertyChange(IQueryFileState.DIRTY, (boolean) evt.getOldValue(), (boolean) evt.getNewValue());
            }
        }
    }
    //
    // private class QueryEditorComponent extends JPanel implements IQueryEditorComponent
    // {
    // private final JToolBar toolbar = new JToolBar();
    // private final DefaultComboBoxModel<IJdbcConnection> connectionsModel;
    // private final DatabasesSelectionModel databasesModel;
    //
    // QueryEditorComponent()
    // {
    // setLayout(new BorderLayout());
    //
    // connectionsModel = new ConnectionsSelectionModel();
    // databasesModel = new DatabasesSelectionModel();
    //
    // JComboBox<IJdbcConnection> comboConnections = new JComboBox<>();
    // comboConnections.setModel(connectionsModel);
    // comboConnections.setMaximumSize(new Dimension(200, 20));
    //
    // JComboBox<IJdbcDatabaseListModel.Database> comboDatabases = new JComboBox<>();
    // comboDatabases.setModel(databasesModel);
    // comboDatabases.setMaximumSize(new Dimension(200, 20));
    // comboDatabases.setRenderer(new DefaultListCellRenderer()
    // {
    // @Override
    // public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
    // {
    // super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    // if (value != null)
    // {
    // IJdbcDatabaseListModel.Database database = (IJdbcDatabaseListModel.Database) value;
    // setText(database.getName());
    // }
    // return this;
    // }
    // });
    //
    // toolbar.setRollover(true);
    // toolbar.setFloatable(false);
    // toolbar.add(new JLabel("Connection: "));
    // toolbar.add(comboConnections);
    // toolbar.addSeparator();
    // toolbar.add(new JLabel("Database: "));
    // toolbar.add(comboDatabases);
    // toolbar.add(new AbstractAction("Reload", iconProvider.getIcon(Provider.FontAwesome, "REFRESH"))
    // {
    // @Override
    // public void actionPerformed(ActionEvent e)
    // {
    // String selected = selectedDatabase != null ? selectedDatabase.getName()
    // : null;
    // databasesModel.model.reload();
    // // Select first or previously selected after reload
    // if (selected == null
    // && databasesModel.getSize() > 0)
    // {
    // databasesModel.setSelectedItem(databasesModel.getElementAt(0));
    // }
    // else
    // {
    // setSelectedDatabase(selected);
    // }
    // }
    // })
    // .setHideActionText(true);
    //
    // add(toolbar, BorderLayout.NORTH);
    // add(textEditor.getComponent(), BorderLayout.CENTER);
    // }
    //
    // @Override
    // public Component getComponent()
    // {
    // if (connectionsModel.getSelectedItem() == null
    // && connectionsModel.getSize() > 0)
    // {
    // connectionsModel.setSelectedItem(connectionsModel.getElementAt(0));
    // }
    // return this;
    // }
    // }
    //
    // private class ConnectionsSelectionModel extends DefaultComboBoxModel<IJdbcConnection>
    // {
    // @Override
    // public Object getSelectedItem()
    // {
    // return selectedJdbcConnection;
    // }
    //
    // @Override
    // public void setSelectedItem(Object connection)
    // {
    // // Guard against weird values sent in
    // if (connection != null
    // && !(connection instanceof IJdbcConnection))
    // {
    // return;
    // }
    //
    // if ((selectedJdbcConnection != null
    // && !selectedJdbcConnection.equals(connection))
    // || selectedJdbcConnection == null
    // && connection != null)
    // {
    // setSelectedJdbcConnection((IJdbcConnection) connection);
    // fireContentsChanged(this, -1, -1);
    //
    // // Fetch database model and update UI
    // if (JdbcQueryFileState.this.editorComponent.databasesModel != null)
    // {
    // IJdbcDatabaseListModel databaseListModel = JdbcQueryFileState.this.databaseModelFactory.getModel((IJdbcConnection) connection);
    // JdbcQueryFileState.this.editorComponent.databasesModel.setModel(databaseListModel);
    // }
    // }
    // }
    //
    // @Override
    // public int getSize()
    // {
    // return connectionsModel.getSize();
    // }
    //
    // @Override
    // public IJdbcConnection getElementAt(int index)
    // {
    // return connectionsModel.getElementAt(index);
    // }
    //
    // @Override
    // public void addListDataListener(ListDataListener l)
    // {
    // super.addListDataListener(l);
    // connectionsModel.addListDataListener(l);
    // }
    //
    // @Override
    // public void removeListDataListener(ListDataListener l)
    // {
    // super.removeListDataListener(l);
    // connectionsModel.removeListDataListener(l);
    // }
    // }
    //
    // private class DatabasesSelectionModel extends DefaultComboBoxModel<IJdbcDatabaseListModel.Database>
    // {
    // private IJdbcDatabaseListModel model;
    //
    // @Override
    // public Object getSelectedItem()
    // {
    // return selectedDatabase;
    // }
    //
    // @Override
    // public void setSelectedItem(Object database)
    // {
    // // Guard against weird values sent in
    // if (database != null
    // && !(database instanceof IJdbcDatabaseListModel.Database))
    // {
    // return;
    // }
    //
    // if ((selectedDatabase != null
    // && !selectedDatabase.equals(database))
    // || selectedDatabase == null
    // && database != null)
    // {
    // setSelectedDatabase((IJdbcDatabaseListModel.Database) database);
    // fireContentsChanged(this, -1, -1);
    // }
    // }
    //
    // @Override
    // public int getSize()
    // {
    // return model != null ? model.getSize()
    // : 0;
    // }
    //
    // @Override
    // public IJdbcDatabaseListModel.Database getElementAt(int index)
    // {
    // return model != null ? model.getElementAt(index)
    // : null;
    // }
    //
    // @Override
    // public void addListDataListener(ListDataListener l)
    // {
    // super.addListDataListener(l);
    // }
    //
    // @Override
    // public void removeListDataListener(ListDataListener l)
    // {
    // super.removeListDataListener(l);
    // }
    //
    // void setModel(IJdbcDatabaseListModel model)
    // {
    // requireNonNull(model, "model");
    //
    // for (ListDataListener l : listenerList.getListeners(ListDataListener.class))
    // {
    // // Remove old listeners
    // if (this.model != null)
    // {
    // this.model.removeListDataListener(l);
    // }
    // // Add new
    // model.addListDataListener(null);
    // }
    // this.model = model;
    // setSelectedDatabase((IJdbcDatabaseListModel.Database) null);
    // fireContentsChanged(this, -1, -1);
    // }
    // }
}
