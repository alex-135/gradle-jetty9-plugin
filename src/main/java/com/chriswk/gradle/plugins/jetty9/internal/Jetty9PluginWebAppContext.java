package com.chriswk.gradle.plugins.jetty9.internal;

import org.eclipse.jetty.plus.webapp.EnvConfiguration;
import org.eclipse.jetty.webapp.*;

import java.io.File;
import java.util.List;

public class Jetty9PluginWebAppContext extends WebAppContext {
    private List<File> classpathFiles;
    private File jettyEnvXmlFile;
    private File webXmlFile;
    private WebInfConfiguration webInfConfig = new WebInfConfiguration();
    private EnvConfiguration envConfig = new EnvConfiguration();
    private Jetty9Configuration gradleConfig = new Jetty9Configuration();
    private JettyWebXmlConfiguration jettyWebConfig = new JettyWebXmlConfiguration();
    private TagLibConfiguration tagConfig = new TagLibConfiguration();
    private Configuration[] configs = new Configuration[]{
            webInfConfig, envConfig, gradleConfig, jettyWebConfig, tagConfig
    };

    public Jetty9PluginWebAppContext() {
        super();
        setConfigurations(configs);
    }

    public void setClasspathFiles(List<File> classpathFiles) {
        this.classpathFiles = classpathFiles;
    }

    public List<File> getClasspathFiles() {
        return this.classpathFiles;
    }

    public void setWebXmlFile(File webXmlFile) {
        this.webXmlFile = webXmlFile;
    }

    public void setJettyEnvXmlFile(File jettyEnvXmlFile) {
        this.jettyEnvXmlFile = jettyEnvXmlFile;
    }

    public File getJettyEnvXmlFile() {
        return this.jettyEnvXmlFile;
    }

    public void configure() {
        setConfigurations(configs);
        gradleConfig.setClassPathConfiguration(classpathFiles);
        gradleConfig.setWebXml(webXmlFile);
        try {
            if (this.jettyEnvXmlFile != null) {
                envConfig.setJettyEnvXml(this.jettyEnvXmlFile.toURI().toURL());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public void doStart() throws Exception {
        super.doStart();
    }

    public void doStop() throws Exception {
        super.doStop();
    }

}
