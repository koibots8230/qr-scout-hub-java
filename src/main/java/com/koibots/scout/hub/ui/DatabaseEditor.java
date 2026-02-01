package com.koibots.scout.hub.ui;

import java.awt.BorderLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.WindowConstants;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;

/**
 * A dialog for editing a project database.
 */
public class DatabaseEditor
    extends JDialog
{
    private static final long serialVersionUID = -3761102509155330653L;

    private DatabaseEditorTableModel tableModel;

    public DatabaseEditor(Window owner) {
        super(owner);

        setTitle("Database Editor");

        setModal(true);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        UIUtils.setupCloseBehavior(getRootPane(), UIUtils.windowClosingAction);

        JPanel contents = new JPanel(new BorderLayout());

        JTable table = new JTable(tableModel = new DatabaseEditorTableModel());
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        contents.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel status = new JPanel();
        status.add(new JLabel("You are live-editing the database. All edits are final and not undoable. Press ESC to cancel an edit."));
        contents.add(status, BorderLayout.SOUTH);

        setContentPane(contents);

        pack();

        setLocationRelativeTo(owner);
    }

    public void setData(List<String[]> data) {
        tableModel.setData(data);
    }

    public String[] getData(int row) {
        return tableModel.getData(row);
    }

    public void addTableListener(TableModelListener listener) {
        tableModel.addTableModelListener(listener);
    }

    private class DatabaseEditorTableModel
        extends AbstractTableModel
    {
        private static final long serialVersionUID = 7459728010401667042L;
        private List<String[]> _data;

        public DatabaseEditorTableModel() {
            _data = new ArrayList<>();
            _data.add(new String[0]); // Empty data to start with empty header
        }

        public void setData(List<String[]> data) {
            _data = data;

            // Let listeners like JTable know that the structure of the
            // table including headings, column and row count, and
            // cell data types have changed.
            fireTableStructureChanged();
        }

        public String[] getData(int row) {
            // Row 0 is the header
            return _data.get(row + 1);
        }

        @Override
        public int getRowCount() {
            // _data[0] contains the headers, so the row count is one less
            return _data.size() - 1;
        }

        @Override
        public int getColumnCount() {
            return _data.get(0).length;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            // Disallow modification of the id field
            return column > 0;
        }

        @Override
        public String getColumnName(int column) {
            return _data.get(0)[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return String.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            // Row 0 is the header
            return _data.get(row + 1)[column];
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            // Row 0 is the header
            String[] data = _data.get(row + 1);

            // Save this in case of error
            String oldValue = data[column];

            if(Objects.equals(oldValue, value)) {
System.out.println("Not bothing to change old value " + oldValue + " to " + value);
                // No actual change; don't bother to do anything
                return;
            }

System.out.println("Updating row " + row + " column " + column + " with value " + value + " for ID=" + data[0]);
            data[column] = (String)value;

            try {
                fireTableRowsUpdated(row, row);
            } catch (Exception e) {
                UIUtils.showError(e, DatabaseEditor.this);

                data[column] = oldValue;
                fireTableRowsUpdated(row, row);
            }
        }
    }
}
