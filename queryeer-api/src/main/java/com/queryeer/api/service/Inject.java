package com.queryeer.api.service;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation used to mark if a type or constructor should be used in service loader when wiring services NOTE! When a service have more than one constructor this annotation is used to determine which
 * ctor that should be used
 */
@Retention(RUNTIME)
@Target({ TYPE, CONSTRUCTOR })
public @interface Inject
{

}
