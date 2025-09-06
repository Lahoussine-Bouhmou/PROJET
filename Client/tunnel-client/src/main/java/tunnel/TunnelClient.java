package tunnel;

import javax.net.ssl.*;
import java.io.*;
import java.net.ServerSocket;
import javax.net.ServerSocketFactory;
import java.net.Socket;
import java.security.KeyStore;
import java.util.logging.*;

public class TunnelClient {
    private static final Logger logger = Logger.getLogger(TunnelClient.class.getName());

    public static void main(String[] args) {
        // 1) Chargement de la config
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

        // 3) Préparation du SSLContext
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

        // 4) Démarrage du serveur local non chiffré
        try (ServerSocket server = ServerSocketFactory
                .getDefault()
                .createServerSocket(cfg.localPort)) {
            logger.info("TunnelClient écoute sur le port " + cfg.localPort);
            while (true) {
                Socket plain = server.accept();
                logger.info("Client connecté depuis " + plain.getRemoteSocketAddress());
                new Thread(() -> handleConnection(plain, sslCtx, cfg.remoteHost, cfg.remotePort),
                        "client-handler-" + plain.getPort())
                        .start();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Erreur réseau dans TunnelClient", e);
        }
    }

    private static void handleConnection(Socket plain, SSLContext sslCtx,
                                         String remoteHost, int remotePort) {
        SSLSocket ssl = null;
        try {
            // 1) Création de la socket SSL + handshake
            ssl = (SSLSocket) sslCtx.getSocketFactory()
                    .createSocket(remoteHost, remotePort);
            ssl.setUseClientMode(true);
            ssl.startHandshake();
            logger.info("Handshake TLS OK avec " + remoteHost + ":" + remotePort);

            // 2) Timeouts
            plain.setSoTimeout(30_000);
            ssl.setSoTimeout(30_000);

            // 3) Lancement des relais
            Thread tPlainToSsl = new Thread(
                    relay(plain.getInputStream(), ssl.getOutputStream()),
                    "relay-plain-to-ssl-" + plain.getPort()
            );
            Thread tSslToPlain = new Thread(
                    relay(ssl.getInputStream(), plain.getOutputStream()),
                    "relay-ssl-to-plain-" + plain.getPort()
            );
            tPlainToSsl.start();
            tSslToPlain.start();

            // 4) Attente et FIN TLS
            tPlainToSsl.join();

            if (!ssl.isClosed() && ssl.isConnected()) {
                try {
                    ssl.shutdownOutput();
                } catch (IOException e) {
                    logger.log(Level.WARNING,
                            "Impossible d'envoyer FIN TLS (socket peut-être déjà fermée)", e);
                }
            }

            tSslToPlain.join();
            logger.info("Tunnel terminé pour " + plain.getRemoteSocketAddress());

        } catch (SSLException e) {
            logger.log(Level.SEVERE, "Erreur SSL dans handleConnection", e);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Erreur I/O dans handleConnection", e);
        } catch (InterruptedException e) {
            logger.log(Level.SEVERE, "Thread interrompu dans handleConnection", e);
            Thread.currentThread().interrupt();
        } finally {
            closeAll(ssl, plain);
        }
    }

    /** Copie les données de in→out dans un Runnable */
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

    /** Ferme proprement plusieurs ressources */
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
     * Construit un SSLContext client à partir de :
     *      - keystoreFile/keystorePassword (optionnel, pour mTLS)
     *      - truststoreFile/truststorePassword (obligatoire)
     */
    private static SSLContext createSSLContext(String cfgDir,
                                               String ksFile, String ksPwd,
                                               String tsFile, String tsPwd) throws Exception {
        // KeyStore (optionnel si présent)
        KeyManager[] kms = null;
        if (ksFile != null) {
            KeyStore ks = KeyStore.getInstance("JKS");
            try (InputStream is = new FileInputStream(new File(cfgDir, ksFile))) {
                ks.load(is, ksPwd.toCharArray());
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, ksPwd.toCharArray());
            kms = kmf.getKeyManagers();
        }

        // TrustStore (obligatoire)
        KeyStore ts = KeyStore.getInstance("JKS");
        try (InputStream is = new FileInputStream(new File(cfgDir, tsFile))) {
            ts.load(is, tsPwd.toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ts);
        TrustManager[] tms = tmf.getTrustManagers();

        SSLContext ctx = SSLContext.getInstance("TLSv1.2");
        ctx.init(kms, tms, null);
        return ctx;
    }
}
