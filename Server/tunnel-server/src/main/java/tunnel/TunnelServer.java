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
        String cfgDir = System.getProperty("configDir", "/tunnelConfig");
        File cfgFile = new File(cfgDir, "parameters.xml");

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
            File logFile = new File(cfg.logFile);
            File parentDir = logFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            FileHandler fh = new FileHandler(logFile.getAbsolutePath(), true);
            fh.setFormatter(new SimpleFormatter());
            logger.addHandler(fh);
            logger.setLevel(cfg.logLevel);
        } catch (IOException e) {
            System.err.println("[LOGGER ERROR] Impossible de configurer le logger : " + e.getMessage());
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
            // Handshake TLS
            ssl.setUseClientMode(false);
            ssl.startHandshake();
            logger.info("Handshake TLS OK avec " + ssl.getRemoteSocketAddress());

            ssl.setSoTimeout(30_000);

            // Connexion avec app-server
            plain = new Socket(remoteHost, remotePort);
            plain.setSoTimeout(30_000);
            logger.info("Connexion plain établie vers " + remoteHost + ":" + remotePort);

            // Lancement des relais
            Thread tSslToPlain = new Thread(relay(ssl.getInputStream(), plain.getOutputStream()),
                    "relay-ssl-to-plain-" + ssl.getPort());
            Thread tPlainToSsl = new Thread(relay(plain.getInputStream(), ssl.getOutputStream()),
                    "relay-plain-to-ssl-" + ssl.getPort());
            tSslToPlain.start();
            tPlainToSsl.start();

            // Attente (avec d'abord demi-fermeture) et FIN TLS
            tSslToPlain.join();

            if (!plain.isClosed() && plain.isConnected()) {
                try {
                    plain.shutdownOutput();
                } catch (IOException e) {
                    logger.log(Level.WARNING,
                            "Impossible d'envoyer FIN plain (socket peut-être déjà fermée)", e);
                }
            }

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
                // fermeture normale
            }
        };
    }

    /** Ferme proprement plusieurs Closeable */
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
     *      - keystoreFile/keystorePassword (optionnel, pour mTLS)
     *      - truststoreFile/truststorePassword (obligatoire)
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
