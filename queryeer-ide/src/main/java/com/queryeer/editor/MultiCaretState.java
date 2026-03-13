package com.queryeer.editor;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;

/**
 * Manages secondary caret state, undo/redo caret stacks, and visual highlight rendering for {@link MultiCaretSupport}.
 */
class MultiCaretState
{
    /** Secondary carets: each {@code int[2]} is {@code {dot, mark}}. {@code dot == mark} means no selection. */
    final List<int[]> secondaryCarets = new ArrayList<>();

    /** Highlight tags returned by {@code Highlighter.addHighlight}, kept for removal on next refresh. */
    private final List<Object> highlightTags = new ArrayList<>();

    /** Painter for secondary selections (translucent blue fill). */
    static final Highlighter.HighlightPainter SELECTION_PAINTER = new DefaultHighlighter.DefaultHighlightPainter(new Color(100, 100, 255, 80));

    /** Painter for secondary carets (2-px vertical blue line). */
    static final Highlighter.HighlightPainter CARET_PAINTER = new SecondaryCaretPainter(new Color(100, 100, 255, 200));

    /**
     * Caret-state stacks that parallel RSTA's document undo/redo stack for multi-caret edits. Each entry in the primary stacks is {@code {dot, mark}} for the primary caret. Each entry in the caret
     * stacks is the secondary caret list snapshot.
     */
    final Deque<int[]> primaryUndoStack = new ArrayDeque<>();
    final Deque<int[]> primaryRedoStack = new ArrayDeque<>();
    final Deque<List<int[]>> caretUndoStack = new ArrayDeque<>();
    final Deque<List<int[]>> caretRedoStack = new ArrayDeque<>();

    private final MultiCaretAwareEditorPane textArea;

    MultiCaretState(MultiCaretAwareEditorPane textArea)
    {
        this.textArea = textArea;
    }

    boolean hasSecondaryCarets()
    {
        return !secondaryCarets.isEmpty();
    }

    boolean hasSecondarySelections()
    {
        for (int[] caret : secondaryCarets)
        {
            if (caret[0] != caret[1])
            {
                return true;
            }
        }
        return false;
    }

    /** Snapshot current primary + secondary carets and push onto the undo stack; clear the redo stacks. */
    void saveCaretSnapshot()
    {
        primaryUndoStack.push(new int[] {
                textArea.getCaretPosition(), textArea.getCaret()
                        .getMark() });
        caretUndoStack.push(snapshotSecondaryCarets());
        primaryRedoStack.clear();
        caretRedoStack.clear();
    }

    List<int[]> snapshotSecondaryCarets()
    {
        List<int[]> snap = new ArrayList<>(secondaryCarets.size());
        for (int[] c : secondaryCarets)
        {
            snap.add(new int[] { c[0], c[1] });
        }
        return snap;
    }

    void restorePrimaryCaret(int[] primaryCaret)
    {
        int dot = primaryCaret[0];
        int mark = primaryCaret[1];
        if (dot == mark)
        {
            textArea.setCaretPosition(dot);
        }
        else
        {
            textArea.getCaret()
                    .setDot(mark);
            textArea.getCaret()
                    .moveDot(dot);
        }
    }

    void restoreSecondaryCarets(List<int[]> snapshot)
    {
        secondaryCarets.clear();
        secondaryCarets.addAll(snapshot);
        updateHighlights();
    }

    void clearSecondaryCarets()
    {
        if (secondaryCarets.isEmpty())
        {
            return;
        }
        secondaryCarets.clear();
        primaryUndoStack.clear();
        primaryRedoStack.clear();
        caretUndoStack.clear();
        caretRedoStack.clear();
        updateHighlights();
    }

    /**
     * Build a list of all carets (primary + secondary) sorted by ascending selection-start offset. Each entry is {@code int[3] = {dot, mark, isPrimary}} where isPrimary is 1 or 0.
     */
    List<int[]> buildAllCaretsSortedAsc()
    {
        List<int[]> all = new ArrayList<>(secondaryCarets.size() + 1);
        all.add(new int[] {
                textArea.getCaretPosition(), textArea.getCaret()
                        .getMark(),
                1 });
        for (int[] sc : secondaryCarets)
        {
            all.add(new int[] { sc[0], sc[1], 0 });
        }
        all.sort(Comparator.comparingInt((int[] c) -> Math.min(c[0], c[1])));
        return all;
    }

    /** Find the secondary caret entry with the given old dot/mark and update it in place. */
    void updateSecondaryPosition(int oldDot, int oldMark, int newDot, int newMark)
    {
        for (int[] caret : secondaryCarets)
        {
            if (caret[0] == oldDot
                    && caret[1] == oldMark)
            {
                caret[0] = newDot;
                caret[1] = newMark;
                return;
            }
        }
    }

    /** Returns the maximum end offset across the primary selection and all secondary selections. */
    int getLastSelectionEnd()
    {
        int max = textArea.getSelectionEnd();
        for (int[] caret : secondaryCarets)
        {
            max = Math.max(max, Math.max(caret[0], caret[1]));
        }
        return max;
    }

    void updateHighlights()
    {
        Highlighter h = textArea.getHighlighter();

        for (Object tag : highlightTags)
        {
            h.removeHighlight(tag);
        }
        highlightTags.clear();

        for (int[] caret : secondaryCarets)
        {
            int dot = caret[0];
            int mark = caret[1];
            try
            {
                if (dot == mark)
                {
                    // Caret only: paint a thin vertical line using a 1-char-wide range
                    if (dot < textArea.getDocument()
                            .getLength())
                    {
                        int end = Math.min(dot + 1, textArea.getDocument()
                                .getLength());
                        highlightTags.add(h.addHighlight(dot, end, CARET_PAINTER));
                    }
                }
                else
                {
                    // Selection
                    int selStart = Math.min(dot, mark);
                    int selEnd = Math.max(dot, mark);
                    highlightTags.add(h.addHighlight(selStart, selEnd, SELECTION_PAINTER));
                    // Also draw caret indicator at dot position
                    if (dot < textArea.getDocument()
                            .getLength())
                    {
                        int caretEnd = Math.min(dot + 1, textArea.getDocument()
                                .getLength());
                        highlightTags.add(h.addHighlight(dot, caretEnd, CARET_PAINTER));
                    }
                }
            }
            catch (BadLocationException e)
            {
                // Swallow
            }
        }
        textArea.repaint();
    }
}
