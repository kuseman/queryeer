package com.queryeer.api.component;

import java.awt.Component;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.Objects;

import javax.swing.event.SwingPropertyChangeSupport;

import com.queryeer.api.extensions.IExtensionAction;

/** Definition of a syntax text editor */
public interface ISyntaxTextEditor
{
    /** Get the actual component for this text editor */
    Component getComponent();

    /** Set parse error location */
    void setParseErrorLocation(int line, int charInLine);

    /** Clear eventual errors in editor */
    void clearErrors();

    /** Get the model from the editor */
    TextEditorModel getModel();

    /** Called when this editor closes. Should clean up resources etc. */
    void close();

    /** Return editor actions. */
    List<IExtensionAction> getActions();

    /** Model for text editor */
    class TextEditorModel
    {
        public static final String TEXT = "text";
        public static final String DIRTY = "dirty";

        private final SwingPropertyChangeSupport pcs = new SwingPropertyChangeSupport(this, true);
        private final Caret caret = new Caret();
        private String text = "";
        private String originalText = "";
        private boolean dirty = false;

        public Caret getCaret()
        {
            return caret;
        }

        /** Set caret */
        public void setCaret(int lineNumber, int offset, int position, int selectionStart, int selectionLength)
        {
            this.caret.setLineNumber(lineNumber);
            this.caret.setOffset(offset);
            this.caret.setPosition(position);
            this.caret.setSelectionStart(selectionStart);
            this.caret.setSelectionLength(selectionLength);
        }

        public String getText()
        {
            return text;
        }

        /**
         * Get text
         *
         * @param selected Only return the selected text. If none is selected then the whole text is returned
         */
        public String getText(boolean selected)
        {
            if (selected)
            {
                return caret.getSelectionLength() > 0 ? text.substring(caret.getSelectionStart(), caret.getSelectionStart() + caret.getSelectionLength())
                        : text;
            }

            return text;
        }

        /** Set text */
        public void setText(String text)
        {
            String old = this.text;
            if (!Objects.equals(old, text))
            {
                this.text = text;
                pcs.firePropertyChange(TEXT, old, this.text);
            }

            boolean dirty = !Objects.equals(this.originalText, this.text);
            if (this.dirty != dirty)
            {
                boolean oldDirty = this.dirty;
                this.dirty = dirty;
                pcs.firePropertyChange(DIRTY, oldDirty, this.dirty);
            }
        }

        public void resetBuffer()
        {
            this.originalText = text;
            this.dirty = false;
        }

        public void addPropertyChangeListener(PropertyChangeListener listener)
        {
            pcs.addPropertyChangeListener(listener);
        }

        public void removePropertyChangeListener(PropertyChangeListener listener)
        {
            pcs.removePropertyChangeListener(listener);
        }
    }
}
