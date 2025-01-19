package com.queryeer;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.beans.IndexedPropertyChangeEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.OverlayLayout;
import javax.swing.Timer;

import org.apache.commons.lang3.time.DurationFormatUtils;

import com.queryeer.QueryFileModel.State;
import com.queryeer.api.extensions.IExtensionAction;
import com.queryeer.api.extensions.output.IOutputComponent;
import com.queryeer.api.extensions.output.IOutputToolbarActionFactory;
import com.queryeer.component.TabComponent;

/** Content of a query editor. Engine editor and a result panel separated with a split panel */
class QueryFileView extends JPanel
{
    private final QueryFileModel model;
    private final JSplitPane splitPane;
    private final JTabbedPane resultTabs;
    private final List<IOutputToolbarActionFactory> toolbarActionFactories;
    private final JLabel labelRunTime;
    private final JLabel labelRowCount;
    private final JLabel labelExecutionStatus;
    private final JLabel fileStatus;

    private final Timer executionTimer;
    private final JToolBar outputComponentToolbar = new JToolBar();
    private final QueryFileChangeListener queryFileChangeListener = new QueryFileChangeListener();
    private boolean resultCollapsed;
    private int prevDividerLocation;
    private boolean fireEvents = true;

    QueryFileView(QueryFileModel model, List<IOutputToolbarActionFactory> toolbarActionFactories)
    {
        this.model = model;
        this.toolbarActionFactories = requireNonNull(toolbarActionFactories, "toolbarActionFactories");

        setLayout(new BorderLayout());
        JPanel tabPanel = new JPanel();
        tabPanel.setLayout(new OverlayLayout(tabPanel));

        resultTabs = new JTabbedPane();
        resultTabs.setAlignmentX(1.0f);
        resultTabs.setAlignmentY(0.0f);

        FlowLayout toolBarLayout = new FlowLayout(FlowLayout.TRAILING, 0, 0);
        toolBarLayout.setAlignOnBaseline(true);

        outputComponentToolbar.setMaximumSize(new Dimension(500, 30));
        outputComponentToolbar.setLayout(toolBarLayout);
        outputComponentToolbar.setFloatable(false);
        outputComponentToolbar.setOpaque(false);
        outputComponentToolbar.setAlignmentX(1.0f);
        outputComponentToolbar.setAlignmentY(0.0f);

        tabPanel.add(outputComponentToolbar);
        tabPanel.add(resultTabs);

        // Add initial output components
        int index = 0;
        for (IOutputComponent outputComponent : model.getOutputComponents())
        {
            resultTabs.add(outputComponent.getComponent());
            resultTabs.setTabComponentAt(index, new OutputComponentTabComponent(outputComponent));
            index++;
        }

        // Populate toolbar
        if (index > 0)
        {
            ((OutputComponentTabComponent) resultTabs.getTabComponentAt(0)).populateToolbar();
        }

        resultTabs.addChangeListener(l -> outputComponentChanged());

        splitPane = new JSplitPane();
        splitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
        splitPane.setDividerLocation(400);
        splitPane.setLeftComponent(model.getEditor()
                .getComponent());
        splitPane.setRightComponent(tabPanel);
        add(splitPane, BorderLayout.CENTER);

        JPanel panelStatus = new JPanel();
        panelStatus.setPreferredSize(new Dimension(10, 20));
        add(panelStatus, BorderLayout.SOUTH);
        panelStatus.setLayout(new GridBagLayout());
        // CSON
        labelExecutionStatus = new JLabel(getIconFromState(model.getState()));

        labelRunTime = new JLabel();
        labelRunTime.setToolTipText("Last Query Run Time");
        labelRowCount = new JLabel();
        labelRowCount.setToolTipText("Last Query Row Count");
        fileStatus = new JLabel();

        panelStatus.add(labelExecutionStatus, new GridBagConstraints(0, 0, 1, 1, 0.0d, 0.0d, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 3, 0));
        panelStatus.add(new JLabel("Time:"), new GridBagConstraints(1, 0, 1, 1, 0.0d, 0.0d, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 3, 0));
        panelStatus.add(labelRunTime, new GridBagConstraints(2, 0, 1, 1, 0.0d, 0.0d, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 3, 0));
        panelStatus.add(new JLabel("Rows:"), new GridBagConstraints(3, 0, 1, 1, 0.0d, 0.0d, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 3, 0));
        panelStatus.add(labelRowCount, new GridBagConstraints(4, 0, 1, 1, 0.0d, 0.0d, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 3, 0));
        panelStatus.add(new JLabel(), new GridBagConstraints(5, 0, 1, 1, 1.0d, 0.0d, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 3, 0));
        panelStatus.add(fileStatus, new GridBagConstraints(6, 0, 1, 1, 0.0d, 0.0d, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 3, 0));

        executionTimer = new Timer(100, l -> setExecutionStats());

        model.addPropertyChangeListener(queryFileChangeListener);
    }

    private void outputComponentChanged()
    {
        if (!fireEvents)
        {
            return;
        }

        OutputComponentTabComponent tabComponent = (OutputComponentTabComponent) resultTabs.getTabComponentAt(resultTabs.getSelectedIndex());
        if (tabComponent != null)
        {
            model.selectOutputComponent(tabComponent.outputComponent.getClass());
        }
    }

    private void handleStateChanged(QueryFileModel file)
    {
        State state = file.getState();
        labelExecutionStatus.setIcon(getIconFromState(state));
        labelExecutionStatus.setToolTipText(state.getToolTip());

        if (state.isExecuting())
        {
            executionTimer.start();
        }
        else
        {
            setExecutionStats();
            executionTimer.stop();
        }
    }

    void close()
    {
        model.removePropertyChangeListener(queryFileChangeListener);
    }

    private Icon getIconFromState(State state)
    {
        // CSOFF
        switch (state)
        // CSOn
        {
            case ABORTED:
                return Constants.CLOSE_ICON;
            case EXECUTING:
            case EXECUTING_BY_EVENT:
                return Constants.PLAY_ICON;
            case COMPLETED:
                return Constants.CHECK_CIRCLE_ICON;
            case ERROR:
                return Constants.WARNING_ICON;
        }

        return null;
    }

    private void setExecutionStats()
    {
        labelRunTime.setText(DurationFormatUtils.formatDurationHMS(model.getExecutionTime()));
        labelRowCount.setText(String.valueOf(model.getTotalRowCount()));
    }

    void toggleResultPane()
    {
        // Expanded
        if (!resultCollapsed)
        {
            prevDividerLocation = splitPane.getDividerLocation();
            splitPane.setDividerLocation(1.0d);
            resultCollapsed = true;
        }
        else
        {
            splitPane.setDividerLocation(prevDividerLocation);
            resultCollapsed = false;
        }
    }

    QueryFileModel getModel()
    {
        return model;
    }

    @Override
    public boolean requestFocusInWindow()
    {
        model.getEditor()
                .focused();
        return true;
    }

    /** Model listener */
    private class QueryFileChangeListener implements PropertyChangeListener
    {
        @Override
        public void propertyChange(PropertyChangeEvent evt)
        {
            if (QueryFileModel.STATE.equals(evt.getPropertyName()))
            {
                handleStateChanged(model);
            }
            else if (QueryFileModel.METADATA.equals(evt.getPropertyName()))
            {
                fileStatus.setText(model.getMetaData()
                        .getDescription());
            }
            else if (QueryFileModel.OUTPUT_COMPONENTS.equals(evt.getPropertyName()))
            {
                int index = evt instanceof IndexedPropertyChangeEvent ievt ? ievt.getIndex()
                        : resultTabs.getTabCount();

                IOutputComponent outputComponent = (IOutputComponent) evt.getNewValue();
                resultTabs.add(outputComponent.getComponent(), index);
                resultTabs.setTabComponentAt(index, new OutputComponentTabComponent(outputComponent));
            }
            else if (QueryFileModel.SELECTED_OUTPUT_COMPONENT.equals(evt.getPropertyName()))
            {
                IOutputComponent selectedOutputComponent = model.getSelectedOutputComponent();
                int index = model.getOutputComponents()
                        .indexOf(selectedOutputComponent);
                if (index >= 0
                        && index < resultTabs.getTabCount())
                {
                    OutputComponentTabComponent tab = (OutputComponentTabComponent) resultTabs.getTabComponentAt(index);
                    fireEvents = false;
                    resultTabs.setSelectedIndex(index);
                    tab.populateToolbar();
                    fireEvents = true;
                }
            }
        }
    }

    private class OutputComponentTabComponent extends TabComponent
    {
        private IOutputComponent outputComponent;
        private List<JButton> toolbarActions;

        OutputComponentTabComponent(IOutputComponent outputComponent)
        {
            super(outputComponent.title(), outputComponent.icon());
            this.outputComponent = outputComponent;
        }

        void populateToolbar()
        {
            outputComponentToolbar.removeAll();
            if (toolbarActions == null)
            {
                toolbarActions = toolbarActionFactories.stream()
                        .map(f -> f.create(outputComponent))
                        .filter(Objects::nonNull)
                        .flatMap(l -> l.stream())
                        .sorted(Comparator.comparingInt(IExtensionAction::order))
                        .map(a -> new JButton(a.getAction()))
                        .collect(toList());
            }
            for (JButton button : toolbarActions)
            {
                outputComponentToolbar.add(button);
            }
        }
    }
}
