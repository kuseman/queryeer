package se.kuseman.payloadbuilder.catalog.es;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.List;

import com.queryeer.api.extensions.payloadbuilder.ICompletionProvider;

import se.kuseman.payloadbuilder.api.QualifiedName;
import se.kuseman.payloadbuilder.api.execution.IQuerySession;
import se.kuseman.payloadbuilder.catalog.es.ESConnectionsModel.Connection;

/** Completion provider for Elastic */
class ESCompletionProvider implements ICompletionProvider
{
    private final ESConnectionsModel model;

    ESCompletionProvider(ESConnectionsModel model)
    {
        this.model = model;
    }

    @Override
    public Object getTableMetaCacheKey(IQuerySession session, String catalogAlias)
    {
        String endpoint = session.getCatalogProperty(catalogAlias, ESCatalog.ENDPOINT_KEY)
                .valueAsString(0);
        String index = session.getCatalogProperty(catalogAlias, ESCatalog.INDEX_KEY)
                .valueAsString(0);
        return new TableCacheKey(endpoint, index);
    }

    @Override
    public String getDescription(IQuerySession session, String catalogAlias)
    {
        String endpoint = session.getCatalogProperty(catalogAlias, ESCatalog.ENDPOINT_KEY)
                .valueAsString(0);
        String index = session.getCatalogProperty(catalogAlias, ESCatalog.INDEX_KEY)
                .valueAsString(0);
        return endpoint + "#" + index;
    }

    @Override
    public boolean enabled(IQuerySession session, String catalogAlias)
    {
        Connection connection = model.findConnection(session, catalogAlias);
        if (connection != null
                && !connection.hasCredentials())
        {
            return false;
        }

        String endpoint = session.getCatalogProperty(catalogAlias, ESCatalog.ENDPOINT_KEY)
                .valueAsString(0);
        String index = session.getCatalogProperty(catalogAlias, ESCatalog.INDEX_KEY)
                .valueAsString(0);
        return !isBlank(endpoint)
                && !isBlank(index);
    }

    @Override
    public List<TableMeta> getTableCompletionMeta(IQuerySession querySession, String catalogAlias)
    {
        String endpoint = querySession.getCatalogProperty(catalogAlias, ESCatalog.ENDPOINT_KEY)
                .valueAsString(0);
        String index = querySession.getCatalogProperty(catalogAlias, ESCatalog.INDEX_KEY)
                .valueAsString(0);

        ElasticsearchMeta esMeta = ElasticsearchMetaUtils.getMeta(querySession, catalogAlias, endpoint, index);
        return esMeta.getMappedTypes()
                .entrySet()
                .stream()
                .map(e ->
                {
                    QualifiedName table = QualifiedName.of(e.getKey());
                    List<ColumnMeta> columns = e.getValue().properties.values()
                            .stream()
                            .map(t ->
                            {

                                String description = """
                                        <html>
                                        <h3>%s</h3>
                                        <ul>
                                        <li>Elastic type: <strong>%s</strong></li>
                                        </ul>
                                        """.formatted(t.name, t.type);
                                if (t.name.getParts()
                                        .size() > 1)
                                {
                                    description += """
                                            <p>NOTE! The mapping for this field has an object structure but returned value might
                                            end up in a composite map key from elasticsearch and payloadbuilder result might yield null.
                                            If so then change to a quoted column reference <strong>"%s"</strong>
                                            </p>
                                             """.formatted(t.name);
                                }

                                return new ColumnMeta(t.name, description, null);
                            })
                            .collect(toList());
                    return new TableMeta(table, null, null, columns);
                })
                .collect(toList());
    }

    record TableCacheKey(String endpoint, String index)
    {
    }
}
