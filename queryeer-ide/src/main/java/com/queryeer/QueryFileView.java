package com.queryeer;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.OverlayLayout;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.fife.rsta.ui.GoToDialog;
import org.fife.rsta.ui.search.FindDialog;
import org.fife.rsta.ui.search.ReplaceDialog;
import org.fife.rsta.ui.search.SearchEvent;
import org.fife.rsta.ui.search.SearchListener;
import org.fife.ui.rsyntaxtextarea.ErrorStrip;
import org.fife.ui.rsyntaxtextarea.SquiggleUnderlineHighlightPainter;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.TextEditorPane;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.fife.ui.rtextarea.SearchResult;

import com.queryeer.QueryFileModel.State;
import com.queryeer.api.IQueryFile;
import com.queryeer.api.TextSelection;
import com.queryeer.api.extensions.IExtensionAction;
import com.queryeer.api.extensions.output.IOutputComponent;
import com.queryeer.api.extensions.output.IOutputExtension;
import com.queryeer.api.extensions.output.IOutputFormatExtension;
import com.queryeer.api.extensions.output.IOutputToolbarActionFactory;
import com.queryeer.api.extensions.output.text.ITextOutputComponent;
import com.queryeer.api.service.IEventBus;
import com.queryeer.completion.CompletionInstaller;
import com.queryeer.component.TabComponent;
import com.queryeer.event.CaretChangedEvent;

import se.kuseman.payloadbuilder.api.execution.IQuerySession;
import se.kuseman.payloadbuilder.core.parser.Location;

/** Content of a query editor. Text editor and a result panel separated with a split panel */
class QueryFileView extends JPanel implements IQueryFile, SearchListener
{
    private static final String GOTO = "GOTO";
    private static final String REPLACE = "REPLACE";
    private static final String FIND = "FIND";
    private static final String PASTE_SPECIAL = "PASTE_SPECIAL";

    private final QueryFileModel file;
    private final JSplitPane splitPane;
    private final TextEditorPane textEditor;
    private final FindDialog findDialog;
    private final ReplaceDialog replaceDialog;
    private final GoToDialog gotoDialog;
    private final JTabbedPane resultTabs;
    private final List<IOutputComponent> outputComponents;
    private final List<IOutputToolbarActionFactory> toolbarActionFactories;
    private final JLabel labelRunTime;
    private final JLabel labelRowCount;
    private final JLabel labelExecutionStatus;
    private final Timer executionTimer;
    private final JToolBar outputComponentToolbar = new JToolBar();
    private final QueryFileChangeListener queryFileChangeListener = new QueryFileChangeListener();

    private boolean suppressDocumentUpdateEvent = false;

    /** Reuse the same event to avoid creating new objects on every change */
    private final CaretChangedEvent caretEvent;

    private boolean resultCollapsed;
    private int prevDividerLocation;

    QueryFileView(QueryFileModel file, IEventBus eventBus, List<IOutputComponent> outputComponents, List<IOutputToolbarActionFactory> toolbarActionFactories, CompletionInstaller completionInstaller)
    {
        this.file = file;
        this.outputComponents = requireNonNull(outputComponents, "outputComponents");
        this.toolbarActionFactories = requireNonNull(toolbarActionFactories, "toolbarActionFactories");
        this.caretEvent = new CaretChangedEvent(file.getCaret());

        setLayout(new BorderLayout());

        // CSOFF
        textEditor = new TextEditorPane();
        textEditor.addParser(null);
        textEditor.setColumns(80);
        textEditor.setRows(40);
        textEditor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        textEditor.setCodeFoldingEnabled(true);
        textEditor.setBracketMatchingEnabled(true);
        textEditor.setTabSize(2);
        textEditor.setTabsEmulated(true);
        textEditor.setText(file.getQuery(false));

        requireNonNull(completionInstaller, "completionInstaller").install(file.getQuerySession(), file.getCatalogs(), textEditor);

        JFrame topFrame = (JFrame) SwingUtilities.getWindowAncestor(this);

        findDialog = new FindDialog(topFrame, this);
        findDialog.setIconImages(Constants.APPLICATION_ICONS);
        replaceDialog = new ReplaceDialog(topFrame, this);
        replaceDialog.setIconImages(Constants.APPLICATION_ICONS);
        SearchContext context = findDialog.getSearchContext();
        replaceDialog.setSearchContext(context);
        gotoDialog = new GoToDialog(topFrame);
        gotoDialog.setIconImages(Constants.APPLICATION_ICONS);

        KeyStroke pasteSpecialKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK);
        KeyStroke findKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK);
        KeyStroke replaceKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK);
        KeyStroke gotoKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK);

        InputMap inputMap = textEditor.getInputMap(JComponent.WHEN_FOCUSED);
        inputMap.put(pasteSpecialKeyStroke, PASTE_SPECIAL);
        inputMap.put(findKeyStroke, FIND);
        inputMap.put(replaceKeyStroke, REPLACE);
        inputMap.put(gotoKeyStroke, GOTO);

        textEditor.getActionMap()
                .put(PASTE_SPECIAL, pasteSpecialAction);
        textEditor.getActionMap()
                .put(FIND, showFindDialogAction);
        textEditor.getActionMap()
                .put(REPLACE, showReplaceDialogAction);
        textEditor.getActionMap()
                .put(GOTO, showGotoLineAction);

        RTextScrollPane sp = new RTextScrollPane(textEditor, true);
        textEditor.getDocument()
                .addDocumentListener(new ADocumentListenerAdapter()
                {
                    @Override
                    protected void update()
                    {
                        if (suppressDocumentUpdateEvent)
                        {
                            return;
                        }

                        suppressDocumentUpdateEvent = true;
                        try
                        {
                            file.setQuery(textEditor.getText());
                        }
                        finally
                        {
                            suppressDocumentUpdateEvent = false;
                        }
                    }
                });
        textEditor.addCaretListener(evt ->
        {
            int length = textEditor.getSelectionEnd() - textEditor.getSelectionStart();
            file.setCaret(textEditor.getCaretLineNumber() + 1, textEditor.getCaretOffsetFromLineStart() + 1, textEditor.getCaretPosition(), textEditor.getSelectionStart(), length);
            eventBus.publish(caretEvent);
        });

        JPanel tabPanel = new JPanel();
        tabPanel.setLayout(new OverlayLayout(tabPanel));

        resultTabs = new JTabbedPane();
        resultTabs.setAlignmentX(1.0f);
        resultTabs.setAlignmentY(0.0f);

        FlowLayout toolBarLayout = new FlowLayout(FlowLayout.TRAILING, 0, 0);
        toolBarLayout.setAlignOnBaseline(true);

        outputComponentToolbar.setMaximumSize(new Dimension(500, 30));
        outputComponentToolbar.setLayout(toolBarLayout);
        outputComponentToolbar.setFloatable(false);
        outputComponentToolbar.setOpaque(false);
        outputComponentToolbar.setAlignmentX(1.0f);
        outputComponentToolbar.setAlignmentY(0.0f);

        tabPanel.add(outputComponentToolbar);
        tabPanel.add(resultTabs);

        int index = 0;
        for (IOutputComponent ouputComponent : outputComponents)
        {
            resultTabs.add(ouputComponent.getComponent());
            resultTabs.setTabComponentAt(index, new OutputComponentTabComponent(ouputComponent));
            index++;
        }

        // Populate toolbar
        if (index > 0)
        {
            outputComponentChanged();
        }

        ErrorStrip errorStrip = new ErrorStrip(textEditor);
        JPanel editorPanel = new JPanel(new BorderLayout());
        editorPanel.add(sp);
        editorPanel.add(errorStrip, BorderLayout.LINE_END);

        resultTabs.addChangeListener(l -> outputComponentChanged());

        splitPane = new JSplitPane();
        splitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
        splitPane.setDividerLocation(400);
        splitPane.setLeftComponent(editorPanel);
        splitPane.setRightComponent(tabPanel);
        add(splitPane, BorderLayout.CENTER);

        JPanel panelStatus = new JPanel();
        panelStatus.setPreferredSize(new Dimension(10, 20));
        add(panelStatus, BorderLayout.SOUTH);
        panelStatus.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 0));
        // CSON
        labelExecutionStatus = new JLabel(getIconFromState(file.getState()));

        labelRunTime = new JLabel("", SwingConstants.LEFT);
        labelRunTime.setToolTipText("Last query run time");
        labelRowCount = new JLabel("", SwingConstants.LEFT);
        labelRowCount.setToolTipText("Last query row count");

        panelStatus.add(labelExecutionStatus);
        panelStatus.add(new JLabel("Time:"));
        panelStatus.add(labelRunTime);
        panelStatus.add(new JLabel("Rows:"));
        panelStatus.add(labelRowCount);

        executionTimer = new Timer(100, l -> setExecutionStats());

        file.addPropertyChangeListener(queryFileChangeListener);
        file.getQuerySession()
                .setPrintWriter(getMessagesWriter());
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

    private void outputComponentChanged()
    {
        OutputComponentTabComponent tabComponent = (OutputComponentTabComponent) resultTabs.getTabComponentAt(resultTabs.getSelectedIndex());
        tabComponent.populateToolbar();
    }

    private void handleStateChanged(QueryFileModel file, State state)
    {
        labelExecutionStatus.setIcon(getIconFromState(state));
        labelExecutionStatus.setToolTipText(state.getToolTip());

        // CSOFF
        switch (state)
        // CSON
        {
            case EXECUTING:
                IOutputExtension selectedOutputExtension = file.getOutputExtension();

                // Clear state of output components and select the one
                // that is choosen in toolbar
                int count = outputComponents.size();
                for (int i = 0; i < count; i++)
                {
                    IOutputComponent outputComponent = outputComponents.get(i);
                    outputComponent.clearState();
                    if (selectedOutputExtension != null
                            && selectedOutputExtension.getResultOutputComponentClass() != null
                            && outputComponent.getClass()
                                    .isAssignableFrom(selectedOutputExtension.getResultOutputComponentClass()))
                    {
                        resultTabs.setSelectedIndex(i);
                    }
                }

                executionTimer.start();
                clearHighLights();

                break;
            case COMPLETED:
                setExecutionStats();
                executionTimer.stop();
                break;
            case ABORTED:
                setExecutionStats();
                getMessagesWriter().println("Query was aborted!");
                executionTimer.stop();
                break;
            case ERROR:
                focusMessages();
                break;
        }

        // No rows, then show messages
        if (state != State.EXECUTING
                && file.getTotalRowCount() == 0)
        {
            focusMessages();
        }
    }

    /* IQueryFile */

    @Override
    public void focusMessages()
    {
        int count = outputComponents.size();
        for (int i = 0; i < count; i++)
        {
            IOutputComponent component = outputComponents.get(i);
            if (ITextOutputComponent.class.isAssignableFrom(component.getClass()))
            {
                resultTabs.setSelectedIndex(i);
                return;
            }
        }
    }

    @Override
    public PrintWriter getMessagesWriter()
    {
        return getOutputComponent(ITextOutputComponent.class).getTextWriter();
    }

    @Override
    public <T extends IOutputComponent> T getOutputComponent(Class<T> clazz)
    {
        int count = outputComponents.size();
        for (int i = 0; i < count; i++)
        {
            IOutputComponent component = outputComponents.get(i);
            if (clazz.isAssignableFrom(component.getClass()))
            {
                return clazz.cast(component);
            }
        }

        throw new RuntimeException("No output component found with extension class " + clazz);
    }

    @Override
    public IQuerySession getSession()
    {
        return file.getQuerySession();
    }

    @Override
    public void incrementTotalRowCount()
    {
        file.incrementTotalRowCount();
    }

    @Override
    public IOutputFormatExtension getOutputFormat()
    {
        return file.getOutputFormat();
    }

    @Override
    public void clearExecutionStats()
    {
        labelRunTime.setText("");
        labelRowCount.setText("");
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

    /* END IQueryFile */

    /** Create a selection from provided location. Translating ev. selected text */
    TextSelection getSelection(Location location)
    {
        if (location == null)
        {
            return TextSelection.EMPTY;
        }

        int selectionStart = 0;
        if (textEditor.getSelectionEnd() - textEditor.getSelectionStart() > 0)
        {
            selectionStart = textEditor.getSelectionStart();
        }

        int start = selectionStart + location.startOffset();
        int end = selectionStart + location.endOffset();
        return new TextSelection(start, end);
    }

    void highlight(TextSelection selection, Color color)
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

    void close()
    {
        file.removePropertyChangeListener(queryFileChangeListener);
    }

    void showFind()
    {
        showFindDialogAction.actionPerformed(null);
    }

    void showReplace()
    {
        showReplaceDialogAction.actionPerformed(null);
    }

    void showGoToLine()
    {
        showGotoLineAction.actionPerformed(null);
    }

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
            Object selected = JOptionPane.showInputDialog(QueryFileView.this, "Paste special", "Select which type to paste", JOptionPane.QUESTION_MESSAGE, null, values, values[0]);
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
    private final Action showFindDialogAction = new AbstractAction()
    {
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
    private final Action showReplaceDialogAction = new AbstractAction()
    {
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
    private final Action showGotoLineAction = new AbstractAction()
    {
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
                { // Never happens
                    UIManager.getLookAndFeel()
                            .provideErrorFeedback(textEditor);
                    ble.printStackTrace();
                }
            }
        }
    };

    private Icon getIconFromState(State state)
    {
        // CSOFF
        switch (state)
        // CSOn
        {
            case ABORTED:
                return Constants.CLOSE_ICON;
            case EXECUTING:
                return Constants.PLAY_ICON;
            case COMPLETED:
                return Constants.CHECK_CIRCLE_ICON;
            case ERROR:
                return Constants.WARNING_ICON;
        }

        return null;
    }

    private void setExecutionStats()
    {
        labelRunTime.setText(DurationFormatUtils.formatDurationHMS(file.getExecutionTime()));
        labelRowCount.setText(String.valueOf(file.getTotalRowCount()));
    }

    void toggleResultPane()
    {
        // Expanded
        if (!resultCollapsed)
        {
            prevDividerLocation = splitPane.getDividerLocation();
            splitPane.setDividerLocation(1.0d);
            resultCollapsed = true;
        }
        else
        {
            splitPane.setDividerLocation(prevDividerLocation);
            resultCollapsed = false;
        }
    }

    /**
     * <pre>
     * Toggle comments on selected lines
     * </pre>
     **/
    void toggleComments()
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

                if (Utils.between(startOffset, endOffset, selStart)
                        || (startOffset > selStart
                                && endOffset <= selEnd)
                        || Utils.between(startOffset, endOffset, selEnd))
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

    QueryFileModel getFile()
    {
        return file;
    }

    void saved()
    {
        textEditor.discardAllEdits();
    }

    private void clearHighLights()
    {
        int selStart = textEditor.getSelectionStart();
        int selEnd = textEditor.getSelectionEnd();

        textEditor.getHighlighter()
                .removeAllHighlights();

        textEditor.setSelectionStart(selStart);
        textEditor.setSelectionEnd(selEnd);
    }

    @Override
    public boolean requestFocusInWindow()
    {
        textEditor.requestFocusInWindow();
        return true;
    }

    /** Model listener */
    private class QueryFileChangeListener implements PropertyChangeListener
    {
        @Override
        public void propertyChange(PropertyChangeEvent evt)
        {
            if (QueryFileModel.STATE.equals(evt.getPropertyName()))
            {
                handleStateChanged(file, (State) evt.getNewValue());
            }
            else if (QueryFileModel.QUERY.equals(evt.getPropertyName()))
            {
                if (suppressDocumentUpdateEvent)
                {
                    return;
                }

                suppressDocumentUpdateEvent = true;
                try
                {
                    textEditor.setText(file.getQuery(false));
                }
                finally
                {
                    suppressDocumentUpdateEvent = false;
                }

            }
        }
    }

    private class OutputComponentTabComponent extends TabComponent
    {
        private IOutputComponent outputComponent;
        private List<JButton> toolbarActions;

        OutputComponentTabComponent(IOutputComponent outputComponent)
        {
            super(outputComponent.title(), outputComponent.icon());
            this.outputComponent = outputComponent;
        }

        void populateToolbar()
        {
            outputComponentToolbar.removeAll();
            if (toolbarActions == null)
            {
                toolbarActions = toolbarActionFactories.stream()
                        .map(f -> f.create(outputComponent))
                        .filter(Objects::nonNull)
                        .flatMap(l -> l.stream())
                        .sorted(Comparator.comparingInt(IExtensionAction::order))
                        .map(a -> new JButton(a.getAction()))
                        .collect(toList());
            }
            for (JButton button : toolbarActions)
            {
                outputComponentToolbar.add(button);
            }
        }
    }
}
