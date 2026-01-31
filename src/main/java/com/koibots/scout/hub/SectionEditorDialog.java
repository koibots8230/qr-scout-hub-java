package com.koibots.scout.hub;

import javax.swing.*;

import com.koibots.scout.hub.GameConfig.Section;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class SectionEditorDialog extends JDialog {
    private static final long serialVersionUID = -7932672670312723882L;
    private final Section section;
    private boolean confirmed = false;

    private JTextField nameField;

    public SectionEditorDialog(Window owner, Section section) {
        super(owner, "Edit Section", ModalityType.APPLICATION_MODAL);
        this.section = section;

        initUI();
        pack();
        setLocationRelativeTo(owner);
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));

        // ---- Form panel ----
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Label
        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(new JLabel("Name:"), gbc);

        // Text field
        nameField = new JTextField(20);
        nameField.setText(section.getName());

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        formPanel.add(nameField, gbc);

        add(formPanel, BorderLayout.CENTER);

        // ---- Buttons ----
        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");

        saveButton.addActionListener(e -> onSave());
        cancelButton.addActionListener(e -> onCancel());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        add(buttonPanel, BorderLayout.SOUTH);

        // Default button
        getRootPane().setDefaultButton(saveButton);

        // ESC = cancel
        getRootPane().registerKeyboardAction(
                e -> onCancel(),
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        // Window close behaves like cancel
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });
    }

    private void onSave() {
        section.setName(nameField.getText());
        confirmed = true;
        dispose();
    }

    private void onCancel() {
        confirmed = false;
        dispose();
    }

    public boolean getConfirmed() {
        return confirmed;
    }
}
