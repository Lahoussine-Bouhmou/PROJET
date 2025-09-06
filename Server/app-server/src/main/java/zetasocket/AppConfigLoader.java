package zetasocket;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.File;

public class AppConfigLoader {
    public final int httpPort;       // port HTTP Jetty
    public final int mllpPort;       // port MLLP
    public final String storeFolder; // stockage de messages hl7 + .ok
    public final String endRecord;
    public final int watchTimer;
    public final String logFile;
    public final int debug;
    public final String hostBind;    // IP à binder (optionnel)
    public final String urlact;      // URL pingée périodiquement (optionnel)

    public AppConfigLoader(File cfgFile) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(cfgFile);
        doc.getDocumentElement().normalize();
        Element cfg = doc.getDocumentElement();

        this.httpPort = getConfigInt(cfg, "httpPort", 8080);
        this.mllpPort = getConfigInt(cfg, "mllpPort", 2202);
        this.storeFolder = getConfigStr(cfg, "storeFolder", "/var/messageStore");
        this.endRecord = getConfigStr(cfg, "endRecord", "$$$");
        this.watchTimer = getConfigInt(cfg, "watchTimer", 90);
        this.debug = getConfigInt(cfg, "debug", 0);
        this.hostBind = getConfigStr(cfg, "hostBind", null);
        this.urlact = getConfigStr(cfg, "urlact", null);
        this.logFile = getConfigStr(cfg, "logFile", null);
    }

    private static String getConfigStr(Element cfg, String tag, String defaultVal) {
        NodeList nl = cfg.getElementsByTagName(tag);
        if (nl.getLength()==0) return defaultVal;
        String s = nl.item(0).getTextContent().trim();
        return s.isEmpty() ? defaultVal : s;
    }
    private static int getConfigInt(Element cfg, String tag, int defaultVal) {
        String s = getConfigStr(cfg, tag, null);
        if (s==null) return defaultVal;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
