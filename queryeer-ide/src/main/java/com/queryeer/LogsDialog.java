package com.queryeer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.EnhancedPatternLayout;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.spi.LoggingEvent;

import com.queryeer.api.component.DialogUtils;

/** Logs dialog */
class LogsDialog extends DialogUtils.AFrame
{
    static JTextPane textPane;

    LogsDialog(JFrame parent)
    {
        super("Logs");
        if (textPane != null)
        {
            throw new IllegalArgumentException("Logs dialog instantiated multiple times");
        }

        initDialog();

        LogManager.getRootLogger()
                .addAppender(new LogsAppender());
    }

    private void initDialog()
    {
        setTitle("Logs");
        getContentPane().setLayout(new BorderLayout());

        textPane = new JTextPane();
        textPane.setEditable(false);

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton clear = new JButton("Clear");
        clear.setHorizontalAlignment(SwingConstants.LEFT);
        clear.addActionListener(l -> textPane.setText(""));
        topPanel.add(clear);

        getContentPane().add(topPanel, BorderLayout.NORTH);
        getContentPane().add(new JScrollPane(textPane), BorderLayout.CENTER);

        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        setPreferredSize(Constants.DEFAULT_DIALOG_SIZE);
        pack();
        setLocationRelativeTo(null);
        pack();
    }

    static class LogsAppender extends AppenderSkeleton
    {
        // CSOFF
        private static SimpleAttributeSet ERROR_ATT, WARN_ATT, INFO_ATT, DEBUG_ATT, TRACE_ATT, RESTO_ATT;
        // CSON

        static
        {
            // ERROR
            ERROR_ATT = new SimpleAttributeSet();
            ERROR_ATT.addAttribute(StyleConstants.CharacterConstants.Bold, Boolean.TRUE);
            ERROR_ATT.addAttribute(StyleConstants.CharacterConstants.Italic, Boolean.FALSE);
            ERROR_ATT.addAttribute(StyleConstants.CharacterConstants.Foreground, new Color(153, 0, 0));

            // WARN
            WARN_ATT = new SimpleAttributeSet();
            WARN_ATT.addAttribute(StyleConstants.CharacterConstants.Bold, Boolean.FALSE);
            WARN_ATT.addAttribute(StyleConstants.CharacterConstants.Italic, Boolean.FALSE);
            WARN_ATT.addAttribute(StyleConstants.CharacterConstants.Foreground, new Color(153, 76, 0));

            // INFO
            INFO_ATT = new SimpleAttributeSet();
            INFO_ATT.addAttribute(StyleConstants.CharacterConstants.Bold, Boolean.FALSE);
            INFO_ATT.addAttribute(StyleConstants.CharacterConstants.Italic, Boolean.FALSE);
            INFO_ATT.addAttribute(StyleConstants.CharacterConstants.Foreground, new Color(0, 0, 153));

            // DEBUG
            DEBUG_ATT = new SimpleAttributeSet();
            DEBUG_ATT.addAttribute(StyleConstants.CharacterConstants.Bold, Boolean.FALSE);
            DEBUG_ATT.addAttribute(StyleConstants.CharacterConstants.Italic, Boolean.TRUE);
            DEBUG_ATT.addAttribute(StyleConstants.CharacterConstants.Foreground, new Color(64, 64, 64));

            // TRACE
            TRACE_ATT = new SimpleAttributeSet();
            TRACE_ATT.addAttribute(StyleConstants.CharacterConstants.Bold, Boolean.FALSE);
            TRACE_ATT.addAttribute(StyleConstants.CharacterConstants.Italic, Boolean.TRUE);
            TRACE_ATT.addAttribute(StyleConstants.CharacterConstants.Foreground, new Color(153, 0, 76));

            // RESTO
            RESTO_ATT = new SimpleAttributeSet();
            RESTO_ATT.addAttribute(StyleConstants.CharacterConstants.Bold, Boolean.FALSE);
            RESTO_ATT.addAttribute(StyleConstants.CharacterConstants.Italic, Boolean.TRUE);
            RESTO_ATT.addAttribute(StyleConstants.CharacterConstants.Foreground, new Color(0, 0, 0));
        }

        LogsAppender()
        {
            setLayout(new EnhancedPatternLayout("%d{ISO8601} [%t] %p %c %x - %m%n%throwable"));
        }

        @Override
        public void close()
        {
        }

        @Override
        public boolean requiresLayout()
        {
            return true;
        }

        @Override
        protected void append(LoggingEvent event)
        {
            String formattedMsg = layout.format(event);

            SwingUtilities.invokeLater(() ->
            {
                JTextPane textPane = LogsDialog.textPane;

                try
                {
                    int limite = 1000;
                    int apaga = 200;
                    if (textPane.getDocument()
                            .getDefaultRootElement()
                            .getElementCount() > limite)
                    {
                        int end = getLineEndOffset(textPane, apaga);
                        replaceRange(textPane, null, 0, end);
                    }

                    if (event.getLevel() == Level.ERROR)
                        textPane.getDocument()
                                .insertString(textPane.getDocument()
                                        .getLength(), formattedMsg, ERROR_ATT);
                    else if (event.getLevel() == Level.WARN)
                        textPane.getDocument()
                                .insertString(textPane.getDocument()
                                        .getLength(), formattedMsg, WARN_ATT);
                    else if (event.getLevel() == Level.INFO)
                        textPane.getDocument()
                                .insertString(textPane.getDocument()
                                        .getLength(), formattedMsg, INFO_ATT);
                    else if (event.getLevel() == Level.DEBUG)
                        textPane.getDocument()
                                .insertString(textPane.getDocument()
                                        .getLength(), formattedMsg, DEBUG_ATT);
                    else if (event.getLevel() == Level.TRACE)
                        textPane.getDocument()
                                .insertString(textPane.getDocument()
                                        .getLength(), formattedMsg, TRACE_ATT);
                    else
                        textPane.getDocument()
                                .insertString(textPane.getDocument()
                                        .getLength(), formattedMsg, RESTO_ATT);
                }
                catch (BadLocationException e)
                {
                }

                textPane.setCaretPosition(textPane.getDocument()
                        .getLength());
            });
        }

        private int getLineCount(JTextPane textPane)
        {
            return textPane.getDocument()
                    .getDefaultRootElement()
                    .getElementCount();
        }

        private int getLineEndOffset(JTextPane textPane, int line) throws BadLocationException
        {
            int lineCount = getLineCount(textPane);
            if (line < 0)
            {
                throw new BadLocationException("Negative line", -1);
            }
            else if (line >= lineCount)
            {
                throw new BadLocationException("No such line", textPane.getDocument()
                        .getLength() + 1);
            }
            else
            {
                Element map = textPane.getDocument()
                        .getDefaultRootElement();
                Element lineElem = map.getElement(line);
                int endOffset = lineElem.getEndOffset();
                return ((line == lineCount - 1) ? (endOffset - 1)
                        : endOffset);
            }
        }

        private void replaceRange(JTextPane textPane, String str, int start, int end) throws IllegalArgumentException
        {
            if (end < start)
            {
                throw new IllegalArgumentException("end before start");
            }
            Document doc = textPane.getDocument();
            if (doc != null)
            {
                try
                {
                    if (doc instanceof AbstractDocument)
                    {
                        ((AbstractDocument) doc).replace(start, end - start, str, null);
                    }
                    else
                    {
                        doc.remove(start, end - start);
                        doc.insertString(start, str, null);
                    }
                }
                catch (BadLocationException e)
                {
                    throw new IllegalArgumentException(e.getMessage());
                }
            }
        }
    }
}
