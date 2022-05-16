package com.queryeer.api.service;

import com.queryeer.api.event.Event;
import com.queryeer.api.event.Subscribe;

/** Definition of the event bus. Used to register for subscriptions of events and also publish events */
public interface IEventBus
{
    /** Register bean for subscriptions. Methods annotated with {@link Subscribe} will be registered */
    void register(Object bean);

    /** Unregister bean from subscriptions */
    void unregister(Object bean);

    /** Publish an event to the bus */
    void publish(Event event);
}
