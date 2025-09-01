package zetasocket;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.io.File;

public class Launcher {
    public static void main(String[] args) throws Exception {
        String cfgDir = System.getProperty("configDir", "/appConfig");
        AppConfigLoader cfg = new AppConfigLoader(new File(cfgDir, "parameters.xml"));

        // Jetty HTTP
        Server server = new Server(cfg.httpPort);

        // Contexte
        ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
        ctx.setContextPath("/app-server");

        // zetaSockHl7 + init-params depuis le XML
        ServletHolder srv = new ServletHolder(new zetaSockHl7());
        srv.setInitOrder(1);
        srv.setInitParameter("debug",    String.valueOf(cfg.debug));
        srv.setInitParameter("port",     String.valueOf(cfg.mllpPort));
        srv.setInitParameter("folder",   cfg.storeFolder);
        srv.setInitParameter("endrecord",cfg.endRecord);
        srv.setInitParameter("timer",    String.valueOf(cfg.watchTimer));
        srv.setInitParameter("logfile",  cfg.logFile);
        if (cfg.urlact   != null) srv.setInitParameter("urlact", cfg.urlact);
        if (cfg.hostBind != null) srv.setInitParameter("host",   cfg.hostBind);
        ctx.addServlet(srv, "/servlet/zetaSockHl7");

        // Health endpoint
        ServletHolder health = new ServletHolder(new HealthServlet());
        health.setInitOrder(2);
        ctx.addServlet(health, "/health");

        server.setHandler(ctx);
        server.start();
        server.join();
    }
}
