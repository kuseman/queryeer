package se.kuseman.payloadbuilder.catalog.jdbc.dialect;

import static java.util.Collections.emptyList;

import java.util.List;

import com.queryeer.api.component.QueryeerTree.RegularNode;

import se.kuseman.payloadbuilder.catalog.jdbc.JdbcConnection;
import se.kuseman.payloadbuilder.catalog.jdbc.SqlConnectionSupplier;

/** Definition of a tree node supplier for a dialect. Used to populate nodes in JDBC navigation tree */
public interface TreeNodeSupplier
{
    /** Return a list of tree nodes that acts as meta data for a connection */
    default List<RegularNode> getMetaDataNodes(JdbcConnection connection, SqlConnectionSupplier connectionSupplier)
    {
        return emptyList();
    }

    /** Return a list of tree nodes that acts as meta data for a database (or schema) */
    default List<RegularNode> getDatabaseMetaDataNodes(JdbcConnection connection, String database, SqlConnectionSupplier connectionSupplier)
    {
        return emptyList();
    }
}
