package com.queryeer.api.utils;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.HeadlessException;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;

/** Utils for handling exceptions */
public class ExceptionUtils
{
    /** Show a dialog with an unhandled exception */
    public static void showExceptionMessage(Component parentComponent, Exception exception) throws HeadlessException
    {
        StringWriter stringWriter = new StringWriter();
        exception.printStackTrace(new PrintWriter(stringWriter));

        JLabel message = new JLabel(exception.getMessage());
        message.setBorder(BorderFactory.createEmptyBorder(3, 0, 10, 0));

        JTextArea text = new JTextArea();
        text.setEditable(false);
        text.setFont(UIManager.getFont("Label.font"));
        text.setText(stringWriter.toString());
        text.setCaretPosition(0);

        JScrollPane scroller = new JScrollPane(text);
        scroller.setPreferredSize(new Dimension(600, 200));

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        panel.add(message, BorderLayout.NORTH);
        panel.add(scroller, BorderLayout.SOUTH);

        JOptionPane.showMessageDialog(parentComponent, panel, "Error", JOptionPane.ERROR_MESSAGE);
    }
}
