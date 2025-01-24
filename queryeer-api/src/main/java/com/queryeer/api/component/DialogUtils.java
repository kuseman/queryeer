package com.queryeer.api.component;

import static java.util.Objects.requireNonNull;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.FocusManager;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JWindow;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import com.queryeer.api.utils.StringUtils;

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

    /** Model used in {@link QuickSearchWindow}. */
    public interface IQuickSearchModel<T extends IQuickSearchModel.Item>
    {
        /** Handle selection. Implementer handles hiding of the popup. */
        void handleSelection(JWindow dialog, T item);

        /**
         * Reload model. Consumer should be called with produced items.
         *
         * <pre>
         * NOTE! This method is called in a threaded fashion outside of EDT.
         * </pre>
         */
        void reload(Consumer<T> itemConsumer);

        /**
         * <pre>
         * Returns true if this model is enabled otherwise false. If false then dialog is never shown if triggered.
         * This to enable to have dynamic models that might don't have items in current context etc.
         * </pre>
         */
        default boolean isEnabled()
        {
            return true;
        }

        /** Return the index to select when dialog is shown. -1 if no selections should be done */
        default int getSelectedIndex()
        {
            return -1;
        }

        /** Item in quick search model. */
        public interface Item
        {
            /** Get title of item. */
            String getTitle();

            /** Get itcon of item. */
            Icon getIcon();

            /** Returns true if this item matches search text. */
            default boolean matches(String searchText)
            {
                return getTitle().toLowerCase()
                        .contains(searchText);
            }
        }
    }

    /**
     * Dialog for a popup window that has a search field with a list of items that can be used to quickly search for an item.
     */
    public static class QuickSearchWindow<T extends IQuickSearchModel.Item> extends JWindow
    {
        private final JList<T> items;
        private final DefaultListModel<T> itemsModel;
        private final JTextField search;
        private final IQuickSearchModel<T> model;
        private SwingWorker<Void, T> currentModelWorker;
        private SwingWorker<Void, T> currentFilterWorker;

        public QuickSearchWindow(Window parent, IQuickSearchModel<T> model)
        {
            super(parent);
            this.model = requireNonNull(model, "model");

            KeyListener closeListener = new KeyAdapter()
            {
                @Override
                public void keyTyped(KeyEvent e)
                {
                    if (e.getKeyChar() == KeyEvent.VK_ESCAPE)
                    {
                        setVisible(false);
                    }
                }
            };

            Consumer<T> selectionHandler = listItem ->
            {
                if (listItem == null)
                {
                    return;
                }
                model.handleSelection(this, listItem);
            };

            items = new JList<>();
            items.addKeyListener(closeListener);
            items.addKeyListener(new KeyAdapter()
            {
                @Override
                public void keyPressed(KeyEvent e)
                {
                    if (e.getKeyChar() == KeyEvent.VK_ENTER)
                    {
                        selectionHandler.accept(items.getSelectedValue());
                    }
                    else if (search.getFont()
                            .canDisplay(e.getKeyChar()))
                    {
                        search.setText(search.getText() + e.getKeyChar());
                        search.requestFocusInWindow();
                    }
                    super.keyPressed(e);
                }
            });
            items.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent e)
                {
                    if (SwingUtilities.isLeftMouseButton(e)
                            && e.getClickCount() == 2)
                    {
                        selectionHandler.accept(items.getSelectedValue());
                    }
                    super.mouseClicked(e);
                }
            });
            items.setCellRenderer(new DefaultListCellRenderer()
            {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
                {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    @SuppressWarnings("unchecked")
                    T listItem = (T) value;
                    setText(listItem.getTitle());
                    setIcon(listItem.getIcon());
                    return this;
                }
            });

            itemsModel = new DefaultListModel<>();
            items.setModel(itemsModel);

            search = new JTextField();
            search.addKeyListener(closeListener);

            // Move focus to list if we press arrows
            search.addKeyListener(new KeyAdapter()
            {
                @Override
                public void keyPressed(KeyEvent e)
                {
                    if (e.getKeyChar() == KeyEvent.VK_ENTER)
                    {
                        selectionHandler.accept(items.getSelectedValue());
                    }
                    else if (e.getKeyCode() == KeyEvent.VK_UP
                            || e.getKeyCode() == KeyEvent.VK_DOWN)
                    {
                        items.requestFocusInWindow();
                        if (items.getSelectedValue() == null)
                        {
                            items.setSelectedIndex(0);
                        }
                        else
                        {
                            int index = items.getSelectedIndex();
                            index += e.getKeyCode() == KeyEvent.VK_UP ? -1
                                    : 1;
                            items.setSelectedIndex(index);
                        }
                    }
                }
            });
            search.getDocument()
                    .addDocumentListener(new ADocumentListenerAdapter()
                    {
                        @Override
                        protected void update()
                        {
                            String text = search.getText()
                                    .toLowerCase();
                            if (StringUtils.isBlank(text))
                            {
                                items.setModel(itemsModel);
                                return;
                            }

                            // Cancel any prev. filtering
                            if (currentFilterWorker != null
                                    && !currentFilterWorker.isDone())
                            {
                                currentFilterWorker.cancel(true);
                            }

                            DefaultListModel<T> filteredItemsModel = new DefaultListModel<>();
                            currentFilterWorker = new SwingWorker<Void, T>()
                            {
                                @Override
                                protected Void doInBackground() throws Exception
                                {
                                    int count = itemsModel.getSize();
                                    for (int i = 0; i < count; i++)
                                    {
                                        if (this.isCancelled())
                                        {
                                            break;
                                        }
                                        T item = itemsModel.get(i);
                                        if (item.matches(text))
                                        {
                                            filteredItemsModel.addElement(item);
                                        }
                                    }
                                    return null;
                                }

                                @Override
                                protected void done()
                                {
                                    if (this.isCancelled())
                                    {
                                        return;
                                    }
                                    // Switch to the filtered model upon completion
                                    items.setModel(filteredItemsModel);
                                }
                            };

                            currentFilterWorker.execute();
                        }
                    });

            JButton cornerButton = new JButton("#");
            MouseAdapter cornerButtonAdapter = new MouseAdapter()
            {
                private Point origPos;

                @Override
                public void mouseDragged(MouseEvent e)
                {
                    Point newPos = e.getPoint();
                    SwingUtilities.convertPointToScreen(newPos, cornerButton);
                    int xdelta = newPos.x - origPos.x;
                    int ydelta = newPos.y - origPos.y;
                    Window wind = QuickSearchWindow.this;
                    int w = wind.getWidth();
                    if (newPos.x >= wind.getX())
                    {
                        w += xdelta;
                    }
                    int h = wind.getHeight();
                    if (newPos.y >= wind.getY())
                    {
                        h += ydelta;
                    }
                    wind.setSize(w, h);
                    origPos.setLocation(newPos);
                }

                @Override
                public void mousePressed(MouseEvent e)
                {
                    origPos = e.getPoint();
                    SwingUtilities.convertPointToScreen(origPos, cornerButton);
                }

                @Override
                public void mouseReleased(MouseEvent e)
                {
                    origPos = null;
                }

            };
            cornerButton.addMouseListener(cornerButtonAdapter);
            cornerButton.addMouseMotionListener(cornerButtonAdapter);
            cornerButton.setCursor(Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR));

            JScrollPane sp = new JScrollPane(items, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
            sp.setCorner(JScrollPane.LOWER_RIGHT_CORNER, cornerButton);

            JPanel contentPane = new JPanel(new BorderLayout());
            contentPane.add(search, BorderLayout.NORTH);
            contentPane.add(sp, BorderLayout.CENTER);
            setContentPane(contentPane);

            pack();

            // Focus search field when shown
            addComponentListener(new ComponentAdapter()
            {
                @Override
                public void componentShown(ComponentEvent e)
                {
                    search.requestFocusInWindow();
                    super.componentShown(e);
                }
            });
        }

        @Override
        public void setVisible(boolean b)
        {
            if (b)
            {
                if (!model.isEnabled())
                {
                    return;
                }
                // Initial load is not done yet, drop out
                else if (currentModelWorker != null
                        && !currentModelWorker.isDone())
                {
                    return;
                }

                search.setText("");
                itemsModel.clear();

                currentModelWorker = new SwingWorker<Void, T>()
                {
                    @Override
                    protected Void doInBackground() throws Exception
                    {
                        model.reload(t -> publish(t));
                        return null;
                    }

                    @Override
                    protected void process(List<T> chunks)
                    {
                        int index = model.getSelectedIndex();
                        for (T t : chunks)
                        {
                            itemsModel.addElement(t);
                            if (index >= 0
                                    && index != items.getSelectedIndex()
                                    && index < itemsModel.getSize())
                            {
                                items.setSelectedIndex(index);
                            }

                            // Show the dialog as soon as we have the first item
                            // This to avoid having an empty dialog if the model didn't
                            // produce any items
                            if (!isVisible())
                            {
                                QuickSearchWindow.super.setVisible(true);
                            }
                        }
                    }
                };

                currentModelWorker.execute();

                setSize(new Dimension(350, 350));
                Window activeWindow = javax.swing.FocusManager.getCurrentManager()
                        .getActiveWindow();
                setLocationRelativeTo(activeWindow);

                return;
            }
            super.setVisible(b);
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
