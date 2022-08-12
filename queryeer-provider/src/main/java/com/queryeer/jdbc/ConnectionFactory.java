package com.queryeer.jdbc;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.queryeer.api.service.Inject;
import com.zaxxer.hikari.HikariDataSource;

/** Connection factory */
@Inject
class ConnectionFactory implements IConnectionFactory
{
    private final Map<IJdbcConnection, HikariDataSource> datasources = new ConcurrentHashMap<>();

    @Override
    public Connection getConnection(IJdbcConnection connection) throws SQLException
    {
        return datasources.compute(connection, (k, v) ->
        {
            if (v == null)
            {
                return createDatasource(k);
            }

            // Settings have changed, close existing and create a new
            if (!Objects.equals(v.getJdbcUrl(), k.getJdbcURL())
                    || !Objects.equals(v.getUsername(), k.getUsername())
                    || !equal(v.getPassword(), k.getPassword()))
            {
                v.close();
                return createDatasource(k);
            }

            return v;
        })
                .getConnection();
    }

    private HikariDataSource createDatasource(IJdbcConnection connection)
    {
        HikariDataSource ds = new HikariDataSource();
        if (isNotBlank(connection.getJdbcDriverClassName()))
        {
            ds.setDriverClassName(connection.getJdbcDriverClassName());
        }
        ds.setRegisterMbeans(true);
        ds.setPoolName(connection.getName()
                .replace(':', '_'));
        ds.setJdbcUrl(connection.getJdbcURL());
        ds.setUsername(connection.getUsername());
        ds.setPassword(new String(connection.getPassword()));
        ds.setConnectionTestQuery(null);
        return ds;
    }

    private boolean equal(String str, char[] ar)
    {
        if (str.length() != ar.length)
        {
            return false;
        }

        int size = str.length();
        for (int i = 0; i < size; i++)
        {
            if (str.charAt(i) != ar[i])
            {
                return false;
            }
        }
        return true;
    }

}
