package com.queryeer;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.io.FileUtils.byteCountToDisplaySize;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.apache.commons.io.FilenameUtils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queryeer.QueryFileModel.State;
import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.extensions.catalog.ICatalogExtension;
import com.queryeer.api.extensions.output.IOutputExtension;
import com.queryeer.api.extensions.output.IOutputFormatExtension;

/** Main controller for Queryeer */
class QueryeerController implements PropertyChangeListener
{
    private static final int TIMER_INTERVAL = 250;
    static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final QueryeerView view;
    private final QueryeerModel model;
    private final Config config;
    private final List<IOutputExtension> outputExtensions;
    private final List<IOutputFormatExtension> outputFormatExtensions;

    private final List<CatalogExtensionView> catalogExtensionViews = new ArrayList<>();
    private final VariablesDialog variablesDialog;
    private final CaretChangedListener caretChangedListener = new CaretChangedListener();
    private final ButtonGroup defaultGroup = new ButtonGroup();
    private final OptionsDialog optionsDialog;
    private final AboutDialog aboutDialog;

    private int newFileCounter = 1;
    private final String version;
    private QueryFileProvider queryFileProvider;

    QueryeerController(ServiceLoader serviceLoader, QueryeerView view, QueryeerModel model)
    {
        this.config = requireNonNull(requireNonNull(serviceLoader, "serviceLoader").get(Config.class), "config");
        this.outputExtensions = requireNonNull(serviceLoader.getAll(IOutputExtension.class), "outputExtensions");
        this.outputFormatExtensions = requireNonNull(serviceLoader.getAll(IOutputFormatExtension.class), "outputFormatExtensions");
        this.queryFileProvider = requireNonNull(serviceLoader.get(QueryFileProvider.class), "queryFileProvider");
        this.view = requireNonNull(view, "view");
        this.model = requireNonNull(model, "model");
        this.model.addPropertyChangeListener(this);
        this.variablesDialog = new VariablesDialog(view);
        this.optionsDialog = new OptionsDialog(view, serviceLoader);
        this.outputExtensions.sort((a, b) -> Integer.compare(a.order(), b.order()));
        this.outputFormatExtensions.sort((a, b) -> Integer.compare(a.order(), b.order()));

        if (!isBlank(QueryeerView.class.getPackage()
                .getImplementationVersion()))
        {
            version = QueryeerView.class.getPackage()
                    .getImplementationVersion();
        }
        else
        {
            version = "Dev";
        }

        this.aboutDialog = new AboutDialog(view, version);

        init();
    }

    // CSOFF
    private void init()
    // CSON
    {
        view.setTitle("Queryeer IDE - " + version);
        view.bindOutputExtensions(outputExtensions);
        view.setActionHandler(this::handleViewAction);
        view.setOpenRecentFileConsumer(this::openRecentFileAction);

        JComboBox<IOutputExtension> outputCombo = view.getOutputCombo();
        DefaultComboBoxModel<IOutputExtension> outputComboModel = (DefaultComboBoxModel<IOutputExtension>) outputCombo.getModel();
        for (IOutputExtension outputExtension : outputExtensions)
        {
            outputComboModel.addElement(outputExtension);
        }
        JComboBox<IOutputFormatExtension> formatCombo = view.getFormatCombo();
        DefaultComboBoxModel<IOutputFormatExtension> formatComboModel = (DefaultComboBoxModel<IOutputFormatExtension>) formatCombo.getModel();
        for (IOutputFormatExtension outputFormatExtension : outputFormatExtensions)
        {
            formatComboModel.addElement(outputFormatExtension);
        }

        int y = 0;
        Insets insets = new Insets(0, 0, 3, 0);
        for (Config.Catalog catalog : config.getCatalogs())
        {
            if (catalog.isDisabled())
            {
                continue;
            }
            ICatalogExtension extension = catalog.getCatalogExtension();
            if (extension.getConfigurableClass() == null
                    && !extension.hasQuickPropertieComponent())
            {
                continue;
            }

            CatalogExtensionView extensionView = new CatalogExtensionView(extension, defaultGroup, c -> defaultCatalogChanged(c), c -> showOptionsAction(c));
            catalogExtensionViews.add(extensionView);
            view.getPanelCatalogs()
                    .add(extensionView, new GridBagConstraints(0, y++, 1, 1, 1, 0, GridBagConstraints.BASELINE, GridBagConstraints.HORIZONTAL, insets, 0, 0));
        }
        view.getPanelCatalogs()
                .add(new JPanel(), new GridBagConstraints(0, y, 1, 1, 1, 1, GridBagConstraints.BASELINE, GridBagConstraints.HORIZONTAL, insets, 0, 0));
        view.getEditorsTabbedPane()
                .addChangeListener(new SelectedFileListener());
        view.getMemoryLabel()
                .setText(getMemoryString());
        new Timer(TIMER_INTERVAL, evt -> view.getMemoryLabel()
                .setText(getMemoryString())).start();
        view.setRecentFiles(config.getRecentFiles());

        SwingUtilities.invokeLater(() ->
        {
            final String message = aboutDialog.getNewVersionString();
            if (message != null)
            {
                view.getLabelVersion()
                        .setText("<html><b>New version available!</b>");
                view.getLabelVersion()
                        .addMouseListener(new MouseAdapter()
                        {
                            @Override
                            public void mouseClicked(MouseEvent e)
                            {
                                aboutDialog.showNewVersionMessage(message);
                            }
                        });
            }
        });

        // Open a new query
        newQueryAction();
    }

    private void defaultCatalogChanged(String catalogAlias)
    {
        QueryFileView queryFile = (QueryFileView) view.getEditorsTabbedPane()
                .getSelectedComponent();
        if (queryFile != null)
        {
            queryFile.getFile()
                    .getQuerySession()
                    .setDefaultCatalogAlias(catalogAlias);
        }
    }

    private String getMemoryString()
    {
        Runtime runtime = Runtime.getRuntime();
        return String.format("%s / %s", byteCountToDisplaySize(runtime.totalMemory()), byteCountToDisplaySize(runtime.totalMemory() - runtime.freeMemory()));
    }

    private void saveConfig()
    {
        config.save();
        view.setRecentFiles(config.getRecentFiles());
    }

    private boolean save(QueryFileModel file, boolean saveAs)
    {
        if (!saveAs
                && !file.isDirty())
        {
            return true;
        }

        if (saveAs
                || file.isNew())
        {
            // CSOFF
            JFileChooser fileChooser = new JFileChooser()
            // CSON
            {
                @Override
                public void approveSelection()
                {
                    File f = getSelectedFile();
                    if (f.exists()
                            && getDialogType() == SAVE_DIALOG)
                    {
                        int result = JOptionPane.showConfirmDialog(this, "The file exists, overwrite?", "Existing file", JOptionPane.YES_NO_CANCEL_OPTION);
                        // CSOFF
                        switch (result)
                        // CSON
                        {
                            case JOptionPane.YES_OPTION:
                                super.approveSelection();
                                return;
                            case JOptionPane.NO_OPTION:
                                return;
                            case JOptionPane.CLOSED_OPTION:
                                return;
                            case JOptionPane.CANCEL_OPTION:
                                cancelSelection();
                                return;
                        }
                    }
                    super.approveSelection();
                }
            };
            fileChooser.setSelectedFile(new File(file.getFilename()));
            int result = fileChooser.showSaveDialog(view);
            if (result == JFileChooser.APPROVE_OPTION)
            {
                file.setFilename(fileChooser.getSelectedFile()
                        .getAbsolutePath());
            }
            else if (result == JFileChooser.CANCEL_OPTION)
            {
                return false;
            }
        }

        file.save();
        config.appendRecentFile(file.getFilename());
        saveConfig();
        return true;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt)
    {
        if (QueryeerModel.SELECTED_FILE.equals(evt.getPropertyName()))
        {
            QueryFileModel file = (QueryFileModel) evt.getNewValue();
            // New tab
            if (evt.getOldValue() == null)
            {
                final QueryFileView content = new QueryFileView(file, outputExtensions, text -> file.setQuery(text), caretChangedListener);
                view.getEditorsTabbedPane()
                        .add(content);

                final TabComponentView tabComponent = TabComponentView.queryFileHeader(file.getTabTitle(), null, () ->
                {
                    // CSOFF
                    if (file.isDirty())
                    // CSON
                    {
                        int result = JOptionPane.showConfirmDialog(view, "Save changes ?", "Save", JOptionPane.YES_NO_CANCEL_OPTION);
                        // CSOFF
                        if (result == JOptionPane.CANCEL_OPTION)
                        // CSON
                        {
                            return;
                        }
                        else if (result == JOptionPane.YES_OPTION)
                        {
                            // CSOFF
                            if (!save(file, false))
                            // CSON
                            {
                                return;
                            }
                        }
                    }

                    // Find tab index with "this" content
                    int length = view.getEditorsTabbedPane()
                            .getComponents().length;
                    for (int i = 0; i < length; i++)
                    {
                        // CSOFF
                        if (view.getEditorsTabbedPane()
                                .getComponents()[i] == content)
                        // CSON
                        {
                            model.removeFile(file);
                            view.getEditorsTabbedPane()
                                    .remove(content);
                            break;
                        }
                    }
                });

                // Set title and tooltip upon change
                file.addPropertyChangeListener(l ->
                {
                    tabComponent.setTitle(file.getTabTitle());
                    int length = view.getEditorsTabbedPane()
                            .getTabCount();
                    for (int i = 0; i < length; i++)
                    {
                        // CSOFF
                        if (view.getEditorsTabbedPane()
                                .getTabComponentAt(i) == tabComponent)
                        // CSON
                        {
                            view.getEditorsTabbedPane()
                                    .setToolTipTextAt(i, file.getFilename());
                            break;
                        }
                    }
                });

                int index = model.getFiles()
                        .size() - 1;
                view.getEditorsTabbedPane()
                        .setToolTipTextAt(index, file.getFilename());
                view.getEditorsTabbedPane()
                        .setTabComponentAt(index, tabComponent);
                view.getEditorsTabbedPane()
                        .setSelectedIndex(index);
                content.requestFocusInWindow();
            }
        }
    }

    /** Selected file listener. */
    private class SelectedFileListener implements ChangeListener
    {
        @Override
        public void stateChanged(ChangeEvent e)
        {
            queryFileProvider.setQueryFile(null);
            int index = view.getEditorsTabbedPane()
                    .getSelectedIndex();
            if (index >= 0)
            {
                QueryFileView queryFile = (QueryFileView) view.getEditorsTabbedPane()
                        .getSelectedComponent();
                queryFileProvider.setQueryFile(queryFile);
                caretChangedListener.accept(queryFile);
                model.setSelectedFile(index);
                view.getOutputCombo()
                        .setSelectedItem(queryFile.getFile()
                                .getOutputExtension());
                view.getFormatCombo()
                        .setSelectedItem(queryFile.getFile()
                                .getOutputFormat());
                defaultGroup.clearSelection();
                catalogExtensionViews.forEach(v -> v.init(queryFile.getFile()));
            }
        }
    }

    private void handleViewAction(ViewAction action)
    {
        switch (action)
        {
            case CANCEL:
                cancelAction();
                break;
            case CONFIG_OUTPUT:
                configOutputAction();
                break;
            case EDIT_VARIABLES:
                editVariablesAction();
                break;
            case EXECUTE:
                executeAction();
                break;
            case EXIT:
                exitAction();
                break;
            case FORMAT_CHANGED:
                formatChangedAction();
                break;
            case NEWQUERY:
                newQueryAction();
                break;
            case OPEN:
                openAction();
                break;
            case OUTPUT_CHANGED:
                outputChangedAction();
                break;
            case SAVE:
                saveAction();
                break;
            case SAVEAS:
                saveAsAction();
                break;
            case TOGGLE_COMMENT:
                toggleCommentAction();
                break;
            case TOGGLE_RESULT:
                toggleResultAction();
                break;
            case OPTIONS:
                showOptionsAction(null);
                break;
            case ABOUT:
                aboutDialog.setVisible(true);
                break;
            default:
                throw new IllegalArgumentException("Unknown action " + action);
        }
    }

    private void openRecentFileAction(String file)
    {
        int length = model.getFiles()
                .size();
        for (int i = 0; i < length; i++)
        {
            if (model.getFiles()
                    .get(i)
                    .getFilename()
                    .equals(file))
            {
                model.setSelectedFile(i);
                // TODO: remove this when proper binding exist in QueryeerModel
                // for selected file
                view.getEditorsTabbedPane()
                        .setSelectedIndex(i);
                return;
            }
        }

        QueryFileModel queryFile = new QueryFileModel(config.getCatalogs(), new File(file));
        queryFile.setOutput(outputExtensions.get(0));
        model.addFile(queryFile);
        config.appendRecentFile(file);
        saveConfig();
    }

    private void configOutputAction()
    {
        IOutputExtension extension = (IOutputExtension) view.getOutputCombo()
                .getSelectedItem();
        if (extension.getConfigurableClass() != null)
        {
            showOptionsAction(extension.getConfigurableClass());
        }
    }

    /** Exit action */
    private void exitAction()
    {
        List<QueryFileModel> dirtyFiles = model.getFiles()
                .stream()
                .filter(f -> f.isDirty())
                .collect(toList());
        if (dirtyFiles.size() == 0)
        {
            System.exit(0);
        }

        int result = JOptionPane.showConfirmDialog(view, "Save changes to the following files: " + System.lineSeparator()
                                                         + dirtyFiles.stream()
                                                                 .map(f -> FilenameUtils.getName(f.getFilename()))
                                                                 .collect(joining(System.lineSeparator())),
                "Unsaved changes", JOptionPane.YES_NO_CANCEL_OPTION);

        if (result == JOptionPane.CANCEL_OPTION)
        {
            return;
        }
        else if (result == JOptionPane.NO_OPTION)
        {
            System.exit(0);
        }

        for (QueryFileModel file : dirtyFiles)
        {
            // Abort on first Cancel
            if (!save(file, false))
            {
                return;
            }
        }

        System.exit(0);
    }

    private void outputChangedAction()
    {
        QueryFileView queryFile = (QueryFileView) view.getEditorsTabbedPane()
                .getSelectedComponent();
        if (queryFile != null)
        {
            queryFile.getFile()
                    .setOutput((IOutputExtension) view.getOutputCombo()
                            .getSelectedItem());

            // Set output format accordingly
            IOutputFormatExtension outputFormat = queryFile.getFile()
                    .getOutputFormat();
            if (outputFormat == null)
            {
                if (view.getFormatCombo()
                        .getItemCount() > 0)
                {
                    view.getFormatCombo()
                            .setSelectedIndex(0);
                }
            }
            else
            {
                view.getFormatCombo()
                        .setSelectedItem(outputFormat);
            }
        }
    }

    private void formatChangedAction()
    {
        QueryFileView queryFile = (QueryFileView) view.getEditorsTabbedPane()
                .getSelectedComponent();
        if (queryFile != null)
        {
            queryFile.getFile()
                    .setOutputFormat((IOutputFormatExtension) view.getFormatCombo()
                            .getSelectedItem());
        }
    }

    private void toggleCommentAction()
    {
        QueryFileView queryFile = (QueryFileView) view.getEditorsTabbedPane()
                .getSelectedComponent();
        if (queryFile != null)
        {
            queryFile.toggleComments();
        }
    }

    private void showOptionsAction(Class<? extends IConfigurable> configurableToSelect)
    {
        optionsDialog.setSelectedConfigurable(configurableToSelect);
        optionsDialog.setVisible(true);
    }

    private void toggleResultAction()
    {
        QueryFileView queryFile = (QueryFileView) view.getEditorsTabbedPane()
                .getSelectedComponent();
        if (queryFile != null)
        {
            queryFile.toggleResultPane();
        }
    }

    /** Cancel action */
    private void cancelAction()
    {
        QueryFileView queryFile = (QueryFileView) view.getEditorsTabbedPane()
                .getSelectedComponent();
        if (queryFile != null
                && queryFile.getFile()
                        .getState() == State.EXECUTING)
        {
            queryFile.getFile()
                    .setState(State.ABORTED);
        }
    }

    /** Execute listener. */
    private void executeAction()
    {
        QueryFileView queryFile = (QueryFileView) view.getEditorsTabbedPane()
                .getSelectedComponent();
        if (queryFile == null)
        {
            return;
        }
        String queryString = queryFile.getQuery(true);
        if (isBlank(queryString))
        {
            return;
        }

        QueryFileModel fileModel = queryFile.getFile();
        if (fileModel.getState() == State.EXECUTING)
        {
            return;
        }

        // Setup extensions from model data
        // Do this here also besides when properties changes,
        // since there is a possibility that no changes was made before a query is executed.
        fileModel.setupBeforeExecution();

        PayloadbuilderService.executeQuery(config, queryFile, queryString, () ->
        {
            QueryFileView current = (QueryFileView) view.getEditorsTabbedPane()
                    .getSelectedComponent();

            // Only update extensions panel if the completed query is current query file
            if (fileModel == current.getFile())
            {
                // Update properties in UI after query is finished
                // Change current index/instance/database etc. that was altered in query
                catalogExtensionViews.forEach(v -> v.init(fileModel));
            }
        });
    }

    /** Edit vars listener. */
    public void editVariablesAction()
    {
        QueryFileView queryFile = (QueryFileView) view.getEditorsTabbedPane()
                .getSelectedComponent();
        if (queryFile == null)
        {
            return;
        }
        String queryString = queryFile.getQuery(true);
        if (isBlank(queryString))
        {
            return;
        }

        QueryFileModel fileModel = queryFile.getFile();

        Set<String> variableNames = PayloadbuilderService.getVariables(queryString);

        for (String name : variableNames)
        {
            if (!fileModel.getVariables()
                    .containsKey(name))
            {
                fileModel.getVariables()
                        .put(name, null);
            }
        }

        variablesDialog.init(FilenameUtils.getName(fileModel.getFilename()), fileModel.getVariables());
        variablesDialog.setVisible(true);

        Map<String, Object> variables = variablesDialog.getVariables();
        if (variables != null)
        {
            fileModel.getVariables()
                    .clear();
            fileModel.getVariables()
                    .putAll(variables);
        }
    }

    /** Caret change listener. */
    private class CaretChangedListener implements Consumer<QueryFileView>
    {
        @Override
        public void accept(QueryFileView t)
        {
            view.getCaretLabel()
                    .setText(String.format("%d : %d : %d", t.getCaretLineNumber(), t.getCaretOffsetFromLineStart(), t.getCaretPosition()));
        }
    }

    /** New query action. */
    private void newQueryAction()
    {
        QueryFileModel queryFile = new QueryFileModel(config.getCatalogs());
        queryFile.setOutput(outputExtensions.get(0));
        queryFile.setFilename("Query" + (newFileCounter++) + ".sql");
        model.addFile(queryFile);
    }

    /** Open action. */
    private void openAction()
    {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setMultiSelectionEnabled(true);
        if (!isBlank(config.getLastOpenPath()))
        {
            fileChooser.setCurrentDirectory(new File(config.getLastOpenPath()));
        }
        if (fileChooser.showOpenDialog(view) == JFileChooser.APPROVE_OPTION)
        {
            if (fileChooser.getSelectedFiles().length <= 0)
            {
                return;
            }

            for (File selectedFile : fileChooser.getSelectedFiles())
            {
                QueryFileModel file = new QueryFileModel(config.getCatalogs(), selectedFile);
                file.setOutput(outputExtensions.get(0));
                model.addFile(file);
                config.appendRecentFile(selectedFile.getAbsolutePath());
            }

            // Store last selected path if differs
            if (!Objects.equals(config.getLastOpenPath(), fileChooser.getCurrentDirectory()
                    .getAbsolutePath()))
            {
                config.setLastOpenPath(fileChooser.getCurrentDirectory()
                        .getAbsolutePath());
            }
            saveConfig();
        }
    }

    /** Save action. */
    private void saveAction()
    {
        QueryFileView queryFile = (QueryFileView) view.getEditorsTabbedPane()
                .getSelectedComponent();
        if (queryFile != null)
        {
            QueryFileModel file = queryFile.getFile();
            save(file, false);
        }
    }

    /** Save As action. */
    private void saveAsAction()
    {
        QueryFileView queryFile = (QueryFileView) view.getEditorsTabbedPane()
                .getSelectedComponent();
        if (queryFile != null)
        {
            QueryFileModel file = queryFile.getFile();
            save(file, true);
        }
    }

    /** View actions */
    enum ViewAction
    {
        EXECUTE,
        CANCEL,
        NEWQUERY,
        OPEN,
        SAVE,
        SAVEAS,
        EXIT,
        TOGGLE_RESULT,
        TOGGLE_COMMENT,
        OUTPUT_CHANGED,
        FORMAT_CHANGED,
        EDIT_VARIABLES,
        CONFIG_OUTPUT,
        OPTIONS,
        ABOUT
    }
}
