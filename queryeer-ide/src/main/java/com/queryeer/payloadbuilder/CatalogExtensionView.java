package com.queryeer.payloadbuilder;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import com.queryeer.Constants;
import com.queryeer.api.IQueryFile;
import com.queryeer.api.event.ShowOptionsEvent;
import com.queryeer.api.extensions.payloadbuilder.ICatalogExtension;
import com.queryeer.api.extensions.payloadbuilder.ICatalogExtensionView;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.IQueryFileProvider;

/** View for extension */
class CatalogExtensionView extends JPanel
{
    private final IQueryFileProvider queryFileProvider;
    private final JLabel labelAlias;
    private final JRadioButton rbDefault;
    private final JButton btnConfig;
    private final JPanel extensionPanel;
    private boolean fireEvents = true;

    CatalogExtensionView(IEventBus eventBus, IQueryFileProvider queryFileProvider, ICatalogExtension extension, String alias, ButtonGroup defaultGroup)
    {
        this.queryFileProvider = queryFileProvider;
        setBorder(BorderFactory.createTitledBorder(extension.getTitle()));
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel();
        extensionPanel = new JPanel();
        extensionPanel.setLayout(new BorderLayout());

        topPanel.setLayout(new GridBagLayout());

        labelAlias = new JLabel(alias);
        rbDefault = new JRadioButton();
        rbDefault.setToolTipText("Set default catalog");
        rbDefault.addActionListener(l ->
        {
            if (rbDefault.isSelected()
                    && fireEvents)
            {
                IQueryFile file = queryFileProvider.getCurrentFile();
                if (file != null)
                {
                    PayloadbuilderState state = file.getEngineState();
                    if (state != null)
                    {
                        state.getQuerySession()
                                .setDefaultCatalogAlias(labelAlias.getText());
                    }

                }
            }
        });
        defaultGroup.add(rbDefault);

        btnConfig = new JButton(Constants.COG);
        btnConfig.addActionListener(l -> eventBus.publish(new ShowOptionsEvent(extension.getConfigurableClass())));
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

        eventBus.register(this);
    }

    void afterExecute(IQueryFile queryFile)
    {
        selectDefaultAlias(queryFile);

        for (Component component : extensionPanel.getComponents())
        {
            if (component instanceof ICatalogExtensionView)
            {
                ((ICatalogExtensionView) component).afterExecute(queryFile);
            }
        }
    }

    void focus(IQueryFile queryFile)
    {
        selectDefaultAlias(queryFile);

        for (Component component : extensionPanel.getComponents())
        {
            if (component instanceof ICatalogExtensionView)
            {
                ((ICatalogExtensionView) component).focus(queryFile);
            }
        }
    }

    private void selectDefaultAlias(IQueryFile queryFile)
    {
        if (queryFileProvider.getCurrentFile() == queryFile)
        {
            PayloadbuilderState state = queryFile.getEngineState();
            if (state != null)
            {
                if (Objects.equals(state.getQuerySession()
                        .getDefaultCatalogAlias(), labelAlias.getText()))
                {
                    fireEvents = false;
                    rbDefault.setSelected(true);
                    fireEvents = true;
                }
            }
        }
    }
}
