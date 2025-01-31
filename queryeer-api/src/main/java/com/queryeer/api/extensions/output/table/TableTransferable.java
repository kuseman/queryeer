package com.queryeer.api.extensions.output.table;

import static java.util.Objects.requireNonNull;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/** Transfer data used when data is copied from table output component. */
public class TableTransferable implements Transferable
{
    public static String SQL_IN = "SQL IN";
    public static String SQL_IN_NEW_LINE = "SQL IN (New Line)";
    public static String MIME_TYPE_SQL_IN = "queryeer/sql-in";
    public static String MIME_TYPE_SQL_IN_NEW_LINE = "queryeer/sql-in-new-line";
    public static final String JAVA_SERIALIZED_OBJECT = "application/x-java-serialized-object";
    public static final String PLAIN_TEXT = "text/plain";

    private static final DataFlavor[] FLAVORS;

    static
    {
        // CSOFF
        FLAVORS = new DataFlavor[9];
        try
        {
            FLAVORS[0] = new DataFlavor("text/html;class=java.lang.String", "Html");
            FLAVORS[1] = new DataFlavor("text/html;class=java.io.Reader");
            FLAVORS[2] = new DataFlavor("text/html;charset=unicode;class=java.io.InputStream");

            FLAVORS[3] = new DataFlavor("text/plain;class=java.lang.String", "Plain Text");
            FLAVORS[4] = new DataFlavor("text/plain;class=java.io.Reader");
            FLAVORS[5] = new DataFlavor("text/plain;charset=unicode;class=java.io.InputStream");

            FLAVORS[6] = new DataFlavor(MIME_TYPE_SQL_IN + ";class=java.lang.String", SQL_IN);
            FLAVORS[7] = new DataFlavor(MIME_TYPE_SQL_IN_NEW_LINE + ";class=java.lang.String", SQL_IN_NEW_LINE);
            FLAVORS[8] = new DataFlavor("queryeer/sql-union-all;class=java.lang.String", "SQL UNION ALL");
            // CSON
        }
        catch (Exception e)
        {
            System.err.println("Error initalizing data flavours for JTable TransferHandler. " + e);
        }
    }

    private final String[] headerNames;
    private final List<Object[]> rowsValues;

    public TableTransferable(String[] headerNames, List<Object[]> rowsValues)
    {
        this.headerNames = requireNonNull(headerNames);
        this.rowsValues = requireNonNull(rowsValues);
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor)
    {
        for (int i = 0; i < FLAVORS.length; i++)
        {
            if (FLAVORS[i].equals(flavor))
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors()
    {
        return FLAVORS;
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException
    {
        String mimeType = flavor.getMimeType();
        if (mimeType.contains("html"))
        {
            return toHtml();
        }
        else if (mimeType.contains("sql-in-new-line"))
        {
            return toSqlIn(true);
        }
        else if (mimeType.contains("sql-in"))
        {
            return toSqlIn(false);
        }
        else if (mimeType.contains("sql-union-all"))
        {
            return toSqlUnionAll();
        }
        return toPlain();
    }

    /** Returns true if provided value is a IN eligable value. */
    public static boolean isSqlIn(String value)
    {
        return value.matches(".*(?:[ \\r\\n\\t].*)+");
    }

    /** Converts a value to a SQL in by spliting it by new lines. */
    public static String getSqlIn(String value, boolean newLines)
    {
        String[] values = value.split("[\n|\r]");
        TableTransferable transferable = new TableTransferable(new String[] { "col" }, Arrays.stream(values)
                .map(v -> new Object[] { v })
                .toList());
        return transferable.toSqlIn(newLines);
    }

    private String toSqlIn(boolean newLines)
    {
        int rows = rowsValues.size();
        StringBuilder sqlIn = new StringBuilder().append('(');
        if (newLines)
        {
            sqlIn.append(System.lineSeparator());
            sqlIn.append("  ");
        }
        for (int row = 0; row < rows; row++)
        {
            if (row > 0)
            {
                sqlIn.append(",");
                if (newLines)
                {
                    sqlIn.append(System.lineSeparator());
                    sqlIn.append("  ");
                }
            }

            Object[] values = rowsValues.get(row);

            // In uses first copied column only
            Object obj = values[0];
            String val = (obj == null) ? ""
                    : obj.toString();
            if (!(obj instanceof Number))
            {
                sqlIn.append("\'")
                        .append(String.valueOf(val)
                                .replace("'", "''"))
                        .append('\'');
            }
            else
            {
                sqlIn.append(val);
            }
        }
        if (newLines)
        {
            sqlIn.append(System.lineSeparator());
        }
        sqlIn.append(')');
        return sqlIn.toString();
    }

    private String toSqlUnionAll()
    {
        /* @formatter:off
         *
         * SELECT *
         * FROM
         * (
         *              SELECT 1,3
         *    UNION ALL SELECT 2,6
         * ) x (header1, header2)
         *
         * @formatter:on
         */
        StringBuilder buf = new StringBuilder("""
                SELECT *
                FROM
                (
                """);
        int rows = rowsValues.size();
        for (int row = 0; row < rows; row++)
        {
            if (row > 0)
            {
                buf.append(System.lineSeparator());
                buf.append("    UNION ALL SELECT ");
            }
            else
            {
                buf.append("              SELECT ");
            }
            Object[] values = rowsValues.get(row);

            boolean first = true;
            for (Object obj : values)
            {
                if (!first)
                {
                    buf.append(',');
                }

                String val = (obj == null) ? ""
                        : obj.toString();
                if (!(obj instanceof Number))
                {
                    buf.append("\'")
                            .append(String.valueOf(val)
                                    .replace("'", "''"))
                            .append('\'');
                }
                else
                {
                    buf.append(val);
                }
                first = false;
            }
        }

        buf.append(System.lineSeparator());
        buf.append(") x (");

        int cols = headerNames.length;
        for (int i = 0; i < cols; i++)
        {
            if (i > 0)
            {
                buf.append(", ");
            }
            buf.append('\"')
                    .append(headerNames[i])
                    .append('\"');
        }
        buf.append(')')
                .append(System.lineSeparator());

        return buf.toString();
    }

    private String toHtml()
    {
        int cols = headerNames.length;

        StringBuilder htmlBuf = new StringBuilder();
        htmlBuf.append("<html>\n<body>\n<table>\n");
        htmlBuf.append("<tr>\n");
        for (int col = 0; col < cols; col++)
        {
            String columnName = headerNames[col];
            htmlBuf.append("  <th>")
                    .append(columnName)
                    .append("</th>\n");
        }
        htmlBuf.append("</tr>\n");

        int rows = rowsValues.size();
        for (int row = 0; row < rows; row++)
        {
            Object[] values = rowsValues.get(row);
            for (int col = 0; col < cols; col++)
            {
                Object obj = values[col];
                String val = (obj == null) ? ""
                        : obj.toString();
                htmlBuf.append("  <td>" + val + "</td>\n");
            }
            htmlBuf.append("</tr>\n");
        }
        htmlBuf.append("</table>\n</body>\n</html>");
        return htmlBuf.toString();
    }

    private String toPlain()
    {
        int cols = headerNames.length;
        int rows = rowsValues.size();

        StringBuilder plainBuf = new StringBuilder();
        for (int row = 0; row < rows; row++)
        {
            Object[] values = rowsValues.get(row);
            for (int col = 0; col < cols; col++)
            {
                Object obj = values[col];
                String val = (obj == null) ? ""
                        : obj.toString();

                plainBuf.append(val);
                if (col < cols - 1)
                {
                    plainBuf.append('\t');
                }
            }
            if (row < rows - 1)
            {
                plainBuf.append(System.lineSeparator());
            }
        }
        return plainBuf.toString();
    }
}
