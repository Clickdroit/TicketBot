package fr.sakura.bot.core.util;

import fr.sakura.bot.database.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Utilitaire pour simplifier les opérations JDBC répétitives.
 * Fail-safe : vérifie si la base est prête avant toute opération.
 */
public class DbHelper {

    private static final Logger logger = LoggerFactory.getLogger(DbHelper.class);

    @FunctionalInterface
    public interface StatementBinder {
        void bind(PreparedStatement pstmt) throws SQLException;
    }

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    public static <T> Optional<T> queryOne(String sql, StatementBinder binder, RowMapper<T> mapper) {
        if (!DatabaseManager.isReady()) return Optional.empty();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            binder.bind(pstmt);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(mapper.map(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Database error in queryOne: {} - {}", sql, e.getMessage());
        }
        return Optional.empty();
    }

    public static <T> List<T> queryList(String sql, StatementBinder binder, RowMapper<T> mapper) {
        List<T> results = new ArrayList<>();
        if (!DatabaseManager.isReady()) return results;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            binder.bind(pstmt);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapper.map(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Database error in queryList: {} - {}", sql, e.getMessage());
        }
        return results;
    }

    public static int update(String sql, StatementBinder binder) {
        if (!DatabaseManager.isReady()) return 0;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            binder.bind(pstmt);
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Database error in update: {} - {}", sql, e.getMessage());
        }
        return 0;
    }
}
