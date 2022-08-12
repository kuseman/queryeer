package com.queryeer;

import static java.util.Objects.requireNonNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.concurrent.TimeUnit;

import javax.swing.event.SwingPropertyChangeSupport;

import org.apache.commons.lang3.time.StopWatch;

import com.queryeer.api.IQueryFile.ExecutionState;
import com.queryeer.api.IQueryFileState;
import com.queryeer.api.extensions.output.IOutputExtension;
import com.queryeer.api.extensions.output.IOutputFormatExtension;

/**
 * Model of a query file. Has information about filename, execution state etc.
 **/
class QueryFileModel implements PropertyChangeListener
{
    private final SwingPropertyChangeSupport pcs = new SwingPropertyChangeSupport(this, true);
    static final String DIRTY = "dirty";
    static final String FILENAME = "filename";
    static final String EXECUTION_STATE = "executionState";

    private final IQueryFileState queryFileState;

    private boolean dirty;
    private boolean newFile = true;
    private String filename = "";
    private ExecutionState executionState = ExecutionState.COMPLETED;
    private int totalRowCount;

    private IOutputExtension outputExtension;
    private IOutputFormatExtension outputFormat;

    /** Execution fields */
    private StopWatch sw;

    /** Initialize a file from filename */
    QueryFileModel(IQueryFileState queryFileState, IOutputExtension outputExtension, IOutputFormatExtension outputFormat, File file)
    {
        this.queryFileState = requireNonNull(queryFileState, "queryFileState");
        queryFileState.addPropertyChangeListener(this);
        this.outputExtension = outputExtension;
        this.outputFormat = outputFormat;
        if (file != null)
        {
            this.filename = file.getAbsolutePath();
            dirty = false;
            newFile = false;
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt)
    {
        // Propagate dirty change from state
        if (IQueryFileState.DIRTY.equals(evt.getPropertyName()))
        {
            setDirty((boolean) evt.getNewValue());
        }
        else
        {
            // Else propagate the event
            pcs.firePropertyChange(evt);
        }
    }

    void close()
    {
        queryFileState.removePropertyChangeListener(this);
    }

    void load()
    {
        try
        {
            queryFileState.load(filename);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Unable to save file " + filename, e);
        }
        newFile = false;
        setDirty(false);
    }

    void save()
    {
        try
        {
            queryFileState.save(filename);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Unable to save file " + filename, e);
        }
        newFile = false;
        setDirty(false);
    }

    IQueryFileState getQueryFileState()
    {
        return queryFileState;
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
            this.dirty = dirty;
            pcs.firePropertyChange(DIRTY, oldValue, newValue);
        }
    }

    ExecutionState getExecutionState()
    {
        return executionState;
    }

    void setExecutionState(ExecutionState state)
    {
        ExecutionState oldValue = this.executionState;
        ExecutionState newValue = state;
        if (oldValue != newValue)
        {
            this.executionState = state;

            // Reset execution fields when starting
            if (state == ExecutionState.EXECUTING)
            {
                sw = new StopWatch();
                sw.start();
                clearForExecution();
            }
            else if (sw.isStarted())
            {
                sw.stop();
            }

            pcs.firePropertyChange(EXECUTION_STATE, oldValue, newValue);
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
    }

    boolean isNew()
    {
        return newFile;
    }

    void setNew(boolean newFile)
    {
        this.newFile = newFile;
    }

    String getFilename()
    {
        return filename;
    }

    void setFilename(String filename)
    {
        String newValue = filename;
        String oldValue = this.filename;
        if (!newValue.equals(oldValue))
        {
            this.filename = filename;
            pcs.firePropertyChange(FILENAME, oldValue, newValue);
        }
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

    void setOutput(IOutputExtension outputExtension)
    {
        this.outputExtension = outputExtension;
    }

    void setOutputFormat(IOutputFormatExtension outputFormat)
    {
        this.outputFormat = outputFormat;
    }

    void incrementTotalRowCount()
    {
        totalRowCount++;
    }

    IOutputFormatExtension getOutputFormat()
    {
        return outputFormat;
    }

    /** Total row count of current execution including all result sets */
    int getTotalRowCount()
    {
        return totalRowCount;
    }
}
