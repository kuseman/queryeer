package com.queryeer;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

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
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.OverlayLayout;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.text.BadLocationException;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.fife.ui.rsyntaxtextarea.SquiggleUnderlineHighlightPainter;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.TextEditorPane;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.kordamp.ikonli.swing.FontIcon;

import com.queryeer.QueryFileModel.State;
import com.queryeer.api.IQueryFile;
import com.queryeer.api.extensions.output.IOutputComponent;
import com.queryeer.api.extensions.output.IOutputExtension;
import com.queryeer.api.extensions.output.IOutputFormatExtension;

import se.kuseman.payloadbuilder.api.session.IQuerySession;

/** Content of a query editor. Text editor and a result panel separated with a split panel */
class QueryFileView extends JPanel implements IQueryFile
{
    private static final String PASTE_SPECIAL = "PASTE_SPECIAL";

    private final JSplitPane splitPane;
    private final TextEditorPane textEditor;
    private final JTabbedPane resultTabs;
    private final QueryFileModel file;
    private final List<IOutputComponent> outputComponents;
    private final JLabel labelRunTime;
    private final JLabel labelRowCount;
    private final JLabel labelExecutionStatus;
    private final Timer executionTimer;

    private boolean resultCollapsed;
    private int prevDividerLocation;

    QueryFileView(QueryFileModel file, List<IOutputExtension> outputExtensions, Consumer<String> textChangeAction, Consumer<QueryFileView> caretChangeListener)
    {
        this.file = file;
        this.outputComponents = outputExtensions.stream()
                .map(e -> e.createResultComponent(this))
                .filter(Objects::nonNull)
                .collect(toList());

        setLayout(new BorderLayout());

        // CSOFF
        textEditor = new TextEditorPane();
        textEditor.setColumns(80);
        textEditor.setRows(40);
        textEditor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        textEditor.setCodeFoldingEnabled(true);
        textEditor.setBracketMatchingEnabled(true);
        textEditor.setTabSize(2);
        textEditor.setTabsEmulated(true);
        textEditor.setText(file.getQuery());

        // Paste special
        KeyStroke pasteSpecialKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK);
        InputMap inputMap = textEditor.getInputMap();
        inputMap.put(pasteSpecialKeyStroke, PASTE_SPECIAL);
        textEditor.getActionMap()
                .put(PASTE_SPECIAL, pasteSpecialAction);

        RTextScrollPane sp = new RTextScrollPane(textEditor);
        textEditor.getDocument()
                .addDocumentListener(new ADocumentListenerAdapter()
                {
                    @Override
                    protected void update()
                    {
                        textChangeAction.accept(textEditor.getText());
                    }
                });
        textEditor.addCaretListener(evt -> caretChangeListener.accept(this));

        JPanel tabPanel = new JPanel();
        tabPanel.setLayout(new OverlayLayout(tabPanel));

        resultTabs = new JTabbedPane();
        resultTabs.setAlignmentX(1.0f);
        resultTabs.setAlignmentY(0.0f);

        int index = 0;
        for (IOutputComponent ouputComponent : outputComponents)
        {
            resultTabs.add(ouputComponent.getComponent());
            resultTabs.setTabComponentAt(index, TabComponentView.queryResultHeader(ouputComponent, ouputComponent.title(), ouputComponent.icon()));
            index++;
        }

        JButton clearButton = new JButton(FontIcon.of(FontAwesome.REMOVE));
        clearButton.setToolTipText("Clear");
        clearButton.setOpaque(false);
        clearButton.setAlignmentX(1.0f);
        clearButton.setAlignmentY(0.0f);

        tabPanel.add(clearButton);
        tabPanel.add(resultTabs);

        splitPane = new JSplitPane();
        splitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
        splitPane.setDividerLocation(400);
        splitPane.setLeftComponent(sp);
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
        file.addPropertyChangeListener(l ->
        {
            if (QueryFileModel.STATE.equals(l.getPropertyName()))
            {
                handleStateChanged(file, (State) l.getNewValue());
            }
        });

        file.getQuerySession()
                .setPrintWriter(getMessagesWriter());
    }

    @Override
    public void focusMessages()
    {
        int count = resultTabs.getTabCount();
        for (int i = 0; i < count; i++)
        {
            IOutputComponent component = ((TabComponentView) resultTabs.getTabComponentAt(i)).getOutputComponent();
            if (component != null
                    && ITextOutputComponent.class.isAssignableFrom(component.getClass()))
            {
                resultTabs.setSelectedIndex(i);
                return;
            }
        }
    }

    @Override
    public PrintWriter getMessagesWriter()
    {
        int count = resultTabs.getTabCount();
        for (int i = 0; i < count; i++)
        {
            IOutputComponent component = ((TabComponentView) resultTabs.getTabComponentAt(i)).getOutputComponent();
            if (component != null
                    && ITextOutputComponent.class.isAssignableFrom(component.getClass()))
            {
                return ((ITextOutputComponent) component).getTextWriter();
            }
        }
        return null;
    }
    //
    // @Override
    // public <T extends IOutputComponent> void focusOutputComponent(Class<T> clazz)
    // {
    // int count = resultTabs.getTabCount();
    // for (int i = 0; i < count; i++)
    // {
    // IOutputComponent component = ((TabComponentView) resultTabs.getTabComponentAt(i)).getOutputComponent();
    // if (component != null
    // && clazz.isAssignableFrom(component.getClass()))
    // {
    // resultTabs.setSelectedIndex(i);
    // return;
    // }
    // }
    // }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends IOutputComponent> T getOutputComponent(Class<T> clazz)
    {
        int count = resultTabs.getTabCount();
        for (int i = 0; i < count; i++)
        {
            IOutputComponent component = ((TabComponentView) resultTabs.getTabComponentAt(i)).getOutputComponent();
            if (component != null
                    && clazz.isAssignableFrom(component.getClass()))
            {
                return (T) component;
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
                int count = resultTabs.getTabCount();
                for (int i = 0; i < count; i++)
                {
                    IOutputComponent outputComponent = ((TabComponentView) resultTabs.getTabComponentAt(i)).getOutputComponent();
                    outputComponent.clearState();
                    if (selectedOutputExtension.getClass() == outputComponent.getExtensionClass())
                    {
                        resultTabs.setSelectedIndex(i);
                    }
                }

                file.clearForExecution();
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
                getMessagesWriter().println(file.getError());
                if (file.getParseErrorLocation() != null)
                {
                    highLight(file.getParseErrorLocation()
                            .getKey(),
                            file.getParseErrorLocation()
                                    .getValue());
                }
                break;
        }

        // No rows, then show messages
        if (state != State.EXECUTING
                && file.getTotalRowCount() == 0)
        {
            focusMessages();
        }
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

    int getCaretLineNumber()
    {
        return textEditor.getCaretLineNumber() + 1;
    }

    int getCaretOffsetFromLineStart()
    {
        return textEditor.getCaretOffsetFromLineStart() + 1;
    }

    int getCaretPosition()
    {
        return textEditor.getCaretPosition();
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

    QueryFileModel getFile()
    {
        return file;
    }

    String getQuery(boolean selected)
    {
        return selected
                && !isBlank(textEditor.getSelectedText()) ? textEditor.getSelectedText()
                        : textEditor.getText();
    }

    void saved()
    {
        textEditor.discardAllEdits();
    }

    private void highLight(int line, int column)
    {
        try
        {
            int pos = Math.max(textEditor.getLineStartOffset(line - 1) + column - 1, 0);
            textEditor.getHighlighter()
                    .addHighlight(pos, pos + 3, new SquiggleUnderlineHighlightPainter(Color.RED));
        }
        catch (BadLocationException e)
        {
        }
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

    private boolean between(int start, int end, int value)
    {
        return value >= start
                && value <= end;
    }
}
