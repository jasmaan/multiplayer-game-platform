package ca.ucalgary.seng300.matchmaking;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a group of players who want to queue together.
 */
public class Party {

    private final String partyId;
    private final String hostId;
    private final List<String> members;
    private final int maxSize;

    public Party(String partyId, String hostId, int maxSize) {
        this.partyId = partyId;
        this.hostId = hostId;
        this.maxSize = maxSize;
        this.members = new ArrayList<>();
        this.members.add(hostId);
    }

    public boolean addMember(String playerId) {
        if (isFull() || members.contains(playerId)) {
            return false;
        }
        members.add(playerId);
        return true;
    }

    public boolean removeMember(String playerId) {
        return members.remove(playerId);
    }

    public boolean isFull() {
        return members.size() >= maxSize;
    }

    public List<String> getMembers() { return new ArrayList<>(members); }
    public String getPartyId() { return partyId; }
    public String getHostId() { return hostId; }
    public int getMaxSize() { return maxSize; }
}
