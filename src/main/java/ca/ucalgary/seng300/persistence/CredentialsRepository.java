package ca.ucalgary.seng300.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * JSON-backed repository for credential storage.
 * Persists a username -> passwordHash map to data/credentials.json.
 * Acts as the durable store behind the in-memory Database stub.
 */
public class CredentialsRepository {

    private static final String FILE_PATH = "data/credentials.json";

    private final ObjectMapper mapper;
    private final File file;
    private final Map<String, String> store;

    public CredentialsRepository() {
        this(FILE_PATH);
    }

    CredentialsRepository(String filePath) {
        this.mapper = new ObjectMapper();
        this.file = new File(filePath);
        this.store = new HashMap<>();
        loadFromDisk();
    }

    /**
     * Persist credentials for a user and flush to disk.
     *
     * @param username     the player's username
     * @param passwordHash SHA-256 hash of the player's password
     */
    public void save(String username, String passwordHash) {
        store.put(username, passwordHash);
        flush();
    }

    /**
     * Retrieve the stored password hash for a username.
     *
     * @return the password hash, or null if not found
     */
    public String load(String username) {
        return store.get(username);
    }

    /**
     * Check whether credentials exist for the given username.
     */
    public boolean exists(String username) {
        return store.containsKey(username);
    }

    /**
     * Remove credentials for a username and flush to disk.
     *
     * @return true if the entry existed and was removed
     */
    public boolean delete(String username) {
        boolean removed = store.remove(username) != null;
        if (removed) {
            flush();
        }
        return removed;
    }

    /**
     * Return all stored credentials for seeding the in-memory Database on startup.
     *
     * @return a copy of the username -> passwordHash map
     */
    public Map<String, String> loadAll() {
        return new HashMap<>(store);
    }

    private void loadFromDisk() {
        if (!file.exists()) {
            return;
        }
        try {
            Map<String, String> loaded = mapper.readValue(file, new TypeReference<>() {});
            store.putAll(loaded);
        } catch (IOException e) {
            System.err.println("CredentialsRepository: failed to load credentials.json — " + e.getMessage());
        }
    }

    private void flush() {
        try {
            file.getParentFile().mkdirs();
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, store);
        } catch (IOException e) {
            System.err.println("CredentialsRepository: failed to write credentials.json — " + e.getMessage());
        }
    }
}