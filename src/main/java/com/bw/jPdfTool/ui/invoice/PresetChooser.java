package com.bw.jPdfTool.ui.invoice;

import com.bw.jPdfTool.invoice.InvoiceData;
import com.bw.jPdfTool.invoice.Party;
import com.bw.jPdfTool.invoice.Role;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.List;

/**
 * Combo box with the seller or buyer presets and a button to manage them.<br>
 * Choosing a preset copies its values into the target party.
 */
class PresetChooser extends JPanel {

    /**
     * Combo box entry for "no preset".
     */
    private static final Party NO_PRESET = new Party();

    private final Role role;
    private final JComboBox<Party> combo = new JComboBox<>();
    private boolean updating;

    /**
     * @param role      Shows the presets offered for this role.
     * @param target    Receives the values of a chosen preset.
     * @param onApplied Called after a preset was copied into the target (to show the new values).
     */
    PresetChooser(Role role, Party target, Runnable onApplied) {
        super(new BorderLayout(6, 0));
        this.role = role;
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                String text = value == NO_PRESET || value == null ? "—" : PartySettingsDialog.displayName((Party) value);
                return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
            }
        });
        combo.setToolTipText("Fills the " + role.title.toLowerCase() + " data from a preset.");
        reload(target.name);
        combo.addActionListener(e -> {
            if (updating)
                return;
            if (combo.getSelectedItem() instanceof Party preset && preset != NO_PRESET) {
                target.copyFrom(preset);
                onApplied.run();
            }
        });

        JButton manage = new JButton("Manage…");
        manage.setToolTipText("Add, change or remove the sellers and buyers.");
        manage.addActionListener(e -> {
            if (PartySettingsDialog.showDialog(this))
                reload(getSelectedName());
        });

        add(combo, BorderLayout.CENTER);
        add(manage, BorderLayout.EAST);
    }

    /**
     * Name of the selected preset, null if none is selected.
     */
    String getSelectedName() {
        return combo.getSelectedItem() instanceof Party p && p != NO_PRESET ? p.name : null;
    }

    /**
     * Fills the combo box again, without changing the target.
     *
     * @param selectName Name of the preset to select, null for none.
     */
    private void reload(String selectName) {
        updating = true;
        try {
            combo.removeAllItems();
            combo.addItem(NO_PRESET);
            Party selected = NO_PRESET;
            for (Party p : InvoiceData.loadParties(role)) {
                combo.addItem(p);
                if (selected == NO_PRESET && sameName(p.name, selectName))
                    selected = p;
            }
            combo.setSelectedItem(selected);
        } finally {
            updating = false;
        }
    }

    static boolean sameName(String a, String b) {
        return a != null && b != null && !a.isBlank() && a.trim().equalsIgnoreCase(b.trim());
    }

    /**
     * Finds the preset with this name.
     *
     * @return The preset or null.
     */
    static Party find(List<Party> presets, String name) {
        for (Party p : presets)
            if (sameName(p.name, name))
                return p;
        return null;
    }
}
