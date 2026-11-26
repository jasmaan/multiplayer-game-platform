package ca.ucalgary.seng300.leaderboard;

public class PlayerRanking {
    private int mmr;
    private String tier;
    private final String gameType;

    public PlayerRanking(String gameType) {
        this.gameType = gameType;
        this.mmr  = 500;        // every new player starts at 500 MMR
        this.tier = "Bronze";   // and starts in the lowest tier
    }

    public int getMmr()             { return mmr; }
    public String getTier()         { return tier; }
    public String getGameType()     { return gameType; }
    public void setMmr(int mmr)     { this.mmr = mmr; }
    public void setTier(String tier){ this.tier = tier; }
}