package fr.sakura.bot.database;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Gestionnaire central de la base de données.
 * Responsable uniquement du cycle de vie du pool et de l'accès aux connexions.
 */
public class DatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    
    private static final String DB_FOLDER = "data";
    private static final String DB_FILE = "sakura.db";
    private static final String DEFAULT_DB_URL = "jdbc:sqlite:" + DB_FOLDER + "/" + DB_FILE;
    
    private static HikariDataSource dataSource;
    private static boolean isPostgres = false;
    private static volatile boolean isReady = false;
    
    private static ScheduledExecutorService healthChecker;

    public static boolean isPostgres() {
        return isPostgres;
    }

    public static boolean isSqlite() {
        return !isPostgres;
    }

    public static boolean isReady() {
        return isReady;
    }

    public static void initialize() {
        initialize(null);
    }

    public static void initialize(String configuredDbUrl) {
        shutdown(); // Nettoyage au cas où
        
        isReady = false;
        isPostgres = false;
        
        healthChecker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "db-health-checker");
            t.setDaemon(true);
            return t;
        });

        String dbUrl = configuredDbUrl;
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = DEFAULT_DB_URL;
            logger.info("DATABASE_URL absente: utilisation de SQLite par défaut ({})", dbUrl);
        } else {
            String maskedUrl = dbUrl.replaceAll(":([^:@/]+)@", ":****@");
            logger.info("Initialisation de la base de données avec l'URL: {}", maskedUrl);
        }

        isPostgres = dbUrl.startsWith("jdbc:postgresql:") || dbUrl.startsWith("postgresql:") || dbUrl.startsWith("postgres:");

        if (isSqlite()) {
            ensureDbFolderExists();
        }

        // Création du pool
        dataSource = HikariPoolFactory.createPool(dbUrl, isPostgres);

        // Initialisation synchrone du premier essai pour éviter les race conditions au boot
        logger.info("Tentative de connexion initiale à la base de données...");
        checkConnectionAndInit();

        if (!isReady) {
            logger.warn("La base de données n'est pas prête au démarrage. Le bot fonctionnera en mode dégradé jusqu'à la connexion.");
        }

        // Lancement du checker de santé périodique
        healthChecker.scheduleAtFixedRate(DatabaseManager::checkConnectionAndInit, 1, 1, TimeUnit.MINUTES);
    }

    private static synchronized void checkConnectionAndInit() {
        if (isReady && dataSource != null && !dataSource.isClosed()) {
            // Optionnel : on pourrait tester la connection ici aussi
            return;
        }

        try (Connection conn = dataSource.getConnection()) {
            SchemaInitializer.initializeSchema(conn, isPostgres);
            isReady = true;
            logger.info("Base de données connectée et initialisée avec succès ! (Dialect={})", isPostgres ? "PostgreSQL" : "SQLite");
        } catch (SQLException e) {
            logger.error("Échec de connexion/initialisation de la base de données: {}", e.getMessage());
            isReady = false;
        }
    }

    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("La source de données n'est pas initialisée");
        }
        return dataSource.getConnection();
    }

    public static void shutdown() {
        if (healthChecker != null) {
            healthChecker.shutdown();
        }
        if (dataSource != null) {
            dataSource.close();
            logger.info("Pool de connexions fermé");
            dataSource = null;
        }
    }

    private static void ensureDbFolderExists() {
        File folder = new File(DB_FOLDER);
        if (!folder.exists()) {
            if (folder.mkdirs()) {
                logger.info("Dossier '{}' créé pour la base de données SQLite", DB_FOLDER);
            }
        }
    }
}
