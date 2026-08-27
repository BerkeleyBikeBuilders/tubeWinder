package com.berkeleybikebuilders.tubewinder.ui;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/**
 * Toolbar icons drawn with Java2D rather than loaded from files.
 *
 * <p>Same approach as UGS's own {@code VectorIcon}: no image assets to ship, sharp at any scale, and
 * the colour can follow the enabled state of the button. Each icon is painted into a 16x16 box that
 * is scaled to whatever size is asked for, so the geometry below is all in 0..16 coordinates.
 */
final class Icons {

    static final Color ACCENT = new Color(0x1E88E5);
    static final Color GO = new Color(0x2E7D32);
    static final Color WARN = new Color(0xE65100);
    static final Color STOP = new Color(0xC62828);
    static final Color NEUTRAL = new Color(0x424242);

    private Icons() {
    }

    /** Paints into a 16x16 box. {@code fg} follows the enabled state; {@code bg} punches cutouts. */
    private interface Painter {
        void paint(Graphics2D g, Color fg, Color bg);
    }

    static Icon gear(int size) {
        return icon(size, NEUTRAL, (g, c, bg) -> {
            double cx = 8, cy = 8, outer = 7.0, inner = 4.6;
            Path2D teeth = new Path2D.Double();
            for (int i = 0; i < 8; i++) {
                double a0 = Math.toRadians(i * 45 - 11);
                double a1 = Math.toRadians(i * 45 + 11);
                if (i == 0) {
                    teeth.moveTo(cx + outer * Math.cos(a0), cy + outer * Math.sin(a0));
                } else {
                    teeth.lineTo(cx + outer * Math.cos(a0), cy + outer * Math.sin(a0));
                }
                teeth.lineTo(cx + outer * Math.cos(a1), cy + outer * Math.sin(a1));
                double b = Math.toRadians(i * 45 + 33);
                teeth.lineTo(cx + inner * Math.cos(b), cy + inner * Math.sin(b));
                double b2 = Math.toRadians((i + 1) * 45 - 33);
                teeth.lineTo(cx + inner * Math.cos(b2), cy + inner * Math.sin(b2));
            }
            teeth.closePath();
            g.setColor(c);
            g.fill(teeth);
            g.setColor(bg);
            g.fill(new Ellipse2D.Double(cx - 2.2, cy - 2.2, 4.4, 4.4));
        });
    }

    static Icon play(int size) {
        return icon(size, GO, (g, c, bg) -> {
            Path2D p = new Path2D.Double();
            p.moveTo(4, 2.5);
            p.lineTo(13, 8);
            p.lineTo(4, 13.5);
            p.closePath();
            g.setColor(c);
            g.fill(p);
        });
    }

    static Icon pause(int size) {
        return icon(size, WARN, (g, c, bg) -> {
            g.setColor(c);
            g.fill(new Rectangle2D.Double(4, 3, 3, 10));
            g.fill(new Rectangle2D.Double(9, 3, 3, 10));
        });
    }

    static Icon stop(int size) {
        return icon(size, STOP, (g, c, bg) -> {
            g.setColor(c);
            g.fill(new Rectangle2D.Double(3.5, 3.5, 9, 9));
        });
    }

    static Icon connect(int size) {
        return icon(size, ACCENT, (g, c, bg) -> {
            g.setColor(c);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Path2D.Double(new java.awt.geom.Line2D.Double(5, 2, 5, 6)));
            g.draw(new Path2D.Double(new java.awt.geom.Line2D.Double(11, 2, 11, 6)));
            g.fill(new Rectangle2D.Double(3.5, 6, 9, 4));
            g.fill(new Rectangle2D.Double(7, 10, 2, 4));
        });
    }

    static Icon disconnect(int size) {
        return icon(size, STOP, (g, c, bg) -> {
            g.setColor(c);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.fill(new Rectangle2D.Double(3.5, 6, 9, 4));
            g.fill(new Rectangle2D.Double(7, 10, 2, 4));
            g.draw(new java.awt.geom.Line2D.Double(2.5, 2.5, 13.5, 13.5));
        });
    }

    static Icon home(int size) {
        return icon(size, NEUTRAL, (g, c, bg) -> {
            Path2D roof = new Path2D.Double();
            roof.moveTo(8, 2);
            roof.lineTo(14.5, 8);
            roof.lineTo(1.5, 8);
            roof.closePath();
            g.setColor(c);
            g.fill(roof);
            g.fill(new Rectangle2D.Double(3.5, 8, 9, 6));
            g.setColor(bg);
            g.fill(new Rectangle2D.Double(6.5, 10, 3, 4));
        });
    }

    static Icon zero(int size) {
        return icon(size, NEUTRAL, (g, c, bg) -> {
            g.setColor(c);
            g.setStroke(new BasicStroke(1.8f));
            g.draw(new Ellipse2D.Double(4, 2.5, 8, 11));
            g.draw(new java.awt.geom.Line2D.Double(5, 12.5, 11, 3.5));
        });
    }

    static Icon reset(int size) {
        return icon(size, WARN, (g, c, bg) -> {
            g.setColor(c);
            g.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new java.awt.geom.Arc2D.Double(2.5, 2.5, 11, 11, 60, 280, java.awt.geom.Arc2D.OPEN));
            Path2D head = new Path2D.Double();
            head.moveTo(8.5, 1.0);
            head.lineTo(12.5, 3.6);
            head.lineTo(8.2, 5.6);
            head.closePath();
            g.fill(head);
        });
    }

    static Icon unlock(int size) {
        return icon(size, ACCENT, (g, c, bg) -> {
            g.setColor(c);
            g.setStroke(new BasicStroke(1.8f));
            g.draw(new java.awt.geom.Arc2D.Double(5, 1.5, 8, 8, 20, 160, java.awt.geom.Arc2D.OPEN));
            g.fill(new Rectangle2D.Double(3, 7.5, 9, 7));
        });
    }


    /** Startup / alignment. A sun, because the machine is waking up and finding itself. */
    static Icon sun(int size) {
        return icon(size, new Color(0xF9A825), (g, c, bg) -> {
            g.setColor(c);
            g.fill(new Ellipse2D.Double(5.2, 5.2, 5.6, 5.6));
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < 8; i++) {
                double a = Math.toRadians(i * 45);
                double cos = Math.cos(a);
                double sin = Math.sin(a);
                g.draw(new java.awt.geom.Line2D.Double(
                        8 + 7.0 * cos, 8 + 7.0 * sin,
                        8 + 4.6 * cos, 8 + 4.6 * sin));
            }
        });
    }


    // ------------------------------------------------------------------

    private static Icon icon(int size, Color colour, Painter painter) {
        return new Icon() {
            @Override
            public void paintIcon(Component component, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                            RenderingHints.VALUE_STROKE_PURE);
                    g.translate(x, y);
                    double scale = size / 16.0;
                    g.scale(scale, scale);
                    boolean enabled = component == null || component.isEnabled();
                    Color fg = enabled
                            ? colour
                            : new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 90);
                    Color bg = component == null || component.getBackground() == null
                            ? Color.WHITE
                            : component.getBackground();
                    painter.paint(g, fg, bg);
                } finally {
                    g.dispose();
                }
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }
}
