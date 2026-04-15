package com.queryeer.output.text;

import static java.util.Objects.requireNonNull;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.FocusManager;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.fife.rsta.ui.search.FindDialog;
import org.fife.rsta.ui.search.SearchEvent;
import org.fife.rsta.ui.search.SearchListener;
import org.fife.ui.rtextarea.SearchContext;
import org.kordamp.ikonli.fontawesome.FontAwesome;

import com.queryeer.Constants;
import com.queryeer.IconFactory;
import com.queryeer.api.IQueryFile;
import com.queryeer.api.editor.IEditor;
import com.queryeer.api.editor.ITextEditor;
import com.queryeer.api.editor.TextSelection;
import com.queryeer.api.extensions.output.IOutputExtension;
import com.queryeer.api.extensions.output.text.ITextOutputComponent;

/** Text output component */
class TextOutputComponent extends JScrollPane implements ITextOutputComponent, SearchListener
{
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(BasicThreadFactory.builder()
            .daemon(true)
            .namingPattern("TextOutputComponentAppender-%d")
            .build());
    private static final String FIND = "FIND";
    private static final String WARNING_LOCATION = "warningLocation";
    private static final String LINK_ACTION = "linkAction";
    private static final Color HYPERLINK = new Color(0, 0, 238);
    private final IQueryFile queryFile;
    private final JTextPane text;
    private final Document document;
    private final PrintWriter printWriter;
    private final IOutputExtension extension;
    private final TextFindDialog findDialog;

    private Future<?> currentAppender;
    private Queue<Chunk> chunkQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean abortAppend;
    private volatile int appendGeneration = 0;
    private int findPos = 0;

    TextOutputComponent(IOutputExtension extension, IQueryFile queryFile)
    {
        super(new JTextPane());
        this.extension = requireNonNull(extension, "extension");
        this.queryFile = requireNonNull(queryFile, "queryFile");
        this.text = (JTextPane) getViewport().getComponent(0);
        this.document = this.text.getDocument();
        this.text.setFont(new Font("Consolas", Font.PLAIN, 13));
        TextMouseListener textMouseListener = new TextMouseListener();
        this.text.addMouseListener(textMouseListener);
        this.text.addMouseMotionListener(textMouseListener);
        this.printWriter = createPrintWriter();

        KeyStroke findKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_F, Toolkit.getDefaultToolkit()
                .getMenuShortcutKeyMaskEx());
        InputMap inputMap = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        inputMap.put(findKeyStroke, FIND);

        ActionMap actionMap = getActionMap();
        actionMap.put(FIND, new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                showFind();
            }
        });

        findDialog = new TextFindDialog();
    }

    @Override
    public String title()
    {
        return "Messages";
    }

    @Override
    public Icon icon()
    {
        return IconFactory.of(FontAwesome.FILE_TEXT_O);
    }

    @Override
    public IOutputExtension getExtension()
    {
        return extension;
    }

    @Override
    public Component getComponent()
    {
        return this;
    }

    @Override
    public void clearState()
    {
        text.setText("");
        abortInternal();
    }

    @Override
    public void abort()
    {
        abortInternal();
    }

    @Override
    public PrintWriter getTextWriter()
    {
        return printWriter;
    }

    @Override
    public void appendWarning(String text, TextSelection textSelection)
    {
        SimpleAttributeSet warning = new SimpleAttributeSet();
        StyleConstants.setForeground(warning, Color.RED);
        warning.addAttribute(WARNING_LOCATION, textSelection);
        verifyAppenderThread();
        chunkQueue.add(new Chunk(text + System.lineSeparator(), warning));
    }

    @Override
    public void appendLink(String message, Runnable action)
    {
        SimpleAttributeSet link = new SimpleAttributeSet();
        StyleConstants.setForeground(link, HYPERLINK);
        StyleConstants.setUnderline(link, true);
        link.addAttribute(LINK_ACTION, action);
        verifyAppenderThread();
        chunkQueue.add(new Chunk(message + System.lineSeparator(), link));
    }

    @Override
    public void searchEvent(SearchEvent e)
    {
        SearchContext context = e.getSearchContext();
        String searchFor = context.getSearchFor();
        if (searchFor == null
                || searchFor.isEmpty())
        {
            return;
        }

        try
        {
            String content = document.getText(0, document.getLength());
            int matchStart = -1;
            int matchLength = searchFor.length();

            if (context.isRegularExpression())
            {
                Pattern pattern = context.getMatchCase() ? Pattern.compile(searchFor)
                        : Pattern.compile(searchFor, Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(content);
                if (matcher.find(findPos))
                {
                    matchStart = matcher.start();
                    matchLength = matcher.end() - matcher.start();
                }
                else if (context.getSearchWrap())
                {
                    matcher.reset();
                    if (matcher.find())
                    {
                        matchStart = matcher.start();
                        matchLength = matcher.end() - matcher.start();
                    }
                }
            }
            else
            {
                String searchContent = context.getMatchCase() ? content
                        : content.toLowerCase();
                String searchTerm = context.getMatchCase() ? searchFor
                        : searchFor.toLowerCase();
                matchStart = searchContent.indexOf(searchTerm, findPos);
                if (matchStart == -1
                        && context.getSearchWrap())
                {
                    matchStart = searchContent.indexOf(searchTerm);
                }
            }

            if (matchStart >= 0)
            {
                text.setCaretPosition(matchStart);
                text.moveCaretPosition(matchStart + matchLength);
                text.getCaret()
                        .setSelectionVisible(true);
                findPos = matchStart + matchLength;
                Shape viewShape = text.modelToView2D(matchStart);
                if (viewShape != null)
                {
                    text.scrollRectToVisible(viewShape.getBounds());
                }
            }
            else
            {
                JOptionPane.showMessageDialog(this, "No more hits", "Search", JOptionPane.INFORMATION_MESSAGE);
            }
        }
        catch (BadLocationException ex)
        {
            // ignore
        }
    }

    @Override
    public String getSelectedText()
    {
        return text.getSelectedText() != null ? text.getSelectedText()
                : "";
    }

    @Override
    public void dispose()
    {
        findDialog.setVisible(false);
        findDialog.dispose();
    }

    private void abortInternal()
    {
        synchronized (TextOutputComponent.this)
        {
            appendGeneration++;
            abortAppend = true;
            chunkQueue.clear();
        }
    }

    void showFind()
    {
        findDialog.setVisible(true);
    }

    private Runnable textAppender = () ->
    {
        final int generation = appendGeneration;
        List<Chunk> batch = new ArrayList<>();
        boolean done = false;
        long lastValueStamp = System.currentTimeMillis();

        while (!done)
        {
            if (abortAppend
                    || generation != appendGeneration)
            {
                chunkQueue.clear();
                break;
            }

            Chunk chunk = chunkQueue.poll();
            if (chunk != null)
            {
                lastValueStamp = System.currentTimeMillis();
                batch.add(chunk);
            }

            long timeSinceLastValue = System.currentTimeMillis() - lastValueStamp;

            // We reached batch size or we reached time threshold since last value
            if (batch.size() >= 500
                    || (!batch.isEmpty()
                            && timeSinceLastValue > 50))
            {
                appendBatch(batch, generation);
                batch.clear();
            }
            else if (batch.isEmpty())
            {
                try
                {
                    Thread.sleep(5);
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread()
                            .interrupt();
                    break;
                }
            }

            // Keep thread awake a bit before ending
            done = chunkQueue.isEmpty()
                    && timeSinceLastValue > 500;
        }
    };

    record Chunk(String text, AttributeSet attributes)
    {
    }

    private void appendBatch(List<Chunk> batch, int generation)
    {
        if (batch.isEmpty())
        {
            return;
        }
        List<Chunk> snapshot = new ArrayList<>(batch);
        Runnable r = () ->
        {
            if (appendGeneration != generation)
            {
                return;
            }
            for (Chunk b : snapshot)
            {
                try
                {
                    document.insertString(document.getLength(), b.text, b.attributes);
                }
                catch (BadLocationException e)
                {
                }
            }
        };

        if (SwingUtilities.isEventDispatchThread())
        {
            r.run();
        }
        else
        {
            try
            {
                SwingUtilities.invokeAndWait(r);
            }
            catch (InvocationTargetException | InterruptedException e)
            {
            }
        }
    }

    private void verifyAppenderThread()
    {
        synchronized (TextOutputComponent.this)
        {
            abortAppend = false;
            // Fire up a new appending thread if previously one is not running
            if (currentAppender == null
                    || currentAppender.isDone())
            {
                currentAppender = EXECUTOR.submit(textAppender);
            }
        }
    }

    private PrintWriter createPrintWriter()
    {
        // CSOFF
        Writer writer = new Writer()
        // CSON
        {
            @Override
            public void write(char[] cbuf, int off, int len) throws IOException
            {
                verifyAppenderThread();
                String string = new String(cbuf, off, len);
                chunkQueue.add(new Chunk(string, null));
            }

            @Override
            public void flush() throws IOException
            {
            }

            @Override
            public void close() throws IOException
            {
            }
        };

        return new PrintWriter(writer, true)
        {
            @Override
            public void close()
            {
            }
        };
    }

    private class TextFindDialog extends FindDialog
    {
        TextFindDialog()
        {
            super((JFrame) SwingUtilities.getWindowAncestor(TextOutputComponent.this), TextOutputComponent.this);
            setIconImages(Constants.APPLICATION_ICONS);

            context.setSearchWrap(true);
            context.setMarkAll(false);

            markAllCheckBox.setSelected(false);
            markAllCheckBox.setEnabled(false);
            wholeWordCheckBox.setEnabled(false);
            upButton.setEnabled(false);
            downButton.setEnabled(false);

            refreshUIFromContext();
            setLocationRelativeTo(getParent());
        }

        @Override
        public void setVisible(boolean visible)
        {
            if (visible)
            {
                // Start search from current selection position
                int selStart = text.getSelectionStart();
                findPos = selStart > 0 ? selStart
                        : 0;

                setLocationRelativeTo(FocusManager.getCurrentManager()
                        .getActiveWindow());
            }
            super.setVisible(visible);
        }
    }

    private class TextMouseListener extends MouseAdapter
    {
        @Override
        public void mouseClicked(MouseEvent e)
        {
            if (SwingUtilities.isLeftMouseButton(e)
                    && e.getClickCount() == 2)
            {
                int character = text.viewToModel2D(e.getPoint());
                if (character >= 0)
                {
                    Element element = ((StyledDocument) text.getDocument()).getCharacterElement(character);
                    TextSelection warningLocation = (TextSelection) element.getAttributes()
                            .getAttribute(WARNING_LOCATION);
                    if (warningLocation != null)
                    {
                        // Mark whole warning text
                        text.setSelectionStart(element.getStartOffset());
                        text.setSelectionEnd(element.getEndOffset());
                        IEditor editor = queryFile.getEditor();
                        if (editor instanceof ITextEditor)
                        {
                            ((ITextEditor) editor).select(warningLocation);
                        }
                    }
                }
            }
            else if (SwingUtilities.isLeftMouseButton(e)
                    && e.getClickCount() == 1)
            {
                int character = text.viewToModel2D(e.getPoint());
                if (character >= 0)
                {
                    Element element = ((StyledDocument) text.getDocument()).getCharacterElement(character);
                    Runnable action = (Runnable) element.getAttributes()
                            .getAttribute(LINK_ACTION);
                    if (action != null)
                    {
                        action.run();
                    }
                }
            }
        }

        @Override
        public void mouseMoved(MouseEvent e)
        {
            int character = text.viewToModel2D(e.getPoint());
            if (character >= 0)
            {
                Element element = ((StyledDocument) text.getDocument()).getCharacterElement(character);
                Runnable action = (Runnable) element.getAttributes()
                        .getAttribute(LINK_ACTION);
                text.setCursor(action != null ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            }
        }
    }
}
