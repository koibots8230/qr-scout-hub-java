package com.koibots.scout.hub;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.bytedeco.javacv.*;
import org.bytedeco.javacv.Frame;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Displays a JDialog showing a video feed from the camera, scanning for QR
 * codes in the frames.
 *
 * @see {@link #scanCode()}
 */
public class CodeScanner {
    /**
     * The default sleep time, about 30 FPS.
     */
    private static final long DEFAULT_SLEEP_MS = 30;

    /**
     * The default mirror mode (mirror = yes).
     */
    private static final boolean DEFAULT_MIRROR = true;

    /**
     * Flag to cancel scanning.
     */
    private volatile boolean cancelled = false;

    /**
     * The time to sleep between frame captures.
     */
    private long sleepMillis = DEFAULT_SLEEP_MS;

    /**
     * The display FPS - distinct from the capture FPS.
     */
    private int displayFramesPerSecond = 33;

    /**
     * The modal state of any dialogs shown by this class.
     */
    private boolean modal = true;

    /**
     * An array of camera devices we have detected.
     */
    private String[] cameraDevices;

    /**
     * The parent of any dialogs created by this class.
     */
    private java.awt.Frame parent;

    /**
     * The camera device to be used for code scanning.
     */
    private int cameraDeviceID = 0;

    /**
     * Whether or not to mirror the camera's view on the screen.
     */
    private boolean mirror = DEFAULT_MIRROR;

    /**
     * Sets the time to sleep between frame captures.
     *
     * @param sleep The time to sleep, in milliseconds.
     */
    public void setCaptureSleepMillis(long sleep) {
        sleepMillis = sleep;
    }

    /**
     * Gets the time to sleep between frame captures.
     *
     * return The time to sleep, in milliseconds.
     */
    public long getCaptureSleepMillis() {
        return sleepMillis;
    }

    /**
     * Sets the FPS for the camera.
     *
     * This is only an estimate, and will simply compute and set the sleep
     * time. It is not actually a target FPS that will result in a dynamic
     * computation of sleep time.
     *
     * @param framesPerSecond The desired approximate number of frames per
     *        second for the camera.
     */
    public void setCaptureFramesPerSecond(int framesPerSecond) {
        // f / s  = 1000 * f / ms
        //
        // Sleep delay = 1000 * 1 / framesPerSecond

        setCaptureSleepMillis((long)1000 * 1 / framesPerSecond);
    }

    /**
     * Gets the FPS for the camera.
     *
     * This is only an estimate, and will simply compute the approximate
     * FPS of the camera given the current sleep time. It is not actually a
     * target FPS that will result in a dynamic computation of sleep time.
     *
     * @return The approximate number of frames per second for the camera.
     */
    public int getCaptureFramesPerSecond() {
        return (int)((double)1000 * 1 / getCaptureSleepMillis());
    }

    public void setDisplayFramesPerSecond(int framesPerSecond) {
        displayFramesPerSecond = framesPerSecond;
    }

    public int getDisplayFramesPerSecond() {
        return displayFramesPerSecond;
    }

    /**
     * Sets the "modal" state of the dialog to show.
     *
     * This class should typically be used in a "modal" state, which means
     * that the window shown by {@link #scanCode()} stays on top and prevents
     * interaction with any other windows in the application.
     *
     * The default value of this property is <code>true</code>.
     *
     * @param modal <code>true</code> if the dialog should be modal,
     *        <code>false</code> if the dialog should <i>not</i> be modal.
     */
    public void setModal(boolean modal) {
        this.modal = modal;
    }

    /**
     * Gets the "modal" state of the dialog to show.
     *
     * This class should typically be used in a "modal" state, which means
     * that the window shown by {@link #scanCode()} stays on top and prevents
     * interaction with any other windows in the application.
     *
     * The default value of this property is <code>true</code>.
     *
     * @return <code>true</code> if the dialog should be modal,
     *        <code>false</code> if the dialog should <i>not</i> be modal.
     */
    public boolean getModal() {
        return modal;
    }

    /**
     * Cancels the scanning, closing the window and cleaning everything up.
     */
    public void cancel() {
        cancelled = true;
    }

    /**
     * Forgets any previously-collected camera devices. New requests for camera
     * information will re-fetch camera information from the system.
     */
    public void clearCameraDevices() {
        cameraDevices = null;
    }

    /**
     * Sets the parent Frame for any dialogs shown by this class.
     *
     * @param parent The parent Frame for dialogs.
     */
    public void setParent(java.awt.Frame parent) {
        this.parent = parent;
    }

    /**
     * Gets the parent Frame for any dialogs shown by this class.
     *
     * @return The parent Frame for dialogs.
     */
    public java.awt.Frame getParent() {
        return parent;
    }

    /**
     * Gets the camera device to use for QR code scanning.
     *
     * @return The id of the camera device used for scanning.
     */
    public int getCameraDeviceID() {
        return cameraDeviceID;
    }

    /**
     * Sets the camera device to use for QR code scanning.
     *
     * @param cameraDeviceID The id of the camera device to use for scanning.
     */
    public void setCameraDeviceID(int cameraDeviceID) {
        this.cameraDeviceID = cameraDeviceID;
    }

    public void setMirror(boolean mirror) {
        this.mirror = mirror;
    }

    public boolean getMirror() {
        return mirror;
    }

    /**
     * Utility method to creates a standard dialog.
     */
    private JDialog createDialog(java.awt.Frame owner, String title) {
        // Frame to display content and cancel button
        JDialog dialog = new JDialog(owner, title);
        dialog.setModal(getModal());
        dialog.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        JLabel videoLabel = new JLabel("Starting camera...", SwingConstants.CENTER);
        videoLabel.setFont(videoLabel.getFont().deriveFont(Font.BOLD, 24f));
        JButton cancelButton = new JButton("Cancel");
        dialog.setLayout(new BorderLayout());
        dialog.add(videoLabel, BorderLayout.CENTER);
        dialog.add(cancelButton, BorderLayout.SOUTH);
        dialog.setSize(640, 480);
        dialog.setLocationRelativeTo(null);

        WindowCloser closer = new WindowCloser() {
            @Override
            public void handleClose() {
                cancelled = true;
            }
        };
        // Closing the window counts as cancel
        dialog.addWindowListener(closer);
        cancelButton.addActionListener(closer);
        InputMap inputMap = dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = dialog.getRootPane().getActionMap();

        inputMap.put(KeyStroke.getKeyStroke("ESCAPE"), "closeDialog");
        int metaKey = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, metaKey), "closeDialog");
        actionMap.put("closeDialog", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                closer.handleClose();
            }
        });

        return dialog;
    }

    /**
     * Opens a window showing the camera feed and scans for QR codes.
     * Blocks until a QR code is detected or the user cancels.
     *
     * @return The decoded QR code String, or <code>null</code> if cancelled.
     *
     * @throws FrameGrabber.Exception If the camera could not be opened.
     */
    public String scanCode() throws FrameGrabber.Exception {
        cancelled = false;

        JDialog dialog = createDialog(getParent(), "QR Code Scanner");

        // Show the frame on the EDT
        SwingUtilities.invokeLater(() -> dialog.setVisible(true));

        String qrResult = null;

        // Grab a reference to the CENTER component -- a JLabel -- so we can
        // update it
        JLabel videoLabel = (JLabel)((BorderLayout)dialog.getContentPane().getLayout()).getLayoutComponent(BorderLayout.CENTER);

        // Start video capture (this thread blocks)
        try (OpenCVFrameGrabber grabber = new OpenCVFrameGrabber(getCameraDeviceID());
             Java2DFrameConverter converter = new Java2DFrameConverter();) {
            grabber.start();

            MultiFormatReader qrReader = new MultiFormatReader();
            ImageIcon icon = new ImageIcon();
            SwingUtilities.invokeLater(() -> videoLabel.setIcon(icon));

            // Try to re-use the same BufferedImage
            BufferedImage targetImage = null;
            AffineTransform flipper = null;
            AffineTransformOp flipperOp = null;

            System.out.println("Camera started.");

            long displayUpdateInterval = (long)1000 * 1 / getDisplayFramesPerSecond();
            long lastDisplayUpdate = 0;

            // This method goes into a loop fetching and displaying frames,
            // decoding any QR codes it sees in the process.
            //
            // Note that the calling thread is completely blocked until
            // the operation is complete. This is intentional, so that the
            // caller's interface is a simple "String code = scanCode()" call.
            // There are other ways of doing this such as returning a
            // Future<String> from this method and having the caller
            // wait on the value.
            //
            // The decision about how to structure this depends heavily
            // upon the caller and the callee (this method) agreeing on
            // how threading will work. The current implementation allows
            // the caller to manage threading (other than UI updates)
            // which is advantageous from the caller's perspective: this
            // method isn't starting any new threads which may need to be
            // somehow managed by the caller, etc.

            while (!cancelled && qrResult == null) {

                Frame frameGrab = grabber.grab();
                if (frameGrab == null) continue;

                BufferedImage img = converter.getBufferedImage(frameGrab);
                if (img != null) {
                    // Final for use in the lambda
                    final BufferedImage displayImage;

                    if(getMirror()) {
                        if(null == targetImage
                           || targetImage.getWidth() != img.getWidth()
                           || targetImage.getHeight() != img.getHeight()
                           || targetImage.getType() != img.getType()) {
                            System.out.println("Creating target image with size=" + img.getWidth() + "x" + img.getHeight() + " and type=" + img.getType());
                            targetImage = new BufferedImage(img.getWidth(), img.getHeight(), img.getType());

                            flipper = AffineTransform.getScaleInstance(-1, 1); // flip horizontally
                            flipper.translate(-img.getWidth(), 0); // move back into view

                            flipperOp = new AffineTransformOp(flipper, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
                        }

                        displayImage = flipperOp.filter(img, targetImage);
                    } else {
                        displayImage = img;
                    }

                    long now = System.currentTimeMillis();
                    if(now - lastDisplayUpdate >= displayUpdateInterval) {
                        lastDisplayUpdate = now;
                        // Update video in Swing safely
                        SwingUtilities.invokeLater(() -> {
                            icon.setImage(displayImage);
                            videoLabel.repaint();
                        });
                    }


                    // Try decoding QR code
                    LuminanceSource source = new BufferedImageLuminanceSource(img); // Use original
                    BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
                    try {
                        Result result = qrReader.decode(bitmap);
                        qrResult = result.getText();
                        cancelled = true; // stop loop
                    } catch (NotFoundException ignored) {
                        // no QR code in this frame
                    }
                }

                // Small sleep to reduce CPU usage
                try {
                    Thread.sleep(getCaptureSleepMillis());
                } catch (InterruptedException e) {
                    cancelled = true;
                }
            }

            grabber.stop();
        } finally {
            // Close the frame safely
            SwingUtilities.invokeLater(dialog::dispose);
        }

        return qrResult; // either QR code string or null if cancelled
    }

    private static void listCameras() {
        try {
            String[] devices = getDevicesWith(OpenCVFrameGrabber.class, "OpenCVFrameGrabber");

            if (devices != null && devices.length > 0) {
                System.out.println("=== Devices from OpenCVFrameGrabber ===");
                for (int i = 0; i < devices.length; i++) {
                    System.out.println("[" + i + "] " + devices[i]);
                }
            }
        } catch (Throwable ignore) {
            ignore.printStackTrace();
        }

        try {
            String[] devices = getDevicesWith(VideoInputFrameGrabber.class, "VideoInputFrameGrabber");

            if (devices != null && devices.length > 0) {
                System.out.println("=== Devices from VideoInputFrameGrabber ===");
                for (int i = 0; i < devices.length; i++) {
                    System.out.println("[" + i + "] " + devices[i]);
                }
            }
        } catch (Throwable ignore) {
            ignore.printStackTrace();

        }

        try {
            String[] devices = getDevicesWith(FFmpegFrameGrabber.class, "FFmpegFrameGrabber");

            if (devices != null && devices.length > 0) {
                System.out.println("=== Devices from FFmpegFrameGrabber ===");
                for (int i = 0; i < devices.length; i++) {
                    System.out.println("[" + i + "] " + devices[i]);
                }
            }
        } catch (Throwable ignore) {
            ignore.printStackTrace();
        }
    }

    private static String[] getDevicesWith(
            Class<? extends FrameGrabber> grabberClass,
            String name) throws Exception {

        return (String[]) grabberClass
                .getMethod("getDeviceDescriptions")
                .invoke(null);
    }

    public static void probeCameras() {
        System.out.println("Enumerating cameras by probing...");

        for (int i = 0; i < 10; i++) {
            try (OpenCVFrameGrabber grabber = new OpenCVFrameGrabber(i)) {
                grabber.start();

                System.out.println("Camera " + i + " available "
                    + "(" + grabber.getImageWidth() + "x" + grabber.getImageHeight() + ")");

                System.out.println("Camera Metadata: " + grabber.getMetadata());

                grabber.stop();
            } catch (FrameGrabber.Exception fge) {
                // No more devices
                System.out.println("No device at index " + i + "; stopping");

                break;
            }
        }
    }

    public String[] probeDevices(JLabel status) {
        SwingUtilities.invokeLater(() -> status.setText("Enumerating cameras by probing..."));

        ArrayList<String> devices = new ArrayList<String>();
        for (int i = 0; i < 10; i++) {
            if(cancelled) {
                return null;
            }
            try (OpenCVFrameGrabber grabber = new OpenCVFrameGrabber(i)) {
                grabber.start();

                String deviceName = "Camera " + i + " (" + grabber.getImageWidth() + "x" + grabber.getImageHeight() + ")";
                SwingUtilities.invokeLater(() -> status.setText("Discovered " + deviceName));

                devices.add(deviceName);

                grabber.stop();
            } catch (FrameGrabber.Exception fge) {
                // No more devices
                System.out.println("No device at index " + i + "; stopping");

                break;
            }
        }

        if(devices.isEmpty()) {
            return null;
        } else {
            return devices.toArray(new String[0]);
        }
    }

    private String[] getDeviceList(JLabel status) {
        // Don't re-enumerate if we've already looked
        if(null != cameraDevices) {
            return cameraDevices;
        }

        SwingUtilities.invokeLater(() -> status.setText("Enumerating devices...") );

        try {
            SwingUtilities.invokeLater(() -> status.setText("Enumerating devices with OpenCV...") );

            return cameraDevices = getDevicesWith(OpenCVFrameGrabber.class, "OpenCVFrameGrabber");
        } catch (Throwable ignore) {
            SwingUtilities.invokeLater(() -> status.setText("OpenCV failed") );
        }

        try {
            SwingUtilities.invokeLater(() -> status.setText("Enumerating devices with VideoInput...") );

            return cameraDevices = getDevicesWith(VideoInputFrameGrabber.class, "VideoInputFrameGrabber");
        } catch (Throwable ignore) {
            SwingUtilities.invokeLater(() -> status.setText("VideoInput failed") );
        }

        try {
            SwingUtilities.invokeLater(() -> status.setText("Enumerating devices with FFmpeg...") );

            return cameraDevices = getDevicesWith(FFmpegFrameGrabber.class, "FFmpegFrameGrabber");
        } catch (Throwable ignore) {
            SwingUtilities.invokeLater(() -> status.setText("FFmpeg failed") );
        }

        if(cancelled) {
            return null;
        }

        return cameraDevices = probeDevices(status);
    }

    /**
     * Opens a window showing the available cameras for selection.
     * Blocks until a camera is chosen or the user cancels.
     *
     * @return The chosen camera, <code>-1</code> if cancelled,
     *         <code>-2</code> if no cameras were detected,
     *         or <code>-3</code> if some other error occurred.
     *
     * @throws FrameGrabber.Exception If the camera could not be opened.
     */
    public int chooseCamera() {
        cancelled = false; // Clear flag

        JDialog dialog = createDialog(getParent(), "Choose Camera");

        // Grab a reference to the CENTER component -- a JLabel -- so we can
        // update it
        BorderLayout layout = (BorderLayout)dialog.getContentPane().getLayout();
        JLabel videoLabel = (JLabel)layout.getLayoutComponent(BorderLayout.CENTER);

        // Show the frame on the EDT
        SwingUtilities.invokeLater(() -> dialog.setVisible(true));

        // Build the camera list while the user can see the progress...
        String[] cameras = getDeviceList(videoLabel);

        CompletableFuture<Integer> chosenCamera = new CompletableFuture<Integer>();

        if(null != cameras) {
            // Build a drop-down of cameras
            JComboBox<String> dropdown = new JComboBox<String>(cameras);

            JPanel panel = new JPanel(new FlowLayout());

            JButton okButton = new JButton("Choose");
            okButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    int deviceId = dropdown.getSelectedIndex();
                    chosenCamera.complete(Integer.valueOf(deviceId));
                }
            });
            panel.add(new JLabel("Camera:"));
            panel.add(dropdown);
            panel.add(okButton);

            SwingUtilities.invokeLater(() -> {
                // Replace the text with the new panel
                Container content = dialog.getContentPane();
                content.remove(videoLabel);
                content.add(panel, BorderLayout.CENTER);
                content.revalidate();
                content.repaint();
            });

            // Wait for the user to choose a camera.
            //
            // This loop differs from the one in scanCode in that we aren't
            // doing any useful work during the loop itself. In scanCode, we
            // are grabbing camera frames and displaying them on the screen,
            // but here we are just waiting for the user to make a choice.
            //
            // It's honestly pretty ugly, but it allows a software interface
            // where the caller just calls chooseCamera and synchronously
            // gets an answer. There are other methods such as returning
            // the Future<Integer> from this method and having the caller
            // wait on the value.
            //
            // The decision about how to structure this depends heavily
            // upon the caller and the callee (this method) agreeing on
            // how threading will work. The current implementation allows
            // the caller to manage threading (other than UI updates)
            // which is advantageous from the caller's perspective: this
            // method isn't starting any new threads which may need to be
            // somehow managed by the caller, etc.
            try {
                Integer deviceId = null;
                while(!cancelled && null == deviceId) {
                    try {
                        deviceId = chosenCamera.get(100, TimeUnit.MILLISECONDS);
                    } catch (TimeoutException te) {
                        // Ignore and re-try
                    }
                }

                SwingUtilities.invokeLater(() -> dialog.dispose());

                if(null == deviceId) {
                    return -1;
                } else {
                    setCameraDeviceID(deviceId.intValue());

                    return deviceId.intValue();
                }
            } catch (ExecutionException | InterruptedException e) {
                SwingUtilities.invokeLater(() -> {
                    dialog.dispose();

                    String errorMessage = e.getClass().getName();

                    if(null != e.getMessage()) {
                        errorMessage += ": " + e.getMessage();
                    }

                    JOptionPane.showMessageDialog(
                            getParent(),
                            errorMessage,
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                            );

                });

                return -3;
            }
        } else {
            SwingUtilities.invokeLater(() -> {
                dialog.dispose();

                JOptionPane.showMessageDialog(
                        getParent(),
                        "No cameras found.",
                        "Camera Detection",
                        JOptionPane.INFORMATION_MESSAGE
                        );
            });

            return -2;
        }
    }

    private static void usage(PrintStream out) {
        out.println("Usage: " + CodeScanner.class.getName() + " [options]");
        out.println();
        out.println("Options:");
        out.println();
        out.println("   --list        List the available cameras.");
        out.println("   --probe       Probe for cameras. (Use if --list doesn't work.)");
        out.println("   --choose      Run the GUI camera-chooser.");
        out.println("   --test        Run the QR scanner.");
        out.println("   --device id   Specify the camera device to use for --test");
        out.println("   --fps FPS     Sets the camera frames per second. No more than 1000. (default 33)");
        out.println("   --dfps FPS    Sets the display bframes per second. No more than 1000. (default 33)");
        out.println("   --mirror      Enable mirroring. (default:" + DEFAULT_MIRROR + ")");
        out.println("   --no-mirror   Disable mirroring. (default:" + !DEFAULT_MIRROR + ")");
    }

    private enum Operation {
        list, probe, choose, test;
    }
    // Example usage
    public static void main(String[] args) throws Exception {
        int argindex = 0;
        int deviceId = 0;
        Operation operation = null;
        int fps = 33;
        int dfps = 33;
        boolean mirror = DEFAULT_MIRROR;

        while(argindex < args.length) {
            String arg = args[argindex++];

            if("--device".equals(arg)) {
                deviceId = Integer.parseInt(args[argindex++]);
            } else if("--list".equals(arg)) {
                operation = Operation.list;
            } else if("--probe".equals(arg)) {
                operation = Operation.probe;
            } else if("choose".equals(arg)) {
                operation = Operation.choose;
            } else if("--test".equals(arg)) {
                operation = Operation.test;
            } else if("--fps".equals(arg)) {
                fps = Integer.parseInt(args[argindex++]);
            } else if("--dfps".equals(arg)) {
                dfps = Integer.parseInt(args[argindex++]);
            } else if("--mirror".equals(arg)) {
                mirror = true;
            } else if("--no-mirror".equals(arg)) {
                mirror = false;
            } else if("--help".equals(arg) || "-h".equals(arg)) {
                usage(System.out);

                System.exit(0);
            } else {
                System.err.println("Unrecognized option: " + arg);

                usage(System.err);

                System.exit(1);
            }
        }

        if(null == operation) {
            usage(System.out);

            System.exit(0);
        }

        CodeScanner scanner;
        switch(operation) {
        case choose:
            scanner = new CodeScanner();

            int camera = scanner.chooseCamera();

            SwingUtilities.invokeLater(() -> {
                if(-1 != camera) {
                    JOptionPane.showMessageDialog(null, "Chose camera " + camera);
                } else {
                    JOptionPane.showMessageDialog(null, "Chose no camera");
                }
            });
            break;
        case list:
            listCameras();
            break;
        case probe:
            probeCameras();
            break;
        case test:
            scanner = new CodeScanner();
            scanner.setCameraDeviceID(deviceId);
            scanner.setCaptureFramesPerSecond(fps);
            scanner.setDisplayFramesPerSecond(dfps);
            scanner.setMirror(mirror);

            String qr = scanner.scanCode();

            System.out.println("QR code string data: " + qr);

            SwingUtilities.invokeLater(() -> {
                if (qr != null) {
                    JOptionPane.showMessageDialog(null, "QR Code: " + qr);
                } else {
                    JOptionPane.showMessageDialog(null, "Scan cancelled.");
                }
            });

            break;
        }
    }
}
