package ca.ucalgary.seng300.matchmaking;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages party creation, membership, and disbanding.
 * Parties allow players to group up before queueing together.
 */
public class PartySystem {

    private static final int DEFAULT_MAX_PARTY_SIZE = 4;

    private final Map<String, Party> activeParties;

    public PartySystem() {
        this.activeParties = new HashMap<>();
    }

    public String createParty(String hostId) {
        String partyId = UUID.randomUUID().toString();
        Party party = new Party(partyId, hostId, DEFAULT_MAX_PARTY_SIZE);
        activeParties.put(partyId, party);
        return partyId;
    }

    public boolean joinParty(String partyId, String playerId) {
        Party party = activeParties.get(partyId);
        if (party == null) {
            return false;
        }
        return party.addMember(playerId);
    }

    public boolean leaveParty(String partyId, String playerId) {
        Party party = activeParties.get(partyId);
        if (party == null) {
            return false;
        }
        boolean removed = party.removeMember(playerId);
        if (removed && (party.getHostId().equals(playerId) || party.getMembers().isEmpty())) {
            disbandParty(partyId);
        }
        return removed;
    }

    public List<String> getPartyMembers(String partyId) {
        Party party = activeParties.get(partyId);
        return party != null ? party.getMembers() : List.of();
    }

    public void disbandParty(String partyId) {
        activeParties.remove(partyId);
    }

    public Party getParty(String partyId) {
        return activeParties.get(partyId);
    }
}
