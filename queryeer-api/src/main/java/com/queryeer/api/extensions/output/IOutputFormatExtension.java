package com.queryeer.api.extensions.output;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.extensions.IExtension;

import se.kuseman.payloadbuilder.api.OutputWriter;

/** Output format extension */
public interface IOutputFormatExtension extends IExtension
{
    /** Get title for this extension. Shown in comboxes etc. */
    String getTitle();

    /** The order in which the extension is shown in combobox in toolbar and in result tab pane */
    default int order()
    {
        return 0;
    }

    /**
     * Create the output writer for this extension
     *
     * @param file The query file that the output writer is created for
     * @return Created output writer
     */
    OutputWriter createOutputWriter(IQueryFile file);
}
