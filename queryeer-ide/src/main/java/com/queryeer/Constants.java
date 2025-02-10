package com.queryeer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Image;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.UIManager;

import org.kordamp.ikonli.fontawesome.FontAwesome;

/** Constants in Queryeer */
public class Constants
{
    private Constants()
    {
    }

    public static final Dimension DEFAULT_DIALOG_SIZE = new Dimension(1000, 800);
    /** Color to use in places when a dark theme is used for light assets like icons etc. */
    public static final String DARK_THEME_LIGHT_COLOR_HEX = "#E0E0E0";
    public static final Color DARK_THEME_LIGHT_COLOR = Color.decode(DARK_THEME_LIGHT_COLOR_HEX);
    public static final Icon CHECK_CIRCLE_ICON = IconFactory.of(FontAwesome.CHECK_CIRCLE);
    public static final Icon PLAY_ICON = IconFactory.of(FontAwesome.PLAY);
    public static final Icon CLOSE_ICON = IconFactory.of(FontAwesome.CLOSE);
    public static final Icon WARNING_ICON = IconFactory.of(FontAwesome.WARNING);
    public static final Icon STICKY_NOTE_O = IconFactory.of(FontAwesome.STICKY_NOTE_O);
    public static final Icon FILE_CODE_O = IconFactory.of(FontAwesome.FILE_CODE_O);
    static final Icon BELL_O = IconFactory.of(FontAwesome.BELL_O);
    static final Icon FOLDER_OPEN_O = IconFactory.of(FontAwesome.FOLDER_OPEN_O);
    static final Icon SAVE = IconFactory.of(FontAwesome.SAVE);
    static final Icon PLAY_CIRCLE = IconFactory.of(FontAwesome.PLAY_CIRCLE);
    static final Icon STOP_CIRCLE = IconFactory.of(FontAwesome.STOP_CIRCLE);
    static final Icon FILE_TEXT_O = IconFactory.of(FontAwesome.FILE_TEXT_O);
    static final Icon ARROWS_V = IconFactory.of(FontAwesome.ARROWS_V);
    static final Icon ARROWS_H = IconFactory.of(FontAwesome.ARROWS_H);
    static final Icon PARAGRAPH = IconFactory.of(FontAwesome.PARAGRAPH);
    static final Icon EDIT = IconFactory.of(FontAwesome.EDIT);
    public static final Icon COG = IconFactory.of(FontAwesome.COG);
    public static final int SCROLLBAR_WIDTH;

    static
    {
        Integer scrollBarWidth = (Integer) UIManager.get("ScrollBar.width");
        SCROLLBAR_WIDTH = scrollBarWidth != null ? scrollBarWidth.intValue()
                : 17;
    }

    public static final Image APPLICATION_ICON_48;
    public static final Image APPLICATION_ICON_96;
    public static final List<? extends Image> APPLICATION_ICONS;

    static
    {
        Image icon48 = null;
        Image icon96 = null;

        try
        {
            icon48 = ImageIO.read(QueryeerView.class.getResource("/icons/icons8-database-administrator-48.png"));
            icon96 = ImageIO.read(QueryeerView.class.getResource("/icons/icons8-database-administrator-96.png"));
        }
        catch (IOException e)
        {
            Main.LOGGER.error("Error reading application icons", e);
        }
        APPLICATION_ICON_48 = icon48;
        APPLICATION_ICON_96 = icon96;
        APPLICATION_ICONS = List.of(icon48, icon96);
    }
}
