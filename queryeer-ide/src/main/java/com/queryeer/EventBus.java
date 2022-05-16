package com.queryeer;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.queryeer.api.event.Event;
import com.queryeer.api.event.Subscribe;
import com.queryeer.api.service.IEventBus;

/** Implementation of the event bus */
class EventBus implements IEventBus
{
    private final Map<Class<?>, List<Subscriber>> subscribersByEventType = new HashMap<>();

    @Override
    public void register(Object bean)
    {
        requireNonNull(bean, "bean cannot be null");
        synchronized (this)
        {
            List<Method> methods = getSubScribeMethods(bean.getClass());
            for (Method method : methods)
            {
                Class<?> eventClass = method.getParameters()[0].getType();
                subscribersByEventType.computeIfAbsent(eventClass, c -> new ArrayList<>())
                        .add(new Subscriber(bean, method));
            }
        }
    }

    @Override
    public void unregister(Object bean)
    {
        synchronized (this)
        {
            List<Method> methods = getSubScribeMethods(bean.getClass());
            for (Method method : methods)
            {
                Class<?> eventClass = method.getParameters()[0].getType();
                List<Subscriber> list = subscribersByEventType.getOrDefault(eventClass, emptyList());
                if (list.isEmpty())
                {
                    continue;
                }
                list.removeIf(s -> s.target == bean);
                if (list.isEmpty())
                {
                    subscribersByEventType.remove(eventClass);
                }
            }
        }
    }

    @Override
    public void publish(Event event)
    {
        requireNonNull(event, "event cannot be null");
        List<Subscriber> list = subscribersByEventType.getOrDefault(event.getClass(), emptyList());
        for (Subscriber subscriber : list)
        {
            try
            {
                subscriber.method.invoke(subscriber.target, event);
            }
            catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e)
            {
                // TODO: logging
                e.printStackTrace();
            }
        }
    }

    private List<Method> getSubScribeMethods(Class<?> clazz)
    {
        List<Method> methods = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods())
        {
            if (method.getAnnotation(Subscribe.class) == null)
            {
                continue;
            }

            if (method.getParameterCount() != 1
                    || !Event.class.isAssignableFrom(method.getParameters()[0].getType()))
            {
                throw new IllegalArgumentException("Subscribe methods should have one argument that is of " + Event.class + " type");
            }

            methods.add(method);
        }
        return methods;
    }

    static class Subscriber
    {
        private final Object target;
        private final Method method;

        Subscriber(Object target, Method method)
        {
            this.target = target;
            this.method = method;
            this.method.setAccessible(true);
        }
    }
}
