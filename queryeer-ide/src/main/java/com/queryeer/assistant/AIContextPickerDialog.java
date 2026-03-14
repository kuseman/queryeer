package com.queryeer.assistant;

import static java.util.stream.Collectors.toList;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.extensions.assistant.IAIContextItem;
import com.queryeer.api.extensions.engine.IQueryEngine;

/** Modal dialog for selecting AI context objects from the current query engine. */
class AIContextPickerDialog extends JDialog
{
    private final IQueryEngine engine;
    private final IQueryFile queryFile;
    private final List<IAIContextItem> currentSelection;

    private JTextField searchField;
    private JPanel itemsPanel;
    private JLabel statusLabel;
    private JButton okButton;

    private List<IAIContextItem> allItems = new ArrayList<>();
    private final Map<IAIContextItem, JCheckBox> checkboxMap = new HashMap<>();
    private final Map<String, Boolean> groupExpanded = new HashMap<>();

    private boolean confirmed = false;

    // CSOFF
    AIContextPickerDialog(JFrame owner, IQueryEngine engine, IQueryFile queryFile, List<IAIContextItem> currentSelection)
    {
        super(owner, "Select Context Objects", true);
        this.engine = engine;
        this.queryFile = queryFile;
        this.currentSelection = currentSelection != null ? new ArrayList<>(currentSelection)
                : new ArrayList<>();
        initUI();
        loadItemsAsync();
    }
    // CSON

    private void initUI()
    {
        setPreferredSize(new Dimension(450, 560));
        getContentPane().setLayout(new BorderLayout(4, 4));

        // Top: filter field
        JPanel topPanel = new JPanel(new BorderLayout(4, 4));
        topPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 4, 6));
        searchField = new JTextField();
        searchField.getDocument()
                .addDocumentListener(new DocumentListener()
                {
                    @Override
                    public void insertUpdate(DocumentEvent e)
                    {
                        filterItems();
                    }

                    @Override
                    public void removeUpdate(DocumentEvent e)
                    {
                        filterItems();
                    }

                    @Override
                    public void changedUpdate(DocumentEvent e)
                    {
                        filterItems();
                    }
                });
        topPanel.add(new JLabel("Filter:"), BorderLayout.WEST);
        topPanel.add(searchField, BorderLayout.CENTER);
        getContentPane().add(topPanel, BorderLayout.NORTH);

        // Center: scrollable items panel
        itemsPanel = new JPanel();
        itemsPanel.setLayout(new BoxLayout(itemsPanel, BoxLayout.Y_AXIS));
        getContentPane().add(new JScrollPane(itemsPanel), BorderLayout.CENTER);

        // Bottom: status + buttons
        JPanel bottomPanel = new JPanel(new BorderLayout(4, 0));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(4, 6, 6, 6));

        statusLabel = new JLabel("Loading...");
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e ->
        {
            allItems.clear();
            checkboxMap.clear();
            groupExpanded.clear();
            statusLabel.setText("Loading...");
            okButton.setEnabled(false);
            loadItemsAsync();
        });
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        statusPanel.add(statusLabel);
        statusPanel.add(refreshButton);
        bottomPanel.add(statusPanel, BorderLayout.WEST);

        okButton = new JButton("OK");
        okButton.setEnabled(false);
        okButton.addActionListener(e ->
        {
            confirmed = true;
            dispose();
        });
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttonPanel.add(cancelButton);
        buttonPanel.add(okButton);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        getContentPane().add(bottomPanel, BorderLayout.SOUTH);
    }

    private void loadItemsAsync()
    {
        Thread thread = new Thread(() ->
        {
            List<IAIContextItem> items = engine.getAIContextItems(queryFile);
            SwingUtilities.invokeLater(() ->
            {
                allItems = items;
                rebuildPanel(preSelectedKeys());
                if (items.isEmpty())
                {
                    statusLabel.setText("No objects available (schema may still be loading)");
                }
                else
                {
                    statusLabel.setText(items.size() + " objects");
                }
                okButton.setEnabled(true);
            });
        }, "AIContextLoader");
        thread.setDaemon(true);
        thread.start();
    }

    private void filterItems()
    {
        rebuildPanel(captureCheckboxStates());
    }

    private Map<String, Boolean> preSelectedKeys()
    {
        Map<String, Boolean> states = new HashMap<>();
        if (!currentSelection.isEmpty())
        {
            for (IAIContextItem item : currentSelection)
            {
                states.put(itemKey(item), true);
            }
        }
        else
        {
            // No prior selection – pre-check items that the engine suggests as defaults
            // (e.g. tables referenced in the current query)
            for (IAIContextItem item : allItems)
            {
                if (item.isDefaultSelected())
                {
                    states.put(itemKey(item), true);
                }
            }
        }
        return states;
    }

    private Map<String, Boolean> captureCheckboxStates()
    {
        Map<String, Boolean> states = new HashMap<>();
        for (Map.Entry<IAIContextItem, JCheckBox> e : checkboxMap.entrySet())
        {
            states.put(itemKey(e.getKey()), e.getValue()
                    .isSelected());
        }
        return states;
    }

    private void rebuildPanel(Map<String, Boolean> states)
    {
        String filter = searchField.getText()
                .trim()
                .toLowerCase();
        checkboxMap.clear();
        itemsPanel.removeAll();

        Map<String, List<IAIContextItem>> groups = new TreeMap<>();
        for (IAIContextItem item : allItems)
        {
            if (filter.isEmpty()
                    || item.getLabel()
                            .toLowerCase()
                            .contains(filter)
                    || item.getGroup()
                            .toLowerCase()
                            .contains(filter))
            {
                groups.computeIfAbsent(item.getGroup(), k -> new ArrayList<>())
                        .add(item);
            }
        }

        for (Map.Entry<String, List<IAIContextItem>> entry : groups.entrySet())
        {
            String group = entry.getKey();
            List<IAIContextItem> groupItems = entry.getValue();

            groupItems.sort((a, b) ->
            {
                boolean sa = states.getOrDefault(itemKey(a), false);
                boolean sb = states.getOrDefault(itemKey(b), false);
                if (sa != sb)
                {
                    return sa ? -1
                            : 1;
                }
                return a.getLabel()
                        .compareToIgnoreCase(b.getLabel());
            });

            long selectedCount = groupItems.stream()
                    .filter(item -> states.getOrDefault(itemKey(item), false))
                    .count();

            boolean expanded = filter.isEmpty() ? groupExpanded.computeIfAbsent(group, k -> selectedCount > 0)
                    : true;

            // Build checkboxes first so the toggle button can reference them
            List<JCheckBox> groupCheckboxes = new ArrayList<>();
            for (IAIContextItem item : groupItems)
            {
                boolean selected = states.getOrDefault(itemKey(item), false);
                JCheckBox cb = new JCheckBox(item.getLabel(), selected);
                cb.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 4));
                cb.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
                cb.setVisible(expanded);
                checkboxMap.put(item, cb);
                groupCheckboxes.add(cb);
            }

            // Toggle button as group header
            String countSuffix = selectedCount > 0 ? " (" + selectedCount + " selected)"
                    : "";
            JButton toggleBtn = new JButton(AIChatWindow.getExpandedUnicodeChar(expanded) + group + countSuffix);
            toggleBtn.setFont(toggleBtn.getFont()
                    .deriveFont(Font.BOLD));
            toggleBtn.setBorderPainted(false);
            toggleBtn.setContentAreaFilled(false);
            toggleBtn.setFocusPainted(false);
            toggleBtn.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
            toggleBtn.setBorder(BorderFactory.createEmptyBorder(6, 4, 2, 4));
            toggleBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

            final String groupKey = group;
            toggleBtn.addActionListener(e ->
            {
                boolean nowExpanded = !groupExpanded.getOrDefault(groupKey, false);
                groupExpanded.put(groupKey, nowExpanded);
                long sel = groupCheckboxes.stream()
                        .filter(JCheckBox::isSelected)
                        .count();
                String suffix = sel > 0 ? " (" + sel + " selected)"
                        : "";
                toggleBtn.setText(AIChatWindow.getExpandedUnicodeChar(nowExpanded) + groupKey + suffix);
                groupCheckboxes.forEach(cb -> cb.setVisible(nowExpanded));
                itemsPanel.revalidate();
                itemsPanel.repaint();
            });

            itemsPanel.add(toggleBtn);
            groupCheckboxes.forEach(itemsPanel::add);
        }

        itemsPanel.add(Box.createVerticalGlue());
        itemsPanel.revalidate();
        itemsPanel.repaint();
    }

    private static String itemKey(IAIContextItem item)
    {
        return item.getGroup() + "\0" + item.getLabel();
    }

    List<IAIContextItem> getSelectedItems()
    {
        if (!confirmed)
        {
            return null;
        }
        return checkboxMap.entrySet()
                .stream()
                .filter(e -> e.getValue()
                        .isSelected())
                .map(Map.Entry::getKey)
                .collect(toList());
    }

    static List<IAIContextItem> show(JFrame owner, IQueryEngine engine, IQueryFile queryFile, List<IAIContextItem> currentSelection)
    {
        AIContextPickerDialog dlg = new AIContextPickerDialog(owner, engine, queryFile, currentSelection);
        dlg.pack();
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
        return dlg.getSelectedItems();
    }
}
