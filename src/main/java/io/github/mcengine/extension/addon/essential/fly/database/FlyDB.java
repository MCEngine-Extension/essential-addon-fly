package io.github.mcengine.extension.addon.essential.fly.database;

import java.util.UUID;

/**
 * Database interface for the Fly AddOn.
 * <p>
 * Convention: {@code fly_duration = 0} means unlimited flight time.
 */
public interface FlyDB {

    /**
     * Ensure the {@code fly} table exists.
     */
    void ensureSchema();

    /**
     * Ensure a player row exists; create with duration 0 if missing.
     */
    void ensurePlayerRow(UUID uuid);

    /**
     * Get remaining duration (seconds) for a player. 0 = unlimited.
     */
    int getDuration(UUID uuid);

    /**
     * Set remaining duration (seconds) for a player.
     */
    void setDuration(UUID uuid, int seconds);

    /**
     * Atomically decrement duration by {@code seconds}, respecting 0 = unlimited and floor at 0.
     *
     * @return remaining seconds after decrement (0 if expired or unlimited).
     */
    int decrementDuration(UUID uuid, int seconds);
}
