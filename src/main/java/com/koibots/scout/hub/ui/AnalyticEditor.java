package com.koibots.scout.hub.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.util.Collection;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;

import org.apache.commons.text.StringEscapeUtils;

import com.koibots.scout.hub.Analytic;
import com.koibots.scout.hub.utils.Queryable;

/**
 * An editor dialog for an Analytic.
 */
public class AnalyticEditor
    extends EditorDialog<Analytic>
{
    private static final long serialVersionUID = -3120450351246782177L;

    private Queryable _queryable;

    public AnalyticEditor(Window owner, String title, Analytic analytic, Queryable queryable) {
        super(owner, title, analytic);

        _queryable = queryable;

        initUI();
        loadFromAnalytic(analytic);
        setMinimumSize(new Dimension(300, 200));
        pack();
        setLocationRelativeTo(owner);
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));

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

        JTextPane helpText = new JTextPane();
        helpText.setContentType("text/html");
        StringBuilder help = new StringBuilder(
        "<html><head><style>"
                + "body { font-family:sans-serif }"
                + "</style>"
                + "<h3>Available Tables</h3>"
                + "<ul><li><code>stand_scouting</code></li></ul>"
                + "<h3>Available Fields</h3>"
                + "<ul>");

        Collection<String> fieldNames = _queryable.getQueryableFieldNames();
        if(null != fieldNames) {
            for(String fieldName : fieldNames) {
                help.append("<li><code>").append(StringEscapeUtils.escapeHtml3(fieldName)).append("</code></li>");
            }
        }
        help.append("</ul>");
        helpText.setText(help.toString());
        JScrollPane helpScroll = new JScrollPane(helpText);
        helpScroll.setPreferredSize(new Dimension(200, 200));
        panel.add(helpScroll, BorderLayout.EAST);

        add(panel, BorderLayout.CENTER);
        add(createButtonPanel(new JButton("Save")), BorderLayout.SOUTH);

        SwingUtilities.invokeLater(() -> helpScroll.getVerticalScrollBar().setValue(0));
    }

    private void loadFromAnalytic(Analytic analytic) {
        if(null == analytic.getName()) {
            _name.setText("New Analytic");
        } else {
            _name.setText(analytic.getName().trim());
        }
        if(null == analytic.getQuery()) {
            _query.setText("SELECT 1 FROM stand_scouting");
        } else {
            _query.setText(analytic.getQuery().trim());
        }
    }
    private String _analyticName;
    private String _analyticQuery;

    private JTextField _name;
    private JTextArea _query;

    @Override
    protected boolean validateInput() {
        // Name
        if (_name.getText() == null || _name.getText().isBlank()) {
            showValidationError("Name is required.");
            _name.requestFocusInWindow();
            return false;
        }

        // Query
        if (_query.getText() == null || _query.getText().isBlank()) {
            showValidationError("Query is required.");
            _query.requestFocusInWindow();
            return false;
        }

        if(null != _queryable) {
            try {
                _queryable.validateQuery(_query.getText());
            } catch (Throwable t) {
                showValidationError(t.getMessage());

                return false;
            }
        }

        return true;
    }

    @Override
    protected void applyChanges(Analytic analytic) {
        analytic.setFilename(getUserObject().getFilename());
        analytic.setName(_name.getText().trim());
        analytic.setQuery(_query.getText().trim());
    }

    public static void main(String[] args) throws Exception {
        Analytic analytic = new Analytic();
        analytic.setName("New Analytic");
        AnalyticEditor editor = new AnalyticEditor(null, "New Analytic", analytic, null);

        editor.setVisible(true);
    }
}
