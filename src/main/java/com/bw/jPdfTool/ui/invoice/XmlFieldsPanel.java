package com.bw.jPdfTool.ui.invoice;

import com.bw.jPdfTool.invoice.InvoiceData;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * All values of the invoice XML as a table: path, attributes and value. Values and attributes can be edited,
 * values can be removed. Used for data without a field in the editor.
 */
class XmlFieldsPanel extends JPanel {

    private static final int COL_PATH = 0;
    private static final int COL_ATTRIBUTES = 1;
    private static final int COL_VALUE = 2;

    /**
     * Shown without this common prefix, it's part of almost every path.
     */
    private static final String COMMON_PREFIX = "SupplyChainTradeTransaction/";

    private final InvoiceData data;
    private final Runnable onChange;
    private final JTextField filter = new JTextField(16);
    private final FieldModel model = new FieldModel();
    private final JTable table = new JTable(model);
    private final JButton remove = new JButton("Remove");
    private final JLabel count = new JLabel();

    /**
     * The fields that match the filter.
     */
    private List<InvoiceData.XmlField> visible = new ArrayList<>();

    /**
     * @param onChange Called after the XML was changed.
     */
    XmlFieldsPanel(InvoiceData data, Runnable onChange) {
        super(new BorderLayout(0, 6));
        this.data = data;
        this.onChange = onChange;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        table.setSurrendersFocusOnKeystroke(true);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(COL_PATH).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                setToolTipText(value == null ? null : value.toString().replace("/", " / "));
                return this;
            }
        });
        int em = table.getFontMetrics(table.getFont()).charWidth('m');
        table.getColumnModel().getColumn(COL_PATH).setPreferredWidth(34 * em);
        table.getColumnModel().getColumn(COL_ATTRIBUTES).setPreferredWidth(9 * em);
        table.getColumnModel().getColumn(COL_VALUE).setPreferredWidth(16 * em);
        table.getSelectionModel().addListSelectionListener(e -> updateRemove());

        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(59 * em, table.getRowHeight() * 9 + table.getTableHeader().getPreferredSize().height));

        filter.putClientProperty("JTextField.placeholderText", "Filter");
        filter.putClientProperty("JTextField.showClearButton", Boolean.TRUE);
        filter.setToolTipText("Shows only values whose path or value contains this text.");
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refresh();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refresh();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refresh();
            }
        });

        remove.setToolTipText("Removes the selected values from the invoice.");
        remove.addActionListener(e -> removeSelected());

        JLabel hint = new JLabel("Changes here are applied to the invoice directly. Sums are calculated again.");
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));
        count.setForeground(UIManager.getColor("Label.disabledForeground"));

        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.add(filter, BorderLayout.WEST);
        top.add(hint, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        buttons.add(remove);
        bottom.add(buttons, BorderLayout.WEST);
        bottom.add(count, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        updateRemove();
    }

    /**
     * Reads the values from the XML again. The selection is kept as far as possible.
     */
    void refresh() {
        if (table.isEditing())
            table.getCellEditor().cancelCellEditing();
        List<String> selected = new ArrayList<>();
        for (int row : table.getSelectedRows())
            selected.add(visible.get(row).path());

        String f = filter.getText().trim().toLowerCase(Locale.ROOT);
        List<InvoiceData.XmlField> all = data.xmlFields();
        visible = new ArrayList<>();
        for (InvoiceData.XmlField x : all)
            if (f.isEmpty() || x.path().toLowerCase(Locale.ROOT).contains(f)
                    || x.value().toLowerCase(Locale.ROOT).contains(f))
                visible.add(x);
        model.fireTableDataChanged();
        for (int i = 0; i < visible.size(); ++i)
            if (selected.contains(visible.get(i).path()))
                table.addRowSelectionInterval(i, i);
        count.setText(visible.size() == all.size() ? all.size() + " values" : visible.size() + " of " + all.size() + " values");
        updateRemove();
    }

    private void updateRemove() {
        boolean removable = false;
        for (int row : table.getSelectedRows())
            removable |= visible.get(row).isRemovable();
        remove.setEnabled(removable);
    }

    private void removeSelected() {
        if (table.isEditing())
            table.getCellEditor().cancelCellEditing();
        int[] rows = table.getSelectedRows();
        if (rows.length == 0)
            return;
        for (int row : rows)
            data.remove(visible.get(row));
        table.clearSelection();
        changed();
    }

    private void changed() {
        onChange.run();
        refresh();
    }

    private class FieldModel extends AbstractTableModel {
        private final String[] names = {"Path", "Attributes", "Value"};

        @Override
        public int getRowCount() {
            return visible.size();
        }

        @Override
        public int getColumnCount() {
            return names.length;
        }

        @Override
        public String getColumnName(int column) {
            return names[column];
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column != COL_PATH;
        }

        @Override
        public Object getValueAt(int row, int column) {
            InvoiceData.XmlField f = visible.get(row);
            return switch (column) {
                case COL_PATH -> f.path().startsWith(COMMON_PREFIX) ? f.path().substring(COMMON_PREFIX.length()) : f.path();
                case COL_ATTRIBUTES -> f.attributes();
                default -> f.value();
            };
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            InvoiceData.XmlField f = visible.get(row);
            String v = value == null ? "" : value.toString();
            if (column == COL_VALUE) {
                if (v.trim().equals(f.value().trim()))
                    return;
                f.setValue(v);
            } else if (column == COL_ATTRIBUTES) {
                if (v.trim().equals(f.attributes()))
                    return;
                try {
                    f.setAttributes(v);
                } catch (IllegalArgumentException e) {
                    JOptionPane.showMessageDialog(XmlFieldsPanel.this, e.getMessage(), "Attributes",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
            // Later, the table must not be changed while it finishes editing.
            javax.swing.SwingUtilities.invokeLater(XmlFieldsPanel.this::changed);
        }
    }
}
