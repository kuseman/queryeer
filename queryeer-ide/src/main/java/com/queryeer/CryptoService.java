package com.queryeer;

import static com.queryeer.api.utils.StringUtils.isBlank;
import static java.util.Objects.requireNonNull;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

import org.apache.commons.lang3.ArrayUtils;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.exceptions.EncryptionOperationNotPossibleException;

import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.service.ICryptoService;

/** Jasypt implementation of {@link ICryptoService} */
class CryptoService implements ICryptoService, IConfigurable
{
    static final String PREFIX = "ENC(";
    static final String SUFFIX = ")";

    private final ServiceLoader serviceLoader;
    private StandardPBEStringEncryptor stringEncryptor;
    private JPanel configurableComponent;

    CryptoService(ServiceLoader serviceLoader)
    {
        this.stringEncryptor = new StandardPBEStringEncryptor();
        this.serviceLoader = requireNonNull(serviceLoader, "serviceLoader");
    }

    @Override
    public String decryptString(String value)
    {
        if (!isBlank(value)
                && value.startsWith(PREFIX)
                && value.endsWith(SUFFIX))
        {
            synchronized (this)
            {
                for (int i = 0; i < 50; i++)
                {
                    if (!initalizeEncryptor())
                    {
                        return null;
                    }
                    try
                    {
                        return stringEncryptor.decrypt(value.substring(4, value.length() - 1));
                    }
                    catch (EncryptionOperationNotPossibleException e)
                    {
                        // Most likely wrong master password, create a new decryptor and ask for password again
                        stringEncryptor = new StandardPBEStringEncryptor();
                    }
                }

                // If we could not decrypt in certain amount of tries then skip
                return null;
            }
        }

        return value;
    }

    @Override
    public String encryptString(String value)
    {
        if (isBlank(value))
        {
            return value;
        }

        // Value already encrypted
        if (value.startsWith(PREFIX)
                && value.endsWith(SUFFIX))
        {
            return value;
        }

        synchronized (this)
        {
            if (!initalizeEncryptor())
            {
                return null;
            }
        }
        return PREFIX + stringEncryptor.encrypt(value) + SUFFIX;
    }

    @Override
    public boolean isInitalized()
    {
        return stringEncryptor.isInitialized();
    }

    @Override
    public String getTitle()
    {
        return "Encryption";
    }

    @Override
    public String groupName()
    {
        return "Security";
    }

    @Override
    public void addDirtyStateConsumer(Consumer<Boolean> consumer)
    {
    }

    @Override
    public Component getComponent()
    {
        if (configurableComponent == null)
        {
            configurableComponent = new JPanel();
            configurableComponent.setLayout(new GridBagLayout());

            JLabel header = new JLabel("<html><h2>Encryption</h2><hr>");
            header.setHorizontalAlignment(JLabel.CENTER);
            configurableComponent.add(header, new GridBagConstraints(0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 3, 3, 3), 0, 0));

            final List<IConfigurable> configurables = serviceLoader.getAll(IConfigurable.class);

            configurableComponent.add(new JLabel("Change master password. Re-encrypt all configured secrets and store do disk."),
                    new GridBagConstraints(0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 3, 3, 3), 0, 0));

            JButton button = new JButton("Change master password");
            button.addActionListener(e -> changeMasterPassword(configurables));

            configurableComponent.add(button, new GridBagConstraints(0, 2, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 3, 3, 3), 0, 0));
        }
        return configurableComponent;
    }

    void changeMasterPassword(List<IConfigurable> configurables)
    {
        synchronized (this)
        {
            char[] newMasterPassword = getMasterPassword("Enter New Master password:", "Change Master Encryption Password", """
                    <html>NOTE! If current session is locked a dialog with unlocking will appear"
                    """);
            if (newMasterPassword != null)
            {
                StandardPBEStringEncryptor newStringEncryptor = new StandardPBEStringEncryptor();
                newStringEncryptor.setPasswordCharArray(newMasterPassword);

                ICryptoService newCryptoService = new ICryptoService()
                {
                    @Override
                    public String decryptString(String value)
                    {
                        throw new IllegalArgumentException("This service does not support decryption");
                    }

                    @Override
                    public String encryptString(String value)
                    {
                        return PREFIX + newStringEncryptor.encrypt(value) + SUFFIX;
                    }

                    @Override
                    public boolean isInitalized()
                    {
                        return true;
                    }
                };

                List<IConfigurable> modifiedConfigurables = new ArrayList<>();
                // Re-encrypt configurables
                boolean revert = false;
                for (IConfigurable configurable : configurables)
                {
                    try
                    {
                        EncryptionResult result = configurable.reEncryptSecrets(newCryptoService);
                        if (result == EncryptionResult.NO_CHANGE)
                        {
                            continue;
                        }
                        else if (result == EncryptionResult.SUCCESS)
                        {
                            modifiedConfigurables.add(configurable);
                        }
                        else if (result == EncryptionResult.ABORT)
                        {
                            revert = true;
                            break;
                        }
                    }
                    catch (Throwable e)
                    {
                        showErrorMessage(configurableComponent, "An error occured when re-encrypt: " + e.getMessage());
                        revert = true;
                    }
                }

                if (!modifiedConfigurables.isEmpty())
                {
                    // Revert/commit changed configurables
                    for (IConfigurable configurable : modifiedConfigurables)
                    {
                        try
                        {
                            if (revert)
                            {
                                configurable.revertChanges();
                            }
                            else
                            {
                                configurable.commitChanges();
                            }
                        }
                        catch (Throwable e)
                        {
                            showErrorMessage(configurableComponent, "An error occured when " + (revert ? "reverting"
                                    : "committing") + " changes: " + e.getMessage());
                        }
                    }
                }
                // Switch the string encryptor upon success
                if (!revert)
                {
                    this.stringEncryptor = newStringEncryptor;
                }
            }
        }
    }

    private boolean initalizeEncryptor()
    {
        if (!stringEncryptor.isInitialized())
        {
            char[] masterPassword = getMasterPassword("Master password:", "Enter master encryption password", """
                    <html>
                    Master password is used to encrypt/decrypt sensitive data in configurations.<br/>
                    NOTE! This password is only stored in memory during application lifetime and needs to be re-entered<br/>
                    once every start.<br/>
                    NOTE! This password is not recoverable, if lost or other problems arises it'd advised to clear all encrypted data in configuration files and start over.
                    """);
            if (masterPassword != null)
            {
                stringEncryptor.setPasswordCharArray(masterPassword);
                stringEncryptor.initialize();
                return true;
            }
            else
            {
                return false;
            }
        }
        else
        {
            return true;
        }
    }

    protected void showErrorMessage(JComponent parent, String message)
    {
        JOptionPane.showMessageDialog(configurableComponent, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    protected char[] getMasterPassword(String passwordLabel, String title, String message)
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel label = new JLabel(passwordLabel);
        label.setAlignmentX(0.0f);
        JPasswordField pass = new JPasswordField(10);
        pass.addAncestorListener(new AncestorListener()
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
            public void ancestorAdded(AncestorEvent event)
            {
                JComponent component = event.getComponent();
                component.requestFocusInWindow();
                component.removeAncestorListener(this);
            }
        });
        pass.setAlignmentX(0.0f);
        panel.add(label);
        panel.add(pass);
        JLabel messageLabel = new JLabel(message);
        messageLabel.setAlignmentX(0.0f);
        panel.add(messageLabel);
        String[] options = new String[] { "OK", "Cancel" };
        int option = JOptionPane.showOptionDialog(null, panel, title, JOptionPane.NO_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (option == 0
                && !ArrayUtils.isEmpty(pass.getPassword())) // pressing OK button
        {
            return pass.getPassword();
        }

        return null;
    }
}
