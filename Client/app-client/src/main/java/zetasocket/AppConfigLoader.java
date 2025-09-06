package zetasocket;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.File;

public class AppConfigLoader {
    public final int httpPort;
    public final String tunnelHost;
    public final int tunnelPort;
    public final String logFile;
    public final int debug;

    public AppConfigLoader(File cfgFile) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(cfgFile);
        doc.getDocumentElement().normalize();
        Element cfg = doc.getDocumentElement();

        this.httpPort = getConfigInt(cfg, "httpPort", 8080);
        this.tunnelHost = getConfigStr(cfg, "tunnelHost", "tunnel-client");
        this.tunnelPort = getConfigInt(cfg, "tunnelPort", 2200);

        this.logFile = getConfigStr(cfg, "logFile", null);
        this.debug = getConfigInt(cfg, "debug", 4);
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
