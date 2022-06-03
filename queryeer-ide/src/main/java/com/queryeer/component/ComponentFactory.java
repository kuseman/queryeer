package com.queryeer.component;

import static java.util.Objects.requireNonNull;

import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.JComboBox;

import com.queryeer.api.component.IListPropertiesComponent;
import com.queryeer.api.component.IPropertiesComponent;
import com.queryeer.api.component.ISyntaxTextEditor;
import com.queryeer.api.service.IComponentFactory;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.Inject;

@Inject
class ComponentFactory implements IComponentFactory
{
    private final IEventBus eventBus;

    ComponentFactory(IEventBus eventBus)
    {
        this.eventBus = requireNonNull(eventBus, "eventBus");
    }

    @Override
    public void enableAutoCompletion(JComboBox<?> combobox)
    {
        AutoCompletionComboBox.enable(combobox);
    }

    @Override
    public ISyntaxTextEditor createSyntaxTextEditor(String preferredSyntax)
    {
        return new SyntaxTextEditor(eventBus, preferredSyntax);
    }

    @Override
    public IPropertiesComponent createPropertiesComponent(Class<?> clazz, Consumer<Boolean> dirtyConsumer)
    {
        return new PropertiesComponent(clazz, dirtyConsumer);
    }

    @Override
    public <T> IListPropertiesComponent<T> createListPropertiesComponent(Class<?> clazz, Consumer<Boolean> dirtyConsumer, Supplier<T> itemCreator)
    {
        return new ListPropertiesComponent<>(clazz, dirtyConsumer, itemCreator);
    }

}
