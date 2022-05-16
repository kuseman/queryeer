package com.queryeer.output.table;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.queryeer.output.table.TableOutputWriter.RowList;

/** Columns merger for {@link RowList}'s */
class ColumnsMerger
{
    /** Merges the provided row with model */
    static void merge(Model model, RowList row)
    {
        // First row => just add columns and row
        if (model.getRowCount() == 0)
        {
            model.setColumns(row.columns);
            row.columns = null;
            model.addRow(row);
            return;
        }

        if (!row.matchesModelColumns)
        {
            int i = 1;
            for (i = 1; i < model.getColumns()
                    .size(); i++)
            {
                String modelColumn = model.getColumns()
                        .get(i);
                String listColumn = i < row.columns.size() ? row.columns.get(i)
                        : null;

                if (listColumn == null)
                {
                    break;
                }
                else if (StringUtils.equalsIgnoreCase(listColumn, modelColumn))
                {
                    continue;
                }

                int steps = findColumn(row.columns, modelColumn, i + 1);
                if (steps >= 0)
                {
                    /* @formatter:off
                     *
                     * Model: col1, col2
                     * Row:   col1, newCol, col2
                     * ==>
                     *
                     * Model: col1, col2, col3, col4
                     * Row:   col3, col4, col5
                     *
                     * @formatter:on
                     */

                    Object value = row.remove(i + steps + 1);
                    row.add(i, value);
                    String column = row.columns.remove(i + steps + 1);
                    row.columns.add(i, column);
                    // }
                    continue;
                }

                /* @formatter:off
                *
                * Find model column in row and move it's value from the current index
                *
                * Model: col1, col2
                * Row:   col1, newCol, col2
                * ==>
                *
                * @formatter:on
                */

                row.add(i, null);
                row.columns.add(i, modelColumn);
            }

            // Add the rest of the columns if any
            if (i < row.columns.size())
            {
                List<String> modelColumns = new ArrayList<>(model.getColumns());
                modelColumns.addAll(row.columns.subList(i, row.columns.size()));
                model.setColumns(modelColumns);
            }
        }

        row.columns = null;
        // row.trimToSize();
        model.addRow(row);
    }

    private static int findColumn(List<String> list, String column, int startIndex)
    {
        int size = list.size();
        int steps = 0;
        for (int i = startIndex; i < size; i++)
        {
            if (column.equalsIgnoreCase(list.get(i)))
            {
                return steps;
            }
            steps++;
        }
        return -1;
    }
}
