package com.queryeer.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.queryeer.api.extensions.output.QueryeerOutputWriter;

import se.kuseman.payloadbuilder.api.execution.Decimal;
import se.kuseman.payloadbuilder.api.execution.EpochDateTime;
import se.kuseman.payloadbuilder.api.execution.EpochDateTimeOffset;
import se.kuseman.payloadbuilder.api.execution.UTF8String;

/**
 * OutputWriter that produces GitHub-flavored Markdown tables. Each initResult call starts a new table. Multiple result sets are separated by a blank line.
 *
 * <p>
 * Column names may be absent in initResult (engine-specific). When columns are empty, headers are collected from writeFieldName calls on the first row and flushed before that row is written (mirrors
 * the delayed-header pattern used by CsvOutputWriter).
 * </p>
 */
class MarkdownTableOutputWriter implements QueryeerOutputWriter
{
    private final StringBuilder output = new StringBuilder();
    private String[] currentColumns;
    private List<String> currentRow;
    private boolean hasAnyResult = false;
    private int nestingDepth = 0;

    /** Non-null while collecting headers from writeFieldName on the first row of a delayed-header result set. */
    private List<String> delayedHeaders;

    @Override
    public void initResult(String[] columns, Map<String, Object> resultMetaData)
    {
        if (hasAnyResult)
        {
            output.append("\n");
        }
        hasAnyResult = true;
        currentColumns = columns;
        delayedHeaders = null;

        if (columns.length > 0)
        {
            writeHeader(columns);
        }
        else
        {
            // Columns unknown — collect from writeFieldName on the first row
            delayedHeaders = new ArrayList<>();
        }
    }

    @Override
    public void endResult()
    {
    }

    @Override
    public void startRow()
    {
        currentRow = new ArrayList<>(currentColumns != null ? currentColumns.length
                : 4);
        nestingDepth = -1;
    }

    @Override
    public void endRow()
    {
        if (currentRow != null)
        {
            if (delayedHeaders != null)
            {
                // First row of a delayed-header result set: flush header then this row
                writeHeader(delayedHeaders.toArray(new String[0]));
                delayedHeaders = null;
            }
            output.append("| ");
            for (int i = 0; i < currentRow.size(); i++)
            {
                String val = currentRow.get(i);
                output.append(val != null ? val.replace("|", "\\|")
                        : "");
                if (i < currentRow.size() - 1)
                {
                    output.append(" | ");
                }
            }
            output.append(" |\n");
        }
        currentRow = null;
    }

    @Override
    public void writeFieldName(String name)
    {
        // In delayed-header mode, collect top-level field names as column headers
        if (delayedHeaders != null
                && nestingDepth <= 0)
        {
            delayedHeaders.add(name);
        }
    }

    @Override
    public void writeValue(Object value)
    {
        if (nestingDepth <= 0
                && currentRow != null)
        {
            currentRow.add(value != null ? value.toString()
                    : null);
        }
    }

    @Override
    public void startObject()
    {
        nestingDepth++;
        // nestingDepth == 0 is the root-level row wrapper — transparent, values flow through
        if (nestingDepth > 0
                && currentRow != null)
        {
            currentRow.add("{...}");
        }
    }

    @Override
    public void endObject()
    {
        nestingDepth--;
    }

    @Override
    public void startArray()
    {
        nestingDepth++;
        // nestingDepth == 0 is the root-level row wrapper — transparent, values flow through
        if (nestingDepth > 0
                && currentRow != null)
        {
            currentRow.add("[...]");
        }
    }

    @Override
    public void endArray()
    {
        nestingDepth--;
    }

    @Override
    public void writeInt(int value)
    {
        writeValue(value);
    }

    @Override
    public void writeLong(long value)
    {
        writeValue(value);
    }

    @Override
    public void writeFloat(float value)
    {
        writeValue(value);
    }

    @Override
    public void writeDouble(double value)
    {
        writeValue(value);
    }

    @Override
    public void writeBool(boolean value)
    {
        writeValue(value);
    }

    @Override
    public void writeString(UTF8String string)
    {
        writeValue(string != null ? string.toString()
                : null);
    }

    @Override
    public void writeNull()
    {
        writeValue(null);
    }

    @Override
    public void writeDecimal(Decimal decimal)
    {
        writeValue(decimal);
    }

    @Override
    public void writeDateTime(EpochDateTime datetime)
    {
        writeValue(datetime);
    }

    @Override
    public void writeDateTimeOffset(EpochDateTimeOffset datetimeOffset)
    {
        writeValue(datetimeOffset);
    }

    @Override
    public void flush()
    {
    }

    @Override
    public void close()
    {
    }

    /** Returns the accumulated markdown table(s). */
    String getResult()
    {
        return output.toString();
    }

    private void writeHeader(String[] columns)
    {
        output.append("| ");
        for (int i = 0; i < columns.length; i++)
        {
            output.append(columns[i]);
            if (i < columns.length - 1)
            {
                output.append(" | ");
            }
        }
        output.append(" |\n");
        output.append("| ");
        for (int i = 0; i < columns.length; i++)
        {
            output.append("---");
            if (i < columns.length - 1)
            {
                output.append(" | ");
            }
        }
        output.append(" |\n");
    }
}
