package com.queryeer.jdbc;

import static java.util.Objects.requireNonNull;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractListModel;

import com.queryeer.api.utils.ExceptionUtils;
import com.queryeer.jdbc.Utils.CredentialsResult;

/** List model for databases for a connection */
class JdbcDatabaseListModel extends AbstractListModel<IJdbcDatabaseListModel.Database> implements IJdbcDatabaseListModel
{
    private final IJdbcConnection connection;
    private final List<IJdbcDatabaseListModel.Database> databases = new ArrayList<>();
    private IConnectionFactory connectionFactory;

    JdbcDatabaseListModel(IConnectionFactory connectionFactory, IJdbcConnection connection)
    {
        this.connectionFactory = requireNonNull(connectionFactory, "connectionFactory");
        this.connection = requireNonNull(connection, "connection");
    }

    @Override
    public int getSize()
    {
        return databases.size();
    }

    @Override
    public Database getElementAt(int index)
    {
        return databases.get(index);
    }

    @Override
    public void reload()
    {
        CredentialsResult credentialsResult = Utils.ensureCredentials(connection);
        if (credentialsResult == CredentialsResult.CANCELLED)
        {
            return;
        }

        databases.clear();
        try (Connection con = connectionFactory.getConnection(connection);
                ResultSet rs = con.getMetaData()
                        .getCatalogs())
        {
            while (rs.next())
            {
                final String database = rs.getString(1);
                databases.add(new Database()
                {
                    @Override
                    public String getName()
                    {
                        return database;
                    }
                });
            }
            fireContentsChanged(this, 0, getSize() - 1);
        }
        catch (Exception e)
        {
            // Clear password upon exception if they were provided on this call
            if (credentialsResult == CredentialsResult.CREDENTIALS_PROVIDED)
            {
                connection.setPassword(null);
            }
            ExceptionUtils.showExceptionMessage(null, e);
        }
    }
}
