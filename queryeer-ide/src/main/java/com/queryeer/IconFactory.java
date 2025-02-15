package com.queryeer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.Icon;
import javax.swing.UIManager;

import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.kordamp.ikonli.swing.FontIcon;

import com.queryeer.api.service.IIconFactory;

/**
 * Icon factory. NOTE! This class is public to easier get icons from quereer-ide project classes without need to inject.
 */
public class IconFactory implements IIconFactory
{
    private static boolean DARKTHEME;

    static
    {
        // This class lives for as long as the application so no need to remove the listener
        UIManager.addPropertyChangeListener(new PropertyChangeListener()
        {
            @Override
            public void propertyChange(PropertyChangeEvent evt)
            {
                if ("lookAndFeel".equalsIgnoreCase(evt.getPropertyName()))
                {
                    DARKTHEME = UiUtils.isDarkLookAndFeel();
                }
            }
        });
        DARKTHEME = UiUtils.isDarkLookAndFeel();
    }

    @Override
    public Icon getIcon(Provider provider, String name)
    {
        return of(provider, 16, name);
    }

    @Override
    public Icon getIcon(Provider provider, String name, int size)
    {
        return of(provider, size, name);
    }

    /** Create icon of provider and name. */
    public static Icon of(Provider provider, int size, String name)
    {
        switch (provider)
        {
            case FONTAWESOME:
            {
                return new FontawesomeIcon(FontAwesome.valueOf(name), size);
            }
            default:
                throw new IllegalArgumentException("Unexpected value: " + provider);
        }
    }

    /** Create an icon from FontAwesome collection. */
    public static Icon of(FontAwesome ikon)
    {
        return of(Provider.FONTAWESOME, 16, ikon.name());
    }

    /** Create an icon from FontAwesome collection with provided size. */
    public static Icon of(FontAwesome ikon, int size)
    {
        return of(Provider.FONTAWESOME, size, ikon.name());
    }

    /** Class that wraps 2 icons in one and uses a dark/light icon depending on current LAF. */
    private static class FontawesomeIcon implements Icon
    {
        private final FontAwesome icon;
        private final int size;
        private Icon darkIcon;
        private Icon lightIcon;

        FontawesomeIcon(FontAwesome icon, int size)
        {
            this.icon = icon;
            this.size = size;
            // Lazy create icons, 99% of the time we only use one of them and the other one is used when switching LAF
            // which happens very rarely
            this.darkIcon = DARKTHEME ? null
                    : FontIcon.of(icon, size, Color.BLACK);
            this.lightIcon = DARKTHEME ? FontIcon.of(icon, size, Constants.DARK_THEME_LIGHT_COLOR)
                    : null;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y)
        {
            getIcon().paintIcon(c, g, x, y);
        }

        @Override
        public int getIconWidth()
        {
            return getIcon().getIconWidth();
        }

        @Override
        public int getIconHeight()
        {
            return getIcon().getIconHeight();
        }

        private Icon getIcon()
        {
            if (DARKTHEME
                    && lightIcon == null)
            {
                lightIcon = FontIcon.of(icon, size, Constants.DARK_THEME_LIGHT_COLOR);
            }
            else if (!DARKTHEME
                    && darkIcon == null)
            {
                darkIcon = FontIcon.of(icon, size, Color.BLACK);
            }

            return DARKTHEME ? lightIcon
                    : darkIcon;
        }
    }
}
