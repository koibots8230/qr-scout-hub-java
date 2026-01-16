package com.koibots.scout.hub;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;

/**
 * Implements a very simple HTTP server.
 *
 * This is a single-threaded server that serves files out of a single directory
 * tree.
 */
public class SimpleHttpServer
{
    public interface RequestListener {
        public void requestErrored(Throwable t);
        public void requestProcessed(int responseCode, Path path, long length);
    }

    private int port;
    private final Path documentRoot;

    private ArrayList<RequestListener> listeners;

    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private Thread serverThread;

    public SimpleHttpServer(int port, Path documentRoot) {
        this.port = port;
        this.documentRoot = documentRoot.normalize().toAbsolutePath();
    }

    public SimpleHttpServer(Path documentRoot) throws IOException {
        this(findAvailablePort(8080), documentRoot);
    }

    /**
     * Finds an available TCP/IP port starting with <code>startPort</code> and
     * increasing until one is found.
     *
     * @param startPort The first port to attempt. Recommended value: 8080.
     *
     * @return The lowest-numbered available port greater than or equal to
     *         <code>startPort</code>.
     *
     * @throws IOException If no port can be found.
     */
    private static int findAvailablePort(int startPort)
        throws IOException
    {
        for (int p = startPort; p <= 65535; p++) {
            try (ServerSocket testSocket = new ServerSocket()) {
                testSocket.setReuseAddress(true);
                testSocket.bind(new InetSocketAddress(p));
                return p;
            } catch (IOException ignored) {
                // Port in use, try next
            }
        }
        throw new IOException("No available ports found");
    }

    /**
     * Gets the port on which the server will be running.
     *
     * @return The port on which the server will run.
     */
    public int getPort() {
        return port;
    }

    /**
     * Adds a RequestListener to the list of listeners for request events.
     *
     * @param listener The listener to add.
     */
    public void addRequestListener(RequestListener listener) {
        if(null == this.listeners) {
            this.listeners= new ArrayList<>();
        }

        this.listeners.add(listener);
    }

    /**
     * Starts the server in its own thread.
     */
    public synchronized void start() throws IOException {
        if (running) {
            return;
        }

        Files.createDirectories(documentRoot);
        serverSocket = new ServerSocket(port);
        running = true;

        serverThread = new Thread(this::runServer, "SimpleHttpServer");
        serverThread.start();
    }

    /**
     * Stops the server.
     *
     * Stops accepting new connections, frees all resources, and terminates
     * the request-handling thread.
     */
    public synchronized void shutdown() {
        running = false;

        try {
            if (serverSocket != null) {
                serverSocket.close(); // unblocks accept()
            }
        } catch (IOException ignored) {
        }

        try {
            if (serverThread != null) {
                serverThread.join(); // wait for any in-progress request to complete
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * The server's main loop:
     *
     * 1. Accept a connection
     * 2. Process the request
     * 3. Repeat
     */
    private void runServer() {
        while (running) {
            try (Socket socket = serverSocket.accept()) {
                handleRequest(socket);
            } catch (SocketException e) {
                System.out.println("Caught SocketException during accept(); assuming shutdown");

                break;
            } catch (IOException e) {
                // Log and continue
                publishError(e);
            }
        }
    }

    private void publishError(Throwable t) {
        if(null != this.listeners) {
            for(RequestListener listener : this.listeners) {
                listener.requestErrored(t);
            }
        }
    }

    private void publishRequest(int responseCode, Path path, long length) {
        if(null != this.listeners) {
            for(RequestListener listener : this.listeners) {
                listener.requestProcessed(responseCode, path, length);
            }
        }
    }

    private final byte[] CRLF = "\r\n".getBytes(StandardCharsets.US_ASCII);
    private final byte[] OK_RESPONSE_BYTES = "HTTP/1.0 200 OK\r\n".getBytes(StandardCharsets.US_ASCII);
    private final byte[] CONTENT_TYPE_HEADER_BYTES = "Content-Type: ".getBytes(StandardCharsets.US_ASCII);
    private final byte[] CONTENT_LENGTH_HEADER_BYTES = "Content-Length: ".getBytes(StandardCharsets.US_ASCII);

    private void handleRequest(Socket socket)
        throws IOException
    {
        try(BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
                OutputStream out = socket.getOutputStream();
            ) {
            String requestLine = in.readLine();
            if (requestLine == null) {
                sendResponse(out, "400 Bad Request", null);

                publishRequest(400, null, -1);

                return;
            }

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                sendResponse(out, "400 Bad Request", null);

                publishRequest(400, null, -1);

                return;
            }

            if(!"GET".equals(parts[0])) {
                sendResponse(out, "405 Method Not Allowed", null);

                publishRequest(405, null, -1);

                return;
            }


            String rawPath = parts[1];
            if (rawPath.equals("/")) {
                rawPath = "/index.html";
            }

            // Resolve and normalize path
            Path requested = documentRoot
                    .resolve(rawPath.substring(1))
                    .normalize();

            // STRICT containment check (prevents ../ attacks and symlink escape)
            if (!requested.startsWith(documentRoot) || !Files.isRegularFile(requested)) {
                sendResponse(out, "404 Not Found", null);

                publishRequest(404, requested, -1);

                return;
            }

            File requestedFile = requested.toFile();
            long fileLength = requestedFile.length();

            String contentType = guessContentType(requested);

            out.write(OK_RESPONSE_BYTES); // HTTP/1.0 200 OK
            if(null != contentType) {
                // Content-Type: mime-type
                out.write(CONTENT_TYPE_HEADER_BYTES);
                out.write(String.valueOf(contentType).getBytes(StandardCharsets.US_ASCII));
                out.write(CRLF);
            }
            // Content-Length: [bytes]
            out.write(CONTENT_LENGTH_HEADER_BYTES);
            out.write(String.valueOf(fileLength).getBytes(StandardCharsets.US_ASCII));
            out.write(CRLF);
            out.write(CRLF);

            byte[] buffer = new byte[8192];
            int c;

            try(FileInputStream fin = new FileInputStream(requestedFile)) {
                while(-1 < (c = fin.read(buffer))) {
                    out.write(buffer, 0, c);
                }
            }
            out.flush();

            publishRequest(200, requested, fileLength);
        }
    }

    /**
     * Guess the MIME type of the file based upon the file extension.
     *
     * Only supports a few types that we care about.
     *
     * @param file The file we will be serving.
     *
     * @return The guessed MIME type of the file; returns null
     *         per RFC 9110 if the file type is not recognized.
     */
    private static String guessContentType(Path file) {
        String name = file.getFileName().toString().toLowerCase();

        if (name.endsWith(".html") || name.endsWith(".htm")) {
            return "text/html";
        }
        if (name.endsWith(".css")) {
            return "text/css";
        }
        if (name.endsWith(".js")) {
            return "application/javascript";
        }
        if (name.endsWith(".png")) {
            return "image/png";
        }

        return null;
    }

    private void sendResponse(OutputStream out, String status, String body)
        throws IOException
    {
        int contentLength;
        byte[] content;

        if(null != body) {
            content = body.getBytes(StandardCharsets.UTF_8);
            contentLength = content.length;
        } else {
            contentLength = 0;
            content = null;
        }

        OutputStreamWriter oor = new OutputStreamWriter(out);
        oor.write("HTTP/1.0 ");
        oor.write(status);
        oor.write("\r\nContent-Length: ");
        oor.write(String.valueOf(contentLength));
        oor.write("\r\n");

        if(contentLength > 0) {
            oor.write("Content-Type: text/plain\r\n");
        }

        oor.write("\r\n");
        oor.flush();

        if(null != content) {
            out.write(content);
        }
        out.flush();
    }
}