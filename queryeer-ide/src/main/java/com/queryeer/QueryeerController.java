package com.queryeer;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.io.FileUtils.byteCountToDisplaySize;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.List;
import java.util.Objects;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.apache.commons.io.FilenameUtils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queryeer.api.IQueryFile.ExecutionState;
import com.queryeer.api.IQueryFileState;
import com.queryeer.api.event.QueryFileChangedEvent;
import com.queryeer.api.event.QueryFileStateEvent;
import com.queryeer.api.event.ShowOptionsEvent;
import com.queryeer.api.event.Subscribe;
import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.extensions.IQueryProvider;
import com.queryeer.api.extensions.output.IOutputExtension;
import com.queryeer.api.extensions.output.IOutputFormatExtension;
import com.queryeer.api.service.IEventBus;
import com.queryeer.event.QueryFileClosingEvent;

/** Main controller for Queryeer */
class QueryeerController implements PropertyChangeListener
{
    private static final int TIMER_INTERVAL = 250;
    static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final QueryeerView view;
    private final QueryeerModel model;
    private final Config config;
    private final IEventBus eventBus;
    private final List<IQueryProvider> queryProviders;
    private final OptionsDialog optionsDialog;
    private final AboutDialog aboutDialog;

    private int newFileCounter = 1;
    private final String version;
    private QueryFileProvider queryFileProvider;

    QueryeerController(Config config, QueryFileProvider queryFileProvider, List<IQueryProvider> queryProviders, IEventBus eventBus, QueryeerModel model, QueryeerView view, OptionsDialog optionsDialog)
    {
        this.config = requireNonNull(config, "config");
        this.queryFileProvider = requireNonNull(queryFileProvider, "queryFileProvider");
        this.queryProviders = requireNonNull(queryProviders, "queryProviders");
        this.eventBus = requireNonNull(eventBus, "eventBus");
        this.model = requireNonNull(model, "model");
        this.view = requireNonNull(view, "view");
        this.optionsDialog = requireNonNull(optionsDialog, "optionsDialog");
        this.model.addPropertyChangeListener(this);

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
        view.setActionHandler(this::handleViewAction);

        eventBus.register(this);

        view.getMemoryLabel()
                .setText(getMemoryString());
        view.setRecentFiles(config.getRecentFiles());
        new Timer(TIMER_INTERVAL, evt -> view.getMemoryLabel()
                .setText(getMemoryString())).start();

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
        newQueryAction(getDefaultQueryProvider(), null);
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

            view.populateQuickPropertiesPanel();
            queryFileProvider.setQueryFile(file);
            eventBus.publish(new QueryFileChangedEvent(file));
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

    private void handleViewAction(ViewAction action, Object data)
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
                formatChangedAction((IOutputFormatExtension) data);
                break;
            case NEWQUERY:
                newQueryAction((IQueryProvider) data, null);
                break;
            case OPEN:
                openAction();
                break;
            case OPEN_RECENT:
                openRecentFileAction((String) data);
                break;
            case OUTPUT_CHANGED:
                outputChangedAction((IOutputExtension) data);
                break;
            case SAVE:
                saveAction();
                break;
            case SAVEAS:
                saveAsAction();
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
        QueryFileView fileView = (QueryFileView) event.getQueryFile();
        QueryFileModel file = fileView.getFile();
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

        // TODO: store the query provider in recent files
        IQueryProvider queryProvider = getDefaultQueryProvider();

        newQueryAction(queryProvider, new File(file));
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

    private void outputChangedAction(IOutputExtension output)
    {
        QueryFileView queryFile = view.getCurrentFile();
        if (queryFile != null)
        {
            queryFile.getFile()
                    .setOutput(output);

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

    private void formatChangedAction(IOutputFormatExtension format)
    {
        QueryFileView queryFile = view.getCurrentFile();
        if (queryFile != null)
        {
            queryFile.getFile()
                    .setOutputFormat(format);
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
        if (queryFile != null
                && queryFile.getFile()
                        .getExecutionState() == ExecutionState.EXECUTING)
        {
            queryFile.getFile()
                    .setExecutionState(ExecutionState.ABORTED);
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

        QueryFileModel queryFile = fileView.getFile();

        if (queryFile.getExecutionState() == ExecutionState.EXECUTING)
        {
            return;
        }

        eventBus.publish(new QueryFileStateEvent(fileView, QueryFileStateEvent.State.BEFORE_QUERY_EXECUTE));
        QueryService.executeQuery(fileView, () ->
        {
            eventBus.publish(new QueryFileStateEvent(fileView, QueryFileStateEvent.State.AFTER_QUERY_EXECUTE));
        });
    }

    /** New query action. */
    private void newQueryAction(IQueryProvider queryProvider, File file)
    {
        IOutputExtension outputExtension = (IOutputExtension) view.getOutputCombo()
                .getSelectedItem();
        IOutputFormatExtension outputFormatExtension = (IOutputFormatExtension) view.getFormatCombo()
                .getSelectedItem();

        IQueryFileState newState = null;

        // Use the same query provider as the current opened file if not explicitly specified
        if (queryProvider == null)
        {
            IQueryFileState currentState = view.getCurrentFile()
                    .getFile()
                    .getQueryFileState();
            queryProvider = currentState.getQueryProvider();
            newState = currentState.cloneState();
        }

        if (newState == null)
        {
            newState = queryProvider.createQueryFileState();
        }

        QueryFileModel queryFile = new QueryFileModel(newState, outputExtension, outputFormatExtension, file);
        if (file == null)
        {
            queryFile.setFilename("Query" + (newFileCounter++) + ".sql");
        }
        else
        {
            queryFile.load();
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
                    // TODO: open as query provider
                    IQueryProvider queryProvider = getDefaultQueryProvider();

                    newQueryAction(queryProvider, selectedFile);
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

    private IQueryProvider getDefaultQueryProvider()
    {
        for (IQueryProvider queryProvider : queryProviders)
        {
            if (isBlank(config.getDefaultQueryProvider())
                    || queryProvider.getClass()
                            .getName()
                            .equals(config.getDefaultQueryProvider()))
            {
                return queryProvider;
            }
        }

        return queryProviders.get(0);
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
        OPEN_RECENT,
        SAVE,
        SAVEAS,
        EXIT,
        TOGGLE_RESULT,
        OUTPUT_CHANGED,
        FORMAT_CHANGED,
        OPTIONS,
        ABOUT
    }
}
