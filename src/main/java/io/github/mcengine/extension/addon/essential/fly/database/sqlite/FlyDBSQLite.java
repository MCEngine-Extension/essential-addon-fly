package io.github.mcengine.extension.addon.essential.fly.database.sqlite;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.extension.addon.essential.fly.database.FlyDB;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * SQLite implementation of {@link FlyDB}.
 */
public class FlyDBSQLite implements FlyDB {

    /**
     * Shared SQL connection provided by MCEngine.
     */
    private final Connection conn;

    /**
     * Logger for DB diagnostics.
     */
    private final MCEngineExtensionLogger logger;

    public FlyDBSQLite(Connection conn, MCEngineExtensionLogger logger) {
        this.conn = conn;
        this.logger = logger;
    }

    @Override
    public void ensureSchema() {
        String sql = """
            CREATE TABLE IF NOT EXISTS fly (
              fly_id INTEGER PRIMARY KEY AUTOINCREMENT,
              player_uuid TEXT NOT NULL UNIQUE,
              fly_duration INTEGER NOT NULL DEFAULT 0
            );
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (Exception e) {
            logger.warning("SQLite ensureSchema error: " + e.getMessage());
        }
    }

    @Override
    public void ensurePlayerRow(UUID uuid) {
        String sql = """
            INSERT INTO fly (player_uuid, fly_duration)
            VALUES (?, 0)
            ON CONFLICT(player_uuid) DO NOTHING;
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (Exception e) {
            logger.warning("SQLite ensurePlayerRow error: " + e.getMessage());
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
            logger.warning("SQLite getDuration error: " + e.getMessage());
        }
        return 0;
    }

    @Override
    public void setDuration(UUID uuid, int seconds) {
        String sql = """
            INSERT INTO fly (player_uuid, fly_duration)
            VALUES (?, ?)
            ON CONFLICT(player_uuid) DO UPDATE SET fly_duration = excluded.fly_duration;
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, seconds);
            ps.executeUpdate();
        } catch (Exception e) {
            logger.warning("SQLite setDuration error: " + e.getMessage());
        }
    }

    @Override
    public int decrementDuration(UUID uuid, int seconds) {
        // 0 = unlimited ⇒ stay 0; else max(fly_duration - seconds, 0)
        String update = """
            UPDATE fly
               SET fly_duration = CASE
                                  WHEN fly_duration = 0 THEN 0
                                  WHEN fly_duration - ? < 0 THEN 0
                                  ELSE fly_duration - ?
                                  END
             WHERE player_uuid = ?;
            """;
        try (PreparedStatement ps = conn.prepareStatement(update)) {
            ps.setInt(1, seconds);
            ps.setInt(2, seconds);
            ps.setString(3, uuid.toString());
            ps.executeUpdate();
        } catch (Exception e) {
            logger.warning("SQLite decrementDuration update error: " + e.getMessage());
        }
        return getDuration(uuid);
    }
}
