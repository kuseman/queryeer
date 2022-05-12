package com.queryeer;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import org.apache.commons.lang3.tuple.Pair;

import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.extensions.catalog.ICatalogExtension;

/** View for extension */
class CatalogExtensionView extends JPanel
{
    private final ICatalogExtension extension;
    private final JLabel labelAlias;
    private final JRadioButton rbDefault;
    private final JButton btnConfig;
    private final JPanel extensionPanel;
    private boolean fireEvents = true;

    CatalogExtensionView(ICatalogExtension extension, ButtonGroup defaultGroup, Consumer<String> defaultCatalogChangedAction, Consumer<Class<? extends IConfigurable>> showConfigAction)
    {
        this.extension = extension;
        setBorder(BorderFactory.createTitledBorder(extension.getTitle()));
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel();
        extensionPanel = new JPanel();
        extensionPanel.setLayout(new BorderLayout());

        topPanel.setLayout(new GridBagLayout());

        labelAlias = new JLabel();
        rbDefault = new JRadioButton();
        rbDefault.setToolTipText("Set default catalog");
        rbDefault.addActionListener(l ->
        {
            if (rbDefault.isSelected()
                    && fireEvents)
            {
                defaultCatalogChangedAction.accept(labelAlias.getText());
            }
        });
        defaultGroup.add(rbDefault);

        btnConfig = new JButton(Constants.COG);
        btnConfig.addActionListener(l -> showConfigAction.accept(extension.getConfigurableClass()));
        btnConfig.setEnabled(extension.getConfigurableClass() != null);
        // CSOFF
        topPanel.add(rbDefault, new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.NONE, new Insets(0, 0, 5, 0), 0, 0));
        topPanel.add(labelAlias, new GridBagConstraints(1, 0, 1, 0, 1, 0, GridBagConstraints.BELOW_BASELINE_LEADING, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
        topPanel.add(btnConfig, new GridBagConstraints(2, 0, 1, 0, 0, 0, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 5, 0), 0, -4));
        // CSON
        add(topPanel, BorderLayout.NORTH);
        add(extensionPanel, BorderLayout.CENTER);

        Component propertiesComponent = extension.getQuickPropertiesComponent();
        if (propertiesComponent != null)
        {
            extensionPanel.add(propertiesComponent, BorderLayout.CENTER);
        }
    }

    /** Init view from QueryFileModel */
    void init(QueryFileModel model)
    {
        fireEvents = false;
        for (Pair<ICatalogExtension, CatalogExtensionModel> p : model.getCatalogExtensions())
        {
            if (p.getKey() == extension)
            {
                CatalogExtensionModel extensionModel = p.getValue();
                labelAlias.setText(extensionModel.getAlias());
                // Update extension UI from query session
                extension.update(extensionModel.getAlias(), model.getQuerySession());
                fireEvents = true;
                break;
            }
        }

        if (Objects.equals(model.getQuerySession()
                .getDefaultCatalogAlias(), labelAlias.getText()))
        {
            rbDefault.setSelected(true);
        }

    }
}
