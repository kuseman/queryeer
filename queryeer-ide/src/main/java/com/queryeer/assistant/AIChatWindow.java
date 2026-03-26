package com.queryeer.assistant;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.AdjustmentListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import com.queryeer.Constants;
import com.queryeer.UiUtils;
import com.queryeer.api.IQueryFile;
import com.queryeer.api.component.DialogUtils;
import com.queryeer.api.event.ShowOptionsEvent;
import com.queryeer.api.extensions.assistant.AIChatMessage;
import com.queryeer.api.extensions.assistant.AIChatMessage.Role;
import com.queryeer.api.extensions.assistant.AIChatSession;
import com.queryeer.api.extensions.assistant.IAIAssistantProvider;
import com.queryeer.api.extensions.assistant.IAIAssistantProvider.ResponseFormat;
import com.queryeer.api.extensions.assistant.IAIContextItem;
import com.queryeer.api.extensions.engine.IQueryEngine;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.IQueryFileProvider;

/** Floating AI assistant chat window. Each query file has its own conversation state. */
class AIChatWindow extends DialogUtils.AFrame
{
    private static final String COPY_SCHEME = "copy:";
    private static final String HTML_END = "</body></html>";

    // CSOFF
    private static String buildHtmlStyle()
    {
        if (UiUtils.isDarkLookAndFeel())
        {
            return "body { font-family: sans-serif; font-size: 12pt; margin: 8px; }" + " .user-label { color: #5ab0ff; font-weight: bold; }"
                   + " .user-text { color: #7dc4ff; margin-left: 8px; margin-top: 2px; }"
                   + " .asst-label { color: #57cc57; font-weight: bold; }"
                   + " .asst-text { color: #80d980; margin-left: 8px; margin-top: 2px; }"
                   + " .error-text { color: #ff6b6b; margin-left: 8px; }"
                   + " pre { font-family: monospace; font-size: 11pt; background-color: #2b2b2b; padding: 6px; margin: 4px; }"
                   + " code { font-family: monospace; font-size: 11pt; background-color: #2b2b2b; }"
                   + " blockquote { margin-left: 16px; }"
                   + " table { border: 1px solid #555555; border-collapse: collapse; }"
                   + " td, th { border: 1px solid #555555; padding: 4px 8px; }"
                   + " .copy-btn { font-size: 10pt; color: #aaaaaa; }";
        }
        return "body { font-family: sans-serif; font-size: 12pt; margin: 8px; }" + " .user-label { color: #0064b4; font-weight: bold; }"
               + " .user-text { color: #0050a0; margin-left: 8px; margin-top: 2px; }"
               + " .asst-label { color: #147814; font-weight: bold; }"
               + " .asst-text { color: #1e641e; margin-left: 8px; margin-top: 2px; }"
               + " .error-text { color: #cc0000; margin-left: 8px; }"
               + " pre { font-family: monospace; font-size: 11pt; background-color: #f0f0f0; padding: 6px; margin: 4px; }"
               + " code { font-family: monospace; font-size: 11pt; background-color: #f0f0f0; }"
               + " blockquote { margin-left: 16px; }"
               + " table { border: 1px solid #cccccc; border-collapse: collapse; }"
               + " td, th { border: 1px solid #cccccc; padding: 4px 8px; }"
               + " .copy-btn { font-size: 10pt; color: #666666; }";
    }

    private static String buildHtmlStart()
    {
        return "<html><head><style>" + buildHtmlStyle() + "</style></head><body>";
    }
    // CSON

    private static final Parser MARKDOWN_PARSER = Parser.builder()
            .extensions(List.of(TablesExtension.create(), StrikethroughExtension.create(), TaskListItemsExtension.create()))
            .build();
    private static final HtmlRenderer MARKDOWN_RENDERER = HtmlRenderer.builder()
            .extensions(List.of(TablesExtension.create(), StrikethroughExtension.create(), TaskListItemsExtension.create()))
            .build();

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
    private JEditorPane chatPane;
    private JScrollPane chatScroll;
    private JPanel newContentIndicator;
    private boolean autoScroll = true;
    private boolean programmaticScroll = false;
    private JTextArea inputArea;
    private JButton sendButton;
    private JButton contextButton;
    private JCheckBox includeQueryCheckBox;

    private IQueryFile currentFile;
    private boolean responding = false;
    private Timer streamingTimer;

    /** Sent-message history for the input area (Alt+Up / Alt+Down navigation). */
    private final List<String> inputHistory = new ArrayList<>();
    /** Current position while browsing history; -1 means "not browsing". */
    private int historyIndex = -1;
    /** Draft text saved when the user starts browsing backwards so it can be restored. */
    private String historyDraft = null;
    /** Maps copy-button IDs (e.g. "cb-3") to the raw code content to be copied. */
    private final Map<String, String> codeBlockMap = new HashMap<>();
    private final AtomicInteger codeBlockIdCounter = new AtomicInteger(0);

    // CSOFF
    AIChatWindow(List<IAIAssistantProvider> providers, IQueryFileProvider queryFileProvider, IEventBus eventBus)
    {
        super("AI Assistant");
        this.providers = requireNonNull(providers, "providers");
        this.eventBus = requireNonNull(eventBus, "eventBus");
        requireNonNull(queryFileProvider, "queryFileProvider");

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
                    String label = p.name();
                    if (p.getResponseFormat() == ResponseFormat.MARKDOWN)
                    {
                        label += " [md]";
                    }
                    setText(label);
                }
                return this;
            }
        });
        providers.stream()
                .filter(IAIAssistantProvider::isConfigured)
                .findFirst()
                .ifPresent(providerCombo::setSelectedItem);
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

        // Center: scrollable chat history rendered as HTML
        chatPane = new JEditorPane("text/html", buildHtmlStart() + HTML_END);
        chatPane.setEditable(false);
        chatPane.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        chatPane.addHyperlinkListener(copyButtonListener());
        chatScroll = new JScrollPane(chatPane);

        // Detect manual scrolling: if user scrolls away from bottom, disable auto-scroll
        AdjustmentListener scrollListener = e ->
        {
            if (programmaticScroll)
            {
                return;
            }
            JScrollBar bar = chatScroll.getVerticalScrollBar();
            boolean atBottom = bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 5;
            if (atBottom)
            {
                autoScroll = true;
                newContentIndicator.setVisible(false);
            }
            else
            {
                autoScroll = false;
            }
        };
        chatScroll.getVerticalScrollBar()
                .addAdjustmentListener(scrollListener);

        // Indicator shown when auto-scroll is off and new content arrives
        // CSOFF
        JButton scrollDownButton = new JButton("\u25BC New content \u2013 scroll down");
        // CSON
        scrollDownButton.addActionListener(e -> scrollToBottom());
        newContentIndicator = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
        newContentIndicator.add(scrollDownButton);
        newContentIndicator.setVisible(false);

        JPanel chatWrapper = new JPanel(new BorderLayout());
        chatWrapper.add(chatScroll, BorderLayout.CENTER);
        chatWrapper.add(newContentIndicator, BorderLayout.SOUTH);
        getContentPane().add(chatWrapper, BorderLayout.CENTER);

        // Bottom: input area + buttons
        JPanel bottomPanel = new JPanel(new BorderLayout(4, 4));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        inputArea = new JTextArea(4, 40);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        inputArea.setToolTipText("Type your message. Ctrl+Enter to send. Alt+Up/Down for history.");
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
                else if (e.getKeyCode() == KeyEvent.VK_UP
                        && e.isAltDown())
                {
                    navigateHistory(-1);
                    e.consume();
                }
                else if (e.getKeyCode() == KeyEvent.VK_DOWN
                        && e.isAltDown())
                {
                    navigateHistory(1);
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
        autoScroll = true;
        newContentIndicator.setVisible(false);
        updateChatHtml(state != null ? buildHtml(state.completedHtmlBody, null)
                : buildHtmlStart() + HTML_END);
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
            state.completedHtmlBody += errorHtml("No provider configured. Please configure an AI provider in Options.");
            updateChatHtml(buildHtml(state.completedHtmlBody, null));
            return;
        }

        inputArea.setText("");
        // Record in input history (avoid consecutive duplicates)
        if (inputHistory.isEmpty()
                || !inputHistory.get(inputHistory.size() - 1)
                        .equals(userText))
        {
            inputHistory.add(userText);
        }
        historyIndex = -1;
        historyDraft = null;
        state.completedHtmlBody += userHtml(userText);
        state.responseBuilder.setLength(0);
        autoScroll = true;
        newContentIndicator.setVisible(false);
        updateChatHtml(buildHtml(state.completedHtmlBody, ""));
        setResponding(true, state, provider);

        List<AIChatMessage> historySnapshot = new ArrayList<>(state.history);
        String systemPrompt = buildSystemPrompt(state);
        state.history.add(new AIChatMessage(Role.USER, userText));

        AIChatSession session = new AIChatSession(state.sessionId, id -> state.sessionId = id);

        EXECUTOR.submit(() -> provider.chat(historySnapshot, userText, systemPrompt, session, chunk -> SwingUtilities.invokeLater(() ->
        {
            state.responseBuilder.append(chunk);
            // streaming display is handled by the timer
        }), () -> SwingUtilities.invokeLater(() ->
        {
            stopStreamingTimer();
            String responseText = state.responseBuilder.toString();
            state.history.add(new AIChatMessage(Role.ASSISTANT, responseText));
            state.completedHtmlBody += assistantHtml(responseText, provider);
            updateChatHtml(buildHtml(state.completedHtmlBody, null));
            setResponding(false, state, provider);
        }), error -> SwingUtilities.invokeLater(() ->
        {
            stopStreamingTimer();
            state.completedHtmlBody += errorHtml(error.getMessage());
            updateChatHtml(buildHtml(state.completedHtmlBody, null));
            setResponding(false, state, provider);
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

    /** Navigate input history. direction=-1 goes back (older), +1 goes forward (newer). */
    private void navigateHistory(int direction)
    {
        if (inputHistory.isEmpty())
        {
            return;
        }
        if (direction < 0)
        {
            // Going back: save draft on first navigation
            if (historyIndex == -1)
            {
                historyDraft = inputArea.getText();
                historyIndex = inputHistory.size();
            }
            int next = historyIndex - 1;
            if (next >= 0)
            {
                historyIndex = next;
                inputArea.setText(inputHistory.get(historyIndex));
                inputArea.setCaretPosition(inputArea.getText()
                        .length());
            }
        }
        else
        {
            // Going forward
            if (historyIndex == -1)
            {
                return;
            }
            int next = historyIndex + 1;
            if (next < inputHistory.size())
            {
                historyIndex = next;
                inputArea.setText(inputHistory.get(historyIndex));
                inputArea.setCaretPosition(inputArea.getText()
                        .length());
            }
            else
            {
                // Restore draft
                historyIndex = -1;
                inputArea.setText(historyDraft != null ? historyDraft
                        : "");
                historyDraft = null;
                inputArea.setCaretPosition(inputArea.getText()
                        .length());
            }
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
        state.completedHtmlBody = "";
        state.sessionId = null;
        codeBlockMap.clear();
        chatPane.setText(buildHtmlStart() + HTML_END);
    }

    private void setResponding(boolean responding, FileChatState state, IAIAssistantProvider provider)
    {
        this.responding = responding;
        sendButton.setEnabled(!responding);
        inputArea.setEnabled(!responding);
        if (responding)
        {
            startStreamingTimer(state, provider);
        }
    }

    private void startStreamingTimer(FileChatState state, IAIAssistantProvider provider)
    {
        // CSOFF
        streamingTimer = new Timer(250, e ->
        // CSON
        {
            String current = state.responseBuilder.toString();
            updateChatHtml(buildHtml(state.completedHtmlBody, current));
        });
        streamingTimer.setRepeats(true);
        streamingTimer.start();
    }

    private void stopStreamingTimer()
    {
        if (streamingTimer != null)
        {
            streamingTimer.stop();
            streamingTimer = null;
        }
    }

    /**
     * Updates the chat HTML content and handles scroll position. When auto-scroll is enabled the view scrolls to the bottom after the update. When the user has scrolled up the current scroll position
     * is restored and the "new content" indicator is shown.
     */
    private void updateChatHtml(String html)
    {
        JScrollBar vbar = chatScroll.getVerticalScrollBar();
        final int savedPos = autoScroll ? -1
                : vbar.getValue();
        programmaticScroll = true;
        chatPane.setText(html);
        SwingUtilities.invokeLater(() ->
        {
            if (autoScroll)
            {
                vbar.setValue(vbar.getMaximum());
            }
            else
            {
                int max = Math.max(0, vbar.getMaximum() - vbar.getVisibleAmount());
                vbar.setValue(Math.min(savedPos, max));
                newContentIndicator.setVisible(true);
            }
            programmaticScroll = false;
        });
    }

    private void scrollToBottom()
    {
        autoScroll = true;
        newContentIndicator.setVisible(false);
        SwingUtilities.invokeLater(() ->
        {
            programmaticScroll = true;
            JScrollBar vbar = chatScroll.getVerticalScrollBar();
            vbar.setValue(vbar.getMaximum());
            programmaticScroll = false;
        });
    }

    // ---- HTML building helpers ----

    private static String buildHtml(String completedBody, String streamingText)
    {
        StringBuilder sb = new StringBuilder(buildHtmlStart());
        sb.append(completedBody);
        if (streamingText != null)
        {
            sb.append("<p><b class=\"asst-label\">Assistant:</b></p>");
            sb.append("<div class=\"asst-text\">");
            if (streamingText.isEmpty())
            {
                sb.append("<p><em>Starting...</em></p>");
            }
            else
            {
                sb.append("<p>");
                sb.append(escapeHtml(streamingText));
                sb.append("</p>");
            }
            sb.append("</div>");
        }
        sb.append(HTML_END);
        return sb.toString();
    }

    private static String userHtml(String text)
    {
        return "<p><b class=\"user-label\">You:</b></p><div class=\"user-text\">" + renderMarkdown(text) + "</div>";
    }

    private String assistantHtml(String text, IAIAssistantProvider provider)
    {
        String content = provider.getResponseFormat() == ResponseFormat.MARKDOWN ? injectCopyButtons(renderMarkdown(text))
                : "<p>" + escapeHtml(text) + "</p>";
        return "<p><b class=\"asst-label\">Assistant:</b></p><div class=\"asst-text\">" + content + "</div>";
    }

    private static String errorHtml(String message)
    {
        return "<p class=\"error-text\">[Error: " + escapeHtml(message) + "]</p>";
    }

    private static String renderMarkdown(String markdown)
    {
        return MARKDOWN_RENDERER.render(MARKDOWN_PARSER.parse(markdown));
    }

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("<pre><code[^>]*>(.*?)</code></pre>", Pattern.DOTALL);

    /**
     * Wraps each {@code 
     * 
     * 
    
    <pre>
     * <code>} block with a copy-button link and registers the code content.
     */
    private String injectCopyButtons(String html)
    {
        Matcher m = CODE_BLOCK_PATTERN.matcher(html);
        if (!m.find())
        {
            return html;
        }
        StringBuilder sb = new StringBuilder();
        m.reset();
        while (m.find())
        {
            String id = "cb-" + codeBlockIdCounter.incrementAndGet();
            codeBlockMap.put(id, unescapeHtml(m.group(1)));
            // CSOFF
            m.appendReplacement(sb, "<div style=\"text-align:right\"><a class=\"copy-btn\" href=\"" + COPY_SCHEME + id + "\">\u29c9 Copy</a></div>" + Matcher.quoteReplacement(m.group(0)));
            // CSON
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private HyperlinkListener copyButtonListener()
    {
        return e ->
        {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED
                    && e.getDescription() != null
                    && e.getDescription()
                            .startsWith(COPY_SCHEME))
            {
                String id = e.getDescription()
                        .substring(COPY_SCHEME.length());
                String code = codeBlockMap.get(id);
                if (code != null)
                {
                    Toolkit.getDefaultToolkit()
                            .getSystemClipboard()
                            .setContents(new StringSelection(code), null);
                }
            }
        };
    }

    private static String unescapeHtml(String html)
    {
        return html.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }

    private static String escapeHtml(String text)
    {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("\n", "<br>");
    }

    private static class FileChatState
    {
        final List<AIChatMessage> history = new ArrayList<>();
        final List<IAIContextItem> selectedContextItems = new ArrayList<>();
        final StringBuilder responseBuilder = new StringBuilder();
        String completedHtmlBody = "";
        /** Session ID returned by the provider (e.g. Claude Code {@code --resume} ID). Null until first response. */
        String sessionId = null;
    }
}
