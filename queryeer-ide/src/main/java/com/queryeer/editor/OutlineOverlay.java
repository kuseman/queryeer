package com.queryeer.editor;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.kordamp.ikonli.fontawesome.FontAwesome;

import com.queryeer.IconFactory;
import com.queryeer.editor.OutlineScanner.OutlineEntry;
import com.queryeer.editor.OutlineScanner.ScanResult;

/**
 * Semi-transparent overlay panel that displays document outline entries in the top-right corner of the text editor. Supports collapse/expand, filter search, keyboard navigation, and
 * click-to-navigate.
 */
class OutlineOverlay extends JPanel
{
    private static final int MAX_WIDTH = 200;
    private static final float BACKGROUND_ALPHA = 0.70f;

    /** Callback: entry to navigate to, and whether to transfer focus to the editor. */
    private final BiConsumer<OutlineEntry, Boolean> onNavigate;
    private final JPanel headerPanel;
    private final JTextField filterField;
    private final JPanel entriesPanel;
    private final JScrollPane entriesScroll;
    private final JButton collapseButton;
    private final List<EntryLabel> allEntryLabels = new ArrayList<>();
    private final List<EntryLabel> visibleEntryLabels = new ArrayList<>();

    private boolean collapsed = false;
    private int selectedIndex = -1;

    OutlineOverlay(BiConsumer<OutlineEntry, Boolean> onNavigate)
    {
        this.onNavigate = onNavigate;

        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1), BorderFactory.createEmptyBorder(1, 3, 1, 3)));

        // Consume mouse wheel events so they don't propagate to the editor scroll pane
        addMouseWheelListener(e -> e.consume());

        // Header panel with filter field and collapse button
        headerPanel = new JPanel();
        headerPanel.setOpaque(false);
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.X_AXIS));

        filterField = new JTextField()
        {
            @Override
            protected void paintComponent(Graphics g)
            {
                // Paint a semi-transparent background before text rendering
                Color bg = UIManager.getColor("TextField.background");
                if (bg == null)
                {
                    bg = Color.WHITE;
                }
                Graphics2D g2 = (Graphics2D) g.create();
                try
                {
                    g2.setColor(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 180));
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }
                finally
                {
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        filterField.setFont(filterField.getFont()
                .deriveFont(11f));
        filterField.putClientProperty("JTextField.placeholderText", "Filter...");
        filterField.setOpaque(false);
        filterField.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1), BorderFactory.createEmptyBorder(1, 2, 1, 2)));
        filterField.getDocument()
                .addDocumentListener(new DocumentListener()
                {
                    @Override
                    public void insertUpdate(DocumentEvent e)
                    {
                        applyFilter();
                    }

                    @Override
                    public void removeUpdate(DocumentEvent e)
                    {
                        applyFilter();
                    }

                    @Override
                    public void changedUpdate(DocumentEvent e)
                    {
                        applyFilter();
                    }
                });

        // Down arrow in filter field moves selection into the entry list
        filterField.addKeyListener(new java.awt.event.KeyAdapter()
        {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e)
            {
                if (e.getKeyCode() == KeyEvent.VK_DOWN)
                {
                    moveSelection(1);
                    e.consume();
                }
                else if (e.getKeyCode() == KeyEvent.VK_UP)
                {
                    moveSelection(-1);
                    e.consume();
                }
                else if (e.getKeyCode() == KeyEvent.VK_ENTER)
                {
                    navigateToSelected();
                    e.consume();
                }
                else if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
                {
                    if (!filterField.getText()
                            .isEmpty())
                    {
                        filterField.setText("");
                    }
                    else
                    {
                        toggleCollapsed();
                    }
                    e.consume();
                }
            }
        });

        headerPanel.add(filterField);

        collapseButton = new JButton(IconFactory.of(FontAwesome.CHEVRON_UP, 10));
        collapseButton.setContentAreaFilled(false);
        collapseButton.setBorderPainted(false);
        collapseButton.setFocusable(false);
        collapseButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        collapseButton.setToolTipText("Collapse outline");
        collapseButton.addActionListener(e -> toggleCollapsed());
        headerPanel.add(collapseButton);

        add(headerPanel, BorderLayout.NORTH);

        // Entries panel inside a scroll pane
        entriesPanel = new JPanel();
        entriesPanel.setOpaque(false);
        entriesPanel.setLayout(new BoxLayout(entriesPanel, BoxLayout.Y_AXIS));

        entriesScroll = new JScrollPane(entriesPanel);
        entriesScroll.setOpaque(false);
        entriesScroll.getViewport()
                .setOpaque(false);
        entriesScroll.setBorder(null);
        entriesScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        entriesScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        entriesScroll.getVerticalScrollBar()
                .setUnitIncrement(10);
        add(entriesScroll, BorderLayout.CENTER);

        installKeyBindings();
    }

    private void installKeyBindings()
    {
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "outline.down");
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "outline.up");
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "outline.navigate");
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "outline.escape");

        getActionMap().put("outline.down", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                moveSelection(1);
            }
        });
        getActionMap().put("outline.up", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                moveSelection(-1);
            }
        });
        getActionMap().put("outline.navigate", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                navigateToSelected();
            }
        });
        getActionMap().put("outline.escape", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                toggleCollapsed();
            }
        });

        // Listen for LAF changes to update border colors
        UIManager.addPropertyChangeListener(new PropertyChangeListener()
        {
            @Override
            public void propertyChange(PropertyChangeEvent evt)
            {
                if ("lookAndFeel".equalsIgnoreCase(evt.getPropertyName()))
                {
                    updateBorder();
                }
            }
        });
    }

    /** Update entries from a scan result. Called on EDT. */
    void updateEntries(ScanResult result)
    {
        entriesPanel.removeAll();
        allEntryLabels.clear();
        visibleEntryLabels.clear();
        selectedIndex = -1;

        if (result == null
                || result.entries()
                        .isEmpty())
        {
            setVisible(false);
            return;
        }

        for (OutlineEntry entry : result.entries())
        {
            EntryLabel label = new EntryLabel(entry);
            allEntryLabels.add(label);
        }

        // Apply current filter and rebuild visible list
        applyFilter();

        setVisible(true);

        revalidate();
        repaint();
    }

    /** Toggle between collapsed and expanded state. */
    void toggleCollapsed()
    {
        collapsed = !collapsed;

        if (collapsed)
        {
            collapseButton.setIcon(IconFactory.of(FontAwesome.LIST_UL, 12));
            collapseButton.setToolTipText("Show outline");
            filterField.setVisible(false);
            entriesScroll.setVisible(false);
            setBorder(null);
        }
        else
        {
            collapseButton.setIcon(IconFactory.of(FontAwesome.CHEVRON_UP, 10));
            collapseButton.setToolTipText("Collapse outline");
            filterField.setVisible(true);
            entriesScroll.setVisible(true);
            updateBorder();
            filterField.requestFocusInWindow();
        }

        revalidate();
        repaint();
    }

    boolean isCollapsed()
    {
        return collapsed;
    }

    boolean hasEntries()
    {
        return !allEntryLabels.isEmpty();
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        if (collapsed)
        {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try
        {
            Color bg = UIManager.getColor("Panel.background");
            if (bg == null)
            {
                bg = getBackground();
            }
            g2.setComposite(AlphaComposite.SrcOver.derive(BACKGROUND_ALPHA));
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
        }
        finally
        {
            g2.dispose();
        }
    }

    @Override
    public Dimension getMaximumSize()
    {
        if (collapsed)
        {
            return getPreferredSize();
        }
        Component parent = getParent();
        int maxHeight = parent != null ? (int) (parent.getHeight() * 0.6)
                : 300;
        return new Dimension(MAX_WIDTH, Math.max(maxHeight, 60));
    }

    @Override
    public Dimension getPreferredSize()
    {
        if (collapsed)
        {
            // Just the icon button when collapsed
            Dimension btnPref = collapseButton.getPreferredSize();
            return new Dimension(btnPref.width + 4, btnPref.height + 4);
        }

        Dimension superPref = super.getPreferredSize();
        int width = Math.min(superPref.width, MAX_WIDTH);
        int height = Math.min(superPref.height, getMaximumSize().height);
        return new Dimension(width, height);
    }

    private void applyFilter()
    {
        entriesPanel.removeAll();
        visibleEntryLabels.clear();
        selectedIndex = -1;

        String filterText = filterField.getText()
                .trim()
                .toLowerCase(Locale.ROOT);
        for (EntryLabel label : allEntryLabels)
        {
            if (filterText.isEmpty()
                    || label.entry.label()
                            .toLowerCase(Locale.ROOT)
                            .contains(filterText))
            {
                visibleEntryLabels.add(label);
                entriesPanel.add(label);
            }
        }

        entriesPanel.revalidate();
        entriesPanel.repaint();
        revalidate();
        repaint();
    }

    private void moveSelection(int delta)
    {
        if (visibleEntryLabels.isEmpty()
                || collapsed)
        {
            return;
        }

        int newIndex = selectedIndex + delta;
        if (newIndex < 0)
        {
            newIndex = 0;
        }
        else if (newIndex >= visibleEntryLabels.size())
        {
            newIndex = visibleEntryLabels.size() - 1;
        }

        setSelectedIndex(newIndex);
    }

    private void setSelectedIndex(int index)
    {
        if (selectedIndex >= 0
                && selectedIndex < visibleEntryLabels.size())
        {
            visibleEntryLabels.get(selectedIndex)
                    .setSelected(false);
        }

        selectedIndex = index;

        if (selectedIndex >= 0
                && selectedIndex < visibleEntryLabels.size())
        {
            EntryLabel label = visibleEntryLabels.get(selectedIndex);
            label.setSelected(true);
            entriesPanel.scrollRectToVisible(label.getBounds());
            onNavigate.accept(label.entry, Boolean.FALSE);
        }
    }

    private void navigateToSelected()
    {
        if (selectedIndex >= 0
                && selectedIndex < visibleEntryLabels.size())
        {
            onNavigate.accept(visibleEntryLabels.get(selectedIndex).entry, Boolean.TRUE);
        }
    }

    private void updateBorder()
    {
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1), BorderFactory.createEmptyBorder(1, 3, 1, 3)));
    }

    /** A clickable label representing a single outline entry. */
    private class EntryLabel extends JLabel
    {
        private final OutlineEntry entry;
        private boolean hovered;
        private boolean selected;

        EntryLabel(OutlineEntry entry)
        {
            super(entry.label());
            this.entry = entry;
            setFont(getFont().deriveFont(11f));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(MAX_WIDTH - 12, getPreferredSize().height));
            setToolTipText("Line " + entry.lineNumber() + ": " + entry.label());

            addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent e)
                {
                    onNavigate.accept(entry, Boolean.TRUE);
                }

                @Override
                public void mouseEntered(MouseEvent e)
                {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e)
                {
                    hovered = false;
                    repaint();
                }
            });
        }

        void setSelected(boolean selected)
        {
            this.selected = selected;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            if (selected
                    || hovered)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                try
                {
                    Color highlight = UIManager.getColor("List.selectionBackground");
                    if (highlight == null)
                    {
                        highlight = new Color(100, 150, 220);
                    }
                    int alpha = selected ? 100
                            : 50;
                    g2.setColor(new Color(highlight.getRed(), highlight.getGreen(), highlight.getBlue(), alpha));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
                }
                finally
                {
                    g2.dispose();
                }
            }
            super.paintComponent(g);
        }
    }
}
