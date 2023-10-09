package com.queryeer.component;

import static java.util.Objects.requireNonNull;

import java.awt.KeyEventDispatcher;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.time.Instant;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.KeyStroke;

/** Key listener that detects multi key strokes like CTRL+K followed by CTRL+N */
public class MultiKeyStrokeKeyListener extends KeyAdapter implements KeyEventDispatcher
{
    private static final Set<Integer> FKEYS = Set.of(KeyEvent.VK_F1, KeyEvent.VK_F2, KeyEvent.VK_F3, KeyEvent.VK_F4, KeyEvent.VK_F5, KeyEvent.VK_F6, KeyEvent.VK_F7, KeyEvent.VK_F8, KeyEvent.VK_F9,
            KeyEvent.VK_F10, KeyEvent.VK_F11, KeyEvent.VK_F12);

    private final Consumer<MultiKeyStokeEvent> listener;
    private KeyStroke first = null;
    private Instant firstKeyInstant = null;

    public MultiKeyStrokeKeyListener(Consumer<MultiKeyStokeEvent> listener)
    {
        this.listener = requireNonNull(listener, "listener");
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e)
    {
        if (e.getID() == KeyEvent.KEY_RELEASED)
        {
            processKeyEvent(e);
        }
        return false;
    }

    @Override
    public void keyReleased(KeyEvent e)
    {
        processKeyEvent(e);
    }

    private void processKeyEvent(KeyEvent e)
    {
        // Only a modifier was pressed, do nothing but keep eventual first state
        // this to be able to for example first press CTRL + K then release
        // then press CTRL + C and still detect a double KeyStroke
        if (e.getKeyChar() == KeyEvent.CHAR_UNDEFINED
                && !FKEYS.contains(e.getKeyCode()))
        {
            return;
        }

        // No modifier was pressed, reset state
        if (e.getModifiersEx() == 0)
        {
            first = null;
            return;
        }

        if (first == null)
        {
            first = KeyStroke.getKeyStroke(e.getKeyCode(), e.getModifiersEx());
            firstKeyInstant = Instant.now();
        }
        else
        {
            // To long between presses
            if (Instant.now()
                    .toEpochMilli() - firstKeyInstant.toEpochMilli() > 500)
            {
                first = null;
                firstKeyInstant = null;
                return;
            }

            listener.accept(new MultiKeyStokeEvent(first, KeyStroke.getKeyStroke(e.getKeyCode(), e.getModifiersEx())));
            first = null;
            firstKeyInstant = null;
        }
    }

    /** Event used when firing a multi key stroke */
    public record MultiKeyStokeEvent(KeyStroke first, KeyStroke second)
    {
    }
}
