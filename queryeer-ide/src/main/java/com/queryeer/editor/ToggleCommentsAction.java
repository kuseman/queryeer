package com.queryeer.editor;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.KeyStroke;
import javax.swing.text.BadLocationException;

import org.apache.commons.lang3.mutable.MutableInt;
import org.fife.ui.rsyntaxtextarea.TextEditorPane;
import org.kordamp.ikonli.fontawesome.FontAwesome;

import com.queryeer.IconFactory;

/** Action that toggles line comments on selected lines. */
class ToggleCommentsAction extends AbstractAction
{
    private final TextEditorPane textEditor;
    private final MultiCaretSupport multiCaretSupport;

    ToggleCommentsAction(TextEditorPane textEditor, MultiCaretSupport multiCaretSupport)
    {
        super("", IconFactory.of(FontAwesome.INDENT));
        this.textEditor = textEditor;
        this.multiCaretSupport = multiCaretSupport;

        putValue(com.queryeer.api.action.Constants.ACTION_SHOW_IN_TOOLBAR, true);
        putValue(com.queryeer.api.action.Constants.ACTION_ORDER, 9);
        putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_7, Toolkit.getDefaultToolkit()
                .getMenuShortcutKeyMaskEx()));
        putValue(Action.ACTION_COMMAND_KEY, "toggleComments");
        putValue(Action.SHORT_DESCRIPTION, "Toggle Comments On Selected Lines");
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {
        toggleComments();
    }

    private void toggleComments()
    {
        List<int[]> allCarets = multiCaretSupport.buildAllCaretsSortedAsc();

        int primaryDot = textEditor.getCaretPosition();
        int primaryMark = textEditor.getCaret()
                .getMark();

        Boolean addComments = null;

        try
        {
            Set<Integer> coveredLines = new java.util.TreeSet<>();
            for (int[] caret : allCarets)
            {
                int selStart = Math.min(caret[0], caret[1]);
                int selEnd = Math.max(caret[0], caret[1]);

                int startLine = textEditor.getLineOfOffset(selStart);
                int endLine = startLine;
                if (selEnd > selStart)
                {
                    endLine = textEditor.getLineOfOffset(Math.max(selStart, selEnd - 1));
                }

                for (int i = startLine; i <= endLine; i++)
                {
                    coveredLines.add(i);
                }
            }

            List<MutableInt> commentOffsets = new ArrayList<>();
            for (int lineIndex : coveredLines)
            {
                int startOffset = textEditor.getLineStartOffset(lineIndex);
                int lineLength = textEditor.getLineEndOffset(lineIndex) - startOffset;
                if (lineLength == 0)
                {
                    continue;
                }

                String lineText = textEditor.getText(startOffset, lineLength);
                int contentLength = lineText.length();
                while (contentLength > 0)
                {
                    char ch = lineText.charAt(contentLength - 1);
                    if (ch == '\n'
                            || ch == '\r')
                    {
                        contentLength--;
                        continue;
                    }
                    break;
                }

                if (contentLength == 0)
                {
                    continue;
                }

                int indent = 0;
                while (indent < contentLength
                        && (lineText.charAt(indent) == ' '
                                || lineText.charAt(indent) == '\t'))
                {
                    indent++;
                }

                if (indent == contentLength)
                {
                    continue;
                }

                boolean lineIsCommented = lineText.substring(indent, contentLength)
                        .startsWith("--");
                if (addComments == null)
                {
                    addComments = !lineIsCommented;
                }

                if (addComments
                        || lineIsCommented)
                {
                    commentOffsets.add(new MutableInt(startOffset + indent));
                }
            }

            if (!commentOffsets.isEmpty())
            {
                List<Integer> originalOffsets = new ArrayList<>(commentOffsets.size());
                for (MutableInt o : commentOffsets)
                {
                    originalOffsets.add(o.intValue());
                }

                textEditor.beginAtomicEdit();
                try
                {
                    int modifier = 0;
                    for (MutableInt commentOffset : commentOffsets)
                    {
                        if (addComments)
                        {
                            textEditor.getDocument()
                                    .insertString(commentOffset.intValue() + modifier, "--", null);
                        }
                        else
                        {
                            textEditor.getDocument()
                                    .remove(commentOffset.intValue() + modifier, 2);
                        }
                        commentOffset.setValue(Math.max(commentOffset.intValue() + modifier, 0));
                        modifier += addComments ? 2
                                : -2;
                    }
                }
                finally
                {
                    textEditor.endAtomicEdit();
                }

                int delta = addComments ? 2
                        : -2;
                int newDot = shiftCaretPosition(primaryDot, originalOffsets, delta);
                int newMark = shiftCaretPosition(primaryMark, originalOffsets, delta);
                if (newDot == newMark)
                {
                    textEditor.setCaretPosition(newDot);
                }
                else
                {
                    textEditor.getCaret()
                            .setDot(newMark);
                    textEditor.getCaret()
                            .moveDot(newDot);
                }
                multiCaretSupport.shiftSecondaryCarets(originalOffsets, delta);
            }
        }
        catch (BadLocationException ex)
        {
            // Ignore invalid offset scenarios
        }
    }

    private static int shiftCaretPosition(int pos, List<Integer> sortedPoints, int delta)
    {
        int shift = 0;
        for (int p : sortedPoints)
        {
            if (p < pos)
            {
                shift += delta;
            }
        }
        return Math.max(0, pos + shift);
    }
}
