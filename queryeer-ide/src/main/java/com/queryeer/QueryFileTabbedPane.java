package com.queryeer;

import static java.util.Objects.requireNonNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;

import org.apache.commons.io.FilenameUtils;

import com.queryeer.QueryFileModel.State;
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
        setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

        this.queryeerModel = requireNonNull(queryeerModel, "queryeerModel");
        this.eventBus = requireNonNull(eventBus, "eventBus");
        this.queryFileViewFactory = requireNonNull(queryFileViewFactory, "queryFileViewFactory");
        this.queryeerModel.addPropertyChangeListener(queryeerModelListener);

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
                    setToolTipTextAt(index, file.getFilename());
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
        }
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

    String getTabTitle(QueryFileModel queryFile)
    {
        String filename = FilenameUtils.getName(queryFile.getFilename());
        StringBuilder sb = new StringBuilder();
        if (queryFile.isDirty())
        {
            sb.append("*");
        }
        sb.append(filename);
        if (queryFile.getState() == State.EXECUTING)
        {
            sb.append(" Executing ...");
        }
        return sb.toString();
    }

    /** View for tab header component */
    private class QueryFileTabComponent extends TabComponent implements PropertyChangeListener
    {
        private QueryFileModel file;

        QueryFileTabComponent(QueryFileModel file, Runnable closeAction)
        {
            super(getTabTitle(file), null, closeAction);
            this.file = file;
            this.file.addPropertyChangeListener(this);
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt)
        {
            setTitle(getTabTitle(file));
            for (int i = 0; i < QueryFileTabbedPane.this.getTabCount(); i++)
            {
                if (QueryFileTabbedPane.this.getTabComponentAt(i) == this)
                {
                    QueryFileTabbedPane.this.setToolTipTextAt(i, file.getFilename());
                    break;
                }
            }
        }
    }
}
