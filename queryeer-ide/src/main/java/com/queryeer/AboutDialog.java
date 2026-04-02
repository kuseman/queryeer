package com.queryeer;

import static java.util.Objects.requireNonNull;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.event.HyperlinkEvent;

import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import com.queryeer.api.component.DialogUtils;

import se.kuseman.payloadbuilder.core.Payloadbuilder;

/** About dialog */
class AboutDialog extends DialogUtils.ADialog
{
    // CSOFF
    private static final Parser MARKDOWN_PARSER = Parser.builder()
            .extensions(List.of(TablesExtension.create(), StrikethroughExtension.create()))
            .build();
    private static final HtmlRenderer MARKDOWN_RENDERER = HtmlRenderer.builder()
            .extensions(List.of(TablesExtension.create(), StrikethroughExtension.create()))
            .build();
    // CSON

    private static final String CHANGELOG = readChangeLog();
    private static final String PAYLOADBUILDER_CHANGELOG = readPayloadbuilderChangeLog();
    private final String version;
    private final File etcFolder;
    private final File sharedFolder;

    AboutDialog(JFrame parent, String version, File etcFolder, File sharedFolder)
    {
        this.version = version;
        this.etcFolder = requireNonNull(etcFolder);
        this.sharedFolder = sharedFolder;
        initDialog();
    }

    private void initDialog()
    {
        setTitle("About Queryeer IDE");
        getContentPane().setLayout(new BorderLayout());

        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new FlowLayout());

        JButton close = new JButton("Close");
        close.addActionListener(l -> setVisible(false));
        bottomPanel.add(close);

        JButton checkForUpdates = new JButton("Check For Updates");
        checkForUpdates.addActionListener(l -> checkNewVersion());
        bottomPanel.add(checkForUpdates);

        JTextArea aboutText = new JTextArea(getAboutString());
        aboutText.setEditable(false);
        aboutText.setOpaque(false);
        aboutText.setBorder(BorderFactory.createEmptyBorder());
        aboutText.setBackground(new Color(0, 0, 0, 0));

        getContentPane().add(bottomPanel, BorderLayout.SOUTH);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new GridBagLayout());

        JLabel banner = new JLabel(getBanner());
        banner.setVerticalAlignment(SwingConstants.TOP);
        contentPanel.add(banner, new GridBagConstraints(0, 0, 1, 3, 0, 1, GridBagConstraints.NORTH, GridBagConstraints.BOTH, new Insets(5, 5, 0, 0), 0, 0));
        contentPanel.add(aboutText, new GridBagConstraints(1, 0, 1, 1, 0, 0, GridBagConstraints.BASELINE, GridBagConstraints.BOTH, new Insets(5, 5, 0, 0), 0, 0));

        JTabbedPane changelogTabs = new JTabbedPane();
        if (!CHANGELOG.isEmpty())
        {
            changelogTabs.addTab("Queryeer", new JScrollPane(createChangelogPane(CHANGELOG)));
        }
        if (PAYLOADBUILDER_CHANGELOG != null)
        {
            changelogTabs.addTab("Payloadbuilder", new JScrollPane(createChangelogPane(PAYLOADBUILDER_CHANGELOG)));
        }
        contentPanel.add(changelogTabs, new GridBagConstraints(1, 1, 1, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(5, 5, 5, 5), 0, 0));

        getContentPane().add(contentPanel, BorderLayout.CENTER);

        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        setPreferredSize(Constants.DEFAULT_DIALOG_SIZE);
        pack();
        setLocationRelativeTo(null);
        pack();

        setModalityType(ModalityType.APPLICATION_MODAL);
    }

    String getNewVersionString()
    {
        String latest = Utils.getLatestTag();
        String message = null;
        if (latest != null
                && Utils.compareVersions(latest, version) < 0)
        {
            message = "<html>New version " + latest + " is available. Get at <a href=\"https://github.com/kuseman/queryeer/releases\">Releases</a>!";
        }
        return message;
    }

    void showNewVersionMessage(String message)
    {
        JEditorPane pane = new JEditorPane("text/html", message);
        pane.addHyperlinkListener(e ->
        {
            if (e.getEventType()
                    .equals(HyperlinkEvent.EventType.ACTIVATED))
            {
                try
                {
                    java.awt.Desktop.getDesktop()
                            .browse(e.getURL()
                                    .toURI());
                }
                catch (IOException | URISyntaxException e1)
                {
                    e1.printStackTrace();
                }
            }
        });
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setBackground(new Color(0, 0, 0, 0));

        Window activeWindow = javax.swing.FocusManager.getCurrentManager()
                .getActiveWindow();

        JOptionPane.showMessageDialog(activeWindow, pane, "Version Check", JOptionPane.INFORMATION_MESSAGE);
    }

    void checkNewVersion()
    {
        String message = getNewVersionString();
        if (message == null)
        {
            message = "No new version found";
        }

        showNewVersionMessage(message);
    }

    private ImageIcon getBanner()
    {
        try
        {
            return new ImageIcon(ImageIO.read(AboutDialog.class.getResource("/icons/sql.png")));
        }
        catch (IOException e)
        {
            throw new RuntimeException("Error fetching image", e);
        }
    }

    private String getAboutString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Queryeer IDE\n\n");

        sb.append("Queryeer Version: " + Objects.toString(AboutDialog.class.getPackage()
                .getImplementationVersion(), "Dev"));
        sb.append("\n");
        sb.append("Payloadbuilder Version: " + Objects.toString(Payloadbuilder.class.getPackage()
                .getImplementationVersion(), "Dev"));
        sb.append("\n");
        sb.append("Config Location: " + etcFolder.getAbsolutePath());
        sb.append("\n");
        sb.append("Shared Location: " + (sharedFolder != null ? sharedFolder.getAbsolutePath()
                : ""));
        sb.append("\n\n");
        sb.append("(C) Copyright Marcus Henriksson (" + LocalDateTime.now()
                .getYear() + ")");

        return sb.toString();
    }

    // CSOFF
    private static String buildHtmlStyle()
    {
        if (UiUtils.isDarkLookAndFeel())
        {
            return "body { font-family: sans-serif; font-size: 12pt; margin: 8px; }" + " pre { font-family: monospace; font-size: 11pt; background-color: #2b2b2b; padding: 6px; margin: 4px; }"
                   + " code { font-family: monospace; font-size: 11pt; background-color: #2b2b2b; }"
                   + " blockquote { margin-left: 16px; }"
                   + " table { border: 1px solid #555555; border-collapse: collapse; }"
                   + " td, th { border: 1px solid #555555; padding: 4px 8px; }";
        }
        return "body { font-family: sans-serif; font-size: 12pt; margin: 8px; }" + " pre { font-family: monospace; font-size: 11pt; background-color: #f0f0f0; padding: 6px; margin: 4px; }"
               + " code { font-family: monospace; font-size: 11pt; background-color: #f0f0f0; }"
               + " blockquote { margin-left: 16px; }"
               + " table { border: 1px solid #cccccc; border-collapse: collapse; }"
               + " td, th { border: 1px solid #cccccc; padding: 4px 8px; }";
    }
    // CSON

    private static String renderMarkdownToHtml(String markdown)
    {
        String body = MARKDOWN_RENDERER.render(MARKDOWN_PARSER.parse(markdown));
        return "<html><head><style>" + buildHtmlStyle() + "</style></head><body>" + body + "</body></html>";
    }

    private static JEditorPane createChangelogPane(String html)
    {
        JEditorPane pane = new JEditorPane("text/html", html);
        pane.setEditable(false);
        pane.addHyperlinkListener(e ->
        {
            if (e.getEventType()
                    .equals(HyperlinkEvent.EventType.ACTIVATED))
            {
                try
                {
                    Desktop.getDesktop()
                            .browse(e.getURL()
                                    .toURI());
                }
                catch (IOException | URISyntaxException e1)
                {
                    e1.printStackTrace();
                }
            }
        });
        return pane;
    }

    private static String readChangeLog()
    {
        return readChangeLogForClass(AboutDialog.class, "");
    }

    private static String readPayloadbuilderChangeLog()
    {
        return readChangeLogForClass(Payloadbuilder.class, null);
    }

    private static String readChangeLogForClass(Class<?> clazz, String fallback)
    {
        try
        {
            URL location = clazz.getProtectionDomain()
                    .getCodeSource()
                    .getLocation();
            if (location == null)
            {
                return fallback;
            }
            Path locationPath = Path.of(location.toURI());
            if (location.getPath()
                    .endsWith(".jar"))
            {
                try (FileSystem fs = FileSystems.newFileSystem(locationPath, (ClassLoader) null))
                {
                    Path changelogPath = fs.getPath("/CHANGELOG.md");
                    if (Files.exists(changelogPath))
                    {
                        return renderMarkdownToHtml(new String(Files.readAllBytes(changelogPath), StandardCharsets.UTF_8));
                    }
                }
            }
            else
            {
                // Dev mode: running from classes directory
                Path changelogPath = locationPath.resolve("CHANGELOG.md");
                if (Files.exists(changelogPath))
                {
                    return renderMarkdownToHtml(new String(Files.readAllBytes(changelogPath), StandardCharsets.UTF_8));
                }
            }
        }
        catch (Exception e)
        {
            // ignore
        }
        return fallback;
    }
}
