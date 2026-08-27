package com.berkeleybikebuilders.tubewinder.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

/**
 * Thin, plain scroll bars.
 *
 * <p>Nimbus draws a chunky rounded thumb that overhangs its own track, which in a narrow panel
 * reads as clipped. This replaces both bars with a flat grey thumb a few pixels wide and drops the
 * arrow buttons entirely, which also means less furniture sitting next to a form.
 *
 * <p>Written against {@code BasicScrollBarUI} rather than Nimbus's painter system, so it looks the
 * same whatever look and feel is in play.
 */
final class ThinScrollBar {

    /** Bar thickness in pixels. */
    private static final int THICKNESS = 9;

    /** Inset of the thumb inside the bar, total across both edges. */
    private static final int THUMB_INSET = 3;

    private static final Color THUMB = new Color(0x8C8C8C);
    private static final Color THUMB_ACTIVE = new Color(0x5E5E5E);
    private static final Color TRACK = new Color(0xEDEDED);

    private ThinScrollBar() {
    }

    /** Style both bars of a scroll pane and return it, so this can wrap a constructor call. */
    static JScrollPane apply(JScrollPane pane) {
        style(pane.getVerticalScrollBar(), false);
        style(pane.getHorizontalScrollBar(), true);
        return pane;
    }

    private static void style(JScrollBar bar, boolean horizontal) {
        if (bar == null) {
            return;
        }
        bar.setUI(new Ui());
        bar.setPreferredSize(horizontal
                ? new Dimension(0, THICKNESS)
                : new Dimension(THICKNESS, 0));
        bar.setUnitIncrement(16);
        bar.setBackground(TRACK);
        bar.setBorder(BorderFactory.createEmptyBorder());
    }

    private static final class Ui extends BasicScrollBarUI {

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return hiddenButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return hiddenButton();
        }

        private static JButton hiddenButton() {
            JButton button = new JButton();
            Dimension none = new Dimension(0, 0);
            button.setPreferredSize(none);
            button.setMinimumSize(none);
            button.setMaximumSize(none);
            button.setBorder(BorderFactory.createEmptyBorder());
            button.setFocusable(false);
            return button;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent component, Rectangle bounds) {
            g.setColor(TRACK);
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        @Override
        protected void paintThumb(Graphics g, JComponent component, Rectangle bounds) {
            if (bounds.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isDragging || isThumbRollover() ? THUMB_ACTIVE : THUMB);
                int half = THUMB_INSET / 2;
                g2.fillRoundRect(
                        bounds.x + half,
                        bounds.y + half,
                        Math.max(1, bounds.width - THUMB_INSET),
                        Math.max(1, bounds.height - THUMB_INSET),
                        6, 6);
            } finally {
                g2.dispose();
            }
        }

        /** Keep the thumb grabbable on a long document. */
        @Override
        protected Dimension getMinimumThumbSize() {
            return new Dimension(THICKNESS, 30);
        }
    }
}
