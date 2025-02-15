package com.queryeer.api.component;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import javax.swing.AbstractAction;
import javax.swing.AbstractCellEditor;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreeCellEditor;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import com.queryeer.api.action.ActionUtils;
import com.queryeer.api.utils.OsUtils;

/** Extension of a {@link JTree} that icons etc. Supports checkboxes, lazy load of children etc. */
public class QueryeerTree extends JTree
{
    private static final Executor EXECUTOR = Executors.newCachedThreadPool();
    private static final Icon SPINNER = AnimatedIcon.createSmallSpinner();

    private final JPopupMenu contextPopup;
    private final JPopupMenu linkActionsPopup;

    private TreePath oldOverPath;
    private QueryeerTreeModel model;
    private boolean hyperLinksEnabled = false;
    private final int linkMask;

    public QueryeerTree(QueryeerTreeModel model)
    {
        super(model);
        this.model = model;
        setCellRenderer(new CheckBoxNodeRenderer());
        setCellEditor(new CheckBoxNodeEditor(this));
        setShowsRootHandles(true);
        setRootVisible(false);
        setEditable(true);
        addTreeWillExpandListener(new QueryeerTreeWillExpandListener(this, model));
        addMouseListener(mouseListener);
        addMouseMotionListener(mouseMotionListener);

        contextPopup = new JPopupMenu();
        linkActionsPopup = new JPopupMenu();
        this.linkMask = OsUtils.isMacOsx() ? InputEvent.META_DOWN_MASK
                : InputEvent.CTRL_DOWN_MASK;
    }

    /**
     * Toggles CTRL + mouse hover on nodes. If node has {@see RegularNode#getLinkActions()} those will appear in a context menu and the first one will be executed if node is clicked
     */
    // CSOFF
    public void setHyperLinksEnabled(boolean value)
    // CSON
    {
        this.hyperLinksEnabled = value;
    }

    @Override
    public void updateUI()
    {
        if (contextPopup != null)
        {
            SwingUtilities.updateComponentTreeUI(contextPopup);
            SwingUtilities.updateComponentTreeUI(linkActionsPopup);
        }
        super.updateUI();
    }

    @Override
    public void setModel(TreeModel model)
    {
        if (!(model instanceof QueryeerTreeModel))
        {
            throw new IllegalArgumentException("Model must be inherited from " + QueryeerTreeModel.class.getName());
        }
        this.model = (QueryeerTreeModel) model;
        super.setModel(model);
    }

    private MouseMotionAdapter mouseMotionListener = new MouseMotionAdapter()
    {
        @Override
        public void mouseMoved(MouseEvent e)
        {
            if (!hyperLinksEnabled)
            {
                return;
            }

            // Clear old over path if any
            if (!((e.getModifiersEx() & linkMask) == linkMask))
            {
                if (oldOverPath != null)
                {
                    QueryeerTreeNode node = (QueryeerTreeNode) oldOverPath.getLastPathComponent();
                    node.underline = false;
                    model.nodeChanged(node);
                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

                }

                linkActionsPopup.setVisible(false);
                return;
            }

            int selRow = getRowForLocation(e.getX(), e.getY());
            if (selRow < 0)
            {
                TreePath currentSelected = oldOverPath;
                oldOverPath = null;
                if (currentSelected != null)
                {
                    QueryeerTreeNode node = (QueryeerTreeNode) currentSelected.getLastPathComponent();
                    node.underline = false;
                    model.nodeChanged(node);
                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                }
                linkActionsPopup.setVisible(false);
            }
            else
            {
                TreePath selectedPath = getPathForRow(selRow);

                boolean newNode = !Objects.equals(oldOverPath, selectedPath);

                if (oldOverPath != null)
                {
                    QueryeerTreeNode node = (QueryeerTreeNode) oldOverPath.getLastPathComponent();
                    node.underline = false;
                    model.nodeChanged(node);
                }

                QueryeerTreeNode node = (QueryeerTreeNode) selectedPath.getLastPathComponent();

                List<Action> linkActions = node.node.getLinkActions();

                if (!linkActions.isEmpty())
                {
                    oldOverPath = selectedPath;
                    node.underline = true;
                    model.nodeChanged((QueryeerTreeNode) oldOverPath.getLastPathComponent());
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

                    if (!linkActionsPopup.isVisible()
                            || newNode)
                    {
                        linkActionsPopup.removeAll();
                        for (Action linkAction : linkActions)
                        {
                            JMenuItem item = ActionUtils.buildMenuItem(linkAction);
                            linkActionsPopup.add(item);
                        }

                        Rectangle pathBounds = QueryeerTree.this.getPathBounds(selectedPath);
                        linkActionsPopup.show(QueryeerTree.this, (int) pathBounds.getX() + 35, (int) (pathBounds.getY() + 14));
                    }
                }
                else
                {
                    linkActionsPopup.setVisible(false);
                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                }
            }
        }
    };

    private MouseAdapter mouseListener = new MouseAdapter()
    {
        @Override
        public void mouseClicked(MouseEvent e)
        {
            if (SwingUtilities.isRightMouseButton(e))
            {
                TreePath selPath = getPathForLocation(e.getX(), e.getY());
                if (selPath != null)
                {
                    QueryeerTreeNode node = (QueryeerTreeNode) selPath.getLastPathComponent();
                    RegularNode regNode = node.node;

                    List<Action> actions = new ArrayList<>(regNode.getContextMenuActions());

                    // Insert a refresh action at the bottom
                    if (!regNode.isLeaf())
                    {
                        actions.add(new AbstractAction("Refresh")
                        {
                            @Override
                            public void actionPerformed(ActionEvent e)
                            {
                                node.childrenLoaded = false;
                                collapsePath(selPath);
                                expandPath(selPath);
                            }
                        });
                    }

                    int row = getClosestRowForLocation(e.getX(), e.getY());
                    setSelectionRow(row);

                    contextPopup.removeAll();
                    for (Action action : actions)
                    {
                        JMenuItem item = ActionUtils.buildMenuItem(action);
                        contextPopup.add(item);
                    }

                    contextPopup.show(e.getComponent(), e.getX(), e.getY());
                }
            }
            else if (hyperLinksEnabled
                    && (e.getModifiersEx() & linkMask) == linkMask
                    && SwingUtilities.isLeftMouseButton(e))
            {
                TreePath path = getPathForLocation(e.getX(), e.getY());
                if (path != null)
                {
                    QueryeerTreeNode node = (QueryeerTreeNode) path.getLastPathComponent();
                    List<Action> linkActions = node.node.getLinkActions();
                    if (!linkActions.isEmpty())
                    {
                        linkActions.get(0)
                                .actionPerformed(new ActionEvent(QueryeerTree.this, 0, ""));
                    }
                }
            }
        }
    };

    /** Base node in the tree */
    public interface RegularNode
    {
        /** Return title of node */
        String getTitle();

        /** Returns true is this node is a leaf or not */
        boolean isLeaf();

        /** Return icon of node */
        default Icon getIcon()
        {
            return null;
        }

        /** Returns a status icon that is shown after the {@link #getIcon()} to indicate status like not connected etc. */
        default Icon getStatusIcon()
        {
            return null;
        }

        /**
         * Returns true if children should be loaded otherwise false. This method is called on EDT and can be used to fire dialogs etc. to allow for data input before loading children
         */
        default boolean shouldLoadChildren()
        {
            return true;
        }

        /** Loads children of this node. This method is called in a threaded fashion if {@link #shouldLoadChildren()} returned true */
        default List<RegularNode> loadChildren()
        {
            return emptyList();
        }

        /** Return context menu actions for this node */
        default List<Action> getContextMenuActions()
        {
            return emptyList();
        }

        /** Return link actions for this node. Only applicable if {@link QueryeerTree#setHyperLinksEnabled(boolean)} is true */
        default List<Action> getLinkActions()
        {
            return emptyList();
        }
    }

    /** Definition of a node that should have a check box */
    public interface CheckBoxNode extends RegularNode
    {
        /** Set checked state */
        void setChecked(boolean checked);

        /** Return checked state */
        boolean isChecked();
    }

    private static class QueryeerTreeWillExpandListener implements TreeWillExpandListener
    {
        private final JTree tree;
        private final QueryeerTreeModel model;

        QueryeerTreeWillExpandListener(JTree tree, QueryeerTreeModel model)
        {
            this.tree = tree;
            this.model = model;
        }

        @Override
        public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException
        {
            QueryeerTreeNode treeNode = (QueryeerTreeNode) event.getPath()
                    .getLastPathComponent();
            RegularNode node = treeNode.node;

            // Abort expand
            if (!node.shouldLoadChildren())
            {
                throw new ExpandVetoException(event);
            }

            // Node already has children loaded
            if (treeNode.childrenLoaded)
            {
                return;
            }

            treeNode.loading = true;
            model.fireTreeNodesChanged(event.getSource(), event.getPath()
                    .getPath(), null, null);
            Runnable r = () ->
            {
                try
                {
                    List<RegularNode> children = node.loadChildren();
                    treeNode.removeAllChildren();
                    for (RegularNode child : children)
                    {
                        treeNode.add(new QueryeerTreeNode(child));
                    }
                    treeNode.childrenLoaded = true;
                    SwingUtilities.invokeLater(() -> model.fireTreeStructureChanged(event.getSource(), event.getPath()
                            .getPath(), null, null));
                }
                catch (Exception e)
                {
                    // Mark that children aren't loaded in case of error to be able to reload again
                    treeNode.childrenLoaded = false;
                    SwingUtilities.invokeLater(() -> tree.collapsePath(event.getPath()));
                }
                finally
                {
                    treeNode.loading = false;
                    SwingUtilities.invokeLater(() -> model.fireTreeNodesChanged(event.getSource(), event.getPath()
                            .getPath(), null, null));
                }
            };

            EXECUTOR.execute(r);
        }

        @Override
        public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException
        {
        }
    }

    /** Renderer that renders a {@link JCheckBox} for nodes that implements {@link CheckBoxNode} */
    private class CheckBoxNodeRenderer extends DefaultTreeCellRenderer
    {
        private final JPanel checkBoxPanel = new JPanel(new GridBagLayout());
        private final JLabel labelIcon = new JLabel();
        private final JLabel labelStatusIcon = new JLabel();
        private final JLabel label = new JLabel();
        private final JCheckBox checkBox = new JCheckBox();

        private final Font regularFont;
        private final Font underLinefont;

        @SuppressWarnings("unchecked")
        CheckBoxNodeRenderer()
        {
            checkBoxPanel.setOpaque(false);
            labelIcon.setOpaque(false);
            labelStatusIcon.setOpaque(false);
            label.setBorder(new EmptyBorder(0, 1, 0, 3));
            labelIcon.setBorder(new EmptyBorder(0, 0, 0, 3));
            this.regularFont = label.getFont();
            @SuppressWarnings("rawtypes")
            Map attributes = regularFont.getAttributes();
            attributes.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
            this.underLinefont = regularFont.deriveFont(attributes);
        }

        /** We manage our own icons on separate JLables */
        @Override
        public Icon getIcon()
        {
            return null;
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus)
        {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

            QueryeerTreeNode treeNode = (QueryeerTreeNode) value;
            RegularNode node = treeNode.node;

            String title = node.getTitle();
            setText(title);

            GridBagConstraints gbc = new GridBagConstraints();
            checkBoxPanel.removeAll();
            gbc.gridx = 0;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.NONE;
            checkBoxPanel.add(checkBox, gbc);
            gbc.gridx = 1;
            checkBoxPanel.add(labelIcon, gbc);
            gbc.gridx = 2;
            checkBoxPanel.add(labelStatusIcon, gbc);
            gbc.gridx = 3;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0d;
            checkBoxPanel.add(this, gbc);

            setFont(treeNode.underline ? underLinefont
                    : regularFont);

            labelIcon.setIcon(node.getIcon());

            if (treeNode.loading)
            {
                labelStatusIcon.setIcon(SPINNER);
            }
            else
            {
                labelStatusIcon.setIcon(node.getStatusIcon());
                labelStatusIcon.setBorder(node.getStatusIcon() != null ? new EmptyBorder(0, 0, 0, 3)
                        : null);
            }

            checkBox.setVisible(false);
            if (node instanceof CheckBoxNode)
            {
                checkBox.setVisible(true);
                CheckBoxNode checkBoxNode = (CheckBoxNode) node;
                checkBox.setSelected(checkBoxNode.isChecked());
            }

            return checkBoxPanel;
        }
    }

    /** Editor that edits {@link CheckBoxNode}'s */
    private class CheckBoxNodeEditor extends AbstractCellEditor implements TreeCellEditor
    {
        private final CheckBoxNodeRenderer renderer = new CheckBoxNodeRenderer();
        private final JTree tree;

        private Object editorValue;

        public CheckBoxNodeEditor(JTree tree)
        {
            this.tree = tree;

            this.renderer.checkBox.addActionListener(new ActionListener()
            {
                @Override
                public void actionPerformed(ActionEvent e)
                {
                    if (editorValue != null)
                    {
                        ((CheckBoxNode) editorValue).setChecked(!((CheckBoxNode) editorValue).isChecked());
                    }
                }
            });
        }

        @Override
        public Object getCellEditorValue()
        {
            return null;
        }

        @Override
        public boolean isCellEditable(EventObject event)
        {
            if (event instanceof MouseEvent)
            {
                MouseEvent mouseEvent = (MouseEvent) event;
                if (mouseEvent.getClickCount() == 2)
                {
                    return false;
                }

                TreePath path = tree.getPathForLocation(mouseEvent.getX(), mouseEvent.getY());
                if (path != null)
                {
                    QueryeerTreeNode node = (QueryeerTreeNode) path.getLastPathComponent();

                    return node.node instanceof CheckBoxNode;
                }
            }
            return false;
        }

        @Override
        public Component getTreeCellEditorComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row)
        {
            Component editor = renderer.getTreeCellRendererComponent(tree, value, true, expanded, leaf, row, true);
            editorValue = ((QueryeerTreeNode) value).node;
            return editor;
        }
    }

    /** Tree model used with {@link QueryeerTree} */
    public static class QueryeerTreeModel extends DefaultTreeModel
    {
        /**
         * Construct a tree model with provided root node. NOTE! {#link {@link RegularNode#loadChildren()} will be called in constructor to populate.
         */
        public QueryeerTreeModel(RegularNode root)
        {
            super(null);
            setRoot(new QueryeerTreeNode(root));
            loadRoot();
        }

        /** Resets root node and marks it as children needs reloading */
        public void resetRoot()
        {
            ((QueryeerTreeNode) getRoot()).removeAllChildren();
            ((QueryeerTreeNode) getRoot()).childrenLoaded = false;
            loadRoot();
            reload();
        }

        private void loadRoot()
        {
            QueryeerTreeNode rootNode = (QueryeerTreeNode) root;
            RegularNode rootObj = rootNode.node;
            rootObj.loadChildren()
                    .stream()
                    .map(QueryeerTreeNode::new)
                    .forEach(n -> rootNode.add(n));
        }

        @Override
        protected void fireTreeStructureChanged(Object source, Object[] path, int[] childIndices, Object[] children)
        {
            super.fireTreeStructureChanged(source, path, childIndices, children);
        }

        @Override
        protected void fireTreeNodesChanged(Object source, Object[] path, int[] childIndices, Object[] children)
        {
            super.fireTreeNodesChanged(source, path, childIndices, children);
        }
    }

    private static class QueryeerTreeNode extends DefaultMutableTreeNode
    {
        private final RegularNode node;
        private boolean childrenLoaded = false;
        private boolean underline;
        private volatile boolean loading = false;

        QueryeerTreeNode(RegularNode node)
        {
            super(requireNonNull(node));
            this.node = node;
        }

        @Override
        public boolean isLeaf()
        {
            return node.isLeaf();
        }
    }

    /** A generic {@link RegularNode} implmentation that can be used for most use cases. */
    public static class TreeNode implements RegularNode
    {
        private final String title;
        private final Icon icon;
        private final ChildNodeSupplier childNodeSupplier;
        private final Supplier<List<Action>> contextMenuActions;
        private final Supplier<List<Action>> linkActions;
        private final boolean leaf;

        public TreeNode(String title, Icon icon)
        {
            this(title, icon, null, () -> emptyList(), () -> emptyList());
        }

        public TreeNode(String title, Icon icon, ChildNodeSupplier childNodeSupplier)
        {
            this(title, icon, childNodeSupplier, () -> emptyList(), () -> emptyList());
        }

        public TreeNode(String title, Icon icon, ChildNodeSupplier childNodeSupplier, Supplier<List<Action>> contextMenuActions, Supplier<List<Action>> linkActions)
        {
            this.title = title;
            this.icon = icon;
            this.childNodeSupplier = childNodeSupplier;
            this.linkActions = linkActions;
            this.leaf = childNodeSupplier == null;
            this.contextMenuActions = contextMenuActions;
        }

        @Override
        public String getTitle()
        {
            return title;
        }

        @Override
        public boolean isLeaf()
        {
            return leaf;
        }

        @Override
        public Icon getIcon()
        {
            return icon;
        }

        @Override
        public List<Action> getContextMenuActions()
        {
            return contextMenuActions.get();
        }

        @Override
        public List<Action> getLinkActions()
        {
            return linkActions.get();
        }

        @Override
        public List<RegularNode> loadChildren()
        {
            if (childNodeSupplier == null)
            {
                return emptyList();
            }
            return childNodeSupplier.loadChildren();
        }

        /** Child nodes suppliers */
        public interface ChildNodeSupplier
        {
            List<RegularNode> loadChildren();
        }
    }
}
