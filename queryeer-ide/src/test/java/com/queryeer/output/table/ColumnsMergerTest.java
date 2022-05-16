package com.queryeer.output.table;

import static java.util.Arrays.asList;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import com.queryeer.output.table.TableOutputWriter.RowList;

/** Unit test of {@link ColumnsMerger} */
public class ColumnsMergerTest extends Assert
{
    @Test
    public void test_merge_of_empty_column_names()
    {
        Model model = new Model();
        ColumnsMerger.merge(model, row(model, cols("", ""), 1, 2));
        ColumnsMerger.merge(model, row(model, cols("", "", ""), 10, 20, 30));
        assertEquals(asList("", "", "", ""), model.getColumns());
        //@formatter:off
        assertEquals(asList(
                asList(1, 1, 2, null),
                asList(2, 10, 20, 30)
                ), getActual(model));
        //@formatter:on
    }

    @Test
    public void test_joined_result_set_regression()
    {
        Model model = new Model();

        // CSOFF
        //@formatter:off
        ColumnsMerger.merge(model, row(model, cols("_categoryLinkFriendlyNames", "_sFreshness", "_aSizeReferences", "_fBaseColors", "_aProductCategories", "_aPrice", "_aDiscount", "_aSubBrand", "_aBrand", "_aFeatures", "_aMaterials", "_aWidth", "body", "_positiveRankings", "_lastSyncDate"), 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15));
        ColumnsMerger.merge(model, row(model, cols("_categoryLinkFriendlyNames", "_sFreshness",                     "_fBaseColors", "_aProductCategories", "_aPrice", "_aDiscount", "_aSubBrand", "_aBrand", "_aFeatures", "_aMaterials", "_aShapes", "_aWidth", "_aHeight", "_aDepth", "body", "_positiveRankings", "_lastSyncDate"), 10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170));
        assertEquals(                   asList("", "_categoryLinkFriendlyNames", "_sFreshness", "_aSizeReferences", "_fBaseColors", "_aProductCategories", "_aPrice", "_aDiscount", "_aSubBrand", "_aBrand", "_aFeatures", "_aMaterials", "_aWidth", "body", "_positiveRankings", "_lastSyncDate", "_aShapes", "_aHeight", "_aDepth"), model.getColumns());
        assertEquals(asList(
                asList(1, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, null, null, null),
                asList(2, 10, 20, null, 30, 40, 50, 60, 70, 80, 90, 100, 120, 150, 160, 170, 110, 130, 140)
                ), getActual(model));
        //@formatter:on
        // CSON
    }

    @Test
    public void test_joined_result_set_regression_1()
    {
        Model model = new Model();
        // CSOFF
        //@formatter:off
        ColumnsMerger.merge(model, row(model, cols("_aMaterials", "_aWidth", "body", "_positiveRankings", "_lastSyncDate", "labels", "_aCarpetTypes", "_aLength", "_aShapes", "_aHeight", "_aDepth", "_aOnOffSwitches", "_aFunctions", "_aNumberOfSeats", "_aBedTypes"), 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14));
        ColumnsMerger.merge(model, row(model, cols("_aMaterials", "_aShapes", "_aWidth", "_aHeight", "_aDepth", "body", "_positiveRankings", "_lastSyncDate"), 10, 20, 30, 40, 50, 60, 70, 80));
        assertEquals(                   asList("", "_aMaterials", "_aWidth", "body", "_positiveRankings", "_lastSyncDate", "labels", "_aCarpetTypes", "_aLength", "_aShapes", "_aHeight", "_aDepth", "_aOnOffSwitches", "_aFunctions", "_aNumberOfSeats", "_aBedTypes"), model.getColumns());
        assertEquals(asList(
                asList(1,  0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14),
                asList(2, 10, 30, 60, 70, 80, null, null, null, 20, 40, 50, null, null, null, null)
                ), getActual(model));
        //@formatter:on
        // CSON
    }

    @Test
    public void test_joined_result_set()
    {
        Model model = new Model();
        ColumnsMerger.merge(model, row(model, cols("col1", "col2", "col1", "col2"), 10, 20, 30, 40));
        assertEquals(asList("", "col1", "col2", "col1", "col2"), model.getColumns());
        assertEquals(asList(asList(1, 10, 20, 30, 40)), getActual(model));

        ColumnsMerger.merge(model, row(model, cols("col1", "col2", "col3", "col1", "col2"), 50, 60, "hello", 70, 80));
        assertEquals(asList("", "col1", "col2", "col1", "col2", "col3"), model.getColumns());
        //@formatter:off
        assertEquals(asList(
                asList(1, 10, 20, 30, 40, null),
                asList(2, 50, 60, 70, 80, "hello")
                ), getActual(model));
        //@formatter:on

        ColumnsMerger.merge(model, row(model, cols("col1", "col2", "col3", "col1", "col2", "col3"), 50, 60, "hello", 70, 80, "world"));
        assertEquals(asList("", "col1", "col2", "col1", "col2", "col3", "col3"), model.getColumns());
        //@formatter:off
        assertEquals(asList(
                asList(1, 10, 20, 30, 40, null,    null),
                asList(2, 50, 60, 70, 80, "hello", null),
                asList(3, 50, 60, 70, 80, "hello", "world")
                ), getActual(model));
        //@formatter:on

        ColumnsMerger.merge(model, row(model, cols("col1", "col2", "col2_1", "col_2_2", "col3"), 90, 100, true, false, 110));
        assertEquals(asList("", "col1", "col2", "col1", "col2", "col3", "col3", "col2_1", "col_2_2"), model.getColumns());
        //@formatter:off
        assertEquals(asList(
                asList(1, 10, 20,  30,   40,     null,   null,    null, null),
                asList(2, 50, 60,  70,   80,    "hello", null,    null, null),
                asList(3, 50, 60,  70,   80,    "hello", "world", null, null),
                asList(4, 90, 100, null, null,  110,     null,    true, false)
                ), getActual(model));
        //@formatter:on
    }

    @Test
    public void test()
    {
        Model model = new Model();

        ColumnsMerger.merge(model, row(model, cols("col1", "col2"), 10, 20));
        assertEquals(asList("", "col1", "col2"), model.getColumns());
        assertEquals(asList(asList(1, 10, 20)), getActual(model));

        List<String> expected = model.getColumns();
        ColumnsMerger.merge(model, row(model, cols("col1", "col2"), 30, 40));
        assertSame(expected, model.getColumns());
        assertEquals(asList(asList(1, 10, 20), asList(2, 30, 40)), getActual(model));

        ColumnsMerger.merge(model, row(model, cols("col1", "col2", "col3"), 50, 60, 70));
        assertEquals(asList("", "col1", "col2", "col3"), model.getColumns());
        assertEquals(asList(asList(1, 10, 20, null), asList(2, 30, 40, null), asList(3, 50, 60, 70)), getActual(model));

        // Insert new col in the middle
        ColumnsMerger.merge(model, row(model, cols("col1", "col1_1", "col2", "col3"), 80, "hello", 90, 100));
        assertEquals(asList("", "col1", "col2", "col3", "col1_1"), model.getColumns());
        //@formatter:off
        assertEquals(asList(
                asList(1, 10, 20, null, null),
                asList(2, 30, 40, null, null),
                asList(3, 50, 60, 70,   null),
                asList(4, 80, 90, 100, "hello")), getActual(model));
        //@formatter:on

        // Insert 2 new columns each in the middle
        ColumnsMerger.merge(model, row(model, cols("col1", "col1_1_1", "col1_1", "col2", "col2_1", "col3"), 110, true, "hello", 120, 10.10f, 130));
        assertEquals(asList("", "col1", "col2", "col3", "col1_1", "col1_1_1", "col2_1"), model.getColumns());
        //@formatter:off
        assertEquals(asList(
                asList(1, 10,  20,  null, null,    null, null),
                asList(2, 30,  40,  null, null,    null, null),
                asList(3, 50,  60,  70,   null,    null, null),
                asList(4, 80,  90,  100,  "hello", null, null),
                asList(5, 110, 120, 130,  "hello", true, 10.10f)), getActual(model));
        //@formatter:on

        // Insert new row with only one existing column in the middle
        ColumnsMerger.merge(model, row(model, cols("col1_1"), 140));
        assertEquals(asList("", "col1", "col2", "col3", "col1_1", "col1_1_1", "col2_1"), model.getColumns());
        //@formatter:off
        assertEquals(asList(
                asList(1, 10,   20,   null, null,    null, null),
                asList(2, 30,   40,   null, null,    null, null),
                asList(3, 50,   60,   70,   null,    null, null),
                asList(4, 80,   90,   100,  "hello", null, null),
                asList(5, 110,  120,  130,  "hello", true, 10.10f),
                asList(6, null, null, null, 140,     null, null)), getActual(model));
        //@formatter:on

        // Insert new row with one new column
        ColumnsMerger.merge(model, row(model, cols("col4"), 150));
        assertEquals(asList("", "col1", "col2", "col3", "col1_1", "col1_1_1", "col2_1", "col4"), model.getColumns());
        //@formatter:off
        assertEquals(asList(
                asList(1, 10,   20,   null, null,    null, null,   null),
                asList(2, 30,   40,   null, null,    null, null,   null),
                asList(3, 50,   60,   70,   null,    null, null,   null),
                asList(4, 80,   90,   100,  "hello", null, null,   null),
                asList(5, 110,  120,  130,  "hello", true, 10.10f, null),
                asList(6, null, null, null, 140,     null, null,   null),
                asList(7, null, null, null, null,    null, null,   150)
                ), getActual(model));
        //@formatter:on

    }

    private RowList row(Model model, String[] columns, Object... values)
    {
        RowList row = new RowList(model.getRowCount() + 1, values.length);
        for (int i = 0; i < columns.length; i++)
        {
            if (i >= 1
                    && i < model.getColumns()
                            .size()
                    && !StringUtils.equalsIgnoreCase(columns[i - i], model.getColumns()
                            .get(i)))
            {
                row.matchesModelColumns = false;
            }
            row.add(columns[i], values[i]);
        }
        if (model.getColumns()
                .size() != columns.length + 1)
        {
            row.matchesModelColumns = false;
        }
        return row;
    }

    private String[] cols(String... columns)
    {
        return asList(columns).toArray(ArrayUtils.EMPTY_STRING_ARRAY);
    }

    private List<List<Object>> getActual(Model model)
    {
        List<List<Object>> result = new ArrayList<>();
        for (int j = 0; j < model.getRowCount(); j++)
        {
            List<Object> rowResult = new ArrayList<>();
            result.add(rowResult);
            for (int i = 0; i < model.getColumnCount(); i++)
            {
                rowResult.add(model.getValueAt(j, i));
            }
        }
        return result;
    }
}
