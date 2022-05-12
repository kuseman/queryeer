package com.queryeer.api.component;

/**
 * Interface implemented on models where {@link Property Properties} changes it's components enabled/visibility status etc.
 */
public interface IPropertyAware
{
    /** Returns if provided property should be visible or not according to current model values */
    default boolean visible(String property)
    {
        return true;
    }

    /** Returns if provided property should be enabled or not according to current model values */
    default boolean enabled(String property)
    {
        return true;
    }
}
