package com.queryeer.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MultiCaretSupport} and the secondary caret state it manages. Tests cover:
 * <ul>
 * <li>Alt+click add / remove secondary carets</li>
 * <li>Add caret above / below</li>
 * <li>Select-next-occurrence (Ctrl+D)</li>
 * <li>Shift of secondary caret positions after document edits</li>
 * <li>Snapshot / restore round-trip</li>
 * <li>Keyboard block selection at column 0 (regression for the column-zero paint bug)</li>
 * </ul>
 */
class MultiCaretSupportTest
{
    private MultiCaretAwareEditorPane textArea;
    private MultiCaretSupport support;

    @BeforeEach
    void setup() throws Exception
    {
        AtomicReference<MultiCaretAwareEditorPane> taRef = new AtomicReference<>();
        AtomicReference<MultiCaretSupport> supRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() ->
        {
            MultiCaretAwareEditorPane ta = new MultiCaretAwareEditorPane();
            MultiCaretSupport sup = new MultiCaretSupport(ta);
            taRef.set(ta);
            supRef.set(sup);
        });
        textArea = taRef.get();
        support = supRef.get();
    }

    // -----------------------------------------------------------------------
    // hasSecondaryCarets
    // -----------------------------------------------------------------------

    @Test
    void initially_no_secondary_carets() throws Exception
    {
        runOnEdt(() -> assertFalse(support.hasSecondaryCarets()));
    }

    // -----------------------------------------------------------------------
    // handleAltClick
    // -----------------------------------------------------------------------

    @Test
    void alt_click_at_different_position_adds_secondary_caret_at_old_primary() throws Exception
    {
        runOnEdt(() ->
        {
            textArea.setText("hello\nworld");
            textArea.setCaretPosition(6); // primary has moved to 'w' (RSTA would do this)
            support.handleAltClick(0, 0, 6); // old primary was at offset 0

            assertTrue(support.hasSecondaryCarets());
            List<int[]> all = support.buildAllCaretsSortedAsc();
            assertEquals(2, all.size());
            // Secondary at 0 sorts first, primary at 6 sorts second
            assertEquals(0, all.get(0)[0], "Secondary caret must be at old primary position 0");
            assertEquals(6, all.get(1)[0], "Primary caret must be at click position 6");
        });
    }

    @Test
    void alt_click_on_existing_secondary_removes_it() throws Exception
    {
        runOnEdt(() ->
        {
            textArea.setText("hello\nworld");
            support.restoreSecondaryCarets(List.of(new int[] { 3, 3, -1 }));
            assertTrue(support.hasSecondaryCarets());

            // Alt+click at position 3 where the zero-width secondary caret lives
            textArea.setCaretPosition(6);
            support.handleAltClick(6, 6, 3);

            assertFalse(support.hasSecondaryCarets(), "Secondary caret at click position must be removed");
        });
    }

    @Test
    void alt_click_at_same_position_as_primary_is_noop() throws Exception
    {
        runOnEdt(() ->
        {
            textArea.setText("hello");
            textArea.setCaretPosition(2);
            support.handleAltClick(2, 2, 2); // savedDot == clickPos — nothing to add

            assertFalse(support.hasSecondaryCarets());
        });
    }

    // -----------------------------------------------------------------------
    // addCaretBelow / addCaretAbove
    // -----------------------------------------------------------------------

    @Test
    void add_caret_below_places_secondary_on_next_line_same_column() throws Exception
    {
        runOnEdt(() ->
        {
            // "line0\nline1\nline2": line 0 starts at 0, line 1 at 6, line 2 at 12
            textArea.setText("line0\nline1\nline2");
            textArea.setCaretPosition(2); // col 2 on line 0

            support.addCaretBelow();

            assertTrue(support.hasSecondaryCarets());
            // Secondary caret: line 1, col 2 → offset 6+2 = 8
            List<int[]> secondaries = secondaryCarets();
            assertEquals(1, secondaries.size());
            assertEquals(8, secondaries.get(0)[0], "Secondary must be at col 2 of line 1 (offset 8)");
        });
    }

    @Test
    void add_caret_above_places_secondary_on_previous_line_same_column() throws Exception
    {
        runOnEdt(() ->
        {
            textArea.setText("line0\nline1\nline2");
            textArea.setCaretPosition(8); // col 2 on line 1 (offset 6+2=8)

            support.addCaretAbove();

            assertTrue(support.hasSecondaryCarets());
            // Secondary caret: line 0, col 2 → offset 2
            List<int[]> secondaries = secondaryCarets();
            assertEquals(1, secondaries.size());
            assertEquals(2, secondaries.get(0)[0], "Secondary must be at col 2 of line 0 (offset 2)");
        });
    }

    @Test
    void add_caret_below_clamps_to_last_line_length() throws Exception
    {
        runOnEdt(() ->
        {
            // "ab\nxy": line 0 is 3 chars (incl. newline), line 1 is 2 chars
            textArea.setText("ab\nxy");
            textArea.setCaretPosition(2); // col 2 on line 0 (at the newline position)

            support.addCaretBelow();

            // line 1 has lineLen=2, col 2 would be beyond it → clamped to lineLen=2
            // line 1 starts at offset 3, end offset is 5, lineLen=2 → max col 2 → offset 3+2=5
            assertTrue(support.hasSecondaryCarets());
            List<int[]> secondaries = secondaryCarets();
            assertEquals(1, secondaries.size());
            // min(col=2, lineLen=2) = 2, so offset = 3 + 2 = 5
            assertEquals(5, secondaries.get(0)[0], "Secondary must be clamped to end of short line");
        });
    }

    @Test
    void add_caret_below_does_nothing_on_last_line() throws Exception
    {
        runOnEdt(() ->
        {
            textArea.setText("only one line");
            textArea.setCaretPosition(0);

            support.addCaretBelow();

            assertFalse(support.hasSecondaryCarets(), "No caret can be added below the last line");
        });
    }

    @Test
    void add_caret_above_does_nothing_on_first_line() throws Exception
    {
        runOnEdt(() ->
        {
            textArea.setText("only one line");
            textArea.setCaretPosition(0);

            support.addCaretAbove();

            assertFalse(support.hasSecondaryCarets(), "No caret can be added above the first line");
        });
    }

    // -----------------------------------------------------------------------
    // selectNextOccurrence (Ctrl+D)
    // -----------------------------------------------------------------------

    @Test
    void select_next_occurrence_selects_word_and_immediately_adds_next_as_secondary() throws Exception
    {
        runOnEdt(() ->
        {
            // "foo bar foo": first "foo" at 0..3, second at 8..11
            textArea.setText("foo bar foo");
            textArea.setCaretPosition(1); // inside first "foo"

            support.selectNextOccurrence();

            // Primary selection becomes the first "foo"
            assertEquals(0, textArea.getSelectionStart(), "Selection must start at word begin");
            assertEquals(3, textArea.getSelectionEnd(), "Selection must end at word end");
            // The second "foo" is added as a secondary caret immediately in the same call
            assertTrue(support.hasSecondaryCarets(), "Second occurrence must be added as secondary caret");
            List<int[]> secondaries = secondaryCarets();
            assertEquals(1, secondaries.size());
            assertEquals(8, Math.min(secondaries.get(0)[0], secondaries.get(0)[1]), "Secondary selection must cover second 'foo' starting at offset 8");
            assertEquals(11, Math.max(secondaries.get(0)[0], secondaries.get(0)[1]), "Secondary selection must cover second 'foo' ending at offset 11");
        });
    }

    @Test
    void select_next_occurrence_second_call_wraps_and_adds_third_caret() throws Exception
    {
        runOnEdt(() ->
        {
            textArea.setText("foo bar foo");
            textArea.setCaretPosition(1);

            // First call: primary = first "foo" (0..3), secondary = second "foo" (8..11)
            support.selectNextOccurrence();
            // Second call: nothing beyond offset 11 → wraps back to start, adds another secondary
            support.selectNextOccurrence();

            List<int[]> all = support.buildAllCaretsSortedAsc();
            assertEquals(3, all.size(), "After two Ctrl+D: primary + 2 secondaries (wrap-around included)");
        });
    }

    @Test
    void select_next_occurrence_with_single_occurrence_wraps_and_adds_secondary() throws Exception
    {
        runOnEdt(() ->
        {
            textArea.setText("bar foo bar");
            textArea.setCaretPosition(5); // inside the only "foo" at 4..7

            // First call selects "foo" and wraps around to add a secondary at the same occurrence
            support.selectNextOccurrence();

            assertTrue(support.hasSecondaryCarets(), "Wrap-around must still add a secondary caret");
        });
    }

    // -----------------------------------------------------------------------
    // shiftSecondaryCarets
    // -----------------------------------------------------------------------

    @Test
    void shift_secondary_carets_moves_positions_after_insertion_point() throws Exception
    {
        runOnEdt(() ->
        {
            textArea.setText("hello world test");
            support.restoreSecondaryCarets(List.of(new int[] { 6, 6, -1 }, new int[] { 12, 12, -1 }));

            // Simulate two chars inserted at offset 3 (delta = +2)
            support.shiftSecondaryCarets(List.of(3), 2);

            List<int[]> all = support.buildAllCaretsSortedAsc();
            boolean has8 = all.stream()
                    .anyMatch(c -> c[0] == 8
                            && c[2] == 0);
            boolean has14 = all.stream()
                    .anyMatch(c -> c[0] == 14
                            && c[2] == 0);
            assertTrue(has8, "Caret originally at 6 must shift to 8 after insertion at 3");
            assertTrue(has14, "Caret originally at 12 must shift to 14 after insertion at 3");
        });
    }

    @Test
    void shift_secondary_carets_does_not_move_positions_before_insertion_point() throws Exception
    {
        runOnEdt(() ->
        {
            textArea.setText("hello world");
            support.restoreSecondaryCarets(List.of(new int[] { 2, 2, -1 }));

            support.shiftSecondaryCarets(List.of(5), 2); // insertion at 5, caret at 2 is before it

            List<int[]> all = support.buildAllCaretsSortedAsc();
            assertTrue(all.stream()
                    .anyMatch(c -> c[0] == 2
                            && c[2] == 0),
                    "Caret before insertion point must not shift");
        });
    }

    // -----------------------------------------------------------------------
    // buildAllCaretsSortedAsc
    // -----------------------------------------------------------------------

    @Test
    void build_all_carets_sorted_includes_primary_and_secondaries_in_order() throws Exception
    {
        runOnEdt(() ->
        {
            textArea.setText("abcdefghij");
            textArea.setCaretPosition(7);
            support.restoreSecondaryCarets(List.of(new int[] { 9, 9, -1 }, new int[] { 2, 2, -1 }));

            List<int[]> all = support.buildAllCaretsSortedAsc();

            assertEquals(3, all.size());
            assertEquals(2, all.get(0)[0], "First entry must be secondary at offset 2");
            assertEquals(7, all.get(1)[0], "Second entry must be primary at offset 7");
            assertEquals(9, all.get(2)[0], "Third entry must be secondary at offset 9");
        });
    }

    // -----------------------------------------------------------------------
    // snapshotSecondaryCarets / restoreSecondaryCarets
    // -----------------------------------------------------------------------

    @Test
    void snapshot_creates_independent_copy_unaffected_by_later_clear() throws Exception
    {
        runOnEdt(() ->
        {
            textArea.setText("hello world");
            support.restoreSecondaryCarets(List.of(new int[] { 3, 3, -1 }));

            List<int[]> snapshot = support.snapshotSecondaryCarets();
            support.clearSecondaryCarets();

            assertEquals(1, snapshot.size(), "Snapshot must survive the subsequent clear");
            assertEquals(3, snapshot.get(0)[0], "Snapshot entry must still hold the original offset");
        });
    }

    @Test
    void restore_replaces_secondary_carets_with_snapshot_contents() throws Exception
    {
        runOnEdt(() ->
        {
            textArea.setText("abcdefghij");
            support.restoreSecondaryCarets(List.of(new int[] { 5, 5, -1 }));
            List<int[]> snap = support.snapshotSecondaryCarets();

            support.clearSecondaryCarets();
            assertFalse(support.hasSecondaryCarets());

            support.restoreSecondaryCarets(snap);
            assertTrue(support.hasSecondaryCarets());
            assertEquals(5, secondaryCarets().get(0)[0]);
        });
    }

    // -----------------------------------------------------------------------
    // clearSecondaryCarets
    // -----------------------------------------------------------------------

    @Test
    void clear_removes_all_secondary_carets() throws Exception
    {
        runOnEdt(() ->
        {
            textArea.setText("hello world");
            support.restoreSecondaryCarets(List.of(new int[] { 2, 2, -1 }, new int[] { 5, 5, -1 }));

            assertTrue(support.hasSecondaryCarets());
            support.clearSecondaryCarets();
            assertFalse(support.hasSecondaryCarets());
        });
    }

    // -----------------------------------------------------------------------
    // Keyboard block selection — regression for column-zero caret paint bug
    // -----------------------------------------------------------------------

    /**
     * Regression test: when Shift+Alt+Down creates a vertical block selection at column 0, the secondary carets must be placed at the start of each line (offset = lineStartOffset + 0), not at the end
     * of the lines. The painting bug (now fixed in {@code paintCaretAt}) was caused by the end-of-line correction misidentifying column-0 positions as newline positions. This test verifies the
     * correct document offsets produced by the block-selection logic.
     */
    @Test
    void block_selection_at_column_zero_places_carets_at_line_starts() throws Exception
    {
        runOnEdt(() ->
        {
            // Line 0: "longline\n" (offsets 0-8), line 1: "ab\n" (offsets 9-11), line 2: "xyz" (12-14)
            textArea.setText("longline\nab\nxyz");
            textArea.setCaretPosition(0); // col 0, line 0

            // Shift+Alt+Down: start block selection and extend one line down
            fireKeyPressed(InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK, KeyEvent.VK_DOWN);

            assertTrue(support.hasSecondaryCarets(), "Block selection must create a secondary caret");

            // Secondary caret must be at line 1, col 0 = offset 9 (NOT at end of line 0 = offset 8)
            List<int[]> secondaries = secondaryCarets();
            assertEquals(1, secondaries.size());
            assertEquals(9, secondaries.get(0)[0], "Secondary caret must be at line 1 col 0 (offset 9), not at end of line 0");
            assertEquals(9, secondaries.get(0)[1], "Zero-width secondary caret: mark must equal dot");
        });
    }

    @Test
    void block_selection_extends_across_multiple_lines_with_correct_offsets() throws Exception
    {
        runOnEdt(() ->
        {
            // "aaa\nbbb\nccc": line 0 at 0, line 1 at 4, line 2 at 8
            textArea.setText("aaa\nbbb\nccc");
            textArea.setCaretPosition(1); // col 1, line 0

            // Press Shift+Alt+Down twice to cover lines 0, 1, 2
            fireKeyPressed(InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK, KeyEvent.VK_DOWN);
            fireKeyPressed(InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK, KeyEvent.VK_DOWN);

            // blockAnchorLine=0, blockAnchorCol=1, blockCurrentLine=2
            // Line 0 (anchor) → primary caret, lines 1 and 2 → secondary carets
            List<int[]> secondaries = secondaryCarets();
            assertEquals(2, secondaries.size(), "Two secondary carets for lines 1 and 2");

            // Line 1, col 1 → offset 4+1 = 5
            assertTrue(secondaries.stream()
                    .anyMatch(c -> c[0] == 5
                            && c[1] == 5),
                    "Secondary at line 1 col 1 (offset 5)");
            // Line 2, col 1 → offset 8+1 = 9
            assertTrue(secondaries.stream()
                    .anyMatch(c -> c[0] == 9
                            && c[1] == 9),
                    "Secondary at line 2 col 1 (offset 9)");
        });
    }

    @Test
    void block_selection_left_arrow_narrows_selection() throws Exception
    {
        runOnEdt(() ->
        {
            textArea.setText("abcd\nefgh\nijkl");
            textArea.setCaretPosition(2); // col 2, line 0

            fireKeyPressed(InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK, KeyEvent.VK_DOWN);
            fireKeyPressed(InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK, KeyEvent.VK_RIGHT); // col → 3
            fireKeyPressed(InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK, KeyEvent.VK_LEFT); // col → 2

            // Primary: anchor col 2, current col 2 → zero-width at line 0 col 2 = offset 2
            assertEquals(2, textArea.getCaretPosition(), "Primary caret must be at col 2 of line 0");
            // Secondary: line 1 col 2 = offset 5+2 = 7
            List<int[]> secondaries = secondaryCarets();
            assertEquals(1, secondaries.size());
            assertEquals(7, secondaries.get(0)[0], "Secondary at line 1 col 2 (offset 7)");
        });
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Directly invokes all registered key listeners with a KEY_PRESSED event. Using {@code dispatchEvent} is unreliable in headless Swing because the component may not have a peer, so events do not
     * always reach listeners. Calling the listeners directly is deterministic.
     */
    private void fireKeyPressed(int modifiers, int keyCode)
    {
        KeyEvent e = new KeyEvent(textArea, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), modifiers, keyCode, KeyEvent.CHAR_UNDEFINED);
        for (java.awt.event.KeyListener listener : textArea.getKeyListeners())
        {
            listener.keyPressed(e);
        }
    }

    /** Returns only the secondary carets (not the primary) from the live state. */
    private List<int[]> secondaryCarets()
    {
        return support.buildAllCaretsSortedAsc()
                .stream()
                .filter(c -> c[2] == 0) // isPrimary == 0 means secondary
                .toList();
    }

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
