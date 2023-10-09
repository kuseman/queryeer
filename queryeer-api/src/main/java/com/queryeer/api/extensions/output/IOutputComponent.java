package com.queryeer.api.extensions.output;

import java.awt.Component;

import javax.swing.Icon;

import com.queryeer.api.IQueryFile;

import se.kuseman.payloadbuilder.api.OutputWriter;

/** Definition of an output component */
public interface IOutputComponent
{
    /** Title that is shown in result tab component */
    String title();

    /** Icon that is shown in result tab component */
    default Icon icon()
    {
        return null;
    }

    /** Return the UI component */
    Component getComponent();

    /** Clear eventual state in the component */
    default void clearState()
    {
    }

    /** Creates an {@link OutputWriter} for provided query file */
    OutputWriter createOutputWriter(IQueryFile queryFile);
}
