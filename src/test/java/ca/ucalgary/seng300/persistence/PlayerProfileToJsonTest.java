package ca.ucalgary.seng300.persistence;

import ca.ucalgary.seng300.core.PlayerProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerProfileToJsonTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    // ---- Helper ----

    private PlayerProfile baseProfile() {
        return new PlayerProfile("jdoe1", "John", "Doe", "hashedpw");
    }

    // ---- Field serialization ----

    @Test
    void fromProfile_username_serializedCorrectly() throws Exception {
        JsonNode json = mapper.valueToTree(PlayerProfileDTO.fromProfile(baseProfile()));
        assertEquals("jdoe1", json.get("username").asText());
    }

    @Test
    void fromProfile_firstName_serializedCorrectly() throws Exception {
        JsonNode json = mapper.valueToTree(PlayerProfileDTO.fromProfile(baseProfile()));
        assertEquals("John", json.get("firstName").asText());
    }

    @Test
    void fromProfile_lastName_serializedCorrectly() throws Exception {
        JsonNode json = mapper.valueToTree(PlayerProfileDTO.fromProfile(baseProfile()));
        assertEquals("Doe", json.get("lastName").asText());
    }

    @Test
    void fromProfile_passwordHash_serializedCorrectly() throws Exception {
        JsonNode json = mapper.valueToTree(PlayerProfileDTO.fromProfile(baseProfile()));
        assertEquals("hashedpw", json.get("passwordHash").asText());
    }

    @Test
    void fromProfile_defaultRank_serializedAsBronze() throws Exception {
        JsonNode json = mapper.valueToTree(PlayerProfileDTO.fromProfile(baseProfile()));
        assertEquals("Bronze", json.get("currentRank").asText());
    }

    @Test
    void fromProfile_customRank_serializedCorrectly() throws Exception {
        PlayerProfile profile = baseProfile();
        profile.setCurrentRank("Gold");
        JsonNode json = mapper.valueToTree(PlayerProfileDTO.fromProfile(profile));
        assertEquals("Gold", json.get("currentRank").asText());
    }

    // ---- Empty nested maps ----

    @Test
    void fromProfile_emptyMatchesPlayed_serializedAsEmptyObject() throws Exception {
        JsonNode json = mapper.valueToTree(PlayerProfileDTO.fromProfile(baseProfile()));
        assertTrue(json.get("matchesPlayed").isObject());
        assertEquals(0, json.get("matchesPlayed").size());
    }

    @Test
    void fromProfile_emptyWins_serializedAsEmptyObject() throws Exception {
        JsonNode json = mapper.valueToTree(PlayerProfileDTO.fromProfile(baseProfile()));
        assertTrue(json.get("wins").isObject());
        assertEquals(0, json.get("wins").size());
    }

    // ---- Populated nested maps ----

    @Test
    void fromProfile_matchesPlayed_serializedWithCorrectGameKey() throws Exception {
        PlayerProfile profile = baseProfile();
        profile.recordMatch("TicTacToe", "asmith2");
        JsonNode json = mapper.valueToTree(PlayerProfileDTO.fromProfile(profile));
        assertTrue(json.get("matchesPlayed").has("TicTacToe"));
    }

    @Test
    void fromProfile_matchesPlayed_serializedWithCorrectOpponents() throws Exception {
        PlayerProfile profile = baseProfile();
        profile.recordMatch("TicTacToe", "asmith2");
        profile.recordMatch("TicTacToe", "bjones3");
        JsonNode json = mapper.valueToTree(PlayerProfileDTO.fromProfile(profile));
        assertEquals(2, json.get("matchesPlayed").get("TicTacToe").size());
    }

    @Test
    void fromProfile_wins_serializedWithCorrectCount() throws Exception {
        PlayerProfile profile = baseProfile();
        profile.recordWin("TicTacToe");
        profile.recordWin("TicTacToe");
        JsonNode json = mapper.valueToTree(PlayerProfileDTO.fromProfile(profile));
        assertEquals(2, json.get("wins").get("TicTacToe").asInt());
    }

    @Test
    void fromProfile_multipleGames_allSerializedCorrectly() throws Exception {
        PlayerProfile profile = baseProfile();
        profile.recordMatch("TicTacToe", "asmith2");
        profile.recordMatch("ConnectFour", "bjones3");
        profile.recordWin("TicTacToe");
        JsonNode json = mapper.valueToTree(PlayerProfileDTO.fromProfile(profile));
        assertEquals(2, json.get("matchesPlayed").size());
        assertEquals(1, json.get("wins").get("TicTacToe").asInt());
        assertFalse(json.get("wins").has("ConnectFour"));
    }

    // ---- JSON string output ----

    @Test
    void fromProfile_producesValidJsonString() throws Exception {
        String json = mapper.writeValueAsString(PlayerProfileDTO.fromProfile(baseProfile()));
        assertNotNull(json);
        assertTrue(json.contains("jdoe1"));
        assertTrue(json.contains("username"));
    }
}
