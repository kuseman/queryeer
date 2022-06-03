package com.queryeer.jdbc;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.queryeer.api.component.IPropertyAware;
import com.queryeer.api.component.Property;

/** Model class for SqlServer properties */
class SqlServerProperties implements IPropertyAware
{
    static final String SERVER = "server";
    static final String DOMAIN = "domain";
    static final String AUTHENTICATION_TYPE = "authenticationType";
    static final String APPLICATION_NAME = "applicationName";
    static final String URL_SUFFIX = "urlSuffix";

    private String server;
    private String domain;
    private SqlServerProperties.AuthenticationType authenticationType = AuthenticationType.SQL_SERVER_AUTHENTICATION;
    private String applicatioName;
    private String urlSuffix;

    SqlServerProperties()
    {
    }

    SqlServerProperties(SqlServerProperties source)
    {
        server = source.server;
        domain = source.domain;
        authenticationType = source.authenticationType;
        applicatioName = source.applicatioName;
        urlSuffix = source.urlSuffix;
    }

    @Property(
            order = 0,
            title = "Server")
    public String getServer()
    {
        return server;
    }

    public void setServer(String server)
    {
        this.server = server;
    }

    @Property(
            order = 1,
            title = "Authentication Type")
    public SqlServerProperties.AuthenticationType getAuthenticationType()
    {
        return authenticationType;
    }

    public void setAuthenticationType(SqlServerProperties.AuthenticationType authenticationType)
    {
        this.authenticationType = authenticationType;
    }

    @Property(
            order = 2,
            title = "Domain Name",
            description = "Used when authentication type is Windows Authentication or Kerberos",
            enableAware = true)
    public String getDomain()
    {
        return domain;
    }

    public void setDomain(String domain)
    {
        this.domain = domain;
    }

    @Property(
            order = 3,
            title = "Application Name",
            description = "A custom name for the connection")
    public String getApplicatioName()
    {
        return applicatioName;
    }

    public void setApplicatioName(String applicatioName)
    {
        this.applicatioName = applicatioName;
    }

    @Property(
            order = 4,
            title = "URL Suffix",
            description = "Last part of connection with parameters separated with ;")
    public String getUrlSuffix()
    {
        return urlSuffix;
    }

    public void setUrlSuffix(String urlSuffix)
    {
        this.urlSuffix = urlSuffix;
    }

    String getJdbcUrl()
    {
        return authenticationType.generateURL(server, domain, applicatioName, urlSuffix);
    }

    String getJdbcDriverClassName()
    {
        return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    }

    @Override
    public boolean enabled(String property)
    {
        if (DOMAIN.equals(property))
        {
            return authenticationType == AuthenticationType.KERBEROS_AUTHENTICATION_CREDENTIAlS
                    || authenticationType == AuthenticationType.WINDOWS_AUTHENTICATION_CREDENTIAlS;
        }

        return true;
    }

    /** Authentication type */
    enum AuthenticationType
    {
        /** Authentication with windows domain user and password */
        WINDOWS_AUTHENTICATION_CREDENTIAlS("Windows NTLM Authentication")
        {
            @Override
            String generateURL(String server, String domain, String applicationName, String urlSuffix)
            {
                return "jdbc:sqlserver://" + server
                       + ";integratedSecurity=true;authenticationScheme=NTLM"
                       + ";domain="
                       + domain
                       + (!isBlank(applicationName) ? (";applicationName=" + applicationName)
                               : "")
                       + (!isBlank(urlSuffix) ? (";" + urlSuffix)
                               : "");
            }
        },

        KERBEROS_AUTHENTICATION_CREDENTIAlS("Kerberos Authentication")
        {
            @Override
            String generateURL(String server, String domain, String applicationName, String urlSuffix)
            {
                return "jdbc:sqlserver://" + server
                       + ";integratedSecurity=true;authenticationScheme=JavaKerberos"
                       + ";domain="
                       + domain
                       + (!isBlank(applicationName) ? (";applicationName=" + applicationName)
                               : "")
                       + (!isBlank(urlSuffix) ? (";" + urlSuffix)
                               : "");
            }
        },

        /** Authentication with sql server user and password */
        SQL_SERVER_AUTHENTICATION("SQL Server Authentication")
        {
            @Override
            String generateURL(String server, String domain, String applicationName, String urlSuffix)
            {
                return "jdbc:sqlserver://" + server
                       + (!isBlank(applicationName) ? (";applicationName=" + applicationName)
                               : "")
                       + (!isBlank(urlSuffix) ? (";" + urlSuffix)
                               : "");
            }
        };

        private final String title;

        AuthenticationType(String title)
        {
            this.title = title;
        }

        /** Generate connection string */
        abstract String generateURL(String server, String domain, String applicationName, String urlSuffix);

        @Override
        public String toString()
        {
            return title;
        }
    }
}