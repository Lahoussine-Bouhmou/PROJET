package zetasocket;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.File;

public class AppConfigLoader {
    public final int httpPort;
    public final String tunnelHost;
    public final int tunnelPort;
    public final String logFile;  // chemin complet
    public final int debug;

    public AppConfigLoader(File cfgFile) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(cfgFile);
        doc.getDocumentElement().normalize();
        Element cfg = doc.getDocumentElement();

        this.httpPort   = optInt(cfg, "httpPort",   8080);
        this.tunnelHost = optStr(cfg, "tunnelHost", "tunnel-client");
        this.tunnelPort = optInt(cfg, "tunnelPort", 2200);

        // Soit <logFile>, soit <logDirectory> + <logFileName>
        String lf = optStr(cfg, "logFile", null);
        if (lf != null) {
            this.logFile = lf;
        } else {
            String dir  = optStr(cfg, "logDirectory", "/var/log/tunnel-logs");
            String name = optStr(cfg, "logFileName", "logs_app-client.log");
            this.logFile = (dir.endsWith("/") ? dir : (dir + "/")) + name;
        }

        this.debug = optInt(cfg, "debug", 4);
    }

    private static String optStr(Element cfg, String tag, String def) {
        NodeList nl = cfg.getElementsByTagName(tag);
        if (nl.getLength()==0) return def;
        String s = nl.item(0).getTextContent().trim();
        return s.isEmpty() ? def : s;
    }
    private static int optInt(Element cfg, String tag, int def) {
        String s = optStr(cfg, tag, null);
        if (s==null) return def;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }
}
