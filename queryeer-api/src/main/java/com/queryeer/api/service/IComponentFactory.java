package com.queryeer.api.service;

import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.JComboBox;

import com.queryeer.api.component.IListPropertiesComponent;
import com.queryeer.api.component.IPropertiesComponent;
import com.queryeer.api.component.ISyntaxTextEditor;
import com.queryeer.api.component.Property;

/**
 * Definition of component factory. This factory provides standard methods for creating commonly used componentes in Queryeer that can be utilized.
 */
public interface IComponentFactory
{
    /** Enables auto completion of provided combobox */
    void enableAutoCompletion(JComboBox<?> combobox);

    /**
     * Create a syntax text editor with provided preferred syntax
     *
     * @param preferredSyntax The mime type of the preferred syntax of the editor
     */
    ISyntaxTextEditor createSyntaxTextEditor(String preferredSyntax);

    /**
     * Creates a {@link IPropertiesComponent} from provided clazz.
     *
     * @param clazz Class to inspect for {@link Property}'s
     * @param dirtyConsumer Consumer of dirty state of the sub components in the properties component.
     */
    IPropertiesComponent createPropertiesComponent(Class<?> clazz, Consumer<Boolean> dirtyConsumer);

    /**
     * Creates a {@link IListPropertiesComponent} from provided clazz.
     *
     * @param clazz Class to inspect for {@link Property}'s
     * @param dirtyConsumer Consumer of dirty state of the sub components in the properties component.
     */
    <T> IListPropertiesComponent<T> createListPropertiesComponent(Class<?> clazz, Consumer<Boolean> dirtyConsumer, Supplier<T> itemCreator);
}
