package com.queryeer.component;

import javax.swing.Icon;

import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.kordamp.ikonli.swing.FontIcon;

import com.queryeer.api.component.IIconProvider;
import com.queryeer.api.service.Inject;

@Inject
class IconProvider implements IIconProvider
{
    @Override
    public Icon getIcon(Provider provider, String name)
    {
        switch (provider)
        {
            case FontAwesome:
                return FontIcon.of(FontAwesome.valueOf(name));
            default:
                throw new IllegalArgumentException("Unkown provider: " + provider);
        }
    }
}
