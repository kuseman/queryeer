package com.queryeer.domain;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import java.time.LocalDateTime;
import java.util.List;

import javax.swing.Action;

/** Domain of a task that can be published and picked up by queryeer to see progress etc. */
public class Task
{
    /** Unique key for this task. Used to track subsequent events for the same task */
    private final Object key;
    private final String name;
    private final String description;
    private final LocalDateTime startTime = LocalDateTime.now();
    private final List<Action> actions;

    public Task(Object key, String name, String description, List<Action> actions)
    {
        this.key = requireNonNull(key, "key");
        this.name = requireNonNull(name, "name");
        this.description = requireNonNull(description, "description");
        this.actions = actions == null ? emptyList()
                : actions;
    }

    public Object getKey()
    {
        return key;
    }

    public String getName()
    {
        return name;
    }

    public String getDescription()
    {
        return description;
    }

    public LocalDateTime getStartTime()
    {
        return startTime;
    }

    public List<Action> getActions()
    {
        return actions;
    }
}
