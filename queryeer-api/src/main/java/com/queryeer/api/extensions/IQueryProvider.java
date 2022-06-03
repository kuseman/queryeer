package com.queryeer.api.extensions;

import java.awt.Component;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.IQueryFileState;

import se.kuseman.payloadbuilder.api.OutputWriter;

/**
 * Definition of a query provider.
 * 
 * <pre>
 * This is the core part of Queryeer. The provider add support for actual doing a query
 * against some datasource.
 * 
 * For example Payloadbuilder is one provider that comes bundled with Queryeer.
 * 
 * </pre>
 */
public interface IQueryProvider extends IExtension
{
    /** Get the title of the provider. Is shown in menu's etc. */
    String getTitle();

    /** Get the extension of filenames for this provider */
    String getFilenameExtension();

    /**
     * Create query file state for this provider
     */
    IQueryFileState createQueryFileState();

    /**
     * Execute the provided query file
     *
     * @param queryFile Query file that should be executed
     * @param writer The output writer to use
     */
    void executeQuery(IQueryFile queryFile, OutputWriter writer);

    /**
     * Get quick properties component for this provider. This is the component is shown at the side of the query editor for quick access to databases etc.
     */
    Component getQuickPropertiesComponent();
}
