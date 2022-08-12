package com.queryeer.provider.jdbc.dialect;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.queryeer.api.service.Inject;
import com.queryeer.api.utils.ExceptionUtils;
import com.queryeer.jdbc.IConnectionFactory;
import com.queryeer.jdbc.IJdbcConnection;
import com.queryeer.jdbc.JdbcType;
import com.queryeer.jdbc.Utils;
import com.queryeer.jdbc.Utils.CredentialsResult;

/** Generic jdbc dialect tree. Makes used of {@link DatabaseMetaData} to fetch nodes in tree */
@Inject
class JdbcTreeDialect implements ITreeDialect
{
    private final IConnectionFactory connectionFactory;

    JdbcTreeDialect(IConnectionFactory connectionFactory)
    {
        this.connectionFactory = requireNonNull(connectionFactory, "connectionFactory");
    }

    @Override
    public JdbcType getType()
    {
        return JdbcType.JDBC_URL;
    }

    @Override
    public List<ITreeDialectNode> getTreeNodes(IJdbcConnection connection)
    {
        return asList(new CatalogsNode(connection));
    }

    private class CatalogsNode extends AJdbcNode
    {
        CatalogsNode(IJdbcConnection connection)
        {
            super("Databases", connection);
        }

        @Override
        public List<ITreeDialectNode> children()
        {
            return getChildItems(con -> con.getMetaData()
                    .getCatalogs(), rs -> rs.getString(1)).stream()
                            .map(catalog -> new CatalogNode(catalog, connection))
                            .collect(toList());
        }
    }

    private class CatalogNode extends AJdbcNode
    {
        private String catalog;

        CatalogNode(String catalog, IJdbcConnection connection)
        {
            super(catalog, connection);
            this.catalog = catalog;
        }

        @Override
        public boolean isDatabase()
        {
            return true;
        }

        @Override
        public List<ITreeDialectNode> children()
        {
            // Functions
            return asList(new TablesNode(catalog, connection), new ProceduresNode(catalog, connection));
        }
    }

    private class ProceduresNode extends AJdbcNode
    {
        private String catalog;

        ProceduresNode(String catalog, IJdbcConnection connection)
        {
            super("Procedures", connection);
            this.catalog = catalog;
        }

        @Override
        public List<ITreeDialectNode> children()
        {
            return getChildItems(con -> con.getMetaData()
                    .getProcedures(catalog, null, null), rs -> rs.getString(3)).stream()
                            .map(procedure -> new ProcedureNode(catalog, procedure, connection))
                            .collect(toList());
        }
    }

    private class ProcedureNode extends AJdbcNode
    {
        private String catalog;
        private String procedure;

        ProcedureNode(String catalog, String procedure, IJdbcConnection connection)
        {
            super(procedure, connection);
            this.catalog = catalog;
            this.procedure = procedure;
        }

        @Override
        public List<ITreeDialectNode> children()
        {
            return getChildItems(con -> con.getMetaData()
                    .getProcedureColumns(catalog, null, procedure, null), this::getProcedureParamDef).stream()
                            .map(param -> new ITreeDialectNode()
                            {
                                @Override
                                public String title()
                                {
                                    return param;
                                }

                                @Override
                                public boolean isLeaf()
                                {
                                    return true;
                                }
                            })
                            .collect(toList());
        }

        private String getProcedureParamDef(ResultSet rs) throws SQLException
        {
            /*
             * @formatter:off
             * PROCEDURE_CAT String => procedure catalog (may be null)
             * 2.PROCEDURE_SCHEM String => procedure schema (may be null)
             * 3.PROCEDURE_NAME String => procedure name
             * 4.COLUMN_NAME String => column/parameter name
             * 5.COLUMN_TYPE Short => kind of column/parameter: ◦ procedureColumnUnknown - nobody knows
             * ◦ procedureColumnIn - IN parameter
             * ◦ procedureColumnInOut - INOUT parameter
             * ◦ procedureColumnOut - OUT parameter
             * ◦ procedureColumnReturn - procedure return value
             * ◦ procedureColumnResult - result column in ResultSet
             * 
             * 6.DATA_TYPE int => SQL type from java.sql.Types
             * 7.TYPE_NAME String => SQL type name, for a UDT type thetype name is fully qualified
             * 8.PRECISION int => precision
             * 9.LENGTH int => length in bytes of data
             * 10.SCALE short => scale - null is returned for data types whereSCALE is not applicable.
             * 11.RADIX short => radix
             * 12.NULLABLE short => can it contain NULL. ◦ procedureNoNulls - does not allow NULL values
             * ◦ procedureNullable - allows NULL values
             * ◦ procedureNullableUnknown - nullability unknown
             * 
             * 13.REMARKS String => comment describing parameter/column
             * 14.COLUMN_DEF String => default value for the column, which should be interpreted as a string when the value is enclosed in single quotes (may be null) ◦ The string NULL (not enclosed
             * in quotes) - if NULL was specified as the default value
             * ◦ TRUNCATE (not enclosed in quotes) - if the specified default value cannot be represented without truncation
             * ◦ NULL - if a default value was not specified
             * 
             * 15.SQL_DATA_TYPE int => reserved for future use
             * 16.SQL_DATETIME_SUB int => reserved for future use
             * 17.CHAR_OCTET_LENGTH int => the maximum length of binary and character based columns. For any other datatype the returned value is aNULL
             * 18.ORDINAL_POSITION int => the ordinal position, starting from 1, for the input and output parameters for a procedure. A value of 0 is returned if this row describes the procedure's
             * return value. For result set columns, it is the ordinal position of the column in the result set starting from 1. If there are multiple result sets, the column ordinal positions are
             * implementationdefined.
             * 19.IS_NULLABLE String => ISO rules are used to determine the nullability for a column. ◦ YES --- if the column can include NULLs
             * ◦ NO --- if the column cannot include NULLs
             * ◦ empty string --- if the nullability for thecolumn is unknown
             * 
             * 20.SPECIFIC_NAME String => the name which uniquely identifies this procedure within its schema.
             * @formatter:on
             */

            short scolumnType = rs.getShort(5);
            String columnType = scolumnType == DatabaseMetaData.procedureColumnInOut ? " InOut"
                    : scolumnType == DatabaseMetaData.procedureColumnOut ? " Out"
                            : "";
            String typeName = rs.getString(7);
            int length = rs.getInt(9);
            if (length > 0)
            {
                typeName += " (" + length + ")";
            }

            return String.format("%s %s%s", rs.getString(4), typeName, columnType);
        }
    }

    private class TablesNode extends AJdbcNode
    {
        private String catalog;

        TablesNode(String catalog, IJdbcConnection connection)
        {
            super("Tables", connection);
            this.catalog = catalog;
        }

        @Override
        public List<ITreeDialectNode> children()
        {
            return getChildItems(con -> con.getMetaData()
                    .getTables(catalog, null, null, null), rs -> rs.getString(3)).stream()
                            .map(table -> new TableNode(catalog, table, connection))
                            .collect(toList());
        }
    }

    private class TableNode extends AJdbcNode
    {
        private String catalog;
        private String table;

        TableNode(String catalog, String table, IJdbcConnection connection)
        {
            super(table, connection);
            this.catalog = catalog;
            this.table = table;
        }

        @Override
        public List<ITreeDialectNode> children()
        {
            return asList(new ColumnsNode(catalog, table, connection), new IndexesNode(catalog, table, connection));
        }
    }

    private class ColumnsNode extends AJdbcNode
    {
        private String catalog;
        private String table;

        ColumnsNode(String catalog, String table, IJdbcConnection connection)
        {
            super("Columns", connection);
            this.catalog = catalog;
            this.table = table;
        }

        @Override
        public List<ITreeDialectNode> children()
        {
            return getChildItems(con -> con.getMetaData()
                    .getColumns(catalog, null, table, null), this::getColumnDef).stream()
                            .map(column -> new ITreeDialectNode()
                            {
                                @Override
                                public String title()
                                {
                                    return column;
                                }

                                @Override
                                public boolean isLeaf()
                                {
                                    return true;
                                }
                            })
                            .collect(toList());
        }

        private String getColumnDef(ResultSet rs) throws SQLException
        {
            /*
             * @formatter:off
             * TABLE_CAT String => table catalog (may be null)
             * 2.TABLE_SCHEM String => table schema (may be null)
             * 3.TABLE_NAME String => table name
             * 4.COLUMN_NAME String => column name
             * 5.DATA_TYPE int => SQL type from java.sql.Types
             * 6.TYPE_NAME String => Data source dependent type name,for a UDT the type name is fully qualified
             * 7.COLUMN_SIZE int => column size.
             * 8.BUFFER_LENGTH is not used.
             * 9.DECIMAL_DIGITS int => the number of fractional digits. Null is returned for data types whereDECIMAL_DIGITS is not applicable.
             * 10.NUM_PREC_RADIX int => Radix (typically either 10 or 2)
             * 11.NULLABLE int => is NULL allowed. ◦ columnNoNulls - might not allow NULL values
             * ◦ columnNullable - definitely allows NULL values
             * ◦ columnNullableUnknown - nullability unknown
             * 
             * 12.REMARKS String => comment describing column (may be null)
             * 13.COLUMN_DEF String => default value for the column, which should be interpreted as a string when the value is enclosed in single quotes (may be null)
             * 14.SQL_DATA_TYPE int => unused
             * 15.SQL_DATETIME_SUB int => unused
             * 16.CHAR_OCTET_LENGTH int => for char types themaximum number of bytes in the column
             * 17.ORDINAL_POSITION int => index of column in table(starting at 1)
             * 18.IS_NULLABLE String => ISO rules are used to determine the nullability for a column. ◦ YES --- if the column can include NULLs
             * ◦ NO --- if the column cannot include NULLs
             * ◦ empty string --- if the nullability for thecolumn is unknown
             * 
             * 19.SCOPE_CATALOG String => catalog of table that is the scopeof a reference attribute (null if DATA_TYPE isn't REF)
             * 20.SCOPE_SCHEMA String => schema of table that is the scopeof a reference attribute (null if the DATA_TYPE isn't REF)
             * 21.SCOPE_TABLE String => table name that this the scopeof a reference attribute (null if the DATA_TYPE isn't REF)
             * 22.SOURCE_DATA_TYPE short => source type of a distinct type or user-generatedRef type, SQL type from java.sql.Types (null if DATA_TYPEisn't DISTINCT or user-generated REF)
             * 23.IS_AUTOINCREMENT String => Indicates whether this column is auto incremented ◦ YES --- if the column is auto incremented
             * ◦ NO --- if the column is not auto incremented
             * ◦ empty string --- if it cannot be determined whether the column is auto incremented
             * 
             * 24.IS_GENERATEDCOLUMN String => Indicates whether this is a generated column ◦ YES --- if this a generated column
             * ◦ NO --- if this not a generated column
             * ◦ empty string --- if it cannot be determined whether this is a generated column
             * @formatter:on
             */

            int columnSize = rs.getInt(7);
            String type = rs.getString(6) + (columnSize > 0 ? (" (" + columnSize + ")")
                    : "");

            return String.format("%s (%s%s%s)", rs.getString(4), "", type, rs.getInt(11) == DatabaseMetaData.columnNullable ? ", NULL"
                    : "");
        }
    }

    private class IndexesNode extends AJdbcNode
    {
        private String table;
        private String catalog;

        IndexesNode(String catalog, String table, IJdbcConnection connection)
        {
            super("Indexes", connection);
            this.catalog = catalog;
            this.table = table;
        }

        @Override
        public List<ITreeDialectNode> children()
        {
            return getChildItems(con -> con.getMetaData()
                    .getIndexInfo(catalog, null, table, false, true), this::getIndexDef).stream()
                            .filter(Objects::nonNull)
                            .map(index -> new ITreeDialectNode()
                            {
                                @Override
                                public String title()
                                {
                                    return index;
                                }

                                @Override
                                public boolean isLeaf()
                                {
                                    return true;
                                }
                            })
                            .collect(toList());
        }

        private String getIndexDef(ResultSet rs) throws SQLException
        {
            /*
             * @formatter:off
             * TABLE_CAT String => table catalog (may be null)
             * 2.TABLE_SCHEM String => table schema (may be null)
             * 3.TABLE_NAME String => table name
             * 4.NON_UNIQUE boolean => Can index values be non-unique.false when TYPE is tableIndexStatistic
             * 5.INDEX_QUALIFIER String => index catalog (may be null); null when TYPE is tableIndexStatistic
             * 6.INDEX_NAME String => index name; null when TYPE istableIndexStatistic
             * 7.TYPE short => index type: ◦ tableIndexStatistic - this identifies table statistics that arereturned in conjuction with a table's index descriptions
             * ◦ tableIndexClustered - this is a clustered index
             * ◦ tableIndexHashed - this is a hashed index
             * ◦ tableIndexOther - this is some other style of index
             * 
             * 8.ORDINAL_POSITION short => column sequence numberwithin index; zero when TYPE is tableIndexStatistic
             * 9.COLUMN_NAME String => column name; null when TYPE istableIndexStatistic
             * 10.ASC_OR_DESC String => column sort sequence, "A" => ascending,"D" => descending, may be null if sort sequence is not supported; null when TYPE is tableIndexStatistic
             * 11.CARDINALITY long => When TYPE is tableIndexStatistic, thenthis is the number of rows in the table; otherwise, it is thenumber of unique values in the index.
             * 12.PAGES long => When TYPE is tableIndexStatisic thenthis is the number of pages used for the table, otherwise itis the number of pages used for the current index.
             * 13.FILTER_CONDITION String => Filter condition, if any.(may be null)
             * @formatter:on
             * 
             */

            // Don't show statistics in index view
            int type = rs.getShort(7);
            if (type == DatabaseMetaData.tableIndexStatistic)
            {
                return null;
            }

            return String.format("%s%s", rs.getString(6), type == DatabaseMetaData.tableIndexClustered ? " (Clustered)"
                    : "");
        }
    }

    private abstract class AJdbcNode implements ITreeDialectNode
    {
        protected IJdbcConnection connection;
        private final String title;

        AJdbcNode(String title, IJdbcConnection connection)
        {
            this.connection = connection;
            this.title = title;
        }

        @Override
        public String title()
        {
            return title;
        }

        protected List<String> getChildItems(ResultSetSupplier resultSetSupplier, ResultSetMapper mapper)
        {
            CredentialsResult credentialsResult = Utils.ensureCredentials(connection);
            if (credentialsResult == CredentialsResult.CANCELLED)
            {
                return emptyList();
            }

            try (Connection con = connectionFactory.getConnection(connection); ResultSet rs = resultSetSupplier.get(con))
            {
                List<String> items = new ArrayList<>();
                while (rs.next())
                {
                    items.add(mapper.map(rs));
                }
                return items;
            }
            catch (Exception e)
            {
                // Clear password upon exception if they were provided on this call
                if (credentialsResult == CredentialsResult.CREDENTIALS_PROVIDED)
                {
                    connection.setPassword(null);
                }
                ExceptionUtils.showExceptionMessage(null, e);
                return emptyList();
            }
        }
    }

    @FunctionalInterface
    private interface ResultSetSupplier
    {
        ResultSet get(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    private interface ResultSetMapper
    {
        String map(ResultSet resultSet) throws SQLException;
    }
}
