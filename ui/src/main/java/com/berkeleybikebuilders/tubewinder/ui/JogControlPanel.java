package com.berkeleybikebuilders.tubewinder.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

/**
 * Manual control of the three axes.
 *
 * <p>Modelled on UGS's jog panel - step-size and feedrate spinners above a grid of large, obvious
 * buttons - but laid out as one labelled row per axis rather than UGS's cartesian cross. On this
 * machine only X is linear; Y and Z are rotations in degrees, so arranging them as a compass rose
 * would suggest a spatial relationship that isn't there. Each row says what it drives and in what
 * units.
 *
 * <p>Two step sizes, because 1 mm of carriage and 1 degree of mandrel are not comparable amounts of
 * motion.
 */
final class JogControlPanel extends JPanel {

    private final WinderConnection connection;
    private final MachineSettings settings;

    private final JSpinner linearStep;
    private final JSpinner rotaryStep;
    private final JSpinner feedrate;
    private final List<JButton> jogButtons = new ArrayList<>();

    JogControlPanel(WinderConnection connection, MachineSettings settings) {
        super(new GridBagLayout());
        this.connection = connection;
        this.settings = settings;

        linearStep = new JSpinner(new SpinnerNumberModel(settings.jogStepLinearMm(), 0.001, 1000.0, 1.0));
        rotaryStep = new JSpinner(new SpinnerNumberModel(settings.jogStepRotaryDeg(), 0.001, 3600.0, 5.0));
        feedrate = new JSpinner(new SpinnerNumberModel(settings.jogFeedrate(), 1.0, 100000.0, 100.0));

        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 3, 3, 3);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        row = addSpinnerRow(c, row, "Step, linear (mm)", linearStep);
        row = addSpinnerRow(c, row, "Step, rotary (deg)", rotaryStep);
        row = addSpinnerRow(c, row, "Jog feedrate", feedrate);

        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = 3;
        c.insets = new Insets(12, 3, 4, 3);
        add(new JLabel("Jog"), c);
        c.insets = new Insets(3, 3, 3, 3);

        row = addAxisRow(c, row, WinderConnection.Axis.X, "Carriage");
        row = addAxisRow(c, row, WinderConnection.Axis.Y, "Mandrel");
        row = addAxisRow(c, row, WinderConnection.Axis.Z, "Tow head");

        JButton toZero = new JButton("Set work zero here");
        toZero.addActionListener(event -> connection.zeroWork());
        jogButtons.add(toZero);
        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = 3;
        c.insets = new Insets(14, 3, 3, 3);
        add(toZero, c);

        // Soak up the leftover height so the controls stay at the top.
        c.gridy = row;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        add(new JPanel(), c);

        setEnabledForState(connection.state());
    }

    private int addSpinnerRow(GridBagConstraints c, int row, String label, JSpinner spinner) {
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 1;
        c.weightx = 0;
        add(new JLabel(label), c);
        c.gridx = 1;
        c.gridwidth = 2;
        c.weightx = 1;
        spinner.setPreferredSize(new Dimension(90, spinner.getPreferredSize().height));
        add(spinner, c);
        return row + 1;
    }

    private int addAxisRow(GridBagConstraints c, int row, WinderConnection.Axis axis, String what) {
        JLabel label = new JLabel("<html>" + axis.letter() + " - " + what
                + "<br><font size='-2' color='#777777'>" + axis.unit() + "</font></html>");
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 1;
        c.weightx = 1;
        add(label, c);

        c.weightx = 0;
        c.gridx = 1;
        add(jogButton(axis, -1), c);
        c.gridx = 2;
        add(jogButton(axis, +1), c);
        return row + 1;
    }

    private JButton jogButton(WinderConnection.Axis axis, int sign) {
        JButton button = new JButton(axis.letter() + (sign > 0 ? "+" : "-"));
        button.setFont(button.getFont().deriveFont(Font.BOLD, 14f));
        button.setPreferredSize(new Dimension(58, 42));
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setToolTipText("Jog " + axis.letter() + " by one step " + (sign > 0 ? "positive" : "negative"));
        button.addActionListener(event -> {
            double step = axis.isRotary() ? value(rotaryStep) : value(linearStep);
            connection.jog(axis, sign * step, value(feedrate));
        });
        jogButtons.add(button);
        return button;
    }

    /** Push the spinner values back into settings so they survive a restart. */
    void captureSettings() {
        settings.setJogStepLinearMm(value(linearStep));
        settings.setJogStepRotaryDeg(value(rotaryStep));
        settings.setJogFeedrate(value(feedrate));
    }

    /** Re-read the spinners from settings, after the settings dialog has changed them. */
    void reloadFromSettings() {
        linearStep.setValue(settings.jogStepLinearMm());
        rotaryStep.setValue(settings.jogStepRotaryDeg());
        feedrate.setValue(settings.jogFeedrate());
    }

    void setEnabledForState(WinderConnection.State state) {
        boolean canJog = state.isIdle();
        jogButtons.forEach(button -> button.setEnabled(canJog));
    }

    private static double value(JSpinner spinner) {
        return ((Number) spinner.getValue()).doubleValue();
    }
}
