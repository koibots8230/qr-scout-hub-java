package com.koibots.scout.hub.ui;

import java.awt.BorderLayout;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.WindowConstants;
import javax.swing.table.AbstractTableModel;

import com.koibots.scout.hub.Analytic;
import com.koibots.scout.hub.utils.Queryable;

/**
 * A window to show a single analytic and its results.
 */
public class AnalyticWindow
    extends JFrame
{
    private static final long serialVersionUID = 6695278361287847426L;

    private Analytic _analytic;
    private AnalyticTableModel _tableModel = new AnalyticTableModel();
    private Queryable _dataSource;

    public AnalyticWindow(Window owner, Analytic analytic, Queryable dataSource) {
        _analytic = analytic;
        _dataSource = dataSource;
        setLocationRelativeTo(owner);
    }

    public Analytic getAnalytic() {
        return _analytic;
    }

    public void init() {
        setTitle(_analytic.getName());

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                //                    _analyticWindows.remove(AnalyticWindow.this);
            }
        });

        UIUtils.setupCloseBehavior(getRootPane(), UIUtils.windowClosingAction);

        JPanel contents = new JPanel(new BorderLayout());

        runQuery();

        JTable table = new JTable(_tableModel);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        contents.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        JButton run = new JButton("Run");
        run.addActionListener((e) -> {
            runQuery();

        });
        buttons.add(run);
        contents.add(buttons, BorderLayout.SOUTH);

        setContentPane(contents);

        pack();
    }

    private void runQuery() {
        System.out.println("Running query: " + _analytic.getQuery());
        try {
            _tableModel.setData(_dataSource.query(_analytic.getQuery()));
        } catch (SQLException sqle) {
            UIUtils.showError(sqle, this);
            return;
        } catch (IOException ioe) {
            UIUtils.showError(ioe, this);
            return;
        }
    }

    private static class AnalyticTableModel
        extends AbstractTableModel
    {
        private static final long serialVersionUID = 3348828243019993524L;

        private List<Object[]> _data;
        public void setData(List<Object[]> data) {
            _data = data;

            // Let listeners like JTable know that the structure of the
            // table including headings, column and row count, and
            // cell data types have changed.
            fireTableStructureChanged();
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
        public String getColumnName(int columnIndex) {
            return String.valueOf(_data.get(0)[columnIndex]);
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            // Always use String for now
            return String.class;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            // Always use String for now
            return String.valueOf(_data.get(rowIndex + 1)[columnIndex]);
        }
    }
}