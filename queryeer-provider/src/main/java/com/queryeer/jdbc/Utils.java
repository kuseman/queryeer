package com.queryeer.jdbc;

import com.queryeer.api.utils.CredentialUtils;
import com.queryeer.api.utils.CredentialUtils.Credentials;

/** Public utils for Jdbc components */
public class Utils
{
    /**
     * Ensures that provided connection has credentials. If missing a dialog is shown for input.
     *
     * @return Returns true if user entered credentials or false if cancelled.
     */
    public static CredentialsResult ensureCredentials(IJdbcConnection connection)
    {
        if (connection.hasCredentials())
        {
            return CredentialsResult.CREDENTIALS_PRESENT;
        }

        Credentials credentials = CredentialUtils.getCredentials(connection.getName(), connection.getUsername(), true);

        if (credentials == null)
        {
            return CredentialsResult.CANCELLED;
        }

        connection.setPassword(credentials.getPassword());
        return CredentialsResult.CREDENTIALS_PROVIDED;
    }

    /** Result of a credentials input */
    public enum CredentialsResult
    {
        /** User cancelled input */
        CANCELLED,

        /** Credentials already set on connection */
        CREDENTIALS_PRESENT,

        /** User entered credentials */
        CREDENTIALS_PROVIDED
    }
}
