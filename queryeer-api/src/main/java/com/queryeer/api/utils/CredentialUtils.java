package com.queryeer.api.utils;

import static com.queryeer.api.utils.StringUtils.isBlank;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.BoxLayout;
import javax.swing.FocusManager;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentListener;

import com.queryeer.api.component.ADocumentListenerAdapter;
import com.queryeer.api.component.AnimatedIcon;

/** CredentialUtils */
public final class CredentialUtils
{
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(new ThreadFactory()
    {
        @Override
        public Thread newThread(Runnable r)
        {
            Thread thread = new Thread(r, "CredentialUtils-Validation");
            thread.setDaemon(true);
            return thread;
        }
    });
    private static final Icon SPINNER = AnimatedIcon.createSmallSpinner();
    private static final int PASSWORD_FIELD_LENGTH = 20;

    /** Show dialog that ask for credentials */
    public static Credentials getCredentials(String connectionDescription, String prefilledUsername)
    {
        return getCredentials(connectionDescription, prefilledUsername, false);
    }

    /** Show dialog that ask for credentials */
    public static Credentials getCredentials(String connectionDescription, String prefilledUsername, boolean readOnlyUsername)
    {
        return getCredentials(connectionDescription, prefilledUsername, readOnlyUsername, null);
    }

    /** Show dialog that ask for credentials */
    public static Credentials getCredentials(String connectionDescription, String prefilledUsername, boolean readOnlyUsername, ValidationHandler validationHandler)
    {
        Window activeWindow = FocusManager.getCurrentManager()
                .getActiveWindow();

        JDialog dialog = new JDialog();
        if (activeWindow != null)
        {
            // Find first window in hierarchy that has icon images else use null
            List<Image> iconImages = activeWindow.getIconImages();
            while (activeWindow != null
                    && (iconImages == null
                            || iconImages.isEmpty()))
            {
                activeWindow = activeWindow.getParent() instanceof Window w ? w
                        : null;
                iconImages = activeWindow != null ? activeWindow.getIconImages()
                        : null;
            }

            dialog.setIconImages(iconImages);
        }
        dialog.setTitle("Enter Credentials");
        dialog.setModal(true);
        dialog.getContentPane()
                .setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.BASELINE_LEADING;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 10, 3, 0);
        dialog.getContentPane()
                .add(new JLabel("Connection: "), gbc);

        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.BASELINE_LEADING;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 0, 3, 10);
        dialog.getContentPane()
                .add(new JLabel(connectionDescription), gbc);

        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.BASELINE_LEADING;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 10, 3, 0);
        dialog.getContentPane()
                .add(new JLabel("Username: "), gbc);

        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0d;
        gbc.anchor = GridBagConstraints.BASELINE_LEADING;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.ipadx = 3;
        gbc.insets = new Insets(0, 0, 3, 10);

        JTextField usernameField = new JTextField();
        JPasswordField passwordField = new JPasswordField(PASSWORD_FIELD_LENGTH);
        JButton okButton = new JButton("OK");
        DocumentListener documentListener = new ADocumentListenerAdapter()
        {
            @Override
            protected void update()
            {
                okButton.setEnabled(passwordField.getPassword() != null
                        && passwordField.getPassword().length > 0
                        && usernameField.getText()
                                .length() > 0);
            }
        };

        usernameField.getDocument()
                .addDocumentListener(documentListener);
        dialog.getContentPane()
                .add(usernameField, gbc);

        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.BASELINE_LEADING;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 10, 3, 0);
        dialog.getContentPane()
                .add(new JLabel("Password: "), gbc);

        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 1.0d;
        gbc.anchor = GridBagConstraints.BASELINE_LEADING;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 3, 10);

        passwordField.getDocument()
                .addDocumentListener(documentListener);
        dialog.getContentPane()
                .add(passwordField, gbc);

        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.weightx = 1.0d;
        gbc.weightx = 1.0d;
        gbc.anchor = GridBagConstraints.BASELINE_LEADING;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 0, 3, 10);
        JLabel validationLabel = new JLabel(" ");
        dialog.getContentPane()
                .add(validationLabel, gbc);

        AtomicBoolean result = new AtomicBoolean(false);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));

        AtomicReference<Future<?>> validationFuture = new AtomicReference<>();
        ActionListener okAction = l ->
        {
            if (validationHandler != null)
            {
                validationLabel.setIcon(SPINNER);
                validationLabel.setText("Validating");

                okButton.setEnabled(false);
                passwordField.setEnabled(false);

                boolean usernameFieldEnabled = usernameField.isEnabled();
                if (usernameFieldEnabled)
                {
                    usernameField.setEnabled(false);
                }

                validationFuture.set(EXECUTOR.submit(() ->
                {

                    AtomicBoolean validationResult = new AtomicBoolean(true);
                    try
                    {
                        validationResult.set(validationHandler.validate(usernameField.getText(), passwordField.getPassword()));
                    }
                    catch (Exception e)
                    {
                    }

                    SwingUtilities.invokeLater(() ->
                    {
                        validationLabel.setIcon(null);
                        validationLabel.setText(" ");
                        okButton.setEnabled(true);
                        passwordField.setEnabled(true);
                        if (usernameFieldEnabled)
                        {
                            usernameField.setEnabled(true);
                        }
                        okButton.requestFocusInWindow();

                        if (validationResult.get())
                        {
                            result.set(true);
                            dialog.dispose();
                            dialog.setVisible(false);
                        }
                        else
                        {
                            String message = validationHandler.getFailureMessage();
                            JOptionPane.showMessageDialog(dialog, message, "Validation Error", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                }));
            }
            else
            {
                result.set(true);
                dialog.dispose();
                dialog.setVisible(false);
            }
        };

        okButton.addActionListener(okAction);
        buttonPanel.add(okButton);
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(l ->
        {
            dialog.dispose();
            dialog.setVisible(false);
        });
        buttonPanel.add(cancelButton);

        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.weighty = 1.0d;
        gbc.anchor = GridBagConstraints.SOUTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 10, 10);
        dialog.getContentPane()
                .add(buttonPanel, gbc);

        usernameField.setText(prefilledUsername);
        usernameField.setEditable(!readOnlyUsername);
        JTextField focusCompoennt = !readOnlyUsername
                && isBlank(usernameField.getText()) ? usernameField
                        : passwordField;

        dialog.addComponentListener(new ComponentAdapter()
        {
            @Override
            public void componentShown(ComponentEvent e)
            {
                focusCompoennt.requestFocusInWindow();
                super.componentShown(e);
            }
        });

        dialog.getRootPane()
                .registerKeyboardAction(new ActionListener()
                {
                    @Override
                    public void actionPerformed(ActionEvent e)
                    {
                        dialog.dispose();
                        dialog.setVisible(false);
                    }
                }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        passwordField.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyTyped(KeyEvent e)
            {
                if (e.getKeyChar() == KeyEvent.VK_ENTER
                        && okButton.isEnabled())
                {
                    okAction.actionPerformed(null);
                }
                super.keyTyped(e);
            }
        });

        okButton.setEnabled(passwordField.getPassword() != null
                && passwordField.getPassword().length > 0
                && usernameField.getText()
                        .length() > 0);

        dialog.setLocationRelativeTo(activeWindow);
        dialog.setPreferredSize(new Dimension(300, 170));
        dialog.pack();
        dialog.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        dialog.setVisible(true);

        Future<?> future = validationFuture.get();

        if (future != null
                && !future.isDone())
        {
            future.cancel(true);
        }

        if (result.get())
        {
            String username = usernameField.getText();
            char[] password = passwordField.getPassword();

            if (!isBlank(username)
                    && !ArrayUtils.isEmpty(password))
            {
                return new Credentials(username, password);
            }
        }
        return null;
    }

    /** Result of credentials dialog */
    public static class Credentials
    {
        private final String username;
        private final char[] password;

        public Credentials(String username, char[] password)
        {
            this.username = username;
            this.password = password;
        }

        public String getUsername()
        {
            return username;
        }

        public char[] getPassword()
        {
            return password;
        }
    }

    /** Optional validation handler for Credentials dialog. */
    public interface ValidationHandler
    {
        /**
         * Method that will be executed in a different thread that can test connections etc. and whilst doing that UI will show a spinner.
         *
         * @return True if no validation error occurred and dialog will close otherwise false and dialog will still be open.
         */
        boolean validate(String username, char[] password);

        /** If {@link #validate()} returned false this method is called to show why. */
        String getFailureMessage();
    }
}
