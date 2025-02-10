package com.queryeer;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent.Kind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.swing.BoxLayout;
import javax.swing.FocusManager;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.WindowConstants;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.commons.lang3.tuple.Pair;
import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.queryeer.FileWatchService.FileWatchListener;
import com.queryeer.api.component.ADocumentListenerAdapter;
import com.queryeer.api.component.DialogUtils;
import com.queryeer.api.event.NewQueryFileEvent;
import com.queryeer.api.service.IEventBus;

class ProjectsView extends JPanel
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectsView.class);
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(new BasicThreadFactory.Builder().daemon(true)
            .namingPattern("ProjectsFileMangagement-%d")
            .build());
    private static final String NAME = "queryeer.projects";
    private final IEventBus eventBus;
    private final QueryeerModel model;
    private final Config config;
    private final FileWatchService watchService;
    private ProjectsConfig projectsConfig;

    // UI
    private JTree projectsTree;
    private DefaultTreeModel projectsTreeModel;
    private DefaultMutableTreeNode root;
    private List<ProjectTreeNode> rootProjectNodes = new ArrayList<>();
    /* Non filtered root nodes. */
    private Future<?> filteredTreeBuilder;
    private JTextField search;
    private ProjectSettingDialog settingsDialog = new ProjectSettingDialog();
    private JToggleButton syncButton;

    ProjectsView(IEventBus eventBus, Config config, QueryeerModel model, FileWatchService watchService)
    {
        this.eventBus = requireNonNull(eventBus, "eventBus");
        this.config = requireNonNull(config, "config");
        this.model = requireNonNull(model, "model");
        this.watchService = requireNonNull(watchService, "watchService");

        model.addPropertyChangeListener(queryeerModelListener);

        loadConfig();
        initUI();
    }

    private void initUI()
    {
        setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        JButton add = new JButton(IconFactory.of(FontAwesome.PLUS, 8));
        add.setToolTipText("Add Project");
        add.addActionListener(l ->
        {
            JFileChooser chooser = new JFileChooser();
            if (!isBlank(config.getLastOpenPath()))
            {
                chooser.setCurrentDirectory(new File(config.getLastOpenPath()));
            }
            chooser.setMultiSelectionEnabled(false);
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

            int result = chooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION)
            {
                if (chooser.getSelectedFile() == null)
                {
                    return;
                }

                File file = chooser.getSelectedFile();
                Project project = new Project();
                project.folder = file.getAbsolutePath();
                project.folderFile = file;
                projectsConfig.projects.add(project);
                saveConfig();
                addProject(project);
            }
        });
        buttonPanel.add(add);

        JButton remove = new JButton(IconFactory.of(FontAwesome.MINUS, 8));
        remove.setToolTipText("Remove Selected Project");
        remove.setEnabled(false);
        remove.addActionListener(l ->
        {
            ProjectTreeNode node = (ProjectTreeNode) projectsTree.getSelectionPath()
                    .getLastPathComponent();
            if (JOptionPane.showConfirmDialog(this, "Remove " + node.project.folder + " ?", "Confirm Remove", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE) == JOptionPane.YES_OPTION)
            {
                rootProjectNodes.remove(node);
                projectsTreeModel.removeNodeFromParent(node);
                projectsConfig.projects.remove(node.project);
                saveConfig();
            }
        });
        buttonPanel.add(remove);

        JButton collapseAll = new JButton(IconFactory.of(FontAwesome.WINDOW_MINIMIZE, 8));
        collapseAll.setToolTipText("Collapse All");
        collapseAll.addActionListener(l ->
        {
            Enumeration<TreeNode> e = root.depthFirstEnumeration();
            while (e.hasMoreElements())
            {
                TreeNode node = e.nextElement();
                if (node == root)
                {
                    break;
                }
                projectsTree.collapsePath(new TreePath(((DefaultMutableTreeNode) node).getPath()));
            }
        });
        buttonPanel.add(collapseAll);

        JButton settings = new JButton(IconFactory.of(FontAwesome.COG, 8));
        settings.setEnabled(false);
        settings.setToolTipText("Change Selected Project Settings");
        settings.addActionListener(l ->
        {
            if (projectsTree.getSelectionPath() == null)
            {
                return;
            }

            ProjectTreeNode node = (ProjectTreeNode) projectsTree.getSelectionPath()
                    .getLastPathComponent();
            if (settingsDialog.showDialog(node.project))
            {
                // Unregister all watches before building new tree
                Enumeration<TreeNode> e = node.breadthFirstEnumeration();
                while (e.hasMoreElements())
                {
                    watchService.unregister((AProjectTreeNode) e.nextElement());
                }

                node.removeAllChildren();
                EXECUTOR.submit(() -> buildTree(node));
                projectsTreeModel.nodeStructureChanged(node);
            }
        });
        buttonPanel.add(settings);

        syncButton = new JToggleButton(IconFactory.of(FontAwesome.ARROWS_H, 8));
        syncButton.setToolTipText("Sync Projects Tree With Selected Tab");
        syncButton.setSelected(projectsConfig.syncProjectView);
        syncButton.addActionListener(l ->
        {
            projectsConfig.syncProjectView = syncButton.isSelected();
            saveConfig();
        });
        buttonPanel.add(syncButton);

        add(buttonPanel, gbc);

        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0d;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        search = new JTextField();
        search.getDocument()
                .addDocumentListener(new ADocumentListenerAdapter()
                {
                    @Override
                    protected void update()
                    {
                        if (isBlank(search.getText()))
                        {
                            root.removeAllChildren();
                            for (DefaultMutableTreeNode node : rootProjectNodes)
                            {
                                root.add(node);
                            }
                            projectsTreeModel.nodeStructureChanged(root);
                            for (ProjectTreeNode node : rootProjectNodes)
                            {
                                if (node.project.expanded)
                                {
                                    projectsTree.expandPath(new TreePath(node.getPath()));
                                }
                            }

                            if (syncButton.isSelected())
                            {
                                markQueryFileModel(model.getSelectedFile(), MarkOp.Selected);
                            }

                            return;
                        }

                        // Cancel previous future
                        if (filteredTreeBuilder != null
                                && !filteredTreeBuilder.isDone())
                        {
                            filteredTreeBuilder.cancel(true);
                        }

                        // Build filtered tree model
                        filteredTreeBuilder = EXECUTOR.submit(() -> buildFilteredTree(search.getText()));
                    }
                });

        search.setToolTipText("Quick Search");

        add(search, gbc);

        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0d;
        gbc.weighty = 1.0d;

        projectsTree = new JTree()
        {
            Point tooltipLocation;

            @Override
            public Point getToolTipLocation(MouseEvent event)
            {
                tooltipLocation = event.getPoint();
                return super.getToolTipLocation(event);
            }

            @Override
            public String getToolTipText()
            {
                if (tooltipLocation == null)
                {
                    return super.getToolTipText();
                }

                int rowForLocation = getRowForLocation(tooltipLocation.x, tooltipLocation.y);
                TreePath path = projectsTree.getPathForRow(rowForLocation);
                if (path != null)
                {
                    if (path.getLastPathComponent() instanceof ProjectFileTreeNode node)
                    {
                        if (node.fileModel != null)
                        {
                            return node.fileModel.getTooltip();
                        }

                        return node.file.getAbsolutePath();
                    }
                    else if (path.getLastPathComponent() instanceof ProjectTreeNode node)
                    {
                        return node.file.getAbsolutePath();
                    }
                }

                return super.getToolTipText();
            }
        };
        ToolTipManager.sharedInstance()
                .registerComponent(projectsTree);
        projectsTree.setCellRenderer(new DefaultTreeCellRenderer()
        {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus)
            {
                super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                if (value instanceof AProjectTreeNode node)
                {
                    setText(node.getText());
                }
                return this;
            }
        });
        projectsTree.addTreeWillExpandListener(willExpandListener);
        projectsTree.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (SwingUtilities.isLeftMouseButton(e)
                        && e.getClickCount() == 2)
                {
                    Point point = e.getPoint();
                    TreePath path = projectsTree.getPathForLocation(point.x, point.y);
                    if (path != null)
                    {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                        if (node instanceof ProjectFileTreeNode treeNode
                                && treeNode.file.isFile())
                        {
                            eventBus.publish(new NewQueryFileEvent(treeNode.file));
                        }
                    }
                }
            }
        });

        projectsTree.addTreeSelectionListener(l ->
        {
            DefaultMutableTreeNode node = l.getNewLeadSelectionPath() != null ? (DefaultMutableTreeNode) l.getNewLeadSelectionPath()
                    .getLastPathComponent()
                    : null;

            settings.setEnabled(node != null
                    && node.getParent() == root);
            remove.setEnabled(node instanceof ProjectTreeNode);

            if (node instanceof ProjectFileTreeNode treeNode
                    && treeNode.fileModel != null)
            {
                model.setSelectedFile(treeNode.fileModel);
            }
        });

        projectsTree.setRootVisible(false);
        projectsTree.setShowsRootHandles(true);
        projectsTree.getSelectionModel()
                .setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        root = new DefaultMutableTreeNode();
        projectsTreeModel = new DefaultTreeModel(root, true);
        projectsTree.setModel(projectsTreeModel);
        for (Project project : projectsConfig.projects)
        {
            addProject(project);
        }
        add(new JScrollPane(projectsTree), gbc);
    }

    /** Enumerates all project files. */
    void enumerateProjectFiles(Consumer<File> consumer)
    {
        Enumeration<TreeNode> e = root.breadthFirstEnumeration();
        while (e.hasMoreElements())
        {
            if (e.nextElement() instanceof AProjectTreeNode node
                    && node.file.isFile())
            {
                consumer.accept(node.file);
            }
        }
    }

    private TreeWillExpandListener willExpandListener = new TreeWillExpandListener()
    {
        @Override
        public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException
        {
            // Expand non expanded folder
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) event.getPath()
                    .getLastPathComponent();
            if (node instanceof ProjectTreeNode proj)
            {
                proj.project.expanded = true;
                saveConfig();
            }
        }

        @Override
        public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException
        {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) event.getPath()
                    .getLastPathComponent();
            if (node instanceof ProjectTreeNode proj)
            {
                proj.project.expanded = false;
                saveConfig();
            }
        }
    };

    private PropertyChangeListener queryeerModelListener = new PropertyChangeListener()
    {
        @Override
        public void propertyChange(PropertyChangeEvent evt)
        {
            if (QueryeerModel.FILES.equalsIgnoreCase(evt.getPropertyName()))
            {
                if (evt.getNewValue() != null)
                {
                    // Add
                    QueryFileModel fileModel = (QueryFileModel) evt.getNewValue();
                    markQueryFileModel(fileModel, MarkOp.Added);
                }
                else if (evt.getOldValue() != null)
                {
                    // Remove
                    QueryFileModel fileModel = (QueryFileModel) evt.getOldValue();
                    markQueryFileModel(fileModel, MarkOp.Removed);
                }
            }
            else if (syncButton.isSelected()
                    && QueryeerModel.SELECTED_FILE.equalsIgnoreCase(evt.getPropertyName()))
            {
                // Scroll and mark file if contained in a project
                QueryFileModel fileModel = (QueryFileModel) evt.getNewValue();
                markQueryFileModel(fileModel, MarkOp.Selected);
            }
        }
    };

    /** Method that builds a full tree of all files and subdirectories of provided project node. This method is executed in a NON EDT thread. */
    private void buildTree(ProjectTreeNode node)
    {
        Pattern pattern = node.project.getFilterRegex();
        Map<File, QueryFileModel> map = model.getFiles()
                .stream()
                .collect(toMap(QueryFileModel::getFile, Function.identity()));

        List<Pair<AProjectTreeNode, File>> queue = new ArrayList<>();
        queue.add(Pair.of(node, node.file));

        watchService.register(node.file.toPath(), node);

        while (!queue.isEmpty())
        {
            Pair<AProjectTreeNode, File> pair = queue.remove(0);
            AProjectTreeNode parent = pair.getKey();
            File file = pair.getValue();

            // Expand one level
            File[] files = file.listFiles();
            Arrays.sort(files, (a, b) ->
            {
                int af = a.isDirectory() ? 0
                        : 1;
                int bf = b.isDirectory() ? 0
                        : 1;
                int c = af - bf;
                if (c == 0)
                {
                    return StringUtils.compareIgnoreCase(a.getName(), b.getName());
                }
                return c;
            });

            for (File f : files)
            {
                if (!f.isDirectory()
                        && pattern != null
                        && !pattern.matcher(f.getName())
                                .matches())
                {
                    continue;
                }

                ProjectFileTreeNode projectTreeNode = new ProjectFileTreeNode(f);
                projectTreeNode.fileModel = map.get(f);
                parent.add(projectTreeNode);

                if (f.isDirectory())
                {
                    watchService.register(projectTreeNode.file.toPath(), projectTreeNode);
                    queue.add(Pair.of(projectTreeNode, f));
                }
            }
        }

        SwingUtilities.invokeLater(() ->
        {
            projectsTreeModel.nodeStructureChanged(node);
            if (node.project.expanded)
            {
                projectsTree.expandPath(new TreePath(node.getPath()));
            }
        });
    }

    /**
     * Method that builds a filtered tree from the original based on provided search text. This method is executed in a NON EDT thread.
     */
    private void buildFilteredTree(String searchText)
    {
        Map<File, QueryFileModel> map = model.getFiles()
                .stream()
                .collect(toMap(QueryFileModel::getFile, Function.identity()));

        root.removeAllChildren();
        List<Pair<DefaultMutableTreeNode, AProjectTreeNode>> queue = new ArrayList<>();
        for (ProjectTreeNode node : rootProjectNodes)
        {
            queue.add(Pair.of(root, node));
        }

        while (!queue.isEmpty())
        {
            if (Thread.interrupted())
            {
                return;
            }

            Pair<DefaultMutableTreeNode, AProjectTreeNode> pair = queue.remove(0);
            DefaultMutableTreeNode parent = pair.getKey();
            AProjectTreeNode originalChild = pair.getValue();
            if (originalChild.file.isDirectory())
            {
                AProjectTreeNode node;
                if (originalChild instanceof ProjectTreeNode projectTreeNode)
                {
                    node = new ProjectTreeNode(projectTreeNode.project);
                }
                else
                {
                    node = new ProjectFileTreeNode(originalChild.file);
                }
                parent.add(node);

                SwingUtilities.invokeLater(() ->
                {
                    try
                    {
                        projectsTreeModel.nodeStructureChanged(parent);
                        TreePath treePath = new TreePath(parent.getPath());
                        if (!projectsTree.isExpanded(treePath))
                        {
                            projectsTree.expandPath(treePath);
                        }
                    }
                    catch (Exception e)
                    {
                        // Swallow this. Happens sometimes when typing really fast in quick search
                        // Most likely that we running 2 or more threads here
                    }
                });

                // Queue up children
                int count = originalChild.getChildCount();
                for (int i = 0; i < count; i++)
                {
                    if (Thread.interrupted())
                    {
                        return;
                    }

                    AProjectTreeNode child = (AProjectTreeNode) originalChild.getChildAt(i);

                    // Check to see if this node has any matches, if not then don't add this node
                    // this to avoid empty folder structures in tree
                    Enumeration<TreeNode> e = child.breadthFirstEnumeration();
                    boolean hasMatch = false;
                    while (e.hasMoreElements())
                    {
                        if (e.nextElement() instanceof ProjectFileTreeNode fileTreeNode
                                && !fileTreeNode.file.isDirectory()
                                && StringUtils.containsAnyIgnoreCase(fileTreeNode.file.getName(), searchText))
                        {
                            hasMatch = true;
                            break;
                        }
                    }

                    if (hasMatch)
                    {
                        queue.add(Pair.of(node, child));
                    }
                }
            }
            else if (StringUtils.containsAnyIgnoreCase(originalChild.file.getName(), searchText))
            {
                ProjectFileTreeNode node = new ProjectFileTreeNode(originalChild.file);
                node.fileModel = map.get(node.file);
                parent.add(node);

                SwingUtilities.invokeLater(() ->
                {
                    projectsTreeModel.nodeStructureChanged(parent);
                    TreePath treePath = new TreePath(parent.getPath());
                    if (!projectsTree.isExpanded(treePath))
                    {
                        projectsTree.expandPath(treePath);
                    }
                });
            }
        }
    }

    enum MarkOp
    {
        Added,
        Removed,
        Selected
    }

    private void markQueryFileModel(QueryFileModel fileModel, MarkOp markOp)
    {
        Enumeration<TreeNode> e = root.breadthFirstEnumeration();
        while (e.hasMoreElements())
        {
            if (e.nextElement() instanceof ProjectFileTreeNode treeNode
                    && Objects.equals(treeNode.file, fileModel.getFile()))
            {
                if (markOp == MarkOp.Removed)
                {
                    treeNode.fileModel = null;
                    fileModel.removePropertyChangeListener(treeNode);
                    projectsTreeModel.nodeChanged(treeNode);
                }
                else if (markOp == MarkOp.Added)
                {
                    treeNode.fileModel = fileModel;
                    fileModel.addPropertyChangeListener(treeNode);
                    projectsTreeModel.nodeChanged(treeNode);
                }
                else if (markOp == MarkOp.Selected)
                {
                    if (treeNode.fileModel == null)
                    {
                        treeNode.fileModel = fileModel;
                        fileModel.addPropertyChangeListener(treeNode);
                        projectsTreeModel.nodeChanged(treeNode);
                    }

                    TreePath treePath = new TreePath(treeNode.getPath());
                    projectsTree.expandPath(projectsTreeModel.isLeaf(treePath.getLastPathComponent()) ? treePath.getParentPath()
                            : treePath);

                    Rectangle bounds = projectsTree.getPathBounds(treePath);
                    if (bounds != null)
                    {
                        // Don't touch the horizontal scroll
                        bounds.x = 0;
                        projectsTree.scrollRectToVisible(bounds);
                    }
                }
                return;
            }
        }
    }

    private void addProject(Project project)
    {
        ProjectTreeNode node = new ProjectTreeNode(project);
        rootProjectNodes.add(node);
        root.add(node);
        projectsTreeModel.nodeStructureChanged(root);
        EXECUTOR.execute(() -> buildTree(node));
    }

    private void loadConfig()
    {
        File configFileName = config.getConfigFileName(NAME);
        if (configFileName.exists())
        {
            try
            {
                projectsConfig = QueryeerController.MAPPER.readValue(configFileName, ProjectsConfig.class);
            }
            catch (IOException e)
            {
                LOGGER.error("Error reading projects config from: {}", configFileName, e);
            }
        }

        if (projectsConfig == null)
        {
            projectsConfig = new ProjectsConfig();
        }
    }

    private void saveConfig()
    {
        File configFileName = config.getConfigFileName(NAME);
        try
        {
            QueryeerController.MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValue(configFileName, projectsConfig);
        }
        catch (IOException e)
        {
            LOGGER.error("Error writing projects config to: {}", configFileName, e);
        }
    }

    private class ProjectSettingDialog extends DialogUtils.ADialog
    {
        private final JTextField name;
        private final JTextField filter;
        private Project project;
        private boolean result;

        ProjectSettingDialog()
        {
            getContentPane().setLayout(new GridBagLayout());

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.anchor = GridBagConstraints.WEST;
            getContentPane().add(new JLabel("Name:"), gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 1;
            gbc.weightx = 1.0d;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            name = new JTextField();
            getContentPane().add(name, gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 2;
            gbc.anchor = GridBagConstraints.WEST;
            JLabel filterLabel = new JLabel("Filter Regex:");
            filterLabel.setToolTipText("Filter Regexp Applied To Files");
            getContentPane().add(filterLabel, gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 3;
            gbc.weightx = 1.0d;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;

            filter = new JTextField();
            filter.setToolTipText("Filter Regexp Applied To Files");
            getContentPane().add(filter, gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 4;
            gbc.weighty = 1.0d;
            gbc.anchor = GridBagConstraints.NORTHWEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;

            JPanel buttonPanel = new JPanel();
            buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
            getContentPane().add(buttonPanel, gbc);

            JButton ok = new JButton("OK");
            ok.addActionListener(l ->
            {
                if (!isBlank(filter.getText()))
                {
                    try
                    {
                        Pattern.compile(filter.getText());
                    }
                    catch (PatternSyntaxException e)
                    {
                        JOptionPane.showMessageDialog(this, "Error parsing Regex: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                }

                project.name = name.getText();
                project.filterRegex = filter.getText();
                project.filterRegexIsSet = false;
                project.filterRegexPattern = null;
                saveConfig();
                result = true;
                setVisible(false);
            });
            JButton cancel = new JButton("Cancel");
            cancel.addActionListener(l ->
            {
                result = false;
                setVisible(false);
            });
            buttonPanel.add(ok);
            buttonPanel.add(cancel);

            setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
            setPreferredSize(new Dimension(600, 400));
            pack();
            setLocationRelativeTo(null);
            pack();

            setModalityType(ModalityType.APPLICATION_MODAL);
        }

        boolean showDialog(Project project)
        {
            setTitle("Settings for: " + project.folder);
            this.result = false;
            this.project = project;
            this.name.setText(project.name);
            this.filter.setText(project.filterRegex);

            Window activeWindow = FocusManager.getCurrentManager()
                    .getActiveWindow();
            setLocationRelativeTo(activeWindow);

            setVisible(true);
            return result;
        }
    }

    private static class ProjectsConfig
    {
        @JsonProperty
        List<Project> projects = new ArrayList<>();

        @JsonProperty
        boolean syncProjectView;
    }

    private static class Project
    {
        @JsonProperty
        String name = "";

        @JsonProperty
        String folder;

        @JsonProperty
        String filterRegex = "";

        @JsonProperty
        boolean expanded;

        @JsonIgnore
        Pattern filterRegexPattern;
        @JsonIgnore
        boolean filterRegexIsSet = false;
        @JsonIgnore
        File folderFile;

        @JsonIgnore
        File getFolder()
        {
            if (folderFile == null)
            {
                folderFile = new File(folder);
            }
            return folderFile;
        }

        @JsonIgnore
        Pattern getFilterRegex()
        {
            if (filterRegexPattern == null
                    || filterRegexIsSet)
            {
                filterRegexIsSet = true;
                if (!isBlank(filterRegex))
                {
                    try
                    {
                        filterRegexPattern = Pattern.compile(filterRegex);
                    }
                    catch (PatternSyntaxException e)
                    {
                        LOGGER.error("Error parsing filter regex: {}, for project: {}", filterRegex, folder, e);
                    }
                }
            }
            return filterRegexPattern;
        }
    }

    private abstract class AProjectTreeNode extends DefaultMutableTreeNode implements FileWatchListener
    {
        final File file;

        AProjectTreeNode(File file)
        {
            this.file = requireNonNull(file);
            setAllowsChildren(file.isDirectory());
        }

        abstract String getText();

        Pattern getPattern()
        {
            if (this instanceof ProjectTreeNode node)
            {
                return node.project.getFilterRegex();
            }
            else if (getParent() instanceof AProjectTreeNode node)
            {
                return node.getPattern();
            }
            return null;
        }

        @Override
        public void pathChanged(Path path, Kind<?> kind)
        {
            if (kind == StandardWatchEventKinds.ENTRY_CREATE)
            {
                handleCreateEvent(path);
            }
            else if (kind == StandardWatchEventKinds.ENTRY_DELETE)
            {
                handleDeleteEvent(path);
            }
        }

        private void handleCreateEvent(Path path)
        {
            File file = path.toFile();

            // Not a file for this project
            Pattern pattern = getPattern();
            if (!file.isDirectory()
                    && pattern != null
                    && !pattern.matcher(file.getName())
                            .matches())
            {
                return;
            }

            ProjectFileTreeNode newNode = new ProjectFileTreeNode(file);

            // Insert new node in tree
            boolean isDirectory = file.isDirectory();
            int addedIndex = -1;
            int childCount = getChildCount();
            for (int i = 0; i < childCount; i++)
            {
                AProjectTreeNode child = (AProjectTreeNode) getChildAt(i);
                // Loop pass all directory is this is a file
                if (!isDirectory
                        && child.file.isDirectory())
                {
                    continue;
                }
                // Insert directory before any files
                else if (isDirectory
                        && !child.file.isDirectory())
                {
                    addedIndex = i;
                    break;
                }

                int c = StringUtils.compareIgnoreCase(child.file.getName(), file.getName());

                // New node should be placed here
                if (c > 0)
                {
                    addedIndex = i;
                    break;
                }
            }

            // Add child last
            if (addedIndex < 0)
            {
                addedIndex = childCount;
            }

            final int index = addedIndex;
            try
            {
                SwingUtilities.invokeAndWait(() -> projectsTreeModel.insertNodeInto(newNode, this, index));
                // Register new folder
                if (file.isDirectory())
                {
                    watchService.register(path, newNode);
                }
            }
            catch (InvocationTargetException | InterruptedException e)
            {
                LOGGER.error("Error adding project tree nodes", e);
            }
        }

        private void handleDeleteEvent(Path path)
        {
            int childCount = getChildCount();
            for (int i = 0; i < childCount; i++)
            {
                AProjectTreeNode child = (AProjectTreeNode) getChildAt(i);
                if (child.file.toPath()
                        .compareTo(path) == 0)
                {
                    // Unregister any existing watch keys recursively
                    Enumeration<TreeNode> ee = child.breadthFirstEnumeration();
                    while (ee.hasMoreElements())
                    {
                        watchService.unregister((AProjectTreeNode) ee.nextElement());
                    }

                    try
                    {
                        SwingUtilities.invokeAndWait(() -> projectsTreeModel.removeNodeFromParent(child));
                    }
                    catch (InvocationTargetException | InterruptedException e)
                    {
                        LOGGER.error("Error removing project tree nodes", e);
                    }
                    break;
                }
            }
        }
    }

    private class ProjectTreeNode extends AProjectTreeNode
    {
        private final Project project;

        private ProjectTreeNode(Project project)
        {
            super(project.getFolder());
            this.project = project;
        }

        @Override
        String getText()
        {
            String text = file.getAbsolutePath();
            if (!isBlank(project.name))
            {
                text = project.name;
            }
            if (!isBlank(project.filterRegex))
            {
                text += " (" + project.filterRegex + ")";
            }
            return text;
        }

        @Override
        public boolean isLeaf()
        {
            return false;
        }
    }

    private class ProjectFileTreeNode extends AProjectTreeNode implements PropertyChangeListener
    {
        /** Associated file model if this node is open in queryeer */
        QueryFileModel fileModel;

        ProjectFileTreeNode(File file)
        {
            super(file);
        }

        @Override
        String getText()
        {
            String text = file.getName();
            // If the node is open in alter the text a bit
            if (fileModel != null)
            {
                text = "<html><b/>" + (fileModel.isDirty() ? "*"
                        : "")
                       + text
                       + (fileModel.getState()
                               .isExecuting() ? " (Executing...)"
                                       : "")
                       + "</b></html>";
            }

            return text;
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt)
        {
            SwingUtilities.invokeLater(() -> projectsTreeModel.nodeChanged(this));
        }
    }
}
