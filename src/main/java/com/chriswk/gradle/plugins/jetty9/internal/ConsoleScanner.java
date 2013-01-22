package com.chriswk.gradle.plugins.jetty9.internal;

import com.chriswk.gradle.plugins.jetty9.AbstractJetty9RunTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ConsoleScanner extends Thread {
    private static Logger LOGGER = LoggerFactory.getLogger(ConsoleScanner.class);

    private final AbstractJetty9RunTask task;

    public ConsoleScanner(AbstractJetty9RunTask abstractJetty9RunTask) {
        this.task = abstractJetty9RunTask;
        setName("Console scanner");
        setDaemon(true);
    }

    public void run() {
        try {
            while (true) {
                checkSystemInput();
                getSomeSleep();
            }
        } catch (IOException e) {
            LOGGER.warn("Error when checking console input.", e);
        }
    }

    private void getSomeSleep() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            LOGGER.debug("Error while sleeping.", e);
        }
    }

    private void checkSystemInput() throws IOException {
        while (System.in.available() > 0) {
            int inputByte = System.in.read();
            if (inputByte >= 0) {
                char c = (char) inputByte;
                if (c == '\n') {
                    restartWebApp();
                }
            }
        }
    }

    /**
     * Skip buffered bytes of system console.
     */
    private void clearInputBuffer() {
        try {
            while (System.in.available() > 0) {
                // System.in.skip doesn't work properly. I don't know why
                long available = System.in.available();
                for (int i = 0; i < available; i++) {
                    if (System.in.read() == -1) {
                        break;
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Error discarding console input buffer", e);
        }
    }

    private void restartWebApp() {
        try {
            task.restartWebApp(false);
            // Clear input buffer to discard anything entered on the console
            // while the application was being restarted.
            clearInputBuffer();
        } catch (Exception e) {
            LOGGER.error("Error reconfiguring/restarting webapp after a new line on the console", e);
        }
    }

}
