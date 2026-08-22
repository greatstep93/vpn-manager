package ru.greatstep.vpnmanager.utils;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class PreferencesManager {
    private static final String PREFS_NODE = "vpnmanager";
    private static final String KEY_HOST = "host";
    private static final String KEY_PORT = "port";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_SAVE_CREDENTIALS = "saveCredentials";

    private final Preferences prefs;

    public PreferencesManager() {
        this.prefs = Preferences.userRoot().node(PREFS_NODE);
    }

    public boolean isSaveCredentials() {
        return prefs.getBoolean(KEY_SAVE_CREDENTIALS, false);
    }

    public void setSaveCredentials(boolean save) {
        prefs.putBoolean(KEY_SAVE_CREDENTIALS, save);
    }

    public String getHost() {
        return prefs.get(KEY_HOST, "");
    }

    public void setHost(String host) {
        prefs.put(KEY_HOST, host);
    }

    public String getPort() {
        return prefs.get(KEY_PORT, "22");
    }

    public void setPort(String port) {
        prefs.put(KEY_PORT, port);
    }

    public String getUsername() {
        return prefs.get(KEY_USERNAME, "root");
    }

    public void setUsername(String username) {
        prefs.put(KEY_USERNAME, username);
    }

    public String getPassword() {
        return prefs.get(KEY_PASSWORD, "");
    }

    public void setPassword(String password) {
        prefs.put(KEY_PASSWORD, password);
    }

    public void clear() throws BackingStoreException {
        prefs.clear();
    }

    public void saveCredentials(String host, String port, String username, String password) {
        setHost(host);
        setPort(port);
        setUsername(username);
        setPassword(password);
    }

    public Credentials loadCredentials() {
        return new Credentials(getHost(), getPort(), getUsername(), getPassword());
    }

    public static class Credentials {
        public final String host;
        public final String port;
        public final String username;
        public final String password;

        public Credentials(String host, String port, String username, String password) {
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
        }
    }
}