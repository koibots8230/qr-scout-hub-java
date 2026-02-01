package com.koibots.scout.hub.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * A utility class to aid in building unified Window closing listeners.
 */
public abstract class WindowCloser
    extends WindowAdapter
    implements ActionListener
{
    public abstract void handleClose();

    /**
     * {@inheritDoc}
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        handleClose();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void windowClosing(WindowEvent e) {
        handleClose();
    }
}
