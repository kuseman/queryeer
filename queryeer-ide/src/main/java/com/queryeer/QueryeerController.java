package com.queryeer;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.io.FileUtils.byteCountToDisplaySize;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queryeer.QueryFileModel.State;
import com.queryeer.api.editor.IEditor;
import com.queryeer.api.event.ExecuteQueryEvent;
import com.queryeer.api.event.ExecuteQueryEvent.OutputType;
import com.queryeer.api.event.NewQueryFileEvent;
import com.queryeer.api.event.ShowOptionsEvent;
import com.queryeer.api.event.Subscribe;
import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.extensions.engine.IQueryEngine;
import com.queryeer.api.extensions.output.IOutputComponent;
import com.queryeer.api.extensions.output.IOutputExtension;
import com.queryeer.api.extensions.output.IOutputFormatExtension;
import com.queryeer.api.extensions.output.queryplan.IQueryPlanOutputComponent;
import com.queryeer.api.extensions.output.table.ITableOutputComponent;
import com.queryeer.api.extensions.output.text.ITextOutputComponent;
import com.queryeer.api.service.IEventBus;
import com.queryeer.event.QueryFileClosingEvent;

import se.kuseman.payloadbuilder.api.OutputWriter;
import se.kuseman.payloadbuilder.core.CsvOutputWriter;

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
    private final String version;
    private final QueryFileProvider queryFileProvider;

    private int newFileCounter = 1;

    QueryeerController(Config config, QueryFileProvider queryFileProvider, IEventBus eventBus, QueryeerModel model, QueryeerView view, OptionsDialog optionsDialog)
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
        init();
    }

    // CSOFF
    private void init()
    // CSON
    {
        view.setTitle("Queryeer IDE - " + version);
        view.setActionHandler(this::handleViewAction);
        view.setOpenRecentFileConsumer(this::openRecentFileAction);
        view.setNewQueryConsumer(qe -> newQueryAction(qe, null, null, Optional.empty()));

        eventBus.register(this);

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

        // Open previous open files
        if (config.isOpenLastOpenFiles()
                && !config.getLastOpenFiles()
                        .isEmpty())
        {
            for (String lastOpenFile : config.getLastOpenFiles())
            {
                newQueryAction(new File(lastOpenFile));
            }
        }
        else
        {
            // ... else open a new query
            newQueryAction(null);
        }
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
            if (evt.getNewValue() == null)
            {
                return;
            }

            QueryFileView viewFile = view.getCurrentFile();
            QueryFileModel model = viewFile.getModel();

            // See if the file has been modified outside of application
            if (!model.isNew()
                    && model.isModified())
            {
                int result = JOptionPane.showConfirmDialog(view, """
                        '%s'
                        has been modified by another application.
                        Replace editor content?
                        """.formatted(model.getFile()
                        .getAbsolutePath()), "Reload", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);

                // Reload content
                if (result == JOptionPane.YES_OPTION)
                {
                    model.load();
                }
                // ... mark the file as dirty
                else
                {
                    model.setDirty(true);
                }
            }

            // Need to set the new file before updating UI state so extensions work correctly
            queryFileProvider.setQueryFile(viewFile);
            view.updateState(viewFile);
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
            fileChooser.setSelectedFile(file.getFile());
            int result = fileChooser.showSaveDialog(view);
            if (result == JFileChooser.APPROVE_OPTION)
            {
                file.setFile(fileChooser.getSelectedFile());
            }
            else if (result == JFileChooser.CANCEL_OPTION)
            {
                return false;
            }
        }

        file.save();
        config.appendRecentFile(file.getFile()
                .getAbsolutePath());
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

    @Subscribe
    private void newQueryEvent(NewQueryFileEvent event)
    {
        newQueryAction(event.getQueryEngine(), event.getState(), null, Optional.ofNullable(event.getEditorValue()));
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

    private void setLastOpenFiles()
    {
        // Store open files
        if (config.isOpenLastOpenFiles())
        {
            List<String> list = model.getFiles()
                    .stream()
                    .filter(f -> !f.isNew())
                    .map(f -> f.getFile()
                            .getAbsolutePath())
                    .toList();
            config.setLastOpenfiles(list);
            config.save();
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
            setLastOpenFiles();
            model.close();
            System.exit(0);
        }

        int result = JOptionPane.showConfirmDialog(view, "Save changes to the following files: " + System.lineSeparator()
                                                         + dirtyFiles.stream()
                                                                 .map(f -> FilenameUtils.getName(f.getFile()
                                                                         .getAbsolutePath()))
                                                                 .collect(joining(System.lineSeparator())),
                "Unsaved changes", JOptionPane.YES_NO_CANCEL_OPTION);

        if (result == JOptionPane.CANCEL_OPTION)
        {
            return;
        }
        else if (result == JOptionPane.NO_OPTION)
        {
            setLastOpenFiles();
            model.close();
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

        setLastOpenFiles();
        model.close();
        System.exit(0);
    }

    private void outputChangedAction()
    {
        QueryFileView queryFile = view.getCurrentFile();
        if (queryFile != null)
        {
            queryFile.getModel()
                    .setOutputExtension((IOutputExtension) view.getOutputCombo()
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
            queryFile.getModel()
                    .getQueryEngine()
                    .abortQuery(queryFile);
            if (queryFile.getModel()
                    .getState()
                    .isExecuting())
            {
                queryFile.getModel()
                        .setState(State.ABORTED);
            }
        }
    }

    @Subscribe
    private void executeFromEvent(ExecuteQueryEvent event)
    {
        QueryFileView fileView = view.getCurrentFile();
        if (view == null)
        {
            return;
        }

        QueryFileModel queryFile = fileView.getModel();
        if (queryFile.getState()
                .isExecuting())
        {
            return;
        }

        OutputWriter ow = null;
        Writer w;
        Class<? extends IOutputComponent> componentToSelect = null;
        Class<? extends IOutputComponent> componentToSelectAfterQuery = null;

        switch (event.getOutputType())
        {
            case QUERY_PLAN:
            case TABLE:
                componentToSelect = ITableOutputComponent.class;
                ow = fileView.getOutputComponent(componentToSelect)
                        .getExtension()
                        .createOutputWriter(fileView);

                if (event.getOutputType() == OutputType.QUERY_PLAN)
                {
                    componentToSelectAfterQuery = IQueryPlanOutputComponent.class;
                }

                break;
            case TEXT:
                componentToSelect = ITextOutputComponent.class;
                ow = new TextOutputWriter(fileView.getMessagesWriter());
                break;
            case NEW_QUERY:
            case CLIPBOARD:
                final boolean clipboard = event.getOutputType() == OutputType.CLIPBOARD;
                w = new StringWriter()
                {
                    @Override
                    public void close() throws IOException
                    {
                        String value = this.toString();
                        if (clipboard)
                        {
                            StringSelection stringSelection = new StringSelection(value);
                            Clipboard clipboard = Toolkit.getDefaultToolkit()
                                    .getSystemClipboard();
                            clipboard.setContents(stringSelection, null);
                            fileView.getMessagesWriter()
                                    .println("Copied to clipboard");
                            fileView.focusMessages();
                        }
                        else
                        {
                            SwingUtilities.invokeLater(() -> newQueryAction(null, Optional.of(value)));
                        }
                    }
                };
                ow = new TextOutputWriter(w);
                break;
            default:
                throw new IllegalArgumentException("Unsupported output type " + event.getOutputType());
        }
        fileView.selectOutputComponent(componentToSelect);

        Class<? extends IOutputComponent> c = componentToSelectAfterQuery;
        QueryService.executeQuery(fileView, ow, event.getContext(), true, () ->
        {
            if (c != null)
            {
                SwingUtilities.invokeLater(() -> fileView.selectOutputComponent(c));
            }
        });
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

        if (!queryFile.getQueryEngine()
                .shouldExecute(fileView))
        {
            return;
        }

        if (queryFile.getState()
                .isExecuting())
        {
            return;
        }

        OutputWriter writer = queryFile.getOutputExtension()
                .createOutputWriter(fileView);

        if (writer == null)
        {
            return;
        }

        Object query = fileView.getEditor()
                .getValue();

        QueryService.executeQuery(fileView, getOutputWriter(fileView, writer), query, false, () ->
        {
        });
    }

    /** Creates a proxy outputwriter that writes to multiple output writers. */
    private static OutputWriter getOutputWriter(QueryFileView fileView, OutputWriter masterWriter)
    {
        List<OutputWriter> activeAutoPopulatedWriters = fileView.getOutputComponents()
                .stream()
                .filter(o -> o.getExtension()
                        .isAutoPopulated()
                        && o.active())
                .map(o -> o.getExtension()
                        .createOutputWriter(fileView))
                .toList();

        // No auto populating components return the master writer
        if (activeAutoPopulatedWriters.isEmpty())
        {
            return masterWriter;
        }

        List<OutputWriter> result = new ArrayList<>(activeAutoPopulatedWriters);
        result.add(0, masterWriter);
        return new ProxyOutputWriter(result);
    }

    /** New query action. */
    private void newQueryAction(File file)
    {
        newQueryAction(file, Optional.empty());
    }

    /** New query action. */
    private void newQueryAction(File file, Optional<Object> editorValue)
    {
        IQueryEngine engine = null;
        IQueryEngine.IState state = null;

        QueryFileView currentFile = view.getCurrentFile();
        // New query window -> use current tabs query engine
        if (file == null)
        {
            // Use the current tabs query engine
            if (currentFile != null)
            {
                engine = currentFile.getModel()
                        .getQueryEngine();

                state = engine.cloneState(currentFile);
            }
            else
            {
                engine = config.getDefaultQueryEngine();
            }
        }
        else
        {
            engine = config.getQueryEngine(file.getName());

            // If the new file's engine is the same engine as the current opened file's one then clone it's state
            if (currentFile != null
                    && engine == currentFile.getModel()
                            .getQueryEngine())
            {
                state = currentFile.getModel()
                        .getQueryEngine()
                        .cloneState(currentFile);
            }
        }

        newQueryAction(engine, state, file, editorValue);
    }

    /** New query action. */
    private void newQueryAction(IQueryEngine queryEngine, IQueryEngine.IState engineState, File file, Optional<Object> editorValue)
    {
        // CSOFF
        IOutputExtension outputExtension = (IOutputExtension) view.getOutputCombo()
                .getSelectedItem();
        IOutputFormatExtension outputFormatExtension = (IOutputFormatExtension) view.getFormatCombo()
                .getSelectedItem();
        boolean newFile = file == null;
        // CSON

        if (file == null)
        {
            String extension = queryEngine.getDefaultFileExtension();
            if (!extension.startsWith("."))
            {
                extension = "." + extension;
            }
            file = new File("Query" + (newFileCounter++) + extension);
        }

        if (engineState == null)
        {
            engineState = queryEngine.createState();
        }

        IEditor editor = queryEngine.createEditor(engineState, file.getName());

        if (editorValue.isPresent())
        {
            editor.setValue(editorValue.get());
        }

        QueryFileModel queryFile = new QueryFileModel(queryEngine, engineState, editor, outputExtension, outputFormatExtension, file, newFile);

        // Load the new file into editor
        if (!newFile)
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
        OUTPUT_CHANGED,
        FORMAT_CHANGED,
        OPTIONS,
        ABOUT
    }

    /**
     * Output writer used when events need to output text/clipboard/new query. We utilize the CSV writer for this.
     */
    private static class TextOutputWriter extends CsvOutputWriter
    {
        private static final CsvOutputWriter.CsvSettings SETTINGS;
        static
        {
            SETTINGS = new CsvSettings();
            SETTINGS.setEscapeNewLines(false);
            // Nothing should be escaped
            SETTINGS.setSeparatorChar('\0');
            SETTINGS.setRowSeparator(System.lineSeparator());
        }

        TextOutputWriter(Writer writer)
        {
            super(writer, SETTINGS);
        }
    }
}
