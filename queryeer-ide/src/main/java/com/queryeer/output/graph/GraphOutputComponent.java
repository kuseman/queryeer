package com.queryeer.output.graph;

import static java.util.Objects.requireNonNull;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;

import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.kordamp.ikonli.fontawesome.FontAwesome;

import com.queryeer.IconFactory;
import com.queryeer.api.IQueryFile;
import com.queryeer.api.extensions.output.IOutputComponent;
import com.queryeer.api.extensions.output.IOutputExtension;

class GraphOutputComponent implements IOutputComponent
{
    private final IQueryFile queryFile;
    private final IOutputExtension extension;
    private final ChartComponent component;

    GraphOutputComponent(IOutputExtension extension, IQueryFile queryFile)
    {
        this.queryFile = requireNonNull(queryFile);
        this.extension = requireNonNull(extension, "extension");
        this.component = new ChartComponent(queryFile);
    }

    @Override
    public String title()
    {
        return "Graph";
    }

    @Override
    public Icon icon()
    {
        return IconFactory.of(FontAwesome.BAR_CHART);
    }

    @Override
    public IOutputExtension getExtension()
    {
        return extension;
    }

    @Override
    public boolean active()
    {
        return component.active.isSelected();
    }

    @Override
    public Component getComponent()
    {
        return component;
    }

    @Override
    public void clearState()
    {
        component.graph.clearState();
    }

    void addRow(int rowNumber, List<String> rowColumns, List<Object> rowValues)
    {
        if (component.autoSelect.isSelected()
                && !(GraphOutputComponent.class.isAssignableFrom(queryFile.getSelectedOutputComponent()
                        .getClass())))
        {
            queryFile.selectOutputComponent(GraphOutputComponent.class);
        }
        component.graph.addRow(rowNumber, rowColumns, rowValues);
    }

    void endResult()
    {
        component.graph.endResult();
    }

    private class ChartComponent extends JPanel
    {
        private final JCheckBox active;
        private final JCheckBox autoSelect;

        private final GraphComponent graph;

        ChartComponent(IQueryFile queryFile)
        {
            super(new GridBagLayout());

            active = new JCheckBox();
            active.setSelected(false);

            autoSelect = new JCheckBox();
            autoSelect.setSelected(false);
            autoSelect.setToolTipText("If selected then Graph tab will be auto selected upon query execution.");

            add(active, new GridBagConstraints(0, 0, 1, 1, 0.0d, 0.0d, GridBagConstraints.BASELINE, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 3, 0));
            add(new JLabel("Enable"), new GridBagConstraints(1, 0, 1, 1, 0.0d, 0.0d, GridBagConstraints.BASELINE, GridBagConstraints.NONE, new Insets(3, 0, 0, 0), 10, 0));

            add(autoSelect, new GridBagConstraints(2, 0, 1, 1, 0.0d, 0.0d, GridBagConstraints.BASELINE, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 3, 0));
            add(new JLabel("Auto Select Tab"), new GridBagConstraints(3, 0, 1, 1, 0.0d, 0.0d, GridBagConstraints.BASELINE, GridBagConstraints.HORIZONTAL, new Insets(3, 0, 0, 0), 10, 0));

            graph = new GraphComponent(queryFile);
            add(graph, new GridBagConstraints(0, 1, 4, 1, 1.0d, 1.0d, GridBagConstraints.BASELINE, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
        }
    }
}
