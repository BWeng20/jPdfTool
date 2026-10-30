package com.bw.jPdfTool.ui;

import com.bw.jPdfTool.Log;
import com.bw.jPdfTool.Preferences;
import com.bw.jPdfTool.ValidationResult;
import com.bw.jPdfTool.invoice.EInvoice;
import com.bw.jPdfTool.model.DocumentProxy;
import com.bw.jPdfTool.toast.ToastType;
import com.bw.jPdfTool.toast.Toaster;
import com.bw.jPdfTool.ui.invoice.InvoiceDialog;
import com.bw.jPdfTool.ui.invoice.InvoiceEditorDialog;
import com.bw.jPdfTool.ui.invoice.PartySettingsDialog;
import com.bw.jPdfTool.ui.invoice.ValidationDialog;

import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.function.Supplier;

/**
 * The validation and e-invoice (ZUGFeRD/Factur-X) menus of the {@link UI} and their actions.
 */
final class InvoiceActions {

    private static final String ATTACH_INVOICE = "Attach Invoice XML…";
    private static final String ATTACH_INVOICE_TOOLTIP =
            "Attaches a ZUGFeRD/Factur-X (CII) XML. An existing invoice is replaced.";

    private final UI ui;
    private final Supplier<DocumentProxy> proxySupplier;
    private final Supplier<Toaster> toasterSupplier;

    InvoiceActions(UI ui, Supplier<DocumentProxy> proxySupplier, Supplier<Toaster> toasterSupplier) {
        this.ui = ui;
        this.proxySupplier = proxySupplier;
        this.toasterSupplier = toasterSupplier;
    }

    private DocumentProxy proxy() {
        return proxySupplier.get();
    }

    private Toaster toaster() {
        return toasterSupplier.get();
    }

    /**
     * Creates the menu with the validations of the current document. None of them changes the document.
     */
    JMenu createValidationMenu() {
        JMenu menu = new JMenu("Validation");
        JMenuItem pdfa = new JMenuItem("Validate PDF/A…");
        pdfa.setToolTipText("Checks the current document with veraPDF against the PDF/A flavour it declares.");
        pdfa.addActionListener(e -> doValidatePdfa());
        JMenuItem invoice = new JMenuItem("Validate Invoice…");
        invoice.setToolTipText("Checks PDF/A-3 and the EN 16931 rules of the current document.");
        invoice.addActionListener(e -> doValidateInvoice());
        menu.add(pdfa);
        menu.add(invoice);
        menu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                boolean hasDocument = proxy() != null && proxy().getDocument() != null;
                pdfa.setEnabled(hasDocument);
                invoice.setEnabled(hasDocument);
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {
            }
        });
        return menu;
    }

    /**
     * Creates the menu with the e-invoice (ZUGFeRD/Factur-X) actions.
     */
    JMenu createInvoiceMenu() {
        JMenu menu = new JMenu("Invoice");

        JMenuItem show = new JMenuItem("Show Invoice…");
        show.addActionListener(e -> doShowInvoice());
        JMenuItem edit = new JMenuItem("Create Invoice…");
        edit.addActionListener(e -> doEditInvoice());
        JMenuItem save = new JMenuItem("Save Invoice XML…");
        save.addActionListener(e -> doSaveInvoiceXml());
        JMenuItem attach = new JMenuItem(ATTACH_INVOICE);
        attach.setToolTipText(ATTACH_INVOICE_TOOLTIP);
        attach.addActionListener(e -> doAttachInvoiceXml());
        JMenuItem remove = new JMenuItem("Remove Invoice");
        remove.addActionListener(e -> doRemoveInvoice());

        menu.add(show);
        menu.add(edit);
        menu.addSeparator();
        menu.add(save);
        menu.add(attach);
        menu.add(remove);
        menu.addSeparator();
        JMenuItem parties = new JMenuItem("Invoice Parties…");
        parties.setToolTipText("Seller and buyer presets for the invoice editor.");
        parties.addActionListener(e -> PartySettingsDialog.showDialog(ui));
        menu.add(parties);

        menu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                boolean hasDocument = proxy() != null && proxy().getDocument() != null;
                boolean hasInvoice = hasDocument && EInvoice.hasInvoice(proxy().getDocument());
                show.setEnabled(hasInvoice);
                save.setEnabled(hasInvoice);
                remove.setEnabled(hasInvoice);

                boolean supported = hasDocument && EInvoice.supportsInvoice(proxy().getDocument());
                attach.setEnabled(supported);
                edit.setEnabled(supported);
                edit.setText(hasInvoice ? "Edit Invoice…" : "Create Invoice…");
                edit.setToolTipText(hasDocument && !supported
                        ? "<html>E-invoices need PDF/A-3, which is based on PDF 1.7.<br>A PDF 2.0 document can't be conform.</html>"
                        : "Enter the invoice data. The invoice is attached as ZUGFeRD/Factur-X XML.");
                if (hasDocument && !supported) {
                    attach.setText(ATTACH_INVOICE + " (not for PDF 2.0)");
                    attach.setToolTipText("<html>E-invoices need PDF/A-3, which is based on PDF 1.7.<br>"
                            + "A PDF 2.0 document can't be conform.</html>");
                } else {
                    attach.setText(ATTACH_INVOICE);
                    attach.setToolTipText(ATTACH_INVOICE_TOOLTIP);
                }
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {
            }
        });
        return menu;
    }

    private void showText(String title, String text) {
        JTextArea area = new JTextArea(text, 24, 90);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, area.getFont().getSize()));
        area.setCaretPosition(0);
        JOptionPane.showMessageDialog(ui, new JScrollPane(area), title, JOptionPane.PLAIN_MESSAGE);
    }

    /**
     * Reports an error. Long or multi-line messages (e.g. validation results) don't fit into a toast,
     * they are shown in a dialog.
     */
    void invoiceError(String what, Exception e) {
        Log.exception(e);
        String message = e.getMessage() == null ? e.toString() : e.getMessage();
        if (message.length() > 80 || message.indexOf('\n') >= 0) {
            toaster().toast(ToastType.ERROR, 10000,
                    "<html><font size='+2'>%s</font><br><font size='+1'><i>See the details dialog</i></font></html>", what);
            SwingUtilities.invokeLater(() -> showText(what, message));
        } else {
            toaster().toast(ToastType.ERROR, 20000,
                    "<html><font size='+2'>%s</font><br><font size='+1'><i>%s</i></font></html>", what, message);
        }
    }

    private void doShowInvoice() {
        if (proxy() == null || proxy().getDocument() == null)
            return;
        try {
            var xml = EInvoice.extractXml(proxy().getDocument());
            if (xml.isEmpty()) {
                toaster().toast(ToastType.INFO, 5000, "<html>The document contains no invoice</html>");
                return;
            }
            InvoiceDialog.showDialog(ui, xml.get());
        } catch (IOException e) {
            invoiceError("Failed to read invoice", e);
        }
    }

    /**
     * Opens the invoice editor. Edits the existing invoice of the document or creates a new one.
     */
    private void doEditInvoice() {
        if (proxy() == null || proxy().getDocument() == null)
            return;
        byte[] xml;
        try {
            xml = EInvoice.extractXml(proxy().getDocument()).orElse(null);
        } catch (IOException e) {
            invoiceError("Failed to read invoice", e);
            return;
        }
        if (InvoiceEditorDialog.showDialog(ui, xml, proxy()))
            toaster().toast(ToastType.SUCCESS, 5000, "<html>%s invoice</html>", xml == null ? "Attached" : "Updated");
    }

    void doValidateInvoice() {
        if (proxy() == null || proxy().getDocument() == null)
            return;
        ui.runInBusy(() -> {
            try {
                EInvoice.Validation v = EInvoice.validate(proxy().getBytes(), "current document");
                SwingUtilities.invokeLater(() -> ValidationDialog.showDialog(ui, v));
            } catch (Exception e) {
                invoiceError("Validation failed", e);
            }
        });
    }

    /**
     * Checks the document with veraPDF, independent of the PDF/A level, and shows the result.
     */
    void doValidatePdfa() {
        if (proxy() == null || proxy().getDocument() == null)
            return;
        ui.runInBusy(() -> {
            try {
                ValidationResult result = proxy().validatePdfa();
                SwingUtilities.invokeLater(() -> {
                    VerificationResultPanel panel = new VerificationResultPanel();
                    panel.setResult(result);
                    JOptionPane.showMessageDialog(ui, panel, "PDF/A", switch (result.getState()) {
                        case CONFORM -> JOptionPane.INFORMATION_MESSAGE;
                        case NOT_DECLARED -> JOptionPane.WARNING_MESSAGE;
                        default -> JOptionPane.ERROR_MESSAGE;
                    });
                });
            } catch (Exception e) {
                invoiceError("Validation failed", e);
            }
        });
    }

    private File chooseXml(boolean save) {
        JFileChooser chooser = new JFileChooser();
        String lastDir = UI.getPref(Preferences.USER_PREF_LAST_PDF_DIR, null);
        if (lastDir != null)
            chooser.setCurrentDirectory(new File(lastDir));
        chooser.setFileFilter(new FileNameExtensionFilter("XML", "xml"));
        chooser.setMultiSelectionEnabled(false);
        if (save) {
            chooser.setDialogTitle("Save Invoice XML…");
            chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), "factur-x.xml"));
        } else {
            chooser.setDialogTitle("Select Invoice XML…");
        }
        int result = save ? chooser.showSaveDialog(ui) : chooser.showOpenDialog(ui);
        return result == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
    }

    private void doSaveInvoiceXml() {
        if (proxy() == null || proxy().getDocument() == null)
            return;
        try {
            var xml = EInvoice.extractXml(proxy().getDocument());
            if (xml.isEmpty())
                return;
            File file = chooseXml(true);
            if (file != null && (!file.exists() || UI.askOverwrite(ui, file.toPath()))) {
                Files.write(file.toPath(), xml.get());
                toaster().toast(ToastType.SUCCESS, 5000, "<html>Stored invoice to<br><i>%s</i></html>", file.getName());
            }
        } catch (IOException e) {
            invoiceError("Failed to store invoice", e);
        }
    }

    private void doAttachInvoiceXml() {
        if (proxy() == null || proxy().getDocument() == null)
            return;
        File file = chooseXml(false);
        if (file != null) {
            ui.runInBusy(() -> {
                try {
                    proxy().attachInvoice(Files.readAllBytes(file.toPath()));
                    toaster().toast(ToastType.SUCCESS, 5000, "<html>Attached invoice<br><i>%s</i></html>", file.getName());
                } catch (Exception e) {
                    invoiceError("Failed to attach invoice", e);
                }
            });
        }
    }

    private void doRemoveInvoice() {
        if (proxy() == null || proxy().getDocument() == null)
            return;
        try {
            if (proxy().removeInvoice())
                toaster().toast(ToastType.SUCCESS, 5000, "<html>Removed invoice</html>");
        } catch (IOException e) {
            invoiceError("Failed to remove invoice", e);
        }
    }
}
