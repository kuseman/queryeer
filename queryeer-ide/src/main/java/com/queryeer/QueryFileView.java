package com.queryeer;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.PrintWriter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.OverlayLayout;
import javax.swing.SwingConstants;
import javax.swing.Timer;

import org.apache.commons.lang3.time.DurationFormatUtils;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.IQueryFileState;
import com.queryeer.api.extensions.IExtensionAction;
import com.queryeer.api.extensions.output.IOutputComponent;
import com.queryeer.api.extensions.output.IOutputExtension;
import com.queryeer.api.extensions.output.IOutputFormatExtension;
import com.queryeer.api.extensions.output.IOutputToolbarActionFactory;
import com.queryeer.api.extensions.output.text.ITextOutputComponent;
import com.queryeer.api.service.IEventBus;
import com.queryeer.component.TabComponent;

/** Content of a query editor. Text editor and a result panel separated with a split panel */
class QueryFileView extends JPanel implements IQueryFile// , SearchListener
{
    private static final int START_DIVIDER_LOCATION = 400;
    // Queryprovider/editor specifics
    // private static final String GOTO = "GOTO";
    // private static final String REPLACE = "REPLACE";
    // private static final String FIND = "FIND";
    // private static final String PASTE_SPECIAL = "PASTE_SPECIAL";
    // private final TextEditorPane textEditor;
    // private final FindDialog findDialog;
    // private final ReplaceDialog replaceDialog;
    // private final GoToDialog gotoDialog;
    /** Reuse the same event to avoid creating new objects on every change */
    // private final CaretChangedEvent caretEvent;

    private final QueryFileModel file;
    private final JSplitPane splitPane;
    private final JTabbedPane resultTabs;
    private final List<IOutputComponent> outputComponents;
    private final List<IOutputToolbarActionFactory> toolbarActionFactories;
    private final JLabel labelRunTime;
    private final JLabel labelRowCount;
    private final JLabel labelExecutionStatus;
    private final Timer executionTimer;
    private final JToolBar outputComponentToolbar = new JToolBar();
    private final QueryFileChangeListener queryFileChangeListener = new QueryFileChangeListener();

    private boolean resultCollapsed;
    private int prevDividerLocation;

    QueryFileView(QueryFileModel file, IEventBus eventBus, List<IOutputComponent> outputComponents, List<IOutputToolbarActionFactory> toolbarActionFactories)
    {
        this.file = file;
        this.outputComponents = requireNonNull(outputComponents, "outputComponents");
        this.toolbarActionFactories = requireNonNull(toolbarActionFactories, "toolbarActionFactories");

        setLayout(new BorderLayout());
        JPanel tabPanel = new JPanel();
        tabPanel.setLayout(new OverlayLayout(tabPanel));

        resultTabs = new JTabbedPane();
        resultTabs.setAlignmentX(1.0f);
        resultTabs.setAlignmentY(0.0f);

        int index = 0;
        for (IOutputComponent ouputComponent : outputComponents)
        {
            resultTabs.add(ouputComponent.getComponent());
            resultTabs.setTabComponentAt(index, new OutputComponentTabComponent(ouputComponent));
            index++;
        }

        // Populate toolbar
        if (index > 0)
        {
            outputComponentChanged();
        }

        resultTabs.addChangeListener(l -> outputComponentChanged());

        outputComponentToolbar.setOpaque(false);
        outputComponentToolbar.setAlignmentX(1.0f);
        outputComponentToolbar.setAlignmentY(0.0f);

        tabPanel.add(outputComponentToolbar);
        tabPanel.add(resultTabs);

        splitPane = new JSplitPane();
        splitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
        splitPane.setDividerLocation(START_DIVIDER_LOCATION);
        splitPane.setLeftComponent(file.getQueryFileState()
                .getQueryEditorComponent()
                .getComponent());
        splitPane.setRightComponent(tabPanel);
        add(splitPane, BorderLayout.CENTER);

        JPanel panelStatus = new JPanel();
        panelStatus.setPreferredSize(new Dimension(10, 20));
        add(panelStatus, BorderLayout.SOUTH);
        panelStatus.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 0));
        // CSON
        labelExecutionStatus = new JLabel(getIconFromState(file.getExecutionState()));

        labelRunTime = new JLabel("", SwingConstants.LEFT);
        labelRunTime.setToolTipText("Last query run time");
        labelRowCount = new JLabel("", SwingConstants.LEFT);
        labelRowCount.setToolTipText("Last query row count");

        panelStatus.add(labelExecutionStatus);
        panelStatus.add(new JLabel("Time:"));
        panelStatus.add(labelRunTime);
        panelStatus.add(new JLabel("Rows:"));
        panelStatus.add(labelRowCount);

        executionTimer = new Timer(100, l -> setExecutionStats());

        file.addPropertyChangeListener(queryFileChangeListener);
    }

    /* IQueryFile */

    @Override
    public <T extends IQueryFileState> Optional<T> getQueryFileState(Class<T> clazz)
    {
        if (clazz.isAssignableFrom(file.getQueryFileState()
                .getClass()))
        {
            return Optional.of(clazz.cast(file.getQueryFileState()));
        }

        return Optional.empty();
    }

    @Override
    public ExecutionState getExecutionState()
    {
        return file.getExecutionState();
    }

    @Override
    public void setExecutionState(ExecutionState state)
    {
        file.setExecutionState(state);
    }

    @Override
    public String getFilename()
    {
        return file.getFilename();
    }

    @Override
    public void focusMessages()
    {
        int count = outputComponents.size();
        for (int i = 0; i < count; i++)
        {
            IOutputComponent component = outputComponents.get(i);
            if (ITextOutputComponent.class.isAssignableFrom(component.getClass()))
            {
                resultTabs.setSelectedIndex(i);
                return;
            }
        }
    }

    @Override
    public PrintWriter getMessagesWriter()
    {
        return getOutputComponent(ITextOutputComponent.class).getTextWriter();
    }

    @Override
    public <T extends IOutputComponent> T getOutputComponent(Class<T> clazz)
    {
        int count = outputComponents.size();
        for (int i = 0; i < count; i++)
        {
            IOutputComponent component = outputComponents.get(i);
            if (clazz.isAssignableFrom(component.getClass()))
            {
                return clazz.cast(component);
            }
        }

        throw new RuntimeException("No output component found with extension class " + clazz);
    }

    @Override
    public void incrementTotalRowCount()
    {
        file.incrementTotalRowCount();
    }

    @Override
    public IOutputExtension getOutput()
    {
        return file.getOutputExtension();
    }

    @Override
    public IOutputFormatExtension getOutputFormat()
    {
        return file.getOutputFormat();
    }

    @Override
    public void clearExecutionStats()
    {
        labelRunTime.setText("");
        labelRowCount.setText("");
    }

    /* END IQueryFile */

    @Override
    public boolean requestFocusInWindow()
    {
        // Delegate focus in window to the query editor component
        return file.getQueryFileState()
                .getQueryEditorComponent()
                .getComponent()
                .requestFocusInWindow();
    }

    void close()
    {
        file.close();
        file.removePropertyChangeListener(queryFileChangeListener);
    }

    QueryFileModel getFile()
    {
        return file;
    }

    private void outputComponentChanged()
    {
        OutputComponentTabComponent tabComponent = (OutputComponentTabComponent) resultTabs.getTabComponentAt(resultTabs.getSelectedIndex());
        tabComponent.populateToolbar();
    }

    private void handleStateChanged(QueryFileModel file, ExecutionState state)
    {
        labelExecutionStatus.setIcon(getIconFromState(state));
        labelExecutionStatus.setToolTipText(getTooltipFromState(state));

        // CSOFF
        switch (state)
        // CSON
        {
            case EXECUTING:
                IOutputExtension selectedOutputExtension = file.getOutputExtension();

                // Clear state of output components and select the one
                // that is choosen in toolbar
                int count = outputComponents.size();
                for (int i = 0; i < count; i++)
                {
                    IOutputComponent outputComponent = outputComponents.get(i);
                    outputComponent.clearState();
                    if (selectedOutputExtension != null
                            && selectedOutputExtension.getResultOutputComponentClass() != null
                            && outputComponent.getClass()
                                    .isAssignableFrom(selectedOutputExtension.getResultOutputComponentClass()))
                    {
                        resultTabs.setSelectedIndex(i);
                    }
                }

                executionTimer.start();
                // clearHighLights();

                break;
            case COMPLETED:
                setExecutionStats();
                executionTimer.stop();
                break;
            case ABORTED:
                setExecutionStats();
                getMessagesWriter().println("Query was aborted!");
                executionTimer.stop();
                break;
            case ERROR:
                focusMessages();
                break;
        }

        // No rows, then show messages
        if (state != ExecutionState.EXECUTING
                && file.getTotalRowCount() == 0)
        {
            focusMessages();
        }
    }

    private Icon getIconFromState(ExecutionState state)
    {
        // CSOFF
        switch (state)
        // CSOn
        {
            case ABORTED:
                return Constants.CLOSE_ICON;
            case EXECUTING:
                return Constants.PLAY_ICON;
            case COMPLETED:
                return Constants.CHECK_CIRCLE_ICON;
            case ERROR:
                return Constants.WARNING_ICON;
        }

        return null;
    }

    private String getTooltipFromState(ExecutionState state)
    {
        // CSOFF
        switch (state)
        // CSOn
        {
            case ABORTED:
                return "Aborted";
            case EXECUTING:
                return "Executing";
            case COMPLETED:
                return "Stopped";
            case ERROR:
                return "Error";
        }

        return null;
    }

    private void setExecutionStats()
    {
        labelRunTime.setText(DurationFormatUtils.formatDurationHMS(file.getExecutionTime()));
        labelRowCount.setText(String.valueOf(file.getTotalRowCount()));
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

    /** Model listener */
    private class QueryFileChangeListener implements PropertyChangeListener
    {
        @Override
        public void propertyChange(PropertyChangeEvent evt)
        {
            if (QueryFileModel.EXECUTION_STATE.equals(evt.getPropertyName()))
            {
                handleStateChanged(file, (ExecutionState) evt.getNewValue());
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
