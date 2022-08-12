package com.queryeer.provider.jdbc.dialect;

import com.queryeer.jdbc.JdbcType;

/** Base definition of a jdbc dialect */
public interface IDialect
{
    /** Get the type of this dialect */
    JdbcType getType();

}
