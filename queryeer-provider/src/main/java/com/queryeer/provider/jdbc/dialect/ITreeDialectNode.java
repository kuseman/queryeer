package com.queryeer.provider.jdbc.dialect;

import static java.util.Collections.emptyList;

import java.util.List;

/**
 * Definition of a tree node used by jdbc query providers tree component to build a tree with domain specific components for this dialect
 */
public interface ITreeDialectNode
{
    /** Title of node */
    String title();

    /**
     * Returns true if this node is a database. Used by Queryeer to perfom generic things related to a database node
     */
    default boolean isDatabase()
    {
        return false;
    }

    /** Returns true if this node is a leaf. Ie. there are no children */
    default boolean isLeaf()
    {
        return false;
    }

    /** Get children of this node */
    default List<ITreeDialectNode> children()
    {
        return emptyList();
    }

    // TODO: popup menu actions
}
