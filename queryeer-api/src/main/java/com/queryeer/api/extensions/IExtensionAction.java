package com.queryeer.api.extensions;

import javax.swing.Action;

/** Definition of an extension action. Depending on context is placed in various places in Queryeer */
public interface IExtensionAction
{
    /** Return the order of the action */
    default int order()
    {
        return Integer.MAX_VALUE;
    }

    /** Return the Swing action */
    Action getAction();
}
