package com.queryeer;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import java.awt.Dimension;
import java.awt.Image;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.UIManager;

import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.kordamp.ikonli.swing.FontIcon;

/** Icons in Queryeer */
public interface Constants
{
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
    static final Icon INDENT = FontIcon.of(FontAwesome.INDENT);
    static final Icon EDIT = FontIcon.of(FontAwesome.EDIT);
    static final Icon COG = FontIcon.of(FontAwesome.COG);
    static final Icon SEARCH = FontIcon.of(FontAwesome.SEARCH);
    static final Icon SHARE = FontIcon.of(FontAwesome.SHARE);
    public static final int SCROLLBAR_WIDTH = ((Integer) UIManager.get("ScrollBar.width")).intValue();
    public static final List<? extends Image> APPLICATION_ICONS = asList("icons8-database-administrator-48.png", "icons8-database-administrator-96.png").stream()
            .map(name -> QueryeerView.class.getResource("/icons/" + name))
            .map(stream ->
            {
                try
                {
                    return ImageIO.read(stream);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(toList());

}
