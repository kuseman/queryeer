package com.queryeer.event;

import static java.util.Objects.requireNonNull;

import com.queryeer.api.event.Event;
import com.queryeer.domain.Task;

/** Event fired when a new task is added */
public class TaskStartedEvent extends Event
{
    private final Task task;

    public TaskStartedEvent(Task task)
    {
        this.task = requireNonNull(task, "task");
    }

    public Task getTask()
    {
        return task;
    }
}
