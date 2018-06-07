/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.wave;

import javax.swing.ImageIcon;
import javax.swing.JButton;

import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.util.WtoolBar;

public class WaveformToolBar extends WtoolBar {
    protected final JButton jButtondelete = new JButton();

    public WaveformToolBar(int index) {
        super("Main Bar", index); //$NON-NLS-1$

        final JButton printButton =
            new JButton(new ImageIcon(ImageViewerPlugin.class.getResource("/icon/32x32/printer.png"))); //$NON-NLS-1$
        printButton.setToolTipText(Messages.getString("SRContainer.print_layout")); //$NON-NLS-1$
        printButton.addActionListener(e -> {
            ImageViewerPlugin<?> container = WaveContainer.ECG_EVENT_MANAGER.getSelectedView2dContainer();
            if (container instanceof WaveContainer) {
                ((WaveContainer) container).printCurrentView();
            }
        });
        add(printButton);

        final JButton metaButton =
            new JButton(new ImageIcon(ImageViewerPlugin.class.getResource("/icon/32x32/text-x-generic.png"))); //$NON-NLS-1$
        metaButton.setToolTipText("Open DICOM Information"); //$NON-NLS-1$
        metaButton.addActionListener(e -> {
            ImageViewerPlugin<?> container = WaveContainer.ECG_EVENT_MANAGER.getSelectedView2dContainer();
            if (container instanceof WaveContainer) {
                ((WaveContainer) container).displayHeader();
            }
        });
        add(metaButton);

        jButtondelete.setToolTipText("Delete all the measurements");
        jButtondelete.setIcon(new ImageIcon(MouseActions.class.getResource("/icon/32x32/draw-delete.png"))); //$NON-NLS-1$
        jButtondelete.addActionListener(e -> {
            ImageViewerPlugin<?> container = WaveContainer.ECG_EVENT_MANAGER.getSelectedView2dContainer();
            if (container instanceof WaveContainer) {
                ((WaveContainer) container).clearMeasurements();
            }
        });
        add(jButtondelete);
    }
}
