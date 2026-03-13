package com.queryeer.editor;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;

import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.LayeredHighlighter;

/**
 * Paints a secondary caret as a 2-pixel-wide vertical line at the left edge of the character cell. Implements {@link LayeredHighlighter.LayerPainter} so that RSTA's {@code RTextAreaHighlighter}
 * invokes it at the correct layer.
 */
class SecondaryCaretPainter extends LayeredHighlighter.LayerPainter
{
    private final Color color;

    SecondaryCaretPainter(Color color)
    {
        this.color = color;
    }

    @Override
    public void paint(Graphics g, int offs0, int offs1, Shape bounds, JTextComponent c)
    {
        paintVerticalLine(g, offs0, c);
    }

    @Override
    public Shape paintLayer(Graphics g, int offs0, int offs1, Shape bounds, JTextComponent c, javax.swing.text.View view)
    {
        paintVerticalLine(g, offs0, c);
        return bounds;
    }

    private void paintVerticalLine(Graphics g, int offs, JTextComponent c)
    {
        try
        {
            Rectangle2D r = c.modelToView2D(offs);
            if (r == null)
            {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            int x = (int) r.getX();
            int y = (int) r.getY();
            int h = (int) r.getHeight();
            if (h <= 0)
            {
                h = c.getFontMetrics(c.getFont())
                        .getHeight();
            }
            g2.fillRect(x, y, 2, h);
            g2.dispose();
        }
        catch (BadLocationException e)
        {
            // Swallow
        }
    }
}
