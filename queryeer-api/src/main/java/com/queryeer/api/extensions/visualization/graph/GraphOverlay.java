package com.queryeer.api.extensions.visualization.graph;

import javax.swing.ImageIcon;

/**
 * An overlay icon rendered on a graph vertex.
 *
 * @param icon The icon to render
 * @param horizontalAlign Horizontal alignment, use {@code com.mxgraph.util.mxConstants#ALIGN_LEFT/CENTER/RIGHT}
 * @param verticalAlign Vertical alignment, use {@code com.mxgraph.util.mxConstants#ALIGN_TOP/MIDDLE/BOTTOM}
 */
public record GraphOverlay(ImageIcon icon, String horizontalAlign, String verticalAlign)
{
}
