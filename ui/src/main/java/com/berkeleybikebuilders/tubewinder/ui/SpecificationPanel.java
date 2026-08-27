package com.berkeleybikebuilders.tubewinder.ui;

import com.berkeleybikebuilders.tubewinder.gcode.GcodeProgram;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;

/**
 * The part specification: what are we winding, and how.
 *
 * <p>Two ways to say it. <b>Parameters</b> is the form - the handful of numbers that change from
 * tube to tube, which is how every wind has been specified so far. <b>Part file</b> is where a CAD
 * file will be dropped instead, once roadmap item 4 lands.
 *
 * <p>The gear sits above both, because machine settings are not per-tube and not per-method: tow
 * width and tow head geometry apply however the shape got here.
 */
final class SpecificationPanel extends JPanel {

    interface Host {
        void onSliced(GcodeProgram program);

        void onInfo(String message);

        void onError(String message);

        void openSettings();
    }

    private final ParametersPanel parameters;
    private final PartFilePanel partFile;

    SpecificationPanel(Host host, MachineSettings settings) {
        super(new BorderLayout());

        parameters = new ParametersPanel(host, settings);
        partFile = new PartFilePanel(host);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Parameters", scrollable(parameters));
        tabs.addTab("Part file", scrollable(partFile));

        add(header(host), BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
    }

    private JPanel header(Host host) {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(8, 10, 4, 6));

        JLabel title = new JLabel("Part specification");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));

        JButton gear = new JButton(Icons.gear(18));
        gear.setToolTipText("Machine settings - the values that don't change between winds");
        gear.setFocusable(false);
        gear.setBorderPainted(false);
        gear.setContentAreaFilled(false);
        gear.setPreferredSize(new Dimension(28, 28));
        gear.addActionListener(event -> host.openSettings());

        header.add(title, BorderLayout.WEST);
        header.add(gear, BorderLayout.EAST);
        return header;
    }

    private static JScrollPane scrollable(Component component) {
        JScrollPane scroll = new JScrollPane(component,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        return ThinScrollBar.apply(scroll);
    }
}
