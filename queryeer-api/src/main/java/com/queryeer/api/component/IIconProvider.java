package com.queryeer.api.component;

import javax.swing.Icon;

/** Definition of icon provider. Can be used to get icons for components UI */
public interface IIconProvider
{
    /** Get icon from provider with provided name */
    Icon getIcon(Provider provider, String name);

    /** Icon provider */
    enum Provider
    {
        FontAwesome;
    }
}
