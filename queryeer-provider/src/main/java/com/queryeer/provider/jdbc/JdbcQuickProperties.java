package com.queryeer.provider.jdbc;

import static java.util.Objects.requireNonNull;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import com.queryeer.api.service.Inject;
import com.queryeer.jdbc.IJdbcConnection;
import com.queryeer.jdbc.IJdbcConnectionListModel;
import com.queryeer.provider.jdbc.dialect.ITreeDialect;
import com.queryeer.provider.jdbc.dialect.ITreeDialectFactory;
import com.queryeer.provider.jdbc.dialect.ITreeDialectNode;

/** Quick properties for {@link JdbcQueryProvider} */
@Inject
class JdbcQuickProperties extends JPanel
{
    private final IJdbcConnectionListModel connectionsModel;
    private final ITreeDialectFactory dialectFactory;
    private final JTree connectionsTree = new JTree();
    private final JPopupMenu popupmenu = new JPopupMenu();

    JdbcQuickProperties(IJdbcConnectionListModel connectionsModel, ITreeDialectFactory dialectFactory)
    {
        this.connectionsModel = requireNonNull(connectionsModel, "connectionsModel");
        this.dialectFactory = requireNonNull(dialectFactory, "dialectFactory");
        init();
    }

    IJdbcConnectionListModel getConnectionsModel()
    {
        return connectionsModel;
    }

    private void init()
    {
        setLayout(new BorderLayout());

        connectionsTree.setRootVisible(false);
        connectionsTree.setShowsRootHandles(true);
        connectionsTree.getSelectionModel()
                .setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        connectionsTree.addTreeWillExpandListener(new JdbcTreeWillExpandListener());
        connectionsTree.addMouseListener(new TreeMouseListener());

        popupmenu.add(new JMenuItem(new AbstractAction("Refresh")
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                JPopupMenu popupmenu = (JPopupMenu) ((JMenuItem) e.getSource()).getParent();
                ATreeNode node = (ATreeNode) popupmenu.getClientProperty("treeNode");
                node.refresh();
            }
        }));

        JTabbedPane tabPane = new JTabbedPane();
        tabPane.addTab("Connections", connectionsTree);

        add(tabPane, BorderLayout.CENTER);

        populateNodes();
    }

    private void populateNodes()
    {
        // TODO: listened on connections changes
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        for (IJdbcConnection connection : connectionsModel.getConnections())
        {
            root.add(new ConnectionNode(connection, dialectFactory.getTreeDialect(connection.getType())));
        }
        ((DefaultTreeModel) connectionsTree.getModel()).setRoot(root);
    }

    /** Mouse listener for tree */
    private class TreeMouseListener extends MouseAdapter
    {
        @Override
        public void mouseClicked(MouseEvent e)
        {
            if (SwingUtilities.isRightMouseButton(e))
            {
                // int row = connectionsTree.getClosestRowForLocation(e.getX(), e.getY());
                TreePath path = connectionsTree.getPathForLocation(e.getX(), e.getY());
                if (path == null
                        || path.getLastPathComponent() == null)
                {
                    return;
                }
                ATreeNode node = (ATreeNode) path.getLastPathComponent();
                popupmenu.putClientProperty("treeNode", node);
                popupmenu.show(e.getComponent(), e.getX(), e.getY());
            }
        }
    }

    /** Listener that loads nodes in the tree */
    private class JdbcTreeWillExpandListener implements TreeWillExpandListener
    {
        @Override
        public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException
        {
            TreePath path = event.getPath();
            ATreeNode node = (ATreeNode) path.getLastPathComponent();
            node.expand();
        }

        @Override
        public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException
        {
        }
    }

    /** Base class for nodes */
    private abstract class ATreeNode extends DefaultMutableTreeNode
    {
        protected boolean expanding = false;
        protected boolean childrenLoaded = false;

        void expand()
        {
            if (childrenLoaded)
            {
                return;
            }

            expanding = true;
            ((DefaultTreeModel) connectionsTree.getModel()).nodeChanged(this);
            try
            {
                expandNode();
            }
            finally
            {
                expanding = false;
                childrenLoaded = true;
                ((DefaultTreeModel) connectionsTree.getModel()).nodeStructureChanged(this);
                connectionsTree.expandPath(new TreePath(getPath()));
            }
        }

        void expandNode()
        {

        }

        void refresh()
        {
            childrenLoaded = false;
            removeAllChildren();
            expand();
        }
    }

    private class ConnectionNode extends ATreeNode
    {
        private final IJdbcConnection connection;
        private final ITreeDialect dialect;

        ConnectionNode(IJdbcConnection connection, ITreeDialect dialect)
        {
            this.connection = connection;
            this.dialect = dialect;
        }

        @Override
        public boolean isLeaf()
        {
            return false;
        }

        @Override
        void expandNode()
        {
            for (ITreeDialectNode child : dialect.getTreeNodes(connection))
            {
                add(new DialectNode(child));
            }
        }

        @Override
        public String toString()
        {
            return connection.getName() + " (" + connection.getType() + ")";
        }
    }

    private class DialectNode extends ATreeNode
    {
        private final ITreeDialectNode node;

        DialectNode(ITreeDialectNode node)
        {
            this.node = node;
        }

        @Override
        public boolean isLeaf()
        {
            return node.isLeaf();
        }

        @Override
        void expandNode()
        {
            for (ITreeDialectNode child : node.children())
            {
                add(new DialectNode(child));
            }
        }

        @Override
        public String toString()
        {
            return String.format("%s%s", node.title(), expanding ? " (Loading ...)"
                    : "");
        }
    }
}
