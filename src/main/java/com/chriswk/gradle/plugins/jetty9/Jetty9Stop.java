package com.chriswk.gradle.plugins.jetty9;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.logging.ProgressLogger;
import org.gradle.logging.ProgressLoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;

public class Jetty9Stop extends ConventionTask {
    private static Logger LOGGER = LoggerFactory.getLogger(Jetty9Stop.class);

    private Integer stopPort;

    private String stopKey;

    @TaskAction
    public void stop() {
        if (getStopPort() == null) {
            throw new InvalidUserDataException("Please specify a valid port");
        }
        if (getStopKey() == null) {
            throw new InvalidUserDataException("Please specify a valid stopKey");
        }

        ProgressLogger progressLogger = getServices().get(ProgressLoggerFactory.class).newOperation(Jetty9Stop.class);
        progressLogger.setDescription("Stop Jetty server");
        progressLogger.setShortDescription("Stopping Jetty");
        progressLogger.started();
        try {
            Socket s = new Socket(InetAddress.getByName("127.0.0.1"), getStopPort());
            s.setSoLinger(false, 0);

            OutputStream out = s.getOutputStream();
            out.write((getStopKey() + "\r\nstop\r\n").getBytes());
            out.flush();
            s.close();
        } catch (ConnectException e) {
            LOGGER.info("Jetty not running!");
        } catch (Exception e) {
            LOGGER.error("Exception during stopping", e);
        } finally {
            progressLogger.completed();
        }
    }

    /**
     * Returns the TCP port to use to send stop command.
     */
    public Integer getStopPort() {
        return stopPort;
    }

    /**
     * Sets the TCP port to use to send stop command.
     */
    public void setStopPort(Integer stopPort) {
        this.stopPort = stopPort;
    }

    /**
     * Returns the stop key.
     *
     * @see #setStopKey(String)
     */
    public String getStopKey() {
        return stopKey;
    }

    /**
     * Sets key to provide when stopping jetty.
     */
    public void setStopKey(String stopKey) {
        this.stopKey = stopKey;
    }
}
