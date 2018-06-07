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
package org.weasis.launcher;

import java.io.File;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.jar.Pack200.Unpacker;
import java.util.zip.GZIPInputStream;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.service.startlevel.StartLevel;

public class AutoProcessor {

    /**
     * The property name used for the bundle directory.
     **/
    public static final String AUTO_DEPLOY_DIR_PROPERTY = "felix.auto.deploy.dir"; //$NON-NLS-1$
    /**
     * The default name used for the bundle directory.
     **/
    public static final String AUTO_DEPLOY_DIR_VALUE = "bundle"; //$NON-NLS-1$
    /**
     * The property name used to specify auto-deploy actions.
     **/
    public static final String AUTO_DEPLOY_ACTION_PROPERTY = "felix.auto.deploy.action"; //$NON-NLS-1$
    /**
     * The property name used to specify auto-deploy start level.
     **/
    public static final String AUTO_DEPLOY_STARTLEVEL_PROPERTY = "felix.auto.deploy.startlevel"; //$NON-NLS-1$
    /**
     * The name used for the auto-deploy install action.
     **/
    public static final String AUTO_DEPLOY_INSTALL_VALUE = "install"; //$NON-NLS-1$
    /**
     * The name used for the auto-deploy start action.
     **/
    public static final String AUTO_DEPLOY_START_VALUE = "start"; //$NON-NLS-1$
    /**
     * The name used for the auto-deploy update action.
     **/
    public static final String AUTO_DEPLOY_UPDATE_VALUE = "update"; //$NON-NLS-1$
    /**
     * The name used for the auto-deploy uninstall action.
     **/
    public static final String AUTO_DEPLOY_UNINSTALL_VALUE = "uninstall"; //$NON-NLS-1$
    /**
     * The property name prefix for the launcher's auto-install property.
     **/
    public static final String AUTO_INSTALL_PROP = "felix.auto.install"; //$NON-NLS-1$
    /**
     * The property name prefix for the launcher's auto-start property.
     **/
    public static final String AUTO_START_PROP = "felix.auto.start"; //$NON-NLS-1$

    public static final String PACK200_COMPRESSION = ".pack.gz"; //$NON-NLS-1$

    /**
     * Used to instigate auto-deploy directory process and auto-install/auto-start configuration property processing
     * during.
     *
     * @param configMap
     *            Map of configuration properties.
     * @param context
     *            The system bundle context.
     * @param weasisLoader
     **/
    public static void process(Map<String, String> configMap, BundleContext context, WeasisLoader weasisLoader) {
        Map<String, String> map = (configMap == null) ? new HashMap<>() : configMap;
        processAutoDeploy(map, context, weasisLoader);
        processAutoProperties(map, context, weasisLoader);
    }

    /**
     * <p>
     * Processes bundles in the auto-deploy directory, performing the specified deploy actions.
     * </p>
     */
    private static void processAutoDeploy(Map<String, String> configMap, BundleContext context,
        WeasisLoader weasisLoader) {
        // Determine if auto deploy actions to perform.
        String action = configMap.get(AUTO_DEPLOY_ACTION_PROPERTY);
        action = (action == null) ? "" : action; //$NON-NLS-1$
        List<String> actionList = new ArrayList<>();
        StringTokenizer st = new StringTokenizer(action, ","); //$NON-NLS-1$
        while (st.hasMoreTokens()) {
            String s = st.nextToken().trim().toLowerCase();
            if (s.equals(AUTO_DEPLOY_INSTALL_VALUE) || s.equals(AUTO_DEPLOY_START_VALUE)
                || s.equals(AUTO_DEPLOY_UPDATE_VALUE) || s.equals(AUTO_DEPLOY_UNINSTALL_VALUE)) {
                actionList.add(s);
            }
        }

        // Perform auto-deploy actions.
        if (!actionList.isEmpty()) {
            // Retrieve the Start Level service, since it will be needed
            // to set the start level of the installed bundles.
            StartLevel sl = (StartLevel) context
                .getService(context.getServiceReference(org.osgi.service.startlevel.StartLevel.class.getName()));

            // Get start level for auto-deploy bundles.
            int startLevel = sl.getInitialBundleStartLevel();
            if (configMap.get(AUTO_DEPLOY_STARTLEVEL_PROPERTY) != null) {
                try {
                    startLevel = Integer.parseInt(configMap.get(AUTO_DEPLOY_STARTLEVEL_PROPERTY).toString());
                } catch (NumberFormatException ex) {
                    // Ignore and keep default level.
                }
            }

            // Get list of already installed bundles as a map.
            Map<String, Bundle> installedBundleMap = new HashMap<>();
            Bundle[] bundles = context.getBundles();
            for (int i = 0; i < bundles.length; i++) {
                installedBundleMap.put(bundles[i].getLocation(), bundles[i]);
            }

            // Get the auto deploy directory.
            String autoDir = configMap.get(AUTO_DEPLOY_DIR_PROPERTY);
            autoDir = (autoDir == null) ? AUTO_DEPLOY_DIR_VALUE : autoDir;
            // Look in the specified bundle directory to create a list
            // of all JAR files to install.
            File[] files = new File(autoDir).listFiles();
            List<File> jarList = new ArrayList<>();
            if (files != null) {
                Arrays.sort(files);
                for (int i = 0; i < files.length; i++) {
                    if (files[i].getName().endsWith(".jar")) { //$NON-NLS-1$
                        jarList.add(files[i]);
                    }
                }
            }
            weasisLoader.setMax(jarList.size());
            // Install bundle JAR files and remember the bundle objects.
            final List<Bundle> startBundleList = new ArrayList<>();
            for (int i = 0; i < jarList.size(); i++) {
                // Look up the bundle by location, removing it from
                // the map of installed bundles so the remaining bundles
                // indicate which bundles may need to be uninstalled.
                File jar = jarList.get(i);
                Bundle b = installedBundleMap.remove((jar).toURI().toString());
                try {
                    weasisLoader.writeLabel(WeasisLoader.LBL_DOWNLOADING + " " + jar.getName()); //$NON-NLS-1$

                    // If the bundle is not already installed, then install it
                    // if the 'install' action is present.
                    if ((b == null) && actionList.contains(AUTO_DEPLOY_INSTALL_VALUE)) {
                        b = installBundle(context, jarList.get(i).toURI().toString());
                    }
                    // If the bundle is already installed, then update it
                    // if the 'update' action is present.
                    else if (b != null && actionList.contains(AUTO_DEPLOY_UPDATE_VALUE)) {
                        b.update();
                    }

                    // If we have found and/or successfully installed a bundle,
                    // then add it to the list of bundles to potentially start
                    // and also set its start level accordingly.
                    if (b != null) {
                        weasisLoader.setValue(i + 1);
                        if (!isFragment(b)) {
                            startBundleList.add(b);
                            sl.setBundleStartLevel(b, startLevel);
                        }
                    }

                } catch (Exception ex) {
                    System.err.println("Auto-deploy install: " + ex //$NON-NLS-1$
                        + ((ex.getCause() != null) ? " - " + ex.getCause() : "")); //$NON-NLS-1$ //$NON-NLS-2$
                    if (!Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT
                        .equals(configMap.get(Constants.FRAMEWORK_STORAGE_CLEAN))) {
                        // Reset all the old cache
                        throw new IllegalStateException("A bundle cannot be started"); //$NON-NLS-1$
                    }
                }
            }

            // Uninstall all bundles not in the auto-deploy directory if
            // the 'uninstall' action is present.
            if (actionList.contains(AUTO_DEPLOY_UNINSTALL_VALUE)) {
                for (Iterator<Entry<String, Bundle>> it = installedBundleMap.entrySet().iterator(); it.hasNext();) {
                    Entry<String, Bundle> entry = it.next();
                    Bundle b = entry.getValue();
                    if (b.getBundleId() != 0) {
                        try {
                            b.uninstall();
                        } catch (BundleException ex) {
                            printError(ex, "Auto-deploy uninstall: "); //$NON-NLS-1$
                        }
                    }
                }
            }

            // Start all installed and/or updated bundles if the 'start'
            // action is present.
            if (actionList.contains(AUTO_DEPLOY_START_VALUE)) {
                for (int i = 0; i < startBundleList.size(); i++) {
                    try {
                        startBundleList.get(i).start();

                    } catch (BundleException ex) {
                        printError(ex, "Auto-deploy start: "); //$NON-NLS-1$
                    }
                }
            }
        }
    }

    /**
     * <p>
     * Processes the auto-install and auto-start properties from the specified configuration properties.
     * </p>
     */
    private static void processAutoProperties(Map<String, String> configMap, BundleContext context,
        WeasisLoader weasisLoader) {
        // Retrieve the Start Level service, since it will be needed
        // to set the start level of the installed bundles.
        StartLevel sl = (StartLevel) context
            .getService(context.getServiceReference(org.osgi.service.startlevel.StartLevel.class.getName()));

        // Retrieve all auto-install and auto-start properties and install
        // their associated bundles. The auto-install property specifies a
        // space-delimited list of bundle URLs to be automatically installed
        // into each new profile, while the auto-start property specifies
        // bundles to be installed and started. The start level to which the
        // bundles are assigned is specified by appending a ".n" to the
        // property name, where "n" is the desired start level for the list
        // of bundles. If no start level is specified, the default start
        // level is assumed.
        Map<String, BundleElement> bundleList = new HashMap<>();

        Set set = configMap.keySet();
        for (Iterator item = set.iterator(); item.hasNext();) {
            String key = ((String) item.next()).toLowerCase();

            // Ignore all keys that are not an auto property.
            if (!key.startsWith(AUTO_INSTALL_PROP) && !key.startsWith(AUTO_START_PROP)) {
                continue;
            }
            // If the auto property does not have a start level,
            // then assume it is the default bundle start level, otherwise
            // parse the specified start level.
            int startLevel = sl.getInitialBundleStartLevel();
            try {
                startLevel = Integer.parseInt(key.substring(key.lastIndexOf('.') + 1));
            } catch (NumberFormatException ex) {
                System.err.println("Invalid start level: " + key); //$NON-NLS-1$
            }
            boolean canBeStarted = key.startsWith(AUTO_START_PROP);
            StringTokenizer st = new StringTokenizer(configMap.get(key), "\" ", true); //$NON-NLS-1$
            for (String location = nextLocation(st); location != null; location = nextLocation(st)) {
                String bundleName = getBundleNameFromLocation(location);
                if (!"System Bundle".equals(bundleName)) { //$NON-NLS-1$
                    BundleElement b = new BundleElement(startLevel, location, canBeStarted);
                    bundleList.put(bundleName, b);
                }
            }
        }
        weasisLoader.setMax(bundleList.size());

        final Map<String, Bundle> installedBundleMap = new HashMap<>();
        Bundle[] bundles = context.getBundles();
        for (int i = 0; i < bundles.length; i++) {
            String bundleName = getBundleNameFromLocation(bundles[i].getLocation());
            if (bundleName == null) {
                // Should never happen
                continue;
            }
            try {
                BundleElement b = bundleList.get(bundleName);
                // Remove the bundles in cache when they are not in the config.properties list
                if (b == null) {
                    if (!"System Bundle".equals(bundleName)) {//$NON-NLS-1$
                        bundles[i].uninstall();
                        System.out.println("Uninstall not used: " + bundleName); //$NON-NLS-1$
                    }
                    continue;
                }
                // Remove snapshot version to install it every time
                if (bundles[i].getVersion().getQualifier().endsWith("SNAPSHOT")) { //$NON-NLS-1$
                    bundles[i].uninstall();
                    System.out.println("Uninstall SNAPSHOT: " + bundleName); //$NON-NLS-1$
                    continue;
                }
                installedBundleMap.put(bundleName, bundles[i]);

            } catch (Exception e) {
                System.err.println("Cannot remove from OSGI cache: " + bundleName); //$NON-NLS-1$
            }
        }

        int bundleIter = 0;

        // Parse and install the bundles associated with the key.
        for (Iterator<Entry<String, BundleElement>> iter = bundleList.entrySet().iterator(); iter.hasNext();) {
            Entry<String, BundleElement> element = iter.next();
            String bundleName = element.getKey();
            BundleElement bundle = element.getValue();
            if (bundle == null) {
                // Should never happen
                continue;
            }
            try {
                weasisLoader.writeLabel(WeasisLoader.LBL_DOWNLOADING + " " + bundleName); //$NON-NLS-1$
                // Do not download again the same bundle version but with different location or already in installed
                // in cache from a previous version of Weasis
                Bundle b = installedBundleMap.get(bundleName);
                if (b == null) {
                    b = installBundle(context, bundle.getLocation());
                    installedBundleMap.put(bundleName, b);
                }
                sl.setBundleStartLevel(b, bundle.getStartLevel());
                loadTranslationBundle(context, b, installedBundleMap);
            } catch (Exception ex) {
                if (bundleName.contains(System.getProperty("native.library.spec"))) { //$NON-NLS-1$
                    System.err.println("Cannot install native bundle: " + bundleName); //$NON-NLS-1$
                } else {
                    printError(ex, "Cannot install bundle: " + bundleName); //$NON-NLS-1$
                    if (ex.getCause() != null) {
                        ex.printStackTrace();
                    }
                    if (!Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT
                        .equals(configMap.get(Constants.FRAMEWORK_STORAGE_CLEAN))) {
                        // Reset all the old cache
                        throw new IllegalStateException("A bundle cannot be started"); //$NON-NLS-1$
                    }
                }
            } finally {
                bundleIter++;
                weasisLoader.setValue(bundleIter);
            }

        }

        weasisLoader.writeLabel(Messages.getString("AutoProcessor.start")); //$NON-NLS-1$
        // Now loop through the auto-start bundles and start them.
        for (Iterator<Entry<String, BundleElement>> iter = bundleList.entrySet().iterator(); iter.hasNext();) {
            Entry<String, BundleElement> element = iter.next();
            String bundleName = element.getKey();
            BundleElement bundle = element.getValue();
            if (bundle == null) {
                // Should never happen
                continue;
            }
            if (bundle.isCanBeStarted()) {
                try {
                    Bundle b = installedBundleMap.get(bundleName);
                    if (b == null) {
                        // Try to reinstall
                        b = installBundle(context, bundle.getLocation());
                    }
                    if (b != null) {
                        b.start();
                    }
                } catch (Exception ex) {
                    printError(ex, "Cannot start bundle: " + bundleName); //$NON-NLS-1$
                }
            }
        }
    }

    private static String getBundleNameFromLocation(String location) {
        if (location != null) {
            int index = location.lastIndexOf("/"); //$NON-NLS-1$
            String name = index >= 0 ? location.substring(index + 1) : location;
            index = name.lastIndexOf(".jar"); //$NON-NLS-1$
            return index >= 0 ? name.substring(0, index) : name;
        }
        return null;
    }

    private static void loadTranslationBundle(BundleContext context, Bundle b,
        final Map<String, Bundle> installedBundleMap) {
        if (WeasisLauncher.modulesi18n != null) {
            if (b != null) {
                StringBuilder p = new StringBuilder(b.getSymbolicName());
                p.append("-i18n-"); //$NON-NLS-1$
                // From 2.0.0, i18n module can be plugged in any version. The date (the qualifier)
                // will update the version.
                p.append("2.0.0"); //$NON-NLS-1$
                p.append(".jar"); //$NON-NLS-1$
                String filename = p.toString();
                String value = WeasisLauncher.modulesi18n.getProperty(filename);
                if (value != null) {
                    String baseURL = System.getProperty("weasis.i18n"); //$NON-NLS-1$
                    if (baseURL != null) {
                        String uri = baseURL + (baseURL.endsWith("/") ? filename : "/" + filename); //$NON-NLS-1$ //$NON-NLS-2$
                        String bundleName = getBundleNameFromLocation(filename);
                        try {
                            Bundle b2 = installedBundleMap.get(bundleName);
                            if (b2 == null) {
                                b2 = context.installBundle(uri,
                                    FileUtil.getAdaptedConnection(new URI(uri).toURL()).getInputStream());
                                installedBundleMap.put(bundleName, b);
                            }
                            if (b2 != null && !value.equals(b2.getVersion().getQualifier())) {
                                if (b2.getLocation().startsWith(baseURL)) {
                                    b2.update();
                                } else {
                                    // Handle same bundle version with different location
                                    try {
                                        b2.uninstall();
                                        context.installBundle(uri,
                                            FileUtil.getAdaptedConnection(new URI(uri).toURL()).getInputStream());
                                        installedBundleMap.put(bundleName, b);
                                    } catch (Exception exc) {
                                        System.err.println("Cannot install translation pack: " + uri); //$NON-NLS-1$
                                    }
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("Cannot install translation pack: " + uri); //$NON-NLS-1$
                        }
                    }
                }
            }
        }
    }

    private static void printError(Exception ex, String prefix) {
        System.err.println(prefix + " (" + ex //$NON-NLS-1$
            + ((ex.getCause() != null) ? " - " + ex.getCause() : "") + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    private static String nextLocation(StringTokenizer st) {
        String retVal = null;

        if (st.countTokens() > 0) {
            String tokenList = "\" "; //$NON-NLS-1$
            StringBuilder tokBuf = new StringBuilder(10);
            String tok = null;
            boolean inQuote = false;
            boolean tokStarted = false;
            boolean exit = false;
            while ((st.hasMoreTokens()) && (!exit)) {
                tok = st.nextToken(tokenList);
                if (tok.equals("\"")) { //$NON-NLS-1$
                    inQuote = !inQuote;
                    if (inQuote) {
                        tokenList = "\""; //$NON-NLS-1$
                    } else {
                        tokenList = "\" "; //$NON-NLS-1$
                    }

                } else if (tok.equals(" ")) { //$NON-NLS-1$
                    if (tokStarted) {
                        retVal = tokBuf.toString();
                        tokStarted = false;
                        tokBuf = new StringBuilder(10);
                        exit = true;
                    }
                } else {
                    tokStarted = true;
                    tokBuf.append(tok.trim());
                }
            }

            // Handle case where end of token stream and
            // still got data
            if ((!exit) && (tokStarted)) {
                retVal = tokBuf.toString();
            }
        }

        return retVal;
    }

    private static boolean isFragment(Bundle bundle) {
        return bundle.getHeaders().get(Constants.FRAGMENT_HOST) != null;
    }

    private static Bundle installBundle(BundleContext context, String location) throws Exception {
        boolean pack = location.endsWith(PACK200_COMPRESSION);
        if (pack) {
            // Remove the pack classifier from the location path
            location = location.substring(0, location.length() - 8);
            pack = context.getBundle(location) == null;
        }

        if (pack) {
            final URL url = new URL(location + PACK200_COMPRESSION);
            final PipedInputStream in = new PipedInputStream();
            try (final PipedOutputStream out = new PipedOutputStream(in)) {
                Thread t = new Thread(() -> {
                    try (JarOutputStream jarStream = new JarOutputStream(out);
                                    GZIPInputStream gzStream =
                                        new GZIPInputStream(FileUtil.getAdaptedConnection(url).getInputStream())) {
                        Unpacker unpacker = Pack200.newUnpacker();
                        unpacker.unpack(gzStream, jarStream);
                    } catch (Exception e1) {
                        System.err.println("Cannot install pack bundle: " + url); //$NON-NLS-1$
                        e1.printStackTrace();
                    }
                });
                t.start();
                return context.installBundle(location, in);
            }
        }
        return context.installBundle(location,
            FileUtil.getAdaptedConnection(new URI(location).toURL()).getInputStream());
    }

    static class BundleElement {
        private final int startLevel;
        private final String location;
        private final boolean canBeStarted;

        public BundleElement(int startLevel, String location, boolean canBeStarted) {

            this.startLevel = startLevel;
            this.location = location;
            this.canBeStarted = canBeStarted;
        }

        public int getStartLevel() {
            return startLevel;
        }

        public String getLocation() {
            return location;
        }

        public boolean isCanBeStarted() {
            return canBeStarted;
        }

    }
}