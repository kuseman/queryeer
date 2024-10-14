package com.queryeer;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.io.FileUtils.byteCountToDisplaySize;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.List;
import java.util.Objects;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queryeer.QueryFileModel.State;
import com.queryeer.api.event.QueryFileChangedEvent;
import com.queryeer.api.event.QueryFileStateEvent;
import com.queryeer.api.event.Subscribe;
import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.extensions.catalog.ICatalogExtension;
import com.queryeer.api.extensions.output.IOutputExtension;
import com.queryeer.api.extensions.output.IOutputFormatExtension;
import com.queryeer.api.service.IEventBus;
import com.queryeer.component.CatalogExtensionViewFactory;
import com.queryeer.domain.ICatalogModel;
import com.queryeer.event.CaretChangedEvent;
import com.queryeer.event.QueryFileClosingEvent;
import com.queryeer.event.ShowOptionsEvent;

import se.kuseman.payloadbuilder.core.execution.QuerySession;

/** Main controller for Queryeer */
class QueryeerController implements PropertyChangeListener
{
    private static final int TIMER_INTERVAL = 250;
    static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final QueryeerView view;
    private final QueryeerModel model;
    private final Config config;
    private final IEventBus eventBus;
    private final OptionsDialog optionsDialog;
    private final AboutDialog aboutDialog;

    private int newFileCounter = 1;
    private final String version;
    private QueryFileProvider queryFileProvider;

    QueryeerController(Config config, QueryFileProvider queryFileProvider, IEventBus eventBus, QueryeerModel model, QueryeerView view, OptionsDialog optionsDialog,
            CatalogExtensionViewFactory catalogExtensionViewFactory)
    {
        this.view = requireNonNull(view, "view");
        this.config = requireNonNull(config, "config");

        this.queryFileProvider = requireNonNull(queryFileProvider, "queryFileProvider");
        this.eventBus = requireNonNull(eventBus, "eventBus");
        this.model = requireNonNull(model, "model");
        this.model.addPropertyChangeListener(this);
        this.optionsDialog = requireNonNull(optionsDialog, "optionsDialog");

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

        init(catalogExtensionViewFactory);
    }

    // CSOFF
    private void init(CatalogExtensionViewFactory catalogExtensionViewFactory)
    // CSON
    {
        view.setTitle("Queryeer IDE - " + version);
        view.setActionHandler(this::handleViewAction);
        view.setOpenRecentFileConsumer(this::openRecentFileAction);

        eventBus.register(this);

        int y = 0;
        Insets insets = new Insets(0, 0, 3, 0);
        for (ICatalogModel catalog : config.getCatalogs())
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

            Component extensionView = catalogExtensionViewFactory.create(extension, catalog.getAlias());
            view.getPanelCatalogs()
                    .add(extensionView, new GridBagConstraints(0, y++, 1, 1, 1, 0, GridBagConstraints.BASELINE, GridBagConstraints.HORIZONTAL, insets, 0, 0));
        }
        view.getPanelCatalogs()
                .add(new JPanel(), new GridBagConstraints(0, y, 1, 1, 1, 1, GridBagConstraints.BASELINE, GridBagConstraints.HORIZONTAL, insets, 0, 0));
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
        newQueryAction(null);
    }

    Component getView()
    {
        return view;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt)
    {
        if (QueryeerModel.SELECTED_FILE.equals(evt.getPropertyName()))
        {
            QueryFileView file = view.getCurrentFile();

            // See if the file has been modified outside of application
            if (!file.getModel()
                    .isNew())
            {
                long lastModified = file.getModel()
                        .getLastModified();
                File ioFile = new File(file.getModel()
                        .getFilename());

                if (lastModified != ioFile.lastModified())
                {
                    int result = JOptionPane.showConfirmDialog(view, """
                            '%s'
                            has been modified by another application.
                            Replace editor content?
                            """.formatted(ioFile.getAbsolutePath()), "Reload", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);

                    // Reload content
                    if (result == JOptionPane.YES_OPTION)
                    {
                        file.getModel()
                                .reloadFromFile();
                    }
                    // ... mark the file as dirty
                    else
                    {
                        file.getModel()
                                .setDirty(true);
                    }
                }
            }

            queryFileProvider.setQueryFile(file);
            eventBus.publish(new QueryFileChangedEvent(file));
            /* Publish caret change to update the current since there is no editor change when switching tab */
            eventBus.publish(new CaretChangedEvent(file.getModel()
                    .getCaret()));
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

    /** Util method for saving a queryfile. Opens dialog and asks for overwrite etc. */
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

    private void handleViewAction(ViewAction action)
    {
        switch (action)
        {
            case CANCEL:
                cancelAction();
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
                newQueryAction(null);
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

    @Subscribe
    private void closeQueryFile(QueryFileClosingEvent event)
    {
        QueryFileModel file = ((QueryFileView) event.getQueryFile()).getModel();
        if (file.isDirty())
        {
            int result = JOptionPane.showConfirmDialog(view, "Save changes ?", "Save", JOptionPane.YES_NO_CANCEL_OPTION);
            if (result == JOptionPane.CANCEL_OPTION)
            {
                return;
            }
            else if (result == JOptionPane.YES_OPTION)
            {
                if (!save(file, false))
                {
                    return;
                }
            }
        }
        model.removeFile(file);
    }

    @Subscribe
    private void showOptions(ShowOptionsEvent event)
    {
        showOptionsAction(event.getConfigurableClassToSelect());
    }

    private void openRecentFileAction(String file)
    {
        if (model.select(file))
        {
            return;
        }

        File recentFile = new File(file);
        if (!recentFile.exists())
        {
            JOptionPane.showMessageDialog(view, "'" + recentFile.getAbsolutePath() + "' does not exists!", "File Not Found", JOptionPane.INFORMATION_MESSAGE);
            config.removeRecentFile(file);
            saveConfig();
            return;
        }

        newQueryAction(recentFile);
        config.appendRecentFile(file);
        saveConfig();
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
        QueryFileView queryFile = view.getCurrentFile();
        if (queryFile != null)
        {
            queryFile.getModel()
                    .setOutput((IOutputExtension) view.getOutputCombo()
                            .getSelectedItem());

            // Set output format accordingly
            IOutputFormatExtension outputFormat = queryFile.getModel()
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
        QueryFileView queryFile = view.getCurrentFile();
        if (queryFile != null)
        {
            queryFile.getModel()
                    .setOutputFormat((IOutputFormatExtension) view.getFormatCombo()
                            .getSelectedItem());
        }
    }

    private void toggleCommentAction()
    {
        QueryFileView queryFile = view.getCurrentFile();
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
        QueryFileView queryFile = view.getCurrentFile();
        if (queryFile != null)
        {
            queryFile.toggleResultPane();
        }
    }

    /** Cancel action */
    private void cancelAction()
    {
        QueryFileView queryFile = view.getCurrentFile();
        if (queryFile != null)
        {
            ((QuerySession) queryFile.getSession()).fireAbortQueryListeners();
            if (queryFile.getModel()
                    .getState() == State.EXECUTING)
            {
                queryFile.getModel()
                        .setState(State.ABORTED);
            }
        }
    }

    /** Execute listener. */
    private void executeAction()
    {
        QueryFileView fileView = view.getCurrentFile();
        if (view == null)
        {
            return;
        }

        QueryFileModel queryFile = fileView.getModel();

        if (queryFile.getState() == State.EXECUTING)
        {
            return;
        }

        eventBus.publish(new QueryFileStateEvent(fileView, QueryFileStateEvent.State.BEFORE_QUERY_EXECUTE));
        PayloadbuilderService.executeQuery(fileView, () ->
        {
            eventBus.publish(new QueryFileStateEvent(fileView, QueryFileStateEvent.State.AFTER_QUERY_EXECUTE));
        });
    }

    /** New query action. */
    private void newQueryAction(File file)
    {
        IOutputExtension outputExtension = (IOutputExtension) view.getOutputCombo()
                .getSelectedItem();
        IOutputFormatExtension outputFormatExtension = (IOutputFormatExtension) view.getFormatCombo()
                .getSelectedItem();

        QueryFileModel queryFile = new QueryFileModel(config.getCatalogs(), outputExtension, outputFormatExtension, file);
        if (file == null)
        {
            queryFile.setFilename("Query" + (newFileCounter++) + ".sql");
        }
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

            boolean saveConfig = false;
            for (File selectedFile : fileChooser.getSelectedFiles())
            {
                if (!model.select(selectedFile.getAbsolutePath()))
                {
                    selectedFile = new File(StringUtils.trim(selectedFile.getAbsolutePath()));
                    newQueryAction(selectedFile);
                    config.appendRecentFile(selectedFile.getAbsolutePath());
                    saveConfig = true;
                }
            }

            // Store last selected path if differs
            if (!Objects.equals(config.getLastOpenPath(), fileChooser.getCurrentDirectory()
                    .getAbsolutePath()))
            {
                config.setLastOpenPath(fileChooser.getCurrentDirectory()
                        .getAbsolutePath());
                saveConfig = true;
            }
            if (saveConfig)
            {
                saveConfig();
            }
        }
    }

    /** Save action. */
    private void saveAction()
    {
        QueryFileModel file = model.getSelectedFile();
        if (file != null)
        {
            save(file, false);
        }
    }

    /** Save As action. */
    private void saveAsAction()
    {
        QueryFileModel file = model.getSelectedFile();
        if (file != null)
        {
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
        OPTIONS,
        ABOUT
    }
}
