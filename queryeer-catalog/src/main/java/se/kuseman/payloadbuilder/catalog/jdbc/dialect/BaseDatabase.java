package se.kuseman.payloadbuilder.catalog.jdbc.dialect;

import static java.util.Collections.emptyList;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.queryeer.api.component.QueryeerTree.RegularNode;
import com.queryeer.api.editor.ITextEditorDocumentParser;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.ITemplateService;

import se.kuseman.payloadbuilder.catalog.jdbc.CatalogCrawlService;
import se.kuseman.payloadbuilder.catalog.jdbc.IConnectionState;
import se.kuseman.payloadbuilder.catalog.jdbc.Icons;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Catalog;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Column;
import se.kuseman.payloadbuilder.catalog.jdbc.model.TableSource;

/** Base database when database type is unkown */
class BaseDatabase implements JdbcDatabase
{
    private static final String[] TABLE_TYPES = new String[] { "TABLE", "VIEW", "SYNONYM" };
    private static final Logger LOGGER = LoggerFactory.getLogger(BaseDatabase.class);
    static final String NAME = "jdbc";
    private final TreeNodeSupplier treeNodeSupplier;
    private final IEventBus eventBus;
    private final QueryActionsConfigurable queryActionsConfigurable;
    private final CatalogCrawlService crawlService;
    private final ITemplateService templateService;

    BaseDatabase(Icons icons, CatalogCrawlService crawlService, IEventBus eventBus, QueryActionsConfigurable queryActionsConfigurable, ITemplateService templateService, ITreeConfig treeConfig)
    {
        this.treeNodeSupplier = new JdbcTreeNodeSupplier(this, icons, eventBus, queryActionsConfigurable, templateService, treeConfig);
        this.crawlService = crawlService;
        this.eventBus = eventBus;
        this.queryActionsConfigurable = queryActionsConfigurable;
        this.templateService = templateService;
    }

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public TreeNodeSupplier getTreeNodeSupplier()
    {
        return treeNodeSupplier;
    }

    @Override
    public ITextEditorDocumentParser getParser(IConnectionState connectionState)
    {
        return new PrestoDocumentParser(eventBus, queryActionsConfigurable, crawlService, connectionState, templateService);
    }

    @Override
    public Catalog getCatalog(IConnectionState connectionState, String database)
    {
        // Queries meta data through plain JDBC meta data
        Catalog catalog = null;
        boolean useSchemaAsDatabase = usesSchemaAsDatabase();
        try (Connection connection = connectionState.createConnection())
        {
            LOGGER.info("Fetching catalog metadata for: " + database);
            // CSOFF
            long time = System.currentTimeMillis();
            // CSON
            Map<TableKey, Pair<TableSource, List<Column>>> columnsByTableSource = new HashMap<>();

            try (ResultSet rs = connection.getMetaData()
                    .getTables(useSchemaAsDatabase ? null
                            : database,
                            useSchemaAsDatabase ? database
                                    : null,
                            null, TABLE_TYPES))
            {
                while (rs.next())
                {
                    String tableType = rs.getString("TABLE_TYPE");
                    String tableCat = rs.getString("TABLE_CAT");
                    String tableSchem = rs.getString("TABLE_SCHEM");
                    String tableName = rs.getString("TABLE_NAME");

                    TableSource.Type type = switch (tableType)
                    {
                        case "TABLE" -> TableSource.Type.TABLE;
                        case "VIEW" -> TableSource.Type.VIEW;
                        case "SYNONYM" -> TableSource.Type.SYNONYM;
                        default -> null;
                    };
                    // Unknown -> move on
                    if (type == null)
                    {
                        continue;
                    }

                    columnsByTableSource.put(new TableKey(tableCat, tableSchem, tableName), Pair.of(new TableSource(tableCat, tableSchem, tableName, type, emptyList()), new ArrayList<>()));
                }
            }

            try (ResultSet rs = connection.getMetaData()
                    .getColumns(useSchemaAsDatabase ? null
                            : database,
                            useSchemaAsDatabase ? database
                                    : null,
                            null, null))
            {
                while (rs.next())
                {
                    // 1.TABLE_CAT String => table catalog (may be null)
                    // 2.TABLE_SCHEM String => table schema (may be null)
                    // 3.TABLE_NAME String => table name
                    // 4.COLUMN_NAME String => column name
                    // 5.DATA_TYPE int => SQL type from java.sql.Types
                    // 6.TYPE_NAME String => Data source dependent type name,for a UDT the type name is fully qualified
                    // 7.COLUMN_SIZE int => column size.
                    // 8.BUFFER_LENGTH is not used.
                    // 9.DECIMAL_DIGITS int => the number of fractional digits. Null is returned for data types whereDECIMAL_DIGITS is not applicable.
                    // 10.NUM_PREC_RADIX int => Radix (typically either 10 or 2)
                    // 11.NULLABLE int => is NULL allowed.
                    // ◦ columnNoNulls - might not allow NULL values
                    // ◦ columnNullable - definitely allows NULL values
                    // ◦ columnNullableUnknown - nullability unknown
                    //
                    // 12.REMARKS String => comment describing column (may be null)
                    // 13.COLUMN_DEF String => default value for the column, which should be interpreted as a string when the value is enclosed in single quotes (may be null)
                    // 14.SQL_DATA_TYPE int => unused
                    // 15.SQL_DATETIME_SUB int => unused
                    // 16.CHAR_OCTET_LENGTH int => for char types themaximum number of bytes in the column
                    // 17.ORDINAL_POSITION int => index of column in table(starting at 1)
                    // 18.IS_NULLABLE String => ISO rules are used to determine the nullability for a column. ◦ YES --- if the column can include NULLs
                    // ◦ NO --- if the column cannot include NULLs
                    // ◦ empty string --- if the nullability for thecolumn is unknown
                    //
                    // 19.SCOPE_CATALOG String => catalog of table that is the scopeof a reference attribute (null if DATA_TYPE isn't REF)
                    // 20.SCOPE_SCHEMA String => schema of table that is the scopeof a reference attribute (null if the DATA_TYPE isn't REF)
                    // 21.SCOPE_TABLE String => table name that this the scopeof a reference attribute (null if the DATA_TYPE isn't REF)
                    // 22.SOURCE_DATA_TYPE short => source type of a distinct type or user-generatedRef type, SQL type from java.sql.Types (null if DATA_TYPEisn't DISTINCT or user-generated REF)
                    // 23.IS_AUTOINCREMENT String => Indicates whether this column is auto incremented ◦ YES --- if the column is auto incremented
                    // ◦ NO --- if the column is not auto incremented
                    // ◦ empty string --- if it cannot be determined whether the column is auto incremented
                    //
                    // 24.IS_GENERATEDCOLUMN String => Indicates whether this is a generated column ◦ YES --- if this a generated column
                    // ◦ NO --- if this not a generated column
                    // ◦ empty string --- if it cannot be determined whether this is a generated column

                    String tableCat = rs.getString("TABLE_CAT");
                    String tableSchem = rs.getString("TABLE_SCHEM");
                    String tableName = rs.getString("TABLE_NAME");
                    String columnName = rs.getString("COLUMN_NAME");
                    String typeName = rs.getString("TYPE_NAME");
                    boolean nullable = rs.getBoolean("NULLABLE");
                    int columnSize = rs.getInt("COLUMN_SIZE");
                    int decimalDigits = rs.getInt("DECIMAL_DIGITS");
                    int scale = rs.getInt("NUM_PREC_RADIX");

                    Pair<TableSource, List<Column>> pair = columnsByTableSource.get(new TableKey(tableCat, tableSchem, tableName));
                    if (pair != null)
                    {
                        Column column = new Column(columnName, typeName, columnSize, decimalDigits, scale, nullable, null);
                        pair.getValue()
                                .add(column);
                    }
                }
            }

            List<TableSource> tableSources = columnsByTableSource.values()
                    .stream()
                    .map(p -> new TableSource(p.getKey()
                            .getCatalog(),
                            p.getKey()
                                    .getSchema(),
                            p.getKey()
                                    .getName(),
                            p.getKey()
                                    .getType(),
                            p.getValue()))
                    .toList();

            catalog = new Catalog(database, tableSources, emptyList(), emptyList(), emptyList(), emptyList());

            LOGGER.info("Fetched metadata for " + database + " in: " + DurationFormatUtils.formatDurationHMS(System.currentTimeMillis() - time));
        }
        catch (SQLException e)
        {
            LOGGER.error("Error fetching catalog meta data", e);
        }

        return catalog;
    }

    /** Result set consumer */
    interface ResultSetConsumer
    {
        void accept(ResultSet rs) throws SQLException;
    }

    /** ChildNodesSupplier */
    interface ChildNodesSupplier
    {
        List<RegularNode> loadChildren() throws SQLException;
    }

    private record TableKey(String catalog, String schema, String name)
    {
    }
}
