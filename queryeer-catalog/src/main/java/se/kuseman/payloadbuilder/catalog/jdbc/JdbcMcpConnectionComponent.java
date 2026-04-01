package se.kuseman.payloadbuilder.catalog.jdbc;

import static java.util.Objects.requireNonNull;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.queryeer.api.component.AutoCompletionComboBox;

/**
 * Engine-specific connection configuration UI for MCP tools using JDBC. Allows selecting a named JDBC connection and a database/schema from a populated dropdown with autocomplete.
 */
class JdbcMcpConnectionComponent extends JPanel
{
    static final String KEY_CONNECTION = "connection";
    static final String KEY_DATABASE = "database";

    JdbcMcpConnectionComponent(JdbcConnectionsModel connectionsModel, Map<String, Object> config, Consumer<Boolean> dirtyConsumer)
    {
        requireNonNull(connectionsModel, "connectionsModel");
        requireNonNull(config, "config");
        requireNonNull(dirtyConsumer, "dirtyConsumer");

        setBorder(BorderFactory.createTitledBorder("Connection"));
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Build connection combo
        String[] connectionNames = connectionsModel.getConnections()
                .stream()
                .map(JdbcConnection::getName)
                .toArray(String[]::new);
        JComboBox<String> connectionCombo = new JComboBox<>(connectionNames);
        String savedConnection = (String) config.get(KEY_CONNECTION);
        if (savedConnection != null)
        {
            connectionCombo.setSelectedItem(savedConnection);
        }
        else if (connectionNames.length > 0)
        {
            // New tool: persist the auto-selected first connection immediately
            config.put(KEY_CONNECTION, connectionNames[0]);
        }

        // Build database combo with autocomplete
        DefaultComboBoxModel<String> databasesModel = new DefaultComboBoxModel<>();
        JComboBox<String> databaseCombo = new JComboBox<>(databasesModel);
        databaseCombo.setEditable(true);
        AutoCompletionComboBox.enable(databaseCombo);

        // Layout
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        add(new JLabel("Connection:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        add(connectionCombo, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        add(new JLabel("Database:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        add(databaseCombo, gbc);

        // Listeners — write directly into config map
        connectionCombo.addActionListener(e ->
        {
            Object selected = connectionCombo.getSelectedItem();
            config.put(KEY_CONNECTION, selected != null ? selected.toString()
                    : "");
            dirtyConsumer.accept(true);
            loadDatabases(connectionsModel, (String) connectionCombo.getSelectedItem(), databasesModel, (String) config.get(KEY_DATABASE));
        });

        databaseCombo.addActionListener(e ->
        {
            Object selected = databaseCombo.getSelectedItem();
            config.put(KEY_DATABASE, selected != null ? selected.toString()
                    .trim()
                    : "");
            dirtyConsumer.accept(true);
        });

        // Load databases for the initially selected connection
        loadDatabases(connectionsModel, (String) connectionCombo.getSelectedItem(), databasesModel, (String) config.get(KEY_DATABASE));
    }

    private static void loadDatabases(JdbcConnectionsModel connectionsModel, String connectionName, DefaultComboBoxModel<String> databasesModel, String preselect)
    {
        if (connectionName == null
                || connectionName.isBlank())
        {
            return;
        }

        JdbcConnection connection = connectionsModel.getConnections()
                .stream()
                .filter(c -> c.getName()
                        .equals(connectionName))
                .findFirst()
                .orElse(null);

        if (connection == null
                || !connection.hasCredentials())
        {
            return;
        }

        new Thread(() ->
        {
            connectionsModel.getDatabases(connection, false, false);
            SwingUtilities.invokeLater(() ->
            {
                databasesModel.removeAllElements();
                if (connection.getDatabases() != null)
                {
                    for (String db : connection.getDatabases())
                    {
                        databasesModel.addElement(db);
                    }
                }
                if (preselect != null
                        && !preselect.isBlank())
                {
                    databasesModel.setSelectedItem(preselect);
                }
            });
        }, "mcp-load-databases").start();
    }
}
