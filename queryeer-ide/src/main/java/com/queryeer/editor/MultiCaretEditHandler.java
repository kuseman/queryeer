package com.queryeer.editor;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

/**
 * Handles keyboard input and document edit operations for multi-caret mode. Responsible for key event routing, caret movement across all positions, applying character/edit operations at all caret
 * positions simultaneously, and clipboard copy/cut/paste.
 */
class MultiCaretEditHandler
{
    static final int MENU_MASK = Toolkit.getDefaultToolkit()
            .getMenuShortcutKeyMaskEx();

    /** Fallback flavor for transferables (e.g. TableTransferable) that don't expose DataFlavor.stringFlavor. */
    private static final DataFlavor PLAIN_TEXT_STRING_FLAVOR;

    static
    {
        DataFlavor f;
        try
        {
            f = new DataFlavor("text/plain;class=java.lang.String");
        }
        catch (Exception e)
        {
            f = null;
        }
        PLAIN_TEXT_STRING_FLAVOR = f;
    }

    private final MultiCaretAwareEditorPane textArea;
    private final MultiCaretState state;

    MultiCaretEditHandler(MultiCaretAwareEditorPane textArea, MultiCaretState state)
    {
        this.textArea = textArea;
        this.state = state;
    }

    // -----------------------------------------------------------------------
    // Key handling
    // -----------------------------------------------------------------------

    void handleKeyPressed(KeyEvent e)
    {
        int code = e.getKeyCode();
        // CSOFF
        boolean shift = e.isShiftDown();
        // CSON

        if (code == KeyEvent.VK_ESCAPE)
        {
            if (!state.secondaryCarets.isEmpty())
            {
                state.clearSecondaryCarets();
                // Do not consume — let autocomplete/other handlers also see Escape
            }
            return;
        }

        if (state.secondaryCarets.isEmpty())
        {
            return;
        }

        // Intercept paste here (key-listener phase) and consume the event so neither the legacy
        // Keymap nor the InputMap/ActionMap dispatch fires — both would call textArea.paste() and
        // our paste() override, causing the paste to be applied twice.
        if (code == KeyEvent.VK_V
                && (e.getModifiersEx() & MENU_MASK) != 0)
        {
            pasteToAllCarets();
            e.consume();
            return;
        }

        if ((code == KeyEvent.VK_C
                || code == KeyEvent.VK_X)
                && (e.getModifiersEx() & MENU_MASK) != 0)
        {
            if (state.hasSecondarySelections())
            {
                copyAllSelections(code == KeyEvent.VK_X);
                e.consume();
            }
            return;
        }

        switch (code)
        {
            case KeyEvent.VK_LEFT:
                moveAllCarets(shift ? MoveType.SELECT_LEFT
                        : MoveType.MOVE_LEFT);
                e.consume();
                break;
            case KeyEvent.VK_RIGHT:
                moveAllCarets(shift ? MoveType.SELECT_RIGHT
                        : MoveType.MOVE_RIGHT);
                e.consume();
                break;
            case KeyEvent.VK_UP:
                moveAllCarets(shift ? MoveType.SELECT_UP
                        : MoveType.MOVE_UP);
                e.consume();
                break;
            case KeyEvent.VK_DOWN:
                moveAllCarets(shift ? MoveType.SELECT_DOWN
                        : MoveType.MOVE_DOWN);
                e.consume();
                break;
            case KeyEvent.VK_HOME:
                moveAllCarets(shift ? MoveType.SELECT_HOME
                        : MoveType.MOVE_HOME);
                e.consume();
                break;
            case KeyEvent.VK_END:
                moveAllCarets(shift ? MoveType.SELECT_END
                        : MoveType.MOVE_END);
                e.consume();
                break;
            case KeyEvent.VK_BACK_SPACE:
                applyEditToAll(EditType.BACKSPACE);
                e.consume();
                break;
            case KeyEvent.VK_DELETE:
                applyEditToAll(EditType.DELETE);
                e.consume();
                break;
            case KeyEvent.VK_ENTER:
                applyEditToAll(EditType.ENTER);
                e.consume();
                break;
            case KeyEvent.VK_TAB:
                applyEditToAll(EditType.TAB);
                e.consume();
                break;
            default:
                break;
        }
    }

    void handleKeyTyped(KeyEvent e)
    {
        char ch = e.getKeyChar();
        if (ch == KeyEvent.CHAR_UNDEFINED
                || Character.isISOControl(ch))
        {
            return;
        }
        // Skip if a command modifier is down (shortcut, not text input)
        int mods = e.getModifiersEx();
        if ((mods & (InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK)) != 0)
        {
            return;
        }
        applyCharToAll(ch);
        e.consume();
    }

    // -----------------------------------------------------------------------
    // Clipboard operations
    // -----------------------------------------------------------------------

    void pasteToAllCarets()
    {
        try
        {
            Transferable contents = Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .getContents(null);
            if (contents != null)
            {
                String text = getStringFromTransferable(contents);
                if (text == null)
                {
                    return;
                }
                // Normalize line endings to \n so that \r\n clipboard content (Windows) does not
                // introduce bare \r characters into the document. The document's EndOfLineStringProperty
                // controls how \n is translated back to the original line ending on save/backup.
                text = text.replace("\r\n", "\n")
                        .replace("\r", "\n");
                // Smart paste: if the clipboard was produced by a multi-caret copy (N lines for
                // N carets), distribute one line per caret; otherwise paste the whole text at
                // every caret position.
                String[] lines = text.split("\n", -1);
                int caretCount = 1 + state.secondaryCarets.size();
                if (lines.length == caretCount
                        && caretCount > 1)
                {
                    applyLinesPerCaret(lines);
                }
                else
                {
                    applyTextToAll(text);
                }
            }
        }
        catch (Exception ex)
        {
            // Swallow clipboard errors
        }
    }

    private String getStringFromTransferable(Transferable contents)
    {
        try
        {
            if (contents.isDataFlavorSupported(DataFlavor.stringFlavor))
            {
                return (String) contents.getTransferData(DataFlavor.stringFlavor);
            }
            else if (PLAIN_TEXT_STRING_FLAVOR != null
                    && contents.isDataFlavorSupported(PLAIN_TEXT_STRING_FLAVOR))
            {
                // Fallback for transferables like TableTransferable that expose text/plain
                // but not DataFlavor.stringFlavor (application/x-java-serialized-object)
                return (String) contents.getTransferData(PLAIN_TEXT_STRING_FLAVOR);
            }
        }
        catch (Exception e)
        {
            // ignore
        }
        return null;
    }

    /**
     * Copies (or cuts) the selected text at every caret. Each caret contributes one segment joined by newlines, enabling smart paste to redistribute the text back to the same positions. When
     * {@code cut} is true the selections are deleted after the copy.
     */
    void copyAllSelections(boolean cut)
    {
        String allSelection = getAllSelections();
        if (allSelection.length() > 0)
        {
            StringSelection sel = new StringSelection(allSelection);
            Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .setContents(sel, null);
        }
        if (cut)
        {
            applyTextToAll("");
        }
    }

    /** Return the block selection. */
    String getAllSelections()
    {
        List<int[]> allCarets = state.buildAllCaretsSortedAsc();
        StringBuilder sb = new StringBuilder();
        try
        {
            for (int i = 0; i < allCarets.size(); i++)
            {
                int[] caretInfo = allCarets.get(i);
                int selStart = Math.min(caretInfo[0], caretInfo[1]);
                int selEnd = Math.max(caretInfo[0], caretInfo[1]);
                if (i > 0)
                {
                    sb.append('\n');
                }
                if (selEnd > selStart)
                {
                    sb.append(textArea.getDocument()
                            .getText(selStart, selEnd - selStart));
                }
            }
        }
        catch (BadLocationException e)
        {
            // Swallow
        }
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Caret movement
    // -----------------------------------------------------------------------

    private enum MoveType
    {
        MOVE_LEFT,
        MOVE_RIGHT,
        MOVE_UP,
        MOVE_DOWN,
        MOVE_HOME,
        MOVE_END,
        SELECT_LEFT,
        SELECT_RIGHT,
        SELECT_UP,
        SELECT_DOWN,
        SELECT_HOME,
        SELECT_END
    }

    private void moveAllCarets(MoveType type)
    {
        // Move primary caret
        movePrimaryCaretBy(type);

        // Move each secondary caret
        try
        {
            for (int[] caret : state.secondaryCarets)
            {
                moveSingleCaret(caret, type);
            }
        }
        catch (BadLocationException e)
        {
            // Swallow
        }
        state.updateHighlights();
    }

    private void movePrimaryCaretBy(MoveType type)
    {
        try
        {
            int dot = textArea.getCaretPosition();
            int mark = textArea.getCaret()
                    .getMark();
            int[] pseudo = new int[] { dot, mark };
            moveSingleCaret(pseudo, type);

            if (pseudo[0] == pseudo[1])
            {
                textArea.setCaretPosition(pseudo[0]);
            }
            else
            {
                // Maintain mark, move dot
                textArea.getCaret()
                        .setDot(pseudo[1]);
                textArea.getCaret()
                        .moveDot(pseudo[0]);
            }
        }
        catch (BadLocationException e)
        {
            // Swallow
        }
    }

    private void moveSingleCaret(int[] caret, MoveType type) throws BadLocationException
    {
        int dot = caret[0];
        int mark = caret[1];
        int docLen = textArea.getDocument()
                .getLength();

        switch (type)
        {
            case MOVE_LEFT:
                if (dot != mark)
                {
                    dot = Math.min(dot, mark);
                }
                else
                {
                    dot = Math.max(0, dot - 1);
                }
                mark = dot;
                break;
            case MOVE_RIGHT:
                if (dot != mark)
                {
                    dot = Math.max(dot, mark);
                }
                else
                {
                    dot = Math.min(docLen, dot + 1);
                }
                mark = dot;
                break;
            case MOVE_UP:
            {
                int line = textArea.getLineOfOffset(dot);
                if (line > 0)
                {
                    int col = dot - textArea.getLineStartOffset(line);
                    int prevStart = textArea.getLineStartOffset(line - 1);
                    int prevEnd = textArea.getLineEndOffset(line - 1);
                    int prevLen = prevEnd - prevStart - (line - 1 < textArea.getLineCount() - 1 ? 1
                            : 0);
                    dot = prevStart + Math.min(col, prevLen);
                }
                mark = dot;
                break;
            }
            case MOVE_DOWN:
            {
                int line = textArea.getLineOfOffset(dot);
                if (line < textArea.getLineCount() - 1)
                {
                    int col = dot - textArea.getLineStartOffset(line);
                    int nextStart = textArea.getLineStartOffset(line + 1);
                    int nextEnd = textArea.getLineEndOffset(line + 1);
                    int nextLen = nextEnd - nextStart - (line + 1 < textArea.getLineCount() - 1 ? 1
                            : 0);
                    dot = nextStart + Math.min(col, nextLen);
                }
                mark = dot;
                break;
            }
            case MOVE_HOME:
            {
                int line = textArea.getLineOfOffset(dot);
                dot = textArea.getLineStartOffset(line);
                mark = dot;
                break;
            }
            case MOVE_END:
            {
                int line = textArea.getLineOfOffset(dot);
                int end = textArea.getLineEndOffset(line);
                if (line < textArea.getLineCount() - 1)
                {
                    end--; // exclude newline
                }
                dot = end;
                mark = dot;
                break;
            }
            case SELECT_LEFT:
                dot = Math.max(0, dot - 1);
                break;
            case SELECT_RIGHT:
                dot = Math.min(docLen, dot + 1);
                break;
            case SELECT_UP:
            {
                int line = textArea.getLineOfOffset(dot);
                if (line > 0)
                {
                    int col = dot - textArea.getLineStartOffset(line);
                    int prevStart = textArea.getLineStartOffset(line - 1);
                    int prevEnd = textArea.getLineEndOffset(line - 1);
                    int prevLen = prevEnd - prevStart - (line - 1 < textArea.getLineCount() - 1 ? 1
                            : 0);
                    dot = prevStart + Math.min(col, prevLen);
                }
                break;
            }
            case SELECT_DOWN:
            {
                int line = textArea.getLineOfOffset(dot);
                if (line < textArea.getLineCount() - 1)
                {
                    int col = dot - textArea.getLineStartOffset(line);
                    int nextStart = textArea.getLineStartOffset(line + 1);
                    int nextEnd = textArea.getLineEndOffset(line + 1);
                    int nextLen = nextEnd - nextStart - (line + 1 < textArea.getLineCount() - 1 ? 1
                            : 0);
                    dot = nextStart + Math.min(col, nextLen);
                }
                break;
            }
            case SELECT_HOME:
            {
                int line = textArea.getLineOfOffset(dot);
                dot = textArea.getLineStartOffset(line);
                break;
            }
            case SELECT_END:
            {
                int line = textArea.getLineOfOffset(dot);
                int end = textArea.getLineEndOffset(line);
                if (line < textArea.getLineCount() - 1)
                {
                    end--;
                }
                dot = end;
                break;
            }
            default:
                break;
        }
        caret[0] = dot;
        caret[1] = mark;
    }

    // -----------------------------------------------------------------------
    // Apply edits
    // -----------------------------------------------------------------------

    private enum EditType
    {
        BACKSPACE,
        DELETE,
        ENTER,
        TAB
    }

    /** Insert a single character at all caret positions (primary + secondary). */
    private void applyCharToAll(char ch)
    {
        applyTextToAll(String.valueOf(ch));
    }

    private void applyEditToAll(EditType type)
    {
        state.saveCaretSnapshot();
        // Process carets ascending with a cumulative offset so that each operation's
        // document position is correctly adjusted for all prior insertions/removals.
        List<int[]> allCarets = state.buildAllCaretsSortedAsc();

        textArea.beginAtomicEdit();
        try
        {
            Document doc = textArea.getDocument();
            int cumulativeOffset = 0;
            for (int[] caretInfo : allCarets)
            {
                int originalDot = caretInfo[0];
                int originalMark = caretInfo[1];
                boolean isPrimary = caretInfo[2] == 1;
                int originalSelStart = Math.min(originalDot, originalMark);
                int originalSelEnd = Math.max(originalDot, originalMark);
                int adjustedStart = originalSelStart + cumulativeOffset;
                int adjustedEnd = originalSelEnd + cumulativeOffset;

                try
                {
                    int newDot = adjustedStart;
                    int delta = 0;
                    switch (type)
                    {
                        case BACKSPACE:
                            if (adjustedEnd > adjustedStart)
                            {
                                doc.remove(adjustedStart, adjustedEnd - adjustedStart);
                                delta = -(adjustedEnd - adjustedStart);
                                newDot = adjustedStart;
                            }
                            else if (adjustedStart > 0)
                            {
                                doc.remove(adjustedStart - 1, 1);
                                delta = -1;
                                newDot = adjustedStart - 1;
                            }
                            break;

                        case DELETE:
                            if (adjustedEnd > adjustedStart)
                            {
                                doc.remove(adjustedStart, adjustedEnd - adjustedStart);
                                delta = -(adjustedEnd - adjustedStart);
                                newDot = adjustedStart;
                            }
                            else if (adjustedStart < doc.getLength())
                            {
                                doc.remove(adjustedStart, 1);
                                delta = -1;
                                newDot = adjustedStart;
                            }
                            break;

                        case ENTER:
                        {
                            if (adjustedEnd > adjustedStart)
                            {
                                doc.remove(adjustedStart, adjustedEnd - adjustedStart);
                                delta -= (adjustedEnd - adjustedStart);
                            }
                            String indent = getIndentOf(adjustedStart);
                            String insertion = "\n" + indent;
                            doc.insertString(adjustedStart, insertion, null);
                            delta += insertion.length();
                            newDot = adjustedStart + insertion.length();
                            break;
                        }

                        case TAB:
                        {
                            if (adjustedEnd > adjustedStart)
                            {
                                doc.remove(adjustedStart, adjustedEnd - adjustedStart);
                                delta -= (adjustedEnd - adjustedStart);
                            }
                            int tabSize = textArea.getTabSize();
                            int col = getColumnOf(adjustedStart);
                            int spaces = tabSize - (col % tabSize);
                            StringBuilder sb = new StringBuilder(spaces);
                            for (int i = 0; i < spaces; i++)
                            {
                                sb.append(' ');
                            }
                            doc.insertString(adjustedStart, sb.toString(), null);
                            delta += spaces;
                            newDot = adjustedStart + spaces;
                            break;
                        }

                        default:
                            break;
                    }

                    cumulativeOffset += delta;
                    if (isPrimary)
                    {
                        textArea.setCaretPosition(newDot);
                    }
                    else
                    {
                        state.updateSecondaryPosition(originalDot, originalMark, newDot, newDot);
                    }
                }
                catch (BadLocationException ex)
                {
                    // Swallow individual caret failures
                }
            }
        }
        finally
        {
            textArea.endAtomicEdit();
        }
        state.updateHighlights();
    }

    void applyTextToAll(String text)
    {
        state.saveCaretSnapshot();
        // Process carets ascending with a cumulative offset so that each operation's
        // document position is correctly adjusted for all prior insertions/removals.
        List<int[]> allCarets = state.buildAllCaretsSortedAsc();

        textArea.beginAtomicEdit();
        try
        {
            Document doc = textArea.getDocument();
            int cumulativeOffset = 0;
            for (int[] caretInfo : allCarets)
            {
                int originalDot = caretInfo[0];
                int originalMark = caretInfo[1];
                boolean isPrimary = caretInfo[2] == 1;
                int originalSelStart = Math.min(originalDot, originalMark);
                int originalSelEnd = Math.max(originalDot, originalMark);
                int adjustedStart = originalSelStart + cumulativeOffset;
                int adjustedEnd = originalSelEnd + cumulativeOffset;

                try
                {
                    int removed = 0;
                    if (adjustedEnd > adjustedStart)
                    {
                        doc.remove(adjustedStart, adjustedEnd - adjustedStart);
                        removed = adjustedEnd - adjustedStart;
                    }
                    doc.insertString(adjustedStart, text, null);
                    int newDot = adjustedStart + text.length();
                    cumulativeOffset += text.length() - removed;

                    if (isPrimary)
                    {
                        textArea.setCaretPosition(newDot);
                    }
                    else
                    {
                        state.updateSecondaryPosition(originalDot, originalMark, newDot, newDot);
                    }
                }
                catch (BadLocationException ex)
                {
                    // Swallow individual caret failures
                }
            }
        }
        finally
        {
            textArea.endAtomicEdit();
        }
        state.updateHighlights();
    }

    /**
     * Applies one line of text per caret in ascending-position order. Used when the clipboard was produced by a multi-caret copy and has the same number of segments as active carets.
     */
    private void applyLinesPerCaret(String[] lines)
    {
        state.saveCaretSnapshot();
        List<int[]> allCarets = state.buildAllCaretsSortedAsc();
        textArea.beginAtomicEdit();
        try
        {
            Document doc = textArea.getDocument();
            int cumulativeOffset = 0;
            for (int i = 0; i < allCarets.size(); i++)
            {
                int[] caretInfo = allCarets.get(i);
                int originalDot = caretInfo[0];
                int originalMark = caretInfo[1];
                boolean isPrimary = caretInfo[2] == 1;
                int originalSelStart = Math.min(originalDot, originalMark);
                int originalSelEnd = Math.max(originalDot, originalMark);
                int adjustedStart = originalSelStart + cumulativeOffset;
                int adjustedEnd = originalSelEnd + cumulativeOffset;
                String lineText = lines[i];
                try
                {
                    int removed = 0;
                    if (adjustedEnd > adjustedStart)
                    {
                        doc.remove(adjustedStart, adjustedEnd - adjustedStart);
                        removed = adjustedEnd - adjustedStart;
                    }
                    doc.insertString(adjustedStart, lineText, null);
                    int newDot = adjustedStart + lineText.length();
                    cumulativeOffset += lineText.length() - removed;
                    if (isPrimary)
                    {
                        textArea.setCaretPosition(newDot);
                    }
                    else
                    {
                        state.updateSecondaryPosition(originalDot, originalMark, newDot, newDot);
                    }
                }
                catch (BadLocationException ex)
                {
                    // Swallow individual caret failures
                }
            }
        }
        finally
        {
            textArea.endAtomicEdit();
        }
        state.updateHighlights();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String getIndentOf(int offset)
    {
        try
        {
            int line = textArea.getLineOfOffset(offset);
            int lineStart = textArea.getLineStartOffset(line);
            int lineEnd = textArea.getLineEndOffset(line);
            String lineText = textArea.getDocument()
                    .getText(lineStart, lineEnd - lineStart);
            StringBuilder indent = new StringBuilder();
            for (char c : lineText.toCharArray())
            {
                if (c == ' '
                        || c == '\t')
                {
                    indent.append(c);
                }
                else
                {
                    break;
                }
            }
            return indent.toString();
        }
        catch (BadLocationException e)
        {
            return "";
        }
    }

    private int getColumnOf(int offset)
    {
        try
        {
            int line = textArea.getLineOfOffset(offset);
            return offset - textArea.getLineStartOffset(line);
        }
        catch (BadLocationException e)
        {
            return 0;
        }
    }
}
