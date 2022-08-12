package com.queryeer.provider.jdbc.dialect.sqlserver;

import static java.util.Collections.emptyList;

import java.util.List;

import com.queryeer.jdbc.IJdbcConnection;
import com.queryeer.jdbc.JdbcType;
import com.queryeer.provider.jdbc.dialect.ITreeDialect;
import com.queryeer.provider.jdbc.dialect.ITreeDialectNode;

/** Jdbc dialect for Sqlserver */
// @Inject
class SqlServerDialect implements ITreeDialect
{
    @Override
    public JdbcType getType()
    {
        return JdbcType.SQLSERVER;
    }

    @Override
    public List<ITreeDialectNode> getTreeNodes(IJdbcConnection connection)
    {
        return emptyList();
    }
}
