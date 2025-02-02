package com.queryeer.payloadbuilder;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.lowerCase;

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.AbstractAction;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.extensions.payloadbuilder.ICatalogExtension;
import com.queryeer.api.extensions.payloadbuilder.ICatalogExtensionFactory;
import com.queryeer.api.service.IConfig;

/** Configurable for payloadbuilder module */
class CatalogsConfigurable implements IConfigurable
{
    static final String PAYLOADBUILDER = "Payloadbuilder";
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogsConfigurable.class);
    private static final String NAME = CatalogsConfigurable.class.getPackageName();
    static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final IConfig config;
    private final List<ICatalogExtensionFactory> catalogExtensionFactories;
    private final List<Consumer<Boolean>> dirtyStateConsumers = new ArrayList<>();

    private PayloadbuilderConfig payloadbuilderConfig;
    private CatalogsComponent component;

    CatalogsConfigurable(IConfig config, List<ICatalogExtensionFactory> catalogExtensionFactories)
    {
        this.config = requireNonNull(config, "config");
        this.catalogExtensionFactories = requireNonNull(catalogExtensionFactories, "catalogExtensionFactories");
        loadSettings();
    }

    @Override
    public Component getComponent()
    {
        if (component == null)
        {
            component = new CatalogsComponent();
            component.init(payloadbuilderConfig.catalogs);
        }
        return component;
    }

    @Override
    public String getTitle()
    {
        return "Catalogs";
    }

    @Override
    public String groupName()
    {
        return PAYLOADBUILDER;
    }

    @Override
    public void addDirtyStateConsumer(Consumer<Boolean> consumer)
    {
        dirtyStateConsumers.add(consumer);
    }

    @Override
    public void removeDirtyStateConsumer(Consumer<Boolean> consumer)
    {
        dirtyStateConsumers.remove(consumer);
    }

    @Override
    public boolean commitChanges()
    {
        File file = config.getConfigFileName(NAME);

        PayloadbuilderConfig payloadBuilderConfig = new PayloadbuilderConfig();
        payloadBuilderConfig.catalogs = component.queryeerCatalogs;
        try
        {
            MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValue(file, payloadBuilderConfig);
        }
        catch (IOException e)
        {
            JOptionPane.showMessageDialog(component, "Error saving config, message: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        // Re-initalize to get a new copy in UI
        component.init(payloadBuilderConfig.catalogs);
        this.payloadbuilderConfig = payloadBuilderConfig;
        return true;
    }

    @Override
    public void revertChanges()
    {
        component.init(payloadbuilderConfig.catalogs);
    }

    private void notifyDirtyStateConsumers()
    {
        if (!component.notify)
        {
            return;
        }
        int size = dirtyStateConsumers.size();
        for (int i = size - 1; i >= 0; i--)
        {
            dirtyStateConsumers.get(i)
                    .accept(true);
        }
    }

    List<QueryeerCatalog> getCatalogs()
    {
        if (payloadbuilderConfig == null)
        {
            return emptyList();
        }
        return payloadbuilderConfig.catalogs;
    }

    private void loadSettings()
    {
        Map<String, Object> catalogsConfig = config.loadExtensionConfig(NAME);
        payloadbuilderConfig = catalogsConfig.isEmpty() ? new PayloadbuilderConfig()
                : MAPPER.convertValue(catalogsConfig, PayloadbuilderConfig.class);

        try
        {
            loadCatalogExtensions();
        }
        catch (IOException e)
        {
            LOGGER.error("Error loading catalog extensions", e);
        }
    }

    @SuppressWarnings("unchecked")
    void loadCatalogExtensions() throws IOException
    {
        Set<String> seenAliases = new HashSet<>();
        for (QueryeerCatalog catalog : payloadbuilderConfig.catalogs)
        {
            if (!seenAliases.add(lowerCase(catalog.getAlias())))
            {
                throw new IllegalArgumentException("Duplicate alias found in config. Alias: " + catalog.getAlias());
            }
        }

        // Load extension according to config
        // Auto add missing extension with the extensions default alias
        Map<String, ICatalogExtensionFactory> extensions = catalogExtensionFactories.stream()
                .sorted(Comparator.comparingInt(ICatalogExtensionFactory::order))
                .collect(toMap(c -> c.getClass()
                        .getName(), Function.identity(), (e1, e2) -> e1, LinkedHashMap::new));

        Set<ICatalogExtensionFactory> processedFactories = new HashSet<>();

        // Loop configured extensions
        boolean configChanged = false;

        for (QueryeerCatalog catalog : payloadbuilderConfig.catalogs)
        {
            ICatalogExtensionFactory factory = extensions.get(catalog.getFactory());
            processedFactories.add(factory);
            // Disable current catalog if no extension found
            if (factory == null)
            {
                configChanged = true;
                catalog.disabled = true;
            }
            else
            {
                catalog.catalogExtensionFactory = factory;
                catalog.catalogExtension = factory.create(catalog.getAlias());
                if (catalog.isDisabled())
                {
                    configChanged = true;
                }
                // Enable config
                catalog.disabled = false;
            }
        }

        // Append all new extensions not found in config
        for (ICatalogExtensionFactory factory : extensions.values())
        {
            if (processedFactories.contains(factory))
            {
                continue;
            }

            QueryeerCatalog catalog = new QueryeerCatalog();
            payloadbuilderConfig.catalogs.add(catalog);

            catalog.catalogExtensionFactory = factory;
            catalog.factory = factory.getClass()
                    .getName();
            catalog.disabled = false;

            String alias = factory.getDefaultAlias();

            // Find an empty alias
            int count = 1;
            String currentAlias = alias;
            while (seenAliases.contains(lowerCase(currentAlias)))
            {
                currentAlias = (alias + count++);
            }

            catalog.alias = alias;
            catalog.catalogExtension = factory.create(alias);

            configChanged = true;
        }

        if (configChanged)
        {
            config.saveExtensionConfig(NAME, MAPPER.convertValue(payloadbuilderConfig, Map.class));
        }
    }

    class CatalogsComponent extends JPanel
    {
        private final JTable catalogs;
        private final JLabel labelWarning = new JLabel();
        private final AbstractTableModel model;
        private final JPopupMenu contextMenu = new JPopupMenu();

        private boolean notify = true;
        private Point popupLocation;
        private List<QueryeerCatalog> queryeerCatalogs = emptyList();

        void init(List<QueryeerCatalog> queryeerCatalogs)
        {
            this.queryeerCatalogs = new ArrayList<>(queryeerCatalogs.stream()
                    .map(QueryeerCatalog::clone)
                    .toList());

            notify = false;
            model.fireTableDataChanged();
            notify = true;
            validateCatalogs();
        }

        CatalogsComponent()
        {
            setLayout(new GridBagLayout());

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.anchor = GridBagConstraints.WEST;
            add(new JLabel("NOTE! Restart Queryeer after catalog changes."), gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 1;
            gbc.anchor = GridBagConstraints.WEST;
            add(new JLabel("Catalogs:"), gbc);

            catalogs = new JTable()
            {
                @Override
                public Point getPopupLocation(MouseEvent event)
                {
                    popupLocation = event != null ? event.getPoint()
                            : null;
                    return super.getPopupLocation(event);
                }
            };
            catalogs.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            catalogs.setComponentPopupMenu(contextMenu);
            contextMenu.addPopupMenuListener(new PopupMenuListener()
            {
                List<Component> contextPopupItems = new ArrayList<>();

                @Override
                public void popupMenuWillBecomeVisible(PopupMenuEvent e)
                {
                    if (popupLocation == null)
                    {
                        contextMenu.setVisible(false);
                        return;
                    }

                    int row = catalogs.rowAtPoint(popupLocation);
                    catalogs.setRowSelectionInterval(row, row);
                    if (row - 1 >= 0)
                    {
                        contextPopupItems.add(contextMenu.add(new AbstractAction("Move Up")
                        {
                            @Override
                            public void actionPerformed(ActionEvent e)
                            {
                                QueryeerCatalog catalog = queryeerCatalogs.remove(row);
                                queryeerCatalogs.add(row - 1, catalog);
                                model.fireTableDataChanged();
                                catalogs.setRowSelectionInterval(row - 1, row - 1);
                                notifyDirtyStateConsumers();
                            }
                        }));
                    }
                    if (row + 1 < queryeerCatalogs.size())
                    {
                        contextPopupItems.add(contextMenu.add(new AbstractAction("Move Down")
                        {
                            @Override
                            public void actionPerformed(ActionEvent e)
                            {
                                QueryeerCatalog catalog = queryeerCatalogs.remove(row);
                                queryeerCatalogs.add(row + 1, catalog);
                                model.fireTableDataChanged();
                                catalogs.setRowSelectionInterval(row + 1, row + 1);
                                notifyDirtyStateConsumers();
                            }
                        }));
                    }
                }

                @Override
                public void popupMenuWillBecomeInvisible(PopupMenuEvent e)
                {
                    if (!contextPopupItems.isEmpty())
                    {
                        for (Component comp : contextPopupItems)
                        {
                            contextMenu.remove(comp);
                        }
                        contextPopupItems.clear();
                    }

                }

                @Override
                public void popupMenuCanceled(PopupMenuEvent e)
                {
                    popupMenuWillBecomeInvisible(e);
                }
            });

            catalogs.putClientProperty("terminateEditOnFocusLost", true);
            model = new AbstractTableModel()
            {
                @Override
                public boolean isCellEditable(int row, int column)
                {
                    // Not loaded catalogs cannot be changed
                    if (queryeerCatalogs.get(row).catalogExtensionFactory == null)
                    {
                        return false;
                    }
                    return column >= 0
                            || column <= 2;
                }

                @Override
                public Class<?> getColumnClass(int columnIndex)
                {
                    return switch (columnIndex)
                    {
                        case 0 -> String.class;
                        case 1 -> ICatalogExtensionFactory.class;
                        case 2 -> Boolean.class;
                        case 3 -> String.class;
                        default -> throw new IllegalArgumentException("Invalid column index");
                    };
                }

                @Override
                public int getRowCount()
                {
                    return queryeerCatalogs.size();
                }

                @Override
                public int getColumnCount()
                {
                    return 4;
                }

                @Override
                public String getColumnName(int column)
                {
                    return switch (column)
                    {
                        case 0 -> "Alias";
                        case 1 -> "Catalog";
                        case 2 -> "Enabled";
                        case 3 -> "Details";
                        default -> throw new IllegalArgumentException("Invalid column index");
                    };
                }

                @Override
                public void setValueAt(Object value, int rowIndex, int columnIndex)
                {
                    QueryeerCatalog catalog = queryeerCatalogs.get(rowIndex);
                    switch (columnIndex)
                    {
                        case 0:
                            catalog.alias = String.valueOf(value);
                            break;
                        case 1:
                            ICatalogExtensionFactory factory = (ICatalogExtensionFactory) value;
                            catalog.factory = factory.getClass()
                                    .getName();
                            catalog.catalogExtensionFactory = factory;
                            break;
                        case 2:
                            catalog.disabled = !(Boolean) value;
                            break;
                        default:
                            throw new IllegalArgumentException("Invalid column index");
                    }
                    validateCatalogs();
                    notifyDirtyStateConsumers();
                }

                @Override
                public Object getValueAt(int rowIndex, int columnIndex)
                {
                    QueryeerCatalog catalog = queryeerCatalogs.get(rowIndex);
                    return switch (columnIndex)
                    {
                        case 0 -> catalog.alias;
                        case 1 -> catalog.catalogExtensionFactory == null ? catalog.factory
                                : catalog.catalogExtensionFactory;
                        case 2 -> !catalog.disabled;
                        case 3 -> catalog.catalogExtensionFactory == null ? "Configured catalog extension not found on classpath."
                                : "";
                        default -> throw new IllegalArgumentException("Invalid column index");
                    };
                }
            };
            catalogs.setModel(model);

            JComboBox<ICatalogExtensionFactory> catalogsCombo = new JComboBox<ICatalogExtensionFactory>();
            catalogsCombo.setRenderer(new DefaultListCellRenderer()
            {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
                {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    ICatalogExtensionFactory factory = (ICatalogExtensionFactory) value;
                    setText(factory.getTitle());
                    return this;
                }
            });
            for (ICatalogExtensionFactory factory : catalogExtensionFactories)
            {
                catalogsCombo.addItem(factory);
            }

            catalogs.getColumnModel()
                    .getColumn(1)
                    .setCellRenderer(new DefaultTableCellRenderer()
                    {
                        @Override
                        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
                        {
                            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                            if (value instanceof ICatalogExtensionFactory factory)
                            {
                                setText(factory.getTitle());
                            }
                            else
                            {
                                setText(String.valueOf(value));
                            }
                            return this;
                        }
                    });

            catalogs.getColumnModel()
                    .getColumn(1)
                    .setCellEditor(new DefaultCellEditor(catalogsCombo));

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 2;
            gbc.gridwidth = 2;
            gbc.weightx = 1.0d;
            gbc.anchor = GridBagConstraints.NORTH;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            add(new JScrollPane(catalogs), gbc);

            JPanel panel = new JPanel();
            JButton add = new JButton("+");
            add.addActionListener(l ->
            {
                QueryeerCatalog catalog = new QueryeerCatalog();
                catalog.catalogExtensionFactory = getNextNonUsedFactory();
                catalog.alias = getNextAlias(catalog.catalogExtensionFactory);
                queryeerCatalogs.add(catalog);
                validateCatalogs();
                int index = queryeerCatalogs.size() - 1;
                model.fireTableRowsInserted(index, index);
                catalogs.setRowSelectionInterval(index, index);
                notifyDirtyStateConsumers();
            });
            panel.add(add);
            JButton remove = new JButton("-");
            remove.addActionListener(l ->
            {
                int index = catalogs.getSelectedRow();
                if (index < 0)
                {
                    return;
                }

                QueryeerCatalog catalog = queryeerCatalogs.get(index);
                queryeerCatalogs.remove(catalog);
                model.fireTableRowsDeleted(index, index);
                if (index >= queryeerCatalogs.size())
                {
                    index--;
                }
                if (index >= 0)
                {
                    catalogs.setRowSelectionInterval(index, index);
                }
                notifyDirtyStateConsumers();
            });
            panel.add(remove);

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 3;
            gbc.anchor = GridBagConstraints.WEST;
            add(panel, gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 4;
            gbc.weighty = 1.0d;
            gbc.anchor = GridBagConstraints.NORTHWEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            labelWarning.setForeground(Color.RED);
            add(labelWarning, gbc);
        }

        private ICatalogExtensionFactory getNextNonUsedFactory()
        {
            Set<ICatalogExtensionFactory> seenFactories = queryeerCatalogs.stream()
                    .map(c -> c.catalogExtensionFactory)
                    .collect(toSet());
            for (ICatalogExtensionFactory factory : catalogExtensionFactories)
            {
                if (!seenFactories.contains(factory))
                {
                    return factory;
                }
            }
            // Return the firs in list
            return catalogExtensionFactories.get(0);
        }

        private String getNextAlias(ICatalogExtensionFactory factory)
        {
            int count = 0;
            for (QueryeerCatalog c : queryeerCatalogs)
            {
                if (c.catalogExtensionFactory == factory)
                {
                    count++;
                }
            }
            return factory.getDefaultAlias() + (count == 0 ? ""
                    : (count + 1));
        }

        private void validateCatalogs()
        {
            Map<String, List<QueryeerCatalog>> map = queryeerCatalogs.stream()
                    .collect(groupingBy(QueryeerCatalog::getAlias));
            List<String> list = map.entrySet()
                    .stream()
                    .filter(e -> e.getValue()
                            .size() > 1)
                    .map(Entry::getKey)
                    .toList();
            if (!list.isEmpty())
            {
                labelWarning.setText("Duplicated aliases found: " + list);
            }
        }
    }

    static class PayloadbuilderConfig
    {
        @JsonProperty
        List<QueryeerCatalog> catalogs = new ArrayList<>();
    }

    /** Catalog extension */
    static class QueryeerCatalog
    {
        @JsonProperty
        private String alias;
        @JsonProperty
        private String factory;
        @JsonProperty
        private boolean disabled;

        @JsonIgnore
        private ICatalogExtension catalogExtension;
        @JsonIgnore
        private ICatalogExtensionFactory catalogExtensionFactory;

        String getAlias()
        {
            return alias;
        }

        String getFactory()
        {
            return factory;
        }

        boolean isDisabled()
        {
            return disabled;
        }

        ICatalogExtension getCatalogExtension()
        {
            return catalogExtension;
        }

        @Override
        public QueryeerCatalog clone()
        {
            QueryeerCatalog result = new QueryeerCatalog();
            result.alias = alias;
            result.factory = factory;
            result.disabled = disabled;
            result.catalogExtension = catalogExtension;
            result.catalogExtensionFactory = catalogExtensionFactory;
            return result;
        }
    }
}
