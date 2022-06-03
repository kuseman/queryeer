package com.queryeer.provider.payloadbuilder;

import static com.queryeer.provider.payloadbuilder.PayloadbuilderQueryProvider.MAPPER;
import static java.util.Collections.emptySet;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.WindowConstants;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.TextEditorPane;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.queryeer.Constants;

import se.kuseman.payloadbuilder.core.parser.AExpressionVisitor;
import se.kuseman.payloadbuilder.core.parser.AStatementVisitor;
import se.kuseman.payloadbuilder.core.parser.Expression;
import se.kuseman.payloadbuilder.core.parser.QueryParser;
import se.kuseman.payloadbuilder.core.parser.QueryStatement;
import se.kuseman.payloadbuilder.core.parser.SetStatement;
import se.kuseman.payloadbuilder.core.parser.VariableExpression;

/** Variables dialog */
class VariablesDialog extends JDialog
{
    private static final QueryParser PARSER = new QueryParser();
    private static final VariableVisitor VISITOR = new VariableVisitor();

    private final TextEditorPane textEditor;
    private boolean cancel;

    VariablesDialog()
    {
        super((JFrame) null, true);
        setIconImages(Constants.APPLICATION_ICONS);
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
            textEditor.setText(MAPPER.writeValueAsString(existingVariables));
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

    /** Get named parameters from query. */
    static Set<String> getVariables(String query)
    {
        QueryStatement parsedQuery;
        try
        {
            parsedQuery = PARSER.parseQuery(query);
        }
        catch (Exception e)
        {
            // TODO: notify error parsing
            return emptySet();
        }
        Set<String> variables = new HashSet<>();
        parsedQuery.getStatements()
                .forEach(s -> s.accept(VISITOR, variables));
        return variables;
    }

    /** Variable visitor. */
    private static class VariableVisitor extends AStatementVisitor<Void, Set<String>>
    {
        private static final ExpressionVisitor EXPRESSION_VISITOR = new ExpressionVisitor();

        @Override
        protected void visitExpression(Set<String> context, Expression expression)
        {
            expression.accept(EXPRESSION_VISITOR, context);
        }

        @Override
        public Void visit(SetStatement statement, Set<String> context)
        {
            if (!statement.isSystemProperty())
            {
                context.add(statement.getName());
            }
            return null;
        }

        /** Expression visitor. */
        private static class ExpressionVisitor extends AExpressionVisitor<Void, Set<String>>
        {
            @Override
            public Void visit(VariableExpression expression, Set<String> context)
            {
                context.add(expression.getName());
                return null;
            }
        }
    }
}
