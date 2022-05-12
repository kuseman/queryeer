package com.queryeer.api.utils;

import static se.kuseman.payloadbuilder.api.utils.StringUtils.isBlank;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

import se.kuseman.payloadbuilder.api.utils.ArrayUtils;

/** CredentialUtils */
public final class CredentialUtils
{
    private static final int PASSWORD_FIELD_LENGTH = 20;

    /** Show dialog that ask for credentials */
    public static Credentials getCredentials(String connectionDescription, String prefilledUsername)
    {
        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        JTextField usernameField = new JTextField();
        JPasswordField passwordField = new JPasswordField(PASSWORD_FIELD_LENGTH);
        panel.add(new JLabel("Connection: "), new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 3, 0), 0, 0));
        panel.add(new JLabel(connectionDescription), new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 3, 0), 0, 0));
        panel.add(new JLabel("Username: "), new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 3, 0), 0, 0));
        panel.add(usernameField, new GridBagConstraints(1, 1, 1, 1, 1.0, 0.0, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 3, 0));
        panel.add(new JLabel("Password: "), new GridBagConstraints(0, 2, 1, 1, 0.0, 0.0, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
        panel.add(passwordField, new GridBagConstraints(1, 2, 1, 1, 1.0, 1.0, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));

        usernameField.setText(prefilledUsername);
        JTextField focusCompoennt = isBlank(usernameField.getText()) ? usernameField
                : passwordField;

        focusCompoennt.addAncestorListener(new AncestorListener()
        {
            @Override
            public void ancestorRemoved(AncestorEvent event)
            {
            }

            @Override
            public void ancestorMoved(AncestorEvent event)
            {
            }

            @Override
            public void ancestorAdded(final AncestorEvent event)
            {
                event.getComponent()
                        .requestFocusInWindow();
                event.getComponent()
                        .removeAncestorListener(this);
            }
        });

        String[] options = new String[] { "OK", "Cancel" };
        int option = JOptionPane.showOptionDialog(null, panel, "Enter Credentials", JOptionPane.NO_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (option == 0)
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

        Credentials(String username, char[] password)
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
}
