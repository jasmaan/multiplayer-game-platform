package ca.ucalgary.seng300.persistence;

import ca.ucalgary.seng300.core.PlayerProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UserRepositoryTest {

    private static final String TEST_FILE = "data/test_users.json";

    private UserRepository repo;

    @BeforeEach
    void setUp() {
        new File(TEST_FILE).delete();
        repo = new UserRepository(TEST_FILE);
    }

    @AfterEach
    void tearDown() {
        new File(TEST_FILE).delete();
    }

    // ---- Helper ----

    private PlayerProfile makeProfile(String username) {
        return new PlayerProfile(username, "John", "Doe", "hashedpw");
    }

    // ---- save() + load() ----

    @Test
    void save_thenLoad_returnsEquivalentProfile() {
        PlayerProfile profile = makeProfile("jdoe1");
        repo.save(profile);

        PlayerProfile loaded = repo.load("jdoe1");
        assertNotNull(loaded);
        assertEquals("jdoe1", loaded.getUsername());
        assertEquals("John", loaded.getFirstName());
        assertEquals("Doe", loaded.getLastName());
        assertEquals("hashedpw", loaded.getPasswordHash());
        assertEquals("Bronze", loaded.getCurrentRank());
    }

    @Test
    void load_unknownUsername_returnsNull() {
        assertNull(repo.load("nobody"));
    }

    @Test
    void save_overwritesExistingEntry() {
        PlayerProfile original = makeProfile("jdoe1");
        repo.save(original);

        PlayerProfile updated = new PlayerProfile("jdoe1", "Jane", "Doe", "newhash");
        repo.save(updated);

        assertEquals("Jane", repo.load("jdoe1").getFirstName());
        assertEquals("newhash", repo.load("jdoe1").getPasswordHash());
    }

    // ---- exists() ----

    @Test
    void exists_afterSave_returnsTrue() {
        repo.save(makeProfile("jdoe1"));
        assertTrue(repo.exists("jdoe1"));
    }

    @Test
    void exists_beforeSave_returnsFalse() {
        assertFalse(repo.exists("jdoe1"));
    }

    // ---- delete() ----

    @Test
    void delete_existingEntry_returnsTrue() {
        repo.save(makeProfile("jdoe1"));
        assertTrue(repo.delete("jdoe1"));
    }

    @Test
    void delete_existingEntry_removesFromStore() {
        repo.save(makeProfile("jdoe1"));
        repo.delete("jdoe1");
        assertFalse(repo.exists("jdoe1"));
        assertNull(repo.load("jdoe1"));
    }

    @Test
    void delete_nonExistentEntry_returnsFalse() {
        assertFalse(repo.delete("nobody"));
    }

    // ---- Rank round-trip ----

    @Test
    void save_customRank_survivesRoundTrip() {
        PlayerProfile profile = makeProfile("jdoe1");
        profile.setCurrentRank("Gold");
        repo.save(profile);

        assertEquals("Gold", repo.load("jdoe1").getCurrentRank());
    }

    // ---- matchesPlayed round-trip ----

    @Test
    void save_matchesPlayed_survivesRoundTrip() {
        PlayerProfile profile = makeProfile("jdoe1");
        profile.recordMatch("TicTacToe", "asmith2");
        profile.recordMatch("TicTacToe", "bjones3");
        profile.recordMatch("ConnectFour", "asmith2");
        repo.save(profile);

        PlayerProfile loaded = repo.load("jdoe1");
        assertEquals(2, loaded.getMatchesPlayed().get("TicTacToe").size());
        assertEquals(1, loaded.getMatchesPlayed().get("ConnectFour").size());
        assertEquals(3, loaded.getTotalMatchesPlayed());
    }

    // ---- wins round-trip ----

    @Test
    void save_wins_survivesRoundTrip() {
        PlayerProfile profile = makeProfile("jdoe1");
        profile.recordWin("TicTacToe");
        profile.recordWin("TicTacToe");
        profile.recordWin("ConnectFour");
        repo.save(profile);

        PlayerProfile loaded = repo.load("jdoe1");
        assertEquals(2, loaded.getWins().get("TicTacToe"));
        assertEquals(1, loaded.getWins().get("ConnectFour"));
        assertEquals(3, loaded.getTotalWins());
    }

    // ---- JSON file creation ----

    @Test
    void save_createsJsonFileOnDisk() {
        repo.save(makeProfile("jdoe1"));
        assertTrue(new File(TEST_FILE).exists());
    }

    @Test
    void delete_flushesToDisk() {
        repo.save(makeProfile("jdoe1"));
        repo.delete("jdoe1");

        UserRepository reloaded = new UserRepository(TEST_FILE);
        assertFalse(reloaded.exists("jdoe1"));
    }

    // ---- Persistence across restarts ----

    @Test
    void profile_survivesRestart() {
        PlayerProfile profile = makeProfile("jdoe1");
        profile.setCurrentRank("Silver");
        profile.recordMatch("TicTacToe", "asmith2");
        profile.recordWin("TicTacToe");
        repo.save(profile);

        UserRepository reloaded = new UserRepository(TEST_FILE);
        PlayerProfile loaded = reloaded.load("jdoe1");

        assertNotNull(loaded);
        assertEquals("Silver", loaded.getCurrentRank());
        assertEquals(1, loaded.getTotalMatchesPlayed());
        assertEquals(1, loaded.getTotalWins());
    }

    @Test
    void multipleProfiles_surviveRestart() {
        repo.save(makeProfile("jdoe1"));
        repo.save(makeProfile("asmith2"));

        UserRepository reloaded = new UserRepository(TEST_FILE);
        assertTrue(reloaded.exists("jdoe1"));
        assertTrue(reloaded.exists("asmith2"));
    }

    // ---- loadAll() ----

    @Test
    void loadAll_returnsAllStoredProfiles() {
        repo.save(makeProfile("jdoe1"));
        repo.save(makeProfile("asmith2"));

        Map<String, PlayerProfile> all = repo.loadAll();
        assertEquals(2, all.size());
        assertTrue(all.containsKey("jdoe1"));
        assertTrue(all.containsKey("asmith2"));
    }

    @Test
    void loadAll_onEmptyStore_returnsEmptyMap() {
        assertTrue(repo.loadAll().isEmpty());
    }

    @Test
    void loadAll_returnsCopy_mutationDoesNotAffectStore() {
        repo.save(makeProfile("jdoe1"));
        repo.loadAll().remove("jdoe1");
        assertTrue(repo.exists("jdoe1"));
    }

    // ---- Missing file handled gracefully ----

    @Test
    void noFile_onStartup_startsWithEmptyStore() {
        UserRepository fresh = new UserRepository("data/nonexistent_users.json");
        assertTrue(fresh.loadAll().isEmpty());
    }
}
