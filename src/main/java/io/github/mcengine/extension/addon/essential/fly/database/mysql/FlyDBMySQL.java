package io.github.mcengine.extension.addon.essential.fly.database.mysql;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.common.essential.MCEngineEssentialCommon;
import io.github.mcengine.extension.addon.essential.fly.database.FlyDB;

import java.util.UUID;

/**
 * MySQL implementation of {@link FlyDB}.
 */
public class FlyDBMySQL implements FlyDB {

    /** Logger for DB diagnostics. */
    private final MCEngineExtensionLogger logger;

    /**
     * Constructs the DB helper.
     *
     * @param logger Logger instance for diagnostics.
     */
    public FlyDBMySQL(MCEngineExtensionLogger logger) {
        this.logger = logger;
    }

    /** Convenience: resolve Essential DB facade. */
    private static MCEngineEssentialCommon db() {
        return MCEngineEssentialCommon.getApi();
    }

    /** Quote/escape a value for inline SQL (used for simple statements). */
    private static String q(String s) {
        if (s == null) return "NULL";
        return "'" + s.replace("'", "''") + "'";
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
        try {
            db().executeQuery(sql);
        } catch (Exception e) {
            logger.warning("MySQL ensureSchema error: " + e.getMessage());
        }
    }

    @Override
    public void ensurePlayerRow(UUID uuid) {
        String sql = "INSERT INTO fly (player_uuid, fly_duration) VALUES (" +
            q(uuid.toString()) + ", 0) " +
            "ON DUPLICATE KEY UPDATE player_uuid = player_uuid";
        try {
            db().executeQuery(sql);
        } catch (Exception e) {
            logger.warning("MySQL ensurePlayerRow error: " + e.getMessage());
        }
    }

    @Override
    public int getDuration(UUID uuid) {
        String sql = "SELECT fly_duration FROM fly WHERE player_uuid = " + q(uuid.toString());
        try {
            Integer v = db().getValue(sql, Integer.class);
            return v != null ? v : 0;
        } catch (Exception e) {
            logger.warning("MySQL getDuration error: " + e.getMessage());
            return 0;
        }
    }

    @Override
    public void setDuration(UUID uuid, int seconds) {
        String sql = "INSERT INTO fly (player_uuid, fly_duration) VALUES (" +
            q(uuid.toString()) + ", " + seconds + ") " +
            "ON DUPLICATE KEY UPDATE fly_duration = VALUES(fly_duration)";
        try {
            db().executeQuery(sql);
        } catch (Exception e) {
            logger.warning("MySQL setDuration error: " + e.getMessage());
        }
    }

    @Override
    public int decrementDuration(UUID uuid, int seconds) {
        String update = "UPDATE fly " +
            "SET fly_duration = IF(fly_duration = 0, 0, GREATEST(fly_duration - " + seconds + ", 0)) " +
            "WHERE player_uuid = " + q(uuid.toString());
        try {
            db().executeQuery(update);
        } catch (Exception e) {
            logger.warning("MySQL decrementDuration update error: " + e.getMessage());
        }
        return getDuration(uuid);
    }
}
