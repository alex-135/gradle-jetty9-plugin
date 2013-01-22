package com.chriswk.gradle.plugins.jetty9;

import com.chriswk.gradle.plugins.jetty9.internal.Jetty9PluginServer;
import com.chriswk.gradle.plugins.jetty9.internal.JettyPluginServerEclipse;
import com.google.common.collect.Sets;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.util.Scanner;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceCollection;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.jetty.ScanTargetPattern;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class Jetty9Run extends AbstractJetty9RunTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(Jetty9Run.class);

    /**
     * List of other contexts to set up. Optional.
     */
    private ContextHandler[] contextHandlers;

    /**
     * The location of a jetty-env.xml file. Optional.
     */
    private File jettyEnvXml;

    /**
     * The location of the web.xml file. If not set then it is assumed it is in ${basedir}/src/main/webapp/WEB-INF
     */
    private File webXml;

    /**
     * Root directory for all html/jsp etc files.
     */
    private File webAppSourceDirectory;

    /**
     * List of files or directories to additionally periodically scan for changes. Optional.
     */
    private File[] scanTargets;

    /**
     * List of directories with ant-style &lt;include&gt; and &lt;exclude&gt; patterns for extra targets to periodically
     * scan for changes. Can be used instead of, or in conjunction with &lt;scanTargets&gt;.Optional.
     */
    private ScanTargetPattern[] scanTargetPatterns;

    /**
     * jetty-env.xml as a File.
     */
    private File jettyEnvXmlFile;

    /**
     * List of files on the classpath for the webapp.
     */
    private List<File> classPathFiles;

    /**
     * Extra scan targets as a list.
     */
    private Set<File> extraScanTargets;

    private FileCollection classpath;
    private Handler[] configuredContextHandlers;

    @Override
    public void validateConfiguration() {
        // check the location of the static content/jsps etc
        try {
            if ((getWebAppSourceDirectory() == null) || !getWebAppSourceDirectory().exists()) {
                throw new InvalidUserDataException("Webapp source directory "
                        + (getWebAppSourceDirectory() == null ? "null" : getWebAppSourceDirectory().getCanonicalPath())
                        + " does not exist");
            } else {
                LOGGER.info("Webapp source directory = " + getWebAppSourceDirectory().getCanonicalPath());
            }
        } catch (IOException e) {
            throw new InvalidUserDataException("Webapp source directory does not exist", e);
        }

        // check reload mechanic
        if (!"automatic".equalsIgnoreCase(reload) && !"manual".equalsIgnoreCase(reload)) {
            throw new InvalidUserDataException("invalid reload mechanic specified, must be 'automatic' or 'manual'");
        } else {
            LOGGER.info("Reload Mechanic: " + reload);
        }

        // get the web.xml file if one has been provided, otherwise assume it is in the webapp src directory
        if (getWebXml() == null) {
            setWebXml(new File(new File(getWebAppSourceDirectory(), "WEB-INF"), "web.xml"));
        }
        LOGGER.info("web.xml file = " + getWebXml());

        //check if a jetty-env.xml location has been provided, if so, it must exist
        if (getJettyEnvXml() != null) {
            setJettyEnvXmlFile(jettyEnvXml);

            try {
                if (!getJettyEnvXmlFile().exists()) {
                    throw new InvalidUserDataException("jetty-env.xml file does not exist at location " + jettyEnvXml);
                } else {
                    LOGGER.info(" jetty-env.xml = " + getJettyEnvXmlFile().getCanonicalPath());
                }
            } catch (IOException e) {
                throw new InvalidUserDataException("jetty-env.xml does not exist");
            }
        }

        setExtraScanTargets(new ArrayList<File>());
        if (scanTargets != null) {
            for (File scanTarget : scanTargets) {
                LOGGER.info("Added extra scan target:" + scanTarget);
                getExtraScanTargets().add(scanTarget);
            }
        }

        if (scanTargetPatterns != null) {
            for (ScanTargetPattern scanTargetPattern : scanTargetPatterns) {
                ConfigurableFileTree files = getProject().fileTree(scanTargetPattern.getDirectory());
                files.include(scanTargetPattern.getIncludes());
                files.exclude(scanTargetPattern.getExcludes());
                Set<File> currentTargets = getExtraScanTargets();
                if (currentTargets != null && !currentTargets.isEmpty()) {
                    currentTargets.addAll(files.getFiles());
                } else {
                    setExtraScanTargets(files.getFiles());
                }
            }
        }
    }

    private Set<File> getDependencyFiles() {
        List<Resource> overlays = new ArrayList<Resource>();

        Set<File> dependencies = getClasspath().getFiles();
        LOGGER.debug("Adding dependencies {} for WEB-INF/lib ", dependencies);

        if (!overlays.isEmpty()) {
            try {
                Resource resource = getWebAppConfig().getBaseResource();
                ResourceCollection rc = new ResourceCollection();
                if (resource == null) {
                    // nothing configured, so we automagically enable the overlays
                    int size = overlays.size() + 1;
                    Resource[] resources = new Resource[size];
                    resources[0] = Resource.newResource(getWebAppSourceDirectory().toURI().toURL());
                    for (int i = 1; i < size; i++) {
                        resources[i] = overlays.get(i - 1);
                        LOGGER.info("Adding overlay: " + resources[i]);
                    }
                    rc.setResources(resources);
                } else {
                    if (resource instanceof ResourceCollection) {
                        // there was a preconfigured ResourceCollection ... append the artifact wars
                        Resource[] old = ((ResourceCollection) resource).getResources();
                        int size = old.length + overlays.size();
                        Resource[] resources = new Resource[size];
                        System.arraycopy(old, 0, resources, 0, old.length);
                        for (int i = old.length; i < size; i++) {
                            resources[i] = overlays.get(i - old.length);
                            LOGGER.info("Adding overlay: " + resources[i]);
                        }
                        rc.setResources(resources);
                    } else {
                        // baseResource was already configured w/c could be src/main/webapp
                        if (!resource.isDirectory() && String.valueOf(resource.getFile()).endsWith(".war")) {
                            // its a war
                            resource = Resource.newResource("jar:" + resource.getURL().toString() + "!/");
                        }
                        int size = overlays.size() + 1;
                        Resource[] resources = new Resource[size];
                        resources[0] = resource;
                        for (int i = 1; i < size; i++) {
                            resources[i] = overlays.get(i - 1);
                            LOGGER.info("Adding overlay: " + resources[i]);
                        }
                        rc.setResources(resources);
                    }
                }
                getWebAppConfig().setBaseResource(rc);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return dependencies;
    }

    private List<File> setUpClassPath() {
        List<File> classPathFiles = new ArrayList<File>();

        classPathFiles.addAll(getDependencyFiles());

        if (LOGGER.isDebugEnabled()) {
            for (File classPathFile : classPathFiles) {
                LOGGER.debug("classpath element: " + classPathFile.getName());
            }
        }
        return classPathFiles;
    }
    
    @Override
    public void configureScanner() {
        // start the scanner thread (if necessary) on the main webapp
        List<File> scanList = new ArrayList<File>();
        scanList.add(getWebXml());
        if (getJettyEnvXmlFile() != null) {
            scanList.add(getJettyEnvXmlFile());
        }
        File jettyWebXmlFile = findJettyWebXmlFile(new File(getWebAppSourceDirectory(), "WEB-INF"));
        if (jettyWebXmlFile != null) {
            scanList.add(jettyWebXmlFile);
        }
        scanList.addAll(getExtraScanTargets());
        scanList.add(getProject().getBuildFile());
        scanList.addAll(getClassPathFiles());
        getScanner().setScanDirs(scanList);
        List<Scanner.Listener> listeners = new ArrayList<Scanner.Listener>();
        listeners.add(new Scanner.BulkListener() {
            public void filesChanged(List changes) {
                try {
                    boolean reconfigure = changes.contains(getProject().getBuildFile().getCanonicalPath());
                    restartWebApp(reconfigure);
                } catch (Exception e) {
                    LOGGER.error("Error reconfiguring/restarting webapp after change in watched files", e);
                }
            }
        });
        setScannerListeners(listeners);
    }

    public void restartWebApp(boolean reconfigureScanner) throws Exception {
        LOGGER.info("restarting " + getWebAppConfig());
        LOGGER.debug("Stopping webapp ...");
        getWebAppConfig().stop();
        LOGGER.debug("Reconfiguring webapp ...");

        validateConfiguration();
        configureWebApplication();

        // check if we need to reconfigure the scanner
        if (reconfigureScanner) {
            LOGGER.info("Reconfiguring scanner ...");
            List<File> scanList = new ArrayList<File>();
            scanList.add(getWebXml());
            if (getJettyEnvXmlFile() != null) {
                scanList.add(getJettyEnvXmlFile());
            }
            scanList.addAll(getExtraScanTargets());
            scanList.add(getProject().getBuildFile());
            scanList.addAll(getClassPathFiles());
            getScanner().setScanDirs(scanList);
        }

        LOGGER.debug("Restarting webapp ...");
        getWebAppConfig().start();
        LOGGER.info("Restart completed at " + new Date().toString());
    }

    @Override
    public void applyJettyXml() throws Exception {
        if (getJettyConfig() == null) {
            return;
        }

        LOGGER.info("Configuring Jetty from xml configuration file = " + getJettyConfig());
        XmlConfiguration xmlConfiguration = new XmlConfiguration(getJettyConfig().toURI().toURL());
        xmlConfiguration.configure(getServer().getProxiedObject());
    }

    @Override
    public JettyPluginServerEclipse createServer() throws Exception {
        return new Jetty9PluginServer();
    }

    @Override
    public void finishConfigurationBeforeStart() throws Exception {
        Handler[] handlers = getConfiguredContextHandlers();
        JettyPluginServerEclipse plugin = getServer();
        Server server = (Server) plugin.getProxiedObject();

        HandlerCollection contexts = (HandlerCollection) server.getChildHandlerByClass(ContextHandlerCollection.class);
        if (contexts == null) {
            contexts = (HandlerCollection) server.getChildHandlerByClass(HandlerCollection.class);
        }

        for (int i = 0; (handlers != null) && (i < handlers.length); i++) {
            contexts.addHandler(handlers[i]);
        }
    }

    public ContextHandler[] getContextHandlers() {
        return contextHandlers;
    }

    public void setContextHandlers(ContextHandler[] contextHandlers) {
        this.contextHandlers = contextHandlers;
    }

    @InputFile
    @Optional
    public File getJettyEnvXml() {
        return jettyEnvXml;
    }

    public void setJettyEnvXml(File jettyEnvXml) {
        this.jettyEnvXml = jettyEnvXml;
    }

    public File getWebXml() {
        return webXml;
    }

    public void setWebXml(File webXml) {
        this.webXml = webXml;
    }

    @InputDirectory
    public File getWebAppSourceDirectory() {
        return webAppSourceDirectory;
    }

    public void setWebAppSourceDirectory(File webAppSourceDirectory) {
        this.webAppSourceDirectory = webAppSourceDirectory;
    }

    public File[] getScanTargets() {
        return scanTargets;
    }

    public void setScanTargets(File[] scanTargets) {
        this.scanTargets = scanTargets;
    }

    public ScanTargetPattern[] getScanTargetPatterns() {
        return scanTargetPatterns;
    }

    public void setScanTargetPatterns(ScanTargetPattern[] scanTargetPatterns) {
        this.scanTargetPatterns = scanTargetPatterns;
    }

    @InputFile
    @Optional
    public File getJettyEnvXmlFile() {
        return jettyEnvXmlFile;
    }

    public void setJettyEnvXmlFile(File jettyEnvXmlFile) {
        this.jettyEnvXmlFile = jettyEnvXmlFile;
    }

    public List<File> getClassPathFiles() {
        return classPathFiles;
    }

    public void setClassPathFiles(List<File> classPathFiles) {
        this.classPathFiles = classPathFiles;
    }

    public Set<File> getExtraScanTargets() {
        return extraScanTargets;
    }

    public void setExtraScanTargets(Iterable<File> extraScanTargets) {
        this.extraScanTargets = Sets.newLinkedHashSet(extraScanTargets);
    }

    @InputFiles
    public FileCollection getClasspath() {
        return classpath;
    }

    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

    public Handler[] getConfiguredContextHandlers() {
        return configuredContextHandlers;
    }
}
