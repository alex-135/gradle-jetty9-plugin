package com.chriswk.gradle.plugins.jetty9.internal;

import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class Monitor extends Thread {
    private static final Logger LOGGER = LoggerFactory.getLogger(Monitor.class);

    private String key;

    ServerSocket serverSocket;
    private final Server server;

    public Monitor(int port, String key, Server server) throws IOException {
        this.server = server;
        if (port <= 0) {
            throw new IllegalStateException("Bad stop port");
        }
        if (key == null) {
            throw new IllegalStateException("Bad stop key");
        }

        this.key = key;
        setDaemon(true);
        setName("StopJetty9PluginMonitor");
        serverSocket = new ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"));
        serverSocket.setReuseAddress(true);
    }

    public void run() {
        while (serverSocket != null) {
            Socket socket = null;
            try {
                socket = serverSocket.accept();
                socket.setSoLinger(false, 0);
                LineNumberReader lin = new LineNumberReader(new InputStreamReader(socket.getInputStream()));

                String key = lin.readLine();
                if (!this.key.equals(key)) {
                    continue;
                }
                String cmd = lin.readLine();
                if ("stop".equals(cmd)) {
                    try {
                        socket.close();
                    } catch (Exception e) {
                        LOGGER.debug("Exception when stopping server", e);
                    }
                    try {
                        socket.close();
                    } catch (Exception e) {
                        LOGGER.debug("Exception when stopping server", e);
                    }
                    try {
                        serverSocket.close();
                    } catch (Exception e) {
                        LOGGER.debug("Exception when stopping server", e);
                    }

                    serverSocket = null;

                    try {
                        LOGGER.info("Stopping server due to received '{}' command...", cmd);
                        server.stop();
                    } catch (Exception e) {
                        LOGGER.error("Exception when stopping server", e);
                    }

                    //We've stopped the server. No point hanging around any more...
                    return;
                } else {
                    LOGGER.info("Unsupported monitor operation");
                }
            } catch (Exception e) {
                LOGGER.error("Exception during monitoring Server", e);
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (Exception e) {
                        LOGGER.debug("Exception when stopping server", e);
                    }
                }
                socket = null;
            }
        }
    }
}
