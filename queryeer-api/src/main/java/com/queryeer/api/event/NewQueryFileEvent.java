package com.queryeer.api.event;

import static java.util.Objects.requireNonNull;

import java.io.File;

import com.queryeer.api.extensions.engine.IQueryEngine;
import com.queryeer.api.extensions.engine.IQueryEngine.IState;

/** Event that can be fired to open up a new query file */
public class NewQueryFileEvent extends Event
{
    private final File file;

    /** Engine for this new file */
    private final IQueryEngine queryEngine;

    /** Pre created state of new file */
    private final IState state;

    /** Editor content of new file */
    private final Object editorValue;

    /** Should the new query file be executed once initialised ? */
    private final boolean execute;

    /** Optional new query name used when opening a new non existing file. */
    private final String newQueryName;

    public NewQueryFileEvent(IQueryEngine queryEngine, IQueryEngine.IState state, Object editorValue, boolean execute, String newQueryName)
    {
        this.queryEngine = requireNonNull(queryEngine);
        this.state = state;
        this.editorValue = editorValue;
        this.file = null;
        this.execute = execute;
        this.newQueryName = newQueryName;
    }

    public NewQueryFileEvent(File file)
    {
        this.queryEngine = null;
        this.state = null;
        this.editorValue = null;
        this.file = requireNonNull(file, "file");
        this.execute = false;
        this.newQueryName = null;
    }

    public IQueryEngine getQueryEngine()
    {
        return queryEngine;
    }

    public IState getState()
    {
        return state;
    }

    public Object getEditorValue()
    {
        return editorValue;
    }

    public String getNewQueryName()
    {
        return newQueryName;
    }

    public File getFile()
    {
        return file;
    }

    public boolean isExecute()
    {
        return execute;
    }
}
