package com.queryeer.domain;

/** Domain of a caret in text editors */
public class Caret
{
    private int lineNumber;
    /** Offset in current line */
    private int offset;
    /** Position in document */
    private int position;

    private int selectionStart;
    private int selectionLength;

    public int getLineNumber()
    {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber)
    {
        this.lineNumber = lineNumber;
    }

    public int getOffset()
    {
        return offset;
    }

    public void setOffset(int offset)
    {
        this.offset = offset;
    }

    public int getPosition()
    {
        return position;
    }

    public void setPosition(int position)
    {
        this.position = position;
    }

    public int getSelectionStart()
    {
        return selectionStart;
    }

    public void setSelectionStart(int selectionStart)
    {
        this.selectionStart = selectionStart;
    }

    public int getSelectionLength()
    {
        return selectionLength;
    }

    public void setSelectionLength(int selectionLength)
    {
        this.selectionLength = selectionLength;
    }
}