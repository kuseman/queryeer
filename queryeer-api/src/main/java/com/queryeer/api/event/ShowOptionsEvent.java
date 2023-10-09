package com.queryeer.api.event;

import com.queryeer.api.extensions.IConfigurable;

/** Event fired when options dialog should be shown */
public class ShowOptionsEvent extends Event
{
    private final Class<? extends IConfigurable> configurableClassToSelect;

    public ShowOptionsEvent(Class<? extends IConfigurable> configurableClassToSelect)
    {
        this.configurableClassToSelect = configurableClassToSelect;
    }

    public Class<? extends IConfigurable> getConfigurableClassToSelect()
    {
        return configurableClassToSelect;
    }
}
