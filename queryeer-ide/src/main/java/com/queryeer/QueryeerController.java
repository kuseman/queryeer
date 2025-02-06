package com.queryeer;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.io.FileUtils.byteCountToDisplaySize;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.Component;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.Window;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queryeer.Config.QueryEngineAssociation;
import com.queryeer.Config.QueryeerSession;
import com.queryeer.Config.QueryeerSessionFile;
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
import com.queryeer.event.QueryFileSaveEvent;

import se.kuseman.payloadbuilder.api.OutputWriter;
import se.kuseman.payloadbuilder.core.CsvOutputWriter;

/** Main controller for Queryeer */
class QueryeerController implements PropertyChangeListener
{
    private static final int TIMER_INTERVAL = 250;
    private static final Logger LOGGER = LoggerFactory.getLogger(QueryeerController.class);
    static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final QueryeerView view;
    private final QueryeerModel model;
    private final Config config;
    private final IEventBus eventBus;
    private final OptionsDialog optionsDialog;
    private final AboutDialog aboutDialog;
    private final String version;
    private final List<IOutputExtension> outputExtensions;
    private boolean dialogShowing;
    private boolean init;

    private int newFileCounter = 1;

    QueryeerController(Config config, IEventBus eventBus, QueryeerModel model, QueryeerView view, List<IOutputExtension> outputExtensions, List<IConfigurable> configurables)
    {
        this.view = requireNonNull(view, "view");
        this.config = requireNonNull(config, "config");
        this.eventBus = requireNonNull(eventBus, "eventBus");
        this.model = requireNonNull(model, "model");
        this.model.addPropertyChangeListener(this);
        this.optionsDialog = new OptionsDialog(view, requireNonNull(configurables, "configurables"));
        this.outputExtensions = requireNonNull(outputExtensions, "outputExtensions");

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

        this.aboutDialog = new AboutDialog(view, version, config.getEtcFolder(), config.getSharedFolder());

        init = true;
        init();
        initSession();
        init = false;
    }

    // CSOFF
    private void init()
    // CSON
    {
        view.setTitle("Queryeer IDE - " + version);
        view.setActionHandler(this::handleViewAction);
        view.setOpenRecentFileConsumer(this::openRecentFileAction);
        view.setNewQueryConsumer(qe -> newQueryAction(qe, null, null, null, null, true, Optional.empty()));

        eventBus.register(this);

        view.getMemoryLabel()
                .setText(getMemoryString());
        new Timer(TIMER_INTERVAL, evt -> view.getMemoryLabel()
                .setText(getMemoryString())).start();
        view.setRecentFiles(config.getRecentFiles());

        Thread t = new Thread(() ->
        {
            final String message = aboutDialog.getNewVersionString();
            if (message != null)
            {
                SwingUtilities.invokeLater(() ->
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
                });
            }
        });
        t.setDaemon(true);
        t.setName("VersionChecker");
        t.start();
    }

    private void initSession()
    {
        QueryeerSession session = config.getSession();
        boolean saveSession = false;
        Iterator<QueryeerSessionFile> it = new ArrayList<>(session.files).iterator();
        while (it.hasNext())
        {
            QueryeerSessionFile sessionFile = it.next();
            // Clean up trash
            if (sessionFile.file == null)
            {
                it.remove();
                saveSession = true;
                continue;
            }

            // Correct wrong new state
            if (sessionFile.file != null
                    && sessionFile.file.exists())
            {
                sessionFile.isNew = false;
            }

            if (sessionFile.isNew)
            {
                newFileCounter++;
            }

            // Verify backup file
            if (sessionFile.backupFile != null
                    && !sessionFile.backupFile.exists())
            {
                // Check if the regular file exists, if so simply use the real file and ditch the backup
                if (sessionFile.file != null
                        && sessionFile.file.exists())
                {
                    sessionFile.backupFile = null;
                }
                else
                {
                    // Create a dummy file with descriptive text about backup file was removed
                    try
                    {
                        FileUtils.write(sessionFile.backupFile, """
                                Backup file: %s could not be found
                                """.formatted(sessionFile.backupFile), StandardCharsets.UTF_8);
                    }
                    catch (IOException e)
                    {
                        LOGGER.error("Error wrting to backupfile: {}", sessionFile.backupFile, e);
                    }
                }

                saveSession = true;
            }

            newQueryAction(sessionFile.file, sessionFile.backupFile, null, sessionFile.isNew, Optional.empty());
        }

        if (session.activeFileIndex >= model.getFiles()
                .size())
        {
            session.activeFileIndex = 0;
            saveSession = true;
        }

        if (saveSession)
        {
            config.saveSession();
        }

        // Add a new empty query if nothing was loaded
        if (session.files.isEmpty())
        {
            newQueryAction(null);
        }
        else
        {
            QueryFileModel fileModel = model.getFiles()
                    .get(session.activeFileIndex);
            model.setSelectedFile(fileModel);
        }

        model.init();
    }

    Component getView()
    {
        return view;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt)
    {
        if (QueryeerModel.SELECTED_FILE.equals(evt.getPropertyName())
                || QueryeerModel.SELECTED_FILE_ALTERED.equals(evt.getPropertyName()))
        {
            if (evt.getNewValue() == null)
            {
                return;
            }

            QueryFileModel fileModel = model.getSelectedFile();

            // If the user switched tab while event was firing, drop out
            if (QueryeerModel.SELECTED_FILE_ALTERED.equals(evt.getPropertyName())
                    && fileModel != evt.getNewValue())
            {
                return;
            }

            // Has file been deleted outside of application
            if (!fileModel.isNew()
                    && !fileModel.getFile()
                            .exists())
            {
                if (dialogShowing
                        || init)
                {
                    return;
                }
                dialogShowing = true;
                int result = JOptionPane.showConfirmDialog(view, """
                        '%s'
                        has been removed by another application.
                        Keep file in editor ?
                        """.formatted(fileModel.getFile()
                        .getAbsolutePath()), "Keep File", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
                dialogShowing = false;
                if (result == JOptionPane.YES_OPTION)
                {
                    fileModel.setDirty(true);
                    // Make sure we trigger backupdirty even if the file wasn't dirty
                    // when deleted
                    fileModel.backupDirty = true;
                }
                else
                {
                    this.model.removeFile(fileModel);
                    return;
                }
            }
            // See if the file has been modified outside of application
            else if (!fileModel.isNew()
                    && fileModel.isModified())
            {
                if (dialogShowing
                        || init)
                {
                    return;
                }
                dialogShowing = true;
                int result = JOptionPane.showConfirmDialog(view, """
                        '%s'
                        has been modified by another application.
                        Replace editor content?
                        """.formatted(fileModel.getFile()
                        .getAbsolutePath()), "Reload", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
                dialogShowing = false;
                // Reload content
                if (result == JOptionPane.YES_OPTION)
                {
                    fileModel.load();
                }
                // ... mark the file as dirty
                else
                {
                    fileModel.setDirty(true);
                }
            }
        }
    }

    private String getMemoryString()
    {
        Runtime runtime = Runtime.getRuntime();
        return String.format("%s / %s", byteCountToDisplaySize(runtime.totalMemory()), byteCountToDisplaySize(runtime.totalMemory() - runtime.freeMemory()));
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
            Window activeWindow = javax.swing.FocusManager.getCurrentManager()
                    .getActiveWindow();
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
                        int result = JOptionPane.showConfirmDialog(activeWindow, "The file exists, overwrite?", "Existing file", JOptionPane.YES_NO_CANCEL_OPTION);
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
            int result = fileChooser.showSaveDialog(activeWindow);
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
        model.fileSaved(file);
        config.appendRecentFile(file.getFile()
                .getAbsolutePath());
        view.setRecentFiles(config.getRecentFiles());
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
    private void saveQueryFile(QueryFileSaveEvent event)
    {
        event.setCanceled(save((QueryFileModel) event.getQueryFile(), false));
    }

    @Subscribe
    private void closeQueryFile(QueryFileClosingEvent event)
    {
        QueryFileModel file = (QueryFileModel) event.getQueryFile();

        // If the file is currently running, abort it
        if (file.getState()
                .isExecuting())
        {
            file.getQueryEngine()
                    .abortQuery(file);
            file.setState(State.ABORTED);
        }

        if (file.isDirty())
        {
            Window activeWindow = javax.swing.FocusManager.getCurrentManager()
                    .getActiveWindow();
            int result = JOptionPane.showConfirmDialog(activeWindow, "Save changes ?", "Save: " + file.getFile()
                    .getName(), JOptionPane.YES_NO_CANCEL_OPTION);
            if (result == JOptionPane.CANCEL_OPTION
                    || result < 0)
            {
                event.setCanceled(true);
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
        if (event.getQueryEngine() != null)
        {
            newQueryAction(event.getQueryEngine(), event.getState(), null, null, event.getNewQueryName(), true, Optional.ofNullable(event.getEditorValue()));
            if (event.isExecute())
            {
                executeAction();
            }
        }
        else if (event.getFile() != null)
        {
            if (!model.select(event.getFile()
                    .getAbsolutePath()))
            {
                newQueryAction(event.getFile());
            }
        }
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
            view.setRecentFiles(config.getRecentFiles());
            return;
        }

        newQueryAction(recentFile);
        config.appendRecentFile(file);
        view.setRecentFiles(config.getRecentFiles());
    }

    /** Exit action */
    private void exitAction()
    {
        model.close();
        System.exit(0);
    }

    private void outputChangedAction()
    {
        QueryFileModel fileModel = model.getSelectedFile();
        if (fileModel != null)
        {
            fileModel.setOutputExtension((IOutputExtension) view.getOutputCombo()
                    .getSelectedItem());

            // Set output format accordingly
            IOutputFormatExtension outputFormat = fileModel.getOutputFormat();
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
        QueryFileModel fileModel = model.getSelectedFile();
        if (fileModel != null)
        {
            fileModel.setOutputFormat((IOutputFormatExtension) view.getFormatCombo()
                    .getSelectedItem());
        }
    }

    private void showOptionsAction(Class<? extends IConfigurable> configurableToSelect)
    {
        SwingUtilities.invokeLater(() ->
        {
            optionsDialog.setSelectedConfigurable(configurableToSelect);
            optionsDialog.setVisible(true);
        });
    }

    /** Cancel action */
    private void cancelAction()
    {
        QueryFileModel queryFile = model.getSelectedFile();
        if (queryFile != null)
        {
            queryFile.getQueryEngine()
                    .abortQuery(queryFile);
            if (queryFile.getState()
                    .isExecuting())
            {
                queryFile.setState(State.ABORTED);
            }
        }
    }

    @Subscribe
    private void executeFromEvent(ExecuteQueryEvent event)
    {
        QueryFileModel queryFile = model.getSelectedFile();
        if (queryFile == null)
        {
            return;
        }

        if (queryFile.getState()
                .isExecuting())
        {
            return;
        }

        OutputWriter ow = null;
        Writer w;
        Class<? extends IOutputComponent> componentToSelect = null;
        Class<? extends IOutputComponent> componentToSelectAfterQuery = null;
        boolean doExecute = false;

        switch (event.getOutputType())
        {
            case QUERY_PLAN:
            case TABLE:
                componentToSelect = ITableOutputComponent.class;
                ow = queryFile.getOutputComponent(componentToSelect)
                        .getExtension()
                        .createOutputWriter(queryFile);

                if (event.getOutputType() == OutputType.QUERY_PLAN)
                {
                    componentToSelectAfterQuery = IQueryPlanOutputComponent.class;
                    IOutputComponent outputComponent = queryFile.getOutputComponent(componentToSelectAfterQuery);
                    if (outputComponent != null)
                    {
                        outputComponent.clearState();
                    }
                }

                // If we are outputing to the table then clear it before, it's weird with an appending
                queryFile.getOutputComponent(componentToSelect)
                        .clearState();
                break;
            case TEXT:
                componentToSelect = ITextOutputComponent.class;
                ow = new TextOutputWriter(queryFile.getMessagesWriter());
                break;
            case NEW_QUERY_EXECUTE:
                doExecute = true;
                // CSOFF
            case NEW_QUERY:
                // CSON
            case CLIPBOARD:
                final boolean clipboard = event.getOutputType() == OutputType.CLIPBOARD;
                final boolean execute = doExecute;
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
                            queryFile.getMessagesWriter()
                                    .println("Copied to clipboard");
                            queryFile.focusMessages();
                        }
                        else
                        {
                            SwingUtilities.invokeLater(() ->
                            {
                                newQueryAction(null, null, event.getNewQueryName(), true, Optional.of(value));
                                if (execute)
                                {
                                    executeAction();
                                }
                            });
                        }
                    }
                };
                ow = new TextOutputWriter(w);
                break;
            default:
                throw new IllegalArgumentException("Unsupported output type " + event.getOutputType());
        }
        if (componentToSelect != null)
        {
            queryFile.selectOutputComponent(componentToSelect);
        }
        Class<? extends IOutputComponent> c = componentToSelectAfterQuery;
        QueryService.executeQuery(queryFile, ow, event.getContext(), true, () ->
        {
            if (c != null)
            {
                SwingUtilities.invokeLater(() -> queryFile.selectOutputComponent(c));
            }
        });
    }

    /** Execute listener. */
    private void executeAction()
    {
        QueryFileModel queryFile = model.getSelectedFile();
        if (queryFile == null)
        {
            return;
        }

        if (!queryFile.getQueryEngine()
                .shouldExecute(queryFile))
        {
            return;
        }

        if (queryFile.getState()
                .isExecuting())
        {
            return;
        }

        // Don't run empty queries
        if (queryFile.getEditor()
                .isValueEmpty())
        {
            return;
        }

        OutputWriter writer = queryFile.getOutputExtension()
                .createOutputWriter(queryFile);

        if (writer == null)
        {
            return;
        }

        Object query = queryFile.getEditor()
                .getValue(false);

        QueryService.executeQuery(queryFile, getOutputWriter(queryFile, writer), query, false, () ->
        {
        });
    }

    /** Creates a proxy outputwriter that writes to multiple output writers. */
    private static OutputWriter getOutputWriter(QueryFileModel file, OutputWriter masterWriter)
    {
        List<OutputWriter> activeAutoPopulatedWriters = file.getOutputComponents()
                .stream()
                .filter(o -> o.getExtension()
                        .isAutoPopulated()
                        && o.active())
                .map(o -> o.getExtension()
                        .createOutputWriter(file))
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
        newQueryAction(file, null, null, file == null, Optional.empty());
    }

    /** New query action. */
    private void newQueryAction(File file, File backupFile, String newQueryName, boolean newFile, Optional<Object> editorValue)
    {
        IQueryEngine engine = null;
        IQueryEngine.IState state = null;

        QueryFileModel currentFile = model.getSelectedFile();
        // New query window -> use current tabs query engine
        if (file == null)
        {
            // Use the current tabs query engine
            if (currentFile != null)
            {
                engine = currentFile.getQueryEngine();

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
                    && engine == currentFile.getQueryEngine())
            {
                state = currentFile.getQueryEngine()
                        .cloneState(currentFile);
            }
        }

        newQueryAction(engine, state, file, backupFile, newQueryName, newFile, editorValue);
    }

    /** New query action. */
    private void newQueryAction(IQueryEngine queryEngine, IQueryEngine.IState engineState, File file, File backupFile, String newQueryName, boolean newFile, Optional<Object> editorValue)
    {
        // CSOFF
        IOutputExtension outputExtension = (IOutputExtension) view.getOutputCombo()
                .getSelectedItem();
        IOutputFormatExtension outputFormatExtension = (IOutputFormatExtension) view.getFormatCombo()
                .getSelectedItem();
        boolean loadFile = !newFile;
        // CSON

        if (file == null)
        {
            newFile = true;
            String extension = queryEngine.getDefaultFileExtension();
            if (!extension.startsWith("."))
            {
                extension = "." + extension;
            }

            if (!isBlank(newQueryName))
            {
                newQueryName = newQueryName.replaceAll("[\\\\/:*?\"<>|]", "_");
            }
            file = new File((!isBlank(newQueryName) ? newQueryName
                    : ("Query" + (newFileCounter++))) + extension);
        }

        if (engineState == null)
        {
            engineState = queryEngine.createState();
        }

        IEditor editor = queryEngine.createEditor(engineState, file.getName());

        QueryFileModel queryFile = new QueryFileModel(queryEngine, engineState, editor, outputExtension, outputFormatExtension, file, newFile);

        // Add output components to query file
        List<IOutputComponent> outputComponents = outputExtensions.stream()
                .filter(IOutputExtension::isAutoAdded)
                .sorted(Comparator.comparingInt(IOutputExtension::order))
                .map(e -> e.createResultComponent(queryFile))
                .filter(Objects::nonNull)
                .toList();
        queryFile.setOutputComponents(outputComponents);

        // Set the first one as the selected one
        queryFile.selectOutputComponent(queryFile.getOutputComponents()
                .get(0)
                .getClass());

        if (backupFile != null)
        {
            queryFile.backupFile = backupFile;
            queryFile.getEditor()
                    .loadFromFile(backupFile);
            queryFile.setDirty(true);
            // NOTE! Set backupdirty AFTER loading since the load triggers a doc. change
            queryFile.backupDirty = false;

            // Don't load the regular file, we want the backup loaded
            editorValue = Optional.empty();
            loadFile = false;
        }

        if (editorValue.isPresent())
        {
            editor.setValue(editorValue.get());
            queryFile.setDirty(true);
        }

        // Load the new file into editor
        if (loadFile)
        {
            queryFile.load();
        }

        queryFile.init();
        model.addFile(queryFile, init);
    }

    /** Open action. */
    private void openAction()
    {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setPreferredSize(Constants.DEFAULT_DIALOG_SIZE);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setAcceptAllFileFilterUsed(true);
        fileChooser.setMultiSelectionEnabled(true);
        JLabel accessory = new JLabel();
        accessory.setVerticalAlignment(JLabel.TOP);

        TitledBorder border = new TitledBorder("Query Engine Associations");
        border.setTitleFont(border.getTitleFont()
                .deriveFont(Font.BOLD));
        accessory.setBorder(border);

        String message = "<html>";
        message += "<p>The used query engine is choosen depending<br> on configured file extensions. Current config:</p>";

        message += "<ul>";
        for (QueryEngineAssociation ass : config.getQueryEngineAssociations())
        {
            message += "<li><b>" + ass.getEngine()
                    .getTitle() + "</b> - *." + ass.getExtension() + "</li>";

            fileChooser.addChoosableFileFilter(new FileNameExtensionFilter(ass.getEngine()
                    .getTitle() + " (*." + ass.getExtension() + ")", ass.getExtension()));
        }

        message += "<li><b>Default Engine</b> - " + config.getDefaultQueryEngine()
                .getTitle()
                   + " (*."
                   + config.getDefaultQueryEngine()
                           .getDefaultFileExtension()
                   + ")</li>";

        message += "</ul><br/>";
        message += "NOTE! This can be changed in Options<br/><b>Queryeer -> General</b>";
        accessory.setText(message);
        fileChooser.setAccessory(accessory);

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
                if (!model.select(selectedFile.getAbsolutePath()))
                {
                    selectedFile = new File(StringUtils.trim(selectedFile.getAbsolutePath()));
                    newQueryAction(selectedFile);
                    config.appendRecentFile(selectedFile.getAbsolutePath());
                }
                view.setRecentFiles(config.getRecentFiles());
            }

            // Store last selected path if differs
            if (!Objects.equals(config.getLastOpenPath(), fileChooser.getCurrentDirectory()
                    .getAbsolutePath()))
            {
                config.setLastOpenPath(fileChooser.getCurrentDirectory()
                        .getAbsolutePath());
                config.save();
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
