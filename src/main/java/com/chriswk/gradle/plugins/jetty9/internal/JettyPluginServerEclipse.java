package com.chriswk.gradle.plugins.jetty9.internal;

import org.eclipse.jetty.webapp.WebAppContext;

public interface JettyPluginServerEclipse extends Proxy {
    public void setRequestLog(Object requestLog);
    public Object getRequestLog();

    public void setConnectors(Object[] connectors) throws Exception;
    public Object[] getConnectors();

    public void setLoginServices(Object[] services) throws Exception;
    public Object[] getLoginServices();

    public void configureHandlers() throws Exception;

    public void addWebApplication(WebAppContext webapp) throws Exception;

    public void start() throws Exception;

    public Object createDefaultConnector(int port) throws Exception;

    public void join() throws Exception;
}
