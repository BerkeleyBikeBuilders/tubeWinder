package com.berkeleybikebuilders.tubewinder.ui;

import com.berkeleybikebuilders.tubewinder.gcode.GcodeProgram;

import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * The application window.
 *
 * <p>Laid out after UGS Classic, with the two substitutions we wanted:
 *
 * <pre>
 *   +--------------------------------------------------------------+
 *   | toolbar: connection | run pause stop | home zero reset unlock |
 *   +---------------------------+----------------------------------+
 *   | gear  Winding parameters  |                                  |
 *   |   tube type, dimensions   |            visualizer            |
 *   |   wrap angle, wall        |            (ours, later)         |
 *   |   [Generate G-code]       |                                  |
 *   +---------------------------+                                  |
 *   | [ Jog | Terminal ]        |                                  |
 *   +---------------------------+----------------------------------+
 *   | status: message                       X ..  Y ..  Z ..       |
 *   +--------------------------------------------------------------+
 * </pre>
 *
 * <p>UGS's file-open panel becomes the parameters form, and its OpenGL toolpath canvas becomes our
 * visualizer slot. The toolbar, the jog controls, the console, the DRO and the general shape are
 * theirs, because they work.
 */
final class MainWindow extends JFrame implements WinderConnection.Listener, SpecificationPanel.Host {

    private final MachineSettings settings;
    private final WinderConnection connection;

    private final TerminalPanel terminal = new TerminalPanel();
    private final VisualizerPanel visualizer = new VisualizerPanel();
    private final StatusBar statusBar = new StatusBar();
    private final SpecificationPanel specification;
    private final JogControlPanel jog;
    private final WinderToolBar toolBar;

    private GcodeProgram program;

    MainWindow(MachineSettings settings, WinderConnection connection) {
        super("Tube Winder");
        this.settings = settings;
        this.connection = connection;

        specification = new SpecificationPanel(this, settings);
        jog = new JogControlPanel(connection, settings);
        toolBar = new WinderToolBar(connection, settings, () -> program);

        setJMenuBar(menuBar());
        add(toolBar, BorderLayout.NORTH);
        add(body(), BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        connection.addListener(this);

        terminal.info("Ready. No machine connected.");

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                shutdown();
            }
        });

        setSize(new Dimension(1240, 820));
        setMinimumSize(new Dimension(880, 560));
        setLocationRelativeTo(null);
    }

    // ------------------------------------------------------------ layout

    private JSplitPane body() {
        JTabbedPane machineTabs = new JTabbedPane();
        machineTabs.addTab("Jog controller", scrollable(jog));
        machineTabs.addTab("Terminal", terminal);

        JSplitPane left = new JSplitPane(JSplitPane.VERTICAL_SPLIT, specification, machineTabs);
        left.setResizeWeight(0.55);
        left.setDividerLocation(470);
        left.setBorder(BorderFactory.createEmptyBorder());
        left.setMinimumSize(new Dimension(300, 300));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, visualizer);
        split.setResizeWeight(0.0);
        split.setDividerLocation(380);
        split.setBorder(BorderFactory.createEmptyBorder());
        return split;
    }

    /**
     * Panels are wrapped rather than left to be clipped: a short window should scroll the form, not
     * quietly swallow the bottom half of it.
     */
    private static JScrollPane scrollable(java.awt.Component component) {
        JScrollPane scroll = new JScrollPane(component,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        return ThinScrollBar.apply(scroll);
    }

    private JMenuBar menuBar() {
        int shortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        JMenu file = new JMenu("File");
        file.add(item("Save G-code...", KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcut),
                this::saveProgram));
        file.addSeparator();
        file.add(item("Exit", KeyStroke.getKeyStroke(KeyEvent.VK_Q, shortcut), this::shutdown));

        JMenu machine = new JMenu("Machine");
        machine.add(item("Settings...", KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, shortcut),
                this::openSettings));
        machine.addSeparator();
        machine.add(item("Clear terminal",
                KeyStroke.getKeyStroke(KeyEvent.VK_L, shortcut | InputEvent.SHIFT_DOWN_MASK),
                terminal::clear));

        JMenu help = new JMenu("Help");
        help.add(item("About", null, this::showAbout));

        JMenuBar bar = new JMenuBar();
        bar.add(file);
        bar.add(machine);
        bar.add(help);
        return bar;
    }

    private static JMenuItem item(String text, KeyStroke accelerator, Runnable action) {
        JMenuItem menuItem = new JMenuItem(text);
        if (accelerator != null) {
            menuItem.setAccelerator(accelerator);
        }
        menuItem.addActionListener(event -> action.run());
        return menuItem;
    }

    // ----------------------------------------------- SpecificationPanel.Host

    @Override
    public void onSliced(GcodeProgram newProgram) {
        program = newProgram;
        visualizer.showProgram(newProgram);
        toolBar.programChanged();
        statusBar.setMessage(newProgram == null
                ? "No program"
                : newProgram.lines().size() + " lines sliced");
    }

    @Override
    public void onInfo(String messageText) {
        terminal.info(messageText);
    }

    @Override
    public void onError(String messageText) {
        terminal.error(messageText);
        showTerminal();
    }

    @Override
    public void openSettings() {
        if (SettingsDialog.show(this, settings)) {
            jog.reloadFromSettings();
            terminal.info("Machine settings saved to " + MachineSettings.settingsFile() + ".");
        }
    }

    // ------------------------------------------- WinderConnection.Listener

    @Override
    public void onStateChanged(WinderConnection.State state) {
        SwingUtilities.invokeLater(() -> {
            toolBar.setState(state);
            jog.setEnabledForState(state);
        });
    }

    /**
     * The machine wants a person to do something before it goes on - mount the mandrel, attach the
     * tow. Put it in front of them, and tell the machine once they say it is done.
     */
    @Override
    public void onOperatorAction(String prompt) {
        SwingUtilities.invokeLater(() -> {
            showTerminal();
            JOptionPane.showMessageDialog(this,
                    "<html><div style='width:340px'>" + prompt.replace("\n", "<br>") + "</div></html>",
                    "Startup procedure", JOptionPane.INFORMATION_MESSAGE);
            connection.confirmOperatorAction();
        });
    }

    @Override
    public void onPositionChanged(double x, double y, double z) {
        SwingUtilities.invokeLater(() -> statusBar.setPosition(x, y, z));
    }

    @Override
    public void onMessage(String text, WinderConnection.MessageKind kind) {
        SwingUtilities.invokeLater(() -> {
            terminal.append(text, kind);
            if (kind == WinderConnection.MessageKind.ERROR) {
                showTerminal();
            }
        });
    }

    @Override
    public void onProgress(double fraction) {
        SwingUtilities.invokeLater(() -> toolBar.setProgress(fraction));
    }

    // ------------------------------------------------------------- actions

    private void showTerminal() {
        // The terminal is where errors are readable; bring it forward when one lands.
        java.awt.Component parent = terminal.getParent();
        if (parent instanceof JTabbedPane tabs) {
            tabs.setSelectedComponent(terminal);
        }
    }

    private void saveProgram() {
        if (program == null) {
            JOptionPane.showMessageDialog(this, "Slice a tube first.",
                    "Nothing to save", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("tube.gcode"));
        chooser.setFileFilter(new FileNameExtensionFilter("G-code", "gcode", "nc", "txt"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path path = chooser.getSelectedFile().toPath();
        try {
            program.writeTo(path);
            terminal.info("Saved " + program.lines().size() + " lines to " + path + ".");
            statusBar.setMessage("Saved " + path.getFileName());
        } catch (IOException e) {
            terminal.error("Could not write " + path + ": " + e.getMessage());
        }
    }

    private void showAbout() {
        JOptionPane.showMessageDialog(this,
                "<html><b>Tube Winder</b><br><br>"
                        + "Berkeley Bike Builders filament winder.<br>"
                        + "G-code generation from Generator v7, with tow head offset compensation."
                        + "<br><br>Layout borrowed from Universal Gcode Sender.</html>",
                "About", JOptionPane.INFORMATION_MESSAGE);
    }

    private void shutdown() {
        jog.captureSettings();
        settings.save();
        if (connection.state().isConnected()) {
            connection.disconnect();
        }
        dispose();
        System.exit(0);
    }
}
