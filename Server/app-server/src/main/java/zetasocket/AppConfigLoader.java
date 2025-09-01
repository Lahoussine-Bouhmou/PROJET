package zetasocket;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.File;

public class AppConfigLoader {
    public final int httpPort;       // port HTTP Jetty
    public final int mllpPort;       // port MLLP écouté par socketSrv
    public final String storeFolder; // dossier de dépôt des messages & .ok
    public final String endRecord;   // segment de fin (ex: $$$)
    public final int watchTimer;     // secondes pour Watcher (ex: 90)
    public final String logFile;     // chemin complet du log app-server
    public final int debug;          // niveau debug
    public final String hostBind;    // IP à binder (optionnel)
    public final String urlact;      // URL pingée périodiquement (optionnel)

    public AppConfigLoader(File cfgFile) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(cfgFile);
        doc.getDocumentElement().normalize();
        Element cfg = doc.getDocumentElement();

        this.httpPort   = optInt(cfg, "httpPort", 8080);
        this.mllpPort   = optInt(cfg, "mllpPort", 2202);
        this.storeFolder= optStr(cfg, "storeFolder", "/var/messageStore");
        this.endRecord  = optStr(cfg, "endRecord", "$$$");
        this.watchTimer = optInt(cfg, "watchTimer", 90);
        this.debug      = optInt(cfg, "debug", 0);
        this.hostBind   = optStr(cfg, "hostBind", null);
        this.urlact     = optStr(cfg, "urlact", null);
        this.logFile    = optStr(cfg, "logFile", null);
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
