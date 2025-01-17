package com.queryeer.dialog;

import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

import org.fife.rsta.ui.search.FindDialog;
import org.fife.rsta.ui.search.SearchEvent;
import org.fife.rsta.ui.search.SearchListener;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.fife.ui.rtextarea.SearchResult;

import com.queryeer.Constants;
import com.queryeer.api.component.DialogUtils;

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

        DialogUtils.AFrame frame = new DialogUtils.AFrame(title);
        RSyntaxTextArea rta = new RSyntaxTextArea();

        FindDialog findDialog = new FindDialog(frame, new ValueSearchListener(rta))
        {
            {
                context.setSearchWrap(true);
                context.setMatchCase(false);
                context.setMarkAll(false);
            }

            @Override
            public void setVisible(boolean b)
            {
                Window activeWindow = javax.swing.FocusManager.getCurrentManager()
                        .getActiveWindow();

                if (b)
                {
                    setLocationRelativeTo(activeWindow);
                }
                super.setVisible(b);
            }
        };

        rta.getInputMap()
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F, Toolkit.getDefaultToolkit()
                        .getMenuShortcutKeyMaskEx()), "FIND");
        rta.getActionMap()
                .put("FIND", new AbstractAction()
                {

                    @Override
                    public void actionPerformed(ActionEvent e)
                    {
                        findDialog.setVisible(true);
                    }
                });

        rta.setCodeFoldingEnabled(true);
        rta.setBracketMatchingEnabled(true);
        // CSOFF
        rta.setColumns(80);
        rta.setRows(40);
        // CSON
        if (value instanceof Collection
                || value instanceof Map)
        {
            // Always use json for map/collection types
            rta.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON);
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
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.pack();
        frame.setSize(Constants.DEFAULT_DIALOG_SIZE);
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

    private static class ValueSearchListener implements SearchListener
    {
        private final RSyntaxTextArea textArea;

        ValueSearchListener(RSyntaxTextArea textArea)
        {
            this.textArea = textArea;
        }

        @Override
        public String getSelectedText()
        {
            return textArea.getSelectedText();
        }

        @Override
        public void searchEvent(SearchEvent e)
        {
            SearchEvent.Type type = e.getType();
            SearchContext context = e.getSearchContext();
            SearchResult result;

            switch (type)
            {
                default:
                case MARK_ALL:
                    result = SearchEngine.markAll(textArea, context);
                    break;
                case FIND:
                    result = SearchEngine.find(textArea, context);
                    if (!result.wasFound()
                            || result.isWrapped())
                    {
                        UIManager.getLookAndFeel()
                                .provideErrorFeedback(textArea);
                    }
                    break;
                case REPLACE:
                    result = SearchEngine.replace(textArea, context);
                    if (!result.wasFound()
                            || result.isWrapped())
                    {
                        UIManager.getLookAndFeel()
                                .provideErrorFeedback(textArea);
                    }
                    break;
                case REPLACE_ALL:
                    result = SearchEngine.replaceAll(textArea, context);
                    Window activeWindow = javax.swing.FocusManager.getCurrentManager()
                            .getActiveWindow();
                    JOptionPane.showMessageDialog(activeWindow, result.getCount() + " occurrences replaced.");
                    break;
            }
        }
    }
}
