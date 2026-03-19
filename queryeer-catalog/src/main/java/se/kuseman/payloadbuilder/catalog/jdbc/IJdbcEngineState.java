package se.kuseman.payloadbuilder.catalog.jdbc;

import java.util.List;

import com.queryeer.api.extensions.engine.IQueryEngine;

/** State of a jdbc query file */
public interface IJdbcEngineState extends IQueryEngine.IState, IConnectionContext
{
    /** Returns true if the query should include a query plan. */
    default boolean isIncludeQueryPlan()
    {
        return false;
    }

    /** Returns true if the query should include an estimated query plan. */
    default boolean isEstimateQueryPlan()
    {
        return false;
    }

    /** Get meta parameters. */
    static List<MetaParameter> getMetaParameters(String url, String database)
    {
        //@formatter:off
        return List.of(
                new MetaParameter("url", url, "JDBC Url for the current query file"),
                new MetaParameter("database", database, "Name of the database of the current query file.")
                );
        //@formatter:on
    }

    /** Add a listener that is notified when the connection state changes. */
    void addChangeListener(Runnable r);

    /** Remove a previously registered connection state change listener. */
    void removeChangeListener(Runnable r);
}
