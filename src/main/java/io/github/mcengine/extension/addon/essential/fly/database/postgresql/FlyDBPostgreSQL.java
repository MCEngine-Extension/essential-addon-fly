package io.github.mcengine.extension.addon.essential.fly.database.postgresql;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.common.essential.MCEngineEssentialCommon;
import io.github.mcengine.extension.addon.essential.fly.database.FlyDB;

import java.util.UUID;

/**
 * PostgreSQL implementation of {@link FlyDB}.
 */
public class FlyDBPostgreSQL implements FlyDB {

    /** Logger for DB diagnostics. */
    private final MCEngineExtensionLogger logger;

    /**
     * Constructs the DB helper.
     *
     * @param logger Logger instance for diagnostics.
     */
    public FlyDBPostgreSQL(MCEngineExtensionLogger logger) {
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
              fly_id SERIAL PRIMARY KEY,
              player_uuid VARCHAR(36) NOT NULL UNIQUE,
              fly_duration INT NOT NULL DEFAULT 0
            );
            """;
        try {
            db().executeQuery(sql);
        } catch (Exception e) {
            logger.warning("PostgreSQL ensureSchema error: " + e.getMessage());
        }
    }

    @Override
    public void ensurePlayerRow(UUID uuid) {
        String sql = "INSERT INTO fly (player_uuid, fly_duration) VALUES (" +
            q(uuid.toString()) + ", 0) ON CONFLICT (player_uuid) DO NOTHING";
        try {
            db().executeQuery(sql);
        } catch (Exception e) {
            logger.warning("PostgreSQL ensurePlayerRow error: " + e.getMessage());
        }
    }

    @Override
    public int getDuration(UUID uuid) {
        String sql = "SELECT fly_duration FROM fly WHERE player_uuid = " + q(uuid.toString());
        try {
            Integer v = db().getValue(sql, Integer.class);
            return v != null ? v : 0;
        } catch (Exception e) {
            logger.warning("PostgreSQL getDuration error: " + e.getMessage());
            return 0;
        }
    }

    @Override
    public void setDuration(UUID uuid, int seconds) {
        String sql = "INSERT INTO fly (player_uuid, fly_duration) VALUES (" +
            q(uuid.toString()) + ", " + seconds + ") " +
            "ON CONFLICT (player_uuid) DO UPDATE SET fly_duration = EXCLUDED.fly_duration";
        try {
            db().executeQuery(sql);
        } catch (Exception e) {
            logger.warning("PostgreSQL setDuration error: " + e.getMessage());
        }
    }

    @Override
    public int decrementDuration(UUID uuid, int seconds) {
        String update = "UPDATE fly SET fly_duration = CASE " +
            "WHEN fly_duration = 0 THEN 0 " +
            "ELSE GREATEST(fly_duration - " + seconds + ", 0) END " +
            "WHERE player_uuid = " + q(uuid.toString());
        try {
            db().executeQuery(update);
        } catch (Exception e) {
            logger.warning("PostgreSQL decrementDuration update error: " + e.getMessage());
        }
        return getDuration(uuid);
    }
}
