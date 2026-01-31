package com.koibots.scout.hub;

import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

/**
 * A Re-usable "editor dialog" that handles things like basic UI
 * and behavior. Subclasses must create their own UI and implement
 * the {@link #applyChanges(Object)} method.
 *
 * @param <T> The type of object being edited.
 */
public abstract class EditorDialog<T>
    extends JDialog
{
    private static final long serialVersionUID = 2367589910644440378L;

    protected final T userObject;
    private boolean confirmed = false;

    protected EditorDialog(Window owner, String title, T userObject) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        this.userObject = userObject;

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cancel();
            }
        });

        getRootPane().registerKeyboardAction(
                e -> cancel(),
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    protected JPanel createButtonPanel(JButton saveButton) {
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> cancel());

        saveButton.addActionListener(e -> save());

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.add(saveButton);
        panel.add(cancel);

        getRootPane().setDefaultButton(saveButton);
        return panel;
    }

    protected final void save() {
        applyChanges(userObject);
        confirmed = true;
        dispose();
    }

    protected final void cancel() {
        confirmed = false;
        dispose();
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    protected T getUserObject() {
        return userObject;
    }

    /** Subclass mutates model here */
    protected abstract void applyChanges(T userObject);
}
