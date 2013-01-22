package com.chriswk.gradle.plugins.jetty9.internal;

import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Jetty9PluginServer implements JettyPluginServerEclipse {
    private static final Logger LOGGER = LoggerFactory.getLogger(Jetty9PluginServer.class);

    public static final int DEFAULT_MAX_IDLE_TIME = 30000;

    private Server server;
    private ContextHandlerCollection contexts;
    HandlerCollection handlers;
    private RequestLogHandler requestLogHandler;
    private DefaultHandler defaultHandler;

    private RequestLog requestLog;

    public Jetty9PluginServer() {
        this.server = new Server();
        this.server.setStopAtShutdown(true);
        Resource.setDefaultUseCaches(true);
    }


    public void setConnectors(Object[] connectors) {
        if (connectors == null || connectors.length == 0) {
            return;
        }

        for (int i = 0; i < connectors.length; i++) {
            Connector connector = (Connector) connectors[i];
            LOGGER.debug("Setting Connector: " + connector.getClass().getName());
            this.server.addConnector(connector);
        }
    }

    public Object[] getConnectors() {
        return this.server.getConnectors();
    }

    @Override
    public void setRequestLog(Object requestLog) {
        this.requestLog = (RequestLog) requestLog;
    }

    @Override
    public Object getRequestLog() {
        return this.requestLog;
    }

    @Override
    public void setLoginServices(Object[] services) throws Exception {
        if (services != null) {
            SecurityHandler sHandler;
            if (this.server.getChildHandlerByClass(SecurityHandler.class) != null) {
                sHandler = this.server.getChildHandlerByClass(SecurityHandler.class);
            } else {
                sHandler = new ConstraintSecurityHandler();
            }
            for(Object service : services) {
                sHandler.addBean(service);
            }
            this.handlers.addHandler(sHandler);
        }
    }

    @Override
    public Object[] getLoginServices() {
        if (this.server.getChildHandlerByClass(SecurityHandler.class) == null ) {
            return new Object[0];
        } else {
            return this.server.getChildHandlerByClass(SecurityHandler.class).getBeans().toArray();
        }
    }

    @Override
    public void configureHandlers() throws Exception {
        this.defaultHandler = new DefaultHandler();
        this.requestLogHandler = new RequestLogHandler();
        if (this.requestLog != null) {
            this.requestLogHandler.setRequestLog(this.requestLog);
        }

        this.contexts = (ContextHandlerCollection) server.getChildHandlerByClass(ContextHandlerCollection.class);
        if (this.contexts == null) {
            this.contexts = new ContextHandlerCollection();
            this.handlers = (HandlerCollection) server.getChildHandlerByClass(HandlerCollection.class);
            if (this.handlers == null) {
                this.handlers = new HandlerCollection();
                this.server.setHandler(handlers);
                this.handlers.setHandlers(new Handler[]{this.contexts, this.defaultHandler, this.requestLogHandler});
            } else {
                this.handlers.addHandler(this.contexts);
            }
        }
    }

    @Override
    public void addWebApplication(WebAppContext webapp) throws Exception {
        contexts.addHandler(webapp);
    }

    @Override
    public void start() throws Exception {
        LOGGER.info("Starting jetty " + this.server.getClass().getPackage().getImplementationVersion() + " ...");
        this.server.start();
    }

    @Override
    public Object createDefaultConnector(int port) throws Exception {
        ServerConnector defaultConnector = new ServerConnector(this.server);
        defaultConnector.setPort(port);
        defaultConnector.setIdleTimeout(DEFAULT_MAX_IDLE_TIME);

        return defaultConnector;
    }

    @Override
    public void join() throws Exception {
        this.server.getThreadPool().join();
    }

    @Override
    public Object getProxiedObject() {
        return this.server;
    }
}
