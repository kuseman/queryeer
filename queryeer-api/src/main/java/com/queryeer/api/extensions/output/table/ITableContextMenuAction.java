package com.queryeer.api.extensions.output.table;

import com.queryeer.api.extensions.IExtensionAction;

/** Definition of an extension action. Depending on context is placed in various places in Queryeer */
public interface ITableContextMenuAction extends IExtensionAction
{
    /** Returns true if this action supports marking of links in table when mouse over */
    default boolean supportsLinks()
    {
        return false;
    }

    /** Returns true if provided value should yield a link when mouse over. When clicked this action will be called */
    default boolean showLink(Object value)
    {
        return false;
    }

    /** Should this action be shown for provided value/header. */
    default boolean showContextMenu(Object value, String header)
    {
        return true;
    }
}
