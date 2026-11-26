package ca.ucalgary.seng300.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CredentialsRepositoryTest {

    private static final String TEST_FILE = "data/test_credentials.json";

    private CredentialsRepository repo;

    @BeforeEach
    void setUp() {
        new File(TEST_FILE).delete();
        repo = new CredentialsRepository(TEST_FILE);
    }

    @AfterEach
    void tearDown() {
        new File(TEST_FILE).delete();
    }

    // ---- save() + load() ----

    @Test
    void save_thenLoad_returnsStoredHash() {
        repo.save("jdoe1", "abc123hash");
        assertEquals("abc123hash", repo.load("jdoe1"));
    }

    @Test
    void load_unknownUsername_returnsNull() {
        assertNull(repo.load("nobody"));
    }

    @Test
    void save_overwritesExistingEntry() {
        repo.save("jdoe1", "oldhash");
        repo.save("jdoe1", "newhash");
        assertEquals("newhash", repo.load("jdoe1"));
    }

    // ---- exists() ----

    @Test
    void exists_afterSave_returnsTrue() {
        repo.save("jdoe1", "abc123hash");
        assertTrue(repo.exists("jdoe1"));
    }

    @Test
    void exists_beforeSave_returnsFalse() {
        assertFalse(repo.exists("jdoe1"));
    }

    // ---- delete() ----

    @Test
    void delete_existingEntry_returnsTrue() {
        repo.save("jdoe1", "abc123hash");
        assertTrue(repo.delete("jdoe1"));
    }

    @Test
    void delete_existingEntry_removesFromStore() {
        repo.save("jdoe1", "abc123hash");
        repo.delete("jdoe1");
        assertFalse(repo.exists("jdoe1"));
        assertNull(repo.load("jdoe1"));
    }

    @Test
    void delete_nonExistentEntry_returnsFalse() {
        assertFalse(repo.delete("nobody"));
    }

    // ---- JSON file creation ----

    @Test
    void save_createsJsonFileOnDisk() {
        repo.save("jdoe1", "abc123hash");
        assertTrue(new File(TEST_FILE).exists());
    }

    @Test
    void delete_flushesToDisk() {
        repo.save("jdoe1", "abc123hash");
        repo.delete("jdoe1");

        CredentialsRepository reloaded = new CredentialsRepository(TEST_FILE);
        assertFalse(reloaded.exists("jdoe1"));
    }

    // ---- Persistence across restarts ----

    @Test
    void credentials_surviveRestart() {
        repo.save("jdoe1", "abc123hash");
        repo.save("asmith2", "xyz789hash");

        CredentialsRepository reloaded = new CredentialsRepository(TEST_FILE);
        assertEquals("abc123hash", reloaded.load("jdoe1"));
        assertEquals("xyz789hash", reloaded.load("asmith2"));
    }

    @Test
    void delete_survivesRestart() {
        repo.save("jdoe1", "abc123hash");
        repo.delete("jdoe1");

        CredentialsRepository reloaded = new CredentialsRepository(TEST_FILE);
        assertFalse(reloaded.exists("jdoe1"));
    }

    // ---- loadAll() ----

    @Test
    void loadAll_returnsAllStoredEntries() {
        repo.save("jdoe1", "hash1");
        repo.save("asmith2", "hash2");

        Map<String, String> all = repo.loadAll();
        assertEquals(2, all.size());
        assertEquals("hash1", all.get("jdoe1"));
        assertEquals("hash2", all.get("asmith2"));
    }

    @Test
    void loadAll_onEmptyStore_returnsEmptyMap() {
        assertTrue(repo.loadAll().isEmpty());
    }

    @Test
    void loadAll_returnsCopy_mutationDoesNotAffectStore() {
        repo.save("jdoe1", "hash1");
        repo.loadAll().put("hacker", "injected");
        assertFalse(repo.exists("hacker"));
    }

    // ---- Missing file handled gracefully ----

    @Test
    void noFile_onStartup_startsWithEmptyStore() {
        CredentialsRepository fresh = new CredentialsRepository("data/nonexistent_credentials.json");
        assertTrue(fresh.loadAll().isEmpty());
    }
}