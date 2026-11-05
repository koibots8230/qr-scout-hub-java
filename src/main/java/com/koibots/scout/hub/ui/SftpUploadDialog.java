package com.koibots.scout.hub.ui;


import javax.swing.*;
import javax.swing.border.EmptyBorder;

import com.koibots.scout.hub.sftp.RemoteInfo;
import com.koibots.scout.hub.sftp.SFTPConnection;
import com.koibots.scout.hub.sftp.SFTPConnection.UploadMonitor;
import com.koibots.scout.hub.sftp.SFTPException;
import com.koibots.scout.hub.sftp.UntrustedHostException;

import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A dialog for configuring an SFTP connection for uploading the web
 * application to the remote server.
 */
public class SftpUploadDialog
    extends JDialog
{
    private static final long serialVersionUID = 5487240798249495268L;

    private final JTextField hostField = new JTextField(25);
    private final JSpinner portSpinner = new JSpinner(new SpinnerNumberModel(22, 1, 65535, 1));
    private final JTextField usernameField = new JTextField(25);
    private final JTextField keyFileField = new JTextField(25);
    private final JPasswordField passwordField = new JPasswordField(25);
    private final JTextField knownHostsField = new JTextField(25);
    private final JTextField serverPathField = new JTextField(25);
    private final JLabel statusLabel = new JLabel("Ready to upload.");

    private final JButton cancelButton;
    private final JButton uploadButton;

    private final Path configJson;

    private boolean uploaded = false;

    public SftpUploadDialog(
            Frame owner,
            String host,
            int port,
            String username,
            String keyFile,
            String knownHostsFile,
            String serverPath,
            Path configJson
            ) {

        super(owner, "SFTP Upload", true);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        UIUtils.setupCloseBehavior(getRootPane(), UIUtils.windowClosingAction);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Connection settings
        JPanel fields = new JPanel(new GridBagLayout());
        fields.setBorder(BorderFactory.createTitledBorder(
                "Connection Settings"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        addField(fields, gbc, 0, "Host:", hostField);
        addField(fields, gbc, 1, "Port:", portSpinner);
        addField(fields, gbc, 2, "Username:", usernameField);
        addField(fields, gbc, 3, "SSH Key File:", keyFileField);
        addField(fields, gbc, 4, "Password / Passphrase:", passwordField);
        addField(fields, gbc, 5, "Known Hosts File:", knownHostsField);
        addField(fields, gbc, 6, "Path on Server:", serverPathField);

        // Initialize from application preferences
        hostField.setText(host);
        portSpinner.setValue(port);
        usernameField.setText(username);
        keyFileField.setText(keyFile);
        serverPathField.setText(serverPath);
        knownHostsField.setText(knownHostsFile);

        // Status area
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createTitledBorder("Status"));
        statusPanel.add(statusLabel, BorderLayout.CENTER);

        // Buttons
        JButton closeButton = new JButton("Close");
        uploadButton = new JButton("Upload");
        cancelButton = new JButton("Cancel");
        cancelButton.setEnabled(false);

        closeButton.addActionListener(e -> dispose());
        cancelButton.addActionListener(e -> cancelUpload());
        uploadButton.addActionListener(e -> startUpload());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(closeButton);
        buttons.add(uploadButton);
        buttons.add(cancelButton);

        content.add(fields, BorderLayout.NORTH);
        content.add(statusPanel, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);

        setContentPane(content);
        getRootPane().setDefaultButton(uploadButton);

        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(owner);

        this.configJson = configJson;
    }

    private void addField(
            JPanel panel,
            GridBagConstraints gbc,
            int row,
            String label,
            JComponent field) {

        gbc.gridy = row;

        gbc.gridx = 0;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(label), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, gbc);
    }

    public String getHost() {
        return hostField.getText().trim();
    }

    public int getPort() {
        return (Integer) portSpinner.getValue();
    }

    public String getUsername() {
        return usernameField.getText().trim();
    }

    public String getKeyFile() {
        return keyFileField.getText().trim();
    }

    public char[] getPassword() {
        return passwordField.getPassword();
    }

    public String getKnownHostsFile() {
        return knownHostsField.getText().trim();
    }

    public String getServerPath() {
        return serverPathField.getText().trim();
    }

    public boolean wasUploaded() {
        return uploaded;
    }

    public void setStatus(String message) {
        if (SwingUtilities.isEventDispatchThread()) {
            statusLabel.setText(message);
        } else {
            SwingUtilities.invokeLater(
                    () -> statusLabel.setText(message));
        }
    }

    private MyUploadMonitor uploadMonitor;

    private void cancelUpload() {
        if(null == uploadMonitor) {
            throw new IllegalStateException("Upload not in progress");
        }

        uploadMonitor.cancel();
    }

    private void startUpload() {
        if(null != uploadMonitor) {
            throw new IllegalStateException("Upload already in progress");
        }

        uploadButton.setEnabled(false);
        cancelButton.setEnabled(true);

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            public Void doInBackground() throws Exception {
                uploadMonitor = new MyUploadMonitor();

                RemoteInfo remote = new RemoteInfo(getHost(), getUsername(), getServerPath());

                setStatus("Initializing connection...");

                String keyFile = getKeyFile();
                if(!keyFile.isBlank()) {
                    remote.setKeyFile(keyFile);
                }

                String knownHostsFile = getKnownHostsFile();
                if(!knownHostsFile.isBlank()) {
                    remote.setKnownHostsFile(knownHostsFile);
                }

                remote.setPassword(new String(getPassword()));

                SFTPConnection conn = null;

                try {
                    conn = new SFTPConnection(remote);

                    if(uploadMonitor.isCancelled()) {
                        uploadMonitor.cancelling();
                        uploadMonitor.cancelled();

                        return null;
                    }

                    conn.init();

                    if(uploadMonitor.isCancelled()) {
                        uploadMonitor.cancelling();
                        uploadMonitor.cancelled();

                        return null;
                    }

                    setStatus("Connecting to server...");

                    try {
                        conn.connect();
                    } catch (UntrustedHostException uhe) {
                        // The client doesn't trust this host.
                        // Let's find out why...

                        try {
                            if(conn.checkTrust(uhe.getPublicKey())) {
                                // Weird; this says it's trusted
                                throw new IllegalStateException("Untrusted host is ... trusted?");
                            } else {
                                // Prompt the user to trust

                                String message =
                                        "Do you trust this SSH server?\n\n" +
                                                "Host: " + uhe.getHost() + "\n" +
                                                "Algorithm: " + uhe.getKeyType() + "\n" +
                                                "Fingerprint: " + uhe.getFingerprint() + "\n\n" +
                                                "Verify this fingerprint with your server administrator.";

                                String[] options;

                                if (remote.getKnownHostsFile() != null) {
                                    options = new String[] {
                                            "Trust Once",
                                            "Trust Permanently",
                                            "Reject"
                                    };
                                } else {
                                    options = new String[] {
                                            "Trust Once",
                                            "Reject"
                                    };
                                }

                                int answer = JOptionPane.showOptionDialog(
                                        SftpUploadDialog.this,
                                        message,
                                        "Confirm SSH Host Identity",
                                        JOptionPane.DEFAULT_OPTION,
                                        JOptionPane.WARNING_MESSAGE,
                                        null,
                                        options,
                                        options[options.length - 1]
                                        );

                                if (answer == 0) {
                                    // Trust once
                                    remote.setDisableKeyChecking(true);
                                    conn.connect();

                                } else if (answer == 1 && remote.getKnownHostsFile() != null) {
                                    // Trust permanently
                                    conn.addTrust(uhe.getPublicKey());
                                    conn.connect();

                                } else {
                                    // Reject, or dialog closed
                                    throw new SFTPException("SSH host key not trusted", uhe);
                                }
                            }
                        } catch (UntrustedHostException uhe2) {
                            // Host has changed its key; deal with this, later

                            throw new UnsupportedOperationException("Can't yet handle changed server key, yet.");
                        }
                    }

                    setStatus("Uploading web application...");

                    int uploadedFiles = conn.pushWebapplication(configJson, uploadMonitor);

                    if(uploadMonitor.isCancelled()) {
                        setStatus("Upload cancelled: " + uploadedFiles + " files uploaded.");
                    } else {
                        setStatus("Upload complete: " + uploadedFiles + " files uploaded.");
                    }
                } finally {
                    if(null != conn) {
                        conn.disconnect();
                    }
                }

                return null;
            }

            @Override
            public void done() {
                try {
                    get();

                    uploaded = !uploadMonitor.isCancelled();
                } catch (Exception e) {
e.printStackTrace();
                    Throwable t = e;
                    while(null != t.getCause()) {
                        t = t.getCause();
                    }

                    statusLabel.setText("Upload failed: "
                            + t.getMessage());

                    JOptionPane.showMessageDialog(
                            SftpUploadDialog.this,
                            t.getMessage(),
                            "Upload Error",
                            JOptionPane.ERROR_MESSAGE);
                }

                cancelButton.setEnabled(false);
                uploadButton.setEnabled(true);
                uploadMonitor = null;
            }
        };

        worker.execute();
    }

    private class MyUploadMonitor
        implements UploadMonitor
    {
        private final AtomicBoolean cancelled = new AtomicBoolean();

        @Override
        public void startDir(String path) {
            setStatus("mkdir(" + path + ")");
        }

        @Override
        public void endDir(String path) {
        }

        @Override
        public void startFile(String path) {
            setStatus("put(" + path + ")");
        }

        @Override
        public void endFile(String path) {
        }

        public void cancel() {
            cancelled.set(true);
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        @Override
        public void cancelling() {
            setStatus("Cancelling...");
        }

        @Override
        public void cancelled() {
            setStatus("Cancelled");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SftpUploadDialog dialog = new SftpUploadDialog(
                    null,
                    "example.com",
                    22,
                    "scott",
                    System.getProperty("user.home") + "/.ssh/id_ed25519",
                    System.getProperty("user.home") + "/.ssh/known_hosts",
                    "/var/www/example.com/qrscout",
                    new File("README.md").toPath()
            );

            dialog.setVisible(true);
        });
    }
}
