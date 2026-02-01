package com.koibots.scout.hub;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;

import javax.swing.*;

import com.koibots.scout.hub.GameConfig.Section;

public class SectionEditorDialog
    extends EditorDialog<Section>
{
    private static final long serialVersionUID = -7932672670312723882L;

    private JTextField nameField;

    public SectionEditorDialog(Window owner, Section section) {
        super(owner, "Edit Section", section);

        setLayout(new BorderLayout(10, 10));

        // Text field
        nameField = new JTextField(20);
        nameField.setText(getUserObject().getName());

        JPanel center = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        center.add(new JLabel("Name:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        center.add(nameField, gbc);

        add(center, BorderLayout.CENTER);
        add(createButtonPanel(new JButton("Save")), BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
    }

    @Override
    protected void applyChanges(Section section) {
        section.setName(nameField.getText());
    }
}
