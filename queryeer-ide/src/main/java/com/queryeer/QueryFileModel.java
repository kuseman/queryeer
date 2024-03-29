package com.queryeer;

import static java.util.Collections.unmodifiableList;
import static org.apache.commons.lang3.StringUtils.isAllBlank;

import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.swing.event.SwingPropertyChangeSupport;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.StopWatch;

import com.queryeer.api.extensions.output.IOutputExtension;
import com.queryeer.api.extensions.output.IOutputFormatExtension;
import com.queryeer.domain.Caret;
import com.queryeer.domain.ICatalogModel;

import se.kuseman.payloadbuilder.core.cache.InMemoryGenericCache;
import se.kuseman.payloadbuilder.core.catalog.CatalogRegistry;
import se.kuseman.payloadbuilder.core.execution.QuerySession;

/**
 * Model of a query file. Has information about filename, execution state etc.
 **/
class QueryFileModel
{
    /** All query tabs share a common cache to be able to reuse cached data etc. */
    private static final InMemoryGenericCache GENERIC_CACHE = new InMemoryGenericCache("QuerySession", true);

    private final SwingPropertyChangeSupport pcs = new SwingPropertyChangeSupport(this, true);
    static final String DIRTY = "dirty";
    static final String FILENAME = "filename";
    static final String QUERY = "query";
    static final String CARET = "carret";
    static final String STATE = "state";

    private final Map<String, Object> variables = new HashMap<>();
    private final QuerySession querySession = new QuerySession(new CatalogRegistry(), variables);
    private final List<ICatalogModel> catalogs;
    private final Caret caret = new Caret();

    private boolean dirty;
    private boolean newFile = true;
    private String filename = "";
    private long lastModified = 0;
    private State state = State.COMPLETED;
    /** Query before modifications */
    private String savedQuery = "";
    private String query = "";
    private int totalRowCount;

    private IOutputExtension outputExtension;
    private IOutputFormatExtension outputFormat;

    /** Execution fields */
    private StopWatch sw;

    /** Initialize a file from filename */
    QueryFileModel(List<ICatalogModel> catalogs, IOutputExtension outputExtension, IOutputFormatExtension outputFormat, File file)
    {
        this.catalogs = unmodifiableList(catalogs);
        this.outputExtension = outputExtension;
        this.outputFormat = outputFormat;
        if (file != null)
        {
            this.filename = file.getAbsolutePath();
            this.newFile = false;
            reloadFromFile();
            savedQuery = query;
        }
        this.querySession.setGenericCache(GENERIC_CACHE);

        initCatalogs();
    }

    private void initCatalogs()
    {
        for (ICatalogModel catalog : catalogs)
        {
            if (catalog.isDisabled())
            {
                continue;
            }

            // Register catalogs
            querySession.getCatalogRegistry()
                    .registerCatalog(catalog.getAlias(), catalog.getCatalogExtension()
                            .getCatalog());

            // Set first extension as default
            // We pick the first catalog that has a UI component
            if (isAllBlank(querySession.getDefaultCatalogAlias())
                    && catalog.getCatalogExtension()
                            .getConfigurableClass() != null)
            {
                querySession.setDefaultCatalogAlias(catalog.getAlias());
            }
        }
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
            this.state = state;

            // Reset execution fields when starting
            if (state == State.EXECUTING)
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

    long getLastModified()
    {
        return lastModified;
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

    /** Saves file to disk */
    void save()
    {
        try
        {
            File file = new File(filename);
            FileUtils.write(file, query, StandardCharsets.UTF_8);
            lastModified = file.lastModified();
        }
        catch (IOException e)
        {
            throw new RuntimeException("Unable to save file " + filename, e);
        }
        newFile = false;
        savedQuery = query;
        setDirty(false);
    }

    String getQuery(boolean selected)
    {
        if (selected)
        {
            return caret.getSelectionLength() > 0 ? query.substring(caret.getSelectionStart(), caret.getSelectionStart() + caret.getSelectionLength())
                    : query;
        }

        return query;
    }

    void setQuery(String query)
    {
        String newValue = query;
        String oldValue = this.query;
        if (!newValue.equals(oldValue))
        {
            this.query = query;
            pcs.firePropertyChange(QUERY, oldValue, newValue);
            setDirty(!Objects.equals(query, savedQuery));
        }
    }

    Caret getCaret()
    {
        return caret;
    }

    void setCaret(int lineNumber, int offset, int position, int selectionStart, int selectionLength)
    {
        caret.setLineNumber(lineNumber);
        caret.setOffset(offset);
        caret.setPosition(position);
        caret.setSelectionStart(selectionStart);
        caret.setSelectionLength(selectionLength);
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

    QuerySession getQuerySession()
    {
        return querySession;
    }

    void incrementTotalRowCount()
    {
        totalRowCount++;
    }

    IOutputFormatExtension getOutputFormat()
    {
        return outputFormat;
    }

    Map<String, Object> getVariables()
    {
        return variables;
    }

    /** Total row count of current execution including all result sets */
    int getTotalRowCount()
    {
        return totalRowCount;
    }

    List<ICatalogModel> getCatalogs()
    {
        return catalogs;
    }

    void reloadFromFile()
    {
        if (newFile)
        {
            return;
        }

        try
        {
            File file = new File(filename);

            String query = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            // Set saved query before setting the query to avoid firing the dirty state
            savedQuery = query;

            // Set query to fire property change
            setQuery(query);
            lastModified = file.lastModified();
            dirty = false;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    /** Execution state of this file */
    enum State
    {
        COMPLETED("Stopped"),
        EXECUTING("Executing"),
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
    }
}
