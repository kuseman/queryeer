package com.queryeer.visualization.graph;

import java.awt.BorderLayout;

import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import com.queryeer.Constants;
import com.queryeer.api.component.DialogUtils;
import com.queryeer.api.extensions.Inject;
import com.queryeer.api.extensions.visualization.graph.Graph;
import com.queryeer.api.service.IGraphVisualizationService;

/** Implementation of {@link IGraphVisualizationService} that opens a non-modal dialog containing a {@link GraphPanel}. */
@Inject
public class GraphVisualizationService implements IGraphVisualizationService
{
    @Override
    public void showGraph(Graph graph)
    {
        if (SwingUtilities.isEventDispatchThread())
        {
            openDialog(graph);
        }
        else
        {
            SwingUtilities.invokeLater(() -> openDialog(graph));
        }
    }

    private void openDialog(Graph graph)
    {
        DialogUtils.AFrame dialog = new DialogUtils.AFrame();
        dialog.setTitle(graph.title());
        dialog.setLayout(new BorderLayout());
        dialog.add(new GraphPanel(graph), BorderLayout.CENTER);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.pack();
        dialog.setSize(Constants.DEFAULT_DIALOG_SIZE);
        dialog.setVisible(true);
    }
}
