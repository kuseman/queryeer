package com.queryeer.jdbc;

/** Type of jdbc. */
public enum JdbcType
{
    JDBC_URL("Plain JDBC"),
    SQLSERVER("MSSql Server");

    final String title;

    JdbcType(String title)
    {
        this.title = title;
    }

    String getTitle()
    {
        return title;
    }

    @Override
    public String toString()
    {
        return title;
    }
}