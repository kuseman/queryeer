package com.queryeer.dialog;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import com.queryeer.Constants;

/** Factory for creating value dialogs with formatted content */
public final class ValueDialog
{
    /** Show a value dialog with provided title value and format */
    public static void showValueDialog(String title, Object val, Format format)
    {
        Object value = val;
        switch (format)
        {
            case JSON:
                value = Utils.formatJson(value);
                break;
            case XML:
                value = Utils.formatXML(String.valueOf(value));
                break;
            default:
                break;
        }

        showValueDialog(title, value, format.syntax);
    }

    private static void showValueDialog(String title, Object val, String preferredSyntax)
    {
        Object value = val;
        if (value == null)
        {
            return;
        }

        if (value.getClass()
                .isArray())
        {
            int length = Array.getLength(value);
            List<Object> list = new ArrayList<>(length);
            for (int i = 0; i < length; i++)
            {
                list.add(Array.get(value, i));
            }
            value = list;
        }

        JFrame frame = new JFrame(title);
        frame.setIconImages(Constants.APPLICATION_ICONS);
        RSyntaxTextArea rta = new RSyntaxTextArea();
        // CSOFF
        rta.setColumns(80);
        rta.setRows(40);
        // CSON
        if (value instanceof Collection
                || value instanceof Map)
        {
            // Always use json for map/collection types
            rta.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON);
            rta.setCodeFoldingEnabled(true);
            rta.setBracketMatchingEnabled(true);
            rta.setText(Utils.formatJson(value));
        }
        else
        {
            rta.setSyntaxEditingStyle(preferredSyntax);
            rta.setText(String.valueOf(value));
        }
        rta.setCaretPosition(0);
        rta.setEditable(false);
        RTextScrollPane sp = new RTextScrollPane(rta);
        frame.getContentPane()
                .add(sp);
        frame.setSize(Constants.DEFAULT_DIALOG_SIZE);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        // Close dialog on escape
        frame.getRootPane()
                .registerKeyboardAction(new ActionListener()
                {
                    @Override
                    public void actionPerformed(ActionEvent e)
                    {
                        frame.setVisible(false);
                    }
                }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        frame.setVisible(true);
    }

    /** Format type of dialog content */
    public static enum Format
    {
        JSON(SyntaxConstants.SYNTAX_STYLE_JSON),
        XML(SyntaxConstants.SYNTAX_STYLE_XML),
        UNKOWN(SyntaxConstants.SYNTAX_STYLE_NONE);

        private final String syntax;

        Format(String syntax)
        {
            this.syntax = syntax;
        }
    }
}
