package com.koibots.scout.hub.ui;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;

import com.koibots.scout.hub.Field;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FieldEditorDialog
    extends EditorDialog<Field>
{
    private static final long serialVersionUID = -1010156114843330623L;

    // Core fields
    private JTextField titleField;
    private JTextArea descriptionArea;
    private JComboBox<String> typeCombo;
    private JCheckBox requiredCheck;
    private JTextField codeField;
    private JComboBox<String> formResetCombo;
    private JTextField defaultValueField;

    // Numeric
    private JTextField minField;
    private JTextField maxField;
    private JTextField stepField;

    // Timer
    private JComboBox<String> outputTypeCombo;

    // Select
    private JTable choicesTable;
    private ChoicesTableModel choicesModel;

    // Grouped panels so labels disable with fields
    private JPanel minPanel;
    private JPanel maxPanel;
    private JPanel stepPanel;
    private JPanel outputTypePanel;
    private JPanel choicesPanel;

    private static final String[] TYPES = {
            "text", "number", "counter", "range",
            "boolean", "timer", "select", "multi-select", "image"
    };

    public FieldEditorDialog(Window owner, Field field) {
        super(owner, "Edit Field", field);

        initUI();
        loadFromField(field);
        setMinimumSize(new Dimension(300, 200));
        pack();
        setLocationRelativeTo(owner);
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        int row = 0;

        titleField = new JTextField(25);
        addRow(form, gbc, row++, "Title:", titleField);

        descriptionArea = new JTextArea(4, 25);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        JScrollPane descScroll = new JScrollPane(descriptionArea);
        addRow(form, gbc, row++, "Description:", descScroll);

        typeCombo = new JComboBox<>(TYPES);
        addRow(form, gbc, row++, "Type:", typeCombo);

        requiredCheck = new JCheckBox("Required");
        addRow(form, gbc, row++, "", requiredCheck);

        codeField = new JTextField(20);
        addRow(form, gbc, row++, "Code:", codeField);

        formResetCombo = new JComboBox<>(new String[]{"reset", "preserve"});
        addRow(form, gbc, row++, "Form Reset:", formResetCombo);

        defaultValueField = new JTextField(20);
        addRow(form, gbc, row++, "Default Value:", defaultValueField);

        // ---- Numeric fields ----
        minField = new JTextField(5);
        maxField = new JTextField(5);
        stepField = new JTextField(5);

        minPanel  = createLabeledPanel("Min:", minField);
        maxPanel  = createLabeledPanel("Max:", maxField);
        stepPanel = createLabeledPanel("Step:", stepField);

        addPanelRow(form, gbc, row++, minPanel);
        addPanelRow(form, gbc, row++, maxPanel);
        addPanelRow(form, gbc, row++, stepPanel);

        // ---- Timer ----
        outputTypeCombo = new JComboBox<>(new String[]{"average", "list"});
        outputTypePanel = createLabeledPanel("Output Type:", outputTypeCombo);
        addPanelRow(form, gbc, row++, outputTypePanel);

        // ---- Choices ----
        choicesModel = new ChoicesTableModel();
        choicesTable = new JTable(choicesModel);
        JScrollPane choicesScroll = new JScrollPane(choicesTable);
        choicesScroll.setPreferredSize(new Dimension(300, 120));

        choicesPanel = createLabeledPanel("Choices:", choicesScroll);
        addPanelRow(form, gbc, row++, choicesPanel);

        typeCombo.addActionListener(e -> updateEnabledFields());

        add(form, BorderLayout.CENTER);
        add(createButtonPanel(new JButton("Save")), BorderLayout.SOUTH);
    }

    private JPanel createLabeledPanel(String labelText, Component component) {
        JPanel panel = new JPanel(new BorderLayout(5, 0));
        JLabel label = new JLabel(labelText);
        label.setVerticalAlignment(SwingConstants.TOP);
        panel.add(label, BorderLayout.WEST);
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    private void addPanelRow(JPanel form, GridBagConstraints gbc, int row, JPanel panel) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        form.add(panel, gbc);
        gbc.gridwidth = 1;
    }

    private void addRow(JPanel panel, GridBagConstraints gbc, int row,
                        String label, Component comp) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.weightx = 0;
        JLabel jLabel = new JLabel(label);
        jLabel.setVerticalAlignment(SwingConstants.TOP);
        panel.add(jLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.NORTHEAST;
        panel.add(comp, gbc);
    }

    private void loadFromField(Field field) {
        titleField.setText(field.getTitle());
        descriptionArea.setText(field.getDescription());
        codeField.setText(field.getCode());
        requiredCheck.setSelected(field.getRequired());
        defaultValueField.setText(
                field.getDefaultValue() != null ? field.getDefaultValue().toString() : ""
        );

        String type = Arrays.asList(TYPES).contains(field.getType())
                ? field.getType()
                : "text";
        typeCombo.setSelectedItem(type);

        formResetCombo.setSelectedItem(
                field.getFormResetBehavior() != null ? field.getFormResetBehavior() : "reset"
        );

        outputTypeCombo.setSelectedItem(
                field.getOutputType() != null ? field.getOutputType() : "average"
        );

        minField.setText(toString(field.getMin()));
        maxField.setText(toString(field.getMax()));
        stepField.setText(toString(field.getStep()));

        if (field.getChoices() != null) {
            choicesModel.setChoices(field.getChoices());
        }

        updateEnabledFields();
    }

    private void updateEnabledFields() {
        String type = (String) typeCombo.getSelectedItem();

        boolean numeric = Set.of("number", "range", "counter").contains(type);
        setPanelEnabled(minPanel, numeric);
        setPanelEnabled(maxPanel, numeric);
        setPanelEnabled(stepPanel, numeric);

        boolean select = Set.of("select", "multi-select").contains(type);
        setPanelEnabled(choicesPanel, select);
        choicesTable.setEnabled(select);

        boolean timer = "timer".equals(type);
        setPanelEnabled(outputTypePanel, timer);
        outputTypeCombo.setEnabled(timer);
    }

    private void setPanelEnabled(JPanel panel, boolean enabled) {
        panel.setEnabled(enabled);
        for (Component c : panel.getComponents()) {
            c.setEnabled(enabled);
        }
    }

    @Override
    protected boolean validateInput() {
        // Title
        if (titleField.getText() == null || titleField.getText().isBlank()) {
            showValidationError("Title is required.");
            titleField.requestFocusInWindow();
            return false;
        }

        // Code
        if (codeField.getText() == null || codeField.getText().isBlank()) {
            showValidationError("Code is required.");
            codeField.requestFocusInWindow();
            return false;
        }

        // Choices for select / multi-select
        String type = (String) typeCombo.getSelectedItem();
        if ("select".equals(type) || "multi-select".equals(type)) {
            if (choicesModel.getChoices().isEmpty()) {
                showValidationError(
                        "At least one choice is required for type \"" + type + "\"."
                );
                choicesTable.requestFocusInWindow();
                return false;
            }
        }

        return true;
    }

    @Override
    protected void applyChanges(Field field) {
        field.setTitle(titleField.getText().trim());
        field.setDescription(descriptionArea.getText().trim());
        field.setType((String)typeCombo.getSelectedItem());
        field.setRequired(requiredCheck.isSelected());
        field.setCode(codeField.getText().trim());
        field.setFormResetBehavior((String) formResetCombo.getSelectedItem());
        field.setDefaultValue(defaultValueField.getText().trim());
        field.setMin(parseInt(minField.getText()));
        field.setMax(parseInt(maxField.getText()));
        field.setStep(parseInt(stepField.getText()));
        field.setOutputType((String) outputTypeCombo.getSelectedItem());
        field.setChoices(choicesModel.getChoices());
    }

    private Integer parseInt(String s) {
        try {
            return s == null || s.isBlank() ? null : Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String toString(Integer i) {
        return i == null ? "" : i.toString();
    }

    // ---------------- Choices table ----------------

    private static class ChoicesTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 3159468127017600841L;

        private final List<Map.Entry<String, String>> data = new ArrayList<>();

        void setChoices(Map<String, String> choices) {
            data.clear();
            choices.forEach((k, v) -> data.add(Map.entry(k, v)));
            fireTableDataChanged();
        }

        Map<String, String> getChoices() {
            Map<String, String> map = new LinkedHashMap<>();
            for (var e : data) {
                if (!e.getKey().isBlank()) {
                    map.put(e.getKey(), e.getValue());
                }
            }
            return map;
        }

        @Override public int getRowCount() { return data.size() + 1; }
        @Override public int getColumnCount() { return 2; }
        @Override public String getColumnName(int c) { return c == 0 ? "Name" : "Value"; }
        @Override public boolean isCellEditable(int r, int c) { return true; }

        @Override
        public Object getValueAt(int r, int c) {
            if (r >= data.size()) return "";
            return c == 0 ? data.get(r).getKey() : data.get(r).getValue();
        }

        @Override
        public void setValueAt(Object value, int r, int c) {
            while (r >= data.size()) {
                data.add(Map.entry("", ""));
            }
            Map.Entry<String, String> e = data.get(r);
            data.set(r, c == 0
                    ? Map.entry(value.toString(), e.getValue())
                    : Map.entry(e.getKey(), value.toString()));
            fireTableRowsUpdated(r, r);
        }
    }
}
