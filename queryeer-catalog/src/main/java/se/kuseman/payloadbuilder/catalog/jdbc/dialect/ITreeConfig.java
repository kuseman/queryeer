package se.kuseman.payloadbuilder.catalog.jdbc.dialect;

import com.queryeer.api.extensions.IExtension;

/** Config of tree. */
public interface ITreeConfig extends IExtension
{
    /** Should Sql server sys schema be hidden or shown. */
    boolean isHideSqlServerSysSchema();
}
