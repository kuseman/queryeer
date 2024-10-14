package com.queryeer.output.text;

import static java.util.Objects.requireNonNull;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

import javax.swing.Icon;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.kordamp.ikonli.swing.FontIcon;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.TextSelection;
import com.queryeer.api.extensions.output.IOutputExtension;
import com.queryeer.api.extensions.output.text.ITextOutputComponent;

/** Text output component */
class TextOutputComponent extends JScrollPane implements ITextOutputComponent
{
    private static final String WARNING_LOCATION = "warningLocation";
    private final IQueryFile queryFile;
    private final JTextPane text;
    private final PrintWriter printWriter;
    private final IOutputExtension extension;

    TextOutputComponent(IOutputExtension extension, IQueryFile queryFile)
    {
        super(new JTextPane());
        this.extension = requireNonNull(extension, "extension");
        this.queryFile = requireNonNull(queryFile, "queryFile");
        this.text = (JTextPane) getViewport().getComponent(0);
        this.text.setFont(new Font("Consolas", Font.PLAIN, 13));
        this.text.addMouseListener(new TextMouseListener());
        this.printWriter = createPrintWriter();
    }

    @Override
    public String title()
    {
        return "Messages";
    }

    @Override
    public Icon icon()
    {
        return FontIcon.of(FontAwesome.FILE_TEXT_O);
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

        appendText(text + System.lineSeparator(), warning);
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
                final String string = new String(cbuf, off, len);
                appendText(string, null);
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
                // DO nothing on close
            }
        };
    }

    private void appendText(String string, AttributeSet attributeSet)
    {
        Runnable r = () ->
        {
            Document doc = text.getDocument();
            try
            {
                doc.insertString(doc.getLength(), string, attributeSet);
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

    private class TextMouseListener extends MouseAdapter
    {
        @Override
        public void mouseClicked(MouseEvent e)
        {
            if (e.getClickCount() == 2)
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
                        queryFile.select(warningLocation);
                    }
                }
            }
        }
    }
}
