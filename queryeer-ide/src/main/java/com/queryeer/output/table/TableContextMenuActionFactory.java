package com.queryeer.output.table;

import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.Action;

import org.apache.commons.lang3.Strings;
import org.kordamp.ikonli.fontawesome.FontAwesome;

import com.queryeer.Constants;
import com.queryeer.IconFactory;
import com.queryeer.api.component.IDialogFactory;
import com.queryeer.api.extensions.output.table.ITableContextMenuAction;
import com.queryeer.api.extensions.output.table.ITableContextMenuActionFactory;
import com.queryeer.api.extensions.output.table.ITableOutputComponent;
import com.queryeer.api.extensions.output.table.ITableOutputComponent.SelectedCell;
import com.queryeer.output.table.Model.QueryeerImage;

/** Default factory for table context menu */
class TableContextMenuActionFactory implements ITableContextMenuActionFactory
{
    private final IDialogFactory dialogFactory;

    TableContextMenuActionFactory(IDialogFactory dialogFactory)
    {
        this.dialogFactory = requireNonNull(dialogFactory, "dialogFactory");
    }

    @Override
    public List<ITableContextMenuAction> create(ITableOutputComponent outputcomponent)
    {
        return asList(new CopyAsCsvAction(outputcomponent), new ViewAsJsonAction(dialogFactory), new ViewAsXmlAction(dialogFactory), new OpenInBrowserAction());
    }

    private static class CopyAsCsvAction implements ITableContextMenuAction
    {
        private final ITableOutputComponent outputcomponent;

        CopyAsCsvAction(ITableOutputComponent outputcomponent)
        {
            this.outputcomponent = outputcomponent;
        }

        @Override
        public int order()
        {
            return 5;
        }

        @Override
        public boolean showContextMenu(ITableOutputComponent.SelectedCell selectedCell)
        {
            int column = -1;
            for (SelectedCell cell : outputcomponent.getSelectedCells())
            {
                if (column == -1)
                {
                    column = cell.getColumnIndex();
                }
                else if (column != cell.getColumnIndex())
                {
                    // Only single column selection
                    return false;
                }
            }
            return column != -1;
        }

        @Override
        public Action getAction()
        {
            return new AbstractAction("Copy as CSV")
            {
                @Override
                public void actionPerformed(ActionEvent e)
                {
                    List<ITableOutputComponent.SelectedCell> cells = outputcomponent.getSelectedCells();
                    StringBuilder sb = new StringBuilder();
                    for (ITableOutputComponent.SelectedCell cell : cells)
                    {
                        if (sb.length() > 0)
                        {
                            sb.append(',');
                        }
                        Object val = cell.getCellValue();
                        sb.append(val != null ? val.toString()
                                : "");
                    }
                    java.awt.Toolkit.getDefaultToolkit()
                            .getSystemClipboard()
                            .setContents(new java.awt.datatransfer.StringSelection(sb.toString()), null);
                }
            };
        }
    }

    /** View as JSON */
    private static class ViewAsJsonAction implements ITableContextMenuAction
    {
        private final IDialogFactory dialogFactory;
        private ITableOutputComponent.SelectedCell contextCell;

        ViewAsJsonAction(IDialogFactory dialogFactory)
        {
            this.dialogFactory = dialogFactory;
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
                    ITableOutputComponent.SelectedCell cell = contextCell;
                    if (cell == null)
                    {
                        return;
                    }
                    dialogFactory.showValueDialog("Json viewer - " + cell.getColumnHeader(), cell.getCellValue(), IDialogFactory.Format.JSON);
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

        @Override
        public boolean showContextMenu(ITableOutputComponent.SelectedCell selectedCell)
        {
            if (showLink(selectedCell.getCellValue()))
            {
                contextCell = selectedCell;
                return true;
            }
            return false;
        }
    }

    private static class ViewAsXmlAction implements ITableContextMenuAction
    {
        private final IDialogFactory dialogFactory;
        private ITableOutputComponent.SelectedCell contextCell;

        ViewAsXmlAction(IDialogFactory dialogFactory)
        {
            this.dialogFactory = dialogFactory;
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
                    ITableOutputComponent.SelectedCell cell = contextCell;
                    if (cell == null)
                    {
                        return;
                    }
                    dialogFactory.showValueDialog("XML viewer - " + cell.getColumnHeader(), cell.getCellValue(), IDialogFactory.Format.XML);
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

        @Override
        public boolean showContextMenu(ITableOutputComponent.SelectedCell selectedCell)
        {
            if (showLink(selectedCell.getCellValue()))
            {
                contextCell = selectedCell;
                return true;
            }
            return false;
        }
    }

    private static class OpenInBrowserAction implements ITableContextMenuAction
    {
        private ITableOutputComponent.SelectedCell contextCell;

        OpenInBrowserAction()
        {
        }

        @Override
        public int order()
        {
            return 30;
        }

        @Override
        public Action getAction()
        {
            return new AbstractAction("Open In Browser", IconFactory.of(FontAwesome.CLOUD))
            {
                @Override
                public void actionPerformed(ActionEvent e)
                {
                    ITableOutputComponent.SelectedCell cell = contextCell;
                    if (cell == null)
                    {
                        return;
                    }
                    Object value = cell.getCellValue();
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
            if (value instanceof String str)
            {
                return Strings.CI.startsWith(str, "http://")
                        || Strings.CI.startsWith(str, "https://");
            }
            else if (value instanceof QueryeerImage)
            {
                return true;
            }
            return false;
        }

        @Override
        public boolean showContextMenu(ITableOutputComponent.SelectedCell selectedCell)
        {
            if (showLink(selectedCell.getCellValue()))
            {
                contextCell = selectedCell;
                return true;
            }
            return false;
        }
    }
}
