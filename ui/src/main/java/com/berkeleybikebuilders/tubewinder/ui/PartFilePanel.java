package com.berkeleybikebuilders.tubewinder.ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Where a CAD file will specify the mandrel instead of the parameter form. Roadmap item 4.
 *
 * <p>Nothing here reads a part yet, and it says so rather than implying otherwise. You can pick or
 * drop a file and it will be remembered and reported; the Slice button stays disabled because
 * there is no geometry extraction behind it.
 *
 * <p>What it will do, when it does: pull the mandrel's axis and its radius profile r(z) out of the
 * file, turn that into a {@code TubeProfile} - the same list of constant-perimeter segments the
 * taper already produces - and hand it to the same generator. Everything downstream of the profile
 * is already written, which is why this is a smaller job than it looks.
 */
final class PartFilePanel extends JPanel {

    private static final String[] EXTENSIONS = {"step", "stp", "stl", "iges", "igs", "obj"};

    private final SpecificationPanel.Host host;
    private final DropZone dropZone = new DropZone();
    private final JLabel selected = new JLabel(" ");
    private final JButton slice = new JButton("Slice from part");

    private File partFile;

    PartFilePanel(SpecificationPanel.Host host) {
        super(new GridBagLayout());
        this.host = host;
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 0, 4, 0);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.gridx = 0;
        int row = 0;

        c.gridy = row++;
        add(dropZone, c);

        JButton choose = new JButton("Choose file...");
        choose.addActionListener(event -> chooseFile());
        c.gridy = row++;
        add(choose, c);

        selected.setFont(TerminalPanel.monospaced().deriveFont(11f));
        selected.setForeground(new Color(0x555555));
        c.gridy = row++;
        add(selected, c);

        slice.setEnabled(false);
        slice.setToolTipText("Not implemented yet");
        c.gridy = row++;
        c.insets = new Insets(10, 0, 4, 0);
        add(slice, c);

        c.insets = new Insets(12, 0, 4, 0);
        c.gridy = row++;
        add(note("<b>Not wired up yet.</b> Choosing a file records it and nothing more."), c);

        setTransferHandler(new FileDropHandler());
        dropZone.setTransferHandler(new FileDropHandler());

        c.gridy = row;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        add(Box.createGlue(), c);
    }

    private void chooseFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("CAD part files", EXTENSIONS));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            accept(chooser.getSelectedFile());
        }
    }

    private void accept(File file) {
        if (file == null || !file.isFile()) {
            return;
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        boolean known = false;
        for (String extension : EXTENSIONS) {
            known |= name.endsWith("." + extension);
        }
        if (!known) {
            host.onError("Not a recognised part file: " + file.getName()
                    + " (expected STEP, STL, IGES or OBJ).");
            return;
        }

        partFile = file;
        selected.setText(file.getName() + "  (" + (file.length() / 1024) + " KB)");
        dropZone.setFileName(file.getName());
        host.onInfo("Selected " + file.getName() + ". Nothing was read from it yet.");
    }

    /** The chosen file, for whoever implements the import. */
    File partFile() {
        return partFile;
    }

    private static JLabel note(String html) {
        JLabel label = new JLabel("<html><div style='width:220px'>" + html + "</div></html>");
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 11f));
        label.setForeground(new Color(0x777777));
        return label;
    }

    /** Dashed target so the panel reads as somewhere you can drop something. */
    private static final class DropZone extends JPanel {

        private String fileName;

        DropZone() {
            setLayout(new BorderLayout());
            setOpaque(false);
            setPreferredSize(new java.awt.Dimension(200, 92));
        }

        void setFileName(String name) {
            fileName = name;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int w = getWidth() - 2;
                int h = getHeight() - 2;
                g.setColor(new Color(0xF4F6F8));
                g.fillRoundRect(1, 1, w, h, 10, 10);
                g.setColor(new Color(0xB9C0C8));
                g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        1f, new float[]{5f, 4f}, 0f));
                g.drawRoundRect(1, 1, w, h, 10, 10);

                g.setColor(new Color(0x7A828C));
                g.setFont(getFont().deriveFont(Font.PLAIN, 12f));
                String line = fileName == null ? "Drop a part file here" : fileName;
                int textWidth = g.getFontMetrics().stringWidth(line);
                g.drawString(line, (getWidth() - textWidth) / 2, getHeight() / 2 - 2);

                g.setFont(getFont().deriveFont(Font.PLAIN, 10f));
                String hint = fileName == null ? "STEP, STL, IGES, OBJ" : "not read yet";
                int hintWidth = g.getFontMetrics().stringWidth(hint);
                g.setColor(new Color(0xA0A8B0));
                g.drawString(hint, (getWidth() - hintWidth) / 2, getHeight() / 2 + 16);
            } finally {
                g.dispose();
            }
        }
    }

    /** Accepts a file dragged from Explorer or Finder onto either the panel or the drop zone. */
    private final class FileDropHandler extends TransferHandler {

        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDrop()
                    && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            try {
                Object data = support.getTransferable()
                        .getTransferData(DataFlavor.javaFileListFlavor);
                if (data instanceof List<?> files && !files.isEmpty()
                        && files.get(0) instanceof File file) {
                    accept(file);
                    return true;
                }
            } catch (Exception e) {
                host.onError("Could not read the dropped file: " + e.getMessage());
            }
            return false;
        }

        @Override
        public int getSourceActions(JComponent component) {
            return COPY;
        }
    }
}
