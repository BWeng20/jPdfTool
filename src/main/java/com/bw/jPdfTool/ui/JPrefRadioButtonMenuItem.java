package com.bw.jPdfTool.ui;

import com.bw.jPdfTool.Preferences;

import javax.swing.JRadioButtonMenuItem;

/**
 * A Menu Radio Button that is bound to a boolean preferences values.
 */
public class JPrefRadioButtonMenuItem extends JRadioButtonMenuItem {

    final String prefKey;
    final boolean negate;
    final boolean defaultValue;

    public JPrefRadioButtonMenuItem(String label, String key, boolean negate, boolean defaultValue) {
        super(label);
        this.prefKey = key;
        this.negate = negate;
        this.defaultValue = defaultValue;

        Preferences.getInstance().addPropertyChangeListener(evt -> update(), prefKey);
        update();
        addActionListener(e -> {
            boolean sel = isSelected();
            if (negate)
                sel = !sel;
            Preferences.getInstance().set(prefKey, sel);
        });
    }

    protected void update() {
        boolean val = Preferences.getInstance().getBoolean(prefKey, negate == (!defaultValue));
        if (negate)
            val = !val;
        if (val != isSelected())
            setSelected(val);
    }

}
