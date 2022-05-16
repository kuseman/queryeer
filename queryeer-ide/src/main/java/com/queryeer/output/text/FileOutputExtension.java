package com.queryeer.output.text;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.CountingOutputStream;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.extensions.output.IOutputComponent;
import com.queryeer.api.extensions.output.IOutputExtension;
import com.queryeer.api.extensions.output.IOutputFormatExtension;

import se.kuseman.payloadbuilder.api.OutputWriter;

/** Output extension for writing to file */
class FileOutputExtension implements IOutputExtension
{
    private static final int FILE_WRITER_BUFFER_SIZE = 4096;

    @Override
    public String getTitle()
    {
        return "FILE";
    }

    @Override
    public int order()
    {
        return 30;
    }

    @Override
    public boolean supportsOutputFormats()
    {
        return true;
    }

    @Override
    public KeyStroke getKeyStroke()
    {
        return KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK);
    }

    @Override
    public Class<? extends IOutputComponent> getResultOutputComponentClass()
    {
        return TextOutputComponent.class;
    }

    @Override
    public OutputWriter createOutputWriter(IQueryFile file)
    {
        String filename = getOutputFileName();

        if (isBlank(filename))
        {
            return null;
        }

        IOutputFormatExtension outputFormat = file.getOutputFormat();
        if (outputFormat == null)
        {
            throw new IllegalArgumentException("No output format selected");
        }

        try
        {
            File outputFile = new File(filename);
            if (!outputFile.exists())
            {
                if (!outputFile.createNewFile())
                {
                    throw new RuntimeException("Error creating file " + filename + " as output.");
                }
            }
            // The framework should close this stream
            CountingOutputStream outputStream = new CountingOutputStream(new FileOutputStream(outputFile))
            {
                private boolean startMessageWritten = false;

                @Override
                protected synchronized void beforeWrite(int n)
                {
                    super.beforeWrite(n);
                    if (!startMessageWritten)
                    {
                        startMessageWritten = true;
                        file.getMessagesWriter()
                                .println("Writing to " + filename + System.lineSeparator());
                    }
                }

                @Override
                public void close() throws IOException
                {
                    super.close();
                    long bytes = getByteCount();
                    file.getMessagesWriter()
                            .println(FileUtils.byteCountToDisplaySize(bytes) + " written to " + filename + System.lineSeparator());
                }
            };
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), FILE_WRITER_BUFFER_SIZE);
            return outputFormat.createOutputWriter(file, writer);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Error creating FILE output writer", e);
        }
    }

    private String getOutputFileName()
    {
        JFileChooser fileChooser = new JFileChooser()
        {
            @Override
            public void approveSelection()
            {
                File f = getSelectedFile();
                if (f.exists()
                        && getDialogType() == SAVE_DIALOG)
                {
                    int result = JOptionPane.showConfirmDialog(this, "The file exists, overwrite?", "Existing file", JOptionPane.YES_NO_CANCEL_OPTION);
                    // CSOFF
                    switch (result)
                    // CSON
                    {
                        case JOptionPane.YES_OPTION:
                            super.approveSelection();
                            return;
                        case JOptionPane.NO_OPTION:
                            return;
                        case JOptionPane.CLOSED_OPTION:
                            return;
                        case JOptionPane.CANCEL_OPTION:
                            cancelSelection();
                            return;
                    }
                }
                super.approveSelection();
            }
        };
        fileChooser.setDialogTitle("Select output filename");
        int result = fileChooser.showSaveDialog(null);
        if (result == JFileChooser.APPROVE_OPTION)
        {
            return fileChooser.getSelectedFile()
                    .getAbsolutePath();
        }

        return null;
    }
}
