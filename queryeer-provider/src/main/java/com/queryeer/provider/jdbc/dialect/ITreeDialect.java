package com.queryeer.provider.jdbc.dialect;

import java.util.List;

import com.queryeer.jdbc.IJdbcConnection;

/**
 * Definition of a jdbc tree dialect. Utilized to build the connections tree UI with components specific to a jdbc flavor/dialect
 */
public interface ITreeDialect extends IDialect
{
    /** Get tree nodes for this dialect */
    List<ITreeDialectNode> getTreeNodes(IJdbcConnection connection);
}
