package ca.ucalgary.seng300.persistence;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Cross-instance online presence detection using heartbeat files.
 * Each logged-in user writes a file to data/online/{username}.presence
 * every few seconds. A user is considered online if their file exists
 * and was modified within the staleness threshold.
 */
public class PresenceService {

    private static final String PRESENCE_DIR = "data/online";
    private static final long HEARTBEAT_INTERVAL_MS = 4000;
    private static final long STALE_THRESHOLD_MS = 10000;

    private final File dir;
    private ScheduledExecutorService scheduler;
    private String activeUser;

    public PresenceService() {
        this.dir = new File(PRESENCE_DIR);
        dir.mkdirs();
        cleanStaleFiles();
        // Clean up presence file when the JVM exits (e.g. window closed without logout)
        Runtime.getRuntime().addShutdownHook(new Thread(this::goOffline, "presence-cleanup"));
    }

    /**
     * Delete all presence files at startup.
     * When a new JVM starts it has no active users, so every existing .presence file
     * is from a previously killed process. Any truly-live user will recreate their file
     * within one heartbeat interval (4 s).
     */
    private void cleanStaleFiles() {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".presence"));
        if (files == null) return;
        for (File f : files) {
            f.delete();
        }
    }

    /**
     * Start heartbeating for the given user.
     * Call on login.
     */
    public void goOnline(String username) {
        goOffline(); // stop any previous heartbeat
        this.activeUser = username;
        touchFile(username);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "presence-heartbeat");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(
                () -> touchFile(username),
                HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Stop heartbeating and remove the presence file.
     * Call on logout.
     */
    public void goOffline() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (activeUser != null) {
            File f = new File(dir, activeUser + ".presence");
            f.delete();
            activeUser = null;
        }
    }

    /**
     * Stop heartbeating for a specific user — no-op if a different user is currently active.
     * Safe to call on WebSocket disconnect without risking stopping another player's heartbeat.
     */
    public void goOffline(String username) {
        if (username != null && username.equals(activeUser)) {
            goOffline();
        }
    }

    /**
     * Check whether a user is online across any instance.
     * A user is online if their heartbeat file exists and was
     * updated within the staleness threshold.
     */
    public boolean isOnline(String username) {
        File f = new File(dir, username + ".presence");
        if (!f.exists()) return false;
        long age = System.currentTimeMillis() - f.lastModified();
        return age < STALE_THRESHOLD_MS;
    }

    private void touchFile(String username) {
        try {
            File f = new File(dir, username + ".presence");
            if (!f.exists()) {
                f.createNewFile();
            }
            f.setLastModified(System.currentTimeMillis());
        } catch (IOException e) {
            System.err.println("[PresenceService] Failed to touch heartbeat file: " + e.getMessage());
        }
    }
}
