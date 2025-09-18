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
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;

import com.queryeer.api.component.QueryeerTree.RegularNode;

import se.kuseman.payloadbuilder.catalog.jdbc.dialect.DialectProvider;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.JdbcDialect;

/** Tree model for jdbc query engine showing connections and child nodes like databases etc. */
class JdbcConnectionsTreeModel implements RegularNode
{
    private final JdbcConnectionsModel model;
    private final Icons icons;
    private final DialectProvider dialectProvider;
    private final Consumer<RegularNode> newQueryConsumer;

    JdbcConnectionsTreeModel(JdbcConnectionsModel model, Icons icons, DialectProvider dialectProvider, Consumer<RegularNode> newQueryConsumer)
    {
        this.model = requireNonNull(model, "model");
        this.icons = requireNonNull(icons, "icons");
        this.dialectProvider = requireNonNull(dialectProvider, "dialectProvider");
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
        private final JdbcDialect jdbcDialect;

        ConnectionNode(JdbcConnection connection)
        {
            this.connection = connection;
            this.jdbcDialect = dialectProvider.getDialect(connection.getJdbcURL());
        }

        ConnectionState createState()
        {
            return new ConnectionState(connection, jdbcDialect);
        }

        JdbcConnection getJdbcConnection()
        {
            return connection;
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
            children.add(new DatabasesNode(this));
            children.addAll(jdbcDialect.getTreeNodeSupplier()
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

        @Override
        public int hashCode()
        {
            return connection.hashCode();
        }

        @Override
        public boolean equals(Object obj)
        {
            if (obj instanceof ConnectionNode that)
            {
                return connection == that.connection;
            }
            return false;
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
        private final ConnectionNode connectionNode;
        private final JdbcDialect jdbcDialect;

        DatabasesNode(ConnectionNode connectionNode)
        {
            this.connectionNode = connectionNode;
            this.jdbcDialect = connectionNode.jdbcDialect;
        }

        JdbcConnection getJdbcConnection()
        {
            return connectionNode.connection;
        }

        @Override
        public String getTitle()
        {
            return jdbcDialect.usesSchemaAsDatabase() ? "Schemas"
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
            List<String> databases = model.getDatabases(connectionNode.connection, true, true);
            return databases.stream()
                    .map(d -> new DatabaseNode(connectionNode, d))
                    .collect(toList());
        }

        @Override
        public int hashCode()
        {
            return connectionNode.hashCode();
        }

        @Override
        public boolean equals(Object obj)
        {
            if (obj instanceof DatabasesNode that)
            {
                return connectionNode.equals(that.connectionNode);
            }
            return false;
        }
    }

    /** Database node. (TODO: Checkbox to be able to multi query of databases). */
    class DatabaseNode implements RegularNode
    {
        private final ConnectionNode connectionNode;
        private final JdbcDialect jdbcDialect;
        private final String database;
        // private boolean checked;

        DatabaseNode(ConnectionNode connectionNode, String database)
        {
            this.connectionNode = connectionNode;
            this.jdbcDialect = connectionNode.jdbcDialect;
            this.database = database;
        }

        ConnectionState createState()
        {
            ConnectionState state = new ConnectionState(connectionNode.connection, jdbcDialect, database);
            return state;
        }

        JdbcConnection getJdbcConnection()
        {
            return connectionNode.connection;
        }

        String getDatabase()
        {
            return database;
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
            return jdbcDialect.getTreeNodeSupplier()
                    .getDatabaseMetaDataNodes(connectionNode.connection, database, new SqlConnectionSupplier()
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

        @Override
        public int hashCode()
        {
            return Objects.hash(connectionNode, database);
        }

        @Override
        public boolean equals(Object obj)
        {
            if (obj instanceof DatabaseNode that)
            {
                return connectionNode.equals(that.connectionNode)
                        && database.equals(that.database);
            }
            return false;
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
