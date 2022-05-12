package com.queryeer.output.table;

import java.awt.Color;
import java.awt.Component;
import java.lang.reflect.Array;

import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;

/** Cell renderer for table output */
class CellRenderer extends DefaultTableCellRenderer
{
    private static final Color TABLE_NULL_BACKGROUND = new Color(255, 253, 237);
    private static final Color TABLE_REGULAR_BACKGROUND = UIManager.getColor("Table.dropCellBackground");

    @Override
    public Component getTableCellRendererComponent(JTable table, Object val, boolean isSelected, boolean hasFocus, int row, int column)
    {
        Object value = val;

        if (value != null
                && value.getClass()
                        .isArray())
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

        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

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
}
