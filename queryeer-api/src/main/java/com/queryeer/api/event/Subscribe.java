package com.queryeer.api.event;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import com.queryeer.api.service.IEventBus;

/** Annotations put on methods to mark for event subscription from the {@link IEventBus} */
@Retention(RUNTIME)
@Target({ ElementType.METHOD })
public @interface Subscribe
{

}
