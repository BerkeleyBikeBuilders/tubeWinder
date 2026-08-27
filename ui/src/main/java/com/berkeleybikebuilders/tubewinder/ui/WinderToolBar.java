package com.berkeleybikebuilders.tubewinder.ui;

import com.berkeleybikebuilders.tubewinder.gcode.GcodeProgram;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JToolBar;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.function.Supplier;

/**
 * The machine toolbar, borrowed wholesale from UGS: connection on the left, program transport in
 * the middle, machine actions after that, state on the right.
 *
 * <p>Every button here talks to {@link WinderConnection} and nothing else, so when the real sender
 * arrives the toolbar does not change. Today it drives the simulator.
 */
final class WinderToolBar extends JToolBar {

    private static final String[] BAUD_RATES =
            {"9600", "19200", "38400", "57600", "115200", "230400"};

    private final WinderConnection connection;
    private final MachineSettings settings;
    private final Supplier<GcodeProgram> programSupplier;

    private final JComboBox<String> port = new JComboBox<>();
    private final JComboBox<String> baud = new JComboBox<>(BAUD_RATES);

    private final JButton connect = new JButton("Connect", Icons.connect(16));
    private final JButton startup = new JButton(Icons.sun(16));
    private final JButton run = new JButton(Icons.play(16));
    private final JButton pause = new JButton(Icons.pause(16));
    private final JButton stop = new JButton(Icons.stop(16));
    private final JButton home = new JButton(Icons.home(16));
    private final JButton zero = new JButton(Icons.zero(16));
    private final JButton reset = new JButton(Icons.reset(16));
    private final JButton unlock = new JButton(Icons.unlock(16));

    private final JProgressBar progress = new JProgressBar(0, 1000);
    private final JLabel state = new JLabel();

    WinderToolBar(WinderConnection connection,
                  MachineSettings settings,
                  Supplier<GcodeProgram> programSupplier) {
        this.connection = connection;
        this.settings = settings;
        this.programSupplier = programSupplier;

        setFloatable(false);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xD0D0D0)),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        port.setEditable(true);
        port.addItem(settings.port());
        port.addItem("/dev/ttyUSB0");
        port.addItem("/dev/tty.usbserial");
        port.setSelectedItem(settings.port());
        port.setMaximumSize(new Dimension(150, 28));
        baud.setSelectedItem(String.valueOf(settings.baudRate()));
        baud.setMaximumSize(new Dimension(100, 28));

        add(label("Port"));
        add(port);
        add(Box.createHorizontalStrut(6));
        add(label("Baud"));
        add(baud);
        add(Box.createHorizontalStrut(8));
        add(connect);

        addSeparator(new Dimension(14, 0));
        add(button(startup, "<html><b>Startup and alignment</b><br>Exercise X, calibrate Y and Z, "
                + "and park the tow head at the left-hand start with room for this program.</html>"));
        add(button(run, "Run the sliced program"));
        add(button(pause, "Feed hold"));
        add(button(stop, "Stop and clear the program"));

        addSeparator(new Dimension(14, 0));
        add(button(home, "Home the axes"));
        add(button(zero, "Set work zero at the current position"));
        add(button(reset, "Soft reset the controller"));
        add(button(unlock, "Clear an alarm"));

        addSeparator(new Dimension(14, 0));
        progress.setPreferredSize(new Dimension(140, 14));
        progress.setMaximumSize(new Dimension(140, 14));
        progress.setVisible(false);
        add(progress);

        add(Box.createHorizontalGlue());
        state.setFont(state.getFont().deriveFont(Font.BOLD, 12f));
        add(state);

        connect.addActionListener(event -> toggleConnection());
        startup.addActionListener(event ->
                connection.runStartupProcedure(programSupplier.get(), settings.railLengthMm()));
        run.addActionListener(event -> startOrResume());
        pause.addActionListener(event -> connection.pause());
        stop.addActionListener(event -> connection.stop());
        home.addActionListener(event -> connection.home());
        zero.addActionListener(event -> connection.zeroWork());
        reset.addActionListener(event -> connection.softReset());
        unlock.addActionListener(event -> connection.unlock());

        setState(connection.state());
    }

    private void toggleConnection() {
        if (connection.state().isConnected()) {
            connection.disconnect();
        } else {
            String selectedPort = String.valueOf(port.getSelectedItem()).trim();
            int selectedBaud = Integer.parseInt(String.valueOf(baud.getSelectedItem()));
            settings.setPort(selectedPort);
            settings.setBaudRate(selectedBaud);
            connection.connect(selectedPort, selectedBaud);
        }
    }

    private void startOrResume() {
        if (connection.state() == WinderConnection.State.PAUSED) {
            connection.resume();
            return;
        }
        GcodeProgram program = programSupplier.get();
        if (program == null) {
            connection.stop();
            return;
        }
        connection.run(program);
    }

    void setState(WinderConnection.State machineState) {
        boolean connected = machineState.isConnected();
        boolean idle = machineState.isIdle();
        boolean running = machineState == WinderConnection.State.RUNNING;
        boolean paused = machineState == WinderConnection.State.PAUSED;

        connect.setText(connected ? "Disconnect" : "Connect");
        connect.setIcon(connected ? Icons.disconnect(16) : Icons.connect(16));
        port.setEnabled(!connected);
        baud.setEnabled(!connected);

        boolean sliced = programSupplier.get() != null;
        startup.setEnabled(idle && sliced);
        // Run needs three things: a sliced program, an idle machine, and a machine that has been
        // through the startup procedure and therefore knows where it is.
        run.setEnabled((idle && sliced && connection.isAligned()) || paused);
        pause.setEnabled(running);
        stop.setEnabled(running || paused);
        home.setEnabled(idle);
        zero.setEnabled(idle);
        reset.setEnabled(connected);
        unlock.setEnabled(connected);

        state.setText(machineState.label());
        state.setForeground(switch (machineState) {
            case DISCONNECTED -> new Color(0x757575);
            case CONNECTED -> new Color(0x2E7D32);
            case ALIGNING -> new Color(0xF9A825);
            case RUNNING -> new Color(0x1565C0);
            case PAUSED -> new Color(0xE65100);
            case ALARM -> new Color(0xC62828);
        });
    }

    /** Call when a program is sliced or cleared, so Run and Startup can re-evaluate. */
    void programChanged() {
        setState(connection.state());
    }

    void setProgress(double fraction) {
        if (fraction < 0) {
            progress.setVisible(false);
            progress.setValue(0);
        } else {
            progress.setVisible(true);
            progress.setValue((int) Math.round(fraction * 1000));
        }
    }

    private static JLabel label(String text) {
        JLabel label = new JLabel(text + " ");
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 11f));
        return label;
    }

    private static JButton button(JButton button, String tooltip) {
        button.setToolTipText(tooltip);
        button.setFocusable(false);
        return button;
    }
}
