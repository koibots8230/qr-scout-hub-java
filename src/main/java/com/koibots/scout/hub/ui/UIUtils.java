package com.koibots.scout.hub.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Enumeration;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BoxLayout;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * Some handy UI utility functions.
 */
public class UIUtils
{
    private static ResourceBundle bundle = new EmptyResourceBundle();

    private static class EmptyResourceBundle
        extends ResourceBundle {

        @Override
        protected Object handleGetObject(String key) {
            System.err.println("Warning: attempt to fetch key " + key + " before resource bundle has been set.");
            return null;
        }

        @Override
        public Enumeration<String> getKeys() {
            return Collections.emptyEnumeration();
        }
    }

    public static void setResourceBundle(ResourceBundle bnd) {
        bundle = bnd;
    }

    /**
     * Gets a localized string for the given key.
     *
     * @param key The resource bundle key of the desired string.
     *
     * @return The string from the resource bundle matching the requested
     *         key, or <code>null</code> if no value was found for the key.
     */
    public static String getString(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException mre) {
            return null;
        }
    }

    public static void showError(Throwable t, Component parent) {
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

        JDialog dialog = op.createDialog(parent, "Error");

        dialog.setResizable(true);
        dialog.setLocationRelativeTo(parent);

        SwingUtilities.invokeLater(() -> dialog.setVisible(true));
    }

    static class StandardWindowClosingAction
        extends AbstractAction
    {
        private static final long serialVersionUID = -1629294416355233718L;

        @Override
        public void actionPerformed(ActionEvent e) {
            Window window = SwingUtilities.getWindowAncestor(
                    (Component) e.getSource()
                    );

            if (window != null) {
                window.dispose();
            }
        }
    }

    // Closes the window, including firing all appropriate event notifications
    public static Action windowClosingAction = new StandardWindowClosingAction();

    public static void setupCloseBehavior(JRootPane rootPane, Action action) {
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke("ESCAPE"), "closeDialog");
        int metaKey = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, metaKey), "closeDialog");
        if(null != action) {
            actionMap.put("closeDialog", action);
        }
    }
}
