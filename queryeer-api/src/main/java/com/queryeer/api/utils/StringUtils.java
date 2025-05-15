package com.queryeer.api.utils;

/** Utils for string */
public class StringUtils
{
    /** Returns true if provided sequence is blank */
    public static boolean isBlank(CharSequence seq)
    {
        if (seq == null)
        {
            return true;
        }
        final int strLen = seq.length();
        if (strLen == 0)
        {
            return true;
        }
        for (int i = 0; i < strLen; i++)
        {
            if (!Character.isWhitespace(seq.charAt(i)))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Finds needle in haystack, regardless of case and order of characters. At least two
     * characters in a row must be found in the same order as in needle.s
     *
     * @param needle the string to find
     * @param haystack the string to search in
     * @return true if needle is found in haystack
     */
    public static boolean findStringsInString(String needle, String haystack)
    {
        if (StringUtils.isBlank(haystack))
        {
            return false;
        }

        if (StringUtils.isBlank(needle))
        {
            return true;
        }

        String lowerNeedle = needle.toLowerCase();
        String lowerHaystack = haystack.toLowerCase();

        if (lowerHaystack.contains(lowerNeedle))
        {
            return true;
        }

        char[] lowerNeedleUnique = lowerNeedle.toCharArray();
        char[] lowerHaystackUnique = lowerHaystack.toCharArray();

        int haystackIndex = 0;
        int needleIndex = 0;
        int charsInRow = 0;
        while (haystackIndex < lowerHaystackUnique.length
                && needleIndex < lowerNeedleUnique.length)
        {
            while (haystackIndex < lowerHaystackUnique.length
                    && needleIndex < lowerNeedleUnique.length
                    && lowerHaystackUnique[haystackIndex] == lowerNeedleUnique[needleIndex]
                    && (charsInRow > 0
                            || lowerHaystackUnique[haystackIndex + 1] == lowerNeedleUnique[needleIndex + 1]))
            {
                charsInRow++;
                lowerHaystackUnique[haystackIndex] = '*';
                haystackIndex++;
                needleIndex++;
            }
            if (needleIndex == lowerNeedleUnique.length)
            {
                return true;
            }
            haystackIndex++;

            if (charsInRow > 2
                    && haystackIndex >= lowerHaystackUnique.length)
            {
                charsInRow = 0;
                haystackIndex = 0;
            }
        }
        return false;
    }
}
