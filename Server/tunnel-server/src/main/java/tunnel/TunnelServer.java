// src/main/java/tunnel/TunnelServer.java
package tunnel;

import javax.net.ssl.*;
import java.io.*;
import java.net.Socket;
import java.security.KeyStore;
import java.util.logging.*;

public class TunnelServer {
    private static final Logger logger = Logger.getLogger(TunnelServer.class.getName());

    public static void main(String[] args) {
        // 1) Chargement de la config XML
        String cfgDir = "/tunnelConfig";
        File cfgFile = new File(System.getProperty("configDir", cfgDir),
                "parameters.xml");
        ConfigLoader cfg;
        try {
            cfg = new ConfigLoader(cfgFile);
        } catch (Exception e) {
            System.err.println("[CONFIG ERROR] " + e.getMessage());
            System.exit(1);
            return;
        }

        // 2) Configuration du logger
        try {
            File dir = new File(cfg.logDirectory);
            if (!dir.exists()) dir.mkdirs();
            FileHandler fh = new FileHandler(
                    new File(dir, cfg.logFileName).getAbsolutePath(), true);
            fh.setFormatter(new SimpleFormatter());
            logger.addHandler(fh);
            logger.setLevel(cfg.logLevel);
        } catch (IOException e) {
            System.err.println("[LOGGER ERROR] Impossible de configurer le logger: " + e.getMessage());
            System.exit(2);
        }

        // 3) Préparation du contexte SSL
        SSLContext sslCtx;
        try {
            sslCtx = createSSLContext(
                    cfgDir,
                    cfg.keystoreFile, cfg.keystorePassword,
                    cfg.truststoreFile, cfg.truststorePassword
            );
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erreur lors de l'initialisation du SSLContext", e);
            return;
        }

        // 4) Démarrage du serveur SSL
        try (SSLServerSocket server =
                     (SSLServerSocket) sslCtx.getServerSocketFactory()
                             .createServerSocket(cfg.localPort)) {
            logger.info("TunnelServer SSL écoute sur port " + cfg.localPort);
            while (true) {
                SSLSocket sslClient = (SSLSocket) server.accept();
                new Thread(() -> handleConnection(sslClient, cfg.remoteHost, cfg.remotePort),
                        "server-handler-" + sslClient.getPort())
                        .start();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Erreur réseau dans le serveur SSL", e);
        }
    }

    private static void handleConnection(SSLSocket ssl, String remoteHost, int remotePort) {
        Socket plain = null;
        try {
            // 1) Handshake TLS explicite
            ssl.setUseClientMode(false);
            ssl.startHandshake();
            logger.info("Handshake TLS OK avec " + ssl.getRemoteSocketAddress());

            // 2) Timeouts
            ssl.setSoTimeout(30_000);

            // 3) Connexion au serveur final en clair
            plain = new Socket(remoteHost, remotePort);
            plain.setSoTimeout(30_000);
            logger.info("Connexion plain établie vers " + remoteHost + ":" + remotePort);

            // 4) Lancement des relais avec noms de thread
            Thread tSslToPlain = new Thread(relay(ssl.getInputStream(), plain.getOutputStream()),
                    "relay-ssl-to-plain-" + ssl.getPort());
            Thread tPlainToSsl = new Thread(relay(plain.getInputStream(), ssl.getOutputStream()),
                    "relay-plain-to-ssl-" + ssl.getPort());
            tSslToPlain.start();
            tPlainToSsl.start();

            // 5) Attente de la fin du flux TLS → plain
            tSslToPlain.join();

            // 6) Demi-fermeture plain → serveur final (FIN)
            if (!plain.isClosed() && plain.isConnected()) {
                try {
                    plain.shutdownOutput();
                } catch (IOException e) {
                    logger.log(Level.WARNING,
                            "Impossible d'envoyer FIN plain (socket peut-être déjà fermée)", e);
                }
            }

            // 7) Attente de la fin du flux plain → TLS
            tPlainToSsl.join();
            logger.info("Tunnel terminé pour client SSL " + ssl.getRemoteSocketAddress());

        } catch (SSLException e) {
            logger.log(Level.SEVERE, "Erreur TLS dans handleConnection", e);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Erreur I/O dans handleConnection", e);
        } catch (InterruptedException e) {
            logger.log(Level.SEVERE, "Thread interrompu dans handleConnection", e);
            Thread.currentThread().interrupt();
        } finally {
            closeAll(plain, ssl);
        }
    }

    /** Copie les données de in→out dans un Thread séparé */
    private static Runnable relay(InputStream in, OutputStream out) {
        return () -> {
            byte[] buf = new byte[4096];
            int len;
            try {
                while ((len = in.read(buf)) != -1) {
                    out.write(buf, 0, len);
                    out.flush();
                }
            } catch (IOException ignored) {
                // Arrêt normal lors de la fermeture
            }
        };
    }

    /** Ferme proprement plusieurs Closeable en loggant les erreurs éventuelles */
    private static void closeAll(Closeable... resources) {
        for (Closeable c : resources) {
            if (c != null) {
                try { c.close(); }
                catch (IOException e) {
                    logger.log(Level.WARNING, "Erreur en fermant la ressource", e);
                }
            }
        }
    }

    /**
     * Construit un SSLContext serveur à partir de :
     * - keystoreFile/keystorePassword (optionnel, pour authentification mutuelle)
     * - truststoreFile/truststorePassword (obligatoire, pour vérifier le serveur)
     */
    private static SSLContext createSSLContext(String cfgDir,
                                               String ksFile, String ksPwd,
                                               String tsFile, String tsPwd) throws Exception {
        // KeyStore (obligatoire)
        KeyStore ks = KeyStore.getInstance("JKS");
        try (InputStream isk = new FileInputStream(new File(cfgDir, ksFile))) {
            ks.load(isk, ksPwd.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, ksPwd.toCharArray());
        KeyManager[] kms = kmf.getKeyManagers();

        // TrustStore (optionnel si présent)
        TrustManager[] tms = null;
        if (tsFile != null) {
            KeyStore ts = KeyStore.getInstance("JKS");
            try (InputStream ist = new FileInputStream(new File(cfgDir, tsFile))) {
                ts.load(ist, tsPwd.toCharArray());
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(ts);
            tms = tmf.getTrustManagers();
        }

        SSLContext ctx = SSLContext.getInstance("TLSv1.2");
        ctx.init(kms, tms, null);
        return ctx;
    }
}
