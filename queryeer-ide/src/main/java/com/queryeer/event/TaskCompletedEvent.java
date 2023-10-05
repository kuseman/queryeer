package com.queryeer.event;

import static java.util.Objects.requireNonNull;

import com.queryeer.api.event.Event;

/** Event fired when a new task is added */
public class TaskCompletedEvent extends Event
{
    private final Object taskKey;
    private Throwable exception;

    public TaskCompletedEvent(Object taskKey)
    {
        this(taskKey, null);
    }

    public TaskCompletedEvent(Object taskKey, Throwable exception)
    {
        this.taskKey = requireNonNull(taskKey, "taskKey");
        this.exception = exception;
    }

    public Object getTaskKey()
    {
        return taskKey;
    }

    public Throwable getException()
    {
        return exception;
    }
}
