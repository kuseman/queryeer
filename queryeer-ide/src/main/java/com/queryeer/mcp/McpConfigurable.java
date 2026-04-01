package com.queryeer.mcp;

import static java.util.Objects.requireNonNull;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ListSelectionEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queryeer.api.editor.IEditorFactory;
import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.extensions.Inject;
import com.queryeer.api.service.IConfig;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.ITemplateService;

/**
 * IConfigurable for the embedded MCP server. Provides UI for configuring the server port and managing MCP tool definitions.
 */
@Inject
public class McpConfigurable implements IConfigurable
{
    private static final Logger LOGGER = LoggerFactory.getLogger(McpConfigurable.class);
    static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    static final String CONFIG_NAME = "mcp-server";

    private final IConfig config;
    private final IEditorFactory editorFactory;
    private final IEventBus eventBus;
    private final McpServer mcpServer;
    private final McpHttpHandler mcpHandler;
    private final List<Consumer<Boolean>> dirtyStateConsumers = new ArrayList<>();

    // Persisted config
    private McpServerConfig serverConfig;

    // UI (lazy init)
    private JComponent component;
    private JSpinner portSpinner;
    private JLabel statusLabel;
    private JButton toggleServerButton;
    private JTextField cmdField;
    private JList<McpTool> toolList;
    private DefaultListModel<McpTool> toolListModel;
    private McpToolEditorPanel toolEditorPanel;
    private Timer statusTimer;

    // Last published running state — used to suppress redundant events
    private boolean lastPublishedRunning = false;

    McpConfigurable(IConfig config, IEditorFactory editorFactory, ITemplateService templateService, IEventBus eventBus)
    {
        this.config = requireNonNull(config, "config");
        this.editorFactory = requireNonNull(editorFactory, "editorFactory");
        this.eventBus = requireNonNull(eventBus, "eventBus");

        serverConfig = loadConfig();
        mcpHandler = new McpHttpHandler(config, serverConfig, templateService);
        mcpServer = new McpServer(mcpHandler);

        // Start server if there's a valid port in config
        startServerSilently(serverConfig);

        // Fire of the status event after some period to let the UI subscribe on eventbus else the event is never reached.
        Timer timer = new Timer(10_000, e -> publishStatusEvent());
        timer.setRepeats(false);
        timer.start();
    }

    private static boolean shouldRun(McpServerConfig cfg)
    {
        return cfg.isEnabled()
                && cfg.getTools()
                        .stream()
                        .anyMatch(McpTool::isActive);
    }

    private void startServerSilently(McpServerConfig cfg)
    {
        if (!shouldRun(cfg))
        {
            return;
        }
        try
        {
            mcpServer.start(cfg);
        }
        catch (IOException e)
        {
            LOGGER.warn("Failed to start MCP server on port {}: {}", cfg.getPort(), e.getMessage());
        }
    }

    // ---- IConfigurable ----

    @Override
    public JComponent getComponent()
    {
        if (component == null)
        {
            component = buildComponent();
            loadToUI(serverConfig);
            startStatusTimer();
        }
        return component;
    }

    @Override
    public String getTitle()
    {
        return "MCP Server";
    }

    @Override
    public String getLongTitle()
    {
        return "MCP Server — Expose Queries as AI Tools";
    }

    @Override
    public String groupName()
    {
        return "MCP Server";
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
        if (component == null)
        {
            return false;
        }
        // Save parameters from tool editor to current tool
        if (toolEditorPanel != null)
        {
            toolEditorPanel.saveCurrentParameters();
        }

        McpServerConfig newConfig = collectFromUI();
        newConfig.setEnabled(serverConfig.isEnabled());
        saveConfig(newConfig);
        serverConfig = newConfig;

        try
        {
            if (shouldRun(serverConfig))
            {
                mcpServer.restart(newConfig);
            }
            else
            {
                mcpServer.stop();
            }
        }
        catch (IOException e)
        {
            LOGGER.error("Failed to restart MCP server", e);
        }
        // Always publish after commit — port or running state may have changed
        publishStatusEvent();
        updateStatusLabel();
        return true;
    }

    @Override
    public void revertChanges()
    {
        if (component == null)
        {
            return;
        }
        serverConfig = loadConfig();
        loadToUI(serverConfig);
    }

    // ---- UI construction ----

    private JComponent buildComponent()
    {
        JPanel root = new JPanel(new BorderLayout(4, 4));
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // --- Top panel: port + status ---
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        topPanel.add(new JLabel("Port:"));
        portSpinner = new JSpinner(new SpinnerNumberModel(3700, 1024, 65535, 1));
        topPanel.add(portSpinner);
        topPanel.add(new JLabel("  Status:"));
        statusLabel = new JLabel("●  Stopped");
        statusLabel.setForeground(Color.RED);
        topPanel.add(statusLabel);
        toggleServerButton = new JButton("Start Server");
        toggleServerButton.addActionListener(e -> toggleServer());
        topPanel.add(toggleServerButton);

        portSpinner.addChangeListener(e ->
        {
            notifyDirty(true);
            updateCmdField();
        });

        // --- Claude CLI command row ---
        JPanel cmdPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        cmdPanel.add(new JLabel("Example for Claude Code:"));
        cmdField = new JTextField(55);
        cmdField.setEditable(false);
        cmdPanel.add(cmdField);
        JButton copyBtn = new JButton("Copy");
        copyBtn.addActionListener(e -> copyToClipboard(cmdField.getText()));
        cmdPanel.add(copyBtn);

        javax.swing.JPanel northWrapper = new javax.swing.JPanel();
        northWrapper.setLayout(new javax.swing.BoxLayout(northWrapper, javax.swing.BoxLayout.Y_AXIS));
        northWrapper.add(topPanel);
        northWrapper.add(cmdPanel);
        root.add(northWrapper, BorderLayout.NORTH);

        // --- Tool list panel ---
        toolListModel = new DefaultListModel<>();
        toolList = new JList<>(toolListModel);
        toolList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        toolList.addListSelectionListener(this::onToolSelected);

        JButton addBtn = new JButton("+");
        addBtn.setToolTipText("Add tool");
        addBtn.addActionListener(e -> addTool());
        JButton cloneBtn = new JButton("Clone");
        cloneBtn.addActionListener(e -> cloneTool());
        JButton removeBtn = new JButton("−");
        removeBtn.setToolTipText("Remove tool");
        removeBtn.addActionListener(e -> removeTool());
        JPanel listButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        listButtonPanel.add(addBtn);
        listButtonPanel.add(cloneBtn);
        listButtonPanel.add(removeBtn);

        JPanel listPanel = new JPanel(new BorderLayout());
        listPanel.setBorder(BorderFactory.createTitledBorder("Tools"));
        listPanel.add(listButtonPanel, BorderLayout.NORTH);
        listPanel.add(new JScrollPane(toolList), BorderLayout.CENTER);

        // --- Tool editor panel ---
        toolEditorPanel = new McpToolEditorPanel(editorFactory, config.getQueryEngines(), this::notifyDirty);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listPanel, new JScrollPane(toolEditorPanel));
        splitPane.setDividerLocation(200);
        root.add(splitPane, BorderLayout.CENTER);

        return root;
    }

    private void loadToUI(McpServerConfig cfg)
    {
        if (portSpinner == null)
        {
            return;
        }
        portSpinner.setValue(cfg.getPort());
        updateCmdField();
        if (toggleServerButton != null)
        {
            toggleServerButton.setText(mcpServer.isRunning() ? "Stop Server"
                    : "Start Server");
        }
        toolListModel.clear();
        for (McpTool tool : cfg.getTools())
        {
            toolListModel.addElement(tool);
        }
        if (!toolListModel.isEmpty())
        {
            toolList.setSelectedIndex(0);
        }
        else
        {
            toolEditorPanel.loadTool(null);
        }
        updateStatusLabel();
    }

    private McpServerConfig collectFromUI()
    {
        McpServerConfig cfg = new McpServerConfig();
        cfg.setPort((Integer) portSpinner.getValue());
        List<McpTool> tools = new ArrayList<>();
        for (int i = 0; i < toolListModel.size(); i++)
        {
            tools.add(toolListModel.get(i));
        }
        cfg.setTools(tools);
        return cfg;
    }

    private void onToolSelected(ListSelectionEvent e)
    {
        if (e.getValueIsAdjusting())
        {
            return;
        }
        McpTool selected = toolList.getSelectedValue();
        toolEditorPanel.loadTool(selected);
    }

    private void addTool()
    {
        McpTool tool = new McpTool();
        tool.setName("new_tool");
        toolListModel.addElement(tool);
        toolList.setSelectedIndex(toolListModel.size() - 1);
        notifyDirty(true);
    }

    private void cloneTool()
    {
        McpTool selected = toolList.getSelectedValue();
        if (selected == null)
        {
            return;
        }
        // Ensure current params are saved before cloning
        toolEditorPanel.saveCurrentParameters();
        McpTool clone = cloneTool(selected);
        clone.setName(selected.getName() + "_copy");
        toolListModel.addElement(clone);
        toolList.setSelectedIndex(toolListModel.size() - 1);
        notifyDirty(true);
    }

    private static McpTool cloneTool(McpTool src)
    {
        McpTool clone = new McpTool();
        clone.setName(src.getName());
        clone.setDescription(src.getDescription());
        clone.setActive(src.isActive());
        clone.setEngineClass(src.getEngineClass());
        clone.setConnectionConfig(new LinkedHashMap<>(src.getConnectionConfig()));
        clone.setQuery(src.getQuery());
        List<McpToolParameter> clonedParams = new ArrayList<>();
        for (McpToolParameter p : src.getParameters())
        {
            McpToolParameter cp = new McpToolParameter();
            cp.setName(p.getName());
            cp.setDescription(p.getDescription());
            cp.setType(p.getType());
            clonedParams.add(cp);
        }
        clone.setParameters(clonedParams);
        return clone;
    }

    private void removeTool()
    {
        int idx = toolList.getSelectedIndex();
        if (idx < 0)
        {
            return;
        }
        toolListModel.remove(idx);
        if (!toolListModel.isEmpty())
        {
            toolList.setSelectedIndex(Math.min(idx, toolListModel.size() - 1));
        }
        else
        {
            toolEditorPanel.loadTool(null);
        }
        notifyDirty(true);
    }

    private void toggleServer()
    {
        if (mcpServer.isRunning())
        {
            mcpServer.stop();
            serverConfig.setEnabled(false);
        }
        else
        {
            if (serverConfig.getTools()
                    .stream()
                    .noneMatch(McpTool::isActive))
            {
                javax.swing.JOptionPane.showMessageDialog(component, "No active tools configured. Enable at least one tool before starting the server.", "MCP Server",
                        javax.swing.JOptionPane.WARNING_MESSAGE);
                return;
            }
            serverConfig.setEnabled(true);
            startServerSilently(serverConfig);
        }
        saveConfig(serverConfig);
        publishStatusEvent();
        updateStatusLabel();
    }

    private void startStatusTimer()
    {
        statusTimer = new Timer(1500, e -> updateStatusLabel());
        statusTimer.start();
    }

    private void publishStatusEvent()
    {
        boolean running = mcpServer.isRunning();
        lastPublishedRunning = running;
        eventBus.publish(new McpServerStatusEvent(running, serverConfig.getPort()));
    }

    private void updateStatusLabel()
    {
        boolean running = mcpServer.isRunning();
        if (running != lastPublishedRunning)
        {
            publishStatusEvent();
        }
        if (statusLabel == null)
        {
            return;
        }
        SwingUtilities.invokeLater(() ->
        {
            if (mcpServer.isRunning())
            {
                int port = serverConfig.getPort();
                statusLabel.setText("●  Running on port " + port);
                statusLabel.setForeground(new Color(0, 150, 0));
                if (toggleServerButton != null)
                {
                    toggleServerButton.setText("Stop Server");
                }
            }
            else
            {
                statusLabel.setText("●  Stopped");
                statusLabel.setForeground(Color.RED);
                if (toggleServerButton != null)
                {
                    toggleServerButton.setText("Start Server");
                }
            }
        });
    }

    private void notifyDirty(boolean dirty)
    {
        dirtyStateConsumers.forEach(c -> c.accept(dirty));
    }

    private void updateCmdField()
    {
        if (cmdField == null)
        {
            return;
        }
        int port = (Integer) portSpinner.getValue();
        cmdField.setText("claude mcp add queryeer --scope user --transport http --url http://127.0.0.1:" + port + "/mcp");
    }

    private static void copyToClipboard(String text)
    {
        java.awt.Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(text), null);
    }

    // ---- Config persistence ----

    private McpServerConfig loadConfig()
    {
        Map<String, Object> raw = config.loadExtensionConfig(CONFIG_NAME);
        if (raw.isEmpty())
        {
            return new McpServerConfig();
        }
        return MAPPER.convertValue(raw, McpServerConfig.class);
    }

    @SuppressWarnings("unchecked")
    private void saveConfig(McpServerConfig cfg)
    {
        config.saveExtensionConfig(CONFIG_NAME, MAPPER.convertValue(cfg, Map.class));
    }

}
