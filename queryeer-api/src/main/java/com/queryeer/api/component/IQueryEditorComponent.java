package com.queryeer.api.component;

import static java.util.Collections.emptyList;

import java.awt.Component;
import java.util.List;

import com.queryeer.api.extensions.IExtensionAction;
import com.queryeer.api.extensions.IMainMenuAction;
import com.queryeer.api.extensions.IMainToolbarAction;

/** Definition of an query editor component */
public interface IQueryEditorComponent
{
    /** Gets the actual component */
    Component getComponent();

    /**
     * Get list of component actions for this editor component.
     * 
     * <pre>
     * Actions should be one of:
     *  - {@link IMainMenuAction}. To be populated in menu
     *  - {@link IMainToolbarAction}. To be populated in main toolbar
     * </pre>
     */
    default List<IExtensionAction> getComponentActions()
    {
        return emptyList();
    }
}
