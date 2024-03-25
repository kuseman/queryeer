package com.queryeer.output.table;

import static java.util.Arrays.asList;

import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.Action;

import org.apache.commons.lang3.StringUtils;
import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.kordamp.ikonli.swing.FontIcon;

import com.queryeer.Constants;
import com.queryeer.api.extensions.output.table.ITableContextMenuAction;
import com.queryeer.api.extensions.output.table.ITableContextMenuActionFactory;
import com.queryeer.api.extensions.output.table.ITableOutputComponent;
import com.queryeer.api.extensions.output.table.ITableOutputComponent.ClickedCell;
import com.queryeer.dialog.ValueDialog;

/** Default factory for table context menu */
class TableContextMenuActionFactory implements ITableContextMenuActionFactory
{
    @Override
    public List<ITableContextMenuAction> create(ITableOutputComponent outputcomponent)
    {
        return asList(new ViewAsJsonAction(outputcomponent), new ViewAsXmlAction(outputcomponent), new OpenInBrowserAction(outputcomponent));
    }

    /** View as JSON */
    private static class ViewAsJsonAction implements ITableContextMenuAction
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
                    ValueDialog.showValueDialog("Json viewer - " + lastClickedCell.getColumnHeader(), value, ValueDialog.Format.JSON);
                }
            };
        }

        @Override
        public boolean supportsLinks()
        {
            return true;
        }

        @Override
        public boolean showLink(Object value)
        {
            if (value instanceof Map
                    || value instanceof Collection)
            {
                return true;
            }

            if (value instanceof CharSequence cs
                    && cs.length() >= 2)
            {
                if ((cs.charAt(0) == '{'
                        && cs.charAt(cs.length() - 1) == '}')
                        || (cs.charAt(0) == '['
                                && cs.charAt(cs.length() - 1) == ']'))
                {
                    return true;
                }
            }
            return false;
        }
    }

    private static class ViewAsXmlAction implements ITableContextMenuAction
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
                    ValueDialog.showValueDialog("XML viewer - " + lastClickedCell.getColumnHeader(), value, ValueDialog.Format.XML);
                }
            };
        }

        @Override
        public boolean supportsLinks()
        {
            return true;
        }

        @Override
        public boolean showLink(Object value)
        {
            return value instanceof CharSequence cs
                    && cs.length() > 2
                    && cs.charAt(0) == '<'
                    && cs.charAt(cs.length() - 1) == '>';
        }
    }

    private static class OpenInBrowserAction implements ITableContextMenuAction
    {
        private final ITableOutputComponent outputcomponent;

        OpenInBrowserAction(ITableOutputComponent outputcomponent)
        {
            this.outputcomponent = outputcomponent;
        }

        @Override
        public int order()
        {
            return 30;
        }

        @Override
        public Action getAction()
        {
            return new AbstractAction("Open In Browser", FontIcon.of(FontAwesome.CLOUD))
            {
                @Override
                public void actionPerformed(ActionEvent e)
                {
                    ClickedCell lastClickedCell = outputcomponent.getLastClickedCell();
                    Object value = lastClickedCell.getValue();
                    if (value == null)
                    {
                        return;
                    }
                    try
                    {
                        Desktop.getDesktop()
                                .browse(new URI(String.valueOf(value)));
                    }
                    catch (Exception e1)
                    {
                        // SWALLOW
                    }
                }
            };
        }

        @Override
        public boolean supportsLinks()
        {
            return true;
        }

        @Override
        public boolean showLink(Object value)
        {
            return value instanceof String str
                    && StringUtils.startsWithIgnoreCase(str, "http");
        }
    }
}
