// src/main/java/tunnel/ConfigLoader.java
package tunnel;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.File;
import java.util.logging.Level;

public class ConfigLoader {
    public final int localPort;
    public final String remoteHost;
    public final int remotePort;

    public final String keystoreFile;
    public final String keystorePassword;

    public final String truststoreFile;      // optionnel
    public final String truststorePassword;  // optionnel

    public final String logDirectory;
    public final String logFileName;
    public final Level logLevel;

    public ConfigLoader(File cfgFile) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(cfgFile);
        doc.getDocumentElement().normalize();
        Element cfg = doc.getDocumentElement();

        // --Paramètres obligatoires
        this.localPort        = getRequiredInt(cfg, "localPort");
        this.remoteHost       = getRequiredString(cfg, "remoteHost");
        this.remotePort       = getRequiredInt(cfg, "remotePort");
        this.keystoreFile     = getRequiredString(cfg, "keystoreFile");
        this.keystorePassword = getRequiredString(cfg, "keystorePassword");

        // --Paramètres optionnels (mutual TLS)
        this.truststoreFile = getOptionalString(cfg, "truststoreFile", null);
        this.truststorePassword = (this.truststoreFile != null)
                ? getRequiredString(cfg, "truststorePassword")
                : null;

        // Logging (avec valeurs par défaut)
        this.logDirectory = getOptionalString(cfg, "logDirectory", "/var/log/tunnel-logs");
        this.logFileName  = getOptionalString(cfg, "logFileName", "logs_tunnel-server.log");
        String lvl = getOptionalString(cfg, "logLevel", "INFO");
        this.logLevel = Level.parse(lvl);
    }

    private String getRequiredString(Element cfg, String tag) {
        NodeList nl = cfg.getElementsByTagName(tag);
        if (nl.getLength() == 0 || nl.item(0).getTextContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Paramètre XML manquant ou vide : <" + tag + ">");
        }
        return nl.item(0).getTextContent().trim();
    }

    private int getRequiredInt(Element cfg, String tag) {
        String s = getRequiredString(cfg, tag);
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Valeur invalide pour <" + tag + ">: '" + s + "' n'est pas un entier", e);
        }
    }

    private String getOptionalString(Element cfg, String tag, String defaultVal) {
        NodeList nl = cfg.getElementsByTagName(tag);
        if (nl.getLength() == 0) return defaultVal;
        String s = nl.item(0).getTextContent().trim();
        return s.isEmpty() ? defaultVal : s;
    }
}