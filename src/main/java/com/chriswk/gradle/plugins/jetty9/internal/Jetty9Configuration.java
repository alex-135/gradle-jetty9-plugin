package com.chriswk.gradle.plugins.jetty9.internal;

import org.eclipse.jetty.plus.webapp.PlusConfiguration;

import java.io.File;
import java.util.List;

public class Jetty9Configuration extends PlusConfiguration {
    private List<File> classPathFiles;
    private File webXmlFile;

    public Jetty9Configuration() {
        super();
    }

    public void setClassPathConfiguration(List<File> classPathFiles) {
        this.classPathFiles = classPathFiles;
    }

    public void setWebXml(File webXmlFile) {
        this.webXmlFile = webXmlFile;
    }

    /**
     * Set up the classloader for the webapp, using the various parts of the gradle project
     *
     * @see org.mortbay.jetty.webapp.Configuration#configureClassLoader()
     */

    public void configureClassLoader() throws Exception {
    }
}
