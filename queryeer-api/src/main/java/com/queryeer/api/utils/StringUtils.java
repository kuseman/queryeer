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

}
