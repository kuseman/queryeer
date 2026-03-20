package se.kuseman.payloadbuilder.catalog.jdbc;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.queryeer.api.component.AnimatedIcon;
import com.queryeer.api.component.DialogUtils.ADialog;
import com.queryeer.api.component.IDialogFactory;
import com.queryeer.api.component.QueryeerTree.RegularNode;
import com.queryeer.api.extensions.visualization.graph.Graph;
import com.queryeer.api.service.IGraphVisualizationService;
import com.queryeer.api.service.IIconFactory;
import com.queryeer.api.service.IPayloadbuilderService;

import se.kuseman.payloadbuilder.catalog.jdbc.dialect.JdbcDialect;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.JdbcDialectProvider;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Catalog;
import se.kuseman.payloadbuilder.catalog.jdbc.monitor.ServerMonitorPanel;

/** Tree model for jdbc query engine showing connections and child nodes like databases etc. */
class JdbcConnectionsTreeModel implements RegularNode
{
    private final IPayloadbuilderService payloadbuilderService;
    private final JdbcConnectionsModel model;
    private final Icons icons;
    private final JdbcDialectProvider dialectProvider;
    private final Consumer<RegularNode> newQueryConsumer;
    private final CatalogCrawlService crawlService;
    private final IGraphVisualizationService graphVisualizationService;
    private final IIconFactory iconFactory;
    private final IDialogFactory dialogFactory;

    //@formatter:off
    JdbcConnectionsTreeModel(IPayloadbuilderService payloadbuilderService, JdbcConnectionsModel model, Icons icons, JdbcDialectProvider dialectProvider, Consumer<RegularNode> newQueryConsumer,
            CatalogCrawlService crawlService, IGraphVisualizationService graphVisualizationService, IIconFactory iconFactory, IDialogFactory dialogFactory)
    //@formatter:on
    {
        this.payloadbuilderService = requireNonNull(payloadbuilderService, "payloadbuilderService");
        this.model = requireNonNull(model, "model");
        this.icons = requireNonNull(icons, "icons");
        this.dialectProvider = requireNonNull(dialectProvider, "dialectProvider");
        this.newQueryConsumer = requireNonNull(newQueryConsumer, "newQueryConsumer");
        this.crawlService = requireNonNull(crawlService, "crawlService");
        this.graphVisualizationService = requireNonNull(graphVisualizationService, "graphVisualizationService");
        this.iconFactory = requireNonNull(iconFactory, "iconFactory");
        this.dialogFactory = requireNonNull(dialogFactory, "dialogFactory");
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

        ConnectionContext createState()
        {
            return new ConnectionContext(connection, jdbcDialect);
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
            if (jdbcDialect.getMonitorExtension() != null)
            {
                return asList(newQuery, serverMonitor);
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

        private Action serverMonitor = new AbstractAction("Show Server Monitor ...")
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                ConnectionContext ctx = createState();
                ServerMonitorPanel panel = new ServerMonitorPanel(ctx, iconFactory, dialogFactory);
                ADialog dialog = new ADialog((Frame) null, connection.getName() + " — Server Monitor", false);
                dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                dialog.addWindowListener(new WindowAdapter()
                {
                    @Override
                    public void windowClosed(WindowEvent ev)
                    {
                        panel.dispose();
                    }
                });
                dialog.setContentPane(panel);
                dialog.setSize(1000, 600);
                dialog.setLocationRelativeTo(null);
                dialog.setVisible(true);
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

        ConnectionContext createState()
        {
            ConnectionContext state = new ConnectionContext(connectionNode.connection, jdbcDialect, database);
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
            return asList(newQuery, importData, schemaDiagram, routineCallGraph);
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

        private Action importData = new AbstractAction("Import Data ...")
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                JdbcDataImporterDialog dialog = new JdbcDataImporterDialog(payloadbuilderService, JdbcConnectionsTreeModel.this.model, connectionNode.connection, database, connectionNode.jdbcDialect);
                dialog.setModal(true);
                dialog.setVisible(true);
            }
        };

        private Action schemaDiagram = new AbstractAction("Show Schema Diagram ...")
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                JdbcQueryEngine.EXECUTOR.execute(() ->
                {
                    ConnectionContext connectionContext = new ConnectionContext(connectionNode.connection, jdbcDialect, database);
                    Catalog catalog = crawlService.getCatalog(connectionContext, database);
                    if (catalog != null)
                    {
                        Graph graph = DatabaseSchemaGraph.buildGraph(database, catalog);
                        graphVisualizationService.showGraph(graph);
                        return;
                    }

                    // Catalog is still loading — show a non-blocking loading dialog with a spinner
                    ADialog[] loadingDialog = new ADialog[1];
                    SwingUtilities.invokeLater(() ->
                    {
                        ADialog dlg = new ADialog((Frame) null, "Schema Diagram", false);
                        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 12));
                        panel.add(new JLabel(AnimatedIcon.createSmallSpinner()));
                        panel.add(new JLabel("Loading schema, please wait…"));
                        dlg.setContentPane(panel);
                        dlg.pack();
                        dlg.setLocationRelativeTo(null);
                        dlg.setVisible(true);
                        loadingDialog[0] = dlg;
                    });

                    // Poll until catalog is ready
                    try
                    {
                        while (catalog == null)
                        {
                            Thread.sleep(300);
                            catalog = crawlService.getCatalog(connectionContext, database);
                        }
                    }
                    catch (InterruptedException ex)
                    {
                        Thread.currentThread()
                                .interrupt();
                    }
                    finally
                    {
                        SwingUtilities.invokeLater(() ->
                        {
                            if (loadingDialog[0] != null)
                            {
                                loadingDialog[0].dispose();
                            }
                        });
                    }

                    if (catalog != null)
                    {
                        Graph graph = DatabaseSchemaGraph.buildGraph(database, catalog);
                        graphVisualizationService.showGraph(graph);
                    }
                });
            }
        };

        private Action routineCallGraph = new AbstractAction("Show Routine Call Graph ...")
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                JdbcQueryEngine.EXECUTOR.execute(() ->
                {
                    ConnectionContext connectionContext = new ConnectionContext(connectionNode.connection, jdbcDialect, database);
                    Catalog catalog = crawlService.getCatalog(connectionContext, database);

                    if (catalog == null)
                    {
                        // Catalog is still loading — show a non-blocking loading dialog with a spinner
                        ADialog[] loadingDialog = new ADialog[1];
                        SwingUtilities.invokeLater(() ->
                        {
                            ADialog dlg = new ADialog((Frame) null, "Routine Call Graph", false);
                            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 12));
                            panel.add(new JLabel(AnimatedIcon.createSmallSpinner()));
                            panel.add(new JLabel("Loading schema, please wait…"));
                            dlg.setContentPane(panel);
                            dlg.pack();
                            dlg.setLocationRelativeTo(null);
                            dlg.setVisible(true);
                            loadingDialog[0] = dlg;
                        });

                        try
                        {
                            while (catalog == null)
                            {
                                Thread.sleep(300);
                                catalog = crawlService.getCatalog(connectionContext, database);
                            }
                        }
                        catch (InterruptedException ex)
                        {
                            Thread.currentThread()
                                    .interrupt();
                        }
                        finally
                        {
                            SwingUtilities.invokeLater(() ->
                            {
                                if (loadingDialog[0] != null)
                                {
                                    loadingDialog[0].dispose();
                                }
                            });
                        }
                    }

                    if (catalog != null)
                    {
                        final Catalog finalCatalog = catalog;
                        SwingUtilities.invokeLater(() ->
                        {
                            RoutineCallGraphDialog dialog = new RoutineCallGraphDialog(database, finalCatalog, jdbcDialect, connectionContext, graphVisualizationService);
                            dialog.setVisible(true);
                        });
                    }
                });
            }
        };
    }

}
