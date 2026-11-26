package ca.ucalgary.seng300.leaderboard;

import ca.ucalgary.seng300.persistence.Database;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Manages game results, player rankings, and leaderboard queries.
 * Persists data through the Database stub.
 */
public class Leaderboard {

    private final Database database;

    // MMR adjustment constants
    private static final int WIN_MMR  =  25;
    private static final int LOSS_MMR = -15;
    private static final int DRAW_MMR =   5;

    // Rank tier thresholds — Bronze < 600, Silver 600–799, Gold 800+
    private static final int SILVER_THRESHOLD = 600;
    private static final int GOLD_THRESHOLD   = 800;

    private static final List<String> ALL_GAME_TYPES =
            Arrays.asList("tictactoe", "connectfour", "checkers");

    public Leaderboard() {
        this.database = new Database();
    }

    /** Use this constructor to share the platform's existing Database instance. */
    public Leaderboard(Database database) {
        this.database = database;
    }

    /**
     * Record the result of a completed game session.
     */
    public void logGameResult(String sessionId, String winnerId, String loserId, boolean isDraw) {
        if (sessionId == null) return;

        GameResult result = new GameResult(sessionId, winnerId, loserId, isDraw,
                System.currentTimeMillis());

        database.storeData("result_" + sessionId, result);

        if (winnerId != null) appendToPlayerHistory("resultHistory:" + winnerId, sessionId);
        if (loserId  != null) appendToPlayerHistory("resultHistory:" + loserId,  sessionId);
    }

    @SuppressWarnings("unchecked")
    private void appendToPlayerHistory(String key, String sessionId) {
        Object stored = database.retrieveData(key);
        List<String> history = (stored instanceof List) ? (List<String>) stored : new ArrayList<>();
        history.add(sessionId);
        database.storeData(key, history);
    }

    /**
     * Update a player's MMR and tier after a game outcome.
     * @param outcome "win", "loss", or "draw"
     */
    public void updatePlayerRanking(String playerId, String gameType, String outcome) {
        if (playerId == null || gameType == null || outcome == null) return;

        String key = "ranking:" + playerId + ":" + gameType;
        Object stored = database.retrieveData(key);
        PlayerRanking ranking = (stored instanceof PlayerRanking r) ? r : new PlayerRanking(gameType);

        int delta = switch (outcome.toLowerCase()) {
            case "win"  -> WIN_MMR;
            case "loss" -> LOSS_MMR;
            case "draw" -> DRAW_MMR;
            default     -> 0;
        };

        int newMmr = Math.max(0, ranking.getMmr() + delta);
        ranking.setMmr(newMmr);
        ranking.setTier(calculateTier(newMmr));
        database.storeData(key, ranking);

        addToRankingIndex(gameType, playerId);
    }

    /**
     * Directly set a player's MMR for a game type.
     * Used to restore persisted MMR values into the in-memory leaderboard on startup.
     */
    public void updatePlayerRankingToMmr(String playerId, String gameType, int mmr) {
        if (playerId == null || gameType == null) return;
        String key = "ranking:" + playerId + ":" + gameType;
        Object stored = database.retrieveData(key);
        PlayerRanking ranking = (stored instanceof PlayerRanking r) ? r : new PlayerRanking(gameType);
        ranking.setMmr(mmr);
        ranking.setTier(calculateTier(mmr));
        database.storeData(key, ranking);
        addToRankingIndex(gameType, playerId);
    }

    /**
     * Get the current MMR for a player in a specific game type.
     * Returns 500 (starting MMR) if the player has no ranking entry yet.
     */
    public int getPlayerMmr(String playerId, String gameType) {
        if (playerId == null || gameType == null) return 500;
        Object stored = database.retrieveData("ranking:" + playerId + ":" + gameType);
        return (stored instanceof PlayerRanking r) ? r.getMmr() : 500;
    }

    /**
     * Get the tier for a player in a specific game type.
     * Returns "Bronze" if the player has no ranking entry yet.
     */
    public String getPlayerTier(String playerId, String gameType) {
        if (playerId == null || gameType == null) return "Bronze";
        Object stored = database.retrieveData("ranking:" + playerId + ":" + gameType);
        return (stored instanceof PlayerRanking r) ? r.getTier() : "Bronze";
    }

    /**
     * Returns the highest tier the player has earned across all game types.
     * Used to set the single overall rank displayed on the dashboard profile.
     */
    public String getOverallTier(String playerId) {
        String best = "Bronze";
        for (String game : ALL_GAME_TYPES) {
            String tier = getPlayerTier(playerId, game);
            if (tierRank(tier) > tierRank(best)) best = tier;
        }
        return best;
    }

    private int tierRank(String tier) {
        return switch (tier) {
            case "Gold"   -> 2;
            case "Silver" -> 1;
            default       -> 0;
        };
    }

    private String calculateTier(int mmr) {
        if (mmr >= GOLD_THRESHOLD)   return "Gold";
        if (mmr >= SILVER_THRESHOLD) return "Silver";
        return "Bronze";
    }

    /**
     * Get the top N players by MMR for a specific game type.
     */
    @SuppressWarnings("unchecked")
    public List<String> getTopPlayersByRank(String gameType, int limit) {
        if (gameType == null || limit <= 0) return new ArrayList<>();

        Object storedIndex = database.retrieveData("rankingIndex:" + gameType);
        if (!(storedIndex instanceof List)) return new ArrayList<>();
        List<String> allPlayers = (List<String>) storedIndex;

        List<Map.Entry<String, Integer>> playerMmrList = new ArrayList<>();
        for (String playerId : allPlayers) {
            Object stored = database.retrieveData("ranking:" + playerId + ":" + gameType);
            if (stored instanceof PlayerRanking ranking) {
                playerMmrList.add(Map.entry(playerId, ranking.getMmr()));
            }
        }
        playerMmrList.sort((a, b) -> b.getValue() - a.getValue());

        List<String> topPlayers = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, playerMmrList.size()); i++) {
            topPlayers.add(playerMmrList.get(i).getKey());
        }
        return topPlayers;
    }

    /**
     * Get a player's rank position (1-based) within a game type, sorted by MMR descending.
     * Returns -1 if the player has no ranking entry for this game type.
     */
    @SuppressWarnings("unchecked")
    public int getPlayerRank(String playerId, String gameType) {
        if (playerId == null || gameType == null) return -1;

        Object stored = database.retrieveData("ranking:" + playerId + ":" + gameType);
        if (!(stored instanceof PlayerRanking playerRanking)) return -1;

        Object storedIndex = database.retrieveData("rankingIndex:" + gameType);
        if (!(storedIndex instanceof List)) return -1;
        List<String> allPlayers = (List<String>) storedIndex;

        int rank = 1;
        for (String otherId : allPlayers) {
            if (otherId.equals(playerId)) continue;
            Object otherStored = database.retrieveData("ranking:" + otherId + ":" + gameType);
            if (otherStored instanceof PlayerRanking other && other.getMmr() > playerRanking.getMmr()) {
                rank++;
            }
        }
        return rank;
    }

    /**
     * Get a player's stats for a game type.
     */
    public Object getPlayerStats(String playerId, String gameType) {
        return null;
    }

    @SuppressWarnings("unchecked")
    private void addToRankingIndex(String gameType, String playerId) {
        String indexKey = "rankingIndex:" + gameType;
        Object storedIndex = database.retrieveData(indexKey);
        List<String> index = (storedIndex instanceof List)
                ? (List<String>) storedIndex : new ArrayList<>();
        if (!index.contains(playerId)) {
            index.add(playerId);
            database.storeData(indexKey, index);
        }
    }

    public Database getDatabase() { return database; }
}
