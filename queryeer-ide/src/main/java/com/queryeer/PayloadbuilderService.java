package com.queryeer;

import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

import com.queryeer.api.service.IPayloadbuilderService;

import se.kuseman.payloadbuilder.api.catalog.Catalog;
import se.kuseman.payloadbuilder.api.catalog.Schema;
import se.kuseman.payloadbuilder.api.execution.TupleVector;
import se.kuseman.payloadbuilder.core.CompiledQuery;
import se.kuseman.payloadbuilder.core.Payloadbuilder;
import se.kuseman.payloadbuilder.core.RawQueryResult;
import se.kuseman.payloadbuilder.core.RawQueryResult.ResultConsumer;
import se.kuseman.payloadbuilder.core.catalog.CatalogRegistry;
import se.kuseman.payloadbuilder.core.execution.QuerySession;

/** Default Implementation of {@link IPayloadbuilderService}. */
class PayloadbuilderService implements IPayloadbuilderService
{
    @Override
    public void queryRaw(Function<TupleVector, Boolean> vectorConsumer, String query, Map<String, Catalog> catalogs)
    {
        CatalogRegistry registry = new CatalogRegistry();
        for (Entry<String, Catalog> e : catalogs.entrySet())
        {
            registry.registerCatalog(e.getKey(), e.getValue());
        }
        CompiledQuery compile = Payloadbuilder.compile(registry, query);
        RawQueryResult queryResult = compile.executeRaw(new QuerySession(registry));

        while (queryResult.hasMoreResults())
        {
            queryResult.consumeResult(new ResultConsumer()
            {
                @Override
                public void schema(Schema schema)
                {
                }

                @Override
                public boolean consume(TupleVector vector)
                {
                    return vectorConsumer.apply(vector);
                }
            });
        }
    }
}
