package com.berkeleybikebuilders.tubewinder.ui;

import com.berkeleybikebuilders.tubewinder.WindingParameters;
import com.berkeleybikebuilders.tubewinder.gcode.GcodeGenerator;
import com.berkeleybikebuilders.tubewinder.gcode.GcodeProgram;
import com.berkeleybikebuilders.tubewinder.geometry.TowHeadGeometry;
import com.berkeleybikebuilders.tubewinder.geometry.TubeProfile;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The per-wind parameters: the handful of numbers that actually change from tube to tube.
 *
 * <p>The Parameters tab of {@link SpecificationPanel}, and what replaces UGS's file-open area. UGS
 * loads a G-code file someone else produced; here the program is produced on the spot from the tube
 * you are about to make. Anything that stays the same between winds - tow width, feedrate, tow head
 * geometry - lives behind the gear above the tab bar and is not asked for again.
 */
final class ParametersPanel extends JPanel {

    private static final String STRAIGHT = "Straight";
    private static final String TAPERED = "Tapered";

    private final SpecificationPanel.Host host;
    private final MachineSettings settings;

    private final JComboBox<String> tubeType = new JComboBox<>(new String[]{STRAIGHT, TAPERED});
    // A BorderLayout holder rather than a CardLayout, so the panel is only ever as tall as the
    // shape actually selected. CardLayout reserves room for the tallest card, which left a hole
    // under the straight-tube fields.
    private final JPanel shapePanel = new JPanel(new BorderLayout());
    private final Map<String, JPanel> shapeCards = new LinkedHashMap<>();
    private final Map<String, JTextField> fields = new LinkedHashMap<>();

    private final JButton slice = new JButton("Slice");
    private final JButton save = new JButton("Download");
    private final JLabel summary = new JLabel();

    /** The most recent slice, kept so Save has something to write. */
    private GcodeProgram program;

    ParametersPanel(SpecificationPanel.Host host, MachineSettings settings) {
        super(new BorderLayout());
        this.host = host;
        this.settings = settings;

        add(form(), BorderLayout.CENTER);
    }

    // ------------------------------------------------------------------

    private JPanel form() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 0, 3, 0);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.gridx = 0;
        int row = 0;

        c.gridy = row++;
        form.add(labelled("Tube type", tubeType), c);

        buildShapeCards();
        c.gridy = row++;
        form.add(shapePanel, c);

        c.gridy = row++;
        form.add(labelled("Wrap angle (deg from axis)", field("wrapAngle", "0")), c);
        c.gridy = row++;
        form.add(labelled("Wall thickness (mm)", field("wallThickness", "0")), c);

        slice.addActionListener(event -> slice());
        c.gridy = row++;
        c.insets = new Insets(14, 0, 4, 0);
        form.add(slice, c);

        save.setEnabled(false);
        save.setToolTipText("Write the sliced program to " + downloadsTarget());
        save.addActionListener(event -> saveToDownloads());
        c.gridy = row++;
        c.insets = new Insets(4, 0, 4, 0);
        form.add(save, c);

        summary.setVerticalAlignment(SwingConstants.TOP);
        summary.setFont(TerminalPanel.monospaced().deriveFont(11f));
        summary.setForeground(new Color(0x555555));
        c.gridy = row++;
        c.insets = new Insets(4, 0, 4, 0);
        form.add(summary, c);

        c.gridy = row;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        form.add(Box.createGlue(), c);

        tubeType.addActionListener(event -> showShape((String) tubeType.getSelectedItem()));
        return form;
    }

    private void buildShapeCards() {
        JPanel straight = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 0, 3, 0);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.gridx = 0;
        c.gridy = 0;
        straight.add(labelled("Cross-section perimeter (mm)", field("perimeter", "0")), c);
        c.gridy = 1;
        straight.add(labelled("Tube length (mm)", field("length", "0")), c);
        topAnchor(straight, c, 2);

        JPanel tapered = new JPanel(new GridBagLayout());
        GridBagConstraints t = new GridBagConstraints();
        t.insets = new Insets(3, 0, 3, 0);
        t.fill = GridBagConstraints.HORIZONTAL;
        t.weightx = 1;
        t.gridx = 0;
        int row = 0;
        t.gridy = row++;
        tapered.add(labelled("Near perimeter (mm)", field("nearPerimeter", "0")), t);
        t.gridy = row++;
        tapered.add(labelled("Far perimeter (mm)", field("farPerimeter", "0")), t);
        t.gridy = row++;
        tapered.add(labelled("Near section length (mm)", field("nearLength", "0")), t);
        t.gridy = row++;
        tapered.add(labelled("Taper length (mm)", field("taperLength", "0")), t);
        t.gridy = row++;
        tapered.add(labelled("Far section length (mm)", field("farLength", "0")), t);
        topAnchor(tapered, t, row);

        shapeCards.put(STRAIGHT, straight);
        shapeCards.put(TAPERED, tapered);
        showShape(STRAIGHT);
    }

    private void showShape(String type) {
        JPanel card = shapeCards.get(type);
        if (card == null) {
            return;
        }
        shapePanel.removeAll();
        shapePanel.add(card, BorderLayout.CENTER);
        shapePanel.revalidate();
        shapePanel.repaint();
    }

    /**
     * The two shape cards share a CardLayout, which sizes itself to the taller one. Without this the
     * shorter card's fields float in the middle of the reserved space.
     */
    private static void topAnchor(JPanel panel, GridBagConstraints c, int row) {
        c.gridy = row;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        panel.add(Box.createGlue(), c);
        c.weighty = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
    }

    private JTextField field(String key, String initial) {
        JTextField textField = new JTextField(initial);
        textField.setColumns(10);
        fields.put(key, textField);
        return textField;
    }

    private static JPanel labelled(String text, java.awt.Component component) {
        JPanel panel = new JPanel(new BorderLayout(0, 2));
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(11f));
        panel.add(label, BorderLayout.NORTH);
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    // ------------------------------------------------------------------

    private void slice() {
        try {
            WindingParameters parameters = buildParameters();
            program = GcodeGenerator.generate(parameters);
            save.setEnabled(true);

            GcodeProgram.Summary s = program.summary();
            summary.setText(String.format(
                    "<html>%d layers &middot; %d passes<br>%d lines%s</html>",
                    s.layers(), s.passCount(), program.lines().size(),
                    s.towHeadEnabled()
                            ? String.format("<br>lead %.2f mm &middot; span %.2f mm",
                            s.axialLeadNearMm(), s.freeSpanNearMm())
                            : "<br>tow head offset off"));

            host.onInfo("Sliced " + program.lines().size() + " lines.");
            host.onInfo(s.report().strip().replace("\n", "  |  "));
            host.onSliced(program);
        } catch (IllegalArgumentException e) {
            program = null;
            save.setEnabled(false);
            summary.setText("<html><font color='#C62828'>slicing failed</font></html>");
            host.onError(e.getMessage());
            host.onSliced(null);
        }
    }

    /**
     * Drop the sliced program straight into the downloads folder as {@code gcode.txt}.
     *
     * <p>One button, one known place, no dialog: the point is to get from parameters to a file you
     * can hand to a sender in two clicks. It overwrites the previous {@code gcode.txt} rather than
     * piling up numbered copies, and says so when it does, so a stale file can never be mistaken
     * for the current one. File > Save G-code... is still there when a specific path is wanted.
     */
    private void saveToDownloads() {
        if (program == null) {
            host.onError("Nothing to save - slice a tube first.");
            return;
        }
        Path target = downloadsTarget();
        boolean replaced = Files.exists(target);
        try {
            program.writeTo(target);
            host.onInfo("Saved " + program.lines().size() + " lines to " + target
                    + (replaced ? " (replaced the previous gcode.txt)." : "."));
        } catch (IOException e) {
            host.onError("Could not write " + target + ": " + e.getMessage());
        }
    }

    /** {@code ~/Downloads/gcode.txt}, or the home folder itself if there is no Downloads. */
    private static Path downloadsTarget() {
        Path home = Path.of(System.getProperty("user.home"));
        Path downloads = home.resolve("Downloads");
        return (Files.isDirectory(downloads) ? downloads : home).resolve("gcode.txt");
    }

    private WindingParameters buildParameters() {
        TubeProfile profile = STRAIGHT.equals(tubeType.getSelectedItem())
                ? TubeProfile.straight(number("perimeter", "cross-section perimeter"),
                number("length", "tube length"))
                : TubeProfile.tapered(
                number("nearPerimeter", "near perimeter"),
                number("farPerimeter", "far perimeter"),
                number("nearLength", "near section length"),
                number("taperLength", "taper length"),
                number("farLength", "far section length"),
                settings.taperStepMm());

        TowHeadGeometry head = settings.offsetCompensation()
                ? new TowHeadGeometry(settings.towHeadHorizontalMm(), settings.towHeadVerticalMm())
                : TowHeadGeometry.none();

        return WindingParameters.builder()
                .profile(profile)
                .wrapAngleDeg(number("wrapAngle", "wrap angle"))
                .wallThicknessMm(number("wallThickness", "wall thickness"))
                .towWidthMm(settings.towWidthMm())
                .feedrate(settings.feedrate())
                .layerThicknessMm(settings.layerThicknessMm())
                .towHead(head)
                .useComputedSlackTakeup(settings.computedTakeup())
                .includeComments(settings.includeComments())
                .build();
    }

    private double number(String key, String what) {
        JTextField textField = fields.get(key);
        String text = textField == null ? "" : textField.getText().trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Enter a value for " + what + ".");
        }
        double value;
        try {
            value = Double.parseDouble(text.replace(",", "."));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "'%s' is not a number - check %s.", text, what));
        }
        if (value <= 0) {
            throw new IllegalArgumentException(
                    "Enter a " + what + " greater than zero.");
        }
        return value;
    }
}
