package com.queryeer.api.extensions.output;

import java.util.List;

import com.queryeer.api.extensions.IExtension;
import com.queryeer.api.extensions.IExtensionAction;

/**
 * Definition of a factory that produces {@link IExtensionAction IExtensionActions} for {@link IOutputComponent IOutputComponents}.
 *
 * <pre>
 * These actions are places in the toolbar along side with the selected output component
 * </pre>
 */
public interface IOutputToolbarActionFactory extends IExtension
{
    /**
     * Creates actions for provided output component.
     *
     * @return Created actions or null if no actions is available for provided component
     */
    List<IExtensionAction> create(IOutputComponent outputcomponent);
}
