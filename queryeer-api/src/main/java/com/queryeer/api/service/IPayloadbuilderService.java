package com.queryeer.api.service;

import java.util.Map;
import java.util.function.Function;

import se.kuseman.payloadbuilder.api.catalog.Catalog;
import se.kuseman.payloadbuilder.api.execution.TupleVector;

/** Service that wraps Payloadbuilder, making clients to be able to run queries etc. */
public interface IPayloadbuilderService
{
    /**
     * Execute provided query.
     *
     * @param vectorConsumer Consumer for the vectors produced.
     * @param query Query to execute
     * @param catalogs Catalogs to register.
     */
    void queryRaw(Function<TupleVector, Boolean> vectorConsumer, String query, Map<String, Catalog> catalogs);
}
