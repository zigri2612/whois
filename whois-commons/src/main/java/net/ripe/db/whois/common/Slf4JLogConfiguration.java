package net.ripe.db.whois.common;

import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.logging.Slf4JLoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class Slf4JLogConfiguration {
    private Slf4JLogConfiguration() {
    }

    public static void init() {
        // If log.dir not configured assume running from workstation/maven project config
        // This should be set on the command line invocation e.g. "-Dlog.dir=/opt/var/log" for deployed builds
        if (System.getProperty("log.dir","").equals("")) {
            System.setProperty("log.dir","target/");
        }

        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        LogManager.getLogManager().reset();
        Logger.getLogger("global").setLevel(Level.WARNING);

        InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory());
    }
}
