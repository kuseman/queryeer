package com.queryeer.api.extensions.output;

import javax.swing.KeyStroke;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.extensions.IExtension;

import se.kuseman.payloadbuilder.api.OutputWriter;

/**
 * Definition of an output extension. Add an output option for queries in toolbar and
 */
public interface IOutputExtension extends IExtension
{
    /** Get title for this extension. Shown in comboxes etc. */
    String getTitle();

    /** The order in which the extension is shown in combobox in toolbar and in result tab pane */
    default int order()
    {
        return 0;
    }

    /** Get key stroke for this output extension */
    default KeyStroke getKeyStroke()
    {
        return null;
    }

    /**
     * Get if this output extension support output formats
     */
    default boolean supportsOutputFormats()
    {
        return false;
    }

    /** Get the configurable class if this extension supports configuration */
    default Class<? extends IConfigurable> getConfigurableClass()
    {
        return null;
    }

    /**
     * Create the result component that is shown in the result tab panel at the bottom of query files.
     *
     * @return Created result component
     */
    default IOutputComponent createResultComponent()
    {
        return null;
    }

    /** Return the class of the output component that should be selected when this output extension is selected for execution */
    default Class<? extends IOutputComponent> getResultOutputComponentClass()
    {
        return null;
    }

    /**
     * Create the output writer for this extension
     *
     * <pre>
     * NOTE! Is called on EDT so user interaction is supported here, for example asking for input etc.
     * </pre>
     *
     * @param file The query file that the output writer is created for
     * @return Created output writer or null if there wasn't any writer created. This could mean the user aborted some action etc.
     */
    OutputWriter createOutputWriter(IQueryFile file);
}
