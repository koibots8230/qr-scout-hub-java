package com.koibots.scout.hub.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.geom.Rectangle2D;
import java.net.URL;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.html.HTMLDocument;

import com.koibots.scout.hub.Main;

public class FileViewer
    extends JDialog
    implements HyperlinkListener
{
    private static final long serialVersionUID = 1572925395890821052L;
    private JTextPane _html;
    private JScrollPane _scroller;

    public FileViewer(Window owner, String title, URL url, String contentType) {
        super(owner, title);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke("meta W"),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        _html = new JTextPane();
        _html.setEditable(false);
        _html.setCaretPosition(0);
        _html.setCaretColor(_html.getBackground());
        _html.setContentType(contentType);
        int marginSize = 10;
        _html.setBorder(new EmptyBorder(marginSize, marginSize, marginSize, marginSize));
        _scroller = new JScrollPane(_html);

        _html.addHyperlinkListener(this);

        if(null != url) {
            _html.setText(Main.getFileContents(url));
        } else {
            _html.setText("No file contents found.");
        }

        add(_scroller, BorderLayout.CENTER);

        setMinimumSize(new Dimension(300, 200));

        pack();

        setSize(800, 600);

        setVisible(true);
        requestFocus();
        _html.setCaretPosition(0);

    }

    @Override
    public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
            return;
        }

        String ref;
        URL url = e.getURL();
        if(null != url) {
            ref = url.getRef();
        } else if(null != e.getDescription() && e.getDescription().startsWith("#")) {
            ref = e.getDescription().substring(1);
        } else {
            ref = null;
        }

        if(null == ref) {
            return;
        }

        HTMLDocument document = (HTMLDocument)_html.getDocument();
        javax.swing.text.Element element = document.getElement(ref);

        if(null == element) {
            System.out.println("No target element found for anchor: " + ref);

            return;
        }

        // This is the target's offset in the text
        int startOffset = element.getStartOffset();

        try {
            // This is the on-screen location of the text offset
            Rectangle2D target = _html.modelToView2D(startOffset);

            // Move the enclosing viewport (scroll pane) so the target
            // is placed as close to the top of the view as possible.
            JViewport viewport = _scroller.getViewport();

            Rectangle r = target.getBounds();
            int maxY = _html.getHeight() - viewport.getHeight();
            // Ensure that the y value is actually within bounds
            int y = Math.max(0, Math.min(r.y, maxY));

            viewport.setViewPosition(new Point(0, y));
        } catch (BadLocationException ble) {
            ble.printStackTrace();
        }
    }
}
