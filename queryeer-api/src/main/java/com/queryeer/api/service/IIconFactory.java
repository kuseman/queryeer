package com.queryeer.api.service;

import java.awt.Image;
import java.util.List;

import javax.swing.Icon;

/** Definition of an icon factory where clients get get icons from various providers */
public interface IIconFactory
{
    /** Get icon with provided provider and name */
    Icon getIcon(Provider provider, String name);

    /** Retun application icons used for dialogs etc. */
    List<? extends Image> getApplicationIcons();

    /** Icon provider */
    enum Provider
    {
        FONTAWESOME
    }

}
