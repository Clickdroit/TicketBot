package fr.sakura.bot.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * Factory pour centraliser la création et la configuration du pool de connexions HikariCP.
 */
public class HikariPoolFactory {
    private static final Logger logger = LoggerFactory.getLogger(HikariPoolFactory.class);

    public static HikariDataSource createPool(String dbUrl, boolean isPostgres) {
        HikariConfig config = new HikariConfig();
        
        if (isPostgres) {
            logger.info("Utilisation du driver PostgreSQL");
            config.setDriverClassName("org.postgresql.Driver");
            setupPostgresConfig(config, dbUrl);
            
            // Robustesse spécifique pour PostgreSQL/Supabase
            config.addDataSourceProperty("tcpKeepAlive", "true");
            config.addDataSourceProperty("assumeMinServerVersion", "14");
            config.addDataSourceProperty("reWriteBatchedInserts", "true");
        } else {
            logger.info("Utilisation du driver SQLite");
            config.setDriverClassName("org.sqlite.JDBC");
            config.setJdbcUrl(dbUrl);
        }

        config.setPoolName("TicketBotPool");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(300000);
        config.setConnectionTimeout(10000);
        
        // CRITIQUE : Empêche le bot de crash si la DB est offline au boot.
        config.setInitializationFailTimeout(0);

        logger.info("Configuration du pool Hikari terminée (dialect={})", isPostgres ? "PostgreSQL" : "SQLite");
        return new HikariDataSource(config);
    }

    private static void setupPostgresConfig(HikariConfig config, String rawUrl) {
        // Si l'URL contient déjà user et password en paramètres JDBC, on l'utilise brute
        if (rawUrl.contains("user=") && (rawUrl.contains("password=") || rawUrl.contains("pass="))) {
            config.setJdbcUrl(rawUrl.startsWith("jdbc:") ? rawUrl : "jdbc:" + rawUrl);
            return;
        }

        try {
            // Tentative de parsing pour les formats postgres://user:pass@host
            String candidate = rawUrl;
            if (candidate.startsWith("jdbc:postgresql://")) {
                candidate = candidate.substring(5);
            } else if (!candidate.contains("://")) {
                candidate = "postgresql://" + candidate;
            }
            
            URI uri = URI.create(candidate);
            String userInfo = uri.getUserInfo();
            
            if (userInfo != null && userInfo.contains(":")) {
                String[] parts = userInfo.split(":", 2);
                config.setUsername(parts[0]);
                config.setPassword(parts[1]);
                
                String cleanUrl = "jdbc:postgresql://" + uri.getHost() + 
                                  (uri.getPort() > 0 ? ":" + uri.getPort() : "") + 
                                  uri.getPath();
                if (uri.getQuery() != null) cleanUrl += "?" + uri.getQuery();
                config.setJdbcUrl(cleanUrl);
            } else {
                config.setJdbcUrl(rawUrl.startsWith("jdbc:") ? rawUrl : "jdbc:" + rawUrl);
            }
        } catch (Exception e) {
            config.setJdbcUrl(rawUrl.startsWith("jdbc:") ? rawUrl : "jdbc:" + rawUrl);
        }
    }
}
