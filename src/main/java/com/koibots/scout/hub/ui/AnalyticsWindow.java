package com.koibots.scout.hub.ui;

import java.awt.Component;
import java.awt.Window;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import com.koibots.scout.hub.Analytic;
import com.koibots.scout.hub.utils.AnalyticUpdater;
import com.koibots.scout.hub.utils.Queryable;

/**
 * A window to display the list of possible analytics.
 */
public class AnalyticsWindow
    extends JFrame
{
    private static final long serialVersionUID = 3889703546419862758L;

    private JPanel contents;
    private List<AnalyticWindow> _analyticWindows;
    private AnalyticUpdater _analyticUpdater;
    private Queryable _queryable;

    public AnalyticsWindow(Window owner,
            List<Analytic> analytics,
            List<AnalyticWindow> analyticWindows,
            Queryable queryable,
            AnalyticUpdater analyticUpdater) {
        _analyticWindows = analyticWindows;
        _analyticUpdater = analyticUpdater;
        _queryable = queryable;

        setTitle("Analytics");

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        UIUtils.setupCloseBehavior(getRootPane(), UIUtils.windowClosingAction);

        contents = new JPanel();
        contents.setLayout(new BoxLayout(contents, BoxLayout.Y_AXIS));

        if(null != analytics) {
            for(Analytic a : analytics) {
                JPanel analyticPanel = createAnalyticPanel(a, this);
                contents.add(analyticPanel);
            }
        }

        JPanel newPanel = new JPanel();
        JButton newButton = new JButton("New...");
        newButton.addActionListener((e) -> {
            AnalyticEditor editor = new AnalyticEditor(this);

            // This call blocks the UI and waits here
            editor.setVisible(true);

            if(editor.isConfirmed()) {
                Analytic newAnalytic = new Analytic();
                newAnalytic.setName(editor.getAnalyticName());
                newAnalytic.setQuery(editor.getAnalyticQuery());

                try {
                    _analyticUpdater.updateAnalytic(null, newAnalytic);

                    JPanel analyticPanel = createAnalyticPanel(newAnalytic, this);
                    contents.add(analyticPanel, contents.getComponentCount() - 1); // Insert before "New..."

                    pack(); // Re-lay-out the container
                } catch (Throwable t) {
                    UIUtils.showError(t, this);
                }
            }
        });
        newPanel.add(newButton);
        contents.add(newPanel);

        setContentPane(contents);

        pack();

        setLocationRelativeTo(owner);
    }

    private void removeAnalytic(Analytic analytic) {
        int count = contents.getComponentCount();
        for(int i=0; i<count; ++i) {
            Component c = contents.getComponent(i);
            if(c instanceof JComponent) {
                JComponent jc = (JComponent)c;

                if(analytic.equals(jc.getClientProperty(Analytic.class))) {
                    contents.remove(i);

                    pack();

                    break;
                }
            }
        }
    }

    private JPanel createAnalyticPanel(final Analytic analytic, final Window parentWindow) {
        JPanel analyticPanel = new JPanel();
        // Set a custom property so we can identify this panel later
        analyticPanel.putClientProperty(Analytic.class, analytic);
        JButton analyticButton = createAnalyticButton(analytic);
        JButton editButton = createAnalyticEditButton(analytic, analyticButton);
        editButton.addActionListener((ae) -> {
            // Perform a re-layout if the analytic name changes
            // and the button needs to change size, etc.
            parentWindow.pack();
        });
        JButton deleteButton = new JButton("Delete");
        deleteButton.addActionListener((e) -> {
            if(JOptionPane.OK_OPTION == JOptionPane.showConfirmDialog(parentWindow,
                    "Are you sure you want to delete this analytic?",
                    "Confirm Delete",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE)) {
                try {
                    _analyticUpdater.deleteAnalytic(analytic);

                    // Close any open analytic window
                    for(AnalyticWindow wnd : _analyticWindows) {
                        if(analytic.equals(wnd.getAnalytic())) {
                            wnd.dispose();
                        }
                    }
                    // Remove the panel from the analytics window
                    removeAnalytic(analytic);
                } catch (Throwable err) {
                    UIUtils.showError(err, this);
                }
            }
        });
        analyticPanel.add(deleteButton);
        analyticPanel.add(editButton);
        analyticPanel.add(analyticButton);

        return analyticPanel;
    }

    private JButton createAnalyticButton(final Analytic analytic) {
        JButton analyticButton = new JButton(analytic.getName());

        analyticButton.addActionListener((e) -> {
            // Check to see if a window for this Analytic is
            // already open. If it is already open, just bring it
            // to the foreground.
            for(AnalyticWindow wnd : _analyticWindows) {
                if(analytic.equals(wnd.getAnalytic())) {
                    wnd.toFront();
                    wnd.requestFocus();
                    return;
                }
            }

            // Nope? Okay, create a new window and register it.
            AnalyticWindow aw = new AnalyticWindow(this, analytic, _queryable);
            aw.init();

            // Remember that we opened this Window
            _analyticWindows.add(aw);

            aw.setVisible(true);
        });

        return analyticButton;
    }

    private JButton createAnalyticEditButton(Analytic analytic, JButton analyticButton) {
        JButton editButton = new JButton("Edit");

        editButton.addActionListener((e) -> {
            AnalyticEditor editor = new AnalyticEditor(this);
            editor.setAnalyticName(analytic.getName());
            editor.setAnalyticQuery(analytic.getQuery());

            // This call blocks the UI and waits here
            editor.setVisible(true);

            if(editor.isConfirmed()) {
                Analytic newAnalytic = new Analytic();
                newAnalytic.setName(editor.getAnalyticName());
                newAnalytic.setQuery(editor.getAnalyticQuery());

                try {
                    _analyticUpdater.updateAnalytic(analytic, newAnalytic);
                    analytic.setName(newAnalytic.getName());
                    analytic.setQuery(newAnalytic.getQuery());
                    analyticButton.setText(newAnalytic.getName());
                } catch (Throwable t) {
                    UIUtils.showError(t, this);
                }
            }
        });

        return editButton;
    }
}