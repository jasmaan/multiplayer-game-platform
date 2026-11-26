package ca.ucalgary.seng300.persistence;

import ca.ucalgary.seng300.core.PlayerProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonToPlayerProfileTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    // ---- Helper ----

    private PlayerProfile fromJson(String json) throws Exception {
        return mapper.readValue(json, PlayerProfileDTO.class).toProfile();
    }

    // ---- Field deserialization ----

    @Test
    void toProfile_username_deserializedCorrectly() throws Exception {
        String json = """
                {"username":"jdoe1","firstName":"John","lastName":"Doe",
                 "passwordHash":"hashedpw","currentRank":"Unranked",
                 "matchesPlayed":{},"wins":{}}
                """;
        assertEquals("jdoe1", fromJson(json).getUsername());
    }

    @Test
    void toProfile_firstName_deserializedCorrectly() throws Exception {
        String json = """
                {"username":"jdoe1","firstName":"John","lastName":"Doe",
                 "passwordHash":"hashedpw","currentRank":"Unranked",
                 "matchesPlayed":{},"wins":{}}
                """;
        assertEquals("John", fromJson(json).getFirstName());
    }

    @Test
    void toProfile_lastName_deserializedCorrectly() throws Exception {
        String json = """
                {"username":"jdoe1","firstName":"John","lastName":"Doe",
                 "passwordHash":"hashedpw","currentRank":"Unranked",
                 "matchesPlayed":{},"wins":{}}
                """;
        assertEquals("Doe", fromJson(json).getLastName());
    }

    @Test
    void toProfile_passwordHash_deserializedCorrectly() throws Exception {
        String json = """
                {"username":"jdoe1","firstName":"John","lastName":"Doe",
                 "passwordHash":"hashedpw","currentRank":"Unranked",
                 "matchesPlayed":{},"wins":{}}
                """;
        assertEquals("hashedpw", fromJson(json).getPasswordHash());
    }

    @Test
    void toProfile_customRank_deserializedCorrectly() throws Exception {
        String json = """
                {"username":"jdoe1","firstName":"John","lastName":"Doe",
                 "passwordHash":"hashedpw","currentRank":"Gold",
                 "matchesPlayed":{},"wins":{}}
                """;
        assertEquals("Gold", fromJson(json).getCurrentRank());
    }

    @Test
    void toProfile_defaultRank_deserializedAsUnranked() throws Exception {
        String json = """
                {"username":"jdoe1","firstName":"John","lastName":"Doe",
                 "passwordHash":"hashedpw","currentRank":"Unranked",
                 "matchesPlayed":{},"wins":{}}
                """;
        assertEquals("Unranked", fromJson(json).getCurrentRank());
    }

    // ---- Empty nested maps ----

    @Test
    void toProfile_emptyMatchesPlayed_producesZeroMatches() throws Exception {
        String json = """
                {"username":"jdoe1","firstName":"John","lastName":"Doe",
                 "passwordHash":"hashedpw","currentRank":"Unranked",
                 "matchesPlayed":{},"wins":{}}
                """;
        assertEquals(0, fromJson(json).getTotalMatchesPlayed());
    }

    @Test
    void toProfile_emptyWins_producesZeroWins() throws Exception {
        String json = """
                {"username":"jdoe1","firstName":"John","lastName":"Doe",
                 "passwordHash":"hashedpw","currentRank":"Unranked",
                 "matchesPlayed":{},"wins":{}}
                """;
        assertEquals(0, fromJson(json).getTotalWins());
    }

    // ---- Populated nested maps ----

    @Test
    void toProfile_matchesPlayed_reconstructedCorrectly() throws Exception {
        String json = """
                {"username":"jdoe1","firstName":"John","lastName":"Doe",
                 "passwordHash":"hashedpw","currentRank":"Unranked",
                 "matchesPlayed":{"TicTacToe":["asmith2","bjones3"]},"wins":{}}
                """;
        PlayerProfile profile = fromJson(json);
        assertEquals(2, profile.getMatchesPlayed().get("TicTacToe").size());
        assertEquals(2, profile.getTotalMatchesPlayed());
    }

    @Test
    void toProfile_wins_reconstructedCorrectly() throws Exception {
        String json = """
                {"username":"jdoe1","firstName":"John","lastName":"Doe",
                 "passwordHash":"hashedpw","currentRank":"Unranked",
                 "matchesPlayed":{},"wins":{"TicTacToe":3}}
                """;
        PlayerProfile profile = fromJson(json);
        assertEquals(3, profile.getWins().get("TicTacToe"));
        assertEquals(3, profile.getTotalWins());
    }

    @Test
    void toProfile_multipleGames_allReconstructedCorrectly() throws Exception {
        String json = """
                {"username":"jdoe1","firstName":"John","lastName":"Doe",
                 "passwordHash":"hashedpw","currentRank":"Silver",
                 "matchesPlayed":{"TicTacToe":["asmith2"],"ConnectFour":["bjones3","clee4"]},
                 "wins":{"TicTacToe":1,"ConnectFour":2}}
                """;
        PlayerProfile profile = fromJson(json);
        assertEquals(3, profile.getTotalMatchesPlayed());
        assertEquals(3, profile.getTotalWins());
        assertEquals("Silver", profile.getCurrentRank());
    }

    // ---- Full round-trip ----

    @Test
    void roundTrip_allFields_preservedCorrectly() throws Exception {
        PlayerProfile original = new PlayerProfile("jdoe1", "John", "Doe", "hashedpw");
        original.setCurrentRank("Platinum");
        original.recordMatch("TicTacToe", "asmith2");
        original.recordMatch("ConnectFour", "bjones3");
        original.recordWin("TicTacToe");
        original.recordWin("ConnectFour");
        original.recordWin("ConnectFour");

        String json = mapper.writeValueAsString(PlayerProfileDTO.fromProfile(original));
        PlayerProfile restored = mapper.readValue(json, PlayerProfileDTO.class).toProfile();

        assertEquals(original.getUsername(), restored.getUsername());
        assertEquals(original.getFirstName(), restored.getFirstName());
        assertEquals(original.getLastName(), restored.getLastName());
        assertEquals(original.getPasswordHash(), restored.getPasswordHash());
        assertEquals(original.getCurrentRank(), restored.getCurrentRank());
        assertEquals(original.getTotalMatchesPlayed(), restored.getTotalMatchesPlayed());
        assertEquals(original.getTotalWins(), restored.getTotalWins());
        assertEquals(1, restored.getWins().get("TicTacToe"));
        assertEquals(2, restored.getWins().get("ConnectFour"));
    }
}
