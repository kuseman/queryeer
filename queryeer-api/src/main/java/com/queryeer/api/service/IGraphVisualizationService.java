package com.queryeer.api.service;

import com.queryeer.api.extensions.visualization.graph.Graph;

/** Service for ad-hoc visualization of graph data using an interactive dialog. */
public interface IGraphVisualizationService
{
    /** Show the graph in a non-modal dialog centered on screen. */
    void showGraph(Graph graph);
}
