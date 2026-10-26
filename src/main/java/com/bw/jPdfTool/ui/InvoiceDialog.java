package com.bw.jPdfTool.ui;

import com.bw.jPdfTool.EInvoice;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;

/**
 * Shows the main data of an e-invoice in a readable form, the XML in an expandable text area below.
 */
public class InvoiceDialog extends JDialog {

    private static final Map<String, String> DOCUMENT_TYPES = Map.of(
            "380", "Invoice",
            "381", "Credit note",
            "384", "Corrected invoice",
            "389", "Self-billed invoice",
            "326", "Partial invoice",
            "386", "Prepayment invoice",
            "875", "Partial construction invoice",
            "876", "Partial final construction invoice",
            "877", "Final construction invoice");

    private static final Map<String, String> PROFILES = Map.of(
            "MINIMUM", "Minimum",
            "BASICWL", "Basic WL",
            "BASIC WL", "Basic WL",
            "BASIC", "Basic",
            "COMFORT", "EN 16931 (Comfort)",
            "EN16931", "EN 16931 (Comfort)",
            "EXTENDED", "Extended",
            "XRECHNUNG", "XRechnung");

    private static final Color AMOUNT_RULE = new Color(128, 128, 128, 90);

    /**
     * Width a value can shrink to (with "…") if the dialog is made narrow.
     */
    private static final int MIN_VALUE_WIDTH = 40;
    private static final int MIN_DIALOG_WIDTH = 280;

    private final Locale locale = Locale.getDefault();

    private InvoiceDialog(Window owner, byte[] xml, EInvoice.Summary summary, String error) {
        super(owner, "Invoice", Dialog.ModalityType.APPLICATION_MODAL);
        JPanel main = new JPanel(new BorderLayout(0, 12));
        main.setBorder(BorderFactory.createEmptyBorder(14, 14, 10, 14));

        JPanel center = new JPanel(new BorderLayout(0, 12));
        // The data block follows the width of the dialog.
        center.add(summary == null ? createError(error) : createSummary(summary), BorderLayout.NORTH);
        // Without readable data, the XML is all there is: show it directly.
        center.add(new CollapsibleTextPanel(prettyXml(xml), "Show XML", "Hide XML", summary == null),
                BorderLayout.CENTER);

        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING, 0, 0));
        buttons.add(close);
        getRootPane().setDefaultButton(close);

        main.add(center, BorderLayout.CENTER);
        main.add(buttons, BorderLayout.SOUTH);
        setContentPane(main);
        pack();
        // Values shrink with "…", but keep the dialog usable.
        setMinimumSize(new Dimension(MIN_DIALOG_WIDTH, 0));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /**
     * Shows the dialog (modal).
     *
     * @param xml The invoice XML.
     */
    public static void showDialog(Component parent, byte[] xml) {
        EInvoice.Summary summary = null;
        String error = null;
        try {
            summary = EInvoice.summarize(xml);
            if (!hasData(summary)) {
                // Mustang doesn't fail on XML that is no invoice, it simply finds nothing.
                summary = null;
                error = "The XML contains no invoice data.";
            }
        } catch (Exception e) {
            error = e.getMessage() == null ? e.toString() : e.getMessage();
        }
        Window owner = parent instanceof Window w ? w : SwingUtilities.getWindowAncestor(parent);
        InvoiceDialog dialog = new InvoiceDialog(owner, xml, summary, error);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    /**
     * Checks if the summary contains anything that identifies an invoice.
     */
    static boolean hasData(EInvoice.Summary s) {
        return s != null && (s.id() != null || s.seller() != null || s.buyer() != null || s.amount() != null);
    }

    private JComponent createError(String error) {
        JLabel label = new JLabel("<html><b>The invoice data can't be read.</b><br>" + escape(error) + "</html>");
        label.setIcon(UIManager.getIcon("OptionPane.warningIcon"));
        label.setIconTextGap(10);
        return label;
    }

    private JComponent createSummary(EInvoice.Summary s) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.anchor = GridBagConstraints.WEST;
        gc.gridy = 0;

        // Head: spans both columns and follows the width.
        gc.gridx = 0;
        gc.gridwidth = 2;
        gc.weightx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(0, 0, 0, 0);
        panel.add(createHead(s), gc);
        gc.gridwidth = 1;
        gc.weightx = 0;
        gc.fill = GridBagConstraints.NONE;

        // Parties and dates.
        gc.gridy++;
        gc.insets = new Insets(14, 0, 0, 0);
        panel.add(spacer(), gc);
        addRow(panel, gc, "From", s.seller());
        addRow(panel, gc, "To", s.buyer());
        addRow(panel, gc, "Issue date", formatDate(s.issueDate(), locale));
        addRow(panel, gc, "Due date", formatDate(s.dueDate(), locale));
        addRow(panel, gc, "Reference", s.reference());

        // Amounts, right aligned at the right edge of the value column.
        boolean hasNet = s.netAmount() != null;
        boolean hasTax = s.taxAmount() != null;
        if (hasNet || hasTax || s.amount() != null) {
            gc.gridy++;
            gc.insets = new Insets(10, 0, 4, 0);
            panel.add(spacer(), gc);
            addAmountRow(panel, gc, "Net amount", formatAmount(s.netAmount(), s.currency(), locale), false);
            addAmountRow(panel, gc, "VAT", formatAmount(s.taxAmount(), s.currency(), locale), false);
            if (hasNet || hasTax) {
                gc.gridy++;
                gc.gridx = 1;
                gc.fill = GridBagConstraints.HORIZONTAL;
                gc.insets = new Insets(3, 0, 3, 0);
                JSeparator rule = new JSeparator();
                rule.setForeground(AMOUNT_RULE);
                panel.add(rule, gc);
                gc.fill = GridBagConstraints.NONE;
            }
            addAmountRow(panel, gc, "Amount due", formatAmount(s.amount(), s.currency(), locale), true);
        }

        // Payment.
        gc.gridy++;
        gc.insets = new Insets(8, 0, 0, 0);
        panel.add(spacer(), gc);
        addRow(panel, gc, "IBAN", formatIban(s.iban()));
        addRow(panel, gc, "Payment terms", s.paymentTerms());
        return panel;
    }

    /**
     * Icon on the left. Right of it the document type and number at the top of the icon,
     * the profile (type of the e-invoice) on the bottom line of the icon.
     */
    private static JComponent createHead(EInvoice.Summary s) {
        JPanel head = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();

        // The icon spans both rows, so the rows together are as high as the icon.
        gc.gridx = 0;
        gc.gridy = 0;
        gc.gridheight = 2;
        gc.anchor = GridBagConstraints.NORTHWEST;
        gc.insets = new Insets(0, 0, 0, 10);
        head.add(new JLabel(UI.getIcon("invoice")), gc);
        gc.gridheight = 1;

        JLabel title = new ShrinkLabel(documentType(s.documentCode()) + (s.id() == null ? "" : " " + s.id()),
                SwingConstants.LEADING);
        Font font = title.getFont();
        title.setFont(font.deriveFont(Font.BOLD, font.getSize2D() * 1.4f));
        gc.gridx = 1;
        gc.weightx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(0, 0, 0, 0);
        head.add(title, gc);

        // Second row gets the remaining height of the icon: the profile sits on its bottom line.
        gc.gridy = 1;
        gc.weighty = 1;
        gc.anchor = GridBagConstraints.SOUTHWEST;
        if (s.profile() != null) {
            JLabel profile = new ShrinkLabel("ZUGFeRD / Factur-X, profile " + profileName(s.profile()),
                    SwingConstants.LEADING);
            profile.setForeground(UIManager.getColor("Label.disabledForeground"));
            head.add(profile, gc);
        } else {
            head.add(spacer(), gc);
        }
        return head;
    }

    private static JComponent spacer() {
        return new JLabel();
    }

    private static JLabel caption(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(UIManager.getColor("Label.disabledForeground"));
        return l;
    }

    /**
     * Adds caption and value. The value takes all extra width and shrinks with "…" if there is not enough.
     */
    private static void addRow(JPanel panel, GridBagConstraints gc, String caption, String value) {
        if (value == null)
            return;
        gc.gridy++;
        gc.gridx = 0;
        gc.anchor = GridBagConstraints.NORTHWEST;
        gc.insets = new Insets(3, 0, 3, 18);
        panel.add(caption(caption), gc);
        gc.gridx = 1;
        gc.insets = new Insets(3, 0, 3, 0);
        gc.weightx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new ShrinkLabel(value, SwingConstants.LEADING), gc);
        gc.weightx = 0;
        gc.fill = GridBagConstraints.NONE;
        gc.anchor = GridBagConstraints.WEST;
    }

    private static void addAmountRow(JPanel panel, GridBagConstraints gc, String caption, String value, boolean total) {
        if (value == null)
            return;
        gc.gridy++;
        gc.gridx = 0;
        gc.insets = new Insets(2, 0, 2, 18);
        JLabel c = caption(caption);
        panel.add(c, gc);
        gc.gridx = 1;
        gc.insets = new Insets(2, 0, 2, 0);
        gc.weightx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        JLabel v = new JLabel(value, SwingConstants.RIGHT);
        if (total) {
            Font f = v.getFont();
            v.setFont(f.deriveFont(Font.BOLD, f.getSize2D() * 1.2f));
            c.setForeground(UIManager.getColor("Label.foreground"));
            c.setFont(c.getFont().deriveFont(Font.BOLD));
        }
        panel.add(v, gc);
        gc.weightx = 0;
        gc.fill = GridBagConstraints.NONE;
        gc.anchor = GridBagConstraints.WEST;
    }

    /**
     * A single-line label that can shrink below its preferred width, the text is cut with "…" then.
     * If the text is cut (or had line breaks), the tooltip shows it completely.
     */
    private static final class ShrinkLabel extends JLabel {
        private final String fullText;

        ShrinkLabel(String text, int alignment) {
            super(text.replaceAll("\\s*\\R\\s*", " "), alignment);
            fullText = text;
            // Registers the label at the tooltip manager, the text itself comes from getToolTipText(MouseEvent).
            setToolTipText("");
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            boolean cut = getWidth() < getPreferredSize().width;
            return cut || !fullText.equals(getText()) ? "<html>" + escape(fullText) + "</html>" : null;
        }

        @Override
        public Dimension getMinimumSize() {
            Dimension d = getPreferredSize();
            int iconWidth = getIcon() == null ? 0 : getIcon().getIconWidth() + getIconTextGap();
            return new Dimension(Math.min(d.width, iconWidth + MIN_VALUE_WIDTH), d.height);
        }
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\n", "<br>");
    }

    /**
     * Name of the document type (UNTDID 1001 code), e.g. "380" is "Invoice".
     */
    static String documentType(String code) {
        if (code == null)
            return "Invoice";
        String name = DOCUMENT_TYPES.get(code.trim());
        return name == null ? "Document (type " + code + ")" : name;
    }

    /**
     * Readable profile name, e.g. "COMFORT" is "EN 16931 (Comfort)".
     */
    static String profileName(String profile) {
        if (profile == null)
            return null;
        String name = PROFILES.get(profile.trim().toUpperCase(Locale.ROOT));
        return name == null ? profile : name;
    }

    /**
     * Formats a date of the invoice ("yyyyMMdd", format 102) for the locale.
     *
     * @return The formatted date, the original text if it has another format, null if missing.
     */
    static String formatDate(String date, Locale locale) {
        if (date == null)
            return null;
        try {
            LocalDate d = LocalDate.parse(date.trim(), DateTimeFormatter.BASIC_ISO_DATE);
            return d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale));
        } catch (Exception e) {
            return date;
        }
    }

    /**
     * Formats an amount with its currency for the locale, e.g. "119.00" and "EUR" is "119,00 €" in Germany.
     *
     * @return The formatted amount, a simple combination if it can't be parsed, null if missing.
     */
    static String formatAmount(String amount, String currency, Locale locale) {
        if (amount == null)
            return null;
        try {
            BigDecimal value = new BigDecimal(amount.trim());
            if (currency == null)
                return NumberFormat.getNumberInstance(locale).format(value);
            NumberFormat nf = NumberFormat.getCurrencyInstance(locale);
            Currency c = Currency.getInstance(currency.trim());
            nf.setCurrency(c);
            nf.setMinimumFractionDigits(Math.max(c.getDefaultFractionDigits(), 0));
            nf.setMaximumFractionDigits(Math.max(Math.max(c.getDefaultFractionDigits(), 0), value.scale()));
            return nf.format(value);
        } catch (Exception e) {
            return currency == null ? amount : amount + " " + currency;
        }
    }

    /**
     * Indents the XML for display. The stored attachment is not changed.
     *
     * @return The indented XML, or the XML as it is if it can't be parsed.
     */
    static String prettyXml(byte[] xml) {
        try {
            javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            javax.xml.parsers.DocumentBuilder builder = dbf.newDocumentBuilder();
            // Don't print parse errors to the console, the text is shown as it is then.
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
            org.w3c.dom.Document doc = builder.parse(new java.io.ByteArrayInputStream(xml));
            removeWhitespace(doc.getDocumentElement());

            // The JDK's own transformer: others on the class path (e.g. Saxon) handle the indent options differently.
            javax.xml.transform.TransformerFactory tf = javax.xml.transform.TransformerFactory.newDefaultInstance();
            javax.xml.transform.Transformer t = tf.newTransformer();
            t.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
            t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            t.setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "UTF-8");
            java.io.StringWriter out = new java.io.StringWriter();
            t.transform(new javax.xml.transform.dom.DOMSource(doc), new javax.xml.transform.stream.StreamResult(out));
            return out.toString().trim();
        } catch (Exception e) {
            return new String(xml, StandardCharsets.UTF_8);
        }
    }

    /**
     * Removes text nodes that contain only white space, otherwise the indentation adds empty lines.
     */
    private static void removeWhitespace(org.w3c.dom.Node node) {
        org.w3c.dom.NodeList children = node.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; --i) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.TEXT_NODE && child.getTextContent().isBlank())
                node.removeChild(child);
            else
                removeWhitespace(child);
        }
    }

    /**
     * Groups an IBAN in blocks of four characters.
     */
    static String formatIban(String iban) {
        if (iban == null)
            return null;
        String compact = iban.replaceAll("\\s+", "");
        return compact.replaceAll("(.{4})(?!$)", "$1 ");
    }
}
