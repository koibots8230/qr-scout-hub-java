package com.koibots.scout.hub;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
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
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileFilter;

import org.bytedeco.javacv.FrameGrabber;

//
// Project directory structure:
//
// /config.json
// /db - Derby database

public class Main {
    private static final String PROGRAM_NAME = "KoiBots Scouting Hub";
    private static final String ABOUT_HTML_URL = "/resources/navigator/about.html";

    private static final String PREFS_KEY_FILE_DIALOG_DIRECTORY = "file.dialog.directory";
    private static final String PREFS_KEY_CAMERA_DEVICE_ID = "camera.device.id";
    private static final String PREFS_KEY_LAST_OPEN_PROJECT = "last.project.directory";

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

    /**
     * The currently open project.
     */
    private Project _project;

    // Actions which can be manifested as buttons or menu items
    private Action _closeAction;
    private Action _scanAction;
    private Action _importAction;
    private Action _exportAction;

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

    public void safeInit() {
        try {
            init();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public void init() {
        String osName = System.getProperty("os.name");
        boolean isMacOS = osName != null && osName.matches(".*Mac OS X.*");

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

        int metaKey = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        Action newAction = new AbstractAction() {
            {
                putValue(Action.NAME, "New...");
                putValue(Action.SHORT_DESCRIPTION, "Creates a new project.");
                putValue(Action.LARGE_ICON_KEY, getIcon("/images/new.png"));
                putValue(Action.MNEMONIC_KEY, (int)'n');
                putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_N, metaKey));
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                File projectDirectory = getNewFilename();

                if(null != projectDirectory) {
                    File configFile = getConfigFilename();

                    if(null != configFile) {
                        try {
                            createProject(projectDirectory, configFile);
                        } catch (IOException ioe) {
                            showError(ioe);
                        }
                    }
                }
            }
        };

        Action openAction = new AbstractAction() {
            {
                putValue(Action.NAME, "Open...");
                putValue(Action.SHORT_DESCRIPTION, "Opens a project.");
                putValue(Action.LARGE_ICON_KEY, getIcon("/images/open.png"));
                putValue(Action.MNEMONIC_KEY, (int)'o');
                putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_O, metaKey));
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                openProjectJFileChooser();
            }
        };

        _closeAction = new AbstractAction() {
            {
                putValue(Action.NAME, "Close Project");
                putValue(Action.SHORT_DESCRIPTION, "Closes the current project.");
                putValue(Action.LARGE_ICON_KEY, getIcon("/images/close.png"));
                putValue(Action.MNEMONIC_KEY, (int)'c');
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                _project = null;

                _cardLayout.first(_cardPanel);
                _closeAction.setEnabled(false);
                _exportAction.setEnabled(false);
                _main.setTitle(PROGRAM_NAME);
                _statusLine.setText("Project closed.");
            }
        };

        _scanAction = new AbstractAction() {
            {
                putValue(Action.NAME, "Scan");
                putValue(Action.SHORT_DESCRIPTION, "Opens the camera to scan a QR code.");
                putValue(Action.LARGE_ICON_KEY, getIcon("/images/koibots-qr.png"));
                putValue(Action.MNEMONIC_KEY, (int)'s');
                putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_S, metaKey));
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                new Thread(() -> {
                    try {
                        String code = _scanner.scanCode();

                        if(null != code) {
                            System.out.println("Got code: " + code);

                            _recordText.setText(code);

                            _importAction.setEnabled(true);
                        } else {
                            System.out.println("User cancelled code capture");
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

        _importAction = new AbstractAction() {
            {
                putValue(Action.NAME, "Import");
                putValue(Action.SHORT_DESCRIPTION, "Import the scanned data into the database.");
                putValue(Action.LARGE_ICON_KEY, getIcon("/images/insert.png"));
                putValue(Action.MNEMONIC_KEY, (int)'i');
                putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_I, metaKey));
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                new Thread(() -> {
                    try {
                        int recordCount = insertRecord(_recordText.getText());

                        SwingUtilities.invokeLater(() -> {
                            _recordText.setText("Import successful.");
                            _statusLine.setText("Record count: " + recordCount);
                            _importAction.setEnabled(false);
                        });
                    } catch (Throwable t) {
                        showError(t);
                    }
                }).start();
            }
        };

        _exportAction = new AbstractAction() {
            {
                putValue(Action.NAME, "Export");
                putValue(Action.SHORT_DESCRIPTION, "Exports the database to CSV.");
                putValue(Action.LARGE_ICON_KEY, getIcon("/images/export.png"));
                putValue(Action.MNEMONIC_KEY, (int)'x');

                setEnabled(false);
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                new Thread(() -> {
                    try {
                        exportDatabase();
                    } catch (Throwable t) {
                        showError(t);
                    }
                }).start();
            }
        };

        Action chooseCameraAction = new AbstractAction() {
            {
                putValue(Action.NAME, "Choose Camera");
                putValue(Action.SHORT_DESCRIPTION, "Allow you to choose the camera device to use.");
                putValue(Action.MNEMONIC_KEY, (int)'c');
            }

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

        Action justScanNow = new AbstractAction() {
            {
                putValue(Action.NAME, "Scan Code");
                putValue(Action.SHORT_DESCRIPTION, "Scan a QR code and show the data.");
                putValue(Action.MNEMONIC_KEY, (int)'s');
                putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_S, metaKey | KeyEvent.SHIFT_DOWN_MASK));
            }

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

        JMenu menu = new JMenu("File");
        JMenuItem item;

        menu.add(new JMenuItem(newAction));

        menu.add(new JMenuItem(openAction));

        menu.add(new JMenuItem(_closeAction));
        menu.add(new JMenuItem(_exportAction));

        // MacOS has its own quit menu under the application menu

        ApplicationQuitHandler quitHandler = new ApplicationQuitHandler();
        if(!isMacOS) {
            item = new JMenuItem("Quit");
            item.setMnemonic('q');
            item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, metaKey));
            item.addActionListener(quitHandler);
            menu.add(item);
        }

        JMenuBar menubar = new JMenuBar();

        menubar.add(menu);

        menu = new JMenu("Tools");
        menu.add(new JMenuItem(chooseCameraAction));
        menu.add(new JMenuItem(justScanNow));

        menubar.add(menu);

        menu = new JMenu("Help");
        item = new JMenuItem("Help");
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                SwingUtilities.invokeLater(() -> showHelp() );
            }
        });
        menu.add(item);
        menubar.add(menu);
        if(isMacOS) {
            // On MacOS, this will cause MacOS to add a "search" bar to the 'help' menu.
        }

        Desktop desktop = Desktop.getDesktop();

        if(desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
            desktop.setQuitHandler(quitHandler);
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
                        // Backup plan if no file found
                        StringBuilder sb = new StringBuilder("<html><style>body {font-family:sans; text-align:center;} p { margin-bottom:0;}</style>");

                        sb.append("<body><p>Copyright © 2025 KoiBots FRC 8230, Inc.</p>");
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

        _main.setJMenuBar(menubar);
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

        JButton button = new JButton(newAction);
        adjust(button);
        welcomePanel.add(button);

        button = new JButton(openAction);
        adjust(button);
        welcomePanel.add(button);

        _statusLine = new JLabel("Ok");
        _statusLine.setBorder(BorderFactory.createLoweredBevelBorder());

        _recordText = new JLabel("(No current record)");
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

        _cardPanel.add(welcomePanel);
        _cardPanel.add(workPanel);

        mainPanel.add(_cardPanel, BorderLayout.CENTER);
        mainPanel.add(_statusLine, BorderLayout.SOUTH);

        _main.setContentPane(mainPanel);

        // Set up default action states
        _closeAction.setEnabled(false);
        _scanAction.setEnabled(false);
        _importAction.setEnabled(false);

        _main.setSize(800, 600);

        _scanner = new CodeScanner();
        _scanner.setParent(_main);

        loadPreferences();

        System.out.println(System.currentTimeMillis() + ", " + (System.currentTimeMillis() - elapsed) + " :  Showing main window...");
        _main.setVisible(true);
    }

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

    private void loadPreferences() {
        Preferences prefs = Preferences.userNodeForPackage(Main.class);

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
        }
    }

    private void savePreferences() {
        Preferences prefs = Preferences.userNodeForPackage(Main.class);

        prefs.put(PREFS_KEY_FILE_DIALOG_DIRECTORY, getFileDialogDirectory().getAbsolutePath());
        prefs.put(PREFS_KEY_CAMERA_DEVICE_ID, String.valueOf(_scanner.getCameraDeviceID()));
        if(null != _project) {
            prefs.put(PREFS_KEY_LAST_OPEN_PROJECT, _project.getDirectory().getAbsolutePath());
        }

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

    private File getNewFilename() {
        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(getFileDialogDirectory());

        chooser.setDialogTitle("New Project");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setApproveButtonText("Create");
        chooser.setMultiSelectionEnabled(false);

        while (true) {
            int result = chooser.showSaveDialog(_main);
            if (result != JFileChooser.APPROVE_OPTION) {
                return null;
            }

            File selectedFile = chooser.getSelectedFile();
            if (selectedFile.exists()) {
                JOptionPane.showMessageDialog(_main,
                        "File already exists. Please choose a different name.",
                        "File Exists",
                        JOptionPane.WARNING_MESSAGE);
            } else {
                setFileDialogDirectory(chooser.getCurrentDirectory());

                return selectedFile;
            }
        }
    }

    private File getConfigFilename() {
        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(getFileDialogDirectory());
        chooser.setDialogTitle("Choose Scouting Config");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setMultiSelectionEnabled(false);

        chooser.setFileFilter(new FileFilter() {

            @Override
            public boolean accept(File f) {
                return !f.isDirectory() && f.getName().toLowerCase().endsWith(".json");
            }

            @Override
            public String getDescription() {
                return "JSON Files";
            }

        });
        int result = chooser.showOpenDialog(_main);
        if (result != JFileChooser.APPROVE_OPTION) {
            return null;
        } else {
            setFileDialogDirectory(chooser.getCurrentDirectory());

            return chooser.getSelectedFile();
        }
    }

    private void openProjectJFileChooser() {
        JFileChooser chooser = new JFileChooser();

        chooser.setCurrentDirectory(getFileDialogDirectory());
        chooser.setMultiSelectionEnabled(false);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        int status = chooser.showOpenDialog(_main);

        if(JFileChooser.APPROVE_OPTION == status) {
            setFileDialogDirectory(chooser.getCurrentDirectory());

            try {
                openProject(chooser.getSelectedFile());
            } catch (Throwable t) {
                showError(t);
            }
        }
    }

    private void createProject(File projectDirectory, File configFile) throws IOException {
        try {
            _project = Project.createProject(projectDirectory, configFile);
        } catch (Throwable t) {
            showError(t);
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

    private void loadProject(File projectDir) throws IOException, SQLException {
        _project = Project.loadProject(projectDir);

        String projectName = _project.getGameConfig().getPageTitle();
        int recordCount = _project.getRecordCount();

        SwingUtilities.invokeLater(() -> {
            _main.setTitle(PROGRAM_NAME + ": " + projectName);

            _cardLayout.next(_cardPanel);

            _scanAction.setEnabled(true);
            _exportAction.setEnabled(true);
            _closeAction.setEnabled(true);

            _statusLine.setText("Record count: " + recordCount);

            JOptionPane.showMessageDialog(_main, "Successfully loaded project \"" + projectName + "\"", "Project Loaded", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private int insertRecord(String codeData) throws SQLException {
        _project.insertRecord(codeData);

        return _project.getRecordCount();
    }

    private void exportDatabase() throws IOException, SQLException {
        String dataString = null;
        try (StringWriter out = new StringWriter()) {
            _project.exportDatabase(out);

            dataString = out.toString();
        }

        JTextArea text = new JTextArea(dataString);
        JDialog dialog = new JDialog(_main, "Export Scouting Data");
        dialog.setModal(true);
        JScrollPane scroller = new JScrollPane(text);
        scroller.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        dialog.getContentPane().add(scroller, BorderLayout.CENTER);
        JButton closeButton = new JButton("Close");
        WindowCloser closer = new WindowCloser() {
            @Override
            public void handleClose() {
                dialog.setVisible(false);
                dialog.dispose();
            }
        };
        closeButton.addActionListener(closer);
        dialog.getContentPane().add(closeButton, BorderLayout.SOUTH);

        // Configure the dialog to close on X button, CTRL-W, or ESC
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(closer);
        InputMap inputMap = dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = dialog.getRootPane().getActionMap();

        int metaKey = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        inputMap.put(KeyStroke.getKeyStroke("ESCAPE"), "closeDialog");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, metaKey), "closeDialog");
        actionMap.put("closeDialog", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                closer.handleClose();
            }
        });
        dialog.setSize(640, 480);

        SwingUtilities.invokeLater(() -> {
            dialog.setVisible(true);
        });
    }

    private void showHelp() {
        JOptionPane.showConfirmDialog(_main, "Not yet implemented", "Help", JOptionPane.OK_CANCEL_OPTION);
    }

    private void showError(Throwable t) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        panel.add(new JLabel(t.getMessage()));

        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));

        JTextArea message = new JTextArea(sw.toString());
        message.setEditable(false);

        JScrollPane js = new JScrollPane(message);
        js.setPreferredSize(new Dimension(300,100));

        panel.add(js);

        JOptionPane op = new JOptionPane(panel, JOptionPane.ERROR_MESSAGE, JOptionPane.DEFAULT_OPTION);

        JDialog dialog = op.createDialog(_main, "Error");

        dialog.setResizable(true);

        SwingUtilities.invokeLater(() -> dialog.setVisible(true));
    }

    private String getFileContents(URL url) {
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

            try {
                Project.dispose();
            } catch (Throwable t) {
                showError(t);
            }

            System.exit(0);
        }
        @Override
        public void actionPerformed(ActionEvent e) {
            quit();
        }

        @Override
        public void handleQuitRequestWith(QuitEvent e, QuitResponse response) {
            quit();

            response.performQuit();
        }
    }
}
