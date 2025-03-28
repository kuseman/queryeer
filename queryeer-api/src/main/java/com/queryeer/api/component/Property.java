package com.queryeer.api.component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Annotation used on get methods on a model POJO. Used by {@link PropertiesComponent} to automatically build UI's */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Property
{
    /** Property name that this property points to. Only applicable when contained inside a {@link Properties} */
    String propertyName() default "";

    /** Get order of property */
    int order();

    /** Get title of property */
    String title();

    /** Get description of property. Will be shown as a tooltip */
    String description() default "";

    /** Is this property enabled aware. If true then the model where this property is used must implement {@link IPropertyAware#enabled(String)} */
    boolean enableAware() default false;

    /** Is this property visible aware. If true then the model where this property is used must implement {@link IPropertyAware#visible(String)} */
    boolean visibleAware() default false;

    /** Shoule this property be ignored from UI */
    boolean ignore() default false;

    /** Is this a password property */
    boolean password() default false;

    /** Return tooltip that should be displayed on this property */
    String tooltip() default "";

    /** Should the parent (UI) be reloaded when this property changes. Useful if other properties is set when this property is set to properly reflect that in UI. */
    boolean reloadParentOnChange() default false;
}
