package se.kuseman.payloadbuilder.catalog.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.queryeer.api.extensions.engine.IQueryEngine.IState.MetaParameter;

import se.kuseman.payloadbuilder.catalog.jdbc.dialect.JdbcDialect;

/** Definition of the state of a connection (query tab) */
public interface IConnectionState
{
    /** Get selected database */
    String getDatabase();

    /** Return the jdbc dialect instance for this state */
    JdbcDialect getJdbcDialect();

    /** Get connection in the state */
    JdbcConnection getJdbcConnection();

    /** Create a new {@link Connection} from this state. */
    Connection createConnection() throws SQLException;

    /** Returns true if the query should include a query plan. */
    boolean isIncludeQueryPlan();

    /** Returns true if the query should include an estimated query plan. */
    boolean isEstimateQueryPlan();

    /** Get meta paramrters. */
    static List<MetaParameter> getMetaParameters(String url, String database)
    {
        //@formatter:off
        return List.of(
                new MetaParameter("url", url, "JDBC Url for the current query file"),
                new MetaParameter("database", database, "Name of the database of the current query file.")
                );
        //@formatter:on
    }
}
