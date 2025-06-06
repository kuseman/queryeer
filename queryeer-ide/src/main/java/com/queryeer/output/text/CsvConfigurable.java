package com.queryeer.output.text;

import static com.queryeer.output.text.TextOutputExtension.MAPPER;
import static java.util.Objects.requireNonNull;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.queryeer.api.component.Properties;
import com.queryeer.api.component.PropertiesComponent;
import com.queryeer.api.component.Property;
import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.service.IConfig;

import se.kuseman.payloadbuilder.core.CsvOutputWriter.CsvSettings;

/** Configurable extension point for {@link CsvFormat} */
class CsvConfigurable implements IConfigurable
{
    private static final String NAME = TextOutputExtension.NAME + ".csv";
    private final List<Consumer<Boolean>> dirstyStateConsumers = new ArrayList<>();
    private final IConfig config;
    private PropertiesComponent component;
    private CsvSettingsWrapper settings;

    CsvConfigurable(IConfig config)
    {
        this.config = requireNonNull(config, "config");
        this.settings = loadSettings();
    }

    CsvSettings getSettings()
    {
        return settings;
    }

    @Override
    public Component getComponent()
    {
        if (component == null)
        {
            component = new PropertiesComponent(CsvSettingsWrapper.class, this::notifyDirty);
            component.init(new CsvSettingsWrapper(settings));
        }
        return component;
    }

    @Override
    public String getTitle()
    {
        return CsvFormat.CSV;
    }

    @Override
    public String getLongTitle()
    {
        return "Settings for CSV Output Writer";
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
    public boolean commitChanges()
    {
        if (component == null)
        {
            return false;
        }
        settings = (CsvSettingsWrapper) component.getTarget();
        config.saveExtensionConfig(NAME, MAPPER.convertValue(settings, Map.class));
        return true;
    }

    @Override
    public void revertChanges()
    {
        if (component == null)
        {
            return;
        }
        component.init(new CsvSettingsWrapper(settings));
    }

    private void notifyDirty(boolean dirty)
    {
        dirstyStateConsumers.forEach(c -> c.accept(dirty));
    }

    @SuppressWarnings("unchecked")
    private CsvSettingsWrapper loadSettings()
    {
        Map<String, Object> settings = config.loadExtensionConfig(NAME);

        // No settings, create default and write to disk
        if (settings.isEmpty())
        {
            CsvSettingsWrapper result = new CsvSettingsWrapper();
            config.saveExtensionConfig(NAME, MAPPER.convertValue(result, Map.class));
            return result;
        }
        else
        {
            return MAPPER.convertValue(settings, CsvSettingsWrapper.class);
        }
    }

    @Properties(
            properties = {
                    @Property(
                            propertyName = "escapeChar",
                            title = "Escape Char",
                            order = 0),
                    @Property(
                            propertyName = "separatorChar",
                            title = "Separator Char",
                            order = 1),
                    @Property(
                            propertyName = "arrayStartChar",
                            title = "Array Start Char",
                            order = 2),
                    @Property(
                            propertyName = "arrayEndChar",
                            title = "Array End Char",
                            order = 3),
                    @Property(
                            propertyName = "objectStartChar",
                            title = "Object Start Char",
                            order = 4),
                    @Property(
                            propertyName = "objectEndChar",
                            title = "Object End Char",
                            order = 5),
                    @Property(
                            propertyName = "writeHeaders",
                            title = "Write Headers",
                            description = "Should headers be written to output",
                            order = 6),
                    @Property(
                            propertyName = "escapeNewLines",
                            title = "Escape New Lines",
                            order = 7),
                    @Property(
                            propertyName = "rowSeparator",
                            title = "Row Separator",
                            description = "Separator between rows. Escape values: \\n, \\t",
                            order = 8),
                    @Property(
                            propertyName = "resultSetSeparator",
                            title = "Result Set Separator",
                            description = "Separator between result sets. Escape values: \\n, \\t",
                            order = 9) })
    private static class CsvSettingsWrapper extends CsvSettings
    {
        @JsonIgnore
        private Preset preset;

        CsvSettingsWrapper()
        {
        }

        CsvSettingsWrapper(CsvSettingsWrapper source)
        {
            setEscapeChar(source.getEscapeChar());
            setSeparatorChar(source.getSeparatorChar());
            setArrayStartChar(source.getArrayStartChar());
            setArrayEndChar(source.getArrayEndChar());
            setObjectStartChar(source.getObjectStartChar());
            setObjectEndChar(source.getObjectEndChar());
            setWriteHeaders(source.isWriteHeaders());
            setEscapeNewLines(source.isEscapeNewLines());
            setRowSeparator(source.getRowSeparator());
            setResultSetSeparator(source.getResultSetSeparator());
        }

        @Property(
                title = "Preset",
                order = 10,
                reloadParentOnChange = true)
        public Preset getTemplate()
        {
            return preset;
        }

        @SuppressWarnings("unused")
        public void setTemplate(Preset preset)
        {
            this.preset = preset;
            if (preset != null)
            {
                switch (preset)
                {
                    case COMMA:
                        setSeparatorChar(',');
                        setEscapeChar('\\');
                        setEscapeNewLines(true);
                        setRowSeparator(System.lineSeparator());
                        setWriteHeaders(true);
                        break;
                    case UNFORMATTED:
                        setSeparatorChar('\0');
                        setEscapeChar('\0');
                        setEscapeNewLines(false);
                        setRowSeparator("");
                        setWriteHeaders(false);
                        break;
                    case SEMICOLON:
                        setSeparatorChar(';');
                        setEscapeChar('\\');
                        setEscapeNewLines(true);
                        setRowSeparator(System.lineSeparator());
                        setWriteHeaders(true);
                        break;
                    case TAB:
                        setSeparatorChar('\t');
                        setEscapeChar('\\');
                        setEscapeNewLines(true);
                        setRowSeparator(System.lineSeparator());
                        setWriteHeaders(true);
                        break;
                    case PIPE:
                        setSeparatorChar('|');
                        setEscapeChar('\\');
                        setEscapeNewLines(true);
                        setRowSeparator(System.lineSeparator());
                        setWriteHeaders(true);
                        break;
                    default:
                        break;

                }
            }
        }

        enum Preset
        {
            UNFORMATTED,
            COMMA,
            SEMICOLON,
            PIPE,
            TAB
        }
    }
}
