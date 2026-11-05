package com.koibots.scout.hub.sftp;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarFile;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.JSchUnknownHostKeyException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.UserInfo;
import com.koibots.scout.hub.ui.UIUtils;

public class SFTPConnection {
    private static final String DEFAULT_ED_KEY_FILE = System.getProperty("user.home") + "/.ssh/id_ed25519";
    private static final String DEFAULT_RSA_KEY_FILE = System.getProperty("user.home") + "/.ssh/id_rsa";
    private static final String DEFAULT_KNOWN_HOSTS_FILE = System.getProperty("user.home") + "/.ssh/known_hosts";
    private static final String DEFAULT_REMOTE_PATH = "/var/www/example.com/qrscout";

    private static void usage(PrintStream out) {
        out.println("Usage: " + SFTPConnection.class.getName() + " [options]");
        out.println();
        out.println("   -c, --clean             Remove all files from the remote directory before uploading (default: false).");
        out.println("   -f, --known-hosts file  The known-hosts file to use (default: " + DEFAULT_KNOWN_HOSTS_FILE + ")");
        out.println("   -h, --help              Print this help message and exit.");
        out.println("   -H, --host hostname     The hostname to connect to.");
        out.println("   -k, --key keyfile       The ssh keyfile to use (default: " + DEFAULT_ED_KEY_FILE + "/" + DEFAULT_RSA_KEY_FILE + ").");
        out.println("   -p, --path path         The remote path to use (default: " + DEFAULT_REMOTE_PATH);
        out.println("   -u, --username name     The remote username to use.");
    }

    public static void main(String[] args) throws Exception {
        String host = null;
        String username = null;
        String path = null;
        String keyFile = null;
        String knownHostsFile = null;
        boolean clean = false;

        int argindex = 0;
        while(argindex < args.length) {
            String arg = args[argindex++];

            if("--help".equals(arg) || "-h".equals(arg)) {
                usage(System.out);
                System.exit(0);
            } else if("--host".equals(arg) || "-H".equals(arg)) {
                host = args[argindex++];
            } else if("--username".equals(arg) || "-u".equals(arg)) {
                username = args[argindex++];
            } else if("--path".equals(arg) || "-p".equals(arg)) {
                path = args[argindex++];
            } else if("--key".equals(arg) || "-k".equals(arg)) {
                keyFile = args[argindex++];
            } else if("--known-hosts".equals(arg) || "-f".equals(arg)) {
                knownHostsFile = args[argindex++];
            } else if("--clean".equals(arg) || "-c".equals(arg)) {
                clean = true;
            } else {
                System.err.println("Unknown option: " + arg);
                System.err.println();

                usage(System.err);

                System.exit(2);
            }
        }

        if(null == host) {
            System.err.println("Must specify host");
            System.err.println();

            usage(System.err);

            System.exit(1);
        }

        if(null == username) {
            System.err.println("Must specify username");
            System.err.println();

            usage(System.err);

            System.exit(1);
        }

        if(null == path) {
            path = DEFAULT_REMOTE_PATH;
        }

        if(null == keyFile) {
            File f = new File(DEFAULT_ED_KEY_FILE);
            if(f.exists() && f.canRead()) {
                keyFile = DEFAULT_ED_KEY_FILE;
            } else {
                f = new File(DEFAULT_RSA_KEY_FILE);

                if(f.exists() && f.canRead()) {
                    keyFile = DEFAULT_ED_KEY_FILE;
                }
            }
        }

        if(null == knownHostsFile) {
            knownHostsFile = DEFAULT_KNOWN_HOSTS_FILE;
        }

        RemoteInfo remote = new RemoteInfo(host, username, path);

        remote.setKeyFile(keyFile);

        if(null == keyFile) {
            remote.setPassword(new String(System.console().readPassword("Password for " + username + "@" + host + ": ")));
        } else {
            remote.setPassword(new String(System.console().readPassword("Password for " + remote.getKeyFile() + ": ")));
        }
        remote.setKnownHostsFile(knownHostsFile);

        SFTPConnection util = new SFTPConnection(remote);

        try {
            util.init();

            try {
                util.connect();
            } catch (UntrustedHostException uhe) {
                System.out.println("Untrusted host, checking...");
                System.out.println("Trusted? " + util.checkTrust(uhe.getPublicKey()));

                System.out.println("Untrusted key found for host " + remote.getHost() + ": " + uhe.getKeyType() + ":" + uhe.getFingerprint());
                String trust = System.console().readLine("Trust this key (y=once/a=always/N=no): ");

                if("Y".equals(trust) || "y".equals(trust)) {
                    remote.setDisableKeyChecking(true);

                    util.connect();
                } else if("A".equals(trust) || "a".equals(trust)) {
                    util.addTrust(uhe.getPublicKey());

                    util.connect();
                } else {
                    System.out.println("Aborting connection.");

                    System.exit(2);
                }
            }

            SftpATTRS attrs = util.safelstat(remote.getPath());

            if(attrs.isDir()) {
                // Great, this directory already exists.
                if(clean) {
                    System.out.println("Cleaning " + remote.getPath());
                    util.removeAllFiles(remote.getPath(), true, new MyCleanupMonitor(remote.getPath()));
                }
            } else if(-1 == attrs.getSize()) {
                // Nothing there; must create directory

                System.out.println("Creating " + remote.getPath());
                util.mkdirs(remote.getPath());
            } else {
                // Can't deal with whatever situation has arisen
                throw new IllegalStateException("Can't figure out what to do with " + remote.getPath());
            }

            util.pushWebapplication(new File("README.md").toPath(), new MyUploadMonitor(remote.getPath()));
        } finally {
            util.disconnect();
        }
    }

    private static class MyUploadMonitor
        implements UploadMonitor
    {
        protected final String remotePath;
        public MyUploadMonitor(String remotePath) {
            this.remotePath = remotePath;
        }

        @Override
        public void startDir(String path) {
            System.out.println("mkdir(" + remotePath + "/" + path + ")");
        }

        @Override
        public void endDir(String path) {
        }

        @Override
        public void startFile(String path) {
            System.out.println("put(" + remotePath + "/" +  path + ")");
        }

        @Override
        public void endFile(String path) {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public void cancelling() {
        }

        @Override
        public void cancelled() {
        }
    }

    private static class MyCleanupMonitor
        extends MyUploadMonitor
    {
        public MyCleanupMonitor(String remotePath) {
            super(remotePath);
        }

        @Override
        public void startDir(String path) {
            System.out.println("rmdir(" + remotePath + "/" + path + ")");
        }

        @Override
        public void endDir(String path) {
        }

        @Override
        public void startFile(String path) {
            System.out.println("rm(" + remotePath + "/" +  path + ")");
        }
    }

    /**
     * Sets the debug state of this class.
     *
     * @param enable <code>true</code> to enable debug logging,
     *        <code>false</code> to disable such logging.
     */
    public static void enableDebug(boolean enable) {
        if(enable) {
            JSch.setLogger(new com.jcraft.jsch.Logger() {
                @Override
                public boolean isEnabled(int level) {
                    return true;
                }

                @Override
                public void log(int level, String message) {
                    System.out.println(message);
                }
            });
        } else {
            JSch.setLogger(null);
        }
    }

    /**
     * An unchecked wrapper for SftpException for internal handling within
     * lambdas which may not throw checked exceptions.
     */
    private static class UncheckedSftpException
        extends RuntimeException
    {
        private static final long serialVersionUID = -8332677520012801702L;

        public UncheckedSftpException(SftpException cause) {
            super(cause);
        }

        /**
         * Returns the cause of this exception.
         *
         * @return the {@code SftpException} which is the cause of this exception.
         */
        @Override
        public SftpException getCause() {
            return (SftpException) super.getCause();
        }
    }

    private final RemoteInfo remote;
    private JSch jsch;
    private ChannelSftp sftp;
    private Session session;

    public SFTPConnection(RemoteInfo remote) {
        this.remote = remote;
    }

    /**
     * Initializes the SFTP connection.
     *
     * @throws JSchException
     */
    public void init()
        throws SFTPException
    {
        this.jsch = new JSch();

        try {
            if(null != remote.getKeyFile()) {
                // Load the OpenSSH private key
                jsch.addIdentity(remote.getKeyFile(), toBytes(remote.getPassword(), StandardCharsets.UTF_8));
            }
            if(null != remote.getKnownHostsFile()) {
                // Verify the server against known_hosts
                jsch.setKnownHosts(remote.getKnownHostsFile());
            }
        } catch (JSchException je) {
            throw new SFTPException("Failed to initialize SFTP connection", je);
        }
    }

    /**
     * Checks the trust store for the public key for the host.
     *
     * @param publicKey The public key of the host being checked.
     *
     * @return <code>true</code> if the key is already trusted for the host,
     *         <code>false</code> if the key is not yet trusted.
     *
     * @throws UntrustedHostException If the host already trusts a different
     *         key. This could mean that someone is impersonating the server,
     *         or just that the server's identity changed.
     */
    public boolean checkTrust(String publicKey)
        throws UntrustedHostException
    {
        if(null == jsch) {
            throw new IllegalStateException("You must initialize this SFTPConnection before checking trust");
        }

        byte[] pk = Base64.getDecoder().decode(publicKey);

        int status = jsch.getHostKeyRepository().check(
                remote.getHost(),
                pk
        );

        switch(status) {
            case HostKeyRepository.OK:
                return true;

            case HostKeyRepository.NOT_INCLUDED:
                return false;

            case HostKeyRepository.CHANGED:
                try {
                    HostKey hk = new HostKey(remote.getHost(), pk);

                    throw new UntrustedHostException(remote.getHost(), hk.getType(), publicKey, hk.getFingerPrint(jsch), null);
                } catch (JSchException je) {
                    throw new IllegalStateException("Bad public key");
                }

            default:
                throw new IllegalStateException("Unexpected host key status: " + status);
        }
    }

    /**
     * Adds the public key to the trust store.
     *
     * @param host The name of the host.
     * @param publicKey The public key belonging to the host.
     *
     * @throws SFTPException If there is a problem adding the host key to the
     *         trust store.
     */
    public void addTrust(String publicKey)
        throws SFTPException, IOException
    {
        if(null == jsch) {
            throw new IllegalStateException("You must initialize this SFTPConnection before checking trust");
        }

        if(null == remote.getKnownHostsFile()) {
            throw new IllegalStateException("No known hosts file specified");
        }

        File file = new File(remote.getKnownHostsFile());

        if(file.exists()) {
            if(!file.isFile()) {
                throw new IOException("Known hosts file " + remote.getKnownHostsFile() + " is not a file");
            }
            if(!file.canWrite()) {
                throw new IOException("Known hosts file is not writable");
            }
        }

        File parent = file.getAbsoluteFile().getParentFile();

        if(!parent.exists()) {
            Files.createDirectories(parent.toPath());
        }

        if(!parent.isDirectory()) {
            throw new IOException("Known hosts directory is not a directory: " + parent);
        }

        if (!parent.canWrite()) {
            throw new IOException("Known hosts directory is not writable: " + parent);
        }

        byte[] pk = Base64.getDecoder().decode(publicKey);

        try {
            HostKey hk = new HostKey(remote.getHost(), pk);

            jsch.getHostKeyRepository().add(hk, getDummyUserInfo());
        } catch (JSchException je) {
            throw new SFTPException("Failed to add host key to trust store", je);
        }
    }

    private static UserInfo getDummyUserInfo() {
        return new UserInfo() {
            @Override
            public boolean promptYesNo(String message) {
                return true;
            }

            @Override
            public void showMessage(String message) {
                System.out.println(message);
            }

            @Override
            public String getPassphrase() {
                return null;
            }

            @Override
            public String getPassword() {
                return null;
            }

            @Override
            public boolean promptPassphrase(String message) {
                return false;
            }

            @Override
            public boolean promptPassword(String message) {
                return false;
            }
        };
    }

    /**
     * Connects to the SFTP server.
     *
     * @throws SFTPException If there is a problem connecting to the server.
     */
    public void connect()
        throws SFTPException
    {
        if(null != session) {
            throw new IllegalStateException("Session already connected");
        }
        if(null != sftp) {
            throw new IllegalStateException("Sftp already connected");
        }

        Session lSession = null;
        ChannelSftp lSftp = null;
        try {
            // Build locally, then set members on success
            lSession = jsch.getSession(remote.getUsername(), remote.getHost(), remote.getPort());

            lSession.setTimeout(10000); // 10-second connect timeout

            if(null == remote.getKeyFile()) {
                // Using password authentication
                lSession.setPassword(toBytes(remote.getPassword(), StandardCharsets.UTF_8));
            }

            if(remote.getDisableKeyChecking()) {
System.out.println("NOTE: disabling server key checking -- INSECURE!");
                lSession.setConfig("StrictHostKeyChecking", "no");
            }

            lSession.connect();

            lSftp = (ChannelSftp) lSession.openChannel("sftp");
            lSftp.connect();

            this.session = lSession;
            this.sftp = lSftp;

            // Ownership has transferred from the method to the class.
            // (Don't disconnect at the end of this method.)
            lSession = null;
            lSftp = null;
        } catch (JSchUnknownHostKeyException juhke) {
            if(remote.getDisableKeyChecking()) {
                // This is a surprise: we weren't enforcing trust and yet
                // we got a trust failure? Weird.
                throw new SFTPException("Host key failed verification (which should have ben disabled)", juhke);
            }

            // Unfortunately, this exception doesn't really expose the host
            // key so we need to fetch it again.
            try {
                HostKey hostKey = getHostKey(remote.getHost(), remote.getPort(), remote.getKnownHostsFile(), remote.getKeyFile(), remote.getUsername(), remote.getPassword());

                throw new UntrustedHostException(remote.getHost(), hostKey.getType(), hostKey.getKey(), hostKey.getFingerPrint(jsch), juhke);
            } catch (JSchException je) {
                juhke.addSuppressed(je);
System.err.println("Failed to get host key info");
je.printStackTrace();
System.err.println("First exception: " + juhke);
juhke.printStackTrace();
                throw new SFTPException("Failed to connect to SFTP server", juhke);
            }
        } catch (JSchException je) {
            throw new SFTPException("Failed to connect to SFTP server", je);
        } finally {
            if(null != lSftp) {
                lSftp.disconnect();
            }
            if(null != lSession) {
                lSession.disconnect();
            }
        }
    }

    // NOTE: Use a static method to be sure not to pollute the "real"
    // connection-tracking objects for this Connection
    private static HostKey getHostKey(String host, int port, String knownHostsFile, String keyFile, String username, String password)
        throws JSchException
    {
        JSch jsch = new JSch();

        if(null != keyFile) {
            // Load the OpenSSH private key
            jsch.addIdentity(keyFile, toBytes(password, StandardCharsets.UTF_8));
        }
        if(null != knownHostsFile) {
            // Verify the server against known_hosts
            jsch.setKnownHosts(knownHostsFile);
        }

        Session session = null;
        try {
            session = jsch.getSession(username, host, port);

            // Temporarily allow unknown hosts for this probe only
            session.setConfig("StrictHostKeyChecking", "no");

            session.connect();

            return session.getHostKey();
        } finally {
            if(null != session) {
                session.disconnect();
            }
        }
    }

    /**
     * Disconnects from the SFTP server.
     */
    public void disconnect() {
        if(null != sftp) {
            sftp.disconnect();
            sftp = null;
        }
        if(null != session) {
            session.disconnect();
            session = null;
        }
    }

    public interface UploadMonitor {
        public void startDir(String path);

        public void endDir(String path);

        public void startFile(String path);

        public void endFile(String path);

        /**
         * Returns <code>true</code> if the operation should be cancelled.

         * @return <code> if the operation should be cancelled,
         *         <code>false</code> otherwise.
         */
        public boolean isCancelled();

        /**
         * Called when the operation has acknowledged the cancel request.
         */
        public void cancelling();

        /**
         * Called when the operation has been successfully cancelled.
         */
        public void cancelled();
    }

    /**
     * Copies the web application files up to the SFTP server.
     *
     * @param configJson The path to the local config JSON file for the
     * project.
     *
     * @return The number of files successfully uploaded to the server.
     *
     * @throws SFTPException If there is a problem communicating with the SFTP server.
     * @throws IOException If there is a problem reading or writing files.
     */
    public int pushWebapplication(Path configJson, UploadMonitor monitor)
        throws SFTPException, IOException
    {
        if(null == sftp) {
            throw new IllegalStateException("Not connected");
        }

        if(null != monitor && monitor.isCancelled()) {
            monitor.cancelling();
            monitor.cancelled();

            return 0;
        }

        AtomicInteger copiedFiles = new AtomicInteger(0);
        AtomicBoolean cancelled = new AtomicBoolean();;

        final String sourceResourceName = "qrscout"; // Relative without leading /
        final String remoteBasePath = remote.getPath();
        try {
            URL url = getClass().getClassLoader().getResource(sourceResourceName);
            if (url != null && url.getProtocol().equals("file")) {
                // Exploded classpath
                Path srcDir;

                try {
                    srcDir = Paths.get(url.toURI());
                } catch (URISyntaxException use) {
                    // This should never happen
                    throw new IllegalStateException("ClassLoader.getResource returned an invalid URI", use);
                }

                Files.walk(srcDir).forEach(p -> {
                    if(null != monitor && monitor.isCancelled()) {
                        monitor.cancelling();
                        cancelled.set(true);

                        return;
                    }

                    try {
                        Path relative = srcDir.relativize(p);
                        String entryName = relative.toString().replace(File.separatorChar, '/'); // Use forward-slash for all paths
                        if(Files.isDirectory(p)) {
                            String dirname = remoteBasePath + "/" + entryName;
                            if(null != monitor) {
                                monitor.startDir(relative.toString());
                            }
                            mkdirs(dirname);
                            if(null != monitor) {
                                monitor.endDir(relative.toString());
                            }
                        } else {
                            String filename = remoteBasePath + "/" + entryName;
                            try (InputStream in = Files.newInputStream(p, StandardOpenOption.READ)) {
                                if(null != monitor) {
                                    monitor.startFile(relative.toString());
                                }
                                sftp.put(in, filename, null);
                                if(null != monitor) {
                                    monitor.endFile(relative.toString());
                                }
                            }

                            copiedFiles.incrementAndGet();
                        }
                    } catch (IOException e) {
                        // Must wrap this in an unchecked exception because
                        // Consumer.accept (which is what this lambda
                        // actually is) doesn't allow any checked
                        // exceptions.
                        //
                        // We will unwrap it later if it gets thrown,
                        // and re-throw the original exception.

                        throw new UncheckedIOException(e);
                    } catch (SftpException se) {
                        throw new UncheckedSftpException(se);
                    }
                });
            } else {
                // Running from JAR: enumerate current JAR
                String jarPath = getClass()
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .getPath();

                String pathPrefix = sourceResourceName + "/";
                try (JarFile jar = new JarFile(URLDecoder.decode(jarPath, "UTF-8"))) {
                    jar.stream()
                    .filter(e -> e.getName().startsWith(pathPrefix))
                    .forEach(entry -> {
                        if(null != monitor && monitor.isCancelled()) {
                            monitor.cancelling();
                            cancelled.set(true);

                            return;
                        }

                        try {
                            if(entry.isDirectory()) {
                                String relativeDirectory = entry.getName().substring(pathPrefix.length());
                                String dirname = remoteBasePath + "/" + relativeDirectory;
                                if(null != monitor) {
                                    monitor.startDir(relativeDirectory);
                                }
                                mkdirs(dirname);
                                if(null != monitor) {
                                    monitor.endDir(relativeDirectory);
                                }
                            } else {
                                String relativeFilename = entry.getName().substring(pathPrefix.length());
                                String filename = remoteBasePath + "/" + relativeFilename;
                                try (InputStream in = jar.getInputStream(entry)) {

                                    if(null != monitor) {
                                        monitor.startFile(relativeFilename);
                                    }

                                    sftp.put(in, filename);

                                    if(null != monitor) {
                                        monitor.endFile(relativeFilename);
                                    }

                                    copiedFiles.incrementAndGet();
                                }
                            }
                        } catch (IOException ex) {
                            // Must wrap this in an unchecked exception because
                            // Consumer.accept (which is what this lambda
                            // actually is) doesn't allow any checked
                            // exceptions.
                            //
                            // We will unwrap it later if it gets thrown,
                            // and re-throw the original exception.
                            throw new UncheckedIOException(ex);
                        } catch (SftpException se) {
                            throw new UncheckedSftpException(se);
                        }
                    });
                }
            }
        } catch (UncheckedIOException uioe) {
            // Unwrap this unchecked exception
            throw uioe.getCause();
        } catch (UncheckedSftpException use) {
            throw new SFTPException("Failed to copy file to server", use.getCause());
        }

        if(0 == copiedFiles.get()) {
            throw new IOException(UIUtils.getString("error.webApp.noSource.text"));
        }

        if(cancelled.get()) {
            monitor.cancelled();
        } else {
            try (InputStream in = Files.newInputStream(configJson, StandardOpenOption.READ)) {
                if(null != monitor) {
                    monitor.startFile("config.json");
                }

                sftp.put(in, remoteBasePath + "/config.json");

                if(null != monitor) {
                    monitor.endFile("config.json");
                }

                // Don't forget to count the configJson file
                copiedFiles.incrementAndGet();
            } catch (SftpException se) {
                throw new SFTPException("Failed to copy file to server", se);
            }
        }

        return copiedFiles.get();
    }

    /**
     * Performs an <code>lstat</code> operation on a path on the SFTP server.
     * This will not throw an exception if the file does not exist.
     * Instead, it will return a {@link SftpATTRS} object with a file size
     * of <code>-1</code> and no type (e.g. neither file, nor directory, etc.).
     *
     * @param path The path to inspect.
     *
     * @return The SftpATTRS object representing the path. (See notes)
     *
     * @throws SftpException If there is an error accessing the path.
     */
    private SftpATTRS safelstat(String path)
        throws SftpException
    {
        SftpATTRS attrs;
        try {
            attrs = sftp.lstat(path);
        } catch (SftpException se) {
            if(ChannelSftp.SSH_FX_NO_SUCH_FILE == se.id) {
                attrs = new SftpATTRS();

                attrs.setSIZE(-1);
            } else {
                throw se;
            }
        }
        return attrs;
    }

    /**
     * Creates a directory on the remote server, including all necessary
     * prefix paths.
     *
     * @param path The path to create.
     *
     * @throws SftpException If there is a problem creating any part of the
     *         path.
     */
    private void mkdirs(String path)
        throws SftpException
    {
        String current = "";

        for (String part : path.split("/")) {
            if (part.isEmpty()) {
                continue;
            }

            current += "/" + part;

            try {
                sftp.mkdir(current);
            } catch (SftpException e) {
                if (e.id != ChannelSftp.SSH_FX_FAILURE) {
                    throw e;
                }

                // Directory may already exist.
                SftpATTRS attrs = sftp.stat(current);
                if (!attrs.isDir()) {
                    throw e;
                }
            }
        }
    }

    /**
     * Removes all files from a directory on the remote server, leaving
     * that directory in place but empty.
     *
     * @param path The path to empty of files.
     * @param recursive <code>true</code> to descend into sub-directories
     *        and remove their files recursively, <code>false</code> to
     *        remove only files in the <code>path</code>, leaving
     *        sub-directories and their contents.
     *
     * @throws SftpException If there is a problem deleting any file.
     */
    public void removeAllFiles(String path, boolean recursive, UploadMonitor monitor)
        throws SFTPException
    {
        try {
            removeAllFiles(path, path, recursive, monitor);
        } catch (SftpException se) {
            throw new SFTPException("Failed to remove all files", se);
        }
    }

    private void removeAllFiles(String basePath, String path, boolean recursive, UploadMonitor monitor)
        throws SftpException
    {
        Vector<ChannelSftp.LsEntry> entries = sftp.ls(path);

        for (ChannelSftp.LsEntry entry : entries) {
            String name = entry.getFilename();

            if (".".equals(name) || "..".equals(name)) {
                continue;
            }

            String childPath = path + "/" + name;

            if (entry.getAttrs().isDir() && recursive) {
                // Recursively empty the subdirectory
                removeAllFiles(basePath, childPath, recursive, monitor);

                // Remove the now-empty subdirectory
                if(null != monitor) {
                    monitor.startDir(childPath.substring(basePath.length()));
                }

                sftp.rmdir(childPath);

                if(null != monitor) {
                    monitor.endDir(childPath.substring(basePath.length()));
                }
            } else {
                // Remove the file
                if(null != monitor) {
                    monitor.startFile(childPath.substring(basePath.length()));
                }

                sftp.rm(childPath);

                if(null != monitor) {
                    monitor.endFile(childPath.substring(basePath.length()));
                }
            }
        }
    }

    /**
     * Converts a String to bytes.
     *
     * @param s The String to convert; may be <code>null</code>
     * @param cs The character set to use for conversion to bytes.
     *
     * @return An array of bytes representing the string in the specified
     *         character encoding, or <code>null</code> if <code>s</code> was
     *         <code>null</code>
     */
    private static byte[] toBytes(String s, Charset cs) {
        if(null == s) {
            return null;
        } else {
            return s.getBytes(cs);
        }
    }
}
