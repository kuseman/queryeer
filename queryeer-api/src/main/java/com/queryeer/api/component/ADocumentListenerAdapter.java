package com.queryeer.api.component;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Document listener adapter that makes it easier to add listeners on documents to matter what type of change it is. */
public abstract class ADocumentListenerAdapter implements DocumentListener
{
    @Override
    public void insertUpdate(DocumentEvent e)
    {
        update();
    }

    @Override
    public void removeUpdate(DocumentEvent e)
    {
        update();
    }

    @Override
    public void changedUpdate(DocumentEvent e)
    {
        update();
    }

    protected abstract void update();
}
