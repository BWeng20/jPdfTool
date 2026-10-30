package com.bw.jPdfTool.ui;

import com.bw.jPdfTool.AboutInfo;
import com.bw.jPdfTool.BuildVersion;
import com.bw.jPdfTool.Log;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;

/**
 * The About dialog: version, build date, license, the system and the libraries.
 */
public final class AboutDialog {

    private AboutDialog() {
    }

    public static void show(Component parent) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "About " + AboutInfo.NAME,
                JDialog.ModalityType.APPLICATION_MODAL);

        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(16, 20, 12, 20));
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.gridy = 0;
        gc.anchor = GridBagConstraints.WEST;

        JLabel title = new JLabel(AboutInfo.NAME + " " + BuildVersion.VERSION);
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() * 1.8f));
        content.add(title, gc);

        gc.gridy++;
        gc.insets = new Insets(2, 0, 0, 0);
        content.add(new JLabel("Build date " + BuildVersion.BUILD_DATE), gc);

        gc.gridy++;
        gc.insets = new Insets(8, 0, 0, 0);
        content.add(new JLabel("<html><body style=\"width:300px\">" + AboutInfo.DESCRIPTION + "</body></html>"), gc);

        gc.gridy++;
        gc.insets = new Insets(14, 0, 0, 0);
        content.add(link(AboutInfo.HOME_PAGE), gc);

        // The license and the button to show its text.
        gc.gridy++;
        gc.insets = new Insets(10, 0, 0, 0);
        content.add(licenseRow(dialog), gc);

        gc.gridy++;
        gc.insets = new Insets(10, 0, 0, 0);
        gc.fill = GridBagConstraints.BOTH;
        gc.weightx = 1;
        gc.weighty = 1;
        JTextArea details = new JTextArea(details());
        details.setEditable(false);
        details.setFont(new Font(Font.MONOSPACED, Font.PLAIN, details.getFont().getSize()));
        details.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(details);
        scroll.setPreferredSize(new Dimension(440, 200));
        content.add(scroll, gc);

        JButton copy = new JButton("Copy Info");
        copy.setToolTipText("Copies version, system and libraries to the clipboard, e.g. for a bug report.");
        copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(AboutInfo.asText()), null));
        JButton close = new JButton("Close");
        close.addActionListener(e -> dialog.dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(copy);
        buttons.add(close);

        dialog.add(content, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.setBorder(BorderFactory.createEmptyBorder(0, 20, 14, 20));
        south.add(buttons, BorderLayout.EAST);
        dialog.add(south, BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(close);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    /**
     * Java, system and libraries, as shown in the text area.
     */
    private static String details() {
        StringBuilder sb = new StringBuilder();
        sb.append("Java    ").append(AboutInfo.java()).append('\n');
        sb.append("System  ").append(AboutInfo.os()).append("\n\n");
        sb.append("Libraries\n");
        AboutInfo.libraries().forEach(l -> sb.append("  ").append(l).append('\n'));
        return sb.toString();
    }

    private static JPanel licenseRow(JDialog dialog) {
        // No gaps at the border, so the row aligns with the other lines.
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JLabel text = new JLabel("License: " + AboutInfo.LICENSE_NAME);
        text.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
        p.add(text);
        JButton show = new JButton("License…");
        show.setToolTipText("Shows the license text.");
        show.addActionListener(e -> showLicense(dialog));
        p.add(show);
        return p;
    }

    /**
     * A text that opens the address in the browser, if the system supports that.
     */
    private static JLabel link(String url) {
        boolean browse = Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE);
        JLabel l = new JLabel(browse ? "<html><a href=\"" + url + "\">" + url + "</a></html>" : url, SwingConstants.LEFT);
        if (browse) {
            l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            l.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    try {
                        Desktop.getDesktop().browse(URI.create(url));
                    } catch (Exception ex) {
                        Log.error("Failed to open %s: %s", url, ex.getMessage());
                    }
                }
            });
        }
        return l;
    }

    private static void showLicense(JDialog owner) {
        String text = AboutInfo.licenseText();
        JTextArea area = new JTextArea(text.isEmpty() ? AboutInfo.LICENSE_NAME + "\n" + AboutInfo.LICENSE_URL : text, 18, 70);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setCaretPosition(0);
        JOptionPane.showMessageDialog(owner, new JScrollPane(area), "License", JOptionPane.PLAIN_MESSAGE);
    }
}
