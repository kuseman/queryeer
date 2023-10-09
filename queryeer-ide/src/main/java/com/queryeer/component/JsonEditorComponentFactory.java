package com.queryeer.component;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.fife.ui.rsyntaxtextarea.FileLocation;
import org.fife.ui.rsyntaxtextarea.TextEditorPane;
import org.fife.ui.rtextarea.RTextScrollPane;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queryeer.api.component.IJsonEditorComponentFactory;
import com.queryeer.editor.ADocumentListenerAdapter;

/** Factory for json editors. Uses RSyntax texteditor and jackson mapper to edit json and deserialize result */
class JsonEditorComponentFactory implements IJsonEditorComponentFactory
{
    @Override
    public <T> IJsonEditorComponent<T> create(Class<T> typeClazz, Function<T, String> verifier)
    {
        return new JsonEditorComponent<T>(typeClazz, verifier);
    }

    /** JSON editor */
    private static class JsonEditorComponent<T> extends JPanel implements IJsonEditorComponent<T>
    {
        private static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        private final List<PropertyChangeListener> propertyChangeListeners = new ArrayList<>();
        private TextEditorPane textEditor;
        private Class<T> typeClazz;
        private Function<T, String> verifier;

        JsonEditorComponent(Class<T> typeClazz, Function<T, String> verifier)
        {
            this.typeClazz = requireNonNull(typeClazz, "typeClazz");
            this.verifier = requireNonNull(verifier, "verifier");
            initComponent();
        }

        @Override
        public Component getComponent()
        {
            return this;
        }

        @Override
        public T getResult()
        {
            if (isBlank(textEditor.getText()))
            {
                return null;
            }

            try
            {
                return MAPPER.readValue(textEditor.getText(), typeClazz);
            }
            catch (Exception ee)
            {
                return null;
            }
        }

        @Override
        public void load(File file)
        {
            try
            {
                textEditor.load(FileLocation.create(file), StandardCharsets.UTF_8);
            }
            catch (IOException e)
            {
                throw new RuntimeException("Error loading from file: " + file.getAbsolutePath(), e);
            }
        }

        @Override
        public void save(File file)
        {
            try
            {
                textEditor.saveAs(FileLocation.create(file));
            }
            catch (IOException e)
            {
                throw new RuntimeException("Error saving file: " + file.getAbsolutePath(), e);
            }
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener listener)
        {
            propertyChangeListeners.add(listener);
        }

        private void initComponent()
        {
            setLayout(new GridBagLayout());

            textEditor = new TextEditorPane();
            textEditor.setColumns(10);
            textEditor.setRows(10);
            textEditor.setTabsEmulated(true);
            textEditor.setEncoding("UTF-8");
            textEditor.setSyntaxEditingStyle(TextEditorPane.SYNTAX_STYLE_JSON);

            textEditor.getDocument()
                    .addDocumentListener(new ADocumentListenerAdapter()
                    {
                        @Override
                        protected void update()
                        {
                            propertyChangeListeners.forEach(p -> p.propertyChange(new PropertyChangeEvent(this, CONTENT, textEditor.getText(), textEditor.getText())));
                        }
                    });

            JButton verifyButton = new JButton("Verify");
            verifyButton.addActionListener(e -> verify());
            JButton prettyButton = new JButton("Pretty");
            prettyButton.addActionListener(e -> pretty());

            add(verifyButton, new GridBagConstraints(0, 1, 1, 1, 0.0d, 0.0d, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 3, 3), 0, 0));
            add(prettyButton, new GridBagConstraints(1, 1, 1, 1, 0.0d, 0.0d, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
            add(new RTextScrollPane(textEditor), new GridBagConstraints(0, 2, 3, 1, 1.0d, 1.0d, GridBagConstraints.NORTH, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
        }

        private void verify()
        {
            String text = textEditor.getText();
            if (isBlank(text))
            {
                return;
            }
            T value;
            try
            {
                value = MAPPER.readValue(textEditor.getText(), typeClazz);
            }
            catch (Exception ee)
            {
                JOptionPane.showMessageDialog(this, "An error occured when deserializing JSON: " + ee.getMessage(), "Error deserializing", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (verifier != null)
            {
                String message = verifier.apply(value);
                if (!isBlank(message))
                {
                    JOptionPane.showMessageDialog(this, message, "Error verifying", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            JOptionPane.showMessageDialog(this, "Ok", "Verify", JOptionPane.INFORMATION_MESSAGE);
        }

        private void pretty()
        {
            String text = textEditor.getText();
            try
            {
                Object value = MAPPER.readValue(text, Object.class);

                DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
                prettyPrinter.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);

                text = MAPPER.writer(prettyPrinter)
                        .writeValueAsString(value);
            }
            catch (Exception e)
            {
                // SWALLOW
            }
            textEditor.setText(text);
        }

        // private String toJson(Object value)
        // {
        // if (value == null)
        // {
        // return "";
        // }
        //
        // try
        // {
        // return MAPPER.writeValueAsString(value);
        // }
        // catch (Exception e)
        // {
        // return "";
        // }
        // }
    }
}
