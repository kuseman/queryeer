package com.queryeer.api.extensions.output;

import static java.util.Collections.emptyMap;

import java.util.Map;

import se.kuseman.payloadbuilder.api.OutputWriter;

/**
 * An extension of PLB's {@link OutputWriter} that has some extensions to make queryeer more rich. Various {@link IOutputComponent}'s has support for this writer.
 */
public interface QueryeerOutputWriter extends OutputWriter
{
    /** Timestamp metadata key. If not present in metadata it will automatically be added by queryeer. */
    public static final String METADATA_TIMESTAMP = "Timestamp";

    /** Init result with columns and meta data about current result. Ie. can be JDBC specifics like current database/cconnection or similar. */
    void initResult(String[] columns, Map<String, Object> resultMetaData);

    @Override
    default void initResult(String[] columns)
    {
        initResult(columns, emptyMap());
    }
}
