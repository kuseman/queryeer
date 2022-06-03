package com.queryeer.jdbc;

/** A jdbc connection */
public interface IJdbcConnection
{
    /** Get JDBC url from connection */
    String getJdbcURL();

    /** Get driver class name */
    String getJdbcDriverClassName();

    /** Gets if this connection has credentials or not */
    boolean hasCredentials();

    /** Get username */
    String getUsername();

    /** Set password on this connection */
    void setPassword(char[] password);

    /** Get the password for this connection */
    char[] getPassword();

    /** Get name of connection */
    String getName();

    /** Get type of jdbc connection */
    JdbcType getType();
}
