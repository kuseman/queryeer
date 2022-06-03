package com.queryeer.jdbc;

import com.queryeer.api.component.Property;

/** Model class for raw JDBC */
class JdbcProperties
{
    static final String URL = "url";
    static final String CLASS_NAME = "className";

    private String url;
    private String className;

    JdbcProperties()
    {
    }

    JdbcProperties(JdbcProperties source)
    {
        this.url = source.url;
        this.className = source.className;
    }

    @Property(
            order = 0,
            title = "JDBC Url")
    public String getUrl()
    {
        return url;
    }

    public void setUrl(String url)
    {
        this.url = url;
    }

    @Property(
            order = 1,
            title = "Driver Class Name")
    public String getClassName()
    {
        return className;
    }

    public void setClassName(String className)
    {
        this.className = className;
    }
}