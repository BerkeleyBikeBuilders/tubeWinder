package com.berkeleybikebuilders.tubewinder.ui;

import com.berkeleybikebuilders.tubewinder.gcode.GcodeProgram;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Where the wrap visualizer will go.
 *
 * <p>Deliberately empty for now - this is the slot UGS fills with its OpenGL toolpath canvas, and
 * the plan is our own view of tow laid on the mandrel. Until then it draws a grid so the space
 * reads as a viewport rather than a bug, and prints the summary of whatever program was last
 * generated so the area is not wasted.
 */
final class VisualizerPanel extends JPanel {

    private static final Color BACKGROUND = new Color(0x20242B);
    private static final Color GRID = new Color(0xFFFFFF, true);
    private static final Color TEXT = new Color(0x8A93A0);
    private static final Color HEADING = new Color(0xD7DCE4);

    private String[] summary = new String[0];

    VisualizerPanel() {
        setBackground(BACKGROUND);
        setOpaque(true);
    }

    void showProgram(GcodeProgram program) {
        if (program == null) {
            summary = new String[0];
        } else {
            GcodeProgram.Summary s = program.summary();
            summary = new String[]{
                    String.format("%d passes over %d layers", s.passCount(), s.layers()),
                    String.format("%.1f mm tube", s.tubeLengthMm()),
                    s.towHeadEnabled()
                            ? String.format("tow head leads %.2f mm", s.axialLeadNearMm())
                            : "tow head offset disabled",
                    String.format("%d lines of G-code", program.lines().size())
            };
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            g.setColor(new Color(GRID.getRed(), GRID.getGreen(), GRID.getBlue(), 12));
            for (int x = 0; x < w; x += 32) {
                g.drawLine(x, 0, x, h);
            }
            for (int y = 0; y < h; y += 32) {
                g.drawLine(0, y, w, y);
            }

            g.setFont(getFont().deriveFont(Font.PLAIN, 15f));
            FontMetrics metrics = g.getFontMetrics();
            String heading = "Visualizer";
            int baseline = h / 2 - (summary.length * (metrics.getHeight() + 2)) / 2;

            g.setColor(HEADING);
            g.drawString(heading, (w - metrics.stringWidth(heading)) / 2, baseline);

            g.setFont(getFont().deriveFont(Font.PLAIN, 12f));
            metrics = g.getFontMetrics();
            g.setColor(TEXT);
            String subtitle = summary.length == 0
                    ? "slice a tube to see its summary here"
                    : "wrap preview goes here";
            baseline += metrics.getHeight() + 6;
            g.drawString(subtitle, (w - metrics.stringWidth(subtitle)) / 2, baseline);

            baseline += metrics.getHeight();
            for (String line : summary) {
                baseline += metrics.getHeight() + 2;
                g.drawString(line, (w - metrics.stringWidth(line)) / 2, baseline);
            }
        } finally {
            g.dispose();
        }
    }
}
