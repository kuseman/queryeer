package com.queryeer;

import java.io.PrintWriter;

import com.queryeer.api.extensions.output.IOutputComponent;

/** Text output extension */
public interface ITextOutputComponent extends IOutputComponent
{
    /** Return the print writer for this component */
    PrintWriter getTextWriter();
}
