package com.bw.jPdfTool;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public class Preferences {

    /**
     * User preferences key for the last PDF directory the user selected.
     */
    public final static String USER_PREF_LAST_PDF_DIR = "last-dir";

    /**
     * User preferences key for the last image directory the user selected.
     */
    public final static String USER_PREF_LAST_IMAGE_DIR = "last-image-dir";

    /**
     * User preferences key for the last Look And Feel selected.
     */
    public final static String USER_PREF_LAF = "laf";

    /**
     * User preferences key for the owner password option.
     */
    public final static String USER_PREF_STORE_OWNER_PASSWORD = "store-owner-password";

    /**
     * User preferences key for the Encryption Key Length.
     */
    public final static String USER_ENCRYPTION_KEY_LENGTH = "encryption-key-length";

    /**
     * User preferences key for the dpi quality option.
     */
    public final static String USER_PREF_DPI = "dpi";

    public final static String USER_PREF_VIEWER_ANTIALIASING = "ViewerAA";
    public final static boolean USER_PREF_VIEWER_ANTIALIASING_DEFAULT = true;

    public final static String USER_PREF_VIEWER_RENDER_QUALITY = "viewer-renderquality";

    public final static String USER_PREF_VIEWER_INTERPOLATE_BI_CUBIC = "viewer-render-bicubic";

    /**
     * User preferences key for the owner password (if option is enabled).
     */
    public final static String USER_PREF_OWNER_PASSWORD = "ownerpassword";

    public final static String LAF_DARK_CLASSNAME = "com.formdev.flatlaf.FlatDarkLaf";
    public final static String LAF_LIGHT_CLASSNAME = "com.formdev.flatlaf.FlatLightLaf";


    public final static String DEFAULT_LAF = LAF_LIGHT_CLASSNAME;

    private final java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userRoot().node("jpdftool");

    private Preferences() {
        prefs.addPreferenceChangeListener(evt -> {
            Log.debug("Preference changed: %s %s", evt.getKey(), evt.getNewValue());
            support.firePropertyChange(evt.getKey(), null, evt.getNewValue());
        });
    }

    private static final Preferences instance = new Preferences();

    public static Preferences getInstance() {
        return instance;
    }

    private final PropertyChangeSupport support = new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener listener, String... keys) {
        for (String k : keys)
            support.addPropertyChangeListener(k, listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener, String... keys) {
        for (String k : keys)
            support.removePropertyChangeListener(k, listener);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return prefs.getBoolean(key, defaultValue);
    }


    public int getInt(String key, int defaultValue) {
        return prefs.getInt(key, defaultValue);
    }

    public String getString(String key, String defaultValue) {
        return prefs.get(key, defaultValue);
    }

    public void set(String key, String value) {
        if (value == null) {
            if (prefs.get(key, null) != null)
                prefs.remove(key);
        } else if (!value.equals(prefs.get(key, null)))
            prefs.put(key, value);
    }

    public void set(String key, boolean value) {
        prefs.putBoolean(key, value);
    }

    public void set(String key, int value) {
        prefs.putInt(key, value);
    }

    public void remove(String key) {
        prefs.remove(key);
    }
}
