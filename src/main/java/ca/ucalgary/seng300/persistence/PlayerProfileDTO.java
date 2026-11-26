package ca.ucalgary.seng300.persistence;

import ca.ucalgary.seng300.core.PlayerProfile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Jackson-friendly DTO for PlayerProfile.
 * Keeps PlayerProfile free of serialization annotations.
 * Used exclusively by UserRepository for JSON persistence.
 */
public class PlayerProfileDTO {

    public String username;
    public String firstName;
    public String lastName;
    public String passwordHash;
    public String currentRank;
    public String avatarPath;
    public String accountCreated;
    public Map<String, List<String>> matchesPlayed;
    public Map<String, Integer> wins;
    public Map<String, Integer> mmrByGame;
    public List<String> recentGameHistory;
    public List<String> friends;
    public List<String> incomingFriendRequests;

    public PlayerProfileDTO() {}

    /**
     * Convert a PlayerProfile domain object into a DTO for serialization.
     */
    public static PlayerProfileDTO fromProfile(PlayerProfile profile) {
        PlayerProfileDTO dto = new PlayerProfileDTO();
        dto.username          = profile.getUsername();
        dto.firstName         = profile.getFirstName();
        dto.lastName          = profile.getLastName();
        dto.passwordHash      = profile.getPasswordHash();
        dto.currentRank       = profile.getCurrentRank();
        dto.avatarPath        = profile.getAvatarPath();
        dto.accountCreated    = profile.getAccountCreated() != null
                ? profile.getAccountCreated().toString()
                : LocalDate.now().toString();
        dto.matchesPlayed           = new HashMap<>(profile.getMatchesPlayed());
        dto.wins                    = new HashMap<>(profile.getWins());
        dto.mmrByGame               = new HashMap<>(profile.getMmrByGame());
        dto.recentGameHistory       = new ArrayList<>(profile.getRecentGameHistory());
        dto.friends                 = new ArrayList<>(profile.getFriends());
        dto.incomingFriendRequests  = new ArrayList<>(profile.getIncomingFriendRequests());
        return dto;
    }

    /**
     * Convert this DTO back into a PlayerProfile domain object.
     */
    public PlayerProfile toProfile() {
        PlayerProfile profile = new PlayerProfile(username, firstName, lastName, passwordHash);
        profile.setCurrentRank(currentRank != null ? currentRank : "Bronze");
        if (avatarPath != null) profile.setAvatarPath(avatarPath);
        if (accountCreated != null) profile.setAccountCreated(LocalDate.parse(accountCreated));
        if (mmrByGame != null) mmrByGame.forEach(profile::setMmr);
        matchesPlayed.forEach((game, opponents) ->
                opponents.forEach(opponent -> profile.recordMatch(game, opponent)));
        wins.forEach((game, count) -> {
            for (int i = 0; i < count; i++) {
                profile.recordWin(game);
            }
        });
        if (recentGameHistory != null) {
            for (int i = recentGameHistory.size() - 1; i >= 0; i--) {
                profile.addRecentGame(recentGameHistory.get(i));
            }
        }
        if (friends != null) friends.forEach(profile::addFriend);
        if (incomingFriendRequests != null) incomingFriendRequests.forEach(profile::addIncomingRequest);
        return profile;
    }

}