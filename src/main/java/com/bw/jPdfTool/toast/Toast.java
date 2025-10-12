package com.bw.jPdfTool.toast;

import com.bw.jPdfTool.Log;
import com.bw.jPdfTool.UI;
import com.formdev.flatlaf.ui.FlatLineBorder;

import javax.swing.*;
import javax.swing.plaf.PanelUI;
import javax.swing.text.Element;
import javax.swing.text.ParagraphView;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * A panel that shows toast.<br>
 * <pre>
 *    +-----------------------+
 *    |       MULTI-LINE     X|
 *    | ICON  HTML-           |
 *    |       MESSAGE         |
 *    +-----------------------+
 * </pre>
 * For the used icon see {@link #getIconForType(ToastType)}.
 */
public class Toast extends JPanel {

    protected Animation animation;
    public final ToastType type;
    protected final JLabel label = new JLabel();


    /**
     * EditorKit inheritance to enable also one-char paragraphs that will enable the kit to break inside words.
     */
    private static class HTMLEditorKitWrapSupport extends HTMLEditorKit {

        @Override
        public ViewFactory getViewFactory() {

            return new HTMLFactory() {
                public View create(Element e) {
                    View v = super.create(e);
                    if (v instanceof ParagraphView) {
                        return new ParagraphView(e) {
                            protected SizeRequirements calculateMinorAxisRequirements(int axis, SizeRequirements r) {
                                SizeRequirements rs = super.calculateMinorAxisRequirements(axis, r);
                                rs.minimum = 8;
                                return rs;
                            }

                        };
                    }
                    return v;
                }
            };
        }
    }

    @Override
    public void setUI(PanelUI ui) {
        super.setUI(ui);
        setToastBorder();
    }

    /**
     * Sets borders and icons according to current LAF.
     */
    protected void setToastBorder() {
        // Check if we are called by base class ctor,
        if (type == null)
            return;

        Color bc = UIManager.getColor("Component.borderColor");
        label.setIcon(getIconForType(type));
        if (UI.isFlatLaf()) {
            setBorder(new FlatLineBorder(new Insets(5, 5, 5, 6), bc, 2, 20));
            setOpaque(false);
            setBackground(UIManager.getColor("TextField.background"));
        } else {
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(bc, 2, true),
                    BorderFactory.createEmptyBorder(5, 5, 5, 3)));
            setOpaque(true);
            setBackground(UIManager.getColor("TextField.background"));
        }
    }

    /**
     * Initialize a new toast message.
     *
     * @param message The text (should be html)
     * @param type    The type (determines the icon, see {@link #getIconForType(ToastType)})
     * @param onClose The callback to be triggered if the toast should close.
     * @param width   The desired width of the message box. Height is calculated to fit the message text.
     */
    public Toast(String message, ToastType type, Runnable onClose, int width) {
        setLayout(new GridBagLayout());
        this.type = type;
        setToastBorder();

        JButton close = new JButton(UI.getIcon("close"));
        close.setBorderPainted(false);
        close.setContentAreaFilled(false);
        close.setBorder(null);

        if (onClose != null) {
            close.addActionListener(e -> {
                if (this.closeTimer != null) {
                    onClose.run();
                }

            });
            // Auto-close after 5 seconds
            closeTimer = new Timer(Toaster.closeDelayMS, e -> {
                if (this.closeTimer != null) {
                    onClose.run();
                }
            });
            closeTimer.start();
        }

        JEditorPane textArea = new JEditorPane();
        textArea.setEditorKit(new HTMLEditorKitWrapSupport());
        textArea.setText(message);

        textArea.setOpaque(false);
        textArea.setEditable(false);

        GridBagConstraints gc = new GridBagConstraints();

        gc.insets = new Insets(0, 0, 0, 0);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridx = 0;
        gc.anchor = GridBagConstraints.LINE_START;
        gc.weightx = 0;
        label.setBackground(Color.RED);
        add(label, gc);
        gc.gridx = 1;
        gc.weightx = 1;
        gc.weighty = 0;
        add(textArea, gc);
        gc.gridx = 2;
        gc.anchor = GridBagConstraints.NORTHEAST;
        gc.weightx = 0;
        gc.weighty = 0;
        add(close, gc);

        // Find the height for the fixed width
        setSize(new Dimension(width, 500));
        doLayout();
        setSize(new Dimension(width,
                Math.max(label.getPreferredSize().height, textArea.getPreferredSize().height) + 10));

        enableEvents(AWTEvent.MOUSE_EVENT_MASK);

    }

    private Timer closeTimer;

    public void cleanUp() {
        removeAll();
        if (closeTimer != null) {
            closeTimer.stop();
            closeTimer = null;
        }

    }

    static {
        // Register a global event listener to catch all sub-elements.
        try {
            Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
                int id = event.getID();
                if ((id == MouseEvent.MOUSE_ENTERED || id == MouseEvent.MOUSE_EXITED) && event.getSource() instanceof Component component) {
                    Toast t;
                    if (component instanceof Toast toast)
                        t = toast;
                    else
                        t = (Toast) SwingUtilities.getAncestorOfClass(Toast.class, component);
                    if (t != null) {
                        if (id == MouseEvent.MOUSE_ENTERED) {
                            System.out.println(" STOP");
                            t.closeTimer.stop();
                        } else {
                            System.out.println(" START");
                            t.closeTimer.start();
                        }
                    }
                }
            }, AWTEvent.MOUSE_EVENT_MASK);
        } catch (SecurityException securityException) {
            Log.error("Can't register AWT Event Listener: %s", securityException.getMessage());
        }
    }

    /**
     * Determines the icon for the message type. Currently, OptionPane icons are returned.
     *
     * @param type The type.
     * @return The icon.
     */
    public static Icon getIconForType(ToastType type) {
        return switch (type) {
            case INFO -> UIManager.getIcon("OptionPane.informationIcon");
            case WARNING -> UIManager.getIcon("OptionPane.warningIcon");
            case ERROR -> UIManager.getIcon("OptionPane.errorIcon");
            case SUCCESS -> UIManager.getIcon("OptionPane.questionIcon");
        };
    }

    public void animateTo(int delayMS, int x, int y) {
        if (animation != null)
            animation.abort();
        animation = new Animation(this, delayMS, x, y);
    }

}