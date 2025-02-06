package com.queryeer;

import java.awt.Dimension;
import java.awt.Image;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.UIManager;

import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.kordamp.ikonli.swing.FontIcon;

/** Constants in Queryeer */
public class Constants
{
    private Constants()
    {
    }

    public static final Dimension DEFAULT_DIALOG_SIZE = new Dimension(1000, 800);
    public static final Icon CHECK_CIRCLE_ICON = FontIcon.of(FontAwesome.CHECK_CIRCLE);
    public static final Icon PLAY_ICON = FontIcon.of(FontAwesome.PLAY);
    public static final Icon CLOSE_ICON = FontIcon.of(FontAwesome.CLOSE);
    public static final Icon WARNING_ICON = FontIcon.of(FontAwesome.WARNING);
    public static final Icon STICKY_NOTE_O = FontIcon.of(FontAwesome.STICKY_NOTE_O);
    public static final Icon FILE_CODE_O = FontIcon.of(FontAwesome.FILE_CODE_O);
    static final Icon BELL_O = FontIcon.of(FontAwesome.BELL_O);
    static final Icon FOLDER_OPEN_O = FontIcon.of(FontAwesome.FOLDER_OPEN_O);
    static final Icon SAVE = FontIcon.of(FontAwesome.SAVE);
    static final Icon PLAY_CIRCLE = FontIcon.of(FontAwesome.PLAY_CIRCLE);
    static final Icon STOP_CIRCLE = FontIcon.of(FontAwesome.STOP_CIRCLE);
    static final Icon FILE_TEXT_O = FontIcon.of(FontAwesome.FILE_TEXT_O);
    static final Icon ARROWS_V = FontIcon.of(FontAwesome.ARROWS_V);
    static final Icon ARROWS_H = FontIcon.of(FontAwesome.ARROWS_H);
    static final Icon PARAGRAPH = FontIcon.of(FontAwesome.PARAGRAPH);
    static final Icon EDIT = FontIcon.of(FontAwesome.EDIT);
    public static final Icon COG = FontIcon.of(FontAwesome.COG);
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
