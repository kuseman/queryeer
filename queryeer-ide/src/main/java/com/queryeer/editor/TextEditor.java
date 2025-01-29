package com.queryeer.editor;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedWriter;
import java.io.CharArrayReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.Segment;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.commons.lang3.mutable.MutableInt;
import org.fife.io.UnicodeWriter;
import org.fife.rsta.ui.GoToDialog;
import org.fife.rsta.ui.search.FindDialog;
import org.fife.rsta.ui.search.ReplaceDialog;
import org.fife.rsta.ui.search.SearchEvent;
import org.fife.rsta.ui.search.SearchListener;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.CompletionCellRenderer;
import org.fife.ui.autocomplete.CompletionProviderBase;
import org.fife.ui.autocomplete.ParameterizedCompletion;
import org.fife.ui.rsyntaxtextarea.ErrorStrip;
import org.fife.ui.rsyntaxtextarea.FileLocation;
import org.fife.ui.rsyntaxtextarea.LinkGenerator;
import org.fife.ui.rsyntaxtextarea.LinkGeneratorResult;
import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SquiggleUnderlineHighlightPainter;
import org.fife.ui.rsyntaxtextarea.TextEditorPane;
import org.fife.ui.rsyntaxtextarea.parser.AbstractParser;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParseResult;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParserNotice;
import org.fife.ui.rsyntaxtextarea.parser.ParseResult;
import org.fife.ui.rsyntaxtextarea.parser.Parser;
import org.fife.ui.rsyntaxtextarea.parser.ParserNotice;
import org.fife.ui.rtextarea.RTextArea;
import org.fife.ui.rtextarea.RTextAreaBase;
import org.fife.ui.rtextarea.RTextAreaEditorKit;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.fife.ui.rtextarea.SearchResult;
import org.fife.ui.rtextarea.ToolTipSupplier;
import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.kordamp.ikonli.swing.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.queryeer.Constants;
import com.queryeer.api.action.ActionUtils;
import com.queryeer.api.component.ADocumentListenerAdapter;
import com.queryeer.api.component.DialogUtils;
import com.queryeer.api.editor.IEditor;
import com.queryeer.api.editor.ITextEditor;
import com.queryeer.api.editor.ITextEditorDocumentParser;
import com.queryeer.api.editor.ITextEditorDocumentParser.CompletionItem;
import com.queryeer.api.editor.ITextEditorDocumentParser.CompletionResult;
import com.queryeer.api.editor.ITextEditorDocumentParser.ParseItem;
import com.queryeer.api.editor.ITextEditorDocumentParser.ToolTipItem;
import com.queryeer.api.editor.ITextEditorKit;
import com.queryeer.api.editor.TextSelection;
import com.queryeer.api.service.IEventBus;
import com.queryeer.domain.Caret;
import com.queryeer.event.CaretChangedEvent;

/** Text editor implemented with {@link RSyntaxTextArea} */
class TextEditor implements ITextEditor, SearchListener
{
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(5, new BasicThreadFactory.Builder().daemon(true)
            .namingPattern("TextEditor-Parser#%d")
            .build());

    private static final Logger LOGGER = LoggerFactory.getLogger(TextEditor.class);

    private static final Icon SEARCH = FontIcon.of(FontAwesome.SEARCH);
    private static final Icon SHARE = FontIcon.of(FontAwesome.SHARE);
    private static final KeyStroke PASTE_SPECIAL_STROKE = KeyStroke.getKeyStroke(KeyEvent.VK_V, Toolkit.getDefaultToolkit()
            .getMenuShortcutKeyMaskEx() | InputEvent.SHIFT_DOWN_MASK);

    private final IEventBus eventBus;
    final ITextEditorKit editorKit;

    private final Panel panel;

    final TextEditorPane textEditor;
    private final ErrorStrip errorStrip;
    private final RTextScrollPane scrollPane;
    private final FindDialog findDialog;
    private final ReplaceDialog replaceDialog;
    private final GoToDialog gotoDialog;
    private final PasteSpecialDialog pasteSpecialDialog;

    /** Reuse the same event to avoid creating new objects on every change */
    private final CaretChangedEvent caretEvent = new CaretChangedEvent(new Caret());
    private final List<PropertyChangeListener> propertyChangeListeners = new ArrayList<>();

    private EditorParser parser;
    private EditorCompleter completer;

    TextEditor(IEventBus eventBus, ITextEditorKit editorKit)
    {
        this.eventBus = eventBus;
        this.editorKit = requireNonNull(editorKit, "editorKit");

        textEditor = new TextEditorPane();
        textEditor.setColumns(editorKit.getColumns());
        textEditor.setRows(editorKit.getRows());
        textEditor.setCodeFoldingEnabled(true);
        textEditor.setBracketMatchingEnabled(true);
        textEditor.setTabSize(2);
        textEditor.setTabsEmulated(true);

        // Unbind F2 since we use that to show quick datasources
        textEditor.getInputMap()
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "none");
        // Unbint CTRL/META+SHIFT+V since we use that for paste special
        textEditor.getInputMap()
                .put(PASTE_SPECIAL_STROKE, "none");

        scrollPane = new RTextScrollPane(textEditor, true);
        errorStrip = new ErrorStrip(textEditor);

        findDialog = new FindDialog((Frame) null, this)
        {
            @Override
            public void setVisible(boolean b)
            {
                Window activeWindow = javax.swing.FocusManager.getCurrentManager()
                        .getActiveWindow();

                if (b)
                {
                    setLocationRelativeTo(activeWindow);
                }
                super.setVisible(b);
            }
        };
        findDialog.setIconImages(Constants.APPLICATION_ICONS);
        replaceDialog = new ReplaceDialog((Frame) null, this)
        {
            @Override
            public void setVisible(boolean b)
            {
                Window activeWindow = javax.swing.FocusManager.getCurrentManager()
                        .getActiveWindow();

                if (b)
                {
                    setLocationRelativeTo(activeWindow);
                }
                super.setVisible(b);
            }
        };
        replaceDialog.setIconImages(Constants.APPLICATION_ICONS);
        SearchContext context = findDialog.getSearchContext();
        replaceDialog.setSearchContext(context);
        gotoDialog = new GoToDialog((Frame) null)
        {
            @Override
            public void setVisible(boolean b)
            {
                Window activeWindow = javax.swing.FocusManager.getCurrentManager()
                        .getActiveWindow();

                if (b)
                {
                    setLocationRelativeTo(activeWindow);
                }
                super.setVisible(b);
            }
        };
        gotoDialog.setIconImages(Constants.APPLICATION_ICONS);

        pasteSpecialDialog = new PasteSpecialDialog();

        textEditor.addCaretListener(evt -> publishCaret());
        textEditor.addPropertyChangeListener(new PropertyChangeListener()
        {
            @Override
            public void propertyChange(PropertyChangeEvent evt)
            {
                if (TextEditorPane.DIRTY_PROPERTY.equals(evt.getPropertyName()))
                {
                    int size = propertyChangeListeners.size();
                    for (int i = size - 1; i >= 0; i--)
                    {
                        propertyChangeListeners.get(i)
                                .propertyChange(new PropertyChangeEvent(textEditor, IEditor.DIRTY, evt.getOldValue(), evt.getNewValue()));
                    }
                }
            }
        });

        // Set parsing state when document changes to avoid fetching completion items for the wrong state etc.
        textEditor.getDocument()
                .addDocumentListener(new ADocumentListenerAdapter()
                {
                    @Override
                    protected void update()
                    {
                        if (parser != null)
                        {
                            parser.state = EditorParser.State.PARSING;
                        }

                        int size = propertyChangeListeners.size();
                        for (int i = size - 1; i >= 0; i--)
                        {
                            propertyChangeListeners.get(i)
                                    .propertyChange(new PropertyChangeEvent(textEditor, IEditor.VALUE_CHANGED, null, null));
                        }
                    }
                });

        //@formatter:off
        List<Action> actions = new ArrayList<>();
        actions.addAll(editorKit.getActions());
        actions.addAll(asList(
                toggleShowWhiteSpaceAction,
                toggleCommentAction,
                showFindDialogAction,
                showReplaceDialogAction,
                showGotoLineAction,
                pasteSpecialAction,
                lowerCaseSelection,
                upperCaseSelection
                ));
        //@formatter:on

        this.panel = new Panel(actions);
    }

    private class Panel extends JPanel
    {
        Panel(List<Action> actions)
        {
            setLayout(new BorderLayout());

            installEditorKit();

            putClientProperty(com.queryeer.api.action.Constants.QUERYEER_ACTIONS, actions);

            add(scrollPane);
            add(errorStrip, BorderLayout.LINE_END);
        }
    }

    private void installEditorKit()
    {
        textEditor.setSyntaxEditingStyle(editorKit.getSyntaxMimeType());
        ITextEditorDocumentParser parser = editorKit.getDocumentParser();
        if (parser != null)
        {
            this.parser = new EditorParser(this, parser);
            textEditor.addParser(this.parser);

            if (parser.supportsToolTips())
            {
                textEditor.setToolTipSupplier(this.parser);
            }
            if (parser.supportsLinkActions())
            {
                textEditor.setLinkGenerator(this.parser);
            }

            if (parser.supportsCompletions())
            {
                completer = new EditorCompleter(this.parser);
                AutoCompletion autoCompletion = new AutoCompletion(completer)
                {
                    @Override
                    protected void insertCompletion(Completion c, boolean typedParamListStartChar)
                    {
                        // Never insert the parsing completion
                        if (c == completer.parsingCompletion)
                        {
                            return;
                        }
                        super.insertCompletion(c, typedParamListStartChar);
                    }
                };
                autoCompletion.setListCellRenderer(new CompletionCellRenderer());
                autoCompletion.setShowDescWindow(true);
                autoCompletion.install(textEditor);
                completer.setAutoCompletion(autoCompletion);
                this.parser.setParseCompleteListener(completer.parseCompleteRunner);
            }

            // Force a parsing after we installed the kit
            textEditor.forceReparsing(this.parser);
        }
    }

    // IEditor

    @Override
    public void clearBeforeExecution()
    {
        int selStart = textEditor.getSelectionStart();
        int selEnd = textEditor.getSelectionEnd();

        textEditor.getHighlighter()
                .removeAllHighlights();

        if (selEnd - selStart > 0)
        {
            textEditor.setSelectionStart(selStart);
            textEditor.setSelectionEnd(selEnd);
        }
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener)
    {
        propertyChangeListeners.add(listener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener)
    {
        propertyChangeListeners.remove(listener);
    }

    @Override
    public JComponent getComponent()
    {
        return panel;
    }

    @Override
    public void loadFromFile(File file)
    {
        try
        {
            int caretPos = textEditor.getCaretPosition();
            textEditor.load(FileLocation.create(file), StandardCharsets.UTF_8);
            if (textEditor.getDocument()
                    .getLength() > caretPos)
            {
                textEditor.setCaretPosition(caretPos);
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException("Error loading file: " + file, e);
        }
    }

    @Override
    public void saveToFile(File file, boolean notifyDirty)
    {
        try
        {
            if (notifyDirty)
            {
                textEditor.saveAs(FileLocation.create(file));
            }
            else
            {
                try (FileOutputStream out = new FileOutputStream(file); BufferedWriter w = new BufferedWriter(new UnicodeWriter(out, textEditor.getEncoding())))
                {
                    textEditor.write(w);
                }
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException("Error saving file: " + file, e);
        }
    }

    @Override
    public void setDirty(boolean dirty)
    {
        textEditor.setDirty(dirty);
    }

    @Override
    public void focused()
    {
        publishCaret();
        textEditor.requestFocusInWindow();
    }

    // ITextEditor

    @Override
    public void parse()
    {
        if (this.parser != null)
        {
            textEditor.forceReparsing(this.parser);
            // Clear cached completions
            if (completer != null)
            {
                completer.cachedCompletionsOffset = -1;
                completer.cachedCompletions = null;
            }
            if (parser != null)
            {
                parser.currentLinkAction = null;
                parser.currentToolTip = null;
            }
        }
    }

    @Override
    public ITextEditorKit getEditorKit()
    {
        return editorKit;
    }

    @Override
    public void select(TextSelection textSelection)
    {
        if (textSelection.isEmpty())
        {
            return;
        }

        textEditor.select(textSelection.start(), textSelection.end());
    }

    @Override
    public TextSelection translate(TextSelection selection)
    {
        if (selection == null)
        {
            return TextSelection.EMPTY;
        }

        int selectionStart = 0;
        if (textEditor.getSelectionEnd() - textEditor.getSelectionStart() > 0)
        {
            selectionStart = textEditor.getSelectionStart();
        }

        int start = selectionStart + selection.start();
        int end = selectionStart + selection.end();
        return new TextSelection(start, end);
    }

    @Override
    public void highlight(TextSelection selection, Color color)
    {
        Runnable r = () ->
        {
            try
            {
                // Clear selection before highlighting and re-add it after
                int selStart = textEditor.getSelectionStart();
                int selEnd = textEditor.getSelectionEnd();
                if (selEnd - selStart > 0)
                {
                    textEditor.select(0, 0);
                }

                textEditor.getHighlighter()
                        .addHighlight(selection.start(), selection.end(), new SquiggleUnderlineHighlightPainter(color));

                if (selEnd - selStart > 0)
                {
                    textEditor.select(selStart, selEnd);
                }
            }
            catch (BadLocationException e)
            {
            }
        };

        if (SwingUtilities.isEventDispatchThread())
        {
            r.run();
        }
        else
        {
            SwingUtilities.invokeLater(r);
        }
    }

    @Override
    public Object getValue(boolean raw)
    {
        if (raw)
        {
            return textEditor.getText();
        }

        // Default value for a text editor is the selected text if any else everything
        String result = textEditor.getSelectedText();
        if (isBlank(result))
        {
            result = textEditor.getText();
        }
        return result;
    }

    @Override
    public void setValue(Object value)
    {
        textEditor.setText(String.valueOf(value));
        textEditor.setCaretPosition(0);
    }

    @Override
    public boolean isValueEmpty()
    {
        return StringUtils.isBlank((String) getValue(false));
    }

    // SearchListener

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
                JOptionPane.showMessageDialog(textEditor, result.getCount() + " occurrences replaced.");
                break;
        }
    }

    /**
     * <pre>
     * Toggle comments on selected lines
     * </pre>
     **/
    private void toggleComments()
    {
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

    private void publishCaret()
    {
        caretEvent.getCaret()
                .setLineNumber(textEditor.getCaretLineNumber() + 1);
        caretEvent.getCaret()
                .setOffset(textEditor.getCaretOffsetFromLineStart() + 1);
        caretEvent.getCaret()
                .setPosition(textEditor.getCaretPosition());
        eventBus.publish(caretEvent);
    }

    private Action lowerCaseSelection = new RTextAreaEditorKit.LowerSelectionCaseAction()
    {
        {
            putValue(Action.NAME, "Lowercase Selection");
            putValue(com.queryeer.api.action.Constants.ACTION_SHOW_IN_MENU, true);
            putValue(com.queryeer.api.action.Constants.ACTION_MENU, com.queryeer.api.action.Constants.EDIT_MENU);
            putValue(com.queryeer.api.action.Constants.ACTION_ORDER, 4);
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_Y, Toolkit.getDefaultToolkit()
                    .getMenuShortcutKeyMaskEx() | InputEvent.SHIFT_DOWN_MASK));
            putValue(Action.ACTION_COMMAND_KEY, "lowercaseSelection");
        }
    };

    private Action upperCaseSelection = new RTextAreaEditorKit.UpperSelectionCaseAction()
    {
        {
            putValue(Action.NAME, "Uppercase Selection");
            putValue(com.queryeer.api.action.Constants.ACTION_SHOW_IN_MENU, true);
            putValue(com.queryeer.api.action.Constants.ACTION_MENU, com.queryeer.api.action.Constants.EDIT_MENU);
            putValue(com.queryeer.api.action.Constants.ACTION_ORDER, 5);
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_X, Toolkit.getDefaultToolkit()
                    .getMenuShortcutKeyMaskEx() | InputEvent.SHIFT_DOWN_MASK));
            putValue(Action.ACTION_COMMAND_KEY, "uppercaseSelection");
        }
    };

    private Action toggleShowWhiteSpaceAction = new AbstractAction("", FontIcon.of(FontAwesome.PARAGRAPH))
    {
        {
            putValue(com.queryeer.api.action.Constants.ACTION_SHOW_IN_TOOLBAR, true);
            putValue(com.queryeer.api.action.Constants.ACTION_TOGGLE, true);
            putValue(com.queryeer.api.action.Constants.ACTION_ORDER, 9);
            putValue(Action.SHORT_DESCRIPTION, "Toggle Visible Whitespace Characters");
        }

        @Override
        public void actionPerformed(ActionEvent e)
        {
            textEditor.setWhitespaceVisible(!textEditor.isWhitespaceVisible());
            textEditor.setEOLMarkersVisible(!textEditor.getEOLMarkersVisible());
        }
    };

    private Action toggleCommentAction = new AbstractAction("", FontIcon.of(FontAwesome.INDENT))
    {
        {
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
    };

    private final Action pasteSpecialAction = new AbstractAction("Paste Special ...")
    {
        {
            putValue(com.queryeer.api.action.Constants.ACTION_SHOW_IN_MENU, true);
            putValue(com.queryeer.api.action.Constants.ACTION_MENU, com.queryeer.api.action.Constants.EDIT_MENU);
            putValue(com.queryeer.api.action.Constants.ACTION_ORDER, 3);
            putValue(Action.ACCELERATOR_KEY, PASTE_SPECIAL_STROKE);
            putValue(Action.ACTION_COMMAND_KEY, "pasteSpecial");
        }

        @Override
        public void actionPerformed(ActionEvent e)
        {
            pasteSpecialDialog.setVisible(true);
        }
    };

    /** Show find */
    private final Action showFindDialogAction = new AbstractAction("Find ...", SEARCH)
    {
        {
            putValue(com.queryeer.api.action.Constants.ACTION_SHOW_IN_MENU, true);
            putValue(com.queryeer.api.action.Constants.ACTION_MENU, com.queryeer.api.action.Constants.EDIT_MENU);
            putValue(com.queryeer.api.action.Constants.ACTION_ORDER, 0);
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_F, Toolkit.getDefaultToolkit()
                    .getMenuShortcutKeyMaskEx()));
            putValue(Action.ACTION_COMMAND_KEY, "find");
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
    private final Action showReplaceDialogAction = new AbstractAction("Replace ...")
    {
        {
            putValue(com.queryeer.api.action.Constants.ACTION_SHOW_IN_MENU, true);
            putValue(com.queryeer.api.action.Constants.ACTION_MENU, com.queryeer.api.action.Constants.EDIT_MENU);
            putValue(com.queryeer.api.action.Constants.ACTION_ORDER, 1);
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_H, Toolkit.getDefaultToolkit()
                    .getMenuShortcutKeyMaskEx()));
            putValue(Action.ACTION_COMMAND_KEY, "replace");
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
    private final Action showGotoLineAction = new AbstractAction("GoTo Line ...", SHARE)
    {
        {
            putValue(com.queryeer.api.action.Constants.ACTION_SHOW_IN_MENU, true);
            putValue(com.queryeer.api.action.Constants.ACTION_MENU, com.queryeer.api.action.Constants.EDIT_MENU);
            putValue(com.queryeer.api.action.Constants.ACTION_ORDER, 2);
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_L, Toolkit.getDefaultToolkit()
                    .getMenuShortcutKeyMaskEx()));
            putValue(Action.ACTION_COMMAND_KEY, "goto");
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

    private static class EditorCompleter extends CompletionProviderBase
    {
        private final EditorParser parser;
        private final BasicCompletion parsingCompletion = new BasicCompletion(this, "Parsing ...");
        private AutoCompletion autoCompletion;

        /**
         * Cached completions that is used as long as the document is dirty and user keeps typing and start offset is the same.
         *
         * <pre>
         * select *
         * from | <--- caret
         * 
         * if completions dialog is popped here we ask the parser for completions at that index
         * and we reuse that list for as long as we have the same cached offset as before when entered text
         * length removed.
         * 
         * We will use the same list of completions as above for this but filtered:
         * 
         * select *
         * from art| <--- caret
         *
         * </pre>
         */
        private int cachedCompletionsOffset = -1;
        private List<CompletionItem> cachedCompletions;

        private boolean doAutoCompleteWhenReady = false;

        EditorCompleter(EditorParser parser)
        {
            this.parser = parser;
        }

        void setAutoCompletion(AutoCompletion autoCompletion)
        {
            this.autoCompletion = autoCompletion;
        }

        @Override
        public String getAlreadyEnteredText(JTextComponent comp)
        {
            Document doc = comp.getDocument();

            int dot = comp.getCaretPosition();
            Element root = doc.getDefaultRootElement();
            int index = root.getElementIndex(dot);
            Element elem = root.getElement(index);
            int start = elem.getStartOffset();
            int len = dot - start;
            try
            {
                doc.getText(start, len, parser.seg);
            }
            catch (BadLocationException ble)
            {
                ble.printStackTrace();
                return EMPTY_STRING;
            }

            int segEnd = parser.seg.offset + len;
            start = segEnd - 1;
            while (start >= parser.seg.offset
                    && isValidChar(parser.seg.array[start]))
            {
                start--;
            }
            start++;

            len = segEnd - start;
            return len == 0 ? EMPTY_STRING
                    : new String(parser.seg.array, start, len);
        }

        private Runnable parseCompleteRunner = new Runnable()
        {
            @Override
            public void run()
            {
                if (doAutoCompleteWhenReady)
                {
                    doAutoCompleteWhenReady = false;
                    SwingUtilities.invokeLater(() ->
                    {
                        if (autoCompletion.isPopupVisible())
                        {
                            autoCompletion.hideChildWindows();
                        }
                        autoCompletion.doCompletion();
                    });
                }
            }
        };

        @Override
        public List<Completion> getCompletionsAt(JTextComponent comp, Point p)
        {
            return null;
        }

        @Override
        public List<ParameterizedCompletion> getParameterizedCompletions(JTextComponent tc)
        {
            return null;
        }

        @Override
        protected List<Completion> getCompletionsImpl(JTextComponent comp)
        {
            int offset = comp.getCaretPosition();
            String enteredText = getAlreadyEnteredText(comp);
            int enteredTextLength = StringUtils.length(enteredText);

            // Filter and reuse cached completions
            if (cachedCompletionsOffset >= 0
                    && cachedCompletions != null
                    && cachedCompletionsOffset == (offset - enteredTextLength))
            {
                return getCompletions(getCompletionByInputText(cachedCompletions, enteredText));
            }

            // Ongoing parsing, return null
            if (parser.state == EditorParser.State.PARSING)
            {
                // parser.editor.textEditor.forceReparsing(parser);
                doAutoCompleteWhenReady = true;
                cachedCompletionsOffset = -1;
                cachedCompletions = null;
                return new ArrayList<>(List.of(parsingCompletion));
            }

            CompletionResult result = parser.parser.getCompletionItems(offset);
            if (result == null)
            {
                cachedCompletionsOffset = -1;
                cachedCompletions = null;
                return emptyList();
            }

            List<CompletionItem> completionItems = result.getItems();
            if (result.isPartialResult())
            {
                cachedCompletions = null;
                cachedCompletionsOffset = -1;
            }
            else
            {
                cachedCompletions = completionItems;
                cachedCompletionsOffset = offset - enteredTextLength;
            }

            if (!isBlank(enteredText))
            {
                completionItems = getCompletionByInputText(completionItems, enteredText);
            }

            return getCompletions(completionItems);
        }

        private List<Completion> getCompletions(List<CompletionItem> items)
        {
            int size = items.size();
            List<Completion> completions = new ArrayList<>(size);
            for (int i = 0; i < size; i++)
            {
                CompletionItem item = items.get(i);
                BasicCompletion completion = new BasicCompletion(this, item.getReplacementText(), item.getShortDesc(), item.getSummary());
                completion.setRelevance(item.getRelevance());
                completion.setIcon(item.getIcon());
                completions.add(completion);
            }
            return completions;
        }

        private boolean isValidChar(char ch)
        {
            return Character.isLetterOrDigit(ch)
                    || ch == '_'
                    || ch == '.'
                    || ch == '#';
        }

        private List<CompletionItem> getCompletionByInputText(List<CompletionItem> completionItems, String inputText)
        {
            List<CompletionItem> result = null;
            int inputLength = inputText.length();
            for (CompletionItem item : completionItems)
            {
                int size = item.getMatchParts()
                        .size();

                boolean anyPartMatch = false;
                // Try to match on single parts
                for (int i = 0; i < size; i++)
                {
                    String string = item.getMatchParts()
                            .get(i);

                    // Skip parts that obviously won't match
                    if (string.length() < inputText.length())
                    {
                        continue;
                    }

                    // int length = Math.min(string.length(), inputLength);

                    int ch1Index = 0;
                    int ch2Index = 0;

                    boolean match = true;
                    // Compare the prefix of current qualified item
                    while (ch1Index < string.length()
                            && ch2Index < inputLength)
                    {
                        char ch1 = Character.toLowerCase(string.charAt(ch1Index));
                        if (ch1 == '_'
                                || ch1 == '.'
                                || ch1 == '#')
                        {
                            ch1Index++;
                            continue;
                        }

                        char ch2 = Character.toLowerCase(inputText.charAt(ch2Index));
                        if (ch2 == '_'
                                || ch2 == '.'
                                || ch2 == '#')
                        {
                            ch2Index++;
                            continue;
                        }

                        ch1Index++;
                        ch2Index++;
                        int ch = Character.compare(ch1, ch2);
                        if (ch != 0)
                        {
                            match = false;
                            break;
                        }

                    }

                    if (match)
                    {
                        if (result == null)
                        {
                            result = new ArrayList<>();
                        }
                        result.add(item);
                        anyPartMatch = true;
                        break;
                    }
                }

                // No match on parts, try to match the replacement text
                if (!anyPartMatch
                        && StringUtils.startsWithIgnoreCase(item.getReplacementText(), inputText))
                {
                    if (result == null)
                    {
                        result = new ArrayList<>();
                    }
                    result.add(item);
                }
            }

            return result == null ? emptyList()
                    : result;
        }
    }

    /** Adapter for bridging {@link ITextEditorDocumentParser} and {@link Parser}, {@link ToolTipSupplier}, {@link LinkGenerator} */
    private static class EditorParser extends AbstractParser implements ToolTipSupplier, LinkGenerator
    {
        private final TextEditor editor;
        private final ITextEditorDocumentParser parser;
        private final Segment seg = new Segment();
        private final JPopupMenu linkActionsPopup = new JPopupMenu();
        private Runnable parseCompleteListener;

        /** Current references of link/tooltip. This is used to avoid looking up more than once when the mouse are over the same token */
        private ITextEditorDocumentParser.LinkAction currentLinkAction;
        private ITextEditorDocumentParser.ToolTipItem currentToolTip;

        private Future<?> currentSession;

        private volatile State state = null;
        private volatile DefaultParseResult parseResult = new DefaultParseResult(this);

        EditorParser(TextEditor editor, ITextEditorDocumentParser parser)
        {
            this.editor = editor;
            this.parser = parser;
            if (parser.supportsLinkActions())
            {
                this.editor.textEditor.addMouseMotionListener(hideLinkActionsListener);
            }
        }

        void setParseCompleteListener(Runnable parseCompleteListener)
        {
            this.parseCompleteListener = parseCompleteListener;
        }

        private MouseMotionListener hideLinkActionsListener = new MouseAdapter()
        {
            private static final int LINK_MASK = RTextAreaBase.isOSX() ? InputEvent.META_DOWN_MASK
                    : InputEvent.CTRL_DOWN_MASK;

            @Override
            public void mouseMoved(MouseEvent e)
            {
                // Hide popup if we're not looking for links anymore
                if (linkActionsPopup.isVisible()
                        && !((e.getModifiersEx() & LINK_MASK) == LINK_MASK))
                {
                    linkActionsPopup.setVisible(false);
                }
            }
        };

        enum State
        {
            IDLE,
            PARSING,
            COMPLETE
        }

        @Override
        public ParseResult parse(RSyntaxDocument doc, String style)
        {
            // Dirty
            if (state != null)
            {
                if (state == State.PARSING)
                {
                    if (currentSession != null
                            && !currentSession.isDone())
                    {
                        LOGGER.debug("Cancel current parsing session");
                        currentSession.cancel(true);
                    }
                }
                else if (state == State.COMPLETE)
                {
                    LOGGER.debug("Returning parse result {}", parseResult);
                    // Reset state to parsing if previous was COMPLETE
                    state = State.IDLE;
                    return parseResult;
                }
            }

            CharArrayReader reader;
            int lineCount;
            try
            {
                lineCount = doc.getDefaultRootElement()
                        .getElementCount();
                doc.getText(0, doc.getLength(), seg);
                reader = new CharArrayReader(seg.array, seg.offset, seg.count);
            }
            catch (BadLocationException e)
            {
                return null;
            }

            LOGGER.debug("Schedule new parse session");
            state = State.PARSING;
            currentToolTip = null;
            currentLinkAction = null;
            try
            {
                currentSession = EXECUTOR.submit(() ->
                {
                    long start = System.currentTimeMillis();
                    parseResult.clearNotices();
                    try
                    {
                        parser.parse(reader);

                        parseResult.setParsedLines(0, lineCount);

                        List<ParseItem> items = parser.getParseResult();

                        for (ParseItem item : items)
                        {
                            DefaultParserNotice notice = new DefaultParserNotice(this, item.getMessage(), item.getLine(), item.getOffset(), item.getLength());
                            notice.setColor(item.getColor());

                            if (item.getLevel() != null)
                            {
                                notice.setLevel(switch (item.getLevel())
                                {
                                    case INFO -> ParserNotice.Level.INFO;
                                    case WARN -> ParserNotice.Level.WARNING;
                                    case ERROR -> ParserNotice.Level.ERROR;
                                    default -> null;
                                });
                            }

                            parseResult.addNotice(notice);
                        }

                        if (parseCompleteListener != null)
                        {
                            parseCompleteListener.run();
                        }
                    }
                    catch (Exception e)
                    {
                        parseResult.setError(e);
                    }
                    finally
                    {
                        parseResult.setParseTime(System.currentTimeMillis() - start);
                    }

                    if (!Thread.interrupted())
                    {
                        LOGGER.debug("Parse session completed {}", parseResult.getParseTime());
                        state = State.COMPLETE;
                        SwingUtilities.invokeLater(() -> editor.textEditor.forceReparsing(this));
                    }
                });
            }
            catch (RejectedExecutionException e)
            {
                // SWALLOW
            }

            return null;
        }

        @Override
        public String getToolTipText(RTextArea textArea, MouseEvent e)
        {
            // Ongoing parsing or we're searching for links then skip tooltips
            if (state == State.PARSING
                    || e.isControlDown())
            {
                return null;
            }

            int offset = textArea.viewToModel2D(e.getPoint());

            ToolTipItem item;
            if (currentToolTip != null
                    && offset >= currentToolTip.getStartOffset()
                    && offset <= currentToolTip.getEndOffset())
            {
                item = currentToolTip;
            }
            else
            {
                item = parser.getToolTip(offset);
                currentToolTip = item;
            }

            if (item != null)
            {
                return item.getToolTip();
            }
            return null;
        }

        @Override
        public LinkGeneratorResult isLinkAtOffset(RSyntaxTextArea textArea, int offset)
        {
            // Ongoing parsing, return null
            if (state == State.PARSING)
            {
                return null;
            }

            ITextEditorDocumentParser.LinkAction action;
            if (currentLinkAction != null
                    && offset >= currentLinkAction.getStartOffset()
                    && offset <= currentLinkAction.getEndOffset())
            {
                action = currentLinkAction;
            }
            else
            {
                action = parser.getLinkAction(offset);
                currentLinkAction = action;
            }

            if (action != null)
            {
                if (!linkActionsPopup.isVisible())
                {
                    linkActionsPopup.removeAll();
                    for (Action a : action.getActions())
                    {
                        JMenuItem item = ActionUtils.buildMenuItem(a);
                        item.setAlignmentX(0.0f);
                        linkActionsPopup.add(item);
                    }
                    try
                    {
                        Rectangle2D rect = textArea.modelToView2D(offset);
                        linkActionsPopup.show(textArea, (int) rect.getX(), (int) (rect.getY() + 14));
                    }
                    catch (BadLocationException e)
                    {
                    }
                }

                return new LinkGeneratorResult()
                {
                    @Override
                    public int getSourceOffset()
                    {
                        return action.getStartOffset();
                    }

                    @Override
                    public HyperlinkEvent execute()
                    {
                        action.getActions()
                                .get(0)
                                .actionPerformed(new ActionEvent(textArea, 0, ""));
                        return null;
                    }
                };
            }
            else
            {
                linkActionsPopup.setVisible(false);
            }
            return null;
        }

    }

    /** Return true or false depending on if value is between start and end. Both inclusive */
    private static boolean between(int start, int end, int value)
    {
        return value >= start
                && value <= end;
    }

    /** Paste special dialog */
    class PasteSpecialDialog extends DialogUtils.ADialog
    {
        private final JPanel flavorsPanel;
        private final JTextArea preview;

        PasteSpecialDialog()
        {
            setTitle("Paste Special");
            setModal(true);
            getContentPane().setLayout(new GridBagLayout());

            Insets insets = new Insets(2, 2, 2, 2);

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.insets = insets;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.NONE;

            getContentPane().add(new JLabel("Flavor"), gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 1;
            gbc.insets = insets;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;

            flavorsPanel = new JPanel();

            getContentPane().add(flavorsPanel, gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 2;
            gbc.gridwidth = 2;
            gbc.insets = insets;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.NONE;

            getContentPane().add(new JLabel("Preview"), gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 3;
            gbc.gridwidth = 2;
            gbc.weightx = 1.0d;
            gbc.weighty = 1.0d;
            gbc.insets = insets;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.BOTH;

            preview = new JTextArea();
            preview.setEditable(false);
            getContentPane().add(new JScrollPane(preview), gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 4;
            gbc.insets = insets;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.NONE;

            JPanel buttonPanel = new JPanel();
            getContentPane().add(buttonPanel, gbc);

            JButton ok = new JButton("OK");
            ok.addActionListener(l ->
            {
                try
                {
                    textEditor.getDocument()
                            .insertString(textEditor.getCaretPosition(), preview.getText(), null);
                }
                catch (BadLocationException e)
                {
                    // Swallow
                }
                setVisible(false);
            });
            buttonPanel.add(ok);

            JButton cancel = new JButton("Cancel");
            cancel.addActionListener(l -> setVisible(false));
            buttonPanel.add(cancel);

            setPreferredSize(new Dimension(600, 400));
            pack();
        }

        @Override
        public void setVisible(boolean b)
        {
            // Populate flavors
            if (b)
            {
                flavorsPanel.removeAll();

                Clipboard clipboard = Toolkit.getDefaultToolkit()
                        .getSystemClipboard();
                DataFlavor[] flavors = clipboard.getAvailableDataFlavors();

                Set<String> seenMimeTypes = new HashSet<>();
                List<DataFlavor> applicableFlavors = new ArrayList<>();
                for (DataFlavor df : flavors)
                {
                    if (String.class.equals(df.getRepresentationClass())
                            && (df.getPrimaryType()
                                    .equals("text")
                                    || df.getPrimaryType()
                                            .equals("plb")))
                    {
                        if (!seenMimeTypes.add(df.getPrimaryType() + "/" + df.getSubType()))
                        {
                            continue;
                        }
                        applicableFlavors.add(df);
                    }
                }

                if (applicableFlavors.isEmpty())
                {
                    return;
                }

                ButtonGroup bg = new ButtonGroup();
                for (DataFlavor df : applicableFlavors)
                {
                    JRadioButton rb = new JRadioButton(df.getHumanPresentableName());
                    rb.addActionListener(l ->
                    {
                        try
                        {
                            preview.setText(String.valueOf(clipboard.getData(df)));
                        }
                        catch (Exception e)
                        {
                            // Swallow
                        }
                    });

                    bg.add(rb);
                    flavorsPanel.add(rb);
                }

                ((JRadioButton) flavorsPanel.getComponent(0)).doClick();
                ((JRadioButton) flavorsPanel.getComponent(0)).setSelected(true);
            }

            super.setVisible(b);
        }
    }
}
