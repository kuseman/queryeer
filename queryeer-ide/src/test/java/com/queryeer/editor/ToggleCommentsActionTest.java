package com.queryeer.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.Action;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import com.queryeer.api.action.Constants;
import com.queryeer.api.editor.ITextEditorKit;

/** Test of {@link ToggleCommentsAction}. */
class ToggleCommentsActionTest
{
    @Test
    void test_toggle_comments_selection_including_last_line_without_newline() throws InvocationTargetException, InterruptedException
    {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Requires Swing UI environment");

        TextEditor editor = createEditor();
        try
        {
            runOnEdt(() -> editor.setValue("line1\n \nx"));
            runOnEdt(() -> editor.textEditor.select(6, ((String) editor.getValue(true)).length()));

            runOnEdt(() -> getToggleCommentAction(editor).actionPerformed(new ActionEvent(editor.getComponent(), ActionEvent.ACTION_PERFORMED, "toggleComments")));

            assertEquals("line1\n \n--x", runOnEdt(() -> (String) editor.getValue(true)));
        }
        finally
        {
            runOnEdt(editor::close);
        }
    }

    @Test
    void test_toggle_comments_twice_keeps_same_line_range() throws InvocationTargetException, InterruptedException
    {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Requires Swing UI environment");

        TextEditor editor = createEditor();
        try
        {
            runOnEdt(() -> editor.setValue("one\ntwo\nthree"));
            runOnEdt(() -> editor.textEditor.select(0, ((String) editor.getValue(true)).length()));

            Action toggle = getToggleCommentAction(editor);
            runOnEdt(() -> toggle.actionPerformed(new ActionEvent(editor.getComponent(), ActionEvent.ACTION_PERFORMED, "toggleComments")));
            runOnEdt(() -> toggle.actionPerformed(new ActionEvent(editor.getComponent(), ActionEvent.ACTION_PERFORMED, "toggleComments")));

            assertEquals("one\ntwo\nthree", runOnEdt(() -> (String) editor.getValue(true)));
        }
        finally
        {
            runOnEdt(editor::close);
        }
    }

    private static TextEditor createEditor()
    {
        return new TextEditor(new ITextEditorKit()
        {
        });
    }

    @SuppressWarnings("unchecked")
    private static Action getToggleCommentAction(TextEditor editor)
    {
        List<Action> actions = (List<Action>) editor.getComponent()
                .getClientProperty(Constants.QUERYEER_ACTIONS);
        assertNotNull(actions);
        return actions.stream()
                .filter(a -> "toggleComments".equals(a.getValue(Action.ACTION_COMMAND_KEY)))
                .findFirst()
                .orElseThrow();
    }

    private static void runOnEdt(Runnable runnable) throws InvocationTargetException, InterruptedException
    {
        if (SwingUtilities.isEventDispatchThread())
        {
            runnable.run();
            return;
        }
        SwingUtilities.invokeAndWait(runnable);
    }

    private static <T> T runOnEdt(ThrowingSupplier<T> supplier) throws InvocationTargetException, InterruptedException
    {
        if (SwingUtilities.isEventDispatchThread())
        {
            try
            {
                return supplier.get();
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }

        AtomicReference<T> ref = new AtomicReference<>();
        AtomicReference<Exception> ex = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                ref.set(supplier.get());
            }
            catch (Exception e)
            {
                ex.set(e);
            }
        });

        if (ex.get() != null)
        {
            throw new RuntimeException(ex.get());
        }
        return ref.get();
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T>
    {
        T get() throws Exception;
    }
}
