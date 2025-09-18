package se.kuseman.payloadbuilder.catalog.jdbc;

import static java.util.Objects.requireNonNull;

import java.awt.event.ActionEvent;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;

import com.queryeer.api.editor.ITextEditorDocumentParser;
import com.queryeer.api.editor.ITextEditorKit;
import com.queryeer.api.event.ExecuteQueryEvent;
import com.queryeer.api.event.ExecuteQueryEvent.OutputType;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.IIconFactory;
import com.queryeer.api.service.IIconFactory.Provider;

/** Text editor kit for jdbc query engines */
class JdbcTextEditorKit implements ITextEditorKit
{
    private final JdbcEngineState state;
    private final Action showEstimatedQueryPlanAction;
    private final Action includeQueryPlanAction;
    private final IEventBus eventBus;

    JdbcTextEditorKit(JdbcEngineState state, IIconFactory iconFactory, IEventBus eventBus)
    {
        this.state = requireNonNull(state);
        this.showEstimatedQueryPlanAction = createShowEstimatedQueryPlanAction(iconFactory);
        this.includeQueryPlanAction = createIncludeQueryPlanAction(iconFactory);
        this.eventBus = requireNonNull(eventBus);

        state.addChangeListener(this::updateActionStatuses);
    }

    void updateActionStatuses()
    {
        includeQueryPlanAction.setEnabled(state.connectionState != null
                && state.connectionState.getjdbcDialect()
                        .supportsIncludeQueryPlanAction() ? true
                                : false);

        showEstimatedQueryPlanAction.setEnabled(state.connectionState != null
                && state.connectionState.getjdbcDialect()
                        .supportsShowEstimatedQueryPlanAction() ? true
                                : false);
    }

    private Action createShowEstimatedQueryPlanAction(IIconFactory iconFactory)
    {
        return new AbstractAction("", iconFactory.getIcon(Provider.FONTAWESOME, "OBJECT_GROUP"))
        {
            {
                {
                    putValue(com.queryeer.api.action.Constants.ACTION_SHOW_IN_TOOLBAR, true);
                    // putValue(com.queryeer.api.action.Constants.ACTION_TOGGLE, true);
                    putValue(com.queryeer.api.action.Constants.ACTION_ORDER, 9);
                    putValue(Action.SHORT_DESCRIPTION, "Show Estimated Query Plan");

                    setEnabled(false);
                }
            }

            @Override
            public void actionPerformed(ActionEvent e)
            {
                state.estimateQueryPlan = true;
                eventBus.publish(new ExecuteQueryEvent(OutputType.QUERY_PLAN, new ExecuteQueryContext(null)));
            }
        };
    }

    private Action createIncludeQueryPlanAction(IIconFactory iconFactory)
    {
        return new AbstractAction("", iconFactory.getIcon(Provider.FONTAWESOME, "OBJECT_UNGROUP"))
        {
            {
                {
                    putValue(com.queryeer.api.action.Constants.ACTION_SHOW_IN_TOOLBAR, true);
                    putValue(com.queryeer.api.action.Constants.ACTION_TOGGLE, true);
                    putValue(com.queryeer.api.action.Constants.ACTION_ORDER, 9);
                    putValue(Action.SHORT_DESCRIPTION, "Include Query Plan");

                    setEnabled(false);
                }
            }

            @Override
            public void actionPerformed(ActionEvent e)
            {
                state.includeQueryPlan = !state.includeQueryPlan;
            }
        };
    }

    @Override
    public String getSyntaxMimeType()
    {
        return JdbcQueryEngine.TEXT_SQL;
    }

    @Override
    public List<Action> getActions()
    {
        return List.of(includeQueryPlanAction, showEstimatedQueryPlanAction);
    }

    @Override
    public ITextEditorDocumentParser getDocumentParser()
    {
        return state.documentParser;
    }
}