package com.koibots.scout.hub.sftp;

/**
 * Information about a remote SFTP connection.
 */
public class RemoteInfo
{
    private final String host;
    private final String username;
    private final String path;
    private int port = 22;
    private String knownHostsFile;
    private String keyFile;
    private String password;

    private boolean disableKeyChecking = false;

    public RemoteInfo(String host, String username, String path) {
        this.host = host;
        this.username = username;
        this.path = path;
    }

    public String getHost() {
        return host;
    }

    public String getUsername() {
        return username;
    }

    public String getPath() {
        return path;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setKeyFile(String keyFile) {
        this.keyFile = keyFile;
    }

    public String getKeyFile() {
        return keyFile;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    String getPassword() {
        return password;
    }

    public void setKnownHostsFile(String knownHostsFile) {
        this.knownHostsFile = knownHostsFile;
    }

    public String getKnownHostsFile() {
        return knownHostsFile;
    }

    /**
     * Sets the flag to disable key checking.
     *
     * NOTE: Setting this flag to TRUE is <b>unsafe</b> and exposes the user
     * to an adversary-in-the-middle attack.
     *
     * @param disable <code>true</code> to disable host key checking,
     *                <code>false</code> to enable host key checking.
     */
    public void setDisableKeyChecking(boolean disable) {
        this.disableKeyChecking = disable;
    }

    public boolean getDisableKeyChecking() {
        return disableKeyChecking;
    }
}