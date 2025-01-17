package com.queryeer.api.service;

import javax.swing.Icon;

/** Definition of an icon factory where clients get get icons from various providers */
public interface IIconFactory
{
    /** Get icon with provided provider and name */
    Icon getIcon(Provider provider, String name);

    /** Icon provider */
    enum Provider
    {
        FONTAWESOME
    }

}
