package com.queryeer.api.utils;

import java.util.Arrays;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import com.queryeer.api.utils.CredentialUtils.Credentials;
import com.queryeer.api.utils.CredentialUtils.ValidationHandler;

/** Manual test of {@link CredentialUtils}. */
public class CredentialsUtilsTest
{
    /** Main. */
    public static void main(String[] args)
    {
        try
        {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ex)
        // CSOFF
        {
            ex.printStackTrace();
        }

        Credentials credentials = CredentialUtils.getCredentials("Connection", "das", true, new ValidationHandler()
        {
            private String message;

            @Override
            public boolean validate(String username, char[] password)
            {
                try
                {
                    Thread.sleep(500);
                }
                catch (InterruptedException e)
                {
                }
                message = username + " " + Arrays.toString(password) + " is not valid. dsslakökda saskld sdlkdsalksdalöksdalöksdalöksdlsdaldsa dd löasd lkö asdsadl ksdalk";
                return false;
            }

            @Override
            public String getFailureMessage()
            {
                return message;
            }
        });
        if (credentials != null)
        {
            System.out.println(credentials.getPassword() != null ? Arrays.toString(credentials.getPassword())
                    : null);
        }
        else
        {
            System.out.println("Cancel");
        }
    }
}
