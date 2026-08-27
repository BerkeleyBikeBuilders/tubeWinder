package com.berkeleybikebuilders.tubewinder.ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The gear. Everything that stays put between winds.
 *
 * <p>Grouped the way you'd think about them: what the material is, where the tow head sits, how the
 * generator should behave, and how to reach the machine. Saved to
 * {@code ~/.tubewinder/settings.properties} on OK, so a fresh session comes up with the shop's
 * numbers already in place.
 */
final class SettingsDialog extends JDialog {

    private final MachineSettings settings;
    private final Map<String, JTextField> fields = new LinkedHashMap<>();

    private final JCheckBox offsetCompensation =
            new JCheckBox("Compensate for the tow head offset");
    private final JCheckBox computedTakeup =
            new JCheckBox("Use computed slack takeup instead of v7's 90 deg");
    private final JCheckBox includeComments =
            new JCheckBox("Write header comments into the G-code");

    private boolean accepted;

    private SettingsDialog(Window owner, MachineSettings settings) {
        super(owner, "Machine settings", Dialog.ModalityType.APPLICATION_MODAL);
        this.settings = settings;

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Material", materialTab());
        tabs.addTab("Machine", machineTab());
        tabs.addTab("Generator", generatorTab());
        tabs.addTab("Connection", connectionTab());

        add(tabs, BorderLayout.CENTER);
        add(buttons(), BorderLayout.SOUTH);

        loadFromSettings();
        pack();
        setMinimumSize(getPreferredSize());
        setLocationRelativeTo(owner);
    }

    /** @return true if the settings were changed and saved */
    static boolean show(Window owner, MachineSettings settings) {
        SettingsDialog dialog = new SettingsDialog(owner, settings);
        dialog.setVisible(true);
        return dialog.accepted;
    }

    // ------------------------------------------------------------------

    private JPanel materialTab() {
        Form form = new Form();
        form.field("towWidth", "Tow width (mm)", "As laid, not the spool width.");
        form.field("layerThickness", "Cured layer thickness (mm)",
                "Measure a scrap part.");
        form.field("feedrate", "Feedrate", null);
        return form.build();
    }

    private JPanel machineTab() {
        Form form = new Form();
        form.field("railLength", "Rail length, X travel (mm)", "Usable travel, end to end.");
        form.note("Tow head offsets, measured from the mandrel axis to the payout point.");
        form.field("headHorizontal", "Horizontal offset (mm)", null);
        form.field("headVertical", "Vertical offset (mm)", null);
        form.check(offsetCompensation, "Off means no lead and no recenter.");
        return form.build();
    }

    private JPanel generatorTab() {
        Form form = new Form();
        form.field("taperStep", "Taper step (mm)", "How finely a taper is chopped up.");
        form.check(computedTakeup, "Otherwise v7's fixed 180 deg is used.");
        form.check(includeComments, "Turn off if your controller is fussy.");
        return form.build();
    }

    private JPanel connectionTab() {
        Form form = new Form();
        form.field("port", "Serial port", "COM3, /dev/ttyUSB0, and so on.");
        form.field("baudRate", "Baud rate", null);
        form.note("No serial transport yet; connecting runs the simulator.");
        return form.build();
    }

    private JPanel buttons() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(6, 10, 10, 10));

        JLabel path = new JLabel(MachineSettings.settingsFile().toString());
        path.setFont(path.getFont().deriveFont(Font.PLAIN, 10f));
        path.setForeground(new Color(0x888888));

        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> dispose());
        JButton ok = new JButton("Save");
        ok.addActionListener(event -> save());
        getRootPane().setDefaultButton(ok);

        JPanel right = new JPanel();
        right.add(cancel);
        right.add(ok);

        panel.add(path, BorderLayout.WEST);
        panel.add(right, BorderLayout.EAST);
        return panel;
    }

    // ------------------------------------------------------------------

    private void loadFromSettings() {
        fields.get("towWidth").setText(String.valueOf(settings.towWidthMm()));
        fields.get("layerThickness").setText(String.valueOf(settings.layerThicknessMm()));
        fields.get("feedrate").setText(String.valueOf(settings.feedrate()));
        fields.get("railLength").setText(String.valueOf(settings.railLengthMm()));
        fields.get("headHorizontal").setText(String.valueOf(settings.towHeadHorizontalMm()));
        fields.get("headVertical").setText(String.valueOf(settings.towHeadVerticalMm()));
        fields.get("taperStep").setText(String.valueOf(settings.taperStepMm()));
        fields.get("port").setText(settings.port());
        fields.get("baudRate").setText(String.valueOf(settings.baudRate()));
        offsetCompensation.setSelected(settings.offsetCompensation());
        computedTakeup.setSelected(settings.computedTakeup());
        includeComments.setSelected(settings.includeComments());
    }

    private void save() {
        try {
            double towWidth = positive("towWidth", "tow width");
            double layerThickness = positive("layerThickness", "layer thickness");
            double feedrate = positive("feedrate", "feedrate");
            double railLength = positive("railLength", "rail length");
            double headH = nonNegative("headHorizontal", "horizontal offset");
            double headV = nonNegative("headVertical", "vertical offset");
            double taperStep = positive("taperStep", "taper step");
            double baud = positive("baudRate", "baud rate");
            String port = fields.get("port").getText().trim();

            if (offsetCompensation.isSelected() && headH == 0 && headV == 0) {
                throw new IllegalArgumentException(
                        "With compensation on, at least one tow head offset must be non-zero.");
            }

            settings.setTowWidthMm(towWidth);
            settings.setLayerThicknessMm(layerThickness);
            settings.setFeedrate(feedrate);
            settings.setRailLengthMm(railLength);
            settings.setTowHeadHorizontalMm(headH);
            settings.setTowHeadVerticalMm(headV);
            settings.setTaperStepMm(taperStep);
            settings.setBaudRate((int) baud);
            settings.setPort(port);
            settings.setOffsetCompensation(offsetCompensation.isSelected());
            settings.setComputedTakeup(computedTakeup.isSelected());
            settings.setIncludeComments(includeComments.isSelected());

            String failure = settings.save();
            if (failure != null) {
                JOptionPane.showMessageDialog(this, failure, "Could not save",
                        JOptionPane.WARNING_MESSAGE);
            }
            accepted = true;
            dispose();
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Check that value",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private double positive(String key, String what) {
        double value = nonNegative(key, what);
        if (value <= 0) {
            throw new IllegalArgumentException(capitalise(what) + " must be greater than zero.");
        }
        return value;
    }

    private double nonNegative(String key, String what) {
        String text = fields.get(key).getText().trim().replace(",", ".");
        try {
            double value = Double.parseDouble(text);
            if (value < 0) {
                throw new IllegalArgumentException(capitalise(what) + " cannot be negative.");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + text + "' is not a number - check "
                    + what + ".");
        }
    }

    private static String capitalise(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** Small helper so each tab reads as a list of rows rather than GridBag boilerplate. */
    private final class Form {
        private final JPanel panel = new JPanel(new GridBagLayout());
        private final GridBagConstraints c = new GridBagConstraints();
        private int row;

        Form() {
            panel.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
            c.insets = new Insets(4, 0, 4, 0);
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            c.gridx = 0;
        }

        void field(String key, String label, String hint) {
            JTextField textField = new JTextField(14);
            fields.put(key, textField);
            add(stack(label, textField, hint));
        }

        void check(JCheckBox box, String hint) {
            add(stack(null, box, hint));
        }

        void note(String text) {
            JLabel label = new JLabel("<html><i>" + text + "</i></html>");
            label.setFont(label.getFont().deriveFont(Font.PLAIN, 11f));
            label.setForeground(new Color(0x666666));
            add(label);
        }

        private void add(JComponent component) {
            c.gridy = row++;
            panel.add(component, c);
        }

        private JPanel stack(String label, JComponent component, String hint) {
            JPanel stack = new JPanel(new BorderLayout(0, 2));
            if (label != null) {
                stack.add(new JLabel(label), BorderLayout.NORTH);
            }
            stack.add(component, BorderLayout.CENTER);
            if (hint != null) {
                JLabel hintLabel = new JLabel("<html><font color='#888888'>" + hint + "</font></html>");
                hintLabel.setFont(hintLabel.getFont().deriveFont(Font.PLAIN, 10f));
                stack.add(hintLabel, BorderLayout.SOUTH);
            }
            return stack;
        }

        JPanel build() {
            c.gridy = row;
            c.weighty = 1;
            c.fill = GridBagConstraints.BOTH;
            panel.add(Box.createGlue(), c);
            return panel;
        }
    }
}
