package io.github.mcengine.extension.addon.essential.fly.database.mysql;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.extension.addon.essential.fly.database.FlyDB;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * MySQL implementation of {@link FlyDB}.
 */
public class FlyDBMySQL implements FlyDB {

    /**
     * Shared SQL connection provided by MCEngine.
     */
    private final Connection conn;

    /**
     * Logger for DB diagnostics.
     */
    private final MCEngineExtensionLogger logger;

    public FlyDBMySQL(Connection conn, MCEngineExtensionLogger logger) {
        this.conn = conn;
        this.logger = logger;
    }

    @Override
    public void ensureSchema() {
        String sql = """
            CREATE TABLE IF NOT EXISTS fly (
              fly_id INT AUTO_INCREMENT PRIMARY KEY,
              player_uuid VARCHAR(36) NOT NULL UNIQUE,
              fly_duration INT NOT NULL DEFAULT 0
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (Exception e) {
            logger.warning("MySQL ensureSchema error: " + e.getMessage());
        }
    }

    @Override
    public void ensurePlayerRow(UUID uuid) {
        String sql = """
            INSERT INTO fly (player_uuid, fly_duration)
            VALUES (?, 0)
            ON DUPLICATE KEY UPDATE player_uuid = player_uuid;
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (Exception e) {
            logger.warning("MySQL ensurePlayerRow error: " + e.getMessage());
        }
    }

    @Override
    public int getDuration(UUID uuid) {
        String sql = "SELECT fly_duration FROM fly WHERE player_uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception e) {
            logger.warning("MySQL getDuration error: " + e.getMessage());
        }
        return 0;
    }

    @Override
    public void setDuration(UUID uuid, int seconds) {
        String sql = """
            INSERT INTO fly (player_uuid, fly_duration)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE fly_duration = VALUES(fly_duration);
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, seconds);
            ps.executeUpdate();
        } catch (Exception e) {
            logger.warning("MySQL setDuration error: " + e.getMessage());
        }
    }

    @Override
    public int decrementDuration(UUID uuid, int seconds) {
        String update = """
            UPDATE fly
               SET fly_duration = IF(fly_duration = 0, 0, GREATEST(fly_duration - ?, 0))
             WHERE player_uuid = ?;
            """;
        try (PreparedStatement ps = conn.prepareStatement(update)) {
            ps.setInt(1, seconds);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (Exception e) {
            logger.warning("MySQL decrementDuration update error: " + e.getMessage());
        }
        return getDuration(uuid);
    }
}
