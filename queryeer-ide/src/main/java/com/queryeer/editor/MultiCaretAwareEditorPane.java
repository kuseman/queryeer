package com.queryeer.editor;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

import javax.swing.SwingUtilities;

import org.fife.ui.rsyntaxtextarea.TextEditorPane;

/**
 * Subclass of {@link TextEditorPane} that overrides {@link #processMouseEvent} so we can capture the primary caret position BEFORE RSTA's UI delegate moves it in response to an Alt+Click. This is
 * necessary because RSTA installs its own {@link java.awt.event.MouseListener} during {@code installUI}, which fires before any listener added via {@code addMouseListener}.
 */
class MultiCaretAwareEditorPane extends TextEditorPane
{
    private MultiCaretSupport multiCaretSupport;

    MultiCaretAwareEditorPane()
    {
        super();
    }

    void setMultiCaretSupport(MultiCaretSupport support)
    {
        this.multiCaretSupport = support;
    }

    @Override
    public void paste()
    {
        if (multiCaretSupport != null
                && multiCaretSupport.hasSecondaryCarets())
        {
            multiCaretSupport.pasteToAllCarets();
            return;
        }
        super.paste();
    }

    @Override
    protected void processMouseEvent(MouseEvent e)
    {
        if (multiCaretSupport != null
                && e.getID() == MouseEvent.MOUSE_PRESSED
                && SwingUtilities.isLeftMouseButton(e))
        {
            int mods = e.getModifiersEx();
            boolean altDown = (mods & InputEvent.ALT_DOWN_MASK) != 0;
            boolean shiftDown = (mods & InputEvent.SHIFT_DOWN_MASK) != 0;

            if (altDown
                    && shiftDown)
            {
                // Alt+Shift+Click: add secondary carets along the vertical axis
                // from the current primary caret line to the clicked line.
                // Let RSTA process the event so focus and accurate position mapping work,
                // then restore the primary caret to its original position.
                int savedDot = getCaretPosition();
                int savedMark = getCaret().getMark();
                super.processMouseEvent(e);
                int clickPos = getCaretPosition();
                // Restore primary to where it was before the click
                if (savedDot == savedMark)
                {
                    setCaretPosition(savedDot);
                }
                else
                {
                    getCaret().setDot(savedMark);
                    getCaret().moveDot(savedDot);
                }
                multiCaretSupport.handleAltShiftClick(clickPos);
                return;
            }
            else if (altDown)
            {
                // Alt+Click: add/toggle a single secondary caret at the click position.
                // Capture OLD primary caret position BEFORE RSTA moves it.
                int savedDot = getCaretPosition();
                int savedMark = getCaret().getMark();
                // Let RSTA process the event — this moves the primary caret to click position
                super.processMouseEvent(e);
                // Now primary is at the click position; savedDot/savedMark = old primary
                multiCaretSupport.handleAltClick(savedDot, savedMark, getCaretPosition());
                return;
            }
            else
            {
                // Plain left-click: clear secondary carets
                multiCaretSupport.clearSecondaryCarets();
            }
        }

        super.processMouseEvent(e);
    }
}
