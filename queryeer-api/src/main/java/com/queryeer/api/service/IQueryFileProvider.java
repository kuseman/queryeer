package com.queryeer.api.service;

import java.util.function.Consumer;

import com.queryeer.api.IQueryFile;

/** Query file provider. Gets access to the current selected {@link IQueryFile} */
public interface IQueryFileProvider
{
    /** Returns the current open query file */
    IQueryFile getCurrentFile();

    /** Register a listener that is notified (on the EDT) when the current file changes. */
    default void addCurrentFileListener(Consumer<IQueryFile> listener)
    {
    }

    /** Remove a previously registered current file listener. */
    default void removeCurrentFileListener(Consumer<IQueryFile> listener)
    {
    }
}
