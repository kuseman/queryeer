package com.queryeer.api.service;

import javax.swing.Icon;

/** Definition of an icon factory where clients get get icons from various providers */
public interface IIconFactory
{
    /** Get icon with provided provider and name */
    Icon getIcon(Provider provider, String name);

    /** Get icon with provider and name and size */
    Icon getIcon(Provider fontawesome, String string, int size);

    /** Icon provider */
    enum Provider
    {
        FONTAWESOME
    }

}
