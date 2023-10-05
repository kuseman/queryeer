package com.queryeer.output.table;

import static java.util.Arrays.asList;

import java.awt.event.ActionEvent;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;

import com.queryeer.Constants;
import com.queryeer.api.extensions.IExtensionAction;
import com.queryeer.api.extensions.output.table.ITableContextMenuActionFactory;
import com.queryeer.api.extensions.output.table.ITableOutputComponent;
import com.queryeer.api.extensions.output.table.ITableOutputComponent.ClickedCell;
import com.queryeer.dialog.ValueDialog;

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
                    ValueDialog.showValueDialog("Json viewer - " + lastClickedCell.getColumnHeader(), value, ValueDialog.Format.JSON);
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
                    ValueDialog.showValueDialog("XML viewer - " + lastClickedCell.getColumnHeader(), value, ValueDialog.Format.XML);
                }
            };
        }
    }
}
