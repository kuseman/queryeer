package com.queryeer.provider.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/** JDBC utils */
class ResultSetUtils
{
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    static Object transform(ResultSet rs, int ordinal, int type) throws SQLException
    {
        // Show hex value for binary data
        if (type == Types.VARBINARY)
        {
            return bytesToHex(rs.getBytes(ordinal));
        }

        return rs.getObject(ordinal);
    }

    private static String bytesToHex(byte[] bytes)
    {
        char[] hexChars = new char[2 + (bytes.length * 2)];
        hexChars[0] = '0';
        hexChars[1] = 'x';
        for (int j = 0; j < bytes.length; j++)
        {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2 + 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 3] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
}
