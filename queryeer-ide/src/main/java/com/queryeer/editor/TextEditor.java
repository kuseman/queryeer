package com.queryeer.editor;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
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
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayer;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;
import javax.swing.plaf.LayerUI;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.Segment;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
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
import org.fife.ui.autocomplete.FunctionCompletion;
import org.fife.ui.autocomplete.ParameterizedCompletion;
import org.fife.ui.rsyntaxtextarea.ErrorStrip;
import org.fife.ui.rsyntaxtextarea.FileLocation;
import org.fife.ui.rsyntaxtextarea.LinkGenerator;
import org.fife.ui.rsyntaxtextarea.LinkGeneratorResult;
import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SquiggleUnderlineHighlightPainter;
import org.fife.ui.rsyntaxtextarea.TextEditorPane;
import org.fife.ui.rsyntaxtextarea.Theme;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.queryeer.Constants;
import com.queryeer.IconFactory;
import com.queryeer.UiUtils;
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
import com.queryeer.api.extensions.output.table.TableTransferable;
import com.queryeer.api.service.IEventBus;
import com.queryeer.domain.Caret;
import com.queryeer.event.CaretChangedEvent;

/** Text editor implemented with {@link RSyntaxTextArea} */
class TextEditor implements ITextEditor, SearchListener
{
    private static final ExecutorService EXECUTOR;

    static
    {
        ThreadPoolExecutor tp = new ThreadPoolExecutor(5, 20, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), BasicThreadFactory.builder()
                .namingPattern("TextEditor-Parser#-%d")
                .daemon(true)
                .build());
        tp.allowCoreThreadTimeOut(true);
        EXECUTOR = tp;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(TextEditor.class);

    private static final String ZOOM_IN = "zoomIn";
    private static final String ZOOM_OUT = "zoomOut";
    private static final Icon SEARCH = IconFactory.of(FontAwesome.SEARCH);
    private static final Icon SHARE = IconFactory.of(FontAwesome.SHARE);
    private static final KeyStroke PASTE_SPECIAL_STROKE = KeyStroke.getKeyStroke(KeyEvent.VK_V, Toolkit.getDefaultToolkit()
            .getMenuShortcutKeyMaskEx() | InputEvent.SHIFT_DOWN_MASK);

    private final IEventBus eventBus;
    final ITextEditorKit editorKit;

    private final Panel panel;

    final TextEditorPane textEditor;
    private final RTextScrollPane scrollPane;
    private final FindDialog findDialog;
    private final ReplaceDialog replaceDialog;
    private final GoToDialog gotoDialog;
    private final PasteSpecialDialog pasteSpecialDialog;

    /** Reuse the same event to avoid creating new objects on every change */
    private final CaretChangedEvent caretEvent = new CaretChangedEvent(new Caret());
    private final List<PropertyChangeListener> propertyChangeListeners = new ArrayList<>();

    private ErrorStrip errorStrip;
    private EditorParser parser;
    private EditorCompleter completer;
    private MultiCaretSupport multiCaretSupport;
    private ParsingIndicator parsingIndicator;

    TextEditor(ITextEditorKit editorKit)
    {
        this(null, editorKit);
    }

    TextEditor(IEventBus eventBus, ITextEditorKit editorKit)
    {
        this.eventBus = eventBus;
        this.editorKit = requireNonNull(editorKit, "editorKit");

        MultiCaretAwareEditorPane multiCaretAwarePane = new MultiCaretAwareEditorPane();
        textEditor = multiCaretAwarePane;
        textEditor.setColumns(editorKit.getColumns());
        textEditor.setRows(editorKit.getRows());
        textEditor.setCodeFoldingEnabled(true);
        textEditor.setBracketMatchingEnabled(true);
        textEditor.setTabSize(2);
        textEditor.setTabsEmulated(true);
        textEditor.setEditable(!editorKit.readOnly());

        multiCaretSupport = new MultiCaretSupport(multiCaretAwarePane);

        UIManager.addPropertyChangeListener(uiManagerListener);
        adaptToLookAndFeel();

        // Unbind F2 since we use that to show quick datasources
        textEditor.getInputMap()
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "none");
        // Unbint CTRL/META+SHIFT+V since we use that for paste special
        textEditor.getInputMap()
                .put(PASTE_SPECIAL_STROKE, "none");

        textEditor.getInputMap()
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, Toolkit.getDefaultToolkit()
                        .getMenuShortcutKeyMaskEx()), ZOOM_IN);
        textEditor.getInputMap()
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, Toolkit.getDefaultToolkit()
                        .getMenuShortcutKeyMaskEx()), ZOOM_OUT);

        textEditor.getActionMap()
                .put(ZOOM_IN, new AbstractAction()
                {
                    @Override
                    public void actionPerformed(ActionEvent e)
                    {
                        Font font = textEditor.getFont();
                        textEditor.setFont(font.deriveFont(font.getSize2D() + 1.0f));
                    }
                });
        textEditor.getActionMap()
                .put(ZOOM_OUT, new AbstractAction()
                {
                    @Override
                    public void actionPerformed(ActionEvent e)
                    {
                        Font font = textEditor.getFont();
                        textEditor.setFont(font.deriveFont(font.getSize2D() - 1.0f));
                    }
                });

        // Multi-caret: Ctrl/Cmd+D — select next occurrence (or delete line when no multi-caret context)
        textEditor.getInputMap()
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_D, Toolkit.getDefaultToolkit()
                        .getMenuShortcutKeyMaskEx()), "multiCaret.selectNextOccurrence");
        textEditor.getActionMap()
                .put("multiCaret.selectNextOccurrence", new AbstractAction()
                {
                    @Override
                    public void actionPerformed(ActionEvent e)
                    {
                        String sel = textEditor.getSelectedText();
                        if (!multiCaretSupport.hasSecondaryCarets()
                                && (sel == null
                                        || sel.isEmpty()))
                        {
                            // No multi-caret context and no selection: delete the current line
                            Action deleteLineAction = textEditor.getActionMap()
                                    .get(RTextAreaEditorKit.rtaDeleteLineAction);
                            if (deleteLineAction != null)
                            {
                                deleteLineAction.actionPerformed(e);
                            }
                        }
                        else
                        {
                            multiCaretSupport.selectNextOccurrence();
                        }
                    }
                });

        // Multi-caret: Ctrl/Cmd+Alt+Down — add caret below
        textEditor.getInputMap()
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, Toolkit.getDefaultToolkit()
                        .getMenuShortcutKeyMaskEx() | InputEvent.ALT_DOWN_MASK), "multiCaret.addCaretBelow");
        textEditor.getActionMap()
                .put("multiCaret.addCaretBelow", new AbstractAction()
                {
                    @Override
                    public void actionPerformed(ActionEvent e)
                    {
                        multiCaretSupport.addCaretBelow();
                    }
                });

        // Multi-caret: Ctrl/Cmd+Alt+Up — add caret above
        textEditor.getInputMap()
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, Toolkit.getDefaultToolkit()
                        .getMenuShortcutKeyMaskEx() | InputEvent.ALT_DOWN_MASK), "multiCaret.addCaretAbove");
        textEditor.getActionMap()
                .put("multiCaret.addCaretAbove", new AbstractAction()
                {
                    @Override
                    public void actionPerformed(ActionEvent e)
                    {
                        multiCaretSupport.addCaretAbove();
                    }
                });

        scrollPane = new RTextScrollPane(textEditor, true);

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
        // Store as a field so it can be re-registered when textEditor.load() replaces the document.
        ADocumentListenerAdapter documentChangeListener = new ADocumentListenerAdapter()
        {
            @Override
            protected void update()
            {
                if (parser != null)
                {
                    parser.state = EditorParser.State.PARSING;
                    parser.docRevision++;
                }

                int size = propertyChangeListeners.size();
                for (int i = size - 1; i >= 0; i--)
                {
                    propertyChangeListeners.get(i)
                            .propertyChange(new PropertyChangeEvent(textEditor, IEditor.VALUE_CHANGED, null, null));
                }
            }
        };
        textEditor.getDocument()
                .addDocumentListener(documentChangeListener);

        // Re-register the document listener whenever textEditor.load() replaces the underlying document.
        // Without this, VALUE_CHANGED is never fired after a session restore and backups stop being written.
        textEditor.addPropertyChangeListener("document", evt ->
        {
            Document oldDoc = (Document) evt.getOldValue();
            Document newDoc = (Document) evt.getNewValue();
            if (oldDoc != null)
            {
                oldDoc.removeDocumentListener(documentChangeListener);
            }
            if (newDoc != null)
            {
                newDoc.addDocumentListener(documentChangeListener);
            }
        });

        //@formatter:off
        List<Action> actions = new ArrayList<>();
        actions.addAll(editorKit.getActions());
        actions.addAll(asList(
                toggleShowWhiteSpaceAction,
                removeTrailingWhiteSpace,
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
        private List<Action> actions;

        Panel(List<Action> actions)
        {
            this.actions = actions;
            setLayout(new BorderLayout());

            installEditorKit();

            putClientProperty(com.queryeer.api.action.Constants.QUERYEER_ACTIONS, actions);

            // Add error strip and parsing indicator if we have a parser installed
            if (parser != null)
            {
                parsingIndicator = new ParsingIndicator(scrollPane);
                add(parsingIndicator.getLayer());
                errorStrip = new ErrorStrip(textEditor);
                add(errorStrip, BorderLayout.LINE_END);
            }
            else
            {
                add(scrollPane);
            }
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
            // Reduce default parse delay (1250ms) to improve completion responsiveness
            textEditor.setParserDelay(750);

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
                EditorAutoCompletion autoCompletion = new EditorAutoCompletion(completer);
                autoCompletion.setListCellRenderer(new CompletionCellRenderer());
                autoCompletion.setShowDescWindow(true);
                autoCompletion.install(textEditor);
                completer.setAutoCompletion(autoCompletion);
                if (parser.supportsSignatureHints())
                {
                    autoCompletion.setParameterAssistanceEnabled(true);
                    // Replace the '(' action installed by AutoCompletion.install() so that
                    // signature hints trigger even when the autocomplete popup is not open.
                    // The default ParameterizedCompletionStartAction only fires when a
                    // ParameterizedCompletion is selected in the popup, which never happens
                    // because getCompletionsImpl returns BasicCompletion items.
                    textEditor.getActionMap()
                            .put("AutoCompletion.FunctionStart", new AbstractAction()
                            {
                                @Override
                                public void actionPerformed(ActionEvent e)
                                {
                                    autoCompletion.hideChildWindows();
                                    autoCompletion.tryParameterizedCompletion();
                                }
                            });
                }
                // Trigger completion automatically when '.' is typed, just like Ctrl+Space.
                // If the parser returns no items for the current context the popup won't appear.
                textEditor.addKeyListener(new KeyAdapter()
                {
                    @Override
                    public void keyTyped(KeyEvent e)
                    {
                        if (e.getKeyChar() == '.')
                        {
                            // Require the fresh parse (the one that will include the dot) before
                            // showing the popup. docRevision is incremented by the document listener
                            // when the dot is inserted, so dotDocRevision = current + 1 is exactly
                            // the revision that the fresh-parse session must have captured.
                            completer.doAutoCompleteWhenReady = true;
                            completer.dotDocRevision = completer.parser.docRevision + 1;
                            if (parsingIndicator != null)
                            {
                                parsingIndicator.startAnimation();
                            }
                        }
                    }
                });
                this.parser.setParseCompleteListener(completer.parseCompleteRunner);
            }

            // Force a parsing after we installed the kit
            textEditor.forceReparsing(this.parser);
        }
    }

    private PropertyChangeListener uiManagerListener = new PropertyChangeListener()
    {
        @Override
        public void propertyChange(PropertyChangeEvent evt)
        {
            if ("lookAndFeel".equals(evt.getPropertyName()))
            {
                adaptToLookAndFeel();
            }
        }
    };

    private void adaptToLookAndFeel()
    {
        String themeName = UiUtils.isDarkLookAndFeel() ? "dark.xml"
                : "default.xml";

        try
        {
            Theme theme = Theme.load(TextEditor.class.getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/" + themeName));
            theme.apply(textEditor);
        }
        catch (IOException ioe)
        { // Never happens
            ioe.printStackTrace();
        }
    }

    // IEditor

    @Override
    public void close()
    {
        multiCaretSupport.dispose();
        if (parser != null)
        {
            Future<?> session = parser.currentSession;
            if (session != null
                    && !session.isDone())
            {
                session.cancel(true);
            }
        }
        if (parsingIndicator != null)
        {
            parsingIndicator.dispose();
        }
        UIManager.removePropertyChangeListener(uiManagerListener);

        List.of(findDialog, replaceDialog, gotoDialog, pasteSpecialDialog)
                .forEach(d ->
                {
                    d.setVisible(false);
                    d.dispose();
                });

        panel.actions.forEach(a ->
        {
            PropertyChangeListener[] listeners = ((AbstractAction) a).getPropertyChangeListeners();
            for (PropertyChangeListener l : listeners)
            {
                a.removePropertyChangeListener(l);
            }
        });

        if (completer != null)
        {
            completer.autoCompletion.uninstall();
        }
    }

    @Override
    public void clearBeforeExecution()
    {
        // Snapshot secondary carets before clearing so they can be restored after highlights are removed
        final List<int[]> secondarySnapshot = multiCaretSupport.snapshotSecondaryCarets();
        multiCaretSupport.clearSecondaryCarets();

        int selStart = textEditor.getSelectionStart();
        int selEnd = textEditor.getSelectionEnd();

        textEditor.getHighlighter()
                .removeAllHighlights();

        if (selEnd - selStart > 0)
        {
            textEditor.setSelectionStart(selStart);
            textEditor.setSelectionEnd(selEnd);
        }

        if (!secondarySnapshot.isEmpty())
        {
            multiCaretSupport.restoreSecondaryCarets(secondarySnapshot);
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

        String result = "";
        if (multiCaretSupport.hasSecondaryCarets())
        {
            result = multiCaretSupport.getSelectedText();
        }
        else
        {
            // Default value for a text editor is the selected text if any else everything
            result = textEditor.getSelectedText();
        }
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

        // Collect all carets (primary + secondary) to cover multicaret selections
        List<int[]> allCarets = multiCaretSupport.buildAllCaretsSortedAsc();
        boolean singleCaret = allCarets.size() == 1;

        int primaryDot = textEditor.getCaretPosition();
        int primaryMark = textEditor.getCaret()
                .getMark();
        boolean caretSelection = singleCaret
                && primaryDot == primaryMark;

        Boolean addComments = null;

        try
        {
            // Collect unique line indices covered by any caret, in document order
            Set<Integer> coveredLines = new java.util.TreeSet<>();
            for (int[] caret : allCarets)
            {
                int selStart = Math.min(caret[0], caret[1]);
                int selEnd = Math.max(caret[0], caret[1]);
                for (int i = 0; i < lines; i++)
                {
                    int startOffset = textEditor.getLineStartOffset(i);
                    int endOffset = textEditor.getLineEndOffset(i) - 1;
                    if (between(startOffset, endOffset, selStart)
                            || (startOffset > selStart
                                    && endOffset <= selEnd)
                            || (selEnd > startOffset
                                    && selEnd <= endOffset))
                    {
                        coveredLines.add(i);
                    }
                }
            }

            List<MutableInt> commentOffsets = new ArrayList<>();
            for (int lineIndex : coveredLines)
            {
                int startOffset = textEditor.getLineStartOffset(lineIndex);
                int endOffset = textEditor.getLineEndOffset(lineIndex) - 1;
                int lineLength = endOffset - startOffset;
                if (lineLength == 0)
                {
                    continue;
                }

                // Find first non-whitespace position on this line
                String lineText = textEditor.getText(startOffset, lineLength);
                int indent = 0;
                while (indent < lineLength
                        && (lineText.charAt(indent) == ' '
                                || lineText.charAt(indent) == '\t'))
                {
                    indent++;
                }

                if (indent == lineLength)
                {
                    // Whitespace-only line, skip
                    continue;
                }

                boolean lineIsCommented = lineText.substring(indent)
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
                // Capture original offsets before the edit loop modifies them
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
                if (singleCaret)
                {
                    // Single-caret: move selection to span the toggled comment range
                    int selStart = commentOffsets.get(0)
                            .intValue();
                    int selEnd = caretSelection ? selStart
                            : commentOffsets.get(commentOffsets.size() - 1)
                                    .intValue();
                    textEditor.setSelectionStart(selStart);
                    textEditor.setSelectionEnd(selEnd);
                }
                else
                {
                    // Multicaret: restore primary caret to its shifted position
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
                    // Shift all secondary carets by the same rule
                    multiCaretSupport.shiftSecondaryCarets(originalOffsets, delta);
                }
            }
        }
        catch (BadLocationException e)
        {
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

    private void publishCaret()
    {
        if (eventBus == null)
        {
            return;
        }
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

    private Action toggleShowWhiteSpaceAction = new AbstractAction("", IconFactory.of(FontAwesome.PARAGRAPH))
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

    private Action removeTrailingWhiteSpace = new AbstractAction("")
    {
        {
            putValue(Action.NAME, "Remove Trailing Whitespace");
            putValue(com.queryeer.api.action.Constants.ACTION_SHOW_IN_MENU, true);
            putValue(com.queryeer.api.action.Constants.ACTION_MENU, com.queryeer.api.action.Constants.EDIT_MENU);
            putValue(com.queryeer.api.action.Constants.ACTION_ORDER, 6);
        }

        @Override
        public void actionPerformed(ActionEvent e)
        {
            textEditor.beginAtomicEdit();
            int caretPos = textEditor.getCaretPosition();
            int numberOfRemovedCharsAfterCaretPos = 0;
            try
            {
                Segment seg = new Segment();

                int count = textEditor.getLineCount();
                for (int i = 0; i < count; i++)
                {
                    // seg.setIndex(0);
                    int start = textEditor.getLineStartOffset(i);
                    int end = textEditor.getLineEndOffset(i);

                    Document document = textEditor.getDocument();

                    int length = end - start;
                    if (length <= 0)
                    {
                        continue;
                    }

                    document.getText(start, end - start, seg);
                    int whiteSpaceCount = 0;

                    char c = seg.setIndex(seg.offset + length - 1);
                    if (c == '\n')
                    {
                        end--;
                        c = seg.previous();
                    }
                    while (c != Segment.DONE)
                    {
                        if (!Character.isSpaceChar(c))
                        {
                            break;
                        }
                        whiteSpaceCount++;
                        c = seg.previous();
                    }

                    int removeLength = end - whiteSpaceCount;
                    if (removeLength >= caretPos)
                    {
                        numberOfRemovedCharsAfterCaretPos += removeLength;
                    }

                    if (whiteSpaceCount > 0)
                    {
                        document.remove(removeLength, whiteSpaceCount);
                    }
                }

                textEditor.setCaretPosition(caretPos - numberOfRemovedCharsAfterCaretPos);
            }
            catch (BadLocationException e1)
            {
                LOGGER.error("Error replacing whitespace", e1);
            }
            finally
            {
                textEditor.endAtomicEdit();
            }
        }
    };

    private Action toggleCommentAction = new AbstractAction("", IconFactory.of(FontAwesome.INDENT))
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
            putValue(Action.ACTION_COMMAND_KEY, com.queryeer.api.action.Constants.FIND_ACTION);
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
        private EditorAutoCompletion autoCompletion;

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
        private volatile boolean doAutoCompleteWhenReady = false;
        /**
         * When completion was triggered by a dot keystroke, this holds the minimum docRevision the completed parse must have started with to be considered fresh (i.e. it actually contains the dot).
         * -1 means the request came from Ctrl+Space (no freshness requirement).
         */
        private volatile int dotDocRevision = -1;
        private volatile boolean doSignatureHintWhenReady = false;
        /** Last successfully fetched unfiltered completion items, used to re-filter while a new parse is in progress. */
        private List<CompletionItem> cachedCompletionItems = null;
        /** Token-start document offset for the cached items (caretOffset - enteredText.length() at the time of caching). */
        private int cachedTokenStartOffset = -1;
        private volatile int pendingSignatureHintOffset = -1;
        /** True while the signature-hint retry (triggered by parseCompleteRunner) is executing. Prevents re-queueing another retry from within the retry itself. */
        private volatile boolean isRetryingSignatureHint = false;

        // --- Async completion fetch (Phase 3) ---
        // getCompletionItems() can be expensive (tree traversal, alias collection, C3 ATN walk).
        // Instead of blocking the EDT, we submit it to EXECUTOR and re-trigger doCompletion() when
        // the result is ready. completionFuture is accessed only on the EDT; pendingResult* fields
        // are written on the EXECUTOR thread and read on the EDT, so they are volatile.

        /** In-flight off-EDT completion fetch, or null. Accessed only from the EDT. */
        private Future<?> completionFuture = null;
        /** Result of the last off-EDT getCompletionItems() call; null if no result is pending. */
        private volatile ITextEditorDocumentParser.CompletionResult pendingResult = null;
        /** Document caret offset for which {@link #pendingResult} was fetched. */
        private volatile int pendingResultOffset = -1;
        /**
         * {@link EditorParser#sessionStartRevision} value captured when {@link #pendingResult} was fetched. Used to discard results from a superseded parse: if a new parse completes before the EDT
         * consumes the pending result, this revision will no longer match {@link EditorParser#sessionStartRevision}.
         */
        private volatile int pendingResultSessionRevision = -1;

        EditorCompleter(EditorParser parser)
        {
            this.parser = parser;
            if (parser.parser.supportsSignatureHints())
            {
                setParameterizedCompletionParams('(', ", ", ')');
            }
        }

        void setAutoCompletion(EditorAutoCompletion autoCompletion)
        {
            this.autoCompletion = autoCompletion;
        }

        /** Cancel any in-flight async completion fetch and discard its pending result. EDT only. */
        private void cancelPendingCompletion()
        {
            if (completionFuture != null
                    && !completionFuture.isDone())
            {
                completionFuture.cancel(true);
            }
            completionFuture = null;
            pendingResult = null;
            pendingResultOffset = -1;
            pendingResultSessionRevision = -1;
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
                    // For dot-triggered completion, skip if the parse that just completed was started
                    // before the dot was inserted (stale parse). The next parse, started by
                    // RSyntaxTextArea's timer after the dot's document change, will have a higher
                    // sessionStartRevision and will correctly trigger the popup.
                    if (dotDocRevision >= 0
                            && parser.sessionStartRevision < dotDocRevision)
                    {
                        return;
                    }
                    doAutoCompleteWhenReady = false;
                    dotDocRevision = -1;
                    SwingUtilities.invokeLater(() -> autoCompletion.doCompletion());
                }
                if (doSignatureHintWhenReady)
                {
                    doSignatureHintWhenReady = false;
                    int offset = pendingSignatureHintOffset;
                    pendingSignatureHintOffset = -1;
                    isRetryingSignatureHint = true;
                    SwingUtilities.invokeLater(() ->
                    {
                        try
                        {
                            JTextComponent tc = autoCompletion.getTextComponent();
                            // Only retry if the caret is still right after the '(' we inserted
                            if (tc != null
                                    && tc.getCaretPosition() == offset + 1)
                            {
                                try
                                {
                                    // Remove the literal '(' so tryParameterizedCompletion can re-insert
                                    // it properly together with the parameter hint tooltip.
                                    tc.getDocument()
                                            .remove(offset, 1);
                                }
                                catch (BadLocationException e)
                                {
                                    return;
                                }
                                autoCompletion.tryParameterizedCompletion();
                            }
                        }
                        finally
                        {
                            isRetryingSignatureHint = false;
                        }
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
            ITextEditorDocumentParser.SignatureHint hint = parser.parser.getSignatureHint(tc.getCaretPosition());
            if (hint == null)
            {
                return null;
            }
            FunctionCompletion fc = new FunctionCompletion(this, hint.functionName(), hint.returnType());
            List<ParameterizedCompletion.Parameter> params = new ArrayList<>();
            for (ITextEditorDocumentParser.SignatureParam sp : hint.params())
            {
                params.add(new ParameterizedCompletion.Parameter(sp.type(), sp.name()));
            }
            fc.setParams(params);
            return List.of(fc);
        }

        @Override
        protected List<Completion> getCompletionsImpl(JTextComponent comp)
        {
            int offset = comp.getCaretPosition();
            String enteredText = getAlreadyEnteredText(comp);

            int tokenStartOffset = offset - (enteredText != null ? enteredText.length()
                    : 0);

            // Ongoing parsing — if the popup is already visible and we have cached items for the
            // same token start, re-filter and return them so the popup stays populated without a
            // spinner. A refresh will happen automatically when the parse completes.
            if (parser.state == EditorParser.State.PARSING)
            {
                // A new parse is in progress — any in-flight completion fetch is now stale.
                cancelPendingCompletion();
                if (autoCompletion != null
                        && autoCompletion.isPopupVisible()
                        && cachedCompletionItems != null
                        && cachedTokenStartOffset == tokenStartOffset)
                {
                    doAutoCompleteWhenReady = true;
                    // Fall through to filtering below using the cached items
                }
                else
                {
                    doAutoCompleteWhenReady = true;
                    // Start the spinner immediately so it is visible even when Ctrl+Space is pressed
                    // before RSyntaxTextArea's parse timer has fired and called startAnimation().
                    if (parser.editor.parsingIndicator != null)
                    {
                        parser.editor.parsingIndicator.startAnimation();
                    }
                    return emptyList();
                }
            }

            List<CompletionItem> completionItems;
            if (parser.state == EditorParser.State.PARSING)
            {
                // Use the cached items from the previous successful parse
                completionItems = cachedCompletionItems;
            }
            else
            {
                // Parse is COMPLETE. Strategy:
                // • Cold start (no cache yet): run synchronously so the popup appears on the
                // very first keypress. Phase 2 per-parse caches keep this fast (<50 ms typical).
                // • Warm start (cache exists): return cached items immediately so the popup
                // appears without any EDT block, then refresh in the background. When fresh
                // results arrive, invokeLater re-calls doCompletion(); at that point the popup
                // is already visible, so it updates cleanly without needing a second trigger.
                int currentSessionRevision = parser.sessionStartRevision;
                if (pendingResult != null
                        && pendingResultOffset == offset
                        && pendingResultSessionRevision == currentSessionRevision)
                {
                    // Async refresh result is ready — consume it and update the (already visible) popup.
                    CompletionResult result = pendingResult;
                    pendingResult = null;
                    completionItems = result.getItems();
                    // Cache the fresh unfiltered items for reuse while the next parse is in progress.
                    // Skip caching partial results (catalog still loading) — they would persist as
                    // stale items after the catalog finishes loading, preventing a fresh fetch.
                    if (!result.isPartialResult())
                    {
                        cachedCompletionItems = completionItems;
                        cachedTokenStartOffset = tokenStartOffset;
                    }
                }
                else if (cachedCompletionItems == null)
                {
                    // Cold start: no cached items yet — run synchronously so the popup appears
                    // immediately on this first request without requiring a second Ctrl+Space.
                    CompletionResult syncResult = parser.parser.getCompletionItems(offset);
                    if (syncResult != null)
                    {
                        completionItems = syncResult.getItems();
                        if (!syncResult.isPartialResult())
                        {
                            cachedCompletionItems = completionItems;
                            cachedTokenStartOffset = tokenStartOffset;
                        }
                    }
                    else
                    {
                        return emptyList();
                    }
                }
                else if (completionFuture == null
                        || completionFuture.isDone())
                {
                    // Cached items exist but may be stale — return them immediately so the popup
                    // appears, and refresh in the background. When the result is ready, the popup
                    // is already visible and doCompletion() will update it correctly.
                    final int capturedRevision = currentSessionRevision;
                    final int capturedOffset = offset;
                    completionFuture = EXECUTOR.submit(() ->
                    {
                        ITextEditorDocumentParser.CompletionResult r = parser.parser.getCompletionItems(capturedOffset);
                        // Discard if the parse was replaced while we were fetching.
                        if (r != null
                                && capturedRevision == parser.sessionStartRevision)
                        {
                            pendingResultSessionRevision = capturedRevision;
                            pendingResultOffset = capturedOffset;
                            pendingResult = r; // volatile write — visible to EDT before invokeLater fires
                            SwingUtilities.invokeLater(() -> autoCompletion.doCompletion());
                        }
                    });
                    completionItems = cachedCompletionItems;
                }
                else
                {
                    // Async refresh already in progress — keep showing cached items.
                    completionItems = cachedCompletionItems;
                }
            }

            // When the entered text contains a dot (e.g. "ac." or "ac.col"), filter only by the
            // segment after the last dot. The prefix ("ac") is the table/alias qualifier that the
            // parser already uses via the caret offset; filtering by the full "ac.col" would skip
            // matchParts whose individual parts are shorter than the combined input string, causing
            // all column completions to be filtered out. Filtering by just "col" lets "column1" match
            // while the replacement text ("ac.column1") still replaces the full entered text correctly.
            String filterText = enteredText;
            String dotQualifierPrefix = null;
            if (enteredText != null
                    && enteredText.contains("."))
            {
                int lastDot = enteredText.lastIndexOf('.');
                // "c." for "c.crea" — used to reject items from other aliases (e.g. "ac.createdAt")
                dotQualifierPrefix = enteredText.substring(0, lastDot + 1);
                filterText = enteredText.substring(lastDot + 1);
            }

            if (!isBlank(filterText))
            {
                // Pre-filter by qualifier: "c.crea" must not match "ac.createdAt" even though
                // "crea" fuzzy-matches the column name. Only keep items whose replacement text
                // starts with the typed qualifier prefix (e.g. "c.").
                if (dotQualifierPrefix != null)
                {
                    final String prefix = dotQualifierPrefix;
                    List<CompletionItem> qualified = null;
                    for (CompletionItem item : completionItems)
                    {
                        if (Strings.CI.startsWith(item.getReplacementText(), prefix))
                        {
                            if (qualified == null)
                            {
                                qualified = new ArrayList<>();
                            }
                            qualified.add(item);
                        }
                    }
                    completionItems = qualified == null ? emptyList()
                            : qualified;
                }
                completionItems = getCompletionByInputText(completionItems, filterText);
            }
            else if (!isBlank(enteredText))
            {
                // enteredText ends with a dot (e.g. "ac.") — filter by full prefix on replacement text
                // to avoid showing items from other aliases like "a.*" when "ac." was typed
                List<CompletionItem> filtered = null;
                for (CompletionItem item : completionItems)
                {
                    if (Strings.CI.startsWith(item.getReplacementText(), enteredText))
                    {
                        if (filtered == null)
                        {
                            filtered = new ArrayList<>();
                        }
                        filtered.add(item);
                    }
                }
                completionItems = filtered == null ? emptyList()
                        : filtered;
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

        private static final Logger LOGGER = LoggerFactory.getLogger(TextEditor.class);

        private List<CompletionItem> getCompletionByInputText(List<CompletionItem> completionItems, String inputText)
        {
            LOGGER.debug("start: getCompletionByInputText. inputText: {}, completionItems: {}", inputText, completionItems);

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
                        && Strings.CI.startsWith(item.getReplacementText(), inputText))
                {
                    if (result == null)
                    {
                        result = new ArrayList<>();
                    }
                    result.add(item);
                }
            }

            LOGGER.debug("end: getCompletionByInputText. result: {}", result);

            return result == null ? emptyList()
                    : result;
        }
    }

    /**
     * AutoCompletion subclass that supports triggering parameterized completion (signature hints) when {@code (} is typed, even without the autocomplete popup being open.
     *
     * <p>
     * The default {@link AutoCompletion} only triggers signature hints when a {@link ParameterizedCompletion} is selected in the popup and {@code (} is pressed. Since
     * {@link EditorCompleter#getCompletionsImpl} returns {@link BasicCompletion} items, that path never fires. This subclass replaces the {@code (} action to call
     * {@link EditorCompleter#getParameterizedCompletions} directly and drive the hint through the protected {@link #insertCompletion} hook.
     * </p>
     */
    private static class EditorAutoCompletion extends AutoCompletion
    {
        private final EditorCompleter completer;

        EditorAutoCompletion(EditorCompleter completer)
        {
            super(completer);
            this.completer = completer;
        }

        /**
         * Called when {@code (} is typed. Tries to show a signature-hint popup via {@link EditorCompleter#getParameterizedCompletions}; falls back to inserting a literal {@code (} if no hint is
         * available.
         */
        void tryParameterizedCompletion()
        {
            JTextComponent tc = getTextComponent();
            List<ParameterizedCompletion> hints = completer.getParameterizedCompletions(tc);
            if (hints != null
                    && !hints.isEmpty())
            {
                FunctionCompletion originalFc = (FunctionCompletion) hints.get(0);
                // Use the already-entered text as the replacement so insertCompletion
                // replaces it in-place (effectively a no-op for document text) and then
                // calls startParameterizedCompletionAssistance to insert "(params)".
                String alreadyEntered = completer.getAlreadyEnteredText(tc);
                FunctionCompletion fc = new FunctionCompletion(completer, alreadyEntered, originalFc.getType());
                List<ParameterizedCompletion.Parameter> params = new ArrayList<>();
                for (int i = 0; i < originalFc.getParamCount(); i++)
                {
                    params.add(originalFc.getParam(i));
                }
                fc.setParams(params);
                insertCompletion(fc, true);
            }
            else
            {
                // Insert '(' now so the user's keystroke is not lost.
                // If we're not already in a retry, schedule a retry once the next parse
                // completes so the signature hint can still appear (covers both the case
                // where the parser is currently running and the case where it hasn't
                // started yet because the parse-delay timer hasn't fired).
                if (!completer.isRetryingSignatureHint)
                {
                    completer.doSignatureHintWhenReady = true;
                    completer.pendingSignatureHintOffset = tc.getCaretPosition();
                    // Start the spinner from the EDT so it is reliably visible even when
                    // the background parse completes in less than one timer interval.
                    if (completer.parser.editor.parsingIndicator != null)
                    {
                        completer.parser.editor.parsingIndicator.startAnimation();
                    }
                }
                tc.replaceSelection("(");
            }
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

        /**
         * Incremented on every document change (EDT only). Captured at session start so parseCompleteRunner can tell whether the completed parse reflects the document state that triggered a
         * dot-completion request.
         */
        volatile int docRevision = 0;
        private volatile int sessionStartRevision = 0;

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
            if (editor.parsingIndicator != null)
            {
                editor.parsingIndicator.startAnimation();
            }
            final int capturedRevision = docRevision;
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
                        sessionStartRevision = capturedRevision;
                        if (parseCompleteListener != null)
                        {
                            parseCompleteListener.run();
                        }
                        SwingUtilities.invokeLater(() ->
                        {
                            if (editor.parsingIndicator != null)
                            {
                                editor.parsingIndicator.stopAnimation();
                            }
                            editor.textEditor.forceReparsing(this);
                        });
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
                        // Show the popup on click so it never grabs focus during Ctrl+hover,
                        // which would otherwise intercept Ctrl+C and other keyboard shortcuts.
                        linkActionsPopup.removeAll();
                        for (Action a : action.getActions())
                        {
                            JMenuItem item = ActionUtils.buildMenuItem(a);
                            item.setAlignmentX(0.0f);
                            linkActionsPopup.add(item);
                        }
                        try
                        {
                            Rectangle2D rect = textArea.modelToView2D(action.getStartOffset());
                            linkActionsPopup.show(textArea, (int) rect.getX(), (int) (rect.getY() + 14));
                        }
                        catch (BadLocationException e)
                        {
                        }
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

    /**
     * Transparent overlay on top of the scroll pane that paints a small rotating arc next to the caret when parsing is in progress.
     */
    private class ParsingIndicator extends LayerUI<RTextScrollPane>
    {
        private static final int SIZE = 10;
        private static final int GAP = 3;

        private final JLayer<RTextScrollPane> layer;
        private final Timer timer;
        private int frame = 0;
        private volatile boolean active = false;

        ParsingIndicator(RTextScrollPane scrollPane)
        {
            this.layer = new JLayer<>(scrollPane, this);
            this.timer = new Timer(80, e ->
            {
                frame++;
                layer.repaint();
            });
            this.timer.setRepeats(true);
        }

        JLayer<RTextScrollPane> getLayer()
        {
            return layer;
        }

        /** Must be called on the EDT. */
        void startAnimation()
        {
            active = true;
            if (!timer.isRunning())
            {
                frame = 0;
                timer.start();
            }
        }

        /** Must be called on the EDT. */
        void stopAnimation()
        {
            active = false;
            timer.stop();
            layer.repaint();
        }

        void dispose()
        {
            timer.stop();
        }

        @Override
        public void paint(Graphics g, JComponent c)
        {
            super.paint(g, c);
            if (!active)
            {
                return;
            }
            try
            {
                int caretPos = textEditor.getCaretPosition();
                Rectangle2D caretRect = textEditor.modelToView2D(caretPos);
                if (caretRect == null)
                {
                    return;
                }
                // Only draw when the caret line is within the visible viewport
                Rectangle visible = textEditor.getVisibleRect();
                if (caretRect.getY() < visible.y
                        || caretRect.getY() >= visible.y + visible.height)
                {
                    return;
                }
                Point p = SwingUtilities.convertPoint(textEditor, (int) (caretRect.getX() + caretRect.getWidth()) + GAP, (int) caretRect.getY() + 1, c);

                Graphics2D g2 = (Graphics2D) g.create();
                try
                {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    // Faint full circle as track
                    g2.setColor(new Color(128, 128, 128, 60));
                    g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawOval(p.x, p.y, SIZE, SIZE);

                    // Spinning arc using the theme's accent color if available
                    Color base = UIManager.getColor("Component.accentColor");
                    if (base == null)
                    {
                        base = new Color(64, 128, 255);
                    }
                    g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 210));
                    g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int startAngle = (frame * 36) % 360;
                    g2.drawArc(p.x, p.y, SIZE, SIZE, startAngle, 270);
                }
                finally
                {
                    g2.dispose();
                }
            }
            catch (BadLocationException e)
            {
                // ignore — caret position no longer valid
            }
        }
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
                DataFlavor plainStringFlavor = null;
                boolean queryeerDataFound = false;
                for (DataFlavor df : flavors)
                {
                    if (!String.class.equals(df.getRepresentationClass()))
                    {
                        continue;
                    }

                    boolean isQueryeer = df.getPrimaryType()
                            .equals("queryeer");
                    boolean isJavaString = df.getMimeType()
                            .startsWith(TableTransferable.JAVA_SERIALIZED_OBJECT);
                    boolean isPlainText = df.getMimeType()
                            .startsWith(TableTransferable.PLAIN_TEXT);
                    boolean isHtml = df.getMimeType()
                            .startsWith("text/html");

                    if (plainStringFlavor == null
                            && isPlainText)
                    {
                        plainStringFlavor = df;
                    }
                    else if (plainStringFlavor == null
                            && isJavaString)
                    {
                        plainStringFlavor = df;
                    }
                    else
                    {
                        isJavaString = false;
                        isPlainText = false;
                    }

                    if (isQueryeer
                            || isJavaString
                            || isPlainText
                            || isHtml)
                    {
                        if (!seenMimeTypes.add(df.getPrimaryType() + "/" + df.getSubType()))
                        {
                            continue;
                        }

                        if (df.getPrimaryType()
                                .equals("queryeer"))
                        {
                            queryeerDataFound = true;
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
                    boolean queryeer = df.getPrimaryType()
                            .equals("queryeer");
                    boolean html = df.getMimeType()
                            .contains("html");
                    JRadioButton rb = new JRadioButton(queryeer ? df.getHumanPresentableName()
                            : html ? "Html"
                                    : "Plain");
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

                // If we only have a java string
                if (!queryeerDataFound
                        && plainStringFlavor != null)
                {
                    DataFlavor df = plainStringFlavor;
                    List.of(TableTransferable.SQL_IN, TableTransferable.SQL_IN_NEW_LINE)
                            .forEach(s ->
                            {
                                JRadioButton rb = new JRadioButton(s);
                                rb.addActionListener(l ->
                                {
                                    try
                                    {
                                        preview.setText(String.valueOf(TableTransferable.getSqlIn((String) clipboard.getData(df), s == TableTransferable.SQL_IN_NEW_LINE)));
                                    }
                                    catch (Exception e)
                                    {
                                        // Swallow
                                    }
                                });

                                bg.add(rb);
                                flavorsPanel.add(rb);
                            });
                }

                ((JRadioButton) flavorsPanel.getComponent(0)).doClick();
                ((JRadioButton) flavorsPanel.getComponent(0)).setSelected(true);
            }

            super.setVisible(b);
        }
    }
}
