package com.queryeer.api;

/** Definition of a text selection range */
public record TextSelection(int start, int end)
{
    public static TextSelection EMPTY = new TextSelection(0, 0);

    public boolean isEmpty()
    {
        return start - end == 0;
    }
}
