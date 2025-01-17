package com.queryeer.api.event;

import static java.util.Objects.requireNonNull;

import com.queryeer.api.IQueryFile;

/** Event that can be published to perform a query to current opened {@link IQueryFile} */
public class ExecuteQueryEvent extends Event
{
    private final OutputType outputType;
    private final String newQueryName;
    private final Object context;

    public ExecuteQueryEvent(OutputType outputType, Object context)
    {
        this(outputType, null, context);
    }

    public ExecuteQueryEvent(OutputType outputType, String newQueryName, Object context)
    {
        this.outputType = requireNonNull(outputType, "outputType");
        this.newQueryName = newQueryName;
        this.context = requireNonNull(context, "context");
    }

    public OutputType getOutputType()
    {
        return outputType;
    }

    public String getNewQueryName()
    {
        return newQueryName;
    }

    public Object getContext()
    {
        return context;
    }

    /** Output of query */
    public enum OutputType
    {
        /** Output result to table */
        TABLE(true),
        /** Output result to text. */
        TEXT(true),
        /** Output result to new query. NOTE! Will write result in plain/text format */
        NEW_QUERY(true),
        /** Output result to new query. And execute it. */
        NEW_QUERY_EXECUTE(true),
        /** Output to clipboard. NOTE! Will write result in plain/text format */
        CLIPBOARD(true),
        /** Outputs result to table but switches the active tab to query plan. */
        QUERY_PLAN(false);

        private final boolean interactive;

        private OutputType(boolean interactive)
        {
            this.interactive = interactive;
        }

        /** Returns true if this output type is of interactive type and choosable in configurations etc. */
        public boolean isInteractive()
        {
            return interactive;
        }
    }
}
