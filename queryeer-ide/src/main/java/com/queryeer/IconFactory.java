package com.queryeer;

import java.awt.Image;
import java.util.List;

import javax.swing.Icon;

import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.kordamp.ikonli.swing.FontIcon;

import com.queryeer.api.service.IIconFactory;

/** Default implementation of {@link IIconFactory} */
class IconFactory implements IIconFactory
{
    @Override
    public Icon getIcon(Provider provider, String name)
    {
        switch (provider)
        {
            case FONTAWESOME:
            {
                return FontIcon.of(FontAwesome.valueOf(name));
            }
            default:
                throw new IllegalArgumentException("Unexpected value: " + provider);
        }
    }

    @Override
    public List<? extends Image> getApplicationIcons()
    {
        return Constants.APPLICATION_ICONS;
    }
}
