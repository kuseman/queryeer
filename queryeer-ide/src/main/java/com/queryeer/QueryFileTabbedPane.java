package com.queryeer;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;

import org.apache.commons.io.FilenameUtils;

import com.queryeer.api.service.IEventBus;
import com.queryeer.component.TabComponent;
import com.queryeer.event.QueryFileClosingEvent;

/** Tabbed pane for query files */
class QueryFileTabbedPane extends JTabbedPane
{
    private final QueryeerModel queryeerModel;
    private final IEventBus eventBus;
    private final QueryFileViewFactory queryFileViewFactory;
    private final QueryeerModelListener queryeerModelListener = new QueryeerModelListener();

    private boolean fireChangeEvent = true;

    QueryFileTabbedPane(QueryeerModel queryeerModel, IEventBus eventBus, QueryFileViewFactory queryFileViewFactory)
    {
        super(SwingConstants.TOP);
        this.queryeerModel = requireNonNull(queryeerModel, "queryeerModel");
        this.eventBus = requireNonNull(eventBus, "eventBus");
        this.queryFileViewFactory = requireNonNull(queryFileViewFactory, "queryFileViewFactory");
        this.queryeerModel.addPropertyChangeListener(queryeerModelListener);

        setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        addChangeListener(l ->
        {
            if (!fireChangeEvent)
            {
                return;
            }

            int index = getSelectedIndex();
            if (index >= 0)
            {
                QueryFileTabComponent component = (QueryFileTabComponent) getTabComponentAt(index);
                if (component != null)
                {
                    queryeerModel.setSelectedFile(component.file);
                }
            }
        });
    }

    private class QueryeerModelListener implements PropertyChangeListener
    {
        @Override
        public void propertyChange(PropertyChangeEvent evt)
        {
            if (QueryeerModel.FILES.equals(evt.getPropertyName()))
            {
                // Remove tab
                if (evt.getNewValue() == null)
                {
                    int index = getTabComponentIndex((QueryFileModel) evt.getOldValue());
                    if (index == -1)
                    {
                        return;
                    }
                    QueryFileView view = (QueryFileView) getComponentAt(index);
                    QueryFileTabComponent component = (QueryFileTabComponent) getTabComponentAt(index);
                    component.file.removePropertyChangeListener(component);
                    view.close();

                    remove(index);
                }
                // New tab
                else
                {
                    final QueryFileModel file = (QueryFileModel) evt.getNewValue();

                    // QueryFileView factory
                    QueryFileView view = queryFileViewFactory.create(file);
                    QueryFileTabComponent component = new QueryFileTabComponent(file, () -> eventBus.publish(new QueryFileClosingEvent(view)));
                    add(view);
                    int index = QueryFileTabbedPane.this.getTabCount() - 1;
                    setTabComponentAt(index, component);
                    setToolTipTextAt(index, file.getFile()
                            .getAbsolutePath());
                    view.requestFocusInWindow();
                }
            }
            else if (QueryeerModel.SELECTED_FILE.equals(evt.getPropertyName()))
            {
                int index = getTabComponentIndex((QueryFileModel) evt.getNewValue());
                if (index != -1)
                {
                    fireChangeEvent = false;
                    QueryFileTabbedPane.this.setSelectedIndex(index);
                    fireChangeEvent = true;
                }
            }
            else if (QueryeerModel.ORDER.equalsIgnoreCase(evt.getPropertyName()))
            {
                fireChangeEvent = false;
                int size = getTabCount();
                Map<QueryFileModel, Tab> tabs = new HashMap<>(size);
                // CSOFF
                QueryFileView selected = (QueryFileView) getSelectedComponent();
                // CSON
                for (int i = 0; i < size; i++)
                {
                    QueryFileView view = (QueryFileView) getComponentAt(i);
                    QueryFileTabComponent component = (QueryFileTabComponent) getTabComponentAt(i);
                    tabs.put(view.getModel(), new Tab(component, getToolTipTextAt(i), view));
                }

                removeAll();

                for (QueryFileModel fileModel : queryeerModel.getFiles())
                {
                    Tab tab = tabs.get(fileModel);
                    add(tab.fileView);
                    int index = QueryFileTabbedPane.this.getTabCount() - 1;
                    setTabComponentAt(index, tab.tabComponent);
                    setToolTipTextAt(index, tab.toolTip);
                }

                setSelectedComponent(selected);
                selected.requestFocusInWindow();

                fireChangeEvent = true;
            }
        }
    }

    private record Tab(QueryFileTabComponent tabComponent, String toolTip, QueryFileView fileView)
    {
    }

    private int getTabComponentIndex(QueryFileModel file)
    {
        for (int i = 0; i < getTabCount(); i++)
        {
            QueryFileTabComponent component = (QueryFileTabComponent) getTabComponentAt(i);
            if (component.file == file)
            {
                return i;
            }
        }
        return -1;
    }

    QueryFileView getFileView(QueryFileModel file)
    {
        for (int i = 0; i < getTabCount(); i++)
        {
            QueryFileTabComponent component = (QueryFileTabComponent) getTabComponentAt(i);
            if (component.file == file)
            {
                return (QueryFileView) getComponentAt(i);
            }
        }
        return null;
    }

    /** View for tab header component */
    private class QueryFileTabComponent extends TabComponent implements PropertyChangeListener
    {
        private final QueryFileModel file;

        QueryFileTabComponent(QueryFileModel file, Runnable closeAction)
        {
            super(getTabTitle(file), file.getQueryEngine()
                    .getIcon(), closeAction);
            this.file = file;
            this.file.addPropertyChangeListener(this);
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt)
        {
            setTitle(getTabTitle(file));
            setIcon(file.getQueryEngine()
                    .getIcon());
            for (int i = 0; i < QueryFileTabbedPane.this.getTabCount(); i++)
            {
                if (QueryFileTabbedPane.this.getTabComponentAt(i) == this)
                {
                    String tooltip = file.getTooltip();
                    QueryFileTabbedPane.this.setToolTipTextAt(i, tooltip);
                    break;
                }
            }
        }

        private static String getTabTitle(QueryFileModel queryFile)
        {
            String filename = FilenameUtils.getName(queryFile.getFile()
                    .getAbsolutePath());
            StringBuilder sb = new StringBuilder();
            if (queryFile.isDirty())
            {
                sb.append("*");
            }

            if (!isBlank(queryFile.getMetaData()
                    .getSessionId()))
            {
                sb.append(" (")
                        .append(queryFile.getMetaData()
                                .getSessionId())
                        .append(") ");
            }

            sb.append(filename);
            if (queryFile.getState()
                    .isExecuting())
            {
                sb.append(" Executing ...");
            }
            return sb.toString();
        }
    }
}
