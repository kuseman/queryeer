package com.queryeer.editor;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import javax.swing.text.BadLocationException;

/**
 * <pre>
 * Adds multi-caret/multi-selection support to a {@link org.fife.ui.rsyntaxtextarea.TextEditorPane}, similar to Monaco/VSCode.
 * Supports: Alt+Click (add/remove caret),
 *           Ctrl/Cmd+Alt+Down/Up (add caret below/above),
 *           Ctrl/Cmd+D (select next occurrence),
 *           Escape (clear carets),
 * typing/backspace/delete/enter/tab (apply at all positions), arrow keys and home/end +/-Shift (move/extend all carets).
 * All simultaneous edits use beginAtomicEdit / endAtomicEdit for single-step undo.
 * </pre>
 *
 * @see MultiCaretAwareEditorPane
 * @see MultiCaretState
 * @see MultiCaretEditHandler
 */
class MultiCaretSupport
{
    private final MultiCaretAwareEditorPane textArea;
    private final MultiCaretState state;
    private final MultiCaretEditHandler editHandler;
    private final KeyAdapter keyInterceptor;

    private static final int MENU_MASK = Toolkit.getDefaultToolkit()
            .getMenuShortcutKeyMaskEx();

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    MultiCaretSupport(MultiCaretAwareEditorPane textArea)
    {
        this.textArea = textArea;
        this.state = new MultiCaretState(textArea);
        this.editHandler = new MultiCaretEditHandler(textArea, state);
        textArea.setMultiCaretSupport(this);

        keyInterceptor = new KeyAdapter()
        {
            @Override
            public void keyPressed(KeyEvent e)
            {
                editHandler.handleKeyPressed(e);
            }

            @Override
            public void keyTyped(KeyEvent e)
            {
                if (!state.secondaryCarets.isEmpty())
                {
                    editHandler.handleKeyTyped(e);
                }
            }
        };
        textArea.addKeyListener(keyInterceptor);

        // Override undo/redo so secondary caret state is restored in sync with document undo/redo.
        // Document operation is performed FIRST, then secondary carets are adjusted — this avoids
        // any ordering ambiguity from intercepting via a key listener that fires before RSTA's handler.
        textArea.getInputMap()
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, MENU_MASK), "multicaret.undo");
        textArea.getActionMap()
                .put("multicaret.undo", new AbstractAction()
                {
                    @Override
                    public void actionPerformed(ActionEvent e)
                    {
                        // Capture post-operation state BEFORE the undo so it can be saved on
                        // the redo stacks (enabling redo to restore the exact positions the user
                        // was at when they originally performed the operation).
                        int[] prePrimary = new int[] {
                                textArea.getCaretPosition(), textArea.getCaret()
                                        .getMark() };
                        List<int[]> preSecondary = state.snapshotSecondaryCarets();

                        textArea.undoLastAction();

                        if (!state.primaryUndoStack.isEmpty())
                        {
                            int[] previousPrimary = state.primaryUndoStack.pop();
                            state.primaryRedoStack.push(prePrimary);
                            state.caretRedoStack.push(preSecondary);
                            state.restorePrimaryCaret(previousPrimary);
                            state.restoreSecondaryCarets(state.caretUndoStack.pop());
                        }
                        else
                        {
                            state.clearSecondaryCarets();
                        }
                    }
                });

        KeyStroke redoKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_Y, MENU_MASK);
        KeyStroke redoAltKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_Z, MENU_MASK | InputEvent.SHIFT_DOWN_MASK);
        textArea.getInputMap()
                .put(redoKeyStroke, "multicaret.redo");
        textArea.getInputMap()
                .put(redoAltKeyStroke, "multicaret.redo");
        textArea.getActionMap()
                .put("multicaret.redo", new AbstractAction()
                {
                    @Override
                    public void actionPerformed(ActionEvent e)
                    {
                        // Capture pre-operation state BEFORE the redo so it can be saved on
                        // the undo stacks (enabling undo to restore the exact positions the user
                        // was at before the operation).
                        int[] prePrimary = new int[] {
                                textArea.getCaretPosition(), textArea.getCaret()
                                        .getMark() };
                        List<int[]> preSecondary = state.snapshotSecondaryCarets();

                        int[] nextPrimary = state.primaryRedoStack.isEmpty() ? null
                                : state.primaryRedoStack.pop();
                        List<int[]> nextSecondary = state.caretRedoStack.isEmpty() ? null
                                : state.caretRedoStack.pop();

                        textArea.redoLastAction();

                        if (nextPrimary != null)
                        {
                            state.primaryUndoStack.push(prePrimary);
                            state.caretUndoStack.push(preSecondary);
                            state.restorePrimaryCaret(nextPrimary);
                            state.restoreSecondaryCarets(nextSecondary);
                        }
                        else
                        {
                            state.clearSecondaryCarets();
                        }
                    }
                });
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    boolean hasSecondaryCarets()
    {
        return state.hasSecondaryCarets();
    }

    void pasteToAllCarets()
    {
        editHandler.pasteToAllCarets();
    }

    void clearSecondaryCarets()
    {
        state.clearSecondaryCarets();
    }

    /** Add a caret one line below the lowest current caret. */
    void addCaretBelow()
    {
        addCaretVertical(1);
    }

    /** Add a caret one line above the topmost current caret. */
    void addCaretAbove()
    {
        addCaretVertical(-1);
    }

    /**
     * Select next occurrence of the current word/selection and add it as a secondary selection. Equivalent to Ctrl+D in VSCode/Monaco.
     */
    void selectNextOccurrence()
    {
        try
        {
            String toFind;
            int searchFrom;

            String primarySel = textArea.getSelectedText();
            if (state.secondaryCarets.isEmpty()
                    && (primarySel == null
                            || primarySel.isEmpty()))
            {
                // No selection: select the word at the primary caret first
                int pos = textArea.getCaretPosition();
                int[] bounds = getWordBoundsAt(pos);
                if (bounds == null)
                {
                    return;
                }
                textArea.setSelectionStart(bounds[0]);
                textArea.setSelectionEnd(bounds[1]);
                toFind = textArea.getSelectedText();
                searchFrom = bounds[1];
            }
            else
            {
                toFind = getSearchText();
                if (toFind == null
                        || toFind.isEmpty())
                {
                    return;
                }
                searchFrom = state.getLastSelectionEnd();
            }

            String docText = textArea.getDocument()
                    .getText(0, textArea.getDocument()
                            .getLength());
            int foundAt = docText.indexOf(toFind, searchFrom);
            if (foundAt < 0)
            {
                // Wrap around
                foundAt = docText.indexOf(toFind);
            }
            if (foundAt < 0)
            {
                return;
            }

            // Secondary caret: dot at end of match, mark at start (selection goes left-to-right)
            state.secondaryCarets.add(new int[] { foundAt + toFind.length(), foundAt, -1 });
            state.updateHighlights();
        }
        catch (BadLocationException e)
        {
            // Swallow
        }
    }

    void dispose()
    {
        textArea.removeKeyListener(keyInterceptor);
        state.clearSecondaryCarets();
    }

    /**
     * Paints secondary caret indicators directly onto the component graphics. Called from {@link MultiCaretAwareEditorPane#paintComponent} so carets render on top of text and highlights, bypassing
     * coordinate and clipping issues that arise when using the highlight layer for positions at end-of-line.
     */
    void paintCaretIndicators(Graphics g)
    {
        if (!state.hasSecondaryCarets())
        {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try
        {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(100, 100, 255, 200));
            for (int[] caret : state.secondaryCarets)
            {
                int virtualX = caret.length > 2 ? caret[2]
                        : -1;
                paintCaretAt(g2, caret[0], virtualX);
            }
        }
        finally
        {
            g2.dispose();
        }
    }

    private void paintCaretAt(Graphics2D g2, int dot, int virtualX)
    {
        try
        {
            Rectangle2D r = textArea.modelToView2D(dot);
            if (r == null)
            {
                return;
            }
            // When dot is at a newline character, modelToView2D may map to the start of the
            // next line (y jumps). Detect this and use the right edge of the preceding character.
            if (dot > 0)
            {
                Rectangle2D prev = textArea.modelToView2D(dot - 1);
                if (prev != null
                        && prev.getY() < r.getY())
                {
                    r = new Rectangle2D.Double(prev.getMaxX(), prev.getY(), 0, prev.getHeight());
                }
            }
            // When the caret was placed beyond the actual line end (block selection on short lines),
            // virtualX holds the intended pixel column so the caret appears at the correct visual
            // position rather than at the end of the text on that line.
            int x = (virtualX >= 0) ? virtualX
                    : (int) r.getX();
            int y = (int) r.getY();
            int h = (int) r.getHeight();
            if (h <= 0)
            {
                h = textArea.getFontMetrics(textArea.getFont())
                        .getHeight();
            }
            g2.fillRect(x, y, 2, h);
        }
        catch (BadLocationException e)
        {
            // Swallow
        }
    }

    // -----------------------------------------------------------------------
    // Alt+Click
    // -----------------------------------------------------------------------

    /**
     * Called from {@link MultiCaretAwareEditorPane#processMouseEvent} after RSTA has moved the primary caret to the click position.
     *
     * @param savedDot primary caret dot position BEFORE the click
     * @param savedMark primary caret mark position BEFORE the click
     * @param clickPos the new primary caret position (where user clicked)
     */
    void handleAltClick(int savedDot, int savedMark, int clickPos)
    {
        // If user Alt+Clicked on an existing secondary caret, remove it
        for (int i = 0; i < state.secondaryCarets.size(); i++)
        {
            int[] sc = state.secondaryCarets.get(i);
            if (sc[0] == clickPos
                    && sc[1] == clickPos)
            {
                state.secondaryCarets.remove(i);
                state.updateHighlights();
                return;
            }
        }

        // Otherwise add the old primary position as a new secondary caret
        // (if it differs from the click position — clicking on the same spot is a no-op)
        if (savedDot != clickPos
                || savedMark != clickPos)
        {
            state.secondaryCarets.add(new int[] { savedDot, savedMark, -1 });
        }
        state.updateHighlights();
    }

    // -----------------------------------------------------------------------
    // Alt+Shift+Click — vertical column of carets
    // -----------------------------------------------------------------------

    /**
     * Called from {@link MultiCaretAwareEditorPane#processMouseEvent} for Alt+Shift+Click and Alt+Shift+Drag. Replaces existing secondary carets with a new set covering every line from the current
     * primary caret line to {@code clickPos}'s line. Both the anchor column and the target column are derived from pixel X coordinates and mapped independently per line via {@link #getVirtualColumn},
     * so that lines with different content before the target position still have their carets placed at the correct visual column rather than at a fixed character offset that would land at different
     * visual positions on different lines.
     *
     * @param clickPos document offset nearest to the mouse (used only to determine the target line)
     * @param mouseX mouse X pixel coordinate (used to compute the virtual target column)
     */
    void handleAltShiftClick(int clickPos, int mouseX)
    {
        try
        {
            int primaryPos = textArea.getCaretPosition();
            int primaryLine = textArea.getLineOfOffset(primaryPos);

            // Convert the anchor caret position to a pixel X so we can independently map it to the
            // correct visual column on each line. A fixed character offset would land at different
            // visual X positions when lines differ in content before the target column.
            Rectangle2D primaryRect = textArea.modelToView2D(primaryPos);
            int primaryX = (primaryRect != null) ? (int) Math.round(primaryRect.getX())
                    : mouseX;

            int clickLine = textArea.getLineOfOffset(clickPos);

            int fromLine = Math.min(primaryLine, clickLine);
            int toLine = Math.max(primaryLine, clickLine);

            state.secondaryCarets.clear();

            for (int line = fromLine; line <= toLine; line++)
            {
                int lineStart = textArea.getLineStartOffset(line);
                int lineEnd = textArea.getLineEndOffset(line);
                boolean isLastLine = (line == textArea.getLineCount() - 1);
                int lineLen = isLastLine ? (lineEnd - lineStart)
                        : (lineEnd - lineStart - 1);

                // Compute the visual column for each line independently so that lines with
                // different content before the target column get their carets at the correct
                // visual position rather than a shifted character offset.
                int dotCol = getVirtualColumn(line, primaryX);
                int markCol = getVirtualColumn(line, mouseX);
                int dotPos = lineStart + Math.min(dotCol, lineLen);
                int markPos = lineStart + Math.min(markCol, lineLen);

                // When the dot column exceeds the line length the document offset is clamped to
                // end-of-line, so record the intended pixel X so the caret can be painted at the
                // correct visual position (in the virtual space beyond the line end).
                int dotVirtualX = (dotCol > lineLen) ? primaryX
                        : -1;

                if (line == primaryLine)
                {
                    // Extend the primary caret into a selection spanning the anchor to the click.
                    textArea.getCaret()
                            .setDot(markPos);
                    textArea.getCaret()
                            .moveDot(dotPos);
                }
                else
                {
                    state.secondaryCarets.add(new int[] { dotPos, markPos, dotVirtualX });
                }
            }

            state.updateHighlights();
        }
        catch (BadLocationException e)
        {
            // Swallow
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Returns the virtual column (zero-based character count from line start) corresponding to pixel x-coordinate {@code mouseX}. Positions within the line are found by scanning character boundaries
     * via {@link javax.swing.JTextComponent#modelToView2D}. If {@code mouseX} lies beyond the last character, the column is extrapolated using the font's space-character width so that dragging into
     * empty space still produces a correct target column rather than being capped at the line length.
     */
    private int getVirtualColumn(int line, int mouseX) throws BadLocationException
    {
        int lineStart = textArea.getLineStartOffset(line);
        int lineEnd = textArea.getLineEndOffset(line);
        boolean isLastLine = (line == textArea.getLineCount() - 1);
        int lineLen = isLastLine ? (lineEnd - lineStart)
                : (lineEnd - lineStart - 1);

        // Scan character positions within the line to find which column mouseX falls into.
        for (int col = 0; col < lineLen; col++)
        {
            Rectangle2D rect = textArea.modelToView2D(lineStart + col);
            if (rect != null
                    && rect.getX() + rect.getWidth() / 2.0 > mouseX)
            {
                return col;
            }
        }

        // mouseX is at or beyond the end of the line — extrapolate using the space character width.
        FontMetrics fm = textArea.getFontMetrics(textArea.getFont());
        int charWidth = (fm != null) ? fm.charWidth(' ')
                : 0;
        if (charWidth > 0)
        {
            Rectangle2D endRect = textArea.modelToView2D(lineStart + lineLen);
            if (endRect != null)
            {
                int extra = (int) Math.max(0, (mouseX - endRect.getX()) / charWidth);
                return lineLen + extra;
            }
        }
        return lineLen;
    }

    private void addCaretVertical(int direction)
    {
        try
        {
            // Reference: last secondary caret (or primary if none)
            int refPos = state.secondaryCarets.isEmpty() ? textArea.getCaretPosition()
                    : state.secondaryCarets.get(state.secondaryCarets.size() - 1)[0];

            int refLine = textArea.getLineOfOffset(refPos);
            int refCol = refPos - textArea.getLineStartOffset(refLine);

            int targetLine = refLine + direction;
            if (targetLine < 0
                    || targetLine >= textArea.getLineCount())
            {
                return;
            }

            int targetLineStart = textArea.getLineStartOffset(targetLine);
            int targetLineEnd = textArea.getLineEndOffset(targetLine);
            // Exclude newline character on non-last lines
            int maxCol = (targetLine < textArea.getLineCount() - 1) ? (targetLineEnd - targetLineStart - 1)
                    : (targetLineEnd - targetLineStart);

            int newPos = targetLineStart + Math.min(refCol, maxCol);
            state.secondaryCarets.add(new int[] { newPos, newPos, -1 });
            state.updateHighlights();
        }
        catch (BadLocationException e)
        {
            // Swallow
        }
    }

    /** Returns word bounds {@code [start, end]} around {@code offset}, or {@code null} if no word. */
    private int[] getWordBoundsAt(int offset)
    {
        try
        {
            String text = textArea.getDocument()
                    .getText(0, textArea.getDocument()
                            .getLength());
            if (offset >= text.length())
            {
                return null;
            }
            // Try position, then one char back
            if (!isWordChar(text.charAt(offset)))
            {
                if (offset > 0
                        && isWordChar(text.charAt(offset - 1)))
                {
                    offset--;
                }
                else
                {
                    return null;
                }
            }
            int start = offset;
            while (start > 0
                    && isWordChar(text.charAt(start - 1)))
            {
                start--;
            }
            int end = offset;
            while (end < text.length()
                    && isWordChar(text.charAt(end)))
            {
                end++;
            }
            return (start < end) ? new int[] { start, end }
                    : null;
        }
        catch (BadLocationException e)
        {
            return null;
        }
    }

    private static boolean isWordChar(char ch)
    {
        return Character.isLetterOrDigit(ch)
                || ch == '_';
    }

    /** Returns the text to search for in Ctrl+D: primary selection, or last secondary selection. */
    private String getSearchText()
    {
        String sel = textArea.getSelectedText();
        if (sel != null
                && !sel.isEmpty())
        {
            return sel;
        }
        if (!state.secondaryCarets.isEmpty())
        {
            int[] last = state.secondaryCarets.get(state.secondaryCarets.size() - 1);
            int s = Math.min(last[0], last[1]);
            int e = Math.max(last[0], last[1]);
            if (e > s)
            {
                try
                {
                    return textArea.getDocument()
                            .getText(s, e - s);
                }
                catch (BadLocationException ex)
                {
                    return null;
                }
            }
        }
        return null;
    }
}
