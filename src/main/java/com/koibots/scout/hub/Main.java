package com.koibots.scout.hub;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Image;
import java.awt.Taskbar;
import java.awt.Toolkit;
import java.awt.desktop.AboutEvent;
import java.awt.desktop.AboutHandler;
import java.awt.desktop.OpenFilesEvent;
import java.awt.desktop.OpenFilesHandler;
import java.awt.desktop.QuitEvent;
import java.awt.desktop.QuitHandler;
import java.awt.desktop.QuitResponse;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarFile;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.swing.Action;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.filechooser.FileFilter;
import org.bytedeco.javacv.FrameGrabber;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.koibots.scout.hub.ui.AnalyticWindow;
import com.koibots.scout.hub.ui.AnalyticsWindow;
import com.koibots.scout.hub.ui.CodeScanner;
import com.koibots.scout.hub.ui.DatabaseEditor;
import com.koibots.scout.hub.ui.FileViewer;
import com.koibots.scout.hub.ui.GameConfigEditorDialog;
import com.koibots.scout.hub.ui.UIUtils;
import com.koibots.scout.hub.utils.AnalyticUpdater;
import com.koibots.scout.hub.utils.Queryable;

//
// Project directory structure:
//
// /config.json
// /db - Derby database

public class Main {
    private static final String PROGRAM_NAME = "KoiBots Scouting Hub";
    private static final String ABOUT_HTML_URL = "/help/about.html";

    private static final String PREFS_KEY_FILE_DIALOG_DIRECTORY = "file.dialog.directory";
    private static final String PREFS_KEY_CAMERA_DEVICE_ID = "camera.device.id";
    private static final String PREFS_KEY_LAST_OPEN_PROJECT = "last.project.directory";
    private static final String PREFS_KEY_INSERT_IMMEDIATELY = "insert.immediately";
    private static final String PREFS_KEY_RESCAN_IMMEDIATELY = "rescan.immediately";
    private static final String PREFS_KEY_USE_PLATFORM_FILE_DIALOGS = "file.use.platform.file.dialogs";

    private static final Collection<String> IMAGE_URLs = Arrays.asList(new String[] {
            "/icons/koibots-logo-16x16.png",
            "/icons/koibots-logo-20x20.png",
            "/icons/koibots-logo-24x24.png",
            "/icons/koibots-logo-32x32.png",
            "/icons/koibots-logo-40x40.png",
            "/icons/koibots-logo-48x48.png",
            "/icons/koibots-logo-64x64.png",
            "/icons/koibots-logo.png"
    });

    public static void main(String[] args) {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", PROGRAM_NAME);
        System.setProperty("com.apple.mrj.application.apple.menu.about.name", PROGRAM_NAME);

        SwingUtilities.invokeLater(() -> new Main().safeInit());
    }

    /**
     * The main window of our GUI.
     */
    private JFrame _main;

    /**
     * A panel of cards which show separate workflows depending on program state.
     */
    private JPanel _cardPanel;
    private CardLayout _cardLayout;

    /**
     * The scanned QR code data.
     */
    private JLabel _recordText;

    /**
     * The status line for the GUI.
     */
    private JLabel _statusLine;

    private AnalyticsWindow _analyticsWindow;

    /**
     * A list of Windows that are actually open.
     *
     * We use this to avoid opening the same window multiple times.
     */
    private ArrayList<AnalyticWindow> _analyticWindows = new ArrayList<AnalyticWindow>();

    /**
     * The currently open project.
     */
    private Project _project;

    // Actions which can be manifested as buttons or menu items
    private Action _newAction;
    private Action _openAction;
    private Action _closeAction;

    private Action _scanAction;
    private Action _importAction;
    private Action _exportAction;
    private Action _generateWebApplicationAction;
    private Action _analyticsAction;

    private Action _justScanNow;
    private Action _chooseCameraAction;
    private Action _launchWebappAction;
    private Action _editGameConfigAction;
    private Action _editDatabaseAction;
    private Action _helpAction;
    private Action _importGameConfigAction;

    private ApplicationQuitHandler _quitHandler;
    private JCheckBoxMenuItem _importImmediatelyOption;
    private Action _importImmediatelyAction;
    private JCheckBoxMenuItem _rescanImmediatelyOption;
    private Action _rescanImmediatelyAction;
    private JCheckBoxMenuItem _usePlatformFileDialogsOption;
    private Action _usePlatformFileDialogsAction;

    /**
     * The number of camera failures since process start.
     */
    private AtomicInteger cameraFailures = new AtomicInteger(0);

    /**
     * The directory where a file was last loaded or saved.
     */
    private File fileDialogDirectory;

    /**
     * An instance of the code scanner. Re-use the same one to retain
     * configuration such as FPS, camera device, etc.
     */
    private CodeScanner _scanner;

    /**
     * The last code that was scanned. Storing this allows us to avoid
     * scanning the same code multiple times.
     */
    private volatile String _lastScannedCode;

    private final AtomicInteger _lastScannedCodeRepeatCount = new AtomicInteger();

    /**
     * Whether or not to insert records immediately without asking.
     */
    private boolean _insertImmediately = true;

    /**
     * Whether or not to resume scanning immediately after importing.
     */
    private boolean _rescanImmediately = true;

    private boolean _usePlatformFileDialogs = false;

    public void setInsertImmediately(boolean insertImmediately) {
        _insertImmediately = insertImmediately;

        _importImmediatelyOption.setSelected(insertImmediately);
        _importImmediatelyAction.putValue(Action.SELECTED_KEY, insertImmediately);
    }

    public boolean getInsertImmediately() {
        return _insertImmediately;
    }

    public void setRescanImmediately(boolean rescanImmediately) {
        _rescanImmediately = rescanImmediately;

        _rescanImmediatelyOption.setSelected(rescanImmediately);
        _rescanImmediatelyAction.putValue(Action.SELECTED_KEY, rescanImmediately);
    }

    public boolean getRescanImmediately() {
        return _rescanImmediately;
    }

    public void setUsePlatformFileDialogs(boolean platformDialogs) {
        _usePlatformFileDialogs = platformDialogs;

        _usePlatformFileDialogsOption.setSelected(platformDialogs);
        _usePlatformFileDialogsAction.putValue(Action.SELECTED_KEY, platformDialogs);
    }

    public boolean getUsePlatformFileDialogs() {
        return _usePlatformFileDialogs;
    }

    /**
     * These menus and menu items are dynamic, and will need to be updated
     * at various times.
     */
//    private JMenu _windowMenu;
//    private JMenuItem _projectMenuItem;

    public void safeInit() {
        try {
            init();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private boolean isMacOS = System.getProperty("os.name") != null && System.getProperty("os.name").matches(".*Mac OS X.*");

    private ResourceBundle bundle;
    /**
     * Gets a localized string for the given key.
     *
     * @param key The resource bundle key of the desired string.
     *
     * @return The string from the resource bundle matching the requested
     *         key, or <code>null</code> if no value was found for the key.
     */
    private synchronized String getString(String key) {
        if(null == bundle) {
            try {
                bundle = ResourceBundle.getBundle("bundles.scoutinghub");
            } catch (MissingResourceException mre) {
                UIUtils.showError(mre, _main);

                // NOTE: Do not internationalize this string
                JOptionPane.showMessageDialog(_main, "Failed to load local strings. Exiting.", "Fatal Error", JOptionPane.ERROR_MESSAGE);

                System.exit(1);
            }
        }

        try {
            return bundle.getString(key);
        } catch (MissingResourceException mre) {
            return null;
        }
    }

    public void init() {
        long elapsed = System.currentTimeMillis();

        System.out.println(System.currentTimeMillis() + ", " + (System.currentTimeMillis() - elapsed) + " :  Initilizing UI...");

        _main = new JFrame(PROGRAM_NAME);

        System.out.println(System.currentTimeMillis() + ", " + (System.currentTimeMillis() - elapsed) + " :  Loading logo images...");

        ArrayList<Image> images = new ArrayList<Image>();
        for(String imageURL : IMAGE_URLs) {
            URL url = this.getClass().getResource(imageURL);

            if(null != url) {
                System.out.println("Loading image " + url);
                images.add(new ImageIcon(url).getImage());
            }
        }
        if(Taskbar.isTaskbarSupported()) {
            Taskbar taskbar = Taskbar.getTaskbar();
            System.out.println("Taskbar supported? " + Taskbar.isTaskbarSupported());
            for(Taskbar.Feature feature : Taskbar.Feature.values()) {
                System.out.println("Taskbar feature " + feature + ": " + (taskbar.isSupported(feature) ? "suppored" : "unsupported"));
            }
            if(taskbar.isSupported(Taskbar.Feature.MENU)) {
                System.out.println("Taskbar default menu: " + taskbar.getMenu());
            }
                /**
            PopupMenu taskBarMenu = new PopupMenu();
            MenuItem foo = new MenuItem("Foo");
            foo.addActionListener(null);
            taskBarMenu.add(foo);
            taskbar.setMenu(taskBarMenu);
                 */

            if(taskbar.isSupported(Taskbar.Feature.ICON_IMAGE) && !images.isEmpty()) {
                taskbar.setIconImage(images.get(0));
            }
        }

        if(!images.isEmpty()) {
            _main.setIconImages(images);
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException e1) {
            e1.printStackTrace();
        } catch (InstantiationException e1) {
            e1.printStackTrace();
        } catch (IllegalAccessException e1) {
            e1.printStackTrace();
        } catch (UnsupportedLookAndFeelException e1) {
            e1.printStackTrace();
        }

        System.out.println(System.currentTimeMillis() + ", " + (System.currentTimeMillis() - elapsed) + " :  Building menus...");

        _newAction = new ActionBase("action.new") {
            @Override
            public void actionPerformed(ActionEvent e) {
                File selectedDirectory = showSaveFileDialog(_main, "New Project", null, JFileChooser.DIRECTORIES_ONLY);

                if(null != selectedDirectory) {
                    if (selectedDirectory.exists()) {
                        JOptionPane.showMessageDialog(_main,
                                "File already exists. Please choose a different name.",
                                "File Exists",
                                JOptionPane.WARNING_MESSAGE);
                    } else {
                        try {
                            System.out.println("Creating project in " + selectedDirectory.getAbsolutePath());

                            GameConfig emptyConfig = new GameConfig();
                            emptyConfig.setPageTitle(getString("title.new-project"));
                            loadProject(Project.createProject(selectedDirectory, emptyConfig));
                        } catch (Throwable t) {
                            showError(t);
                        }
                    }
                }
            }
        };

        _openAction = new ActionBase("action.open") {
            @Override
            public void actionPerformed(ActionEvent e) {
                File selectedFile = showOpenFileDialog(_main,
                        "Open Project",
                        JFileChooser.DIRECTORIES_ONLY,
                        new FileFilter[] {
                                new FileFilter() {
                                    @Override
                                    public boolean accept(File f) {
                                        return f.isDirectory();
                                    }

                                    @Override
                                    public String getDescription() {
                                        return "Project Directory";
                                    }
                                }
                        });

                if(null != selectedFile) {
                    if(selectedFile.isDirectory()) {
                        try {
                            openProject(selectedFile);
                        } catch (Throwable t) {
                            showError(t);
                        }
                    } else {
                        JOptionPane.showMessageDialog(_main,
                                "You must select a project directory to open.",
                                "Invalid Project",
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        };

        _closeAction = new ActionBase("action.close") {
            @Override
            public void actionPerformed(ActionEvent e) {
                 closeProject();
            }
        };

        _scanAction = new ActionBase("action.scan") {
            @Override
            public void actionPerformed(ActionEvent e) {
                new Thread(() -> {
                    scan();
                }).start();
            }
        };

        _launchWebappAction = new ActionBase("action.launchWeb") {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    buildWebApplication();

                    startWebServer();

                    if(null == webServer) {
                        showError(new Throwable("Oh noes!"));
                    }

                    URI uri = new URI("http://localhost:" + webServer.getPort() + "/");

                    String urlString = uri.toString().replace("localhost", getLocalIPAddress());
                    JPanel panel = new JPanel();
                    ImageIcon qr = generateQRImageIcon(urlString, 200, 200);
                    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); // Stack components vertically

                 // Info text
                    JTextArea infoText = new JTextArea(
                            "The web server is running at " + urlString +
                            ". You may be able to scan this QR code on your phone to load it, if you are on the same network and there are no firewalls, etc. stopping you."
                    );
                    infoText.setLineWrap(true);
                    infoText.setWrapStyleWord(true);
                    infoText.setEditable(false);
                    infoText.setFocusable(false);
                    infoText.setOpaque(false);
                    infoText.setAlignmentX(Component.CENTER_ALIGNMENT);


                    int maxWidth = 400; // desired maximum width in pixels
                    infoText.setSize(maxWidth, Short.MAX_VALUE); // temporarily allow huge height
                    Dimension pref = infoText.getPreferredSize(); // now pref.height matches text
                    infoText.setMaximumSize(new Dimension(maxWidth, pref.height));
                    infoText.setPreferredSize(new Dimension(maxWidth, pref.height));

                    panel.add(infoText);

                    panel.add(Box.createVerticalStrut(10));

                    // QR code
                    JLabel qrLabel = new JLabel(qr);
                    qrLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                    qrLabel.setMaximumSize(qrLabel.getPreferredSize());  // prevent extra vertical space
                    panel.add(qrLabel);

                    panel.add(Box.createVerticalStrut(10));

                    JLabel questionLabel = new JLabel("Would you like to launch QR Scout in your web browser?");
                    questionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                    panel.add(questionLabel);

                    int result = JOptionPane.showConfirmDialog(_main,
                            panel,
                            "Web Server Started",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.INFORMATION_MESSAGE);

                    if(JOptionPane.YES_OPTION == result) {
                        Desktop.getDesktop().browse(uri);
                    }
                } catch (Throwable t) {
                    showError(t);
                }
            }
        };

        _importAction = new ActionBase("action.import") {
            @Override
            public void actionPerformed(ActionEvent e) {
                new Thread(() -> {
                    insertRecord(_recordText.getText());
                }).start();
            }
        };

        _exportAction = new ActionBase("action.export") {
            @Override
            public void actionPerformed(ActionEvent e) {
                File selectedFile = showSaveFileDialog(_main,
                        "Export CSV",
                        new File(getFileDialogDirectory(), _project.getGameConfig().getPageTitle() + ".csv"),
                        JFileChooser.FILES_ONLY);

                if (selectedFile != null) {
                    setFileDialogDirectory(selectedFile.getParentFile());

                    new Thread(() -> {
                        try {
                            exportDatabase(selectedFile);
                        } catch (Throwable t) {
                            showError(t);
                        }
                    }).start();
                }
            }
        };

        _generateWebApplicationAction = new ActionBase("action.makeWebApp") {
            @Override
            public void actionPerformed(ActionEvent e) {
                File selectedFile = showSaveFileDialog(_main,
                        "Save As",
                        new File(getFileDialogDirectory(), _project.getGameConfig().getPageTitle() + ".zip"),
                        JFileChooser.FILES_ONLY);

                if (null != selectedFile) {
                    new Thread(() -> {
                        try {
                            exportQRScout(selectedFile);
                        } catch (Throwable t) {
                            showError(t);
                        }
                    }).start();
                }
            }
        };

        _analyticsAction = new ActionBase("action.analytics") {
            @Override
            public void actionPerformed(ActionEvent e) {
                // If the window already exists, just show it.
                //
                // When the window closes, null-out the reference
                // so we can re-create it the next time it's requested.
                if(null == _analyticsWindow) {
                    _analyticsWindow = new AnalyticsWindow(_main,
                            _project.getAnalytics(),
                            _analyticWindows,
                            new Queryable() {
                                @Override
                                public List<Object[]> query(String query) throws IOException, SQLException
                                {
                                    return _project.queryDatabase(query);
                                }
                                @Override
                                public void validateQuery(String query) throws IOException, SQLException
                                {
                                    _project.validateQuery(query);
                                }

                                @Override
                                public Collection<String> getQueryableFieldNames() {
                                    ArrayList<String> allFields = new ArrayList<String>();
                                    allFields.add("ID");

                                    Collection<Field> fields = _project.getGameConfig().getFields();
                                    if(null != fields) {
                                        // Add all field names
                                        fields.stream().map(Field::getCode).collect(
                                                () -> allFields,
                                                ArrayList::add,
                                                ArrayList::addAll
                                                )
                                        ;
                                    }
                                    return allFields;
                                }
                            },
                            new AnalyticUpdater() {

                                @Override
                                public void updateAnalytic(Analytic oldAnalytic, Analytic newAnalytic)
                                        throws IOException {
                                    _project.updateAnalytic(oldAnalytic, newAnalytic);
                                }

                                @Override
                                public void deleteAnalytic(Analytic analytic) throws IOException {
                                    _project.deleteAnalytic(analytic);
                                }
                            });
/*
                    JMenuItem item = new JMenuItem("Analytics");
                    item.addActionListener((ev) -> {
                        _analyticsWindow.toFront();
                        _analyticsWindow.requestFocus();
                    });
                    _windowMenu.add(item);
*/
                    _analyticsWindow.addWindowListener(new WindowAdapter() {
                        @Override
                        public void windowClosing(WindowEvent e) {
//                            removeWindowMenuItem(_analyticsWindow.getTitle());
                            _analyticsWindow = null;
                        }
                    });

                    _analyticsWindow.setVisible(true);
                }
                _analyticsWindow.toFront();
                _analyticsWindow.requestFocus();
            }
        };

        _chooseCameraAction = new ActionBase("action.chooseCamera") {
            @Override
            public void actionPerformed(ActionEvent e) {
                new Thread(() -> {
                    int cameraDeviceID = _scanner.chooseCamera();

                    System.out.println("Got chosen camera device: " + cameraDeviceID);

                    // NOTE: chooseCamera already sets the device to be used
                    // with later CodeScanner-related operations (e.g. "scan").

                    if(-3 == cameraDeviceID) {
                        // This was an error
                        SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(_main,
                                "Failed to open the camera. If you just gave permission to use the camera, please try one more time, or quit the app and re-launch it.",
                                "Camera Failure",
                                JOptionPane.INFORMATION_MESSAGE)
                                );
                    } else if(-2 == cameraDeviceID) {
                        // No cameras detected
                        SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(_main,
                                "No cameras detected. If you just gave permission to use the camera, please try one more time, or quit the app and re-launch it.",
                                "No Cameras Found",
                                JOptionPane.INFORMATION_MESSAGE)
                                );
                    }
                }).start();
            }
        };

        _justScanNow = new ActionBase("action.scanNow") {
            @Override
            public void actionPerformed(ActionEvent e) {
                new Thread(() -> {
                    try {
                        String code = _scanner.scanCode();

                        if(null != code) {
                            SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(_main,
                                    "Code scanned; data:\n" + code.replaceAll("\t", "\u2409"),
                                    "Code Scanned",
                                    JOptionPane.INFORMATION_MESSAGE)
                                    );
                        }
                    } catch (FrameGrabber.Exception fge) {
                        if(cameraFailures.get() < 1) {
                            SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(_main,
                                    "Failed to open the camera. If you just gave permission to use the camera, please try one more time, or quit the app and re-launch it.",
                                    "Camera Failure",
                                    JOptionPane.INFORMATION_MESSAGE)
                            );
                        } else {
                            showError(fge);
                        }
                        cameraFailures.incrementAndGet();
                    } catch (Throwable t) {
                        showError(t);
                    }
                }).start();
            }
        };

        _editGameConfigAction = new ActionBase("action.editGame") {
            @Override
            public void actionPerformed(ActionEvent e) {
                final GameConfig config = _project.getGameConfig();
                GameConfigEditorDialog gced = new GameConfigEditorDialog(null, config);
                gced.setVisible(true);

                if(gced.isConfirmed()) {
                    try {
                        updateGameConfig(config);
                    } catch (Exception ex) {
                        UIUtils.showError(ex, _main);
                    }
                }
            }
        };

        _editDatabaseAction = new ActionBase("action.editDatabase") {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    DatabaseEditor de = new DatabaseEditor(_main);
                    de.setData(_project.getRecords());
                    de.addTableListener(new TableModelListener() {
                        @Override
                        public void tableChanged(TableModelEvent e) {
                            int row = e.getFirstRow();
                            try {
                                _project.updateRecord(de.getData(row));
                            } catch (RuntimeException rte) {
                                // Throw this back to the TableModel to deal with
                                throw rte;
                            } catch (Exception ex) {
                                // Throw this back to the TableModel to deal with
                                throw new IllegalStateException("Table change veto");
                            }
                        }
                    });

                    de.setVisible(true);
                } catch (Exception ex) {
                    showError(ex);
                }
            }
        };

        if(isMacOS) {
            // On MacOS, this will cause MacOS to add a "search" bar to the 'help' menu.
        }

        _importImmediatelyAction = new ActionBase("action.importImmediately") {
            @Override
            public void actionPerformed(ActionEvent e) {
                JCheckBoxMenuItem item = (JCheckBoxMenuItem) e.getSource();
                boolean selected = item.isSelected();

                setInsertImmediately(selected);
            }
        };

        _rescanImmediatelyAction = new ActionBase("action.rescanImmediately") {
            @Override
            public void actionPerformed(ActionEvent e) {
                JCheckBoxMenuItem item = (JCheckBoxMenuItem) e.getSource();
                boolean selected = item.isSelected();

                setRescanImmediately(selected);
            }
        };

        _usePlatformFileDialogsAction = new ActionBase("action.usePlatformFileDialogs") {
            @Override
            public void actionPerformed(ActionEvent e) {
                JCheckBoxMenuItem item = (JCheckBoxMenuItem) e.getSource();
                boolean selected = item.isSelected();

                setUsePlatformFileDialogs(selected);
            }
        };

        _helpAction = new ActionBase("action.help") {
            private FileViewer helpFrame;
            @Override
            public void actionPerformed(ActionEvent e) {
                if(null == helpFrame) {

                    URL url = getClass().getResource("/help/help.html");
                    helpFrame = new FileViewer(null, getString("window.help.title"), url, "text/html");

                    helpFrame.addWindowListener(new WindowAdapter() {
                        @Override
                        public void windowClosed(WindowEvent e) {
                            helpFrame = null;
                        }
                    });
                    helpFrame.setVisible(true);
                }

                helpFrame.requestFocus();
            }
        };

        _importGameConfigAction = new ActionBase("action.importGameConfig") {

            @Override
            public void actionPerformed(ActionEvent e) {
                Collection<Field> fields = _project.getGameConfig().getFields();
                if(null != fields && fields.size() > 1) {
                    JOptionPane.showMessageDialog(_main,
                            "You cannot import into a project with existing fields.",
                            "Cannot Import",
                            JOptionPane.ERROR_MESSAGE);

                    return;
                }

                File selectedFile = showOpenFileDialog(_main,
                        "Import Game Config",
                        JFileChooser.FILES_ONLY,
                        new FileFilter[] {
                                new FileFilter() {
                                    @Override
                                    public boolean accept(File f) {
                                        return f.isFile() && f.getName().toLowerCase().endsWith(".json");
                                    }

                                    @Override
                                    public String getDescription() {
                                        return "JSON Files";
                                    }

                                }
                });

                if (null != selectedFile) {
                    try {
                        updateGameConfig(GameConfig.readFile(selectedFile));
                    } catch (Exception ex) {
                        UIUtils.showError(ex, _main);
                    }
                }
            }
        };
        Desktop desktop = Desktop.getDesktop();
        _quitHandler = new ApplicationQuitHandler();

        if(desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
            desktop.setQuitHandler(_quitHandler);
        }

        if(desktop.isSupported(Desktop.Action.APP_ABOUT)) {
            desktop.setAboutHandler(new AboutHandler() {
                @Override
                public void handleAbout(AboutEvent e) {

                    JPanel panel = new JPanel();

                    JTextPane text = new JTextPane();
                    text.setContentType("text/html");

                    String derbyVersion;

                    try {
                        derbyVersion = org.apache.derby.tools.sysinfo.getVersionString();
                    } catch (Throwable t) {
                        derbyVersion = "(unknown)";
                    }

                    URL url = this.getClass().getResource(ABOUT_HTML_URL);

                    String html = null;
                    if(null != url) {
                        html = getFileContents(url);
                        if(null != html) {
                            html = html
                                    .replaceAll("\\$\\{java.version}", System.getProperty("java.version"))
                                    .replaceAll("\\$\\{derby.version}", derbyVersion)
                                    ;
                        }
                    }

                    if(null == html) {
                        System.out.println("No about file found at " + ABOUT_HTML_URL + "; falling back to build-in 'about' verbiage");

                        // Backup plan if no file found
                        StringBuilder sb = new StringBuilder("<html><style>body {font-family:sans; text-align:center;} p { margin-bottom:0;}</style>");

                        sb.append("<body><p>Copyright © 2025 - 2026 FRC Team 8230, KoiBots</p>");
                        sb.append("<p>All Rights Reserved.</p><br/>");
                        sb.append("<p>v1.0</p>");
                        sb.append("<p>Java ");
                        sb.append(System.getProperty("java.version"));
                        sb.append("</p>");
                        sb.append("<p>Apache Derby ").append(derbyVersion).append("</p>");
                        sb.append("</body></html>");

                        html = sb.toString();
                    }

                    text.setText(html);
                    text.setEditable(false);
                    text.setCaretColor(text.getBackground());
                    text.setBackground(panel.getBackground());
                    text.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));

                    panel.add(text, BorderLayout.CENTER);

                    JOptionPane op = new JOptionPane(panel, JOptionPane.INFORMATION_MESSAGE, JOptionPane.DEFAULT_OPTION);

                    op.createDialog(_main, PROGRAM_NAME).setVisible(true);
                }
            });
        }

        // NOTE: If you need to open an external URL, you can:
        // Desktop.getDesktop().browse(e.getURL().toURI());

        if(desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) {
            desktop.setOpenFileHandler(new OpenFilesHandler() {
                @Override
                public void openFiles(OpenFilesEvent e) {
                    System.out.println("NOTE: Received event to open files: " + e);
                    System.out.println("List of files to open: " + e.getFiles());
                }
            });
        }

        _main.setJMenuBar(createMenuBar());
        _main.setLocationByPlatform(true);
        _main.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent we) {
                _main.dispose(); // Close the window

                try {
                    Project.dispose();
                } catch (Throwable t) {
                    showError(t);
                }

                System.exit(0);
            }
        });

        // Welcome panel
        JPanel welcomePanel = new JPanel(new FlowLayout());

        JButton button = new JButton(_newAction);
        adjust(button);
        welcomePanel.add(button);

        button = new JButton(_openAction);
        adjust(button);
        welcomePanel.add(button);

        _statusLine = new JLabel("Ok");
        _statusLine.setBorder(BorderFactory.createLoweredBevelBorder());

        _recordText = new JLabel(getString("recordText.noCurrentRecord"));
        _recordText.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        // Swing alignment is a Dark Art
        _recordText.setVerticalAlignment(SwingConstants.CENTER);
        _recordText.setHorizontalAlignment(SwingConstants.CENTER);
        _recordText.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel flowPanel = new JPanel(new FlowLayout());

        button = new JButton(_scanAction);
        adjust(button);
        flowPanel.add(button);

        button = new JButton(_importAction);
        adjust(button);
        flowPanel.add(button);

        JPanel workPanel = new JPanel();
        workPanel.setLayout(new BoxLayout(workPanel, BoxLayout.Y_AXIS));
        workPanel.add(flowPanel);
        workPanel.add(_recordText);

        JPanel mainPanel = new JPanel(new BorderLayout());

        _cardLayout = new CardLayout();
        _cardPanel = new JPanel(_cardLayout);

        _cardPanel.add(welcomePanel, "welcome");
        _cardPanel.add(workPanel, "work");

        mainPanel.add(_cardPanel, BorderLayout.CENTER);
        mainPanel.add(_statusLine, BorderLayout.SOUTH);

        _main.setContentPane(mainPanel);

        // Set up default action states
        _main.setSize(800, 600);
        _main.setMinimumSize(new Dimension(300, 200));

        _scanner = new CodeScanner();
        _scanner.setParent(_main);

        setProjectLoaded(false);

        loadPreferences();

        System.out.println(System.currentTimeMillis() + ", " + (System.currentTimeMillis() - elapsed) + " :  Showing main window...");
        _main.setVisible(true);
    }

    private File showSaveFileDialog(Frame owner,
                                    String title,
                                    File sampleFile,
                                    int fileSelectionMode)
    {
        File selectedFile = null;

        if(getUsePlatformFileDialogs()) {
            FileDialog dialog = new FileDialog(owner, title, FileDialog.SAVE);

            // Set default filename
            if(null != sampleFile) {
                dialog.setFile(sampleFile.getName());
            } else {
                dialog.setDirectory(getFileDialogDirectory().getAbsolutePath());
            }
            dialog.setVisible(true);

            String file = dialog.getFile();

            if(null != file) {
                selectedFile = new File(dialog.getDirectory(), file);
            }
        } else {
            JFileChooser chooser = new JFileChooser(getFileDialogDirectory());
            chooser.setDialogTitle(title);
            chooser.setFileSelectionMode(fileSelectionMode);
            if(null != sampleFile) {
                chooser.setSelectedFile(sampleFile);
            }

            int result = chooser.showSaveDialog(owner);
            if (result == JFileChooser.APPROVE_OPTION) {
                selectedFile = chooser.getSelectedFile();
            }
        }

        if(null != selectedFile) {
            setFileDialogDirectory(selectedFile.getParentFile());
        }

        return selectedFile;
    }

    private File showOpenFileDialog(Frame owner,
            String title,
            int fileSelectionMode,
            FileFilter[] fileFilters)
    {
        File selectedFile = null;

        if(getUsePlatformFileDialogs()) {
            if(0 == (fileSelectionMode & JFileChooser.DIRECTORIES_ONLY)) {
                // Don't enable directory-selection
                System.setProperty("apple.awt.fileDialogForDirectories", "false");
            } else {
                // Allow directories to be selected
                System.setProperty("apple.awt.fileDialogForDirectories", "true");
            }

            FileDialog dialog = new FileDialog(owner, title, FileDialog.LOAD);
            dialog.setDirectory(getFileDialogDirectory().getAbsolutePath());

            if(null != fileFilters) {
                dialog.setFilenameFilter(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        File file = new File(dir, name);

                        for(FileFilter ff : fileFilters) {
                            if(ff.accept(file)) {
                                return true;
                            }
                        }

                        return false;
                    }
                });
            }

            dialog.setVisible(true);

            String file = dialog.getFile();

            if(null != file) {
                selectedFile = new File(dialog.getDirectory(), file);
            }
        } else {
            JFileChooser chooser = new JFileChooser(getFileDialogDirectory());
            chooser.setDialogTitle(title);
            chooser.setFileSelectionMode(fileSelectionMode);

            if(null != fileFilters) {
                FileFilter firstFilter = null;

                for(FileFilter ff : fileFilters) {
                    if(null == firstFilter) {
                        firstFilter = ff;
                    } else {
                        chooser.addChoosableFileFilter(ff);
                    }
                }
                if(null != firstFilter) {
                    chooser.setFileFilter(firstFilter);
                }
            }

            int result = chooser.showOpenDialog(owner);

            if (result == JFileChooser.APPROVE_OPTION) {
                selectedFile = chooser.getSelectedFile();
            }
        }

        if(null != selectedFile) {
            setFileDialogDirectory(selectedFile.getParentFile());
        }

        return selectedFile;
    }
    /**
     * A base class for Actions.
     *
     * Mostly configures an action based upon resource bundle values for
     * localization.
     */
    private abstract class ActionBase extends AbstractAction {
        private static final long serialVersionUID = 5480341350963717542L;

        protected ActionBase(String bundleKeyPrefix) {
            putValue(Action.NAME, getString(bundleKeyPrefix + ".name"));
            putValue(Action.SHORT_DESCRIPTION, getString(bundleKeyPrefix + ".shortDescription"));
            putValue(Action.LONG_DESCRIPTION, getString(bundleKeyPrefix + ".longDescription"));
            String accelerator = getString(bundleKeyPrefix + ".accelerator");
            if(null != accelerator) {
                // Use "platform" to mean "the commonly used control key on this platform"
                if(accelerator.contains("platform")) {
                    String platform;
                    int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

                    if (mask == InputEvent.META_DOWN_MASK) {
                        platform = "meta";
                    } else {
                        platform = "control";
                    }

                    accelerator = accelerator.replace("platform", platform);
                }

                putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(accelerator));
            }
            putValue(Action.ACTION_COMMAND_KEY, getString(bundleKeyPrefix + ".actionCommand"));
            putValue(Action.SELECTED_KEY, Boolean.valueOf(getString(bundleKeyPrefix + ".selected")));

            String mnemonic = getString(bundleKeyPrefix + ".mnemonic");
            if(null != mnemonic && mnemonic.length() > 0) {
                putValue(Action.MNEMONIC_KEY, (int)mnemonic.charAt(0));
            }

            String mnemonicIndex = getString(bundleKeyPrefix + ".mnemonicIndex");
            if(null != mnemonicIndex) {
                try {
                    putValue(Action.DISPLAYED_MNEMONIC_INDEX_KEY, Integer.valueOf(mnemonicIndex));
                } catch (NumberFormatException nfe) {
                    // Log and continue
                    nfe.printStackTrace();
                }
            }

            String icon = getString(bundleKeyPrefix + ".largeIcon");
            if(null != icon) {
                putValue(Action.LARGE_ICON_KEY, getIcon(icon));
            }
            icon = getString(bundleKeyPrefix + ".smallIcon");
            if(null != icon) {
                putValue(Action.SMALL_ICON, getIcon(icon));
            }
        }
    }

    private JMenuBar createMenuBar() {
        JMenuBar menubar = new JMenuBar();

        JMenu menu = new JMenu(getString("menu.file.name"));
        JMenuItem item;

        menu.add(new JMenuItem(_newAction));
        menu.add(new JMenuItem(_openAction));
        menu.add(new JMenuItem(_closeAction));

        // MacOS has its own quit menu under the application menu
        if(!isMacOS) {
            Action quitAction = new ActionBase("action.quit") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    _quitHandler.actionPerformed(e);
                }
            };
            menu.add(new JMenuItem(quitAction));
        }

        menubar.add(menu);

        menu = new JMenu(getString("menu.project.name"));
        menu.add(new JMenuItem(_importGameConfigAction));
        menu.add(new JMenuItem(_editGameConfigAction));
        menu.add(new JMenuItem(_launchWebappAction));
        menu.add(new JMenuItem(_generateWebApplicationAction));
        menubar.add(menu);

        menu = new JMenu(getString("menu.database.name"));
        menu.add(new JMenuItem(_analyticsAction));
        menu.add(new JMenuItem(_editDatabaseAction));
        menu.add(new JMenuItem(_exportAction));
        menubar.add(menu);

        menu = new JMenu(getString("menu.tools.name"));
        menu.add(new JMenuItem(_chooseCameraAction));
        menu.add(new JMenuItem(_justScanNow));
        menubar.add(menu);

//        _projectMenuItem = new JMenuItem("Project");
//        _windowMenu = new JMenu("Window");
//        _projectMenuItem.addActionListener((e) -> {
//            _main.toFront();
//            _main.requestFocus();
//        });
//        _windowMenu.add(_projectMenuItem);
//        menubar.add(_windowMenu);

        menu = new JMenu(getString("menu.options.name"));
        menu.add(_importImmediatelyOption = new JCheckBoxMenuItem(_importImmediatelyAction));
        menu.add(_rescanImmediatelyOption = new JCheckBoxMenuItem(_rescanImmediatelyAction));
        menu.add(_usePlatformFileDialogsOption = new JCheckBoxMenuItem(_usePlatformFileDialogsAction));
        menubar.add(menu);

        menu = new JMenu(getString("menu.help.name"));

        item = new JMenuItem(getString("menu.help.thirdPartyLicenses.name"));
        item.addActionListener((e) -> {
            URL url = null;
            System.out.println("CWD is " + new File(".").getAbsolutePath());

            for(String path : new String[] {
                    "app/LICENSES-THIRD-PARTY.txt",
                    "target/generated-sources/license/LICENSES-THIRD-PARTY.txt"
            }) {
                File file = new File(path);

                if(file.exists()) {
                    try {
                        url = file.toURI().toURL();

                        System.out.println("Found third-party licenses file at " + file.getAbsolutePath());
                        break;
                    } catch (MalformedURLException mue) {
                        // Shouldn't happen
                        UIUtils.showError(mue, _main);
                    }
                }
            }

            if(null == url) {
                try {
                    // Try to load the file off the disk in the neighborhood of the app bundle
                    Path jarPath = Paths.get(
                            Main.class.getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
                            );

                    Path appDir = jarPath.getParent();  // Contents/app
                    Path textFile = appDir.resolve("LICENSES-THIRD-PARTY.txt");
                    if(Files.exists(textFile)) {
                        System.out.println("Found third-party licenses file at " + textFile.toAbsolutePath());
                        url = textFile.toUri().toURL();
                    }
                    /*} catch (Exception ex) {
                ex.printStackTrace();*/
                } catch (URISyntaxException | MalformedURLException urle) {
                    // Should never happen
                    UIUtils.showError(urle, _main);
                }
            }

            if(null != url) {
                FileViewer viewer = new FileViewer(_main, getString("window.thirdPartyLicenses.title"), url, "text/plain");
                viewer.setModal(true);
                viewer.setVisible(true);
            } else {
                JOptionPane.showMessageDialog(_main, "Not Found", "Could not file LICENSES-THIRD-PARTY.txt", JOptionPane.ERROR_MESSAGE);
            }
        });
        menu.add(item);

        menu.add(_helpAction);
        menubar.add(menu);

        return menubar;
    }

//    private void removeWindowMenuItem(String windowTitle) {
//        for(int i=0; i<_windowMenu.getItemCount(); ++i) {
//            JMenuItem item = _windowMenu.getItem(i);
//            if(windowTitle.equals(item.getName())) {
//                _windowMenu.remove(item);
//            }
//        }
//    }

    /**
     * Sets the directory where file dialogs should be focused when opened.
     *
     * This directory will be persisted between application launches.
     *
     * @param dir The directory where file dialogs should be focused when
     *        opened.
     */
    private void setFileDialogDirectory(File dir) {
        fileDialogDirectory = dir;
    }

    /**
     * Gets the directory where file dialogs should be focused when opened.
     *
     * This directory will be persisted between application launches.
     *
     * @return The directory where file dialogs should be focused when
     *         opened.
     */
    private File getFileDialogDirectory() {
        if(null == fileDialogDirectory) {
            // Not set: try to get from preferences
            Preferences prefs = Preferences.userNodeForPackage(Main.class);

            String fileDialogPath = prefs.get(PREFS_KEY_FILE_DIALOG_DIRECTORY, null);
            System.out.println("Loaded file dialog path from preferences: " + fileDialogPath);
            if(null != fileDialogPath) {
                File dir = new File(fileDialogPath);
                if(dir.isDirectory()) {
                    fileDialogDirectory = dir;
                } else {
                    fileDialogPath = null; // Run the fallback behavior
                }
            }

            if(null == fileDialogPath) {
                // No preference established, or preference is invalid

                // Either choose the current working directory or the user's
                // home directory, whichever is more specific. The CWD is
                // root if this is a MacOS application bundle, so we don't
                // want to choose that.
                File cwd = new File(".");
                File userHome = new File(System.getProperty("user.home"));

                fileDialogDirectory = cwd.getAbsolutePath().length() >= userHome.getAbsolutePath().length() ? cwd : userHome;
            }
        }

        return fileDialogDirectory;
    }

    private String toString(Preferences prefs) {
        StringBuilder sb = new StringBuilder("Preferences {");

        try {
            boolean first = true;
            for(String key : prefs.keys()) {
                if(first) { first = false; } else { sb.append(", "); }

                sb.append(key).append('=').append(prefs.get(key, ""));
            }
        } catch (BackingStoreException bse) {
            // Do nothing
        }

        sb.append(" }");

        return sb.toString();
    }
    private void loadPreferences() {
        Preferences prefs = Preferences.userNodeForPackage(Main.class);

System.out.println("Loaded preferences: " + toString(prefs));

        // File dialog path
        String fileDialogPath = prefs.get(PREFS_KEY_FILE_DIALOG_DIRECTORY, null);
        System.out.println("Loaded file dialog path from preferences: " + fileDialogPath);
        if(null != fileDialogPath) {
            File dir = new File(fileDialogPath);
            if(dir.isDirectory()) {
                setFileDialogDirectory(dir);
            } else {
                fileDialogPath = null; // Run the fallback behavior below
            }
        }
        if(null == fileDialogPath) {
            // No preference established, or preference is invalid

            // Either choose the current working directory or the user's
            // home directory, whichever is more specific. The CWD is
            // root if this is a MacOS application bundle, so we don't
            // want to choose that.
            File cwd = new File(".");
            File userHome = new File(System.getProperty("user.home"));

            setFileDialogDirectory(cwd.getAbsolutePath().length() >= userHome.getAbsolutePath().length() ? cwd : userHome);
        }

        // Camera device id
        String cameraDeviceId = prefs.get(PREFS_KEY_CAMERA_DEVICE_ID, null);
        if(null != cameraDeviceId) {
            try {
                _scanner.setCameraDeviceID(Integer.parseInt(cameraDeviceId));
            } catch (NumberFormatException nfe) {
                // Ignore
            }
        }

        String lastProjectDirectory = prefs.get(PREFS_KEY_LAST_OPEN_PROJECT, null);
        if(null != lastProjectDirectory) {
            System.out.println("Last open project: " + lastProjectDirectory);

            if(null != lastProjectDirectory) {
                File projectDir = new File(lastProjectDirectory);
                if(projectDir.isDirectory()) {
                    try {
                        loadProject(projectDir);
                    } catch (Exception e) {
                        showError(e);
                    }
                }
            }
        }

        setInsertImmediately(prefs.getBoolean(PREFS_KEY_INSERT_IMMEDIATELY, false));
        setRescanImmediately(prefs.getBoolean(PREFS_KEY_RESCAN_IMMEDIATELY, false));
        setUsePlatformFileDialogs(prefs.getBoolean(PREFS_KEY_USE_PLATFORM_FILE_DIALOGS, false));
    }

    private void scan() {
        String code = null;
        try {
            code = _scanner.scanCode();

            if(null != code) {
                System.out.println("Got code: " + code);

                _recordText.setText(code);

                if(getInsertImmediately()) {
                    insertRecord(code);
                } else {
                    SwingUtilities.invokeLater(() -> _importAction.setEnabled(true));
                }
            } else {
                System.out.println("User cancelled code capture");
            }
        } catch (FrameGrabber.Exception fge) {
            if(cameraFailures.get() < 1) {
                SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(_main,
                        getString("error.camera.failedToOpen.text"),
                        getString("error.camera.failedToOpen.title"),
                        JOptionPane.INFORMATION_MESSAGE)
                );
            } else {
                showError(fge);
            }
            cameraFailures.incrementAndGet();
        } catch (Throwable t) {
            showError(t);
        }
    }

    private void exportQRScout(File file)
        throws IOException
    {
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {

            //
            // First, copy all of the stock QR Scout files from the current JAR
            // file into the ZIP file.
            //

            AtomicInteger copiedFiles = new AtomicInteger(0);

            String sourceResourceName = "qrscout"; // Relative without leading /
            try {
                URL url = getClass().getClassLoader().getResource(sourceResourceName);
                if (url != null && url.getProtocol().equals("file")) {
                    // Exploded classpath
                    Path srcDir = Paths.get(url.toURI());
                    Files.walk(srcDir).forEach(p -> {
                        try {
                            ZipEntry zipEntry = new ZipEntry(srcDir.relativize(p).toString());
                            zip.putNextEntry(zipEntry);

                            Files.copy(p, zip);

                            zip.closeEntry();

                            copiedFiles.incrementAndGet();
                        } catch (IOException e) {
                            // Must wrap this in an unchecked exception because
                            // Consumer.accept (which is what this lambda
                            // actually is) doesn't allow any checked
                            // exceptions.
                            //
                            // We will unwrap it later if it gets thrown,
                            // and re-throw the original exception.

                            throw new UncheckedIOException(e);
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
                        .filter(e -> e.getName().startsWith(pathPrefix) && !e.isDirectory())
                        .forEach(entry -> {
                            try (InputStream in = jar.getInputStream(entry)) {
                                ZipEntry zipEntry = new ZipEntry(entry.getName().substring(pathPrefix.length()));
                                zip.putNextEntry(zipEntry);

                                in.transferTo(zip);

                                zip.closeEntry();
                                copiedFiles.incrementAndGet();
                            } catch (IOException ex) {
                                // Must wrap this in an unchecked exception because
                                // Consumer.accept (which is what this lambda
                                // actually is) doesn't allow any checked
                                // exceptions.
                                //
                                // We will unwrap it later if it gets thrown,
                                // and re-throw the original exception.
                                throw new UncheckedIOException(ex);
                            }
                        });
                    }
                }
            } catch (UncheckedIOException uioe) {
                // Unwrap this unchecked exception
                throw uioe.getCause();
            }

            if(0 == copiedFiles.get()) {
                throw new IOException("Could not find QR Scout web application source. Packaging error?");
            }

            ZipEntry zipEntry = new ZipEntry("config.json");
            zip.putNextEntry(zipEntry);

            // Finally, copy the config.json file from the project into the target
            Files.copy(new File(_project.getDirectory(), "config.json").toPath(), zip);

            zip.closeEntry();
        } catch (URISyntaxException use) {
            // Shouldn't happen, since these URIs are being generated by the JVM
            throw new IOException(use.getMessage(), use);
        }

        JOptionPane.showMessageDialog(_main,
                "QR Scout successfully saved to " + file.getAbsolutePath(),
                "QR Scout Exported",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void savePreferences() {
        Preferences prefs = Preferences.userNodeForPackage(Main.class);

        prefs.put(PREFS_KEY_FILE_DIALOG_DIRECTORY, getFileDialogDirectory().getAbsolutePath());
        prefs.put(PREFS_KEY_CAMERA_DEVICE_ID, String.valueOf(_scanner.getCameraDeviceID()));
        prefs.putBoolean(PREFS_KEY_INSERT_IMMEDIATELY, getInsertImmediately());
        if(null != _project) {
            prefs.put(PREFS_KEY_LAST_OPEN_PROJECT, _project.getDirectory().getAbsolutePath());
        }

        prefs.putBoolean(PREFS_KEY_INSERT_IMMEDIATELY, getInsertImmediately());
        prefs.putBoolean(PREFS_KEY_RESCAN_IMMEDIATELY, getRescanImmediately());
        prefs.putBoolean(PREFS_KEY_USE_PLATFORM_FILE_DIALOGS, getUsePlatformFileDialogs());

System.out.println("Saving preferences: " + toString(prefs));
        try {
            prefs.flush();
        } catch (BackingStoreException e) {
            // Not much we can do about this
            e.printStackTrace();
        }
    }

    private Icon getIcon(String iconPath) {
        URL iconURL = getClass().getResource(iconPath);

        if (iconURL == null) {
            return null;
        } else {
            ImageIcon icon = new ImageIcon(iconURL);

            Image img = icon.getImage().getScaledInstance(256, 256, Image.SCALE_SMOOTH);

            icon = new ImageIcon(img);

            return icon;
        }
    }

    private void adjust(JButton button) {
        if(null != button.getIcon()) {
            button.setHorizontalTextPosition(SwingConstants.CENTER);
            button.setVerticalTextPosition(SwingConstants.BOTTOM);
        }
    }

    private void openProject(File project) {
        if(project.isDirectory()) {
            File dbFile = new File(project, "db");
            File configFile = new File(project, "config.json");

            if(!configFile.exists() || !dbFile.isDirectory()) {
                throw new IllegalArgumentException("Directory " + project + " does not contain a valid scouting config.");
            } else if(!dbFile.isDirectory()) {
                throw new IllegalArgumentException("Directory " + project + " does not contain a valid database.");
            } else {
                new Thread(() -> {
                        try {
                            loadProject(project);
                        } catch (Throwable t) {
                            showError(t);
                        }
                    }
                ).start();
            }
        }
    }

    private void setProjectLoaded(boolean loaded) {
        _closeAction.setEnabled(loaded);
        _scanAction.setEnabled(loaded);
        _launchWebappAction.setEnabled(loaded);
        _exportAction.setEnabled(loaded);
        _generateWebApplicationAction.setEnabled(loaded);
        _analyticsAction.setEnabled(loaded);
        _editGameConfigAction.setEnabled(loaded);
        _editDatabaseAction.setEnabled(loaded);
        _importGameConfigAction.setEnabled(loaded);

        // Whether just closing OR loading a project, there is no data to import
        _importAction.setEnabled(false);

        if(loaded) {
            _cardLayout.show(_cardPanel, "work");
        } else {
            _cardLayout.show(_cardPanel, "welcome");
        }
    }

    private void loadProject(Project project) throws SQLException {
        String projectName = project.getGameConfig().getPageTitle();
        int recordCount = project.getRecordCount();

        _project = project;

        SwingUtilities.invokeLater(() -> {
            _main.setTitle(PROGRAM_NAME + ": " + projectName);

            setProjectLoaded(true);

            _statusLine.setText("Record count: " + recordCount);

            JOptionPane.showMessageDialog(_main, "Successfully loaded project \"" + projectName + "\"", "Project Loaded", JOptionPane.INFORMATION_MESSAGE);
        });
    }
    private void loadProject(File projectDir) throws IOException, SQLException {
        loadProject(Project.loadProject(projectDir));
    }

    private void closeProject() {
        setProjectLoaded(false);

        _main.setTitle(PROGRAM_NAME);
        _statusLine.setText("Project closed.");

        // Close any analytic windows
        for(Iterator<AnalyticWindow> i=_analyticWindows.iterator(); i.hasNext(); ) {
            i.next().dispose();
            i.remove();
        }

        if(null != _analyticsWindow) {
            // Close the analytics window which is only appropriate for a specific project
            _analyticsWindow.dispose();
        }

        _project = null;
    }

    private void insertRecord(String codeData) {
        if(null == codeData) {
            System.out.println("Ignoring empty code");
            return;
        }

        if(codeData.equals(_lastScannedCode)) {
            System.out.println("Ignoring duplicate code (" + _lastScannedCodeRepeatCount + ")");

            int count = _lastScannedCodeRepeatCount.incrementAndGet();
            if(count > 2) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(_main,
                            "You've already scanned this code " + _lastScannedCodeRepeatCount + " times. It's time to move on.",
                            "Duplicate Code Scanned",
                            JOptionPane.WARNING_MESSAGE);

                    if(getRescanImmediately()) {
                        // Run this separately in its own thread.
                        // This prevents infinite recursion which we might get
                        // if we call scan() directly, which calls us back
                        // and so on.
                        System.out.println("Immediately re-scanning after shaming the user.");
                        new Thread(this::scan).start();
                    }
                });
            } else {
                if(getRescanImmediately()) {
                    // Run this separately in its own thread.
                    // This prevents infinite recursion which we might get
                    // if we call scan() directly, which calls us back
                    // and so on.
                    System.out.println("Immediately re-scanning after a duplicate");
                    new Thread(this::scan).start();
                }
            }

            return;
        }

        System.out.println("Zeroing-out last scanned code repeat count");
        _lastScannedCodeRepeatCount.set(0);

        try {
            _project.insertRecord(codeData);
            _lastScannedCode = codeData;

            int recordCount = _project.getRecordCount();

            SwingUtilities.invokeLater(() -> {
                _recordText.setText("Import successful.");
                _statusLine.setText("Record count: " + recordCount);

                if(getRescanImmediately()) {
                    // Run this separately in its own thread.
                    // This prevents infinite recursion which we might get
                    // if we call scan() directly, which calls us back
                    // and so on.
                    System.out.println("Immeditely re-scanning after successful import");
                    new Thread(this::scan).start();
                } else {
                    _importAction.setEnabled(false);
                }
            });
        } catch (Throwable t) {
            showError(t);
        }
    }

    private void exportDatabase(File targetFile) throws IOException, SQLException {
        try (FileWriter out = new FileWriter(targetFile)) {
            _project.exportDatabase(out);

            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(_main,
                    "Exported database to " + targetFile,
                    "Data Exported",
                    JOptionPane.INFORMATION_MESSAGE));
        }
    }

    private void showError(Throwable t) {
        UIUtils.showError(t, _main);
    }

    private SimpleHttpServer webServer;

    private void buildWebApplication() throws IOException {
        // Create the target directory if necessary
        Path targetDir = new File(_project.getDirectory(), "web").toPath();
        Files.createDirectories(targetDir);

        //
        // First, copy all of the stock QR Scout files from the current JAR
        // file into the target directory.
        //

        AtomicInteger copiedFiles = new AtomicInteger(0);

        String sourceResourceName = "qrscout"; // Relative without leading /
        try {
            URL url = getClass().getClassLoader().getResource(sourceResourceName);
            if (url != null && url.getProtocol().equals("file")) {
                // Exploded classpath
                Path srcDir = Paths.get(url.toURI());
                Files.walk(srcDir).forEach(p -> {
                    try {
                        Path dest = targetDir.resolve(srcDir.relativize(p).toString());
                        if (Files.isDirectory(p)) {
                            Files.createDirectories(dest);
                        } else {
                            Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                            copiedFiles.incrementAndGet();
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
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
                    .filter(e -> e.getName().startsWith(pathPrefix) && !e.isDirectory())
                    .forEach(entry -> {
                        Path dest = targetDir.resolve(entry.getName().substring(pathPrefix.length()));
                        try {
                            Files.createDirectories(dest.getParent());
                            try (InputStream in = jar.getInputStream(entry)) {
                                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                                copiedFiles.incrementAndGet();
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
                        }
                    });
                }
            }
        } catch (UncheckedIOException uioe) {
            // Unwrap this unchecked exception
            throw uioe.getCause();
        } catch (URISyntaxException use) {
            // Shouldn't happen, since these URIs are being generated by the JVM
            throw new IOException(use.getMessage(), use);
        }

        if(0 == copiedFiles.get()) {
            throw new IOException("Could not find QR Scout web application source. Packaging error?");
        }

        // Finally, copy the config.json file from the project into the target
        Files.copy(new File(_project.getDirectory(), "config.json").toPath(),
                targetDir.resolve("config.json"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private void startWebServer() {
        if(null == webServer) {
            try {
                SimpleHttpServer server = new SimpleHttpServer(8080, new File(_project.getDirectory(), "web").toPath());
                server.start();

                server.addRequestListener(new SimpleHttpServer.RequestListener() {

                    @Override
                    public void requestErrored(Throwable t) {
                        System.err.println("HTTP server request error:");
                        t.printStackTrace();
                    }

                    @Override
                    public void requestProcessed(int responseCode, Path path, long length) {
                        System.out.println("HTTP Server: " + responseCode + " " + path + " len=" + length);
                    }
                });
                webServer = server;
            } catch (IOException e) {
                showError(e);
            }
        }
    }

    private void stopWebServer() {
        if(null != webServer) {
            System.out.println("Stopping web server...");

            webServer.shutdown();

            System.out.println("Stopped web server.");

            webServer = null;
        }
    }

    private void updateGameConfig(GameConfig config) throws SQLException, IOException {
        // Update the database structure
        processDatabaseAlterations(config);

        // Make a backup copy of the original config
        File configFile = new File(_project.getDirectory(), "config.json");
        configFile.renameTo(new File(_project.getDirectory(), "config.bak"));

        // Save the new config
        config.saveToFile(configFile, true);

        _project.setGameConfig(config);

        _main.setTitle(PROGRAM_NAME + ": " + config.getPageTitle());
    }

    private void processDatabaseAlterations(GameConfig config) throws SQLException {
        _project.applyChanges(config);
    }

    private ImageIcon generateQRImageIcon(String s, int width, int height)
        throws WriterException
    {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();

        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
        hints.put(EncodeHintType.MARGIN, 1);

        // Encode the text into a QR code BitMatrix
        BitMatrix bitMatrix = qrCodeWriter.encode(
                s,
                BarcodeFormat.QR_CODE,
                width,
                height,
                hints
        );

        // Convert BitMatrix to BufferedImage
        BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

        // Convert BufferedImage to ImageIcon (for Swing)
        return new ImageIcon(bufferedImage);
    }

    private static String getLocalIPAddress() throws IOException {
        return getPrimaryLocalAddress().getHostAddress();
    }

    private static InetAddress getPrimaryLocalAddress() throws IOException {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("192.168.0.1"), 10002);
            return socket.getLocalAddress();
        }
    }

    public static String getFileContents(URL url) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream(), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while(null != (line = in.readLine())) {
                sb.append(line).append("\n");
            }

            return sb.toString();
        } catch (IOException ioe) {
            System.err.println("Failed to get contents of file " + url);
            ioe.printStackTrace();

            return null;
        }
    }

    private class ApplicationQuitHandler
        implements ActionListener, QuitHandler
    {
        private void quit() {
            System.err.println("Shutting down normally (menu action handler) ...");

            stopWebServer();

            try {
                Project.dispose();
            } catch (Throwable t) {
                showError(t);
            }

            savePreferences();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            quit();

            System.exit(0);
        }

        @Override
        public void handleQuitRequestWith(QuitEvent e, QuitResponse response) {
            quit();

            response.performQuit();
        }
    }
}
