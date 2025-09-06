package tunnel;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.File;
import java.util.logging.Level;

public class ConfigLoader {
    public final int localPort;
    public final String remoteHost;
    public final int remotePort;

    public final String keystoreFile;       // optionnel
    public final String keystorePassword;   // optionnel

    public final String truststoreFile;
    public final String truststorePassword;

    public final String logFile;
    public final Level logLevel;

    public ConfigLoader(File cfgFile) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(cfgFile);
        doc.getDocumentElement().normalize();
        Element cfg = doc.getDocumentElement();

        // --Paramètres obligatoires
        this.localPort = getRequiredInt(cfg, "localPort");
        this.remoteHost = getRequiredString(cfg, "remoteHost");
        this.remotePort = getRequiredInt(cfg, "remotePort");
        this.truststoreFile = getRequiredString(cfg, "truststoreFile");
        this.truststorePassword = getRequiredString(cfg, "truststorePassword");

        // --Paramètres optionnels (mutual TLS)
        this.keystoreFile = getOptionalString(cfg, "keystoreFile", null);
        this.keystorePassword = (keystoreFile != null)
                ? getRequiredString(cfg, "keystorePassword")
                : null;

        // Logging (avec valeurs par défaut)
        this.logFile = getOptionalString(cfg, "logFile", "/var/log/tunnel-logs/logs_tunnel-client.log");
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
