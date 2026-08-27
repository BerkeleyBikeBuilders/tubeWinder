package com.berkeleybikebuilders.tubewinder.ui;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Entry point.
 *
 * <pre>
 *   mvn package
 *   java -cp "ui/target/classes:backend/target/classes" \
 *        com.berkeleybikebuilders.tubewinder.ui.TubeWinderApp
 * </pre>
 *
 * (On Windows the classpath separator is {@code ;} rather than {@code :}.)
 */
public final class TubeWinderApp {

    private TubeWinderApp() {
    }

    public static void main(String[] args) {
        applyLookAndFeel();
        MachineSettings settings = MachineSettings.load();
        WinderConnection connection = new SimulatedConnection();
        SwingUtilities.invokeLater(() -> new MainWindow(settings, connection).setVisible(true));
    }

    /**
     * Nimbus where it exists, otherwise whatever the platform offers. UGS does the same thing -
     * the point is that the app looks native-ish everywhere without shipping a theme.
     */
    private static void applyLookAndFeel() {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    return;
                }
            }
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // The cross-platform default is always available; nothing to recover from.
        }
    }
}
