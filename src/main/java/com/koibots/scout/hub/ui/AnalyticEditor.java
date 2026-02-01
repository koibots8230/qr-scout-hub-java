package com.koibots.scout.hub.ui;

import java.awt.BorderLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.WindowConstants;

/**
 * An editor dialog for an Analytic.
 */
public class AnalyticEditor
    extends JDialog
{
    private static final long serialVersionUID = -3120450351246782177L;

    public AnalyticEditor(Window owner) {
        super(owner);

        setTitle("Editing Analytic");

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        UIUtils.setupCloseBehavior(getRootPane(), new UIUtils.StandardWindowClosingAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
               _confirmed = true;

               super.actionPerformed(e);
            }
        });

        setModal(true);
        _name = new JTextField(_analyticName);
        _query = new JTextArea(_analyticQuery);
        _query.setRows(10);
        _query.setColumns(50);
        _query.setLineWrap(true);
        _query.setWrapStyleWord(true);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        JPanel top = new JPanel(new BorderLayout(5, 0));
        top.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
        // Get as wide as the panel allows
        top.add(new JLabel("Name"), BorderLayout.NORTH);
        top.add(_name, BorderLayout.CENTER);

        JPanel center = new JPanel(new BorderLayout(5, 0));
        center.add(new JLabel("Query"), BorderLayout.NORTH);
        center.add(new JScrollPane(_query), BorderLayout.CENTER);
        center.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
        panel.add(top, BorderLayout.NORTH);
        panel.add(center, BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        JButton save = new JButton("Save");
        save.addActionListener((e) -> {
            closeEditor(true);
        });

        JButton cancel = new JButton("Cancel");
        cancel.addActionListener((e) -> {
            closeEditor(false);
        });

        buttons.add(save);
        buttons.add(cancel);

        panel.add(buttons, BorderLayout.SOUTH);

        add(panel, BorderLayout.CENTER);

        pack();

        setLocationRelativeTo(owner);
    }

    private String _analyticName;
    private String _analyticQuery;

    private boolean _confirmed = false;

    private JTextField _name;
    private JTextArea _query;

    public String getAnalyticName() {
        return _analyticName;
    }

    public void setAnalyticName(String name) {
        _analyticName = name;
    }

    public String getAnalyticQuery() {
        return _analyticQuery;
    }

    public void setAnalyticQuery(String query) {
        _analyticQuery = query;
    }

    private void closeEditor(boolean confirmed) {
        _confirmed = confirmed;

        if(confirmed) {
            _analyticName = _name.getText();
            _analyticQuery = _query.getText();
        }

        dispose();
    }

    public boolean isConfirmed() {
        return _confirmed;
    }

    public static void main(String[] args) throws Exception {
        AnalyticEditor editor = new AnalyticEditor(null);
        editor.setAnalyticName("Foo");

        editor.setVisible(true);
    }
}
