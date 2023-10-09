package com.queryeer.api.editor;

/** Definition of a text selection range */
public record TextSelection(int line, int start, int end)
{

    public static TextSelection EMPTY = new TextSelection(-1, 0, 0);

    public TextSelection(int start, int end)
    {
        this(-1, start, end);
    }

    public boolean isEmpty()
    {
        return start - end == 0;
    }
}
