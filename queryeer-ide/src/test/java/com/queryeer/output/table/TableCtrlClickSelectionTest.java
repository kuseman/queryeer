package com.queryeer.output.table;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.queryeer.api.extensions.output.table.ITableOutputComponent.SelectedCell;

/** Tests for CTRL+click individual cell selection in {@link Table} */
class TableCtrlClickSelectionTest
{
    private Table table;

    @BeforeEach
    void setup() throws Exception
    {
        AtomicReference<Table> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() ->
        {
            Table t = new Table(Collections.emptyList());
            // 5 rows x 5 columns — column 0 acts as the row-number column
            t.setModel(new DefaultTableModel(5, 5));
            ref.set(t);
        });
        table = ref.get();
    }

    // --- helpers to simulate the three relevant click flavours ---

    /** CTRL+click: toggle=true, extend=false */
    private void ctrlClick(int row, int col)
    {
        table.changeSelection(row, col, true, false);
    }

    /** Plain click: toggle=false, extend=false */
    private void plainClick(int row, int col)
    {
        table.changeSelection(row, col, false, false);
    }

    /** Drag / shift-extend: toggle=false, extend=true */
    private void drag(int row, int col)
    {
        table.changeSelection(row, col, false, true);
    }

    /** CTRL+SHIFT+click: toggle=true, extend=true */
    private void ctrlShiftClick(int row, int col)
    {
        table.changeSelection(row, col, true, true);
    }

    // --- tests ---

    @Test
    void ctrl_click_single_cell_selects_it() throws Exception
    {
        runOnEdt(() ->
        {
            ctrlClick(1, 1);

            assertTrue(table.isCellSelected(1, 1));
            assertFalse(table.isCellSelected(0, 0));
            assertFalse(table.isCellSelected(2, 2));
        });
    }

    @Test
    void ctrl_click_two_cells_both_remain_selected() throws Exception
    {
        runOnEdt(() ->
        {
            ctrlClick(1, 1);
            ctrlClick(2, 2);

            assertTrue(table.isCellSelected(1, 1), "First CTRL-clicked cell must still be selected after second click");
            assertTrue(table.isCellSelected(2, 2), "Second CTRL-clicked cell must be selected");

            // Cross-product cells that JTable would normally select must NOT appear selected
            assertFalse(table.isCellSelected(1, 2), "Cross-product cell (1,2) must not be selected");
            assertFalse(table.isCellSelected(2, 1), "Cross-product cell (2,1) must not be selected");
        });
    }

    @Test
    void ctrl_click_three_cells_all_remain_selected() throws Exception
    {
        runOnEdt(() ->
        {
            ctrlClick(0, 1);
            ctrlClick(2, 3);
            ctrlClick(4, 2);

            assertTrue(table.isCellSelected(0, 1));
            assertTrue(table.isCellSelected(2, 3));
            assertTrue(table.isCellSelected(4, 2));

            assertFalse(table.isCellSelected(0, 3));
            assertFalse(table.isCellSelected(2, 1));
            assertFalse(table.isCellSelected(4, 3));
        });
    }

    @Test
    void ctrl_click_selected_cell_deselects_it_others_remain() throws Exception
    {
        runOnEdt(() ->
        {
            ctrlClick(1, 1);
            ctrlClick(2, 2);
            ctrlClick(3, 3);

            // Toggle off the middle cell
            ctrlClick(2, 2);

            assertFalse(table.isCellSelected(2, 2), "Toggled-off cell must be deselected");
            assertTrue(table.isCellSelected(1, 1), "First cell must remain selected");
            assertTrue(table.isCellSelected(3, 3), "Third cell must remain selected");
        });
    }

    @Test
    void ctrl_click_after_plain_click_preserves_plain_clicked_cell() throws Exception
    {
        runOnEdt(() ->
        {
            plainClick(1, 1);
            ctrlClick(2, 2);

            assertTrue(table.isCellSelected(1, 1), "Plain-clicked cell must stay visible after first CTRL+click adds another cell");
            assertTrue(table.isCellSelected(2, 2), "CTRL-clicked cell must be selected");
            assertFalse(table.isCellSelected(1, 2), "Cross-product cell must not be selected");
            assertFalse(table.isCellSelected(2, 1), "Cross-product cell must not be selected");
        });
    }

    @Test
    void plain_click_clears_all_ctrl_selections() throws Exception
    {
        runOnEdt(() ->
        {
            ctrlClick(1, 1);
            ctrlClick(2, 2);

            plainClick(3, 3);

            assertFalse(table.isCellSelected(1, 1), "CTRL selection must be cleared on plain click");
            assertFalse(table.isCellSelected(2, 2), "CTRL selection must be cleared on plain click");
            assertTrue(table.isCellSelected(3, 3), "Plain-clicked cell must be selected");
        });
    }

    @Test
    void drag_event_while_in_ctrl_mode_does_not_clear_selection() throws Exception
    {
        runOnEdt(() ->
        {
            ctrlClick(1, 1);
            ctrlClick(2, 2);

            // A tiny mouse movement during a CTRL+click fires mouseDragged (extend=true).
            // This must NOT wipe out the individually selected cells.
            drag(2, 2);

            assertTrue(table.isCellSelected(1, 1), "First cell must survive drag jitter");
            assertTrue(table.isCellSelected(2, 2), "Second cell must survive drag jitter");
        });
    }

    @Test
    void plain_click_after_drag_jitter_still_clears_ctrl_selection() throws Exception
    {
        runOnEdt(() ->
        {
            ctrlClick(1, 1);
            ctrlClick(2, 2);
            drag(2, 2);

            plainClick(3, 3);

            assertFalse(table.isCellSelected(1, 1));
            assertFalse(table.isCellSelected(2, 2));
            assertTrue(table.isCellSelected(3, 3));
        });
    }

    @Test
    void get_selected_rows_returns_sorted_distinct_ctrl_selected_rows() throws Exception
    {
        runOnEdt(() ->
        {
            ctrlClick(3, 1);
            ctrlClick(1, 2);
            ctrlClick(2, 4);

            assertArrayEquals(new int[] { 1, 2, 3 }, table.getSelectedRows());
        });
    }

    @Test
    void get_selected_columns_returns_sorted_distinct_ctrl_selected_columns() throws Exception
    {
        runOnEdt(() ->
        {
            ctrlClick(1, 3);
            ctrlClick(3, 1);
            ctrlClick(2, 2);

            assertArrayEquals(new int[] { 1, 2, 3 }, table.getSelectedColumns());
        });
    }

    @Test
    void ctrl_click_col_zero_marks_entire_row_as_selected() throws Exception
    {
        runOnEdt(() ->
        {
            ctrlClick(2, 0);

            for (int col = 0; col < table.getColumnCount(); col++)
            {
                assertTrue(table.isCellSelected(2, col), "All columns in row 2 should appear selected when col 0 is CTRL-clicked");
            }
            assertFalse(table.isCellSelected(1, 1), "Other rows must not be affected");
            assertFalse(table.isCellSelected(3, 1), "Other rows must not be affected");
        });
    }

    @Test
    void clear_selection_resets_ctrl_selection() throws Exception
    {
        runOnEdt(() ->
        {
            ctrlClick(1, 1);
            ctrlClick(2, 2);

            table.clearSelection();

            assertFalse(table.isCellSelected(1, 1));
            assertFalse(table.isCellSelected(2, 2));
        });
    }

    @Test
    void set_row_selection_interval_resets_ctrl_selection() throws Exception
    {
        runOnEdt(() ->
        {
            ctrlClick(1, 1);
            ctrlClick(2, 2);

            table.setRowSelectionInterval(3, 3);

            assertFalse(table.isCellSelected(1, 1));
            assertFalse(table.isCellSelected(2, 2));
        });
    }

    @Test
    void ctrl_shift_click_extends_range_from_anchor_and_keeps_prior_ctrl_cells() throws Exception
    {
        runOnEdt(() ->
        {
            // CTRL+click two individual cells, then CTRL+SHIFT+click to extend from the last one
            ctrlClick(1, 1);
            ctrlClick(3, 2); // anchor is now (3,2)
            ctrlShiftClick(4, 3); // extend rect (3..4) x (2..3)

            // Original CTRL+clicked cells must stay
            assertTrue(table.isCellSelected(1, 1), "First CTRL+clicked cell must stay selected");

            // The rectangle from anchor (3,2) to (4,3) must be selected
            assertTrue(table.isCellSelected(3, 2));
            assertTrue(table.isCellSelected(3, 3));
            assertTrue(table.isCellSelected(4, 2));
            assertTrue(table.isCellSelected(4, 3));

            // Cells outside the rectangle must not be selected
            assertFalse(table.isCellSelected(2, 2), "Row above range must not be selected");
            assertFalse(table.isCellSelected(1, 2), "Cross-product cell must not be selected");
        });
    }

    @Test
    void ctrl_shift_click_extends_downward_range_of_rows_via_col_zero() throws Exception
    {
        runOnEdt(() ->
        {
            // CTRL+click row 1 (col 0 = entire row), then CTRL+SHIFT to row 3
            ctrlClick(1, 0); // anchor (1,0)
            ctrlShiftClick(3, 0); // extend rows 1..3

            for (int col = 0; col < table.getColumnCount(); col++)
            {
                assertTrue(table.isCellSelected(1, col), "Row 1 must be fully selected");
                assertTrue(table.isCellSelected(2, col), "Row 2 must be fully selected");
                assertTrue(table.isCellSelected(3, col), "Row 3 must be fully selected");
            }
            assertFalse(table.isCellSelected(4, 1), "Row outside range must not be selected");
        });
    }

    @Test
    void ctrl_shift_click_when_no_prior_ctrl_selection_selects_clicked_cell() throws Exception
    {
        runOnEdt(() ->
        {
            // No CTRL+click yet; CTRL+SHIFT should still select the clicked cell
            ctrlShiftClick(2, 2);

            assertTrue(table.isCellSelected(2, 2));
            assertFalse(table.isCellSelected(1, 1));
        });
    }

    // --- copy tests ---

    @Test
    void copy_sparse_ctrl_selection_only_copies_selected_cells() throws Exception
    {
        AtomicReference<String> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() ->
        {
            DefaultTableModel model = new DefaultTableModel(5, 5);
            for (int r = 0; r < 5; r++)
            {
                for (int c = 0; c < 5; c++)
                {
                    model.setValueAt("R" + r + "C" + c, r, c);
                }
            }
            Table t = new Table(Collections.emptyList());
            t.setModel(model);

            t.changeSelection(1, 1, true, false); // CTRL+click (1,1)
            t.changeSelection(3, 3, true, false); // CTRL+click (3,3) — sparse, not contiguous

            Transferable transferable = TableTransferHandler.generate(t, false);
            try
            {
                DataFlavor plain = new DataFlavor("text/plain;class=java.lang.String", "Plain Text");
                result.set((String) transferable.getTransferData(plain));
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        });

        String text = result.get();
        // Selected cells must appear in output
        assertTrue(text.contains("R1C1"), "Selected cell (1,1) must be in clipboard");
        assertTrue(text.contains("R3C3"), "Selected cell (3,3) must be in clipboard");
        // Cross-product cells (1,3) and (3,1) must NOT appear
        assertFalse(text.contains("R1C3"), "Cross-product cell (1,3) must not be copied");
        assertFalse(text.contains("R3C1"), "Cross-product cell (3,1) must not be copied");
    }

    @Test
    void copy_contiguous_selection_copies_all_cells_in_range() throws Exception
    {
        AtomicReference<String> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() ->
        {
            DefaultTableModel model = new DefaultTableModel(5, 5);
            for (int r = 0; r < 5; r++)
            {
                for (int c = 0; c < 5; c++)
                {
                    model.setValueAt("R" + r + "C" + c, r, c);
                }
            }
            Table t = new Table(Collections.emptyList());
            t.setModel(model);

            // Plain click + shift-extend selects a contiguous 2x2 block
            t.changeSelection(1, 1, false, false);
            t.changeSelection(2, 2, false, true);

            Transferable transferable = TableTransferHandler.generate(t, false);
            try
            {
                DataFlavor plain = new DataFlavor("text/plain;class=java.lang.String", "Plain Text");
                result.set((String) transferable.getTransferData(plain));
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        });

        String text = result.get();
        assertTrue(text.contains("R1C1"));
        assertTrue(text.contains("R1C2"));
        assertTrue(text.contains("R2C1"));
        assertTrue(text.contains("R2C2"));
    }

    // --- getSelectedCells tests ---

    /** Build a 3-row x 3-column table with named columns (Col0..Col2) and values "R{row}C{col}" */
    private Table buildNamedTable() throws Exception
    {
        AtomicReference<Table> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() ->
        {
            String[] cols = { "Col0", "Col1", "Col2" };
            Object[][] data = new Object[3][3];
            for (int r = 0; r < 3; r++)
            {
                for (int c = 0; c < 3; c++)
                {
                    data[r][c] = "R" + r + "C" + c;
                }
            }
            Table t = new Table(Collections.emptyList());
            t.setModel(new DefaultTableModel(data, cols));
            ref.set(t);
        });
        return ref.get();
    }

    @Test
    void getSelectedCells_returns_empty_when_nothing_selected() throws Exception
    {
        runOnEdt(() ->
        {
            Table t = new Table(Collections.emptyList());
            t.setModel(new DefaultTableModel(3, 3));

            List<SelectedCell> cells = TableOutputComponent.computeSelectedCells(t);

            assertTrue(cells.isEmpty());
        });
    }

    @Test
    void getSelectedCells_single_ctrl_click_returns_one_cell() throws Exception
    {
        Table t = buildNamedTable();
        runOnEdt(() ->
        {
            t.changeSelection(1, 2, true, false);

            List<SelectedCell> cells = TableOutputComponent.computeSelectedCells(t);

            assertEquals(1, cells.size());
            assertEquals(1, cells.get(0)
                    .getRowIndex());
            assertEquals(2, cells.get(0)
                    .getColumnIndex());
            assertEquals("R1C2", cells.get(0)
                    .getCellValue());
            assertEquals("Col2", cells.get(0)
                    .getColumnHeader());
        });
    }

    @Test
    void getSelectedCells_sparse_ctrl_click_excludes_cross_product_cells() throws Exception
    {
        Table t = buildNamedTable();
        runOnEdt(() ->
        {
            // Use non-zero columns to avoid the "col 0 = full row" behaviour
            t.changeSelection(0, 1, true, false); // CTRL+click (0,1)
            t.changeSelection(2, 2, true, false); // CTRL+click (2,2)

            List<SelectedCell> cells = TableOutputComponent.computeSelectedCells(t);

            assertEquals(2, cells.size());
            assertTrue(cells.stream()
                    .anyMatch(c -> c.getRowIndex() == 0
                            && c.getColumnIndex() == 1),
                    "(0,1) must be present");
            assertTrue(cells.stream()
                    .anyMatch(c -> c.getRowIndex() == 2
                            && c.getColumnIndex() == 2),
                    "(2,2) must be present");
            assertFalse(cells.stream()
                    .anyMatch(c -> c.getRowIndex() == 0
                            && c.getColumnIndex() == 2),
                    "Cross-product (0,2) must not appear");
            assertFalse(cells.stream()
                    .anyMatch(c -> c.getRowIndex() == 2
                            && c.getColumnIndex() == 1),
                    "Cross-product (2,1) must not appear");
        });
    }

    @Test
    void getSelectedCells_contiguous_selection_returns_all_four_cells() throws Exception
    {
        Table t = buildNamedTable();
        runOnEdt(() ->
        {
            t.changeSelection(0, 0, false, false); // plain click anchor
            t.changeSelection(1, 1, false, true); // shift-extend → 2×2 block

            List<SelectedCell> cells = TableOutputComponent.computeSelectedCells(t);

            assertEquals(4, cells.size());
            assertTrue(cells.stream()
                    .anyMatch(c -> c.getRowIndex() == 0
                            && c.getColumnIndex() == 0));
            assertTrue(cells.stream()
                    .anyMatch(c -> c.getRowIndex() == 0
                            && c.getColumnIndex() == 1));
            assertTrue(cells.stream()
                    .anyMatch(c -> c.getRowIndex() == 1
                            && c.getColumnIndex() == 0));
            assertTrue(cells.stream()
                    .anyMatch(c -> c.getRowIndex() == 1
                            && c.getColumnIndex() == 1));
        });
    }

    @Test
    void getSelectedCells_column_count_matches_table_column_count() throws Exception
    {
        Table t = buildNamedTable();
        runOnEdt(() ->
        {
            t.changeSelection(1, 1, true, false);

            List<SelectedCell> cells = TableOutputComponent.computeSelectedCells(t);

            assertEquals(1, cells.size());
            assertEquals(t.getColumnCount(), cells.get(0)
                    .getColumnCount());
        });
    }

    @Test
    void getSelectedCells_row_value_returns_values_across_all_columns_in_same_row() throws Exception
    {
        Table t = buildNamedTable();
        runOnEdt(() ->
        {
            t.changeSelection(2, 1, true, false);

            List<SelectedCell> cells = TableOutputComponent.computeSelectedCells(t);

            assertEquals(1, cells.size());
            SelectedCell cell = cells.get(0);
            assertEquals("R2C0", cell.getRowValue(0));
            assertEquals("R2C1", cell.getRowValue(1));
            assertEquals("R2C2", cell.getRowValue(2));
        });
    }

    @Test
    void getSelectedCells_row_header_returns_column_name() throws Exception
    {
        Table t = buildNamedTable();
        runOnEdt(() ->
        {
            t.changeSelection(0, 0, true, false);

            List<SelectedCell> cells = TableOutputComponent.computeSelectedCells(t);

            assertEquals(1, cells.size());
            SelectedCell cell = cells.get(0);
            assertEquals("Col0", cell.getRowHeader(0));
            assertEquals("Col1", cell.getRowHeader(1));
            assertEquals("Col2", cell.getRowHeader(2));
        });
    }

    // --- EDT helper ---

    private void runOnEdt(ThrowingRunnable r) throws Exception
    {
        AtomicReference<Exception> caught = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                r.run();
            }
            catch (Exception e)
            {
                caught.set(e);
            }
        });
        if (caught.get() != null)
        {
            throw caught.get();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable
    {
        void run() throws Exception;
    }
}
