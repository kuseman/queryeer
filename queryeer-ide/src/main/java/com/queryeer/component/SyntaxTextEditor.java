package com.queryeer.component;

import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

import java.awt.Color;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.fife.rsta.ui.GoToDialog;
import org.fife.rsta.ui.search.FindDialog;
import org.fife.rsta.ui.search.ReplaceDialog;
import org.fife.rsta.ui.search.SearchEvent;
import org.fife.rsta.ui.search.SearchListener;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SquiggleUnderlineHighlightPainter;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.TextEditorPane;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.fife.ui.rtextarea.SearchResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.queryeer.Constants;
import com.queryeer.api.component.ISyntaxTextEditor;
import com.queryeer.api.extensions.IExtensionAction;
import com.queryeer.api.extensions.IMainMenuAction;
import com.queryeer.api.extensions.IMainToolbarAction;
import com.queryeer.api.service.IEventBus;
import com.queryeer.event.CaretChangedEvent;

/** Implementation of {@link ISyntaxTextEditor} based on {@link RSyntaxTextArea} */
class SyntaxTextEditor extends RTextScrollPane implements ISyntaxTextEditor, PropertyChangeListener, SearchListener
{
    private static final String GOTO = "GOTO";
    private static final String REPLACE = "REPLACE";
    private static final String FIND = "FIND";
    private static final String PASTE_SPECIAL = "PASTE_SPECIAL";
    private static final String TOGGLE_COMMENTS = "TOGGLE_COMMENTS";

    private static final KeyStroke KS_PASTE_SPECIAL = KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK);
    private static final KeyStroke KS_FIND = KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK);
    private static final KeyStroke KS_REPLACE = KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK);
    private static final KeyStroke KS_GOTO_LINE = KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK);
    private static final KeyStroke KS_TOGGLE_COMMENTS = KeyStroke.getKeyStroke(KeyEvent.VK_7, InputEvent.CTRL_DOWN_MASK);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TextEditorPane textEditor;
    private final ISyntaxTextEditor.TextEditorModel model = new TextEditorModel();
    private final FindDialog findDialog;
    private final ReplaceDialog replaceDialog;
    private final GoToDialog gotoDialog;
    private final CaretChangedEvent caretChangeEvent = new CaretChangedEvent(model.getCaret());
    private List<IExtensionAction> editorActions;
    private boolean isUpdating = false;
    private IEventBus eventBus;

    SyntaxTextEditor(IEventBus eventBus, String preferredSyntax)
    {
        super(new TextEditorPane());
        this.eventBus = requireNonNull(eventBus, "eventBus");
        this.textEditor = (TextEditorPane) this.getViewport()
                .getView();
        textEditor.setSyntaxEditingStyle(preferredSyntax);
        textEditor.setCodeFoldingEnabled(true);
        textEditor.setBracketMatchingEnabled(true);
        this.model.addPropertyChangeListener(this);
        textEditor.getDocument()
                .addDocumentListener(new DocumentListener()
                {
                    @Override
                    public void insertUpdate(DocumentEvent e)
                    {
                        update();
                    }

                    @Override
                    public void removeUpdate(DocumentEvent e)
                    {
                        update();
                    }

                    @Override
                    public void changedUpdate(DocumentEvent e)
                    {
                        // update();
                    }

                    private void update()
                    {
                        if (isUpdating)
                        {
                            return;
                        }
                        isUpdating = true;
                        model.setText(textEditor.getText());
                        isUpdating = false;
                    }
                });
        textEditor.addCaretListener(evt ->
        {
            int length = textEditor.getSelectionEnd() - textEditor.getSelectionStart();
            model.setCaret(textEditor.getCaretLineNumber() + 1, textEditor.getCaretOffsetFromLineStart() + 1, textEditor.getCaretPosition(), textEditor.getSelectionStart(), length);
            eventBus.publish(caretChangeEvent);
        });

        JFrame topFrame = (JFrame) SwingUtilities.getWindowAncestor(this);

        findDialog = new FindDialog(topFrame, this);
        findDialog.setIconImages(Constants.APPLICATION_ICONS);
        replaceDialog = new ReplaceDialog(topFrame, this);
        replaceDialog.setIconImages(Constants.APPLICATION_ICONS);
        SearchContext context = findDialog.getSearchContext();
        replaceDialog.setSearchContext(context);
        gotoDialog = new GoToDialog(topFrame);
        gotoDialog.setIconImages(Constants.APPLICATION_ICONS);

        InputMap inputMap = textEditor.getInputMap(JComponent.WHEN_FOCUSED);
        inputMap.put(KS_PASTE_SPECIAL, PASTE_SPECIAL);
        inputMap.put(KS_FIND, FIND);
        inputMap.put(KS_REPLACE, REPLACE);
        inputMap.put(KS_GOTO_LINE, GOTO);
        inputMap.put(KS_TOGGLE_COMMENTS, TOGGLE_COMMENTS);

        textEditor.getActionMap()
                .put(PASTE_SPECIAL, pasteSpecialAction);
        textEditor.getActionMap()
                .put(FIND, showFindDialogAction);
        textEditor.getActionMap()
                .put(REPLACE, showReplaceDialogAction);
        textEditor.getActionMap()
                .put(GOTO, showGotoLineAction);
        textEditor.getActionMap()
                .put(TOGGLE_COMMENTS, toggleCommentsAction);
    }

    private final Action formatJsonAction = new AbstractAction("Format JSON (Selected text)")
    {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            try
            {
                Object value = MAPPER.readValue(model.getText(true), Object.class);

                String formatted = MAPPER.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(value);

                if (model.getCaret()
                        .getSelectionLength() > 0)
                {
                    int position = model.getCaret()
                            .getSelectionStart();
                    textEditor.getDocument()
                            .remove(position, model.getCaret()
                                    .getSelectionLength());
                    textEditor.getDocument()
                            .insertString(position, formatted, null);
                }
                else
                {
                    textEditor.setText(formatted);
                }
            }
            catch (Exception ex)
            {
                // SWALLOW
            }
        }
    };

    private final Action toggleCommentsAction = new AbstractAction("Toggle Comments", Constants.INDENT)
    {
        {
            putValue(TOOL_TIP_TEXT_KEY, "Toggle comment on selected lines");
            putValue(Action.ACCELERATOR_KEY, KS_TOGGLE_COMMENTS);
        }

        @Override
        public void actionPerformed(ActionEvent e)
        {
            toggleComments();
        }
    };

    // CSOFF
    private final Action pasteSpecialAction = new AbstractAction(PASTE_SPECIAL)
    // CSON
    {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            Clipboard clipboard = Toolkit.getDefaultToolkit()
                    .getSystemClipboard();
            DataFlavor[] flavors = clipboard.getAvailableDataFlavors();

            List<DataFlavor> applicableFlavors = new ArrayList<>();
            for (DataFlavor df : flavors)
            {
                if (String.class.equals(df.getRepresentationClass())
                        && (df.getPrimaryType()
                                .equals("text")
                                || df.getPrimaryType()
                                        .equals("plb")))
                {
                    applicableFlavors.add(df);
                }
            }

            if (applicableFlavors.isEmpty())
            {
                return;
            }

            String[] values = applicableFlavors.stream()
                    .map(f -> f.getHumanPresentableName())
                    .toArray(String[]::new);
            JFrame topFrame = (JFrame) SwingUtilities.getWindowAncestor(SyntaxTextEditor.this);
            Object selected = JOptionPane.showInputDialog(topFrame, "Paste special", "Select which type to paste", JOptionPane.QUESTION_MESSAGE, null, values, values[0]);
            if (selected != null)
            {
                // null if the user cancels.
                String selectedString = selected.toString();
                int index = ArrayUtils.indexOf(values, selectedString);
                if (index <= -1)
                {
                    return;
                }

                DataFlavor selectedFlavor = applicableFlavors.get(index);
                try
                {
                    Object data = clipboard.getData(selectedFlavor);
                    textEditor.getDocument()
                            .insertString(textEditor.getCaretPosition(), String.valueOf(data), null);
                }
                catch (Exception ee)
                {
                    // Swallow
                }
            }
        }
    };

    /** Show find */
    private final Action showFindDialogAction = new AbstractAction("Find", Constants.SEARCH)
    {
        {
            putValue(TOOL_TIP_TEXT_KEY, "Show Find Dialog");
            putValue(Action.ACCELERATOR_KEY, KS_FIND);
        }

        @Override
        public void actionPerformed(ActionEvent e)
        {
            if (replaceDialog.isVisible())
            {
                replaceDialog.setVisible(false);
            }
            findDialog.setVisible(true);
        }
    };

    /** Show replace */
    private final Action showReplaceDialogAction = new AbstractAction("Replace")
    {
        {
            putValue(TOOL_TIP_TEXT_KEY, "Show Replace Dialog");
            putValue(Action.ACCELERATOR_KEY, KS_REPLACE);
        }

        @Override
        public void actionPerformed(ActionEvent e)
        {
            if (findDialog.isVisible())
            {
                findDialog.setVisible(false);
            }
            replaceDialog.setVisible(true);
        }
    };

    /** Goto line */
    private final Action showGotoLineAction = new AbstractAction("GoTo Line", Constants.SHARE)
    {
        {
            putValue(TOOL_TIP_TEXT_KEY, "Show GoTO Line Dialog");
            putValue(Action.ACCELERATOR_KEY, KS_GOTO_LINE);
        }

        @Override
        public void actionPerformed(ActionEvent e)
        {
            if (findDialog.isVisible())
            {
                findDialog.setVisible(false);
            }
            if (replaceDialog.isVisible())
            {
                replaceDialog.setVisible(false);
            }
            gotoDialog.setMaxLineNumberAllowed(textEditor.getLineCount());
            gotoDialog.setVisible(true);
            int line = gotoDialog.getLineNumber();
            if (line > 0)
            {
                try
                {
                    textEditor.setCaretPosition(textEditor.getLineStartOffset(line - 1));
                }
                catch (BadLocationException ble)
                { // Never
                  // happens
                    UIManager.getLookAndFeel()
                            .provideErrorFeedback(textEditor);
                    ble.printStackTrace();
                }
            }
        }
    };

    @Override
    public boolean requestFocusInWindow()
    {
        // Publish a caret event when this component got focus since there is no event fired from text editor
        // when switching tab's
        eventBus.publish(caretChangeEvent);
        return textEditor.requestFocusInWindow();
    }

    @Override
    public String getSelectedText()
    {
        return textEditor.getSelectedText();
    }

    @Override
    public void searchEvent(SearchEvent e)
    {
        SearchEvent.Type type = e.getType();
        SearchContext context = e.getSearchContext();
        SearchResult result;

        switch (type)
        {
            default:
            case MARK_ALL:
                result = SearchEngine.markAll(textEditor, context);
                break;
            case FIND:
                result = SearchEngine.find(textEditor, context);
                if (!result.wasFound()
                        || result.isWrapped())
                {
                    UIManager.getLookAndFeel()
                            .provideErrorFeedback(textEditor);
                }
                break;
            case REPLACE:
                result = SearchEngine.replace(textEditor, context);
                if (!result.wasFound()
                        || result.isWrapped())
                {
                    UIManager.getLookAndFeel()
                            .provideErrorFeedback(textEditor);
                }
                break;
            case REPLACE_ALL:
                result = SearchEngine.replaceAll(textEditor, context);
                JOptionPane.showMessageDialog(null, result.getCount() + " occurrences replaced.");
                break;
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt)
    {
        if (!isUpdating
                && TextEditorModel.TEXT.equals(evt.getPropertyName()))
        {
            textEditor.setText((String) evt.getNewValue());
        }
    }

    @Override
    public void close()
    {
        model.removePropertyChangeListener(this);
    }

    @Override
    public List<IExtensionAction> getActions()
    {
        if (editorActions == null)
        {
            List<IExtensionAction> extensionActions = new ArrayList<>();
            extensionActions.add(IMainMenuAction.menuAction(100, showFindDialogAction, "Edit"));
            extensionActions.add(IMainMenuAction.menuAction(200, showReplaceDialogAction, "Edit"));
            extensionActions.add(IMainMenuAction.menuAction(300, showGotoLineAction, "Edit"));
            extensionActions.add(IMainMenuAction.menuAction(100, toggleCommentsAction, "Edit/Format"));
            extensionActions.add(IMainMenuAction.menuAction(200, formatJsonAction, "Edit/Format"));

            // Show toggle in toolbar
            extensionActions.add(IMainToolbarAction.toolbarAction(10000, toggleCommentsAction));

            editorActions = unmodifiableList(extensionActions);
        }
        return editorActions;
    }

    @Override
    public Component getComponent()
    {
        return this;
    }

    @Override
    public void setParseErrorLocation(int line, int column)
    {
        try
        {
            int pos = Math.max(textEditor.getLineStartOffset(line - 1) + column - 1, 0);
            textEditor.getHighlighter()
                    .addHighlight(pos, pos + 3, new SquiggleUnderlineHighlightPainter(Color.RED));
            textEditor.repaint();
        }
        catch (BadLocationException e)
        {
        }
    }

    @Override
    public void clearErrors()
    {
        int selStart = textEditor.getSelectionStart();
        int selEnd = textEditor.getSelectionEnd();

        textEditor.getHighlighter()
                .removeAllHighlights();

        textEditor.setSelectionStart(selStart);
        textEditor.setSelectionEnd(selEnd);
    }

    @Override
    public TextEditorModel getModel()
    {
        return model;
    }

    /** Return true or false depending on if value is between start and end. Both inclusive */
    private boolean between(int start, int end, int value)
    {
        return value >= start
                && value <= end;
    }

    /**
     * <pre>
     * Toggle comments on selected lines
     * </pre>
     **/
    private void toggleComments()
    {
        // Only SQL supported for now
        if (!SyntaxConstants.SYNTAX_STYLE_SQL.equalsIgnoreCase(textEditor.getSyntaxEditingStyle()))
        {
            return;
        }

        int lines = textEditor.getLineCount();

        int selStart = textEditor.getSelectionStart();
        int selEnd = textEditor.getSelectionEnd();
        boolean caretSelection = selEnd - selStart == 0;
        Boolean addComments = null;

        try
        {
            List<MutableInt> startOffsets = new ArrayList<>();

            for (int i = 0; i < lines; i++)
            {
                int startOffset = textEditor.getLineStartOffset(i);
                int endOffset = textEditor.getLineEndOffset(i) - 1;

                if (between(startOffset, endOffset, selStart)
                        || (startOffset > selStart
                                && endOffset <= selEnd)
                        || between(startOffset, endOffset, selEnd))
                {
                    if (addComments == null)
                    {
                        addComments = !"--".equals(textEditor.getText(startOffset, 2));
                    }

                    startOffsets.add(new MutableInt(startOffset));
                }
            }

            if (!startOffsets.isEmpty())
            {
                int modifier = 0;
                for (MutableInt startOffset : startOffsets)
                {
                    if (addComments)
                    {
                        textEditor.getDocument()
                                .insertString(startOffset.getValue() + modifier, "--", null);
                    }
                    else
                    {
                        textEditor.getDocument()
                                .remove(startOffset.getValue() + modifier, 2);
                    }
                    startOffset.setValue(Math.max(startOffset.getValue() + modifier, 0));
                    modifier += addComments ? 2
                            : -2;
                }

                selStart = startOffsets.get(0)
                        .getValue();
                if (!caretSelection)
                {
                    selEnd = startOffsets.get(startOffsets.size() - 1)
                            .getValue();
                }
                else
                {
                    selEnd = selStart;
                }
            }

        }
        catch (BadLocationException e)
        {
        }

        textEditor.setSelectionStart(selStart);
        textEditor.setSelectionEnd(selEnd);
    }
}
