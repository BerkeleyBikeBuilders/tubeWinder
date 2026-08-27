package com.berkeleybikebuilders.tubewinder.ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JToolBar;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.EnumMap;
import java.util.Map;

/**
 * The console: every move the winder is asked to make, every reply, and anything that went wrong.
 *
 * <p>Errors are coloured rather than merely printed, because the whole reason this pane exists is
 * for something to be visible when a wind goes wrong at pass 60 of 74. Sent commands, machine
 * replies and informational notes each get their own colour, the way UGS's console does.
 */
final class TerminalPanel extends JPanel {

    /** Keeps the document from growing without bound over a long wind. */
    private static final int MAX_LINES = 5000;

    private final JTextPane output = new JTextPane();
    private final StyledDocument document = output.getStyledDocument();
    private final Map<WinderConnection.MessageKind, SimpleAttributeSet> styles =
            new EnumMap<>(WinderConnection.MessageKind.class);
    private final JCheckBox autoScroll = new JCheckBox("Scroll", true);
    private final JCheckBox showMoves = new JCheckBox("Show moves", true);

    private int lineCount;

    TerminalPanel() {
        super(new BorderLayout());

        style(WinderConnection.MessageKind.SENT, new Color(0x1565C0), false);
        style(WinderConnection.MessageKind.RECEIVED, new Color(0x2E7D32), false);
        style(WinderConnection.MessageKind.INFO, new Color(0x616161), true);
        style(WinderConnection.MessageKind.ERROR, new Color(0xC62828), false);

        output.setEditable(false);
        output.setFont(monospaced());
        output.setBackground(new Color(0xFAFAFA));
        output.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        JToolBar controls = new JToolBar();
        controls.setFloatable(false);
        controls.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        JButton clear = new JButton("Clear");
        clear.addActionListener(event -> clear());
        controls.add(clear);
        controls.add(Box.createHorizontalStrut(8));
        controls.add(showMoves);
        controls.add(Box.createHorizontalGlue());
        controls.add(autoScroll);

        add(controls, BorderLayout.NORTH);
        add(ThinScrollBar.apply(new JScrollPane(output)), BorderLayout.CENTER);
    }

    void append(String text, WinderConnection.MessageKind kind) {
        if (kind == WinderConnection.MessageKind.SENT && !showMoves.isSelected()) {
            return;
        }
        try {
            document.insertString(document.getLength(), text + "\n", styles.get(kind));
            lineCount++;
            if (lineCount > MAX_LINES) {
                trim();
            }
        } catch (BadLocationException e) {
            // A console that cannot print its own failure is not worth crashing the UI over.
            return;
        }
        if (autoScroll.isSelected()) {
            output.setCaretPosition(document.getLength());
        }
    }

    void info(String text) {
        append(text, WinderConnection.MessageKind.INFO);
    }

    void error(String text) {
        append(text, WinderConnection.MessageKind.ERROR);
    }

    void clear() {
        try {
            document.remove(0, document.getLength());
            lineCount = 0;
        } catch (BadLocationException ignored) {
            // Nothing sensible to do; the pane stays as it is.
        }
    }

    private void trim() {
        try {
            String all = document.getText(0, document.getLength());
            int cut = 0;
            for (int i = 0; i < MAX_LINES / 5; i++) {
                int next = all.indexOf('\n', cut);
                if (next < 0) {
                    break;
                }
                cut = next + 1;
            }
            if (cut > 0) {
                document.remove(0, cut);
                lineCount -= MAX_LINES / 5;
            }
        } catch (BadLocationException ignored) {
            // Leave the document alone rather than risk corrupting it.
        }
    }

    private void style(WinderConnection.MessageKind kind, Color colour, boolean italic) {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setForeground(attributes, colour);
        StyleConstants.setItalic(attributes, italic);
        styles.put(kind, attributes);
    }

    static Font monospaced() {
        return new Font(Font.MONOSPACED, Font.PLAIN, 12);
    }
}
