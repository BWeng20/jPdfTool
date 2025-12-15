package com.bw.jPdfTool.ui;

import com.bw.jPdfTool.Preferences;

import javax.swing.JCheckBoxMenuItem;

/**
 * A Menu Checkbox that is bound to a boolean preferences values.
 */
public class JPrefCheckBoxMenuItem extends JCheckBoxMenuItem {

    final String prefKey;
    final boolean defaultValue;

    public JPrefCheckBoxMenuItem(String label, final String prefKey, final boolean defaultValue) {
        super(label);
        this.prefKey = prefKey;
        this.defaultValue = defaultValue;
        Preferences.getInstance().addPropertyChangeListener(evt -> update(), prefKey);
        update();
        addActionListener(e -> Preferences.getInstance().set(prefKey, isSelected()));
    }

    protected void update() {
        boolean val = Preferences.getInstance().getBoolean(prefKey, defaultValue);
        if (val != isSelected())
            setSelected(val);
    }
}
