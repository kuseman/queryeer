package com.queryeer.api.component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Properties descriptors that can be */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Properties
{
    /** Header that is shown above properties. HTML is allowed */
    String header();

    /** List of property descriptors that this type contains */
    Property[] properties() default @Property(
            propertyName = "DEFAULT",
            title = "DEFAULT",
            order = 0);
}
