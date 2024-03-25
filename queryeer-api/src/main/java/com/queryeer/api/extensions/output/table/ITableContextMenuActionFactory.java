package com.queryeer.api.extensions.output.table;

import java.util.List;

import com.queryeer.api.extensions.IExtension;
import com.queryeer.api.extensions.IExtensionAction;

/**
 * Definition of a factory that produces {@link IExtensionAction IExtensionActions} for {@link ITableOutputComponent}'s context menu.
 *
 * <pre>
 * These actions are places in the toolbar along side with the selected output component
 * </pre>
 */
public interface ITableContextMenuActionFactory extends IExtension
{
    /**
     * Creates actions for provided output component.
     *
     * @return Created actions or null if no actions is available for provided component
     */
    List<ITableContextMenuAction> create(ITableOutputComponent outputcomponent);
}
