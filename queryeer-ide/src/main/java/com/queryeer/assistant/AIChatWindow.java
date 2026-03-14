package com.queryeer.assistant;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;

import com.queryeer.Constants;
import com.queryeer.api.IQueryFile;
import com.queryeer.api.component.DialogUtils;
import com.queryeer.api.event.ShowOptionsEvent;
import com.queryeer.api.extensions.assistant.AIChatMessage;
import com.queryeer.api.extensions.assistant.AIChatMessage.Role;
import com.queryeer.api.extensions.assistant.IAIAssistantProvider;
import com.queryeer.api.extensions.assistant.IAIContextItem;
import com.queryeer.api.extensions.engine.IQueryEngine;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.IQueryFileProvider;

/** Floating AI assistant chat window. Each query file has its own conversation state. */
class AIChatWindow extends DialogUtils.AFrame
{
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(BasicThreadFactory.builder()
            .daemon(true)
            .namingPattern("AIAssistant-%d")
            .build());

    private final List<IAIAssistantProvider> providers;
    private final IEventBus eventBus;
    private final WeakHashMap<IQueryFile, FileChatState> fileStates = new WeakHashMap<>();

    private JComboBox<IAIAssistantProvider> providerCombo;
    private JButton infoToggleButton;
    private JScrollPane infoScrollPane;
    private boolean infoExpanded = false;
    private JTextPane chatPane;
    private JTextArea inputArea;
    private JButton sendButton;
    private JButton contextButton;
    private JCheckBox includeQueryCheckBox;

    private final SimpleAttributeSet userLabelStyle;
    private final SimpleAttributeSet userTextStyle;
    private final SimpleAttributeSet assistantLabelStyle;
    private final SimpleAttributeSet assistantTextStyle;

    private IQueryFile currentFile;
    private boolean responding = false;

    // CSOFF
    AIChatWindow(List<IAIAssistantProvider> providers, IQueryFileProvider queryFileProvider, IEventBus eventBus)
    {
        super("AI Assistant");
        this.providers = requireNonNull(providers, "providers");
        this.eventBus = requireNonNull(eventBus, "eventBus");
        requireNonNull(queryFileProvider, "queryFileProvider");

        userLabelStyle = new SimpleAttributeSet();
        StyleConstants.setBold(userLabelStyle, true);
        StyleConstants.setForeground(userLabelStyle, new Color(0, 100, 180));

        userTextStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(userTextStyle, new Color(0, 80, 160));

        assistantLabelStyle = new SimpleAttributeSet();
        StyleConstants.setBold(assistantLabelStyle, true);
        StyleConstants.setForeground(assistantLabelStyle, new Color(20, 120, 20));

        assistantTextStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(assistantTextStyle, new Color(30, 100, 30));

        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        setPreferredSize(new Dimension(650, 800));
        initUI();
        pack();

        queryFileProvider.addCurrentFileListener(f -> SwingUtilities.invokeLater(() -> switchToFile(f)));
        switchToFile(queryFileProvider.getCurrentFile());
    }
    // CSON

    private void initUI()
    {
        getContentPane().setLayout(new BorderLayout(4, 4));

        // Top: provider selector + controls
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        topPanel.add(new JLabel("Provider:"));

        providerCombo = new JComboBox<>(providers.toArray(new IAIAssistantProvider[0]));
        providerCombo.setRenderer(new DefaultListCellRenderer()
        {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
            {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof IAIAssistantProvider p)
                {
                    setText(p.name());
                }
                return this;
            }
        });
        providerCombo.addItemListener(e -> updateInfoPanel());
        topPanel.add(providerCombo);

        includeQueryCheckBox = new JCheckBox("Include query", true);
        includeQueryCheckBox.setToolTipText("Include the current query text as context");
        topPanel.add(includeQueryCheckBox);

        contextButton = new JButton("Context objects");
        contextButton.setToolTipText("Select database objects to include as context");
        contextButton.addActionListener(e -> openContextPicker());
        topPanel.add(contextButton);

        JButton configureButton = new JButton(Constants.COG);
        configureButton.setToolTipText("Open Options to configure the selected provider");
        configureButton.addActionListener(e -> openProviderOptions());
        topPanel.add(configureButton);

        // Provider info panel (collapsible, collapsed by default)
        // CSOFF
        infoToggleButton = new JButton("\u25BA Provider Settings");
        // CSON
        infoToggleButton.setBorderPainted(false);
        infoToggleButton.setContentAreaFilled(false);
        infoToggleButton.setFocusPainted(false);
        infoToggleButton.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        infoToggleButton.addActionListener(e -> toggleInfoPanel());

        infoScrollPane = new JScrollPane();
        infoScrollPane.setBorder(BorderFactory.createEmptyBorder(0, 4, 4, 4));
        infoScrollPane.setVisible(false);

        JPanel northContainer = new JPanel(new BorderLayout(0, 0));
        northContainer.add(topPanel, BorderLayout.NORTH);
        JPanel infoContainer = new JPanel(new BorderLayout(0, 0));
        infoContainer.add(infoToggleButton, BorderLayout.NORTH);
        infoContainer.add(infoScrollPane, BorderLayout.CENTER);
        northContainer.add(infoContainer, BorderLayout.CENTER);
        getContentPane().add(northContainer, BorderLayout.NORTH);

        // Center: scrollable chat history
        chatPane = new JTextPane();
        chatPane.setEditable(false);
        chatPane.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        JScrollPane chatScroll = new JScrollPane(chatPane);
        getContentPane().add(chatScroll, BorderLayout.CENTER);

        // Bottom: input area + buttons
        JPanel bottomPanel = new JPanel(new BorderLayout(4, 4));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        inputArea = new JTextArea(4, 40);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        inputArea.setToolTipText("Type your message. Press Ctrl+Enter to send.");
        inputArea.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyPressed(KeyEvent e)
            {
                if (e.getKeyCode() == KeyEvent.VK_ENTER
                        && e.isControlDown())
                {
                    sendMessage();
                    e.consume();
                }
            }
        });
        bottomPanel.add(new JScrollPane(inputArea), BorderLayout.CENTER);

        JButton clearButton = new JButton("Clear");
        clearButton.setToolTipText("Clear the chat history");
        clearButton.addActionListener(e -> clearChat());
        sendButton = new JButton("Send (Ctrl+Enter)");
        sendButton.addActionListener(e -> sendMessage());
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttonPanel.add(clearButton);
        buttonPanel.add(sendButton);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

        getContentPane().add(bottomPanel, BorderLayout.SOUTH);
    }

    private void toggleInfoPanel()
    {
        infoExpanded = !infoExpanded;
        infoScrollPane.setVisible(infoExpanded);
        infoToggleButton.setText(getExpandedUnicodeChar(infoExpanded) + " Provider Settings");
        revalidate();
    }

    static String getExpandedUnicodeChar(boolean expanded)
    {
        // CSOFF
        return expanded ? "\u25BC "
                : "\u25BA ";
        // CSON
    }

    private void updateInfoPanel()
    {
        IAIAssistantProvider provider = (IAIAssistantProvider) providerCombo.getSelectedItem();
        Map<String, Object> info = provider != null ? provider.getInfo()
                : null;
        boolean hasInfo = info != null
                && !info.isEmpty();
        infoToggleButton.setVisible(hasInfo);
        if (!hasInfo)
        {
            infoScrollPane.setViewportView(null);
            infoScrollPane.setVisible(false);
            infoExpanded = false;
            // CSOFF
            infoToggleButton.setText("\u25BA Provider Settings");
            // CSON
            return;
        }

        JPanel panel = new JPanel(new java.awt.GridBagLayout());
        int row = 0;
        for (Map.Entry<String, Object> entry : info.entrySet())
        {
            JLabel keyLabel = new JLabel(entry.getKey() + ":");
            keyLabel.setFont(keyLabel.getFont()
                    .deriveFont(Font.BOLD));
            panel.add(keyLabel,
                    new java.awt.GridBagConstraints(0, row, 1, 1, 0.0, 0.0, java.awt.GridBagConstraints.NORTHWEST, java.awt.GridBagConstraints.NONE, new java.awt.Insets(2, 4, 2, 8), 0, 0));

            String valueStr = String.valueOf(entry.getValue());
            if (valueStr.contains("\n")
                    || valueStr.length() > 60)
            {
                JTextArea valueArea = new JTextArea(valueStr);
                valueArea.setEditable(false);
                valueArea.setLineWrap(true);
                valueArea.setWrapStyleWord(true);
                valueArea.setRows(2);
                valueArea.setOpaque(false);
                panel.add(valueArea,
                        new java.awt.GridBagConstraints(1, row, 1, 1, 1.0, 0.0, java.awt.GridBagConstraints.NORTHWEST, java.awt.GridBagConstraints.HORIZONTAL, new java.awt.Insets(2, 0, 2, 4), 0, 0));
            }
            else
            {
                JLabel valueLabel = new JLabel(valueStr);
                panel.add(valueLabel,
                        new java.awt.GridBagConstraints(1, row, 1, 1, 1.0, 0.0, java.awt.GridBagConstraints.NORTHWEST, java.awt.GridBagConstraints.HORIZONTAL, new java.awt.Insets(2, 0, 2, 4), 0, 0));
            }
            row++;
        }
        infoScrollPane.setViewportView(panel);
        // visibility is controlled by the toggle button, not here
    }

    private void openProviderOptions()
    {
        IAIAssistantProvider provider = (IAIAssistantProvider) providerCombo.getSelectedItem();
        if (provider != null)
        {
            eventBus.publish(new ShowOptionsEvent(provider.getClass()));
        }
    }

    private void switchToFile(IQueryFile file)
    {
        currentFile = file;
        FileChatState state = currentState();
        chatPane.setDocument(state != null ? state.document
                : new DefaultStyledDocument());
        updateContextButton();
        updateInfoPanel();
    }

    private FileChatState currentState()
    {
        if (currentFile == null)
        {
            return null;
        }
        return fileStates.computeIfAbsent(currentFile, k -> new FileChatState());
    }

    private void openContextPicker()
    {
        if (currentFile == null)
        {
            return;
        }
        IQueryEngine.IState state = currentFile.getEngineState();
        if (state == null)
        {
            return;
        }
        FileChatState chatState = currentState();
        List<IAIContextItem> result = AIContextPickerDialog.show(this, state.getQueryEngine(), currentFile, chatState.selectedContextItems);
        if (result != null)
        {
            chatState.selectedContextItems.clear();
            chatState.selectedContextItems.addAll(result);
            updateContextButton();
        }
    }

    private void updateContextButton()
    {
        FileChatState state = currentState();
        int n = state != null ? state.selectedContextItems.size()
                : 0;
        contextButton.setText(n == 0 ? "Context objects"
                : "Context objects (" + n + ")");
    }

    private void sendMessage()
    {
        if (responding)
        {
            return;
        }

        FileChatState state = currentState();
        if (state == null)
        {
            return;
        }

        String userText = inputArea.getText()
                .trim();
        if (isBlank(userText))
        {
            return;
        }

        IAIAssistantProvider provider = (IAIAssistantProvider) providerCombo.getSelectedItem();
        if (provider == null
                || !provider.isConfigured())
        {
            appendText(state.document, "\n[No provider configured. Please configure an AI provider in Options.]\n", assistantTextStyle);
            return;
        }

        inputArea.setText("");
        appendText(state.document, "\nYou: ", userLabelStyle);
        appendText(state.document, userText + "\n", userTextStyle);
        setResponding(true);
        state.responseBuilder.setLength(0);
        appendText(state.document, "\nAssistant: ", assistantLabelStyle);

        List<AIChatMessage> historySnapshot = new ArrayList<>(state.history);
        String systemPrompt = buildSystemPrompt(state);
        state.history.add(new AIChatMessage(Role.USER, userText));

        EXECUTOR.submit(() -> provider.chat(historySnapshot, userText, systemPrompt, chunk -> SwingUtilities.invokeLater(() ->
        {
            state.responseBuilder.append(chunk);
            appendText(state.document, chunk, assistantTextStyle);
        }), () -> SwingUtilities.invokeLater(() ->
        {
            state.history.add(new AIChatMessage(Role.ASSISTANT, state.responseBuilder.toString()));
            appendText(state.document, "\n", assistantTextStyle);
            setResponding(false);
        }), error -> SwingUtilities.invokeLater(() ->
        {
            appendText(state.document, "\n[Error: " + error.getMessage() + "]\n", assistantTextStyle);
            setResponding(false);
        })));
    }

    private String buildSystemPrompt(FileChatState state)
    {
        IAIAssistantProvider provider = (IAIAssistantProvider) providerCombo.getSelectedItem();
        String base = provider != null ? provider.getSystemPrompt()
                : IAIAssistantProvider.DEFAULT_SYSTEM_PROMPT;
        StringBuilder sb = new StringBuilder(base);

        if (currentFile == null)
        {
            return sb.toString();
        }

        if (includeQueryCheckBox.isSelected())
        {
            Object value = currentFile.getEditor()
                    .getValue(false);
            if (value != null)
            {
                String query = value.toString()
                        .trim();
                if (!isBlank(query))
                {
                    sb.append("\n\nCurrent query:\n```sql\n")
                            .append(query)
                            .append("\n```");
                }
            }
        }

        if (!state.selectedContextItems.isEmpty())
        {
            sb.append("\n\nSelected database objects:");
            for (IAIContextItem item : state.selectedContextItems)
            {
                sb.append("\n")
                        .append(item.getContent());
            }
        }

        return sb.toString();
    }

    private void appendText(StyledDocument doc, String text, AttributeSet style)
    {
        try
        {
            doc.insertString(doc.getLength(), text, style);
            if (chatPane.getDocument() == doc)
            {
                chatPane.setCaretPosition(doc.getLength());
            }
        }
        catch (BadLocationException e)
        {
            // ignore
        }
    }

    private void clearChat()
    {
        FileChatState state = currentState();
        if (state == null)
        {
            return;
        }
        state.history.clear();
        try
        {
            state.document.remove(0, state.document.getLength());
        }
        catch (BadLocationException e)
        {
            // ignore
        }
    }

    private void setResponding(boolean responding)
    {
        this.responding = responding;
        sendButton.setEnabled(!responding);
        inputArea.setEnabled(!responding);
    }

    private static class FileChatState
    {
        final List<AIChatMessage> history = new ArrayList<>();
        final List<IAIContextItem> selectedContextItems = new ArrayList<>();
        final DefaultStyledDocument document = new DefaultStyledDocument();
        final StringBuilder responseBuilder = new StringBuilder();
    }
}
