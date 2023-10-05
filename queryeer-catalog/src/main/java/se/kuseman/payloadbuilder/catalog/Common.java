package se.kuseman.payloadbuilder.catalog;

/** Common for catalogs */
public class Common
{
    public static final String AUTH_STATUS_LOCKED_TOOLTIP = """
            <html>
            <h4>Connection missing credentials</h4>
            Reload or trigger a query to with this connection to set credentials.
            <br/>
            <b>NOTE! No auto completions etc. will be loaded until connection has a valid credentials</n>
            """;

    public static final String AUTH_PASSWORD_TOOLTIP = """
            <html>
            <h4>Password to use on connection</h4>
            <pre>
            NOTE!
            Passwords are encrypted using a <b>master password</b> and is never stored in plain text.
            The master password is requested once during the applications life time and is never stored on disk!

            It's optional to persist a connection password and instead enter the connection password once for each
            connection. (It's stored in memory during application life time).
            </pre>
            """;
}
