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
package org.weasis.core.ui.editor;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.command.Option;
import org.weasis.core.api.command.Options;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.AbstractFileModel;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.util.FileUtil;

// TODO required to change the static ref
//@org.osgi.service.component.annotations.Component(immediate = false, property = {
//    CommandProcessor.COMMAND_SCOPE + "=image", CommandProcessor.COMMAND_FUNCTION + "=get",
//    CommandProcessor.COMMAND_FUNCTION + "=close" }, service = FileModel.class)
public class FileModel extends AbstractFileModel {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileModel.class);

    public static final File IMAGE_CACHE_DIR =
        AppProperties.buildAccessibleTempDirectory(AppProperties.FILE_CACHE_DIR.getName(), "image"); //$NON-NLS-1$

    private File getFile(String url) {
        File outFile = null;
        try {
            outFile = File.createTempFile("img_", FileUtil.getExtension(url), IMAGE_CACHE_DIR); // $NON-NLS-2$ //$NON-NLS-1$
            LOGGER.debug("Start to download image {} to {}.", url, outFile.getName()); //$NON-NLS-1$
            FileUtil.writeStreamWithIOException(new URL(url).openConnection(), outFile);
        } catch (IOException e) {
            LOGGER.error("Dowloading image", e); //$NON-NLS-1$
            return null;
        }
        return outFile;
    }

    @Override
    public void get(String[] argv) throws IOException {
        final String[] usage = { "Load images remotely or locally", "Usage: image:get ([-f file]... [-u url]...)", //$NON-NLS-1$ //$NON-NLS-2$
            "  -f --file=FILE     open an image from a file", // $NON-NLS-1$ //$NON-NLS-1$
            "  -u --url=URL       open an image from an URL", //$NON-NLS-1$
            "  -? --help          show help" }; // $NON-NLS-1$ //$NON-NLS-1$

        final Option opt = Options.compile(usage).parse(argv);
        final List<String> fargs = opt.getList("file"); //$NON-NLS-1$
        final List<String> uargs = opt.getList("url"); //$NON-NLS-1$

        if (opt.isSet("help") || (fargs.isEmpty() && uargs.isEmpty())) { //$NON-NLS-1$
            opt.usage();
            return;
        }
        GuiExecutor.instance().execute(() -> {
            AbstractFileModel dataModel = ViewerPluginBuilder.DefaultDataModel;
            dataModel.firePropertyChange(
                new ObservableEvent(ObservableEvent.BasicAction.SELECT, dataModel, null, dataModel));
            if (opt.isSet("file")) { //$NON-NLS-1$
                fargs.stream().map(File::new).filter(File::isFile)
                    .forEach(f -> ViewerPluginBuilder.openSequenceInDefaultPlugin(f, true, true));
            }
            if (opt.isSet("url")) { //$NON-NLS-1$
                uargs.stream().map(this::getFile)
                    .forEach(f -> ViewerPluginBuilder.openSequenceInDefaultPlugin(f, true, true));
            }
        });

    }

}
