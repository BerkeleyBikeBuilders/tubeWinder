package com.berkeleybikebuilders.tubewinder.ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Font;
import java.util.Locale;

/**
 * Position readout along the bottom, in UGS's spirit but with this machine's units: the carriage in
 * millimetres, the mandrel and the tow head in degrees.
 */
final class StatusBar extends JPanel {

    private final JLabel x = dro();
    private final JLabel y = dro();
    private final JLabel z = dro();
    private final JLabel message = new JLabel(" ");

    StatusBar() {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xD0D0D0)),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));

        message.setFont(message.getFont().deriveFont(Font.PLAIN, 11f));
        message.setForeground(new Color(0x666666));
        add(message);
        add(Box.createHorizontalGlue());

        add(caption("X"));
        add(x);
        add(Box.createHorizontalStrut(6));
        add(unit("mm"));
        add(Box.createHorizontalStrut(16));

        add(caption("Y"));
        add(y);
        add(Box.createHorizontalStrut(6));
        add(unit("deg"));
        add(Box.createHorizontalStrut(16));

        add(caption("Z"));
        add(z);
        add(Box.createHorizontalStrut(6));
        add(unit("deg"));

        setPosition(0, 0, 0);
    }

    void setPosition(double xValue, double yValue, double zValue) {
        x.setText(format(xValue));
        y.setText(format(yValue));
        z.setText(format(zValue));
    }

    void setMessage(String text) {
        message.setText(text == null || text.isBlank() ? " " : text);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%9.2f", value);
    }

    private static JLabel dro() {
        JLabel label = new JLabel();
        label.setFont(TerminalPanel.monospaced().deriveFont(Font.BOLD, 12f));
        return label;
    }

    private static JLabel caption(String text) {
        JLabel label = new JLabel(text + " ");
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        label.setForeground(new Color(0x555555));
        return label;
    }

    private static JLabel unit(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 10f));
        label.setForeground(new Color(0x999999));
        return label;
    }
}
