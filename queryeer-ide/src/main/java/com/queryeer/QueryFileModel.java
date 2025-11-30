package com.queryeer;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.SwingPropertyChangeSupport;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.queryeer.FileWatchService.FileWatchListener;
import com.queryeer.api.IQueryFile;
import com.queryeer.api.QueryFileMetaData;
import com.queryeer.api.editor.IEditor;
import com.queryeer.api.extensions.engine.IQueryEngine;
import com.queryeer.api.extensions.engine.IQueryEngine.IState;
import com.queryeer.api.extensions.output.IOutputComponent;
import com.queryeer.api.extensions.output.IOutputExtension;
import com.queryeer.api.extensions.output.IOutputFormatExtension;
import com.queryeer.api.extensions.output.text.ITextOutputComponent;

import se.kuseman.payloadbuilder.api.OutputWriter;

/**
 * Model of a query file. Has information about filename, execution state etc.
 **/
class QueryFileModel implements IQueryFile
{
    private static final Logger LOGGER = LoggerFactory.getLogger(QueryFileModel.class);
    private final SwingPropertyChangeSupport pcs = new SwingPropertyChangeSupport(this, true);
    static final String DIRTY = "dirty";
    static final String FILE = "file";
    static final String STATE = "state";
    static final String METADATA = "metaData";
    static final String SELECTED_OUTPUT_COMPONENT = "selectedOutputComponent";
    static final String OUTPUT_COMPONENTS = "outputComponents";

    private final IQueryEngine queryEngine;
    private final IQueryEngine.IState engineState;
    private final IEditor editor;

    private boolean dirty;
    /** Flag that indicates that this file has not been saved in backup yet. */
    volatile boolean backupDirty;
    private boolean newFile = true;
    private File file;
    File backupFile;
    private long lastModified = 0;
    private long lastActivity = System.currentTimeMillis();
    private State state = State.COMPLETED;
    private QueryFileMetaData metaData;
    private int totalRowCount;
    private IOutputExtension outputExtension;
    private IOutputFormatExtension outputFormat;
    private List<IOutputComponent> outputComponents = new ArrayList<>();
    private IOutputComponent selectedOutputComponent;

    FileWatchListener watchListener;

    /** Execution fields */
    private StopWatch sw;

    /** Timer if this file is executing at an interval. */
    private Timer executionTimer;

    /** Initialize a file from filename */
    QueryFileModel(IQueryEngine queryEngine, IQueryEngine.IState engineState, IEditor editor, IOutputExtension outputExtension, IOutputFormatExtension outputFormat, File file, boolean newFile)
    {
        this.queryEngine = requireNonNull(queryEngine, "queryEngine");
        this.engineState = requireNonNull(engineState, "engineState");
        this.editor = requireNonNull(editor, "editor");
        this.outputExtension = outputExtension;
        this.outputFormat = outputFormat;
        this.file = requireNonNull(file, "file");
        this.lastModified = file.lastModified();
        this.newFile = newFile;
    }

    /** Setup listeners etc. */
    void init()
    {
        // Propagate dirty from editor to this
        editor.addPropertyChangeListener(new PropertyChangeListener()
        {
            @Override
            public void propertyChange(PropertyChangeEvent evt)
            {
                if (IEditor.DIRTY.equals(evt.getPropertyName()))
                {
                    setDirty((boolean) evt.getNewValue());
                }
                else if (IEditor.VALUE_CHANGED.equals(evt.getPropertyName()))
                {
                    lastActivity = System.currentTimeMillis();
                    backupDirty = true;
                }
            }
        });
    }

    List<IOutputComponent> getOutputComponents()
    {
        return outputComponents;
    }

    /** Set output components. NOTE! Does not fire property change events. */
    void setOutputComponents(List<IOutputComponent> outputComponents)
    {
        this.outputComponents = new ArrayList<>(requireNonNull(outputComponents));
    }

    boolean isDirty()
    {
        return dirty;
    }

    void setDirty(boolean dirty)
    {
        boolean oldValue = this.dirty;
        boolean newValue = dirty;
        if (newValue != oldValue)
        {
            this.lastActivity = System.currentTimeMillis();
            if (dirty)
            {
                this.backupDirty = true;
            }
            this.dirty = dirty;
            pcs.firePropertyChange(DIRTY, oldValue, newValue);
        }
    }

    State getState()
    {
        return state;
    }

    @SuppressWarnings("incomplete-switch")
    void setState(State state)
    {
        State oldValue = this.state;
        State newValue = state;
        if (oldValue != newValue)
        {
            this.lastActivity = System.currentTimeMillis();
            this.state = state;

            // Reset execution fields when starting
            if (state.isExecuting())
            {
                sw = new StopWatch();
                sw.start();
                clearForExecution();
            }
            else if (sw.isStarted())
            {
                sw.stop();
            }

            // CSOFF
            switch (state)
            // CSON
            {
                case EXECUTING:
                case EXECUTING_BY_EVENT:

                    boolean byEvent = state == State.EXECUTING_BY_EVENT;

                    // Don't clear or switch outputs when executing by event, then it's handled already
                    if (!byEvent)
                    {
                        // Clear state of output components
                        int count = outputComponents.size();
                        for (int i = 0; i < count; i++)
                        {
                            IOutputComponent outputComponent = outputComponents.get(i);
                            outputComponent.clearState();
                        }

                        IOutputExtension selectedOutputExtension = outputExtension;

                        Class<? extends IOutputComponent> outputComponentClass = selectedOutputExtension.getResultOutputComponentClass();
                        if (outputComponentClass != null)
                        {
                            selectOutputComponent(outputComponentClass);
                        }
                    }
                    break;
                case ABORTED:
                    getMessagesWriter().println("Query was aborted!");
                    break;
                case ERROR:
                    focusMessages();
                    break;
            }

            // No rows, then show messages
            if (!state.isExecuting()
                    && totalRowCount == 0)
            {
                focusMessages();
            }

            pcs.firePropertyChange(STATE, oldValue, newValue);
        }
    }

    void bumpActivity()
    {
        lastActivity = System.currentTimeMillis();
    }

    boolean isRunningAtInterval()
    {
        return executionTimer != null;
    }

    /** Abort this query. */
    void abort()
    {
        queryEngine.abortQuery(this);

        // If running at interval then stop the timer
        if (executionTimer != null)
        {
            executionTimer.stop();
            executionTimer = null;

            // Fire an event to let titles etc. redraw when ending interval execution
            pcs.firePropertyChange(STATE, null, state);
        }
        if (state.isExecuting())
        {
            setState(State.ABORTED);
        }
    }

    /** Execute this query file. */
    void execute(Duration duration)
    {
        if (duration == null)
        {
            // If this query is on a timer then skip regular execution to avoid weird states
            if (executionTimer != null)
            {
                return;
            }
            executeInternal();
        }
        else
        {
            // Stop previous timer if any
            if (executionTimer != null)
            {
                executionTimer.stop();
            }

            executionTimer = new Timer((int) duration.toMillis(), evt -> executeInternal());
            // Do the execution instantly
            executionTimer.setInitialDelay(0);
            executionTimer.start();
        }
    }

    /** Execute this query file. */
    void executeInternal()
    {
        if (!queryEngine.shouldExecute(this))
        {
            return;
        }

        if (state.isExecuting())
        {
            return;
        }

        OutputWriter writer = outputExtension.createOutputWriter(this);

        if (writer == null)
        {
            return;
        }

        // Don't run empty queries
        if (editor.isValueEmpty())
        {
            return;
        }
        // When executing in a timer we don't apply selected text execution
        Object query = editor.getValue(executionTimer != null ? true
                : false);

        QueryService.executeQuery(this, getOutputWriter(this, writer), query, false, () ->
        {
        });
    }

    /** Returns the execution interval if any as string. */
    String getExecutionInterval()
    {
        if (executionTimer != null)
        {
            return "" + (executionTimer.getDelay() / 1000) + "s";
        }
        return "";
    }

    QueryFileMetaData getMetaData()
    {
        if (metaData == null)
        {
            return QueryFileMetaData.EMPTY;
        }

        return metaData;
    }

    @Override
    public void selectOutputComponent(Class<? extends IOutputComponent> outputComponentClass)
    {
        requireNonNull(outputComponentClass);
        int count = outputComponents.size();
        for (int i = 0; i < count; i++)
        {
            IOutputComponent outputComponent = outputComponents.get(i);
            if (outputComponentClass.isAssignableFrom(outputComponent.getClass()))
            {
                if (selectedOutputComponent != outputComponent)
                {
                    IOutputComponent old = selectedOutputComponent;
                    selectedOutputComponent = outputComponent;
                    pcs.firePropertyChange(SELECTED_OUTPUT_COMPONENT, old, outputComponent);
                }
                return;
            }
        }
    }

    @Override
    public IOutputComponent getSelectedOutputComponent()
    {
        return selectedOutputComponent;
    }

    @Override
    public void focusMessages()
    {
        selectOutputComponent(ITextOutputComponent.class);
    }

    @Override
    public PrintWriter getMessagesWriter()
    {
        return getOutputComponent(ITextOutputComponent.class).getTextWriter();
    }

    @Override
    public <T extends IOutputComponent> T getOutputComponent(Class<T> clazz)
    {
        int count = outputComponents.size();
        for (int i = 0; i < count; i++)
        {
            IOutputComponent component = outputComponents.get(i);
            if (clazz.isAssignableFrom(component.getClass()))
            {
                return clazz.cast(component);
            }
        }
        return null;
    }

    @Override
    public void addOutputComponent(IOutputComponent outputComponent)
    {
        Class<?> clazz = outputComponent.getClass();
        int count = outputComponents.size();
        for (int i = 0; i < count; i++)
        {
            IOutputComponent component = outputComponents.get(i);
            if (clazz.isAssignableFrom(component.getClass()))
            {
                // Already added, just return
                return;
            }
        }

        int index = outputComponents.size();
        outputComponents.add(outputComponent);
        pcs.fireIndexedPropertyChange(OUTPUT_COMPONENTS, index, null, outputComponent);
    }

    @Override
    public void setMetaData(QueryFileMetaData metaData)
    {
        QueryFileMetaData oldValue = this.metaData;
        QueryFileMetaData newValue = metaData;
        if (!Objects.equals(oldValue, newValue))
        {
            this.lastActivity = System.currentTimeMillis();
            this.metaData = metaData;
            pcs.firePropertyChange(METADATA, oldValue, newValue);
        }
    }

    /** Get current execution time in millis */
    long getExecutionTime()
    {
        return sw != null ? sw.getTime(TimeUnit.MILLISECONDS)
                : 0;
    }

    void clearForExecution()
    {
        totalRowCount = 0;
        SwingUtilities.invokeLater(() -> editor.clearBeforeExecution());
    }

    boolean isNew()
    {
        return newFile;
    }

    void setNew(boolean newFile)
    {
        this.newFile = newFile;
    }

    long getLastModified()
    {
        return lastModified;
    }

    long getLastActivity()
    {
        return lastActivity;
    }

    boolean isModified()
    {
        return lastModified != file.lastModified();
    }

    File getFile()
    {
        return file;
    }

    void setFile(File file)
    {
        String newValue = file.getAbsolutePath();
        String oldValue = this.file.getAbsolutePath();
        if (!newValue.equals(oldValue))
        {
            this.file = file;
            pcs.firePropertyChange(FILE, file, this.file);
        }
    }

    /** Saves file to disk */
    void save()
    {
        editor.saveToFile(file, true);
        lastModified = file.lastModified();
        newFile = false;
        setDirty(false);
    }

    /** Load file from disk */
    void load()
    {
        // CSOFF
        boolean backupDirty = this.backupDirty;
        // CSON
        editor.loadFromFile(file);
        lastModified = file.lastModified();
        newFile = false;
        setDirty(false);
        // Restore the backup dirty flag during load since the load might trigger a value change
        this.backupDirty = backupDirty;
    }

    String getTooltip()
    {
        //@formatter:off
        return """
                <html>
                %s%s%s%s
                """.formatted
                (
                        getQueryEngine() != null ? ("<b>" + getQueryEngine().getTitle() + "</b><br/>") : "",
                        getFile().getAbsolutePath() + "<br/>",
                        !isBlank(getMetaData().getDescription()) ? (getMetaData().getDescription() + "<br/>") : "",
                        executionTimer != null ? ("... every " + getExecutionInterval()) : ""
                );
        //@formatter:on
    }

    void addPropertyChangeListener(PropertyChangeListener listener)
    {
        pcs.addPropertyChangeListener(listener);
    }

    void removePropertyChangeListener(PropertyChangeListener listener)
    {
        pcs.removePropertyChangeListener(listener);
    }

    IOutputExtension getOutputExtension()
    {
        return outputExtension;
    }

    void setOutputExtension(IOutputExtension outputExtension)
    {
        this.outputExtension = outputExtension;
    }

    @Override
    public IOutputFormatExtension getOutputFormat()
    {
        return outputFormat;
    }

    void setOutputFormat(IOutputFormatExtension outputFormat)
    {
        this.outputFormat = outputFormat;
    }

    IQueryEngine getQueryEngine()
    {
        return queryEngine;
    }

    @Override
    public IEditor getEditor()
    {
        return editor;
    }

    @Override
    public void incrementTotalRowCount()
    {
        totalRowCount++;
    }

    /** Total row count of current execution including all result sets */
    int getTotalRowCount()
    {
        return totalRowCount;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends IState> T getEngineState()
    {
        return (T) engineState;
    }

    /**
     * Executor for query file dispose. This can be time consuming if connections etc. are slow to close. NOTE! Non daemon to let all dispose calls finish
     */
    static final ExecutorService DISPOSE_EXECUTOR = Executors.newCachedThreadPool(BasicThreadFactory.builder()
            .daemon(false)
            .namingPattern("QueryFileDisposer#%d")
            .build());

    /** Dispose file. Closing all stored states etc */
    void dispose()
    {
        for (IOutputComponent component : outputComponents)
        {
            try
            {
                component.dispose();
            }
            catch (Exception e)
            {
                LOGGER.error("Error disposing {}", component.getClass(), e);
            }
        }

        editor.close();
        DISPOSE_EXECUTOR.execute(() -> IOUtils.closeQuietly(engineState));
    }

    /** Execution state of this file */
    enum State
    {
        COMPLETED("Stopped"),
        EXECUTING("Executing"),
        EXECUTING_BY_EVENT("Executing"),
        ABORTED("Aborted"),
        ERROR("Error");

        final String tooltip;

        State(String tooltip)
        {
            this.tooltip = tooltip;
        }

        public String getToolTip()
        {
            return tooltip;
        }

        boolean isExecuting()
        {
            return this == EXECUTING
                    || this == EXECUTING_BY_EVENT;
        }
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
}
