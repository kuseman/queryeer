package se.kuseman.payloadbuilder.catalog.jdbc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Parser that parses a query and extracts named params (:param1). This parser handles corner cases like params inside comments etc. Returns a query with JDBC params ? for all named variables
 */
class NamedParameterParser
{
    private NamedParameterParser()
    {
    }

    /** Result of a parsed query. Where all named params are replaced by '?' placeholder. */
    record ParsedQuery(String query, List<Object> values)
    {
    }

    /** Parses SQL against with provided params returning a SQL with replaced named params with JDBC ? placeholder */
    static ParsedQuery parse(String sql, Map<String, Object> params)
    {
        StringBuilder out = new StringBuilder();
        List<Object> values = new ArrayList<>();

        int len = sql.length();
        int i = 0;
        int blockCommentDepth = 0;
        String dollarTag = null;

        State state = State.NORMAL;

        while (i < len)
        {
            char c = sql.charAt(i);

            // CSOFF
            switch (state)
            // CSON
            {
                case NORMAL ->
                {
                    if (c == '\'')
                    {
                        state = State.IN_SINGLE_QUOTE;
                        out.append(c);
                        i++;
                    }
                    else if (c == '"')
                    {
                        state = State.IN_DOUBLE_QUOTE;
                        out.append(c);
                        i++;
                    }
                    else if (c == '`')
                    {
                        state = State.IN_BACKTICK;
                        out.append(c);
                        i++;
                    }
                    else if (c == '$')
                    {
                        // Check for PostgreSQL dollar-quoting: $$ or $tag$
                        int j = i + 1;
                        while (j < len
                                && (Character.isLetterOrDigit(sql.charAt(j))
                                        || sql.charAt(j) == '_'))
                        {
                            j++;
                        }
                        if (j < len
                                && sql.charAt(j) == '$')
                        {
                            dollarTag = sql.substring(i, j + 1);
                            out.append(dollarTag);
                            i = j + 1;
                            state = State.IN_DOLLAR_QUOTE;
                        }
                        else
                        {
                            out.append(c);
                            i++;
                        }
                    }
                    else if (c == '-'
                            && i + 1 < len
                            && sql.charAt(i + 1) == '-')
                    {
                        state = State.IN_LINE_COMMENT;
                        out.append(c);
                        i++;
                    }
                    else if (c == '/'
                            && i + 1 < len
                            && sql.charAt(i + 1) == '*')
                    {
                        state = State.IN_BLOCK_COMMENT;
                        blockCommentDepth = 1;
                        out.append(c);
                        i++;
                    }
                    else if (c == ':'
                            && isParamStart(sql, i))
                    {
                        // handle PostgreSQL ::
                        if (i > 0
                                && sql.charAt(i - 1) == ':')
                        {
                            out.append(':');
                            i++;
                            break;
                        }

                        int start = i + 1;
                        int j = start;

                        while (j < len
                                && isParamPart(sql.charAt(j)))
                        {
                            j++;
                        }

                        String name = sql.substring(start, j);

                        if (!params.containsKey(name))
                        {
                            throw new IllegalArgumentException("Missing param: " + name);
                        }

                        Object value = params.get(name);

                        if (value instanceof Collection<?> col)
                        {
                            if (col.isEmpty())
                            {
                                throw new IllegalArgumentException("Empty collection for: " + name);
                            }

                            StringJoiner sj = new StringJoiner(", ");
                            for (Object v : col)
                            {
                                sj.add("?");
                                values.add(v);
                            }
                            out.append(sj);
                        }
                        else
                        {
                            out.append("?");
                            values.add(value);
                        }

                        i = j;
                    }
                    else
                    {
                        out.append(c);
                        i++;
                    }
                }

                case IN_SINGLE_QUOTE ->
                {
                    if (c == '\\'
                            && i + 1 < len)
                    {
                        // Backslash escape (MySQL-style): consume both chars to stay inside the string
                        out.append(c);
                        out.append(sql.charAt(i + 1));
                        i += 2;
                    }
                    else
                    {
                        out.append(c);
                        if (c == '\''
                                && !(i + 1 < len
                                        && sql.charAt(i + 1) == '\''))
                        {
                            state = State.NORMAL;
                        }
                        else if (c == '\''
                                && i + 1 < len
                                && sql.charAt(i + 1) == '\'')
                        {
                            out.append(sql.charAt(i + 1));
                            i++;
                        }
                        i++;
                    }
                }

                case IN_DOUBLE_QUOTE ->
                {
                    out.append(c);
                    if (c == '"')
                    {
                        if (i + 1 < len
                                && sql.charAt(i + 1) == '"')
                        {
                            // Escaped double-quote ""
                            out.append(sql.charAt(i + 1));
                            i += 2;
                        }
                        else
                        {
                            state = State.NORMAL;
                            i++;
                        }
                    }
                    else
                    {
                        i++;
                    }
                }

                case IN_BACKTICK ->
                {
                    out.append(c);
                    if (c == '`')
                    {
                        state = State.NORMAL;
                    }
                    i++;
                }

                case IN_LINE_COMMENT ->
                {
                    out.append(c);
                    if (c == '\n')
                    {
                        state = State.NORMAL;
                    }
                    i++;
                }

                case IN_BLOCK_COMMENT ->
                {
                    out.append(c);
                    if (c == '/'
                            && i + 1 < len
                            && sql.charAt(i + 1) == '*')
                    {
                        // Nested block comment opening
                        out.append('*');
                        i += 2;
                        blockCommentDepth++;
                    }
                    else if (c == '*'
                            && i + 1 < len
                            && sql.charAt(i + 1) == '/')
                    {
                        out.append('/');
                        i += 2;
                        blockCommentDepth--;
                        if (blockCommentDepth == 0)
                        {
                            state = State.NORMAL;
                        }
                    }
                    else
                    {
                        i++;
                    }
                }

                case IN_DOLLAR_QUOTE ->
                {
                    if (c == '$'
                            && sql.startsWith(dollarTag, i))
                    {
                        out.append(dollarTag);
                        i += dollarTag.length();
                        state = State.NORMAL;
                        dollarTag = null;
                    }
                    else
                    {
                        out.append(c);
                        i++;
                    }
                }
            }
        }

        if (state != State.NORMAL
                && state != State.IN_LINE_COMMENT)
        {
            throw new IllegalArgumentException("Unterminated SQL literal, parser ended in state: " + state);
        }

        return new ParsedQuery(out.toString(), values);
    }

    private static boolean isParamStart(String sql, int i)
    {
        return i + 1 < sql.length()
                && Character.isLetter(sql.charAt(i + 1));
    }

    private static boolean isParamPart(char c)
    {
        return Character.isLetterOrDigit(c)
                || c == '_';
    }

    private enum State
    {
        NORMAL,
        IN_SINGLE_QUOTE,
        IN_DOUBLE_QUOTE,
        IN_BACKTICK,
        IN_LINE_COMMENT,
        IN_BLOCK_COMMENT,
        IN_DOLLAR_QUOTE
    }
}
