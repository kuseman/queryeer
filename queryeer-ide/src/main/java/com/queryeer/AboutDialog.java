package com.queryeer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.event.HyperlinkEvent;

import org.apache.commons.io.IOUtils;

import se.kuseman.payloadbuilder.core.Payloadbuilder;

/** About dialog */
class AboutDialog extends JDialog
{
    private final String version;
    private final File etcFolder;
    private static final String CHANGELOG = readChangeLog();

    AboutDialog(JFrame parent, String version, File etcFolder)
    {
        this.version = version;
        this.etcFolder = etcFolder;
        initDialog();
    }

    private void initDialog()
    {
        setTitle("About Queryeer IDE");
        setIconImages(Constants.APPLICATION_ICONS);
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

        JTextArea changelog = new JTextArea();
        changelog.setEditable(false);
        changelog.setText(CHANGELOG);

        JLabel banner = new JLabel(getBanner());
        banner.setVerticalAlignment(SwingConstants.TOP);
        contentPanel.add(banner, new GridBagConstraints(0, 0, 1, 3, 0, 1, GridBagConstraints.NORTH, GridBagConstraints.BOTH, new Insets(5, 5, 0, 0), 0, 0));
        contentPanel.add(aboutText, new GridBagConstraints(1, 0, 1, 1, 0, 0, GridBagConstraints.BASELINE, GridBagConstraints.BOTH, new Insets(5, 5, 0, 0), 0, 0));
        contentPanel.add(new JScrollPane(changelog), new GridBagConstraints(1, 1, 1, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(5, 5, 5, 5), 0, 0));

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

    @Override
    public void setVisible(boolean b)
    {
        if (b)
        {
            Window activeWindow = javax.swing.FocusManager.getCurrentManager()
                    .getActiveWindow();
            setLocationRelativeTo(activeWindow);
        }
        super.setVisible(b);
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
        JOptionPane.showMessageDialog(this, pane, "Version Check", JOptionPane.INFORMATION_MESSAGE);
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
        sb.append("\n\n");
        sb.append("(C) Copyright Marcus Henriksson 2025");

        return sb.toString();
    }

    private static String readChangeLog()
    {
        try
        {
            return IOUtils.toString(AboutDialog.class.getResourceAsStream("/changelog.txt"), StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {
            return "";
        }
    }
}
