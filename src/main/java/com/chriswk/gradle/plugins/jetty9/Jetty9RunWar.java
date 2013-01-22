package com.chriswk.gradle.plugins.jetty9;

import com.chriswk.gradle.plugins.jetty9.internal.Jetty9PluginServer;
import com.chriswk.gradle.plugins.jetty9.internal.JettyPluginServerEclipse;
import org.eclipse.jetty.util.Scanner;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.gradle.api.tasks.InputFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Jetty9RunWar extends AbstractJetty9RunTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(Jetty9RunWar.class);


    /**
     * The location of the war file.
     */
    private File webApp;

    public void configureWebApplication() throws Exception {
        super.configureWebApplication();
        getWebAppConfig().setWar(getWebApp().getCanonicalPath());
        getWebAppConfig().configure();
    }

    public void validateConfiguration() {
    }

    public void configureScanner() {
        List<File> scanList = new ArrayList<File>();
        scanList.add(getProject().getBuildFile());
        scanList.add(getWebApp());
        getScanner().setScanDirs(scanList);

        List<Scanner.Listener> listeners = new ArrayList<Scanner.Listener>();
        listeners.add(new Scanner.BulkListener() {
            public void filesChanged(List changes) {
                try {
                    boolean reconfigure = changes.contains(getProject().getBuildFile().getCanonicalPath());
                    restartWebApp(reconfigure);
                } catch (Exception e) {
                    LOGGER.error("Error reconfiguring/restarting webapp after change in watched files");
                }
            }
        });

        setScannerListeners(listeners);
    }

    public void restartWebApp(boolean reconfigureScanner) throws Exception {
        LOGGER.info("Restarting webapp ...");
        LOGGER.debug("Stopping webapp ...");
        getWebAppConfig().stop();
        LOGGER.debug("Reconfiguring webapp ...");

        validateConfiguration();

        if (reconfigureScanner) {
            LOGGER.info("Reconfiguring scanner");
            List<File> scanList = new ArrayList<File>();
            scanList.add(getProject().getBuildFile());
            scanList.add(getWebApp());
            getScanner().setScanDirs(scanList);
        }

        LOGGER.debug("Restarting webapp ...");
        getWebAppConfig().start();
        LOGGER.info("Restart completed");
    }

    public void finishConfigurationBeforeStart() {
    }

    /**
     * Returns the web application to deploy.
     */
    @InputFile
    public File getWebApp() {
        return webApp;
    }

    public void setWebApp(File webApp) {
        this.webApp = webApp;
    }

    public void applyJettyXml() throws Exception {

        if (getJettyConfig() == null) {
            return;
        }

        LOGGER.info("Configuring Jetty from xml configuration file = {}", getJettyConfig());
        XmlConfiguration xmlConfiguration = new XmlConfiguration(getJettyConfig().toURI().toURL());
        xmlConfiguration.configure(getServer().getProxiedObject());
    }

    public JettyPluginServerEclipse createServer() throws Exception {
        return new Jetty9PluginServer();
    }

}
