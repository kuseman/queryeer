package com.queryeer.output.text;

import java.awt.Component;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

import javax.swing.Icon;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.kordamp.ikonli.swing.FontIcon;

import com.queryeer.ITextOutputComponent;
import com.queryeer.api.extensions.output.IOutputExtension;

/** Text output component */
class TextOutputComponent extends JScrollPane implements ITextOutputComponent
{
    private final JTextArea text;
    private final PrintWriter printWriter;

    TextOutputComponent()
    {
        super(new JTextArea());
        this.text = (JTextArea) getViewport().getComponent(0);
        printWriter = createPrintWriter();
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
    public Component getComponent()
    {
        return this;
    }

    @Override
    public Class<? extends IOutputExtension> getExtensionClass()
    {
        return TextOutputExtension.class;
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
                Runnable r = () -> text.append(string);
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
}
