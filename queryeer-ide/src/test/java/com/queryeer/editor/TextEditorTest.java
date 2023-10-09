package com.queryeer.editor;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.event.UndoableEditEvent;
import javax.swing.plaf.TextUI;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.Document;
import javax.swing.text.EditorKit;
import javax.swing.text.JTextComponent;
import javax.swing.text.Keymap;
import javax.swing.text.NavigationFilter;
import javax.swing.text.Position;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import org.apache.commons.lang3.StringUtils;
import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaEditorKit;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaEditorKit.InsertTabAction;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaHighlighter;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaUI;
import org.fife.ui.rsyntaxtextarea.RSyntaxUtilities;
import org.fife.ui.rtextarea.ConfigurableCaret;
import org.fife.ui.rtextarea.RTextArea;
import org.fife.ui.rtextarea.RTextAreaEditorKit.DeleteNextCharAction;
import org.fife.ui.rtextarea.RTextAreaEditorKit.DeletePrevCharAction;
import org.fife.ui.rtextarea.RTextAreaEditorKit.NextVisualPositionAction;
import org.fife.ui.rtextarea.RTextAreaEditorKit.PasteAction;
import org.fife.ui.rtextarea.RUndoManager;
import org.fife.ui.rtextarea.RecordableTextAction;

import com.queryeer.editor.TextEditorTest.MultiCaret.CaretInfo;

//CSOFF
public class TextEditorTest extends JFrame
{
    TextEditorTest()
    {
        ExRSyntaxTextArea ta = new ExRSyntaxTextArea();

        // ta.getKeymap()
        // .setDefaultAction(new DefaultKeyTypedAction());

        // ta.setCaret(new MyCaret());
        ta.setText("""

                public static void main(String[] args)
                {
                    TextEditorTest t = new TextEditorTest();
                    t.setSize(new Dimension(800, 600));
                    t.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    t.setVisible(true);
                }

                            """);

        getContentPane().add(ta);
        pack();
    }

    public static class ExRSyntaxTextArea extends RSyntaxTextArea
    {
        private RUndoManager undoManager;

        @Override
        protected void processKeyEvent(KeyEvent e)
        {
            // Drop out of multi caret mode when ESC
            if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
            {
                ((MultiCaret) getCaret()).carets.clear();
            }
            super.processKeyEvent(e);
        }

        @Override
        protected org.fife.ui.rtextarea.RTextAreaUI createRTextAreaUI()
        {
            return new ExRSyntaxTextAreaUI(this);
        };

        @Override
        protected RUndoManager createUndoManager()
        {
            // Store the undo manager, we need to access that one to be able to restore carets
            undoManager = super.createUndoManager();
            return undoManager;
        }
    }

    public static class ExRSyntaxTextAreaUI extends RSyntaxTextAreaUI
    {
        public ExRSyntaxTextAreaUI(JComponent rSyntaxTextArea)
        {
            super(rSyntaxTextArea);
        }

        @Override
        protected Caret createCaret()
        {
            return new MultiCaret();
        }

        @Override
        public EditorKit getEditorKit(JTextComponent tc)
        {
            return new ExRSyntaxTextAreaEditorKit();
        }

        @Override
        protected Keymap createKeymap()
        {
            Keymap map = super.createKeymap();
            // Replace default action
            map.setDefaultAction(new ExDefaultKeyTypedAction());
            return map;
        }
    }

    public static class ExRSyntaxTextAreaEditorKit extends RSyntaxTextAreaEditorKit
    {
        @Override
        public Action[] getActions()
        {
            Action[] actions = super.getActions();

            /* Replace actions that needs extensions for multi caret. */
            for (int i = 0; i < actions.length; i++)
            {
                String name = (String) actions[i].getValue(Action.NAME);

                if (DefaultEditorKit.deleteNextCharAction.equalsIgnoreCase(name))
                {
                    actions[i] = new ExDeleteNextCharAction();
                }
                else if (DefaultEditorKit.deletePrevCharAction.equalsIgnoreCase(name))
                {
                    actions[i] = new ExDeletePrevCharAction();
                }
                else if (DefaultEditorKit.insertTabAction.equalsIgnoreCase(name))
                {
                    actions[i] = new ExInsertTabAction();
                }
                else if (DefaultEditorKit.forwardAction.equalsIgnoreCase(name))
                {
                    actions[i] = new ExNextVisualPositionAction(name, false, SwingConstants.EAST);
                }
                else if (DefaultEditorKit.backwardAction.equalsIgnoreCase(name))
                {
                    actions[i] = new ExNextVisualPositionAction(name, false, SwingConstants.WEST);
                }
                else if (DefaultEditorKit.upAction.equalsIgnoreCase(name))
                {
                    actions[i] = new ExNextVisualPositionAction(name, false, SwingConstants.NORTH);
                }
                else if (DefaultEditorKit.downAction.equalsIgnoreCase(name))
                {
                    actions[i] = new ExNextVisualPositionAction(name, false, SwingConstants.SOUTH);
                }
                else if (DefaultEditorKit.pasteAction.equalsIgnoreCase(name))
                {
                    actions[i] = new ExPasteAction();
                }
            }

            List<Action> result = new ArrayList<>(Arrays.asList(actions));

            return result.toArray(new Action[0]);
        }
    }

    public static class ExPasteAction extends PasteAction
    {
        @Override
        public void actionPerformedImpl(ActionEvent e, RTextArea textArea)
        {
            MultiCaret multiCaret = (MultiCaret) textArea.getCaret();
            if (multiCaret.carets.isEmpty())
            {
                super.actionPerformedImpl(e, textArea);
                return;
            }

            Clipboard c = Toolkit.getDefaultToolkit()
                    .getSystemClipboard();
            Transferable contents = c.getContents(null);
            if (contents == null)
            {
                return;
            }
            String text = null;
            try
            {
                text = contents.getTransferData(DataFlavor.stringFlavor) instanceof String s ? s
                        : null;
            }
            catch (Exception e1)
            {
            }
            if (text == null)
            {
                return;
            }
            String cmd = text;
            ExRSyntaxTextArea ta = (ExRSyntaxTextArea) textArea;
            RSyntaxDocument document = (RSyntaxDocument) textArea.getDocument();
            try
            {
                int length = 0;
                for (CaretInfo ci : multiCaret.carets)
                {
                    document.insertString(ci.startOffset + length, cmd, null);
                    length += cmd.length();
                }

                length = 0;
                for (CaretInfo ci : multiCaret.carets)
                {
                    length += cmd.length();
                    // Increase the offset of the caret to reflect is new position
                    ci.startOffset += length;
                }

                // Register undo for carets
                ta.undoManager.undoableEditHappened(new UndoableEditEvent(ta, new AbstractUndoableEdit()
                {
                    @Override
                    public void redo() throws CannotRedoException
                    {
                        super.redo();
                        int length = 0;
                        for (CaretInfo ci : multiCaret.carets)
                        {
                            length += cmd.length();
                            ci.startOffset += length;
                        }
                    }

                    @Override
                    public void undo() throws CannotUndoException
                    {
                        super.undo();
                        int length = 0;
                        for (CaretInfo ci : multiCaret.carets)
                        {
                            length += cmd.length();
                            ci.startOffset -= length;
                        }
                    }
                }));
            }
            catch (BadLocationException e1)
            {
                e1.printStackTrace();
            }
        }
    }

    public static class ExNextVisualPositionAction extends NextVisualPositionAction
    {
        // private boolean select;
        private int direction;

        public ExNextVisualPositionAction(String nm, boolean select, int dir)
        {
            super(nm, select, dir);
            // this.select = select;
            this.direction = dir;
        }

        @Override
        public void actionPerformedImpl(ActionEvent e, RTextArea textArea)
        {
            MultiCaret multiCaret = (MultiCaret) textArea.getCaret();
            if (multiCaret.carets.isEmpty())
            {
                super.actionPerformedImpl(e, textArea);
                return;
            }

            // Reset magic position to void weird jumps
            Point magicCaretPosition = multiCaret.getMagicCaretPosition();
            multiCaret.setMagicCaretPosition(null);
            for (CaretInfo ci : multiCaret.carets)
            {
                try
                {
                    Position.Bias[] bias = new Position.Bias[1];
                    NavigationFilter filter = textArea.getNavigationFilter();
                    int offset;
                    if (filter != null)
                    {
                        offset = filter.getNextVisualPositionFrom(textArea, ci.startOffset, Position.Bias.Forward, direction, bias);
                    }
                    else
                    {
                        offset = textArea.getUI()
                                .getNextVisualPositionFrom(textArea, ci.startOffset, Position.Bias.Forward, direction, bias);
                    }

                    ci.startOffset = offset;
                }
                catch (BadLocationException e1)
                {
                    e1.printStackTrace();
                }
            }
            multiCaret.setMagicCaretPosition(magicCaretPosition);
        }
    }

    public static class ExInsertTabAction extends InsertTabAction
    {
        @Override
        public void actionPerformedImpl(ActionEvent e, RTextArea textArea)
        {
            MultiCaret multiCaret = (MultiCaret) textArea.getCaret();
            if (multiCaret.carets.isEmpty())
            {
                super.actionPerformedImpl(e, textArea);
                return;
            }

            String replacement = textArea.getTabsEmulated() ? StringUtils.repeat(' ', textArea.getTabSize())
                    : "\t";
            ExRSyntaxTextArea ta = (ExRSyntaxTextArea) textArea;
            Document document = textArea.getDocument();
            try
            {
                int length = 0;
                for (CaretInfo ci : multiCaret.carets)
                {
                    // tex|t - Offset: 3
                    // tex |t - Offset: 5
                    document.insertString(ci.startOffset + length, replacement, null);
                    length += replacement.length();
                }

                // Adjust offsets
                length = 0;
                for (CaretInfo ci : multiCaret.carets)
                {
                    length += replacement.length();
                    // Increase the offset of the caret to reflect is new position
                    ci.startOffset += length;
                }

                // Register undo for carets
                ta.undoManager.undoableEditHappened(new UndoableEditEvent(ta, new AbstractUndoableEdit()
                {
                    @Override
                    public void redo() throws CannotRedoException
                    {
                        super.redo();
                        int length = 0;
                        for (com.queryeer.editor.TextEditorTest.MultiCaret.CaretInfo ci : multiCaret.carets)
                        {
                            length += replacement.length();
                            ci.startOffset += length;
                        }
                    }

                    @Override
                    public void undo() throws CannotUndoException
                    {
                        super.undo();
                        int length = 0;
                        for (com.queryeer.editor.TextEditorTest.MultiCaret.CaretInfo ci : multiCaret.carets)
                        {
                            length += replacement.length();
                            ci.startOffset -= length;
                        }
                    }
                }));
            }
            catch (BadLocationException e1)
            {
                e1.printStackTrace();
            }
        }
    }

    public static class ExDeletePrevCharAction extends DeletePrevCharAction
    {
        @Override
        public void actionPerformedImpl(ActionEvent e, RTextArea textArea)
        {
            MultiCaret multiCaret = (MultiCaret) textArea.getCaret();
            if (multiCaret.carets.isEmpty())
            {
                super.actionPerformedImpl(e, textArea);
                return;
            }

            ExRSyntaxTextArea ta = (ExRSyntaxTextArea) textArea;
            Document document = textArea.getDocument();

            for (CaretInfo ci : multiCaret.carets)
            {
                // One offset is at start then we cannot remove
                if (ci.startOffset <= 0)
                {
                    UIManager.getLookAndFeel()
                            .provideErrorFeedback(textArea);
                    return;
                }
            }

            try
            {
                int length = 0;
                for (CaretInfo ci : multiCaret.carets)
                {
                    // tex|t - Offset: 3
                    // te|t - Offset: 2
                    document.remove(ci.startOffset - 1 - length, 1);
                    length++;
                }

                // Adjust offsets
                length = 0;
                for (CaretInfo ci : multiCaret.carets)
                {
                    // Decrease the offset of the caret to reflect is new position
                    ci.startOffset -= (1 + length);
                    length++;
                }

                // Register undo for carets
                ta.undoManager.undoableEditHappened(new UndoableEditEvent(ta, new AbstractUndoableEdit()
                {
                    @Override
                    public void redo() throws CannotRedoException
                    {
                        super.redo();
                        int length = 0;
                        for (CaretInfo ci : multiCaret.carets)
                        {
                            ci.startOffset -= (1 + length);
                            length++;
                        }
                    }

                    @Override
                    public void undo() throws CannotUndoException
                    {
                        super.undo();
                        int length = 0;
                        for (CaretInfo ci : multiCaret.carets)
                        {
                            ci.startOffset += (1 + length);
                            length++;
                        }
                    }
                }));
            }
            catch (BadLocationException e1)
            {
                e1.printStackTrace();
            }
        }
    }

    public static class ExDeleteNextCharAction extends DeleteNextCharAction
    {
        @Override
        public void actionPerformedImpl(ActionEvent e, RTextArea textArea)
        {
            MultiCaret multiCaret = (MultiCaret) textArea.getCaret();
            if (multiCaret.carets.isEmpty())
            {
                super.actionPerformedImpl(e, textArea);
                return;
            }

            ExRSyntaxTextArea ta = (ExRSyntaxTextArea) textArea;
            Document document = textArea.getDocument();

            for (CaretInfo ci : multiCaret.carets)
            {
                // One offset is at end then we cannot remove next
                if (ci.startOffset >= document.getLength())
                {
                    UIManager.getLookAndFeel()
                            .provideErrorFeedback(textArea);
                    return;
                }
            }

            try
            {
                int length = 0;
                for (CaretInfo ci : multiCaret.carets)
                {
                    // tex|t - Offset: 3
                    // tex| - Offset: 3
                    document.remove(ci.startOffset - length, 1);
                    length++;
                }

                length = 0;
                for (CaretInfo ci : multiCaret.carets)
                {
                    // Decrease the offset of the caret to reflect is new position
                    ci.startOffset -= length;
                    length++;
                }

                // Register undo for carets
                ta.undoManager.undoableEditHappened(new UndoableEditEvent(ta, new AbstractUndoableEdit()
                {
                    @Override
                    public void redo() throws CannotRedoException
                    {
                        super.redo();
                        int length = 0;
                        for (CaretInfo ci : multiCaret.carets)
                        {
                            ci.startOffset -= length;
                            length++;
                        }
                    }

                    @Override
                    public void undo() throws CannotUndoException
                    {
                        super.undo();
                        int length = 0;
                        for (CaretInfo ci : multiCaret.carets)
                        {
                            ci.startOffset += length;
                            length++;
                        }
                    }
                }));
            }
            catch (BadLocationException e1)
            {
                e1.printStackTrace();
            }
        }
    }

    public static class ExDefaultKeyTypedAction extends RecordableTextAction
    {
        private Action delegate;

        public ExDefaultKeyTypedAction()
        {
            this(DefaultEditorKit.defaultKeyTypedAction);
        }

        protected ExDefaultKeyTypedAction(String name)
        {
            super(name, null, null, null, null);
            delegate = new DefaultEditorKit.DefaultKeyTypedAction();
        }

        @Override
        public void actionPerformedImpl(ActionEvent e, RTextArea textArea)
        {
            // DefaultKeyTypedAction *is* different across different JVM's
            // (at least the OSX implementation must be different - Alt+Numbers
            // inputs symbols such as '[', '{', etc., which is a *required*
            // feature on MacBooks running with non-English input, such as
            // German or Swedish Pro). So we can't just copy the
            // implementation, we must delegate to it.
            MultiCaret multiCaret = (MultiCaret) textArea.getCaret();
            if (multiCaret.carets.isEmpty())
            {
                delegate.actionPerformed(e);
                return;
            }
            if (!isValidDefaultTypedAction(e)
                    || !isValidDefaultTypedCommand(e))
            {
                return;
            }

            String cmd = e.getActionCommand();
            ExRSyntaxTextArea ta = (ExRSyntaxTextArea) textArea;
            RSyntaxDocument document = (RSyntaxDocument) textArea.getDocument();
            try
            {
                int length = 0;
                for (CaretInfo ci : multiCaret.carets)
                {
                    document.insertString(ci.startOffset + length, cmd, null);
                    length += cmd.length();
                }

                length = 0;
                for (CaretInfo ci : multiCaret.carets)
                {
                    length += cmd.length();
                    // Increase the offset of the caret to reflect is new position
                    ci.startOffset += length;
                }

                // Register undo for carets
                ta.undoManager.undoableEditHappened(new UndoableEditEvent(ta, new AbstractUndoableEdit()
                {
                    @Override
                    public void redo() throws CannotRedoException
                    {
                        super.redo();
                        int length = 0;
                        for (CaretInfo ci : multiCaret.carets)
                        {
                            length += cmd.length();
                            ci.startOffset += length;
                        }
                    }

                    @Override
                    public void undo() throws CannotUndoException
                    {
                        super.undo();
                        int length = 0;
                        for (CaretInfo ci : multiCaret.carets)
                        {
                            length += cmd.length();
                            ci.startOffset -= length;
                        }
                    }
                }));
            }
            catch (BadLocationException e1)
            {
                e1.printStackTrace();
            }

        }

        @Override
        public final String getMacroID()
        {
            return getName();
        }
    }

    static boolean isValidDefaultTypedAction(ActionEvent evt)
    {
        // Check whether the modifiers are OK
        int mod = evt.getModifiers();
        boolean ctrl = ((mod & ActionEvent.CTRL_MASK) != 0);
        boolean alt = RSyntaxUtilities.getOS() == RSyntaxUtilities.OS_MAC_OSX ? ((mod & ActionEvent.META_MASK) != 0)
                : ((mod & ActionEvent.ALT_MASK) != 0);
        return !(alt
                || ctrl);
    }

    static boolean isValidDefaultTypedCommand(ActionEvent evt)
    {
        final String cmd = evt.getActionCommand();
        return (cmd != null
                && cmd.length() == 1
                && cmd.charAt(0) >= 0x20
                && cmd.charAt(0) != 0x7F);
    }

    public static void main(String[] args)
    {
        TextEditorTest t = new TextEditorTest();
        t.setSize(new Dimension(800, 600));
        t.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        t.setVisible(true);
    }

    static class MultiCaretHighlighter extends RSyntaxTextAreaHighlighter
    {

    }

    static class MultiCaret extends ConfigurableCaret
    {
        private List<CaretInfo> carets = new ArrayList<>();
        private int mousePressDot;

        @Override
        public void mousePressed(MouseEvent e)
        {
            mousePressDot = getDot();
            // If alt is down then skip original mouse handler to avoid text getting selected when in multi caret mode
            if (e.isAltDown())
            {
                return;
            }
            super.mousePressed(e);
        }

        @Override
        public void mouseClicked(MouseEvent e)
        {
            // Multi caret mode, add a caret
            if (e.isAltDown())
            {
                JTextComponent tc = getComponent();
                try
                {
                    CaretInfo ci;
                    if (carets.isEmpty())
                    {
                        // Add the original dot if empty
                        ci = new CaretInfo(mousePressDot, mousePressDot);
                        if (!carets.contains(ci))
                        {
                            System.out.println("Added caret: " + mousePressDot + ", text: " + tc.getText(mousePressDot, 1));
                            carets.add(ci);
                        }
                    }
                    int offs = tc.viewToModel2D(e.getPoint());

                    // Put out carets along the y axis between original dot and click location
                    if (e.isShiftDown())
                    {
                        int startOffset = mousePressDot;
                        int endOffset = offs;
                        int direction = SwingConstants.SOUTH;
                        if (offs < startOffset)
                        {
                            startOffset = mousePressDot;
                            endOffset = offs;
                            direction = SwingConstants.NORTH;
                        }

                        Position.Bias[] bias = new Position.Bias[1];
                        NavigationFilter filter = tc.getNavigationFilter();
                        int offset = startOffset;
                        System.out.println("Start: " + startOffset);
                        System.out.println("End: " + endOffset);

                        while ((direction == SwingConstants.SOUTH
                                && offset < endOffset)
                                || (direction == SwingConstants.NORTH
                                        && offset > endOffset))
                        {
                            try
                            {
                                if (filter != null)
                                {
                                    offset = filter.getNextVisualPositionFrom(tc, offset, Position.Bias.Forward, direction, bias);
                                }
                                else
                                {
                                    offset = tc.getUI()
                                            .getNextVisualPositionFrom(tc, offset, Position.Bias.Forward, direction, bias);
                                }

                                ci = new CaretInfo(offset, offset);
                                if (!carets.contains(ci))
                                {
                                    System.out.println("Added caret: " + offset + ", text: " + tc.getText(offset, 1));
                                    carets.add(ci);
                                }
                            }
                            catch (BadLocationException ee)
                            {
                                break;
                            }
                        }
                    }
                    else
                    {
                        ci = new CaretInfo(offs, offs);
                        if (!carets.contains(ci))
                        {
                            System.out.println("Added caret: " + offs + ", text: " + tc.getText(offs, 1));
                            carets.add(ci);
                        }
                    }
                }
                catch (BadLocationException e1)
                {
                    e1.printStackTrace();
                }

                carets.sort(Comparator.comparingInt(CaretInfo::getStartOffset));

                e.consume();
            }
            else
            {
                carets.clear();
                super.mouseClicked(e);
            }
        }

        @Override
        public void paint(Graphics g)
        {
            if (carets.isEmpty())
            {
                super.paint(g);
                return;
            }
            if (isVisible()
                    && !carets.isEmpty())
            {
                damage(this);

                RTextArea textArea = getTextArea();
                g.setColor(textArea.getCaretColor());
                TextUI mapper = textArea.getUI();

                try
                {
                    for (CaretInfo caret : carets)
                    {
                        @SuppressWarnings("deprecation")
                        Rectangle r = mapper.modelToView(textArea, caret.startOffset);
                        int lineY = r.y + 1;
                        g.drawLine(r.x, lineY, r.x, lineY + r.height - 2);
                        r.x++;
                        g.drawLine(r.x, lineY, r.x, lineY + r.height - 2);
                        textArea.repaint(r);
                    }
                }
                catch (BadLocationException e)
                {
                    e.printStackTrace();
                }
            }
        }

        static class CaretInfo
        {
            CaretInfo(int startOffset, int endOffset)
            {
                this.startOffset = startOffset;
                this.endOffset = endOffset;
            }

            int startOffset;
            int endOffset;

            int getStartOffset()
            {
                return startOffset;
            }

            @Override
            public int hashCode()
            {
                return startOffset;
            }

            @Override
            public boolean equals(Object obj)
            {
                if (obj == null)
                {
                    return false;
                }
                else if (obj == this)
                {
                    return true;
                }
                if (obj instanceof CaretInfo ci)
                {
                    return startOffset == ci.startOffset
                            && endOffset == ci.endOffset;
                }
                return false;
            }
        }
    }
}
// CSON
