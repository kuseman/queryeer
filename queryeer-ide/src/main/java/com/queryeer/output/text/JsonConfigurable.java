package com.queryeer.output.text;

import static com.queryeer.output.text.TextOutputExtension.MAPPER;
import static java.util.Objects.requireNonNull;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.queryeer.api.component.IPropertyAware;
import com.queryeer.api.component.Properties;
import com.queryeer.api.component.PropertiesComponent;
import com.queryeer.api.component.Property;
import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.service.IConfig;

import se.kuseman.payloadbuilder.core.JsonOutputWriter.JsonSettings;

/** Configurable extension point for {@link JsonFormat} */
class JsonConfigurable implements IConfigurable
{
    private static final String NAME = TextOutputExtension.NAME + ".json";
    private final List<Consumer<Boolean>> dirstyStateConsumers = new ArrayList<>();
    private final IConfig config;
    private JsonSettingsWrapper settings;
    private PropertiesComponent component;

    JsonConfigurable(IConfig config)
    {
        this.config = requireNonNull(config, "config");
        this.settings = loadSettings();
    }

    JsonSettings getSettings()
    {
        return settings;
    }

    @Override
    public Component getComponent()
    {
        if (component == null)
        {
            component = new PropertiesComponent(JsonSettingsWrapper.class, this::notifyDirty);
            component.init(new JsonSettingsWrapper(settings));
        }
        return component;
    }

    @Override
    public String getTitle()
    {
        return JsonFormat.JSON;
    }

    @Override
    public String getLongTitle()
    {
        return "Settings for JSON Output Writer";
    }

    @Override
    public String groupName()
    {
        return IConfigurable.OUTPUT_FORMAT;
    }

    @Override
    public void addDirtyStateConsumer(Consumer<Boolean> consumer)
    {
        dirstyStateConsumers.add(consumer);
    }

    @Override
    public void removeDirtyStateConsumer(Consumer<Boolean> consumer)
    {
        dirstyStateConsumers.remove(consumer);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void commitChanges()
    {
        if (component == null)
        {
            return;
        }
        settings = (JsonSettingsWrapper) component.getTarget();
        config.saveExtensionConfig(NAME, MAPPER.convertValue(settings, Map.class));
    }

    @Override
    public void revertChanges()
    {
        if (component == null)
        {
            return;
        }
        component.init(new JsonSettingsWrapper(settings));
    }

    private void notifyDirty(boolean dirty)
    {
        dirstyStateConsumers.forEach(c -> c.accept(dirty));
    }

    @SuppressWarnings("unchecked")
    private JsonSettingsWrapper loadSettings()
    {
        Map<String, Object> settings = config.loadExtensionConfig(NAME);

        // No settings, create default and write to disk
        if (settings.isEmpty())
        {
            JsonSettingsWrapper result = new JsonSettingsWrapper();
            config.saveExtensionConfig(NAME, MAPPER.convertValue(result, Map.class));
            return result;
        }
        else
        {
            return MAPPER.convertValue(settings, JsonSettingsWrapper.class);
        }
    }

    @Properties(
            properties = {
                    @Property(
                            propertyName = "rowSeparator",
                            title = "Row Separator",
                            order = 0),
                    @Property(
                            propertyName = "resultSetSeparator",
                            title = "Result Set Separator",
                            order = 1),
                    @Property(
                            propertyName = "prettyPrint",
                            title = "Pretty Print",
                            order = 2),
                    @Property(
                            propertyName = JsonSettingsWrapper.RESULT_SETS_AS_ARRAYS,
                            title = "Result Sets As Arrays",
                            description = "<html>Wrap result set rows as an array. Mutual exclusive with <b>All Result Sets As One Array</b>",
                            visibleAware = true,
                            order = 3),
                    @Property(
                            propertyName = JsonSettingsWrapper.ALL_RESULT_SETS_AS_ONE_ARRAYS,
                            title = "All Result Sets As One Array",
                            description = "<html>Wrap rows from all result sets in one big array. Mutual exclusive with <b>Result Sets As Arrays</b>",
                            visibleAware = true,
                            order = 4) })
    private static class JsonSettingsWrapper extends JsonSettings implements IPropertyAware
    {
        static final String RESULT_SETS_AS_ARRAYS = "resultSetsAsArrays";
        static final String ALL_RESULT_SETS_AS_ONE_ARRAYS = "allResultSetsAsOneArray";

        JsonSettingsWrapper()
        {
        }

        JsonSettingsWrapper(JsonSettingsWrapper source)
        {
            setAllResultSetsAsOneArray(source.isAllResultSetsAsOneArray());
            setPrettyPrint(source.isPrettyPrint());
            setResultSetsAsArrays(source.isResultSetsAsArrays());
            setResultSetSeparator(source.getResultSetSeparator());
            setRowSeparator(source.getRowSeparator());
        }

        @Override
        public boolean enabled(String property)
        {
            if (RESULT_SETS_AS_ARRAYS.equals(property))
            {
                return !isAllResultSetsAsOneArray();
            }
            else if (ALL_RESULT_SETS_AS_ONE_ARRAYS.equals(property))
            {
                return !isResultSetsAsArrays();
            }

            return true;
        }
    }
}
