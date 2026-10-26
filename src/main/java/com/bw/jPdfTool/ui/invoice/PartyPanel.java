package com.bw.jPdfTool.ui.invoice;

import com.bw.jPdfTool.invoice.InvoiceData;
import com.bw.jPdfTool.invoice.Party;

import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Edits the data of a seller or buyer. Changes are written directly into the party.<br>
 * Used by the invoice editor and the party settings.
 */
class PartyPanel extends FormPanel {

    /**
     * @param group Field group the profile of the invoice must support, null if every profile has the field.
     */
    private record Binding(JTextField field, InvoiceData.Field group, String toolTip,
                           Function<Party, String> getter, BiConsumer<Party, String> setter) {
    }

    private final List<Binding> bindings = new ArrayList<>();
    private final JTextField nameField;
    private Party party;
    private Runnable onChange;
    private boolean updating;
    private Predicate<InvoiceData.Field> supported = f -> true;

    /**
     * @param seller        True for a seller: with tax number, contact and bank account.
     * @param headerCaption Caption of the header row.
     * @param header        Optional component shown in the first row, e.g. the preset chooser of the editor.
     */
    PartyPanel(boolean seller, String headerCaption, JComponent header) {
        if (header != null)
            row(headerCaption, header);
        InvoiceData.Field address = seller ? InvoiceData.Field.SELLER_ADDRESS : InvoiceData.Field.BUYER_DETAILS;
        InvoiceData.Field details = seller ? null : InvoiceData.Field.BUYER_DETAILS;
        InvoiceData.Field email = seller ? InvoiceData.Field.SELLER_EMAIL : InvoiceData.Field.BUYER_DETAILS;

        nameField = field(null, 14, null, p -> p.name, (p, v) -> p.name = v);
        row("Name", nameField);
        row("Street", field(address, 14, null, p -> p.street, (p, v) -> p.street = v));
        row("Postcode", field(address, 6, null, p -> p.zip, (p, v) -> p.zip = v),
                "City", field(address, 10, null, p -> p.city, (p, v) -> p.city = v));
        row("Country", field(details, 3, "Two letter code (ISO 3166-1), e.g. DE, AT, FR.", p -> p.country, (p, v) -> p.country = v),
                "VAT ID", field(details, 10, null, p -> p.vatId, (p, v) -> p.vatId = v));
        if (seller) {
            row("Tax number", field(null, 10, "Tax number (\"Steuernummer\"), needed if there is no VAT ID.",
                            p -> p.taxId, (p, v) -> p.taxId = v),
                    "E-mail", field(email, 10, null, p -> p.email, (p, v) -> p.email = v));
            row("Contact", field(InvoiceData.Field.SELLER_CONTACT, 10, null, p -> p.contactName, (p, v) -> p.contactName = v),
                    "Phone", field(InvoiceData.Field.SELLER_CONTACT, 10, null, p -> p.phone, (p, v) -> p.phone = v));
            section("Bank account");
            row("IBAN", field(InvoiceData.Field.BANK_ACCOUNT, 14, null,
                            p -> p.iban == null ? null : InvoiceDialog.formatIban(p.iban), (p, v) -> p.iban = v),
                    "BIC", field(InvoiceData.Field.ACCOUNT_DETAILS, 8, null, p -> p.bic, (p, v) -> p.bic = v));
            row("Account holder", field(InvoiceData.Field.ACCOUNT_DETAILS, 14, null,
                    p -> p.accountHolder, (p, v) -> p.accountHolder = v));
        } else {
            row("E-mail", field(email, 14, null, p -> p.email, (p, v) -> p.email = v));
        }
        setParty(null);
    }

    /**
     * Called after each change by the user.
     */
    void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    JTextField getNameField() {
        return nameField;
    }

    /**
     * Disables the fields the profile of the invoice doesn't have. Their values are kept, but not written.
     */
    void setSupported(Predicate<InvoiceData.Field> supported) {
        this.supported = supported;
        updateEnabled();
    }

    /**
     * Shows and edits this party. Null clears and disables the fields.
     */
    void setParty(Party party) {
        this.party = party;
        reload();
    }

    /**
     * Shows the values of the party again, e.g. after it was changed from outside.
     */
    void reload() {
        updating = true;
        try {
            for (Binding b : bindings) {
                String v = party == null ? null : b.getter.apply(party);
                b.field.setText(v == null ? "" : v);
                b.field.setCaretPosition(0);
            }
        } finally {
            updating = false;
        }
        updateEnabled();
    }

    private void updateEnabled() {
        for (Binding b : bindings) {
            boolean ok = b.group == null || supported.test(b.group);
            b.field.setEnabled(party != null && ok);
            b.field.setToolTipText(ok ? b.toolTip : "Not part of the profile of this invoice, the value is not written.");
        }
    }

    private JTextField field(InvoiceData.Field group, int columns, String toolTip, Function<Party, String> getter,
                             BiConsumer<Party, String> setter) {
        JTextField f = new JTextField(columns);
        Binding b = new Binding(f, group, toolTip, getter, setter);
        bindings.add(b);
        f.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                changed(b);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                changed(b);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                changed(b);
            }
        });
        return f;
    }

    private void changed(Binding b) {
        if (updating || party == null)
            return;
        String v = b.field.getText();
        b.setter.accept(party, v.isBlank() ? null : v);
        if (onChange != null)
            onChange.run();
    }
}
