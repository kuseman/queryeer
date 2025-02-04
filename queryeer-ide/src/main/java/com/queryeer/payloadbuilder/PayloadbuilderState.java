package com.queryeer.payloadbuilder;

import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import com.queryeer.api.extensions.engine.IQueryEngine;
import com.queryeer.api.extensions.payloadbuilder.IPayloadbuilderState;
import com.queryeer.payloadbuilder.CatalogsConfigurable.QueryeerCatalog;
import com.queryeer.payloadbuilder.VariablesConfigurable.Environment;

import se.kuseman.payloadbuilder.api.catalog.Catalog;
import se.kuseman.payloadbuilder.core.execution.QuerySession;

/** State for payloadbuilder */
class PayloadbuilderState implements IPayloadbuilderState, BooleanSupplier
{
    private final IQueryEngine queryEngine;
    private final CatalogsConfigurable catalogsConfigurable;
    private final QuerySession querySession;
    volatile boolean abort;

    /** Variable environment that was used for execution. */
    Environment environment;

    PayloadbuilderState(IQueryEngine queryEngine, CatalogsConfigurable catalogsConfigurable, QuerySession querySession)
    {
        this.catalogsConfigurable = catalogsConfigurable;
        this.queryEngine = requireNonNull(queryEngine, "queryEngine");
        this.querySession = requireNonNull(querySession, "querySession");
    }

    void abort()
    {
        querySession.fireAbortQueryListeners();
        abort = true;
    }

    @Override
    public QuerySession getQuerySession()
    {
        return querySession;
    }

    @Override
    public void close() throws IOException
    {
    }

    @Override
    public IQueryEngine getQueryEngine()
    {
        return queryEngine;
    }

    @Override
    public boolean getAsBoolean()
    {
        return abort;
    }

    @Override
    public List<MetaParameter> getMetaParameters(boolean testData)
    {
        /*
         * @formatter:off
         * We expose all the registered catalogs and their properties on this format:
         * {
         *   "catalogs": [
         *     {
         *       "alias": "alias",
         *       "name": "catalogname",
         *       "default": true,
         *       "properties": {
         *         "prop1": 123,
         *         "prop2": "abc"
         *       }
         *     }
         *   ]
         * }
         * @formatter:on
         */

        List<Map<String, Object>> catalogsParam = new ArrayList<>();

        for (QueryeerCatalog qc : catalogsConfigurable.getCatalogs())
        {
            if (qc.isDisabled())
            {
                continue;
            }

            Catalog catalog = qc.getCatalogExtension()
                    .getCatalog();

            List<MetaParameter> catalogMetaParameters = qc.getCatalogExtension()
                    .getMetaParameters(querySession, testData);

            //@formatter:off
            catalogsParam.add(Map.<String, Object>of(
                    "alias", qc.getAlias(),
                    "name", catalog.getName(),
                    "default", querySession.getDefaultCatalog() == catalog,
                    "properties", catalogMetaParameters
                            .stream()
                            .collect(LinkedHashMap::new, (m, v) -> m.put(v.name(), v.value()), LinkedHashMap::putAll)
                            ));
            //@formatter:on
        }

        return singletonList(new MetaParameter("catalogs", catalogsParam, "List with catalogs with sub properties: <b>alias, name, default, properties</b>"));
    }
}