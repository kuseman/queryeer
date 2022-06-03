package com.queryeer.output.table;

import static java.util.Arrays.asList;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JFrame;
import javax.swing.WindowConstants;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import com.queryeer.Constants;
import com.queryeer.api.extensions.IExtensionAction;
import com.queryeer.api.extensions.output.table.ITableContextMenuActionFactory;
import com.queryeer.api.extensions.output.table.ITableOutputComponent;
import com.queryeer.api.extensions.output.table.ITableOutputComponent.ClickedCell;

/** Default factory for table context menu */
class TableContextMenuActionFactory implements ITableContextMenuActionFactory
{
    @Override
    public List<IExtensionAction> create(ITableOutputComponent outputcomponent)
    {
        return asList(new ViewAsJsonAction(outputcomponent), new ViewAsXmlAction(outputcomponent));
    }

    /** View as JSON */
    private static class ViewAsJsonAction implements IExtensionAction
    {
        private final ITableOutputComponent outputcomponent;

        ViewAsJsonAction(ITableOutputComponent outputcomponent)
        {
            this.outputcomponent = outputcomponent;
        }

        @Override
        public int order()
        {
            return 10;
        }

        @Override
        public Action getAction()
        {
            return new AbstractAction("View as JSON", Constants.STICKY_NOTE_O)
            {
                @Override
                public void actionPerformed(ActionEvent e)
                {
                    ClickedCell lastClickedCell = outputcomponent.getLastClickedCell();
                    Object value = lastClickedCell.getValue();
                    if (value instanceof String)
                    {
                        try
                        {
                            value = Utils.READER.readValue((String) value);
                        }
                        catch (IOException ee)
                        {
                        }
                    }
                    showValueDialog("Json viewer - " + lastClickedCell.getColumnHeader(), value, SyntaxConstants.SYNTAX_STYLE_JSON);
                }
            };
        }

    }

    private static class ViewAsXmlAction implements IExtensionAction
    {
        private final ITableOutputComponent outputcomponent;

        ViewAsXmlAction(ITableOutputComponent outputcomponent)
        {
            this.outputcomponent = outputcomponent;
        }

        @Override
        public int order()
        {
            return 20;
        }

        @Override
        public Action getAction()
        {
            return new AbstractAction("View as XML", Constants.STICKY_NOTE_O)
            {
                @Override
                public void actionPerformed(ActionEvent e)
                {
                    ClickedCell lastClickedCell = outputcomponent.getLastClickedCell();
                    Object value = lastClickedCell.getValue();
                    if (value instanceof String)
                    {
                        value = Utils.formatXML((String) value);
                    }
                    showValueDialog("XML viewer - " + lastClickedCell.getColumnHeader(), value, SyntaxConstants.SYNTAX_STYLE_XML);
                }
            };
        }
    }

    static void showValueDialog(String title, Object val, String preferredSyntax)
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
            rta.setText(Model.getPrettyJson(value));
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
        frame.setVisible(true);
    }
}
