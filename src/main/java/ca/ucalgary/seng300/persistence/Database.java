package ca.ucalgary.seng300.persistence;

import java.util.HashMap;
import java.util.Map;

/**
 * Stub database for persistence.
 * Uses an in-memory HashMap. No external database required.
 * This stub defines where the real database integration will connect.
 */
public class Database {

    private final Map<String, Object> dataStore;

    public Database() {
        this.dataStore = new HashMap<>();
    }

    /**
     * Store a key-value pair.
     */
    public void storeData(String key, Object value) {
        dataStore.put(key, value);
    }

    /**
     * Retrieve data by key.
     * @return the stored value, or null if not found
     */
    public Object retrieveData(String key) {
        return dataStore.get(key);
    }

    /**
     * Delete data by key.
     * @return true if the key existed and was removed
     */
    public boolean deleteData(String key) {
        return dataStore.remove(key) != null;
    }

    /**
     * Check if a key exists in the store.
     */
    public boolean exists(String key) {
        return dataStore.containsKey(key);
    }
}
