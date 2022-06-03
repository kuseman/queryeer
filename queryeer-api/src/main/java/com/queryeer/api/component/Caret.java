package com.queryeer.api.component;

/** Domain of a caret in text editors */
public class Caret
{
    private int lineNumber = 1;
    /** Offset in current line */
    private int offset = 1;
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

    @Override
    public int hashCode()
    {
        return lineNumber + position + offset;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof Caret)
        {
            Caret that = (Caret) obj;
            return lineNumber == that.lineNumber
                    && offset == that.offset
                    && position == that.position
                    && selectionStart == that.selectionStart
                    && selectionLength == that.selectionLength;
        }
        return false;
    }
}