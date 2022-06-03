package com.queryeer.component;

import static se.kuseman.payloadbuilder.api.utils.StringUtils.isBlank;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.queryeer.api.component.IPropertiesComponent;
import com.queryeer.api.component.IPropertyAware;
import com.queryeer.api.component.Property;

/**
 * Component that inspects a class for {@link Property} and builds a plain table of title and components from it's property types
 */
public class PropertiesComponent extends JPanel implements IPropertiesComponent
{
    final PropertyFields propertyFields;
    private final Consumer<Boolean> dirtyConsumer;
    private final List<Consumer<Object>> componentSetters;
    private final Map<String, Consumer<Boolean>> enableAwareProperties;
    private final Map<String, Consumer<Boolean>> visibleAwareProperties;
    private final boolean showPropertiesHeader;

    private Object target;
    private Map<String, Object> originalState = new HashMap<>();

    public PropertiesComponent(Class<?> clazz, Consumer<Boolean> dirtyConsumer)
    {
        this(clazz, dirtyConsumer, true);
    }

    PropertiesComponent(Class<?> clazz, Consumer<Boolean> dirtyConsumer, boolean showPropertiesHeader)
    {
        this.showPropertiesHeader = showPropertiesHeader;
        this.propertyFields = Utils.collectPropertyFields(clazz);
        this.componentSetters = new ArrayList<>(propertyFields.getFields()
                .size());
        this.enableAwareProperties = new HashMap<>(propertyFields.getFields()
                .size());
        this.visibleAwareProperties = new HashMap<>(propertyFields.getFields()
                .size());
        this.dirtyConsumer = dirtyConsumer;

        initComponent();
    }

    @Override
    public Component getComponent()
    {
        return this;
    }

    /** Sets enabled status of all components */
    @Override
    public void setEnabled(boolean enabled)
    {
        super.setEnabled(enabled);
        for (Component cmp : getComponents())
        {
            cmp.setEnabled(enabled);
        }
    }

    /**
     * Init component with target object.
     *
     * <pre>
     * NOTE! A deep copy if the target should be provided to be able to revert changes made
     * </pre>
     */
    @Override
    public void init(Object target)
    {
        this.target = target;
        this.originalState.clear();
        int index = 0;
        for (PropertyFields.PropertyField pf : propertyFields.getFields())
        {
            Object value = pf.getValue(target);
            originalState.put(pf.getName(), value);
            componentSetters.get(index++)
                    .accept(value);
        }

        processAwareProperties();
    }

    /** Return the current target/resulting model from component and marking the component as not dirty */
    @Override
    public Object getTarget()
    {
        // Re-init original state
        for (PropertyFields.PropertyField pf : propertyFields.getFields())
        {
            Object value = pf.getValue(target);
            originalState.put(pf.getName(), value);
        }

        return target;
    }

    private void initComponent()
    {
        setLayout(new GridBagLayout());

        int y = 0;
        if (showPropertiesHeader
                && !isBlank(propertyFields.getHeader()))
        {
            JLabel label = new JLabel(propertyFields.getHeader());
            label.setHorizontalAlignment(JLabel.CENTER);
            add(label, new GridBagConstraints(0, y++, 2, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 3, 3, 3), 0, 0));
        }

        for (PropertyFields.PropertyField pf : propertyFields.getFields())
        {
            final Component component = createPropertyComponent(pf);
            final JLabel label = new JLabel();
            if (component instanceof PropertiesComponent)
            {
                // No label when having a nested properties component
                add(component, new GridBagConstraints(0, y, 2, 1, 1.0, 0.0, GridBagConstraints.BASELINE, GridBagConstraints.BOTH, new Insets(0, 0, 3, 0), 0, 0));
            }
            else
            {
                label.setText(pf.getTitle());
                if (!isBlank(pf.getDescription()))
                {
                    label.setToolTipText(pf.getDescription());
                }

                add(label, new GridBagConstraints(0, y, 1, 1, 0.0, 0.0, GridBagConstraints.BASELINE, GridBagConstraints.HORIZONTAL, new Insets(0, 3, 3, 3), 0, 0));
                add(component, new GridBagConstraints(1, y, 1, 1, 1.0, 0.0, GridBagConstraints.BASELINE, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 3, 3), 0, 0));
            }

            y++;

            if (pf.getProperty()
                    .visibleAware())
            {
                visibleAwareProperties.put(pf.getName(), v ->
                {
                    component.setVisible(v);
                    label.setVisible(v);
                });
            }
        }
        add(new JPanel(), new GridBagConstraints(0, y, 2, 1, 1.0, 1.0, GridBagConstraints.BASELINE, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
    }

    private void setValue(PropertyFields.PropertyField field, Object newValue)
    {
        if (target == null)
        {
            return;
        }

        Object oldValue = originalState.get(field.getName());
        field.setValue(target, newValue);
        dirtyConsumer.accept(!Objects.equals(oldValue, newValue));

        processAwareProperties();
    }

    private void processAwareProperties()
    {
        // Toggle enable
        for (Entry<String, Consumer<Boolean>> e : enableAwareProperties.entrySet())
        {
            e.getValue()
                    .accept(((IPropertyAware) target).enabled(e.getKey()));
        }

        // Toggle visible
        for (Entry<String, Consumer<Boolean>> e : visibleAwareProperties.entrySet())
        {
            e.getValue()
                    .accept(((IPropertyAware) target).visible(e.getKey()));
        }
    }

    private Component createPropertyComponent(PropertyFields.PropertyField field)
    {
        final Class<?> type = field.getType();
        if (Boolean.class == type
                || boolean.class == type)
        {
            final JCheckBox checkbox = new JCheckBox()
            {
                @Override
                protected void fireActionPerformed(ActionEvent event)
                {
                    if (this.hasFocus())
                    {
                        super.fireActionPerformed(event);
                    }
                }
            };
            checkbox.addActionListener(l ->
            {
                setValue(field, checkbox.isSelected());
            });
            componentSetters.add(o -> checkbox.setSelected(booleanValue(o)));
            checkbox.setEnabled(!field.isReadOnly());

            if (!field.isReadOnly()
                    && field.getProperty()
                            .enableAware())
            {
                enableAwareProperties.put(field.getName(), e -> checkbox.setEnabled(e));
            }

            return checkbox;
        }
        else if (Integer.class == type
                || int.class == type)
        {
            final JTextField textField = new JTextField()
            {
                @Override
                protected void processKeyEvent(KeyEvent e)
                {
                    if (this.hasFocus())
                    {
                        super.processKeyEvent(e);
                    }
                };
            };
            textField.addKeyListener(new KeyAdapter()
            {
                @Override
                public void keyReleased(KeyEvent e)
                {
                    String text = textField.getText();
                    Integer value = null;
                    if (text.length() > 0
                            && text.matches("^\\d+$"))
                    {
                        value = Integer.parseInt(text);
                    }
                    else
                    {
                        e.consume();
                        textField.setText(stringValue(field.getValue(target)));
                        return;
                    }

                    setValue(field, value);
                }
            });
            componentSetters.add(o -> textField.setText(stringValue(o)));
            textField.setEditable(!field.isReadOnly());

            if (!field.isReadOnly()
                    && field.getProperty()
                            .enableAware())
            {
                enableAwareProperties.put(field.getName(), e -> textField.setEnabled(e));
            }

            return textField;
        }
        else if (String.class == type)
        {
            final JTextField textField = new JTextField()
            {
                @Override
                protected void processKeyEvent(KeyEvent e)
                {
                    if (this.hasFocus())
                    {
                        super.processKeyEvent(e);
                    }
                };
            };
            textField.addKeyListener(new KeyAdapter()
            {
                @Override
                public void keyReleased(KeyEvent e)
                {
                    setValue(field, textField.getText());
                }
            });
            componentSetters.add(o -> textField.setText(stringValue(o)));
            textField.setEditable(!field.isReadOnly());

            if (!field.isReadOnly()
                    && field.getProperty() != null
                    && field.getProperty()
                            .enableAware())
            {
                enableAwareProperties.put(field.getName(), e -> textField.setEnabled(e));
            }

            return textField;
        }
        else if (char.class == type
                || Character.class == type)
        {
            final JTextField textField = new JTextField()
            {
                @Override
                protected void processKeyEvent(KeyEvent e)
                {
                    if (this.hasFocus())
                    {
                        super.processKeyEvent(e);
                    }
                };
            };
            textField.addKeyListener(new KeyAdapter()
            {
                @Override
                public void keyReleased(KeyEvent e)
                {
                    Character ch = textField.getText()
                            .length() > 0 ? textField.getText()
                                    .charAt(0)
                                    : null;

                    setValue(field, ch);

                    if (textField.getText()
                            .length() > 1)
                    {
                        textField.setText(textField.getText()
                                .substring(0, 1));
                    }
                }
            });
            componentSetters.add(o -> textField.setText(stringValue(o)));
            textField.setEnabled(!field.isReadOnly());

            if (!field.isReadOnly()
                    && field.getProperty()
                            .enableAware())
            {
                enableAwareProperties.put(field.getName(), e -> textField.setEnabled(e));
            }

            return textField;
        }
        else if (type.isEnum())
        {
            final JComboBox<Object> combobox = new JComboBox<Object>()
            {
                @Override
                protected void fireActionEvent()
                {
                    if (this.hasFocus())
                    {
                        super.fireActionEvent();
                    }
                }
            };
            combobox.setModel(new DefaultComboBoxModel<>(type.getEnumConstants()));
            combobox.addActionListener(l -> setValue(field, combobox.getSelectedItem()));
            componentSetters.add(o -> combobox.setSelectedItem(o));
            combobox.setEnabled(!field.isReadOnly());

            if (!field.isReadOnly()
                    && field.getProperty()
                            .enableAware())
            {
                enableAwareProperties.put(field.getName(), e -> combobox.setEnabled(e));
            }

            return combobox;
        }
        else
        {
            final PropertiesComponent component = new PropertiesComponent(type, d ->
            {
                PropertiesComponent.this.dirtyConsumer.accept(d);
                processAwareProperties();
            });
            // Remove the last padding panel
            component.remove(component.getComponentCount() - 1);
            componentSetters.add(o ->
            {
                // Create value on model when null
                if (o == null)
                {
                    o = Utils.newInstance(type);
                    field.setValue(target, o);
                }

                component.init(o);
            });

            if (!field.isReadOnly()
                    && field.getProperty()
                            .enableAware())
            {
                enableAwareProperties.put(field.getName(), e -> component.setEnabled(e));
            }

            return component;
        }
    }

    private static String stringValue(Object o)
    {
        if (o == null)
        {
            return "";
        }
        return String.valueOf(o);
    }

    private static boolean booleanValue(Object o)
    {
        if (o == null)
        {
            return false;
        }

        return (Boolean) o;
    }
}
