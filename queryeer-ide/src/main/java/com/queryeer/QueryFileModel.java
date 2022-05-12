package com.queryeer;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isAllBlank;

import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.SwingUtilities;
import javax.swing.event.SwingPropertyChangeSupport;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.lang3.tuple.Pair;

import com.queryeer.api.extensions.catalog.ICatalogExtension;
import com.queryeer.api.extensions.output.IOutputExtension;
import com.queryeer.api.extensions.output.IOutputFormatExtension;

import se.kuseman.payloadbuilder.core.QuerySession;
import se.kuseman.payloadbuilder.core.catalog.CatalogRegistry;

/**
 * Model of a query file. Has information about filename, execution state etc.
 **/
class QueryFileModel
{
    private final SwingPropertyChangeSupport pcs = new SwingPropertyChangeSupport(this, true);
    // static final String RESULT_MODEL = "resultModel";
    static final String DIRTY = "dirty";
    static final String FILENAME = "filename";
    static final String QUERY = "query";
    static final String STATE = "state";

    private final AtomicInteger queryId = new AtomicInteger();
    private final Map<String, Object> variables = new HashMap<>();
    private final QuerySession querySession = new QuerySession(new CatalogRegistry(), variables);
    private final List<Pair<ICatalogExtension, CatalogExtensionModel>> catalogExtensions;

    private boolean dirty;
    private boolean newFile = true;
    private String filename = "";
    private State state = State.COMPLETED;
    /** Query before modifications */
    private String savedQuery = "";
    private String query = "";
    private int totalRowCount;

    private IOutputExtension outputExtension;
    private IOutputFormatExtension outputFormat;

    // private Output output = Output.TABLE;
    // private Format format = Format.CSV;

    /** Execution fields */
    // private final List<ResultModel> results = new ArrayList<>();

    private StopWatch sw;
    private String error;
    private Pair<Integer, Integer> parseErrorLocation;

    QueryFileModel(List<Config.Catalog> catalogs)
    {
        this(catalogs, null);
    }

    /** Initialize a file from filename */
    QueryFileModel(List<Config.Catalog> catalogs, File file)
    {
        if (file != null)
        {
            this.filename = file.getAbsolutePath();
            try
            {
                query = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
            dirty = false;
            newFile = false;
            savedQuery = query;
        }

        catalogExtensions = init(catalogs);
    }

    private List<Pair<ICatalogExtension, CatalogExtensionModel>> init(List<Config.Catalog> catalogs)
    {
        List<Pair<ICatalogExtension, CatalogExtensionModel>> catalogExtensions = catalogs.stream()
                .filter(c -> !c.isDisabled())
                .map(c -> Pair.of(c.getCatalogExtension(), new CatalogExtensionModel(c.getAlias())))
                .collect(toList());

        for (Config.Catalog catalog : catalogs)
        {
            // Register catalogs
            querySession.getCatalogRegistry()
                    .registerCatalog(catalog.getAlias(), catalog.getCatalogExtension()
                            .getCatalog());

            // Set first extension as default
            // We pick the first catalog that has a UI component
            if (isAllBlank(querySession.getDefaultCatalogAlias())
                    && !catalog.isDisabled()
                    && catalog.getCatalogExtension()
                            .getConfigurableClass() != null)
            {
                querySession.setDefaultCatalogAlias(catalog.getAlias());
            }
        }

        return catalogExtensions;
    }

    int getQueryId()
    {
        return queryId.get();
    }

    int incrementAndGetQueryId()
    {
        return queryId.incrementAndGet();
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

            // Let UI update correctly when chaning state of query model
            if (SwingUtilities.isEventDispatchThread())
            {
                pcs.firePropertyChange(STATE, oldValue, newValue);
            }
            else
            {
                try
                {
                    SwingUtilities.invokeAndWait(() -> pcs.firePropertyChange(STATE, oldValue, newValue));
                }
                catch (InvocationTargetException | InterruptedException e)
                {
                    throw new RuntimeException("Error updating state of query", e);
                }
            }
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
        error = "";
        parseErrorLocation = null;
        // results.clear();
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

    /** Saves file to disk */
    void save()
    {
        try
        {
            FileUtils.write(new File(filename), query, StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Unable to save file " + filename, e);
        }
        newFile = false;
        savedQuery = query;
        setDirty(false);
    }

    String getQuery()
    {
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

    void addPropertyChangeListener(PropertyChangeListener listener)
    {
        pcs.addPropertyChangeListener(listener);
    }

    IOutputExtension getOutputExtension()
    {
        return outputExtension;
    }

    void setOutput(IOutputExtension outputExtension)
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

    String getError()
    {
        return error;
    }

    void setError(String error)
    {
        this.error = error;
    }

    Pair<Integer, Integer> getParseErrorLocation()
    {
        return parseErrorLocation;
    }

    void setParseErrorLocation(Pair<Integer, Integer> parseErrorLocation)
    {
        this.parseErrorLocation = parseErrorLocation;
    }

    String getTabTitle()
    {
        String filename = FilenameUtils.getName(getFilename());
        StringBuilder sb = new StringBuilder();
        if (isDirty())
        {
            sb.append("*");
        }
        sb.append(filename);
        if (getState() == State.EXECUTING)
        {
            sb.append(" Executing ...");
        }
        return sb.toString();
    }

    QuerySession getQuerySession()
    {
        return querySession;
    }

    Map<String, Object> getVariables()
    {
        return variables;
    }

    // List<ResultModel> getResults()
    // {
    // return results;
    // }

    // void addResult(ResultModel model)
    // {
    //// results.add(model);
    //// if (output != Output.NONE)
    //// {
    //// pcs.firePropertyChange(RESULT_MODEL, null, model);
    //// }
    // }

    /** Total row count of current execution including all result sets */
    int getTotalRowCount()
    {
        return totalRowCount;
    }

    void incrementTotalRowCount()
    {
        totalRowCount++;
    }

    List<Pair<ICatalogExtension, CatalogExtensionModel>> getCatalogExtensions()
    {
        return catalogExtensions;
    }

    /** Setup extensions before query */
    void setupBeforeExecution()
    {
        for (Pair<ICatalogExtension, CatalogExtensionModel> pair : catalogExtensions)
        {
            ICatalogExtension extension = pair.getKey();
            CatalogExtensionModel model = pair.getValue();
            extension.setup(model.getAlias(), querySession);
        }

        // .forEach(e ->
        // {
        // // Register catalog in session
        // fileModel.getQuerySession()
        // .getCatalogRegistry()
        // .registerCatalog(model.getAlias(), extension.getCatalog());
        // // Setup extensions from model data
        // // Do this here also besides when properties changes,
        // // since there is a possibility that no changes was made before a query is executed.
        // extension.setup(model.getAlias(), fileModel.getQuerySession());
        // });
    }

    /** Execution state of this file */
    enum State
    {
        COMPLETED("Stopped"),
        EXECUTING("Executing"),
        ABORTED("Aborted"),
        ERROR("Error");

        String tooltip;

        State(String tooltip)
        {
            this.tooltip = tooltip;
        }

        public String getToolTip()
        {
            return null;
        }
    }

    // /** Current output of this file */
    // enum Output
    // {
    // TABLE, FILE, TEXT, NONE;
    // }

    // /** Current output format of this file */
    // enum Format
    // {
    // CSV, JSON
    // }
}
