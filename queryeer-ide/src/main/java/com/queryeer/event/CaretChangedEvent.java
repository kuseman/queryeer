package com.queryeer.event;

import com.queryeer.api.component.Caret;
import com.queryeer.api.event.Event;

/** Event fired when the caret changes in query files */
public class CaretChangedEvent extends Event
{
    private final Caret caret;

    public CaretChangedEvent(Caret caret)
    {
        this.caret = caret;
    }

    public Caret getCaret()
    {
        return caret;
    }
}
