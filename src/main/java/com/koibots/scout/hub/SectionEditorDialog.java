package com.koibots.scout.hub;

import java.awt.BorderLayout;
import java.awt.GridBagLayout;
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
        center.add(new JLabel("Name:"));
        center.add(nameField);

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
