package com.queryeer;

import static java.util.Objects.requireNonNull;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.WindowConstants;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import com.queryeer.api.component.DialogUtils;
import com.queryeer.api.extensions.IConfigurable;

/** Options dialog */
class OptionsDialog extends DialogUtils.ADialog
{
    private final List<IConfigurable> configurables;
    private final List<Configurable> configurableNodes = new ArrayList<>();
    private final DefaultMutableTreeNode root;
    private final JPanel configComponentPanel = new JPanel();
    private final JTree optionsTree = new JTree();
    private JButton ok;
    private JButton cancel;

    OptionsDialog(List<IConfigurable> configurables)
    {
        super((Frame) null, "Options", true);
        this.configurables = requireNonNull(configurables, "configurables");
        root = initRootNode();
        initDialog();
    }

    private DefaultMutableTreeNode initRootNode()
    {
        Map<String, List<IConfigurable>> entries = configurables.stream()
                .sorted((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.groupName(), b.groupName()))
                .collect(Collectors.groupingBy(IConfigurable::groupName, LinkedHashMap::new, Collectors.toList()));

        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        for (Entry<String, List<IConfigurable>> e : entries.entrySet())
        {
            OptionType optionType = new OptionType(e.getKey());
            for (IConfigurable configurable : e.getValue())
            {
                Configurable configurableNode = new Configurable(configurable);
                configurableNodes.add(configurableNode);
                optionType.add(configurableNode);
            }
            root.add(optionType);
        }
        return root;
    }

    private void initDialog()
    {
        getContentPane().setLayout(new BorderLayout());

        JSplitPane options = new JSplitPane();
        options.setOrientation(JSplitPane.HORIZONTAL_SPLIT);

        optionsTree.setExpandsSelectedPaths(true);
        optionsTree.setRootVisible(false);
        optionsTree.setShowsRootHandles(true);
        optionsTree.getSelectionModel()
                .setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        ((DefaultTreeModel) optionsTree.getModel()).setRoot(root);

        optionsTree.addTreeSelectionListener(l -> treeNodeSelected(l.getPath()));
        optionsTree.setCellRenderer(new DefaultTreeCellRenderer()
        {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus)
            {
                super.getTreeCellRendererComponent(optionsTree, value, selected, expanded, leaf, row, hasFocus);

                if (value instanceof Configurable node)
                {
                    if (node.dirty)
                    {
                        this.setText("<html><b>" + this.getText() + "</b>");
                    }
                    else
                    {
                        this.setText(this.getText());
                    }
                }
                return this;
            }
        });

        configComponentPanel.setLayout(new BorderLayout());

        options.setLeftComponent(new JScrollPane(optionsTree));
        options.setRightComponent(new JScrollPane(configComponentPanel));
        options.setResizeWeight(0.15);

        getContentPane().add(options, BorderLayout.CENTER);

        ok = new JButton("OK");
        ok.setEnabled(false);
        ok.addActionListener(l1 ->
        {
            if (commit())
            {
                setVisible(false);
            }
        });
        cancel = new JButton("Cancel");
        cancel.setEnabled(false);
        cancel.addActionListener(l1 ->
        {
            if (shouldClose())
            {
                setVisible(false);
            }
        });

        JPanel bottomPanel = new JPanel();
        bottomPanel.add(ok);
        bottomPanel.add(cancel);

        addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
                if (shouldClose())
                {
                    setVisible(false);
                }
            }
        });

        getContentPane().add(bottomPanel, BorderLayout.SOUTH);

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setPreferredSize(Constants.DEFAULT_DIALOG_SIZE);
        pack();
        setLocationRelativeTo(null);
        pack();

        setModalityType(ModalityType.APPLICATION_MODAL);
    }

    void setSelectedConfigurable(Class<? extends IConfigurable> configurableToSelect)
    {
        if (configurableToSelect == null)
        {
            return;
        }

        for (Configurable configurable : configurableNodes)
        {
            if (configurableToSelect == configurable.getConfigurable()
                    .getClass())
            {
                optionsTree.setSelectionPath(new TreePath(configurable.getPath()));
                return;
            }
        }
    }

    private boolean commit()
    {
        AtomicBoolean anyFail = new AtomicBoolean(false);
        configurableNodes.forEach(node ->
        {
            if (node.dirty)
            {
                if (node.getConfigurable()
                        .commitChanges())
                {
                    node.dirty = false;
                }
                else
                {
                    anyFail.set(true);
                }
            }
        });

        ok.setEnabled(anyFail.get());
        cancel.setEnabled(anyFail.get());

        return !anyFail.get();
    }

    @Override
    protected boolean shouldClose()
    {
        if (configurableNodes.stream()
                .anyMatch(c -> c.dirty))
        {
            int result = JOptionPane.showConfirmDialog(this, "There are changes made to configuration, proceed ?", "Unsaved changes", JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.NO_OPTION)
            {
                return false;
            }
        }

        configurableNodes.forEach(node ->
        {
            if (node.dirty)
            {
                try
                {
                    node.getConfigurable()
                            .revertChanges();
                }
                finally
                {
                    // Always set dirty flag because else we might end up with
                    // not being able to close dialog
                    node.dirty = false;
                }
            }
        });

        ok.setEnabled(false);
        cancel.setEnabled(false);

        return true;
    }

    private void treeNodeSelected(TreePath path)
    {
        if (!(path.getLastPathComponent() instanceof Configurable))
        {
            return;
        }

        Configurable node = (Configurable) path.getLastPathComponent();

        if (node.configComponent == null)
        {
            JPanel panel = new JPanel(new BorderLayout());
            JLabel label = new JLabel("<html><h2>" + node.getConfigurable()
                    .getLongTitle() + "</h2><hr></html>");
            label.setHorizontalAlignment(JLabel.CENTER);
            panel.add(label, BorderLayout.NORTH);

            // Load component and set up listeners
            Component component = node.getConfigurable()
                    .getComponent();
            node.getConfigurable()
                    .addDirtyStateConsumer(dirty ->
                    {
                        ok.setEnabled(dirty);
                        cancel.setEnabled(dirty);
                        node.dirty = dirty;
                        optionsTree.repaint();
                    });

            panel.add(component, BorderLayout.CENTER);
            node.configComponent = panel;
        }

        configComponentPanel.removeAll();
        configComponentPanel.add(node.configComponent, BorderLayout.CENTER);
        configComponentPanel.revalidate();
        configComponentPanel.repaint();
    }

    private static class OptionType extends DefaultMutableTreeNode
    {
        private final String title;

        OptionType(String title)
        {
            this.title = title;
        }

        @Override
        public String toString()
        {
            return title;
        }
    }

    private static class Configurable extends DefaultMutableTreeNode
    {
        private boolean dirty;
        private Component configComponent;

        Configurable(IConfigurable configurable)
        {
            super(configurable);
        }

        @Override
        public boolean isLeaf()
        {
            return true;
        }

        @Override
        public String toString()
        {
            return getConfigurable().getTitle();
        }

        IConfigurable getConfigurable()
        {
            return (IConfigurable) getUserObject();
        }
    }
}
