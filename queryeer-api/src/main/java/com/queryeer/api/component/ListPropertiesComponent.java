package com.queryeer.api.component;

import static java.util.Objects.requireNonNull;
import static se.kuseman.payloadbuilder.api.utils.StringUtils.isBlank;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

/**
 * Component with a editable list of items and a {@link PropertiesComponent} for selected item.
 *
 * @param <T> Type of object used in list/properties
 */
public class ListPropertiesComponent<T> extends JPanel
{
    private final PropertiesComponent propertiesComponent;
    private final Consumer<Boolean> dirtyConsumer;
    private final DefaultListModel<T> listModel = new DefaultListModel<>();
    private final Supplier<T> itemCreator;
    private final JList<T> itemList = new JList<>();

    /**
     * Construct a new list properties component
     *
     * @param clazz Type of items in the list
     * @param dirtyConsumer Consumer for handling dirty notifications
     * @param itemCreator Creator of new items
     */
    public ListPropertiesComponent(Class<?> clazz, Consumer<Boolean> dirtyConsumer, Supplier<T> itemCreator)
    {
        this.dirtyConsumer = requireNonNull(dirtyConsumer, "dirtyConsumer");
        this.itemCreator = requireNonNull(itemCreator, "itemCreator");
        this.propertiesComponent = new PropertiesComponent(requireNonNull(clazz, "clazz"), this::dirtyConsumer, false);
        initComponent();
    }

    /**
     * Init component with target list.
     *
     * <pre>
     * NOTE! A deep copy if the list should be provided to be able to revert changes made
     * </pre>
     */
    public void init(List<T> target)
    {
        listModel.clear();
        for (T item : target)
        {
            listModel.addElement(item);
        }
    }

    /** Get resulting list of items */
    public List<T> getResult()
    {
        int size = listModel.size();
        List<T> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++)
        {
            result.add(listModel.get(i));
        }
        return result;
    }

    private void dirtyConsumer(boolean dirty)
    {
        // Repaint list to reflect changes made to items
        itemList.repaint();
        this.dirtyConsumer.accept(dirty);
    }

    private void initComponent()
    {
        propertiesComponent.setEnabled(false);
        setLayout(new GridBagLayout());

        itemList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        itemList.setModel(listModel);

        JButton add = new JButton("Add");
        JButton remove = new JButton("Remove");

        JPanel listPanel = new JPanel();
        listPanel.setLayout(new GridBagLayout());
        listPanel.add(itemList, new GridBagConstraints(0, 0, 2, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
        listPanel.add(add, new GridBagConstraints(0, 1, 1, 1, 0.5, 0.0, GridBagConstraints.BASELINE, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
        listPanel.add(remove, new GridBagConstraints(1, 1, 1, 1, 0.5, 0.0, GridBagConstraints.BASELINE, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));

        add.addActionListener(l ->
        {
            T item = itemCreator.get();
            // Call dirty before adding to model to let host handle the current model
            dirtyConsumer.accept(true);
            listModel.addElement(item);
            itemList.setSelectedIndex(listModel.getSize() - 1);
        });

        remove.addActionListener(l ->
        {
            T item = itemList.getSelectedValue();
            if (item != null)
            {
                int index = itemList.getSelectedIndex();
                // Call dirty before adding to model to let host handle the current model
                dirtyConsumer.accept(true);
                listModel.removeElement(item);
                if (index >= listModel.getSize())
                {
                    index = listModel.getSize() - 1;
                }
                itemList.setSelectedIndex(index);
            }
        });

        itemList.addListSelectionListener(l ->
        {
            if (l.getValueIsAdjusting())
            {
                return;
            }
            T item = itemList.getSelectedValue();
            propertiesComponent.setEnabled(item != null);
            if (item != null)
            {
                propertiesComponent.init(item);
            }
        });

        JPanel propertiesPanel = new JPanel();
        propertiesPanel.setLayout(new BorderLayout());
        propertiesPanel.add(propertiesComponent, BorderLayout.CENTER);

        int y = 0;
        if (!isBlank(propertiesComponent.propertyFields.getHeader()))
        {
            JLabel label = new JLabel(propertiesComponent.propertyFields.getHeader());
            label.setHorizontalAlignment(JLabel.CENTER);
            add(label, new GridBagConstraints(0, y++, 4, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 3, 3, 3), 0, 0));
        }

        add(listPanel, new GridBagConstraints(0, y, 1, 1, 0.25, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 3, 3, 0), 0, 0));
        add(propertiesPanel, new GridBagConstraints(1, y, 1, 1, 0.75, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 3, 3, 0), 0, 0));

        addAncestorListener(new AncestorListener()
        {
            @Override
            public void ancestorAdded(AncestorEvent event)
            {
                // Select first item if nothing is selected
                T item = itemList.getSelectedValue();
                if (item == null
                        && listModel.size() > 0)
                {
                    itemList.setSelectedIndex(0);
                }
            }

            @Override
            public void ancestorRemoved(AncestorEvent event)
            {
            }

            @Override
            public void ancestorMoved(AncestorEvent event)
            {
            }
        });
    }
}
