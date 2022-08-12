package com.queryeer.provider.jdbc.dialect;

import com.queryeer.jdbc.JdbcType;

/** Definition of a tree dialect factory */
public interface ITreeDialectFactory
{
    /** Get dialect tree for provided type */
    ITreeDialect getTreeDialect(JdbcType type);
}
