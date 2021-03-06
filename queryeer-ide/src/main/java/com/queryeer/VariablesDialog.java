package com.queryeer;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.WindowConstants;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.TextEditorPane;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Variables dialog */
class VariablesDialog extends JDialog
{
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final TextEditorPane textEditor;
    private boolean cancel;

    VariablesDialog(JFrame parent)
    {
        super(parent, true);
        getContentPane().setLayout(new BorderLayout());
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);

        JButton ok = new JButton("OK");
        ok.addActionListener(l ->
        {
            this.setVisible(false);
        });
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(l ->
        {
            this.cancel = true;
            this.setVisible(false);
        });

        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new FlowLayout());
        bottomPanel.add(ok);
        bottomPanel.add(cancel);

        getContentPane().add(bottomPanel, BorderLayout.SOUTH);

        textEditor = new TextEditorPane();
        textEditor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON);
        textEditor.setCodeFoldingEnabled(true);
        textEditor.setBracketMatchingEnabled(true);
        textEditor.setCaretPosition(0);

        getContentPane().add(new JScrollPane(textEditor), BorderLayout.CENTER);

        setPreferredSize(Constants.DEFAULT_DIALOG_SIZE);
        pack();
        setLocationRelativeTo(null);
    }

    /** Init dialog */
    void init(String title, Map<String, Object> existingVariables)
    {
        setTitle("Edit variables: " + title);
        cancel = false;
        try
        {
            textEditor.setText(QueryeerController.MAPPER.writeValueAsString(existingVariables));
        }
        catch (JsonProcessingException e)
        {
        }
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> getVariables()
    {
        if (cancel)
        {
            return null;
        }
        try
        {
            return MAPPER.readValue(textEditor.getText()
                    .replace("\\R+", ""), Map.class);
        }
        catch (JsonProcessingException e)
        {
            return null;
        }
    }
}
