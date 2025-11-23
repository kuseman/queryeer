package com.queryeer.api.editor;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import java.awt.Color;
import java.io.Reader;
import java.util.List;

import javax.swing.Action;
import javax.swing.Icon;

/**
 * Definition of a document parser for a {@link ITextEditor}. It handles parsing for errors, collecting code completions, token tooltips etc.
 */
public interface ITextEditorDocumentParser
{
    /**
     * Parse with provided document reader. This method is called when the document is drity and needs parsing. It's called in a separate thread. This method should parse and store resulting state
     * that is needed for what this parser supports.
     *
     * <p>
     * Thread can be interrupted when document gets dirty and a new session is scheduled so it's good to support {@link Thread#isInterrupted()} and cancel current session
     * </p>
     */
    void parse(Reader documentReader);

    /** Return the result from the paring done after {@link #parse(Reader)} is complete. */
    default List<ParseItem> getParseResult()
    {
        return emptyList();
    }

    /** Returns true if completions are supported otherwise false. {@see #getCompletionItems(int)} */
    default boolean supportsCompletions()
    {
        return false;
    }

    /**
     * Return a list of {@link CompletionItem}'s at provided offset.
     */
    default CompletionResult getCompletionItems(int offset)
    {
        return null;
    }

    /** Returns true if tooltips are supported otherwise false. {@see #getToolTip(int)} */
    default boolean supportsToolTips()
    {
        return false;
    }

    /**
     * Return {@link ToolTipItem} at provided offset. This is called on mouse over to get detailed information about an item at a specific offset.'
     *
     * <p>
     * NOTE! No heavy calculations should be done here since this is executed on EDT
     * </p>
     *
     * @param offset The offset in the parsed document
     */
    default ToolTipItem getToolTip(int offset)
    {
        return null;
    }

    /** Returns true if link actions are supported otherwise false. {@see #getLinkAction(int)} */
    default boolean supportsLinkActions()
    {
        return false;
    }

    /**
     * Return {@link LinkAction} at provided offset. This is called on mouse over when CTRL is pressed to get actions for an item at a specific offset. A hyper link.
     *
     * <p>
     * NOTE! No heavy calculations should be done here since this is executed on EDT
     * </p>
     *
     * @param offset The offset in the parsed document
     */
    default LinkAction getLinkAction(int offset)
    {
        return null;
    }

    /** Result of a code completion for an offset */
    static class CompletionResult
    {
        public static final CompletionResult EMPTY = new CompletionResult(emptyList(), false);

        private final List<CompletionItem> items;
        private final boolean partialResult;

        /**
         * Create a completion result with items and a flag indicating if the result is partial which means that there are more items to come after a reload etc. is complete. This is to indicate to
         * the framework that items cannot be cached.
         */
        public CompletionResult(List<CompletionItem> items, boolean partialResult)
        {
            this.items = requireNonNull(items, "items");
            this.partialResult = partialResult;
        }

        public List<CompletionItem> getItems()
        {
            return items;
        }

        public boolean isPartialResult()
        {
            return partialResult;
        }
    }

    /** Resulting tooltip item */
    static class ToolTipItem
    {
        private final int startOffset;
        private final int endOffset;
        private final String toolTip;

        /**
         * Create a tool tip for provided offset interval
         *
         * @param startOffset Start offset of the token in document
         * @param endOffset End offset of the token in document (inclusive)
         * @param toolTip Tooltip to show. Suppors HTML
         */
        public ToolTipItem(int startOffset, int endOffset, String toolTip)
        {
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.toolTip = requireNonNull(toolTip, "toolTip");
        }

        public String getToolTip()
        {
            return toolTip;
        }

        public int getStartOffset()
        {
            return startOffset;
        }

        public int getEndOffset()
        {
            return endOffset;
        }
    }

    /** Resulting link action */
    static class LinkAction
    {
        private final List<Action> actions;
        private final int startOffset;
        private final int endOffset;

        /**
         * Create a link action.
         *
         * @param startOffset Start offset of the token in document
         * @param endOffset End offset of the token in document (inclusive)
         * @param actions Actions for the token in document. Must be non empty
         */
        public LinkAction(int startOffset, int endOffset, List<Action> actions)
        {
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.actions = requireNonNull(actions, "actions");
            if (actions.isEmpty())
            {
                throw new IllegalArgumentException("An ActionItem requires at least one action");
            }
        }

        public List<Action> getActions()
        {
            return actions;
        }

        public int getStartOffset()
        {
            return startOffset;
        }

        public int getEndOffset()
        {
            return endOffset;
        }
    }

    /** Resulting completion item */
    static class CompletionItem
    {
        /**
         * List with with parts that can match input text. For example a multi qualifier "a.column" should match both 'a' and 'c'
         */
        private final List<String> matchParts;
        /** The replacement text that should be inserted */
        private final String replacementText;
        /** Short description that is shown after the replacement text in completions dialog */
        private final String shortDesc;
        /** Large summary that is shown in description window at the side of the completions popup dialog */
        private final String summary;
        private final Icon icon;
        private final int relevance;

        public CompletionItem(String replacementText, String shortDesc, Icon icon)
        {
            this(List.of(replacementText), replacementText, shortDesc, null, icon, 0);
        }

        public CompletionItem(List<String> matchParts, String replacementText, String shortDesc)
        {
            this(matchParts, replacementText, shortDesc, null, null, 0);
        }

        public CompletionItem(String replacementText, int relevance)
        {
            this(List.of(replacementText), replacementText, null, null, null, relevance);
        }

        public CompletionItem(List<String> matchParts, String replacementText, String shortDesc, String summary, Icon icon, int relevance)
        {
            this.matchParts = requireNonNull(matchParts);
            this.replacementText = replacementText;
            this.shortDesc = shortDesc;
            this.summary = summary;
            this.icon = icon;
            this.relevance = relevance;
        }

        public List<String> getMatchParts()
        {
            return matchParts;
        }

        public String getReplacementText()
        {
            return replacementText;
        }

        public String getShortDesc()
        {
            return shortDesc;
        }

        public String getSummary()
        {
            return summary;
        }

        public Icon getIcon()
        {
            return icon;
        }

        public int getRelevance()
        {
            return relevance;
        }

        @Override
        public String toString()
        {
            return replacementText;
        }
    }

    /** Resulting item from a document parse */
    static class ParseItem
    {
        private final String message;
        private final int line;
        private final int offset;
        private final int length;
        private final Color color;
        private final Level level;

        public ParseItem(String message, int line, Level level)
        {
            this(message, line, -1, -1, null, level);
        }

        public ParseItem(String message, int line, int offset, int length)
        {
            this(message, line, offset, length, null, null);
        }

        public ParseItem(String message, int line, int offset, int length, Color color, Level level)
        {
            this.message = message;
            this.line = line;
            this.offset = offset;
            this.length = length;
            this.color = color;
            this.level = level;
        }

        public String getMessage()
        {
            return message;
        }

        public int getLine()
        {
            return line;
        }

        public int getOffset()
        {
            return offset;
        }

        public int getLength()
        {
            return length;
        }

        public Color getColor()
        {
            return color;
        }

        public Level getLevel()
        {
            return level;
        }

        /** Severity level of parse item */
        public enum Level
        {
            INFO,
            WARN,
            ERROR
        }
    }
}
