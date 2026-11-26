package ca.ucalgary.seng300.persistence;

import ca.ucalgary.seng300.core.PlayerProfile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * JSON-backed repository for PlayerProfile persistence.
 * Serializes profiles via PlayerProfileDTO to data/users.json.
 * PlayerProfile itself remains free of Jackson annotations.
 */
public class UserRepository {

    private static final String FILE_PATH = "data/users.json";

    private final ObjectMapper mapper;
    private final File file;
    private final Map<String, PlayerProfileDTO> store;

    public UserRepository() {
        this(FILE_PATH);
    }

    UserRepository(String filePath) {
        this.mapper = new ObjectMapper();
        this.file = new File(filePath);
        this.store = new HashMap<>();
        loadFromDisk();
    }

    /**
     * Persist a PlayerProfile to disk.
     */
    public void save(PlayerProfile profile) {
        store.put(profile.getUsername(), PlayerProfileDTO.fromProfile(profile));
        flush();
    }

    /**
     * Retrieve a PlayerProfile by username from the in-memory cache.
     *
     * @return the PlayerProfile, or null if not found
     */
    public PlayerProfile load(String username) {
        PlayerProfileDTO dto = store.get(username);
        return dto != null ? dto.toProfile() : null;
    }

    /**
     * Re-read the entire store from disk and return the requested profile.
     * Used for cross-instance synchronization (e.g. detecting friend requests
     * written by another running instance).
     */
    public PlayerProfile loadFresh(String username) {
        loadFromDisk();
        return load(username);
    }

    /**
     * Check whether a profile exists for the given username.
     */
    public boolean exists(String username) {
        return store.containsKey(username);
    }

    /**
     * Remove a profile and flush to disk.
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
     * Return all stored profiles for seeding the in-memory Database on startup.
     *
     * @return a map of username -> PlayerProfile
     */
    public Map<String, PlayerProfile> loadAll() {
        Map<String, PlayerProfile> result = new HashMap<>();
        store.forEach((username, dto) -> result.put(username, dto.toProfile()));
        return result;
    }

    private void loadFromDisk() {
        if (!file.exists()) {
            return;
        }
        try {
            Map<String, PlayerProfileDTO> loaded = mapper.readValue(
                    file, new TypeReference<>() {});
            store.putAll(loaded);
        } catch (IOException e) {
            System.err.println("UserRepository: failed to load users.json — " + e.getMessage());
        }
    }

    private void flush() {
        try {
            file.getParentFile().mkdirs();
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, store);
        } catch (IOException e) {
            System.err.println("UserRepository: failed to write users.json — " + e.getMessage());
        }
    }
}