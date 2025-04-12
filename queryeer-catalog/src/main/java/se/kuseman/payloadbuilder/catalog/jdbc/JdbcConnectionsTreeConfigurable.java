package se.kuseman.payloadbuilder.catalog.jdbc;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.service.IConfig;

import se.kuseman.payloadbuilder.catalog.jdbc.dialect.ITreeConfig;

class JdbcConnectionsTreeConfigurable implements IConfigurable, ITreeConfig
{
    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcConnectionsTreeConfigurable.class);
    private static final String NAME = "se.kuseman.payloadbuilder.catalog.jdbc.Tree";
    private final IConfig config;
    private final List<Consumer<Boolean>> dirtyStateConsumers = new ArrayList<>();
    private TreeConfig treeConfig;
    private TreeConfigComponent treeConfigComponent;
    private boolean disableNotify;

    JdbcConnectionsTreeConfigurable(IConfig config)
    {
        this.config = config;
        loadConfig();
    }

    @Override
    public String groupName()
    {
        return Constants.TITLE;
    }

    @Override
    public String getTitle()
    {
        return "Jdbc Tree Config";
    }

    @Override
    public Component getComponent()
    {
        if (treeConfigComponent == null)
        {
            treeConfigComponent = new TreeConfigComponent();
            treeConfigComponent.init();
        }
        return treeConfigComponent;
    }

    @Override
    public void revertChanges()
    {
        treeConfigComponent.init();
    }

    @Override
    public boolean commitChanges()
    {
        treeConfig.hideSqlServerSysSchema = treeConfigComponent.hideSqlServerSysSchema.isSelected();
        return saveConfig();
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

    private void loadConfig()
    {
        File configFileName = config.getConfigFileName(NAME);
        if (configFileName.exists())
        {
            try
            {
                treeConfig = Constants.MAPPER.readValue(configFileName, TreeConfig.class);
            }
            catch (IOException e)
            {
                LOGGER.error("Error reading projects config from: {}", configFileName, e);
            }
        }

        if (treeConfig == null)
        {
            treeConfig = new TreeConfig();
        }
    }

    boolean saveConfig()
    {
        File configFileName = config.getConfigFileName(NAME);
        try
        {
            Constants.MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValue(configFileName, treeConfig);
            return true;
        }
        catch (IOException e)
        {
            LOGGER.error("Error writing projects config to: {}", configFileName, e);
            return false;
        }
    }

    private void notifyDirty()
    {
        if (disableNotify)
        {
            return;
        }
        dirtyStateConsumers.forEach(c -> c.accept(true));
    }

    boolean isSyncTree()
    {
        return treeConfig.syncTree;
    }

    boolean isFilterTree()
    {
        return treeConfig.filterTree;
    }

    @Override
    public boolean isHideSqlServerSysSchema()
    {
        return treeConfig.hideSqlServerSysSchema;
    }

    void setSyncTree(boolean value)
    {
        treeConfig.syncTree = value;
    }

    void setFilterTree(boolean value)
    {
        treeConfig.filterTree = value;
    }

    private static class TreeConfig
    {
        @JsonProperty
        private boolean syncTree;

        @JsonProperty
        private boolean filterTree;

        @JsonProperty
        private boolean hideSqlServerSysSchema = true;
    }

    class TreeConfigComponent extends JPanel
    {
        private final JCheckBox hideSqlServerSysSchema;

        TreeConfigComponent()
        {
            setLayout(new GridBagLayout());

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.NONE;

            String hideSqlServerSysSchemaToolTip = """
                    Check to hide schema 'sys' from all tree componentns
                    """;

            add(new JLabel("Hide SQL Server 'sys' schema")
            {
                {
                    setToolTipText(hideSqlServerSysSchemaToolTip);
                }
            }, gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 1;
            gbc.gridy = 0;
            gbc.weightx = 1.0d;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;

            hideSqlServerSysSchema = new JCheckBox();
            hideSqlServerSysSchema.setToolTipText(hideSqlServerSysSchemaToolTip);
            hideSqlServerSysSchema.addActionListener(l -> notifyDirty());
            add(hideSqlServerSysSchema, gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 1;
            gbc.weightx = 1.0d;
            gbc.weighty = 1.0d;
            gbc.gridwidth = 2;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.BOTH;
            add(new JPanel(), gbc);
        }

        void init()
        {
            disableNotify = true;
            hideSqlServerSysSchema.setSelected(treeConfig.hideSqlServerSysSchema);
            disableNotify = false;
        }
    }
}
