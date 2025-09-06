package zetasocket;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;

import java.io.File;

public class Launcher {
    public static void main(String[] args) throws Exception {
        String cfgDir = System.getProperty("configDir", "/appConfig");
        AppConfigLoader cfg = new AppConfigLoader(new File(cfgDir, "parameters.xml"));

        Server server = new Server(cfg.httpPort);

        ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
        ctx.setContextPath("/app-client");
        ctx.setWelcomeFiles(new String[]{"index.html"});

        Resource base = Resource.newClassPathResource("/webroot");
        if (base == null) throw new IllegalStateException("Missing /webroot in classpath");
        ctx.setBaseResource(base);
        ctx.addServlet(new ServletHolder("default", new DefaultServlet()), "/");

        // Injecter les init-params dans le servlet socketSenderSt depuis le XML
        ServletHolder sender = new ServletHolder(new socketSenderSt());
        sender.setInitParameter("debug", String.valueOf(cfg.debug));
        sender.setInitParameter("host", cfg.tunnelHost);
        sender.setInitParameter("port", String.valueOf(cfg.tunnelPort));
        sender.setInitParameter("logfile", cfg.logFile);
        ctx.addServlet(sender, "/servlet/socketSender");

        server.setHandler(ctx);
        server.start();
        server.join();
    }
}
