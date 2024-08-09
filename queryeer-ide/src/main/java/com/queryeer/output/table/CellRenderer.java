package com.queryeer.output.table;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;

import com.queryeer.api.extensions.output.table.ITableContextMenuAction;

/** Cell renderer for table output */
@SuppressWarnings("deprecation")
class CellRenderer extends DefaultTableCellRenderer implements MouseListener, MouseMotionListener
{
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    private static final Color TABLE_NULL_BACKGROUND = new Color(255, 253, 237);
    private static final Color TABLE_REGULAR_BACKGROUND = UIManager.getColor("Table.dropCellBackground");
    private static final Cursor HAND_CURSOR = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
    private static final Cursor DEFAULT_CURSOR = Cursor.getDefaultCursor();

    private final List<ITableContextMenuAction> actions;
    private final int actionsSize;
    private int row = -1;
    private int col = -1;
    private boolean isRollover = false;

    CellRenderer(List<ITableContextMenuAction> actions)
    {
        this.actions = new ArrayList<>(actions);
        this.actions.removeIf(a -> !a.supportsLinks());
        this.actionsSize = this.actions.size();
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object val, boolean isSelected, boolean hasFocus, int row, int column)
    {
        Object value = val;

        if (value != null
                && value.getClass()
                        .isArray())
        {
            // Print HEX string for byte arrays
            if (value.getClass() == byte[].class)
            {
                value = bytesToHex((byte[]) value);
            }
            else
            {
                StringBuilder sb = new StringBuilder();
                int length = Array.getLength(value);
                sb.append("[");
                for (int i = 0; i < length; i++)
                {
                    if (i > 0)
                    {
                        sb.append(", ");
                    }
                    sb.append(Array.get(value, i));
                }
                sb.append("]");
                value = sb.toString();
            }
        }

        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        if (!table.isEditing()
                && this.row == row
                && this.col == column
                && this.isRollover)
        {
            String str = value.toString();
            // To avoid to large html strings abbreviate the string before underline
            if (str.length() > 170)
            {
                str = StringUtils.abbreviate(str, 170);
            }

            setText("<html><u><font color='blue'>" + StringEscapeUtils.escapeHtml4(str));
        }
        else if (value != null)
        {
            setText(value.toString());
        }

        if (value == null)
        {
            setText("NULL");
            if (!isSelected)
            {
                setBackground(TABLE_NULL_BACKGROUND);
            }
        }
        else if (!isSelected)
        {
            setBackground(TABLE_REGULAR_BACKGROUND);
        }

        return this;
    }

    @Override
    public void mouseMoved(MouseEvent e)
    {
        JTable table = (JTable) e.getSource();
        Point pt = e.getPoint();
        // CSOFF
        int prev_row = row;
        int prev_col = col;
        boolean prev_ro = isRollover;
        // CSON
        row = table.rowAtPoint(pt);
        col = table.columnAtPoint(pt);
        isRollover = hasLinkAction(table, row, col);
        if ((row == prev_row
                && col == prev_col
                && Boolean.valueOf(isRollover)
                        .equals(prev_ro))
                || (!isRollover
                        && !prev_ro))
        {
            return;
        }
        Rectangle repaintRect;
        if (isRollover)
        {
            Rectangle r = table.getCellRect(row, col, false);
            repaintRect = prev_ro ? r.union(table.getCellRect(prev_row, prev_col, false))
                    : r;
            table.setCursor(HAND_CURSOR);
        }
        else
        {
            repaintRect = table.getCellRect(prev_row, prev_col, false);
            table.setCursor(DEFAULT_CURSOR);
        }
        table.repaint(repaintRect);
    }

    @Override
    public void mouseDragged(MouseEvent e)
    {
    }

    @Override
    public void mouseEntered(MouseEvent e)
    {
    }

    @Override
    public void mousePressed(MouseEvent e)
    {
    }

    @Override
    public void mouseReleased(MouseEvent e)
    {
    }

    @Override
    public void mouseExited(MouseEvent e)
    {
        JTable table = (JTable) e.getSource();
        if (hasLinkAction(table, row, col))
        {
            table.repaint(table.getCellRect(row, col, false));
            row = -1;
            col = -1;
            isRollover = false;
        }
        table.setCursor(DEFAULT_CURSOR);
    }

    @Override
    public void mouseClicked(MouseEvent e)
    {
    }

    private boolean hasLinkAction(JTable table, int row, int col)
    {
        if (row < 0
                || col < 0)
        {
            return false;
        }

        Object value = table.getValueAt(row, col);
        for (int i = 0; i < actionsSize; i++)
        {
            ITableContextMenuAction action = actions.get(i);
            if (action.showLink(value))
            {
                return true;
            }
        }
        return false;
    }

    /** Get HEX represenatation of a byte arrya */
    private static String bytesToHex(byte[] bytes)
    {
        // CSOFF
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++)
        {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        // CSON
        return "0x" + new String(hexChars);
    }
}
