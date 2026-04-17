package com.queryeer.editor;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scans document text for an outline pattern directive and collects bookmark entries. Pure logic, no Swing dependencies. */
class OutlineScanner
{
    /** Maximum number of lines to scan for the directive. */
    private static final int DIRECTIVE_LINE_THRESHOLD = 20;

    /**
     * Regex to detect the directive line. Matches comment styles: {@code --}, {@code //}, {@code #}. Captures the marker pattern after the colon.
     */
    private static final Pattern DIRECTIVE_REGEX = Pattern.compile("^\\s*(?:--|//|#)\\s*outline\\s+pattern:\\s*(.+?)\\s*$", Pattern.CASE_INSENSITIVE);

    record OutlineEntry(String label, int lineNumber, int offset)
    {
    }

    record ScanResult(String pattern, List<OutlineEntry> entries)
    {
    }

    /**
     * Scan the document text for an outline directive in the first {@value #DIRECTIVE_LINE_THRESHOLD} lines. If found, collect all lines in the full document that start with the declared pattern.
     *
     * @return scan result, or {@code null} if no directive was found or text is empty
     */
    static ScanResult scan(String text)
    {
        if (isBlank(text))
        {
            return null;
        }

        // Find the directive in the first N lines
        String pattern = null;
        int directiveLineNumber = -1;
        int lineNumber = 0;
        int offset = 0;

        while (offset < text.length()
                && lineNumber < DIRECTIVE_LINE_THRESHOLD)
        {
            int lineEnd = text.indexOf('\n', offset);
            if (lineEnd == -1)
            {
                lineEnd = text.length();
            }

            lineNumber++;
            String line = text.substring(offset, lineEnd);
            Matcher m = DIRECTIVE_REGEX.matcher(line);
            if (m.matches())
            {
                pattern = m.group(1)
                        .trim();
                directiveLineNumber = lineNumber;
                break;
            }

            offset = lineEnd + 1;
        }

        if (pattern == null)
        {
            return null;
        }

        // Now scan the full document for lines matching the pattern
        List<OutlineEntry> entries = new ArrayList<>();
        lineNumber = 0;
        offset = 0;

        while (offset < text.length())
        {
            int lineEnd = text.indexOf('\n', offset);
            if (lineEnd == -1)
            {
                lineEnd = text.length();
            }

            lineNumber++;
            String line = text.substring(offset, lineEnd);
            String trimmedLine = line.stripLeading();

            // Skip the directive line itself
            if (lineNumber != directiveLineNumber
                    && trimmedLine.startsWith(pattern))
            {
                String label = trimmedLine.substring(pattern.length())
                        .trim();
                if (!label.isEmpty())
                {
                    entries.add(new OutlineEntry(label, lineNumber, offset));
                }
            }

            offset = lineEnd + 1;
        }

        return new ScanResult(pattern, Collections.unmodifiableList(entries));
    }
}
