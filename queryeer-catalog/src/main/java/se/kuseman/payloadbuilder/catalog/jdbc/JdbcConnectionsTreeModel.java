package se.kuseman.payloadbuilder.catalog.jdbc;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.awt.event.ActionEvent;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;

import com.queryeer.api.component.QueryeerTree.RegularNode;

import se.kuseman.payloadbuilder.catalog.jdbc.dialect.DatabaseProvider;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.JdbcDatabase;

/** Tree model for jdbc query engine showing connections and child nodes like databases etc. */
class JdbcConnectionsTreeModel implements RegularNode
{
    private final JdbcConnectionsModel model;
    private final Icons icons;
    private final DatabaseProvider databaseProvider;
    private final Consumer<RegularNode> newQueryConsumer;

    JdbcConnectionsTreeModel(JdbcConnectionsModel model, Icons icons, DatabaseProvider databaseProvider, Consumer<RegularNode> newQueryConsumer)
    {
        this.model = requireNonNull(model, "model");
        this.icons = requireNonNull(icons, "icons");
        this.databaseProvider = requireNonNull(databaseProvider, "databaseProvider");
        this.newQueryConsumer = requireNonNull(newQueryConsumer, "newQueryConsumer");
    }

    @Override
    public String getTitle()
    {
        return null;
    }

    @Override
    public boolean isLeaf()
    {
        return false;
    }

    @Override
    public List<RegularNode> loadChildren()
    {
        return model.getConnections()
                .stream()
                .map(ConnectionNode::new)
                .collect(toList());
    }

    /** Node representing a JDBC connection */
    class ConnectionNode implements RegularNode
    {
        private final JdbcConnection connection;
        private final JdbcDatabase jdbcDatabase;

        ConnectionNode(JdbcConnection connection)
        {
            this.connection = connection;
            this.jdbcDatabase = databaseProvider.getDatabase(connection.getJdbcURL());
        }

        ConnectionState createState()
        {
            return new ConnectionState(connection, jdbcDatabase);
        }

        @Override
        public String getTitle()
        {
            if (!connection.isEnabled())
            {
                return "<html><i>" + connection.getName() + "</i></html>";
            }
            return connection.getName();
        }

        @Override
        public Icon getIcon()
        {
            return icons.server;
        }

        @Override
        public Icon getStatusIcon()
        {
            if (!connection.isEnabled())
            {
                return icons.banIcon;
            }
            else if (connection.hasCredentials())
            {
                return null;
            }
            return icons.lock;
        }

        @Override
        public boolean isLeaf()
        {
            if (!connection.isEnabled())
            {
                return true;
            }
            return false;
        }

        @Override
        public boolean shouldLoadChildren()
        {
            if (!connection.isEnabled())
            {
                return false;
            }
            return model.prepare(connection, false);
        }

        @Override
        public List<RegularNode> loadChildren()
        {
            if (!connection.isEnabled())
            {
                return emptyList();
            }

            List<RegularNode> children = new ArrayList<>();
            children.add(new DatabasesNode(connection, jdbcDatabase));
            children.addAll(jdbcDatabase.getTreeNodeSupplier()
                    .getMetaDataNodes(connection, new SqlConnectionSupplier()
                    {
                        @Override
                        public Connection get(JdbcConnection connection) throws SQLException
                        {
                            return model.createConnection(connection);
                        }
                    }));
            return children;
        }

        @Override
        public List<Action> getContextMenuActions()
        {
            if (!connection.isEnabled())
            {
                return emptyList();
            }
            return asList(newQuery);
        }

        @Override
        public List<Action> getLinkActions()
        {
            if (!connection.isEnabled())
            {
                return emptyList();
            }
            return asList(newQuery);
        }

        private Action newQuery = new AbstractAction("New Query")
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                newQueryConsumer.accept(ConnectionNode.this);
            }
        };
    }

    /** Node representing a collection of databases */
    class DatabasesNode implements RegularNode
    {
        private final JdbcConnection connection;
        private final JdbcDatabase jdbcDatabase;

        DatabasesNode(JdbcConnection connection, JdbcDatabase jdbcDatabase)
        {
            this.connection = connection;
            this.jdbcDatabase = jdbcDatabase;
        }

        @Override
        public String getTitle()
        {
            return jdbcDatabase.usesSchemaAsDatabase() ? "Schemas"
                    : "Databases";
        }

        @Override
        public Icon getIcon()
        {
            return icons.folder_o;
        }

        @Override
        public boolean isLeaf()
        {
            return false;
        }

        @Override
        public List<RegularNode> loadChildren()
        {
            List<String> databases = model.getDatabases(connection, true, true);
            return databases.stream()
                    .map(d -> new DatabaseNode(connection, jdbcDatabase, d))
                    .collect(toList());
        }
    }

    /** Database node. (TODO: Checkbox to be able to multi query of databases). */
    class DatabaseNode implements RegularNode
    {
        private final JdbcConnection connection;
        private final JdbcDatabase jdbcDatabase;
        private final String database;
        // private boolean checked;

        DatabaseNode(JdbcConnection connection, JdbcDatabase jdbcDatabase, String database)
        {
            this.connection = connection;
            this.jdbcDatabase = jdbcDatabase;
            this.database = database;
        }

        ConnectionState createState()
        {
            ConnectionState state = new ConnectionState(connection, jdbcDatabase, database);
            return state;
        }

        @Override
        public String getTitle()
        {
            return database;
        }

        @Override
        public boolean isLeaf()
        {
            return false;
        }

        @Override
        public List<RegularNode> loadChildren()
        {
            return jdbcDatabase.getTreeNodeSupplier()
                    .getDatabaseMetaDataNodes(connection, database, new SqlConnectionSupplier()
                    {
                        @Override
                        public Connection get(JdbcConnection connection) throws SQLException
                        {
                            return model.createConnection(connection);
                        }
                    });
        }

        @Override
        public Icon getIcon()
        {
            return icons.database;
        }

        // @Override
        // public void setChecked(boolean checked)
        // {
        // this.checked = checked;
        // }
        //
        // @Override
        // public boolean isChecked()
        // {
        // return checked;
        // }

        @Override
        public List<Action> getContextMenuActions()
        {
            return asList(newQuery);
        }

        @Override
        public List<Action> getLinkActions()
        {
            return asList(newQuery);
        }

        private Action newQuery = new AbstractAction("New Query")
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                newQueryConsumer.accept(DatabaseNode.this);
            }
        };
    }
}
