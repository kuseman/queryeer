package com.queryeer.editor;

import java.awt.Graphics;
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

    // Tracks whether an Alt+Shift drag is in progress, and the anchor position from the initial press.
    private boolean altDragActive = false;
    private int altDragAnchorDot = -1;
    private int altDragAnchorMark = -1;

    MultiCaretAwareEditorPane()
    {
        super();
    }

    void setMultiCaretSupport(MultiCaretSupport support)
    {
        this.multiCaretSupport = support;
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);
        if (multiCaretSupport != null)
        {
            multiCaretSupport.paintCaretIndicators(g);
        }
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
                && SwingUtilities.isLeftMouseButton(e))
        {
            if (e.getID() == MouseEvent.MOUSE_RELEASED)
            {
                altDragActive = false;
            }
            else if (e.getID() == MouseEvent.MOUSE_PRESSED)
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
                    // Save anchor so that subsequent drag events extend from this position.
                    altDragActive = true;
                    altDragAnchorDot = savedDot;
                    altDragAnchorMark = savedMark;
                    super.processMouseEvent(e);
                    int clickPos = getCaretPosition();
                    // Restore primary to where it was before the click
                    restoreCaret(savedDot, savedMark);
                    multiCaretSupport.handleAltShiftClick(clickPos, e.getPoint().x);
                    return;
                }
                else if (altDown)
                {
                    altDragActive = false;
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
                    altDragActive = false;
                    // Plain left-click: clear secondary carets
                    multiCaretSupport.clearSecondaryCarets();
                }
            }
        }

        super.processMouseEvent(e);
    }

    @Override
    protected void processMouseMotionEvent(MouseEvent e)
    {
        if (altDragActive
                && multiCaretSupport != null
                && e.getID() == MouseEvent.MOUSE_DRAGGED
                && SwingUtilities.isLeftMouseButton(e))
        {
            int dragPos = viewToModel2D(e.getPoint());
            restoreCaret(altDragAnchorDot, altDragAnchorMark);
            multiCaretSupport.handleAltShiftClick(dragPos, e.getPoint().x);
            return;
        }
        super.processMouseMotionEvent(e);
    }

    private void restoreCaret(int dot, int mark)
    {
        if (dot == mark)
        {
            setCaretPosition(dot);
        }
        else
        {
            getCaret().setDot(mark);
            getCaret().moveDot(dot);
        }
    }
}
