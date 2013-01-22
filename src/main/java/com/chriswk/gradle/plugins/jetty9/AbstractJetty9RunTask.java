package com.chriswk.gradle.plugins.jetty9;

import com.chriswk.gradle.plugins.jetty9.internal.ConsoleScanner;
import com.chriswk.gradle.plugins.jetty9.internal.Jetty9PluginWebAppContext;
import com.chriswk.gradle.plugins.jetty9.internal.JettyPluginServerEclipse;
import com.chriswk.gradle.plugins.jetty9.internal.Monitor;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Scanner;
import org.gradle.api.GradleException;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.plugins.jetty.AbstractJettyRunTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.logging.ProgressLogger;
import org.gradle.logging.ProgressLoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URLClassLoader;
import java.util.*;

/**
 * Base class for all tasks which deploy a web application to an embedded Jetty9 web container.
 */
public abstract class AbstractJetty9RunTask extends ConventionTask {
    private static Logger logger = LoggerFactory.getLogger(AbstractJetty9RunTask.class);

    private Iterable<File> additionalRuntimeJars = new ArrayList<File>();

    /**
     * The proxy for the Server object
     */
    private JettyPluginServerEclipse server;


    /**
     * The "virtual" webapp created by the plugin.
     */
    private Jetty9PluginWebAppContext webAppConfig;
    /**
     * The context path for the webapp
     */
    private String contextPath;

    /**
     * A webdefault.xml file to use instead of the default for the webapp. Optional.
     */
    private File webDefaultXml;

    /**
     * A web.xml file to be applied AFTER the webapp's web.xml file. Useful for applying different build profiles, eg test, production etc. Optional.
     */
    private File overrideWebXml;

    /**
     * The interval in seconds to scan the webapp for changes and restart the context if necessary. Ignored if reload is enabled. Disabled by default.
     */
    private int scanIntervalSeconds;


    /**
     * reload can be set to either 'automatic' or 'manual' <p/> if 'manual' then the context can be reloaded by a linefeed in the console if 'automatic' then traditional reloading on changed files is
     * enabled.
     */
    protected String reload;

    /**
     * Location of a jetty xml configuration file whose contents will be applied before any plugin configuration. Optional.
     */
    private File jettyConfig;

    /**
     * Port to listen to stop jetty on.
     */
    private Integer stopPort;

    /**
     * Key to provide when stopping jetty.
     */
    private String stopKey;

    /**
     * <p> Determines whether or not the server blocks when started. The default behavior (daemon = false) will cause the server to pause other processes while it continues to handle web requests.
     * This is useful when starting the server with the intent to work with it interactively. </p><p> Often, it is desirable to let the server start and continue running subsequent processes in an
     * automated build environment. This can be facilitated by setting daemon to true. </p>
     */
    private boolean daemon;

    private Integer httpPort;

    /**
     * List of connectors to use. If none are configured then we use a single SelectChannelConnector at port 8080
     */
    private Connector[] connectors;

    /**
     * List of security realms to set up. Optional.
     */
    private LoginService[] loginServices;

    /**
     * A RequestLog implementation to use for the webapp at runtime. Optional.
     */
    private RequestLog requestLog;

    /**
     * A scanner to check for changes to the webapp.
     */
    private Scanner scanner = new Scanner();

    /**
     * List of Listeners for the scanner.
     */
    protected List<Scanner.Listener> scannerListeners;

    /**
     * A scanner to check ENTER hits on the console.
     */
    protected Thread consoleScanner;

    public static final String PORT_SYSPROPERTY = "jetty.port";

    public abstract void validateConfiguration();

    public abstract void configureScanner();

    public abstract void applyJettyXml() throws Exception;

    public abstract void restartWebApp(boolean reconfigureScanner) throws Exception;

    /**
     * create a proxy that wraps a particular jetty version Server object.
     *
     * @return The Jetty Plugin Server
     */
    public abstract JettyPluginServerEclipse createServer() throws Exception;

    public abstract void finishConfigurationBeforeStart() throws Exception;

    @TaskAction
    protected void start() {
        ClassLoader originalClassLoader = Server.class.getClassLoader();
        List<File> additionalClasspath = new ArrayList<File>();
        for (File additionalRuntimeJar : getAdditionalRuntimeJars()) {
            additionalClasspath.add(additionalRuntimeJar);
        }
        URLClassLoader jettyClassLoader = new URLClassLoader(new DefaultClassPath(additionalClasspath).getAsURLArray(), originalClassLoader);
        try {
            Thread.currentThread().setContextClassLoader(jettyClassLoader);
            startJetty9();
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    public JettyPluginServerEclipse getServer() {
        return this.server;
    }

    public void setServer(JettyPluginServerEclipse server) {
        this.server = server;
    }

    public List<Scanner.Listener> getScannerListeners() {
        return scannerListeners;
    }

    public void setScannerListeners(List<Scanner.Listener> scannerListeners) {
        this.scannerListeners = scannerListeners;
    }

    public Scanner getScanner() {
        return scanner;
    }

    public void startJetty9() {
        logger.info("Configuring Jetty 9 for " + getProject());
        validateConfiguration();
        startJetty9Internal();
    }

    public void startJetty9Internal() {
        ProgressLoggerFactory progressLoggerFactory = getServices().get(ProgressLoggerFactory.class);
        ProgressLogger progressLogger = progressLoggerFactory.newOperation(AbstractJettyRunTask.class);
        progressLogger.setDescription("Start Jetty 9 server");
        progressLogger.setShortDescription("Starting Jetty 9");
        progressLogger.started();
        try {
            setServer(createServer());

            applyJettyXml();

            JettyPluginServerEclipse plugin = getServer();

            Object[] configuredConnectors = getConnectors();

            plugin.setConnectors(configuredConnectors);
            Object[] connectors = plugin.getConnectors();

            if (connectors == null || connectors.length == 0) {
                configuredConnectors = new Object[]{plugin.createDefaultConnector(getHttpPort())};
            }
            //set up a RequestLog if one is provided
            if (getRequestLog() != null) {
                getServer().setRequestLog(getRequestLog());
            }

            //set up the webapp and any context provided
            getServer().configureHandlers();
            configureWebApplication();
            getServer().addWebApplication(webAppConfig);

            // set up login services
            Object[] configuredLoginServices = getLoginServices();
            for (int i = 0; (configuredLoginServices != null) && i < configuredLoginServices.length; i++) {
                logger.debug(configuredLoginServices[i].getClass().getName() + ": " + configuredLoginServices[i].toString());
            }

            plugin.setLoginServices(configuredLoginServices);

            //do any other configuration required by the
            //particular Jetty version
            finishConfigurationBeforeStart();

            // start Jetty
            server.start();

            if (daemon) {
                return;
            }

            if (getStopPort() != null && getStopPort() > 0 && getStopKey() != null) {
                Monitor monitor = new Monitor(getStopPort(), getStopKey(), (Server) server.getProxiedObject());
                monitor.start();
            }

            // start the scanner thread (if necessary) on the main webapp
            configureScanner();
            startScanner();

            // start the new line scanner thread if necessary
            startConsoleScanner();
        } catch (Exception e) {
            throw new GradleException("Could not start the Jetty 9 server.", e);
        } finally {
            progressLogger.completed();
        }
    }

    /**
     * Run a scanner thread on the given list of files and directories, calling stop/start on the given list of LifeCycle objects if any of the watched files change.
     */
    private void startScanner() throws Exception {
        // check if scanning is enabled
        if (getScanIntervalSeconds() <= 0) {
            return;
        }

        // check if reload is manual. It disables file scanning
        if ("manual".equalsIgnoreCase(reload)) {
            // issue a warning if both scanIntervalSeconds and reload
            // are enabled
            logger.warn("scanIntervalSeconds is set to " + scanIntervalSeconds
                    + " but will be IGNORED due to manual reloading");
            return;
        }

        scanner.setReportExistingFilesOnStartup(false);
        scanner.setScanInterval(getScanIntervalSeconds());
        scanner.setRecursive(true);
        List listeners = getScannerListeners();
        Iterator itor = listeners == null ? null : listeners.iterator();
        while (itor != null && itor.hasNext()) {
            scanner.addListener((Scanner.Listener) itor.next());
        }
        logger.info("Starting scanner at interval of " + getScanIntervalSeconds() + " seconds.");
        scanner.start();
    }

    /**
     * Run a thread that monitors the console input to detect ENTER hits.
     */
    protected void startConsoleScanner() {
        if ("manual".equalsIgnoreCase(reload)) {
            logger.info("Console reloading is ENABLED. Hit ENTER on the console to restart the context.");
            consoleScanner = new ConsoleScanner(this);
            consoleScanner.start();
        }
    }
    /**
     * Subclasses should invoke this to setup basic info on the webapp
     *
     * @throws Exception
     */
    public void configureWebApplication() throws Exception {
        if (webAppConfig == null) {
            webAppConfig = new Jetty9PluginWebAppContext();
        }
        webAppConfig.setContextPath(getContextPath().startsWith("/") ? getContextPath() : "/" + getContextPath());
        if (getTemporaryDir() != null) {
            webAppConfig.setTempDirectory(getTemporaryDir());
        }
        if (getWebDefaultXml() != null) {
            webAppConfig.setDefaultsDescriptor(getWebDefaultXml().getCanonicalPath());
        }
        if (getOverrideWebXml() != null) {
            webAppConfig.setOverrideDescriptor(getOverrideWebXml().getCanonicalPath());
        }

        // Don't treat JCL or Log4j as system classes
        Set<String> systemClasses = new LinkedHashSet<String>(Arrays.asList(webAppConfig.getSystemClasses()));
        systemClasses.remove("org.apache.commons.logging.");
        systemClasses.remove("org.apache.log4j.");
        webAppConfig.setSystemClasses(systemClasses.toArray(new String[systemClasses.size()]));

        webAppConfig.setParentLoaderPriority(false);

        logger.info("Context path = " + webAppConfig.getContextPath());
        logger.info("Tmp directory = " + " determined at runtime");
        logger.info("Web defaults = " + (webAppConfig.getDefaultsDescriptor() == null ? " jetty default"
                : webAppConfig.getDefaultsDescriptor()));
        logger.info("Web overrides = " + (webAppConfig.getOverrideDescriptor() == null ? " none"
                : webAppConfig.getOverrideDescriptor()));
    }

    /**
     * Try and find a jetty-web.xml file, using some historical naming conventions if necessary.
     *
     * @return File object to the location of the jetty-web.xml
     */
    public File findJettyWebXmlFile(File webInfDir) {
        if (webInfDir == null) {
            return null;
        }
        if (!webInfDir.exists()) {
            return null;
        }

        File f = new File(webInfDir, "jetty-web.xml");
        if (f.exists()) {
            return f;
        }

        //try some historical alternatives
        f = new File(webInfDir, "web-jetty.xml");
        if (f.exists()) {
            return f;
        }
        f = new File(webInfDir, "jetty6-web.xml");
        if (f.exists()) {
            return f;
        }

        return null;
    }

    /**
     * Returns the classpath to make available to the web application.
     */
    @InputFiles
    public Iterable<File> getAdditionalRuntimeJars() {
        return additionalRuntimeJars;
    }

    public void setAdditionalRuntimeJars(Iterable<File> additionalRuntimeJars) {
        this.additionalRuntimeJars = additionalRuntimeJars;
    }

    public Connector[] getConnectors() {
        return connectors;
    }

    public Integer getHttpPort() {
        return httpPort;
    }

    public RequestLog getRequestLog() {
        return requestLog;
    }

    public LoginService[] getLoginServices() {
        return loginServices;
    }

    public Integer getStopPort() {
        return stopPort;
    }

    public String getStopKey() {
        return stopKey;
    }

    public String getContextPath() {
        return contextPath;
    }

    public File getWebDefaultXml() {
        return webDefaultXml;
    }

    public File getOverrideWebXml() {
        return overrideWebXml;
    }

    public int getScanIntervalSeconds() {
        return scanIntervalSeconds;
    }
    public Jetty9PluginWebAppContext getWebAppConfig() {
        return webAppConfig;
    }

    public void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }

    public void setScanIntervalSeconds(int scanIntervalSeconds) {
        this.scanIntervalSeconds = scanIntervalSeconds;
    }

    public String getReload() {
        return reload;
    }

    public void setReload(String reload) {
        this.reload = reload;
    }

    @InputFile
    @Optional
    public File getJettyConfig() {
        return jettyConfig;
    }

    public void setJettyConfig(File jettyConfig) {
        this.jettyConfig = jettyConfig;
    }


}
