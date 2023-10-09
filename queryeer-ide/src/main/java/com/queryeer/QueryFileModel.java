package com.queryeer;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;
import javax.swing.event.SwingPropertyChangeSupport;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.commons.lang3.time.StopWatch;

import com.queryeer.FileWatchService.FileWatchListener;
import com.queryeer.api.QueryFileMetaData;
import com.queryeer.api.editor.IEditor;
import com.queryeer.api.extensions.engine.IQueryEngine;
import com.queryeer.api.extensions.output.IOutputExtension;
import com.queryeer.api.extensions.output.IOutputFormatExtension;

/**
 * Model of a query file. Has information about filename, execution state etc.
 **/
class QueryFileModel
{
    private final SwingPropertyChangeSupport pcs = new SwingPropertyChangeSupport(this, true);
    static final String DIRTY = "dirty";
    static final String FILE = "file";
    static final String STATE = "state";
    static final String METADATA = "metaData";

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

    FileWatchListener watchListener;

    /** Execution fields */
    private StopWatch sw;

    /** Initialize a file from filename */
    QueryFileModel(IQueryEngine queryEngine, IQueryEngine.IState engineState, IEditor editor, IOutputExtension outputExtension, IOutputFormatExtension outputFormat, File file, boolean newFile)
    {
        this.queryEngine = requireNonNull(queryEngine, "queryEngine");
        this.engineState = engineState;
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

            pcs.firePropertyChange(STATE, oldValue, newValue);
        }
    }

    QueryFileMetaData getMetaData()
    {
        if (metaData == null)
        {
            return QueryFileMetaData.EMPTY;
        }

        return metaData;
    }

    void setMetaData(QueryFileMetaData metaData)
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
                %s%s%s
                """.formatted(getQueryEngine() != null ? ("<b>" + getQueryEngine()
                .getTitle() + "</b><br/>")
                : "",
                getFile()
                        .getAbsolutePath()
                      + "<br/>",
                !isBlank(getMetaData()
                        .getDescription())
                                ? (getMetaData()
                                        .getDescription()
                                   + "<br/>")
                                : "");
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

    IOutputFormatExtension getOutputFormat()
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

    IEditor getEditor()
    {
        return editor;
    }

    void incrementTotalRowCount()
    {
        totalRowCount++;
    }

    /** Total row count of current execution including all result sets */
    int getTotalRowCount()
    {
        return totalRowCount;
    }

    IQueryEngine.IState getEngineState()
    {
        return engineState;
    }

    /**
     * Executor for query file dispose. This can be time consuming if connections etc. are slow to close. NOTE! Non daemon to let all dispose calls finish
     */
    static final ExecutorService DISPOSE_EXECUTOR = Executors.newCachedThreadPool(new BasicThreadFactory.Builder().daemon(false)
            .namingPattern("QueryFileDisposer#%d")
            .build());

    /** Dispose file. Closing all stored states etc */
    void dispose()
    {
        if (engineState == null)
        {
            return;
        }

        DISPOSE_EXECUTOR.execute(() ->
        {
            try
            {
                engineState.close();
            }
            catch (IOException e)
            {
                // SWALLOW
            }
        });
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
}
