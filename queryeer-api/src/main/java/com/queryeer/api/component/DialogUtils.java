package com.queryeer.api.component;

import java.awt.Frame;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.FocusManager;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;

/** Utils when working with dialogs. */
public final class DialogUtils
{
    private static final KeyStroke ESC = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
    private static final KeyStroke CTRLW = KeyStroke.getKeyStroke(KeyEvent.VK_W, Toolkit.getDefaultToolkit()
            .getMenuShortcutKeyMaskEx());

    private DialogUtils()
    {
    }

    /** Base class for dialogs that handles close keyboard shortcuts etc. */
    public static class ADialog extends JDialog
    {
        private boolean iconImagesIsSet;

        public ADialog()
        {
            this((Frame) null, "", false);
        }

        public ADialog(Frame owner, String title, boolean modal)
        {
            super(owner, title, modal);
            bindCloseShortcuts(getRootPane());
        }

        /** Show the dialog where the active window is to be able to handle multiple monitors. */
        @Override
        public void setVisible(boolean b)
        {
            if (b)
            {
                if (!iconImagesIsSet)
                {
                    setIconImagesFromActive(this);
                    iconImagesIsSet = true;
                }

                Window activeWindow = javax.swing.FocusManager.getCurrentManager()
                        .getActiveWindow();
                setLocationRelativeTo(activeWindow);
                super.setVisible(b);
            }
            else if (shouldClose())
            {
                super.setVisible(b);
            }
        }

        protected boolean shouldClose()
        {
            return true;
        }
    }

    /** Base class for JFrame based dialogs that handles close keyboard shortcuts etc. */
    public static class AFrame extends JFrame
    {
        private boolean iconImagesIsSet;

        public AFrame()
        {
            this("");
        }

        public AFrame(String title)
        {
            super(title);
            bindCloseShortcuts(getRootPane());
        }

        /** Show the dialog where the active window is to be able to handle multiple monitors. */
        @Override
        public void setVisible(boolean b)
        {
            if (b)
            {
                if (!iconImagesIsSet)
                {
                    setIconImagesFromActive(this);
                    iconImagesIsSet = true;
                }

                Window activeWindow = javax.swing.FocusManager.getCurrentManager()
                        .getActiveWindow();
                setLocationRelativeTo(activeWindow);
                super.setVisible(b);
            }
            else if (shouldClose())
            {
                super.setVisible(b);
            }
        }

        protected boolean shouldClose()
        {
            return true;
        }
    }

    /** Binds ESC and CTRL/META-W to close a dialog */
    public static void bindCloseShortcuts(JRootPane rootPane)
    {
        ActionListener listener = new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                rootPane.getParent()
                        .setVisible(false);
            }
        };

        rootPane.registerKeyboardAction(listener, ESC, JComponent.WHEN_IN_FOCUSED_WINDOW);
        rootPane.registerKeyboardAction(listener, CTRLW, JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /**
     * Sets icon images on provided window based on the parent hierarchy. Basically uses the main windows icon images.
     */
    private static void setIconImagesFromActive(Window window)
    {
        Window activeWindow = FocusManager.getCurrentManager()
                .getActiveWindow();
        if (activeWindow == null)
        {
            return;
        }

        // Find first window in hierarchy that has icon images else use null
        List<Image> iconImages = activeWindow.getIconImages();
        while (activeWindow != null
                && (iconImages == null
                        || iconImages.isEmpty()))
        {
            activeWindow = activeWindow.getParent() instanceof Window w ? w
                    : null;
            iconImages = activeWindow != null ? activeWindow.getIconImages()
                    : null;
        }

        window.setIconImages(iconImages);
    }
}
