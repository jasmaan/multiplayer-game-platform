package ca.ucalgary.seng300.matchmaking;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import ca.ucalgary.seng300.events.EventBus;
import ca.ucalgary.seng300.events.MatchFoundEvent;
import ca.ucalgary.seng300.leaderboard.Leaderboard;
import ca.ucalgary.seng300.room.Room;
import ca.ucalgary.seng300.room.RoomManager;

/**
 * Manages player matchmaking queues.
 * Pairs players by game type (skill-based pairing to be refined in later iterations).
 */
public class MatchMaking {

    private final Map<String, PlayerQueue> queues;
    private final Map<String, Long> queueJoinTime;
    private final PartySystem partySystem;
    private final RoomManager roomManager;
    private final EventBus eventBus;
    private final Leaderboard leaderboard;

    private static final int RANK_RANGE = 100; // The base MMR range
    private static final int RANK_RANGE_EXPANSION = 50;  // extra range added per interval
    private static final long EXPANSION_INTERVAL = 15_000; // expand range every 15 seconds

    public MatchMaking(RoomManager roomManager, EventBus eventBus, Leaderboard leaderboard) {
        this.queues = new HashMap<>();
        this.queueJoinTime = new HashMap<>();
        this.partySystem = new PartySystem();
        this.roomManager = roomManager;
        this.eventBus = eventBus;
        this.leaderboard = leaderboard;
    }

    /**
     * Add a player to the queue for a specific game type.
     */
    public void addPlayerToQueue(String playerId, String gameType) {
        queues.computeIfAbsent(gameType, PlayerQueue::new).enqueue(playerId);
        queueJoinTime.put(playerId, System.currentTimeMillis());
    }

    /**
     * Remove a player from whatever queue they are in.
     */
    public void removePlayer(String playerId) {
        for (PlayerQueue queue : queues.values()) {
            if (queue.remove(playerId)) {
                queueJoinTime.remove(playerId);
                return;
            }
        }
    }

    /**
     * Attempt to match players for a game type.
     * Default: matches 2 players (first-come-first-served).
     * TODO: implement skill-based matching using player rank/MMR.
     * @return list of matched player IDs, or empty if not enough players
     */
    public List<String> matchPlayers(String gameType) {
        PlayerQueue queue = queues.get(gameType);
        if (queue == null || queue.size() < 2) {
            return List.of();
        }

        List<String> waiting = queue.peek(queue.size());

        for (int i = 0; i < waiting.size(); i++) {
            String playerOne = waiting.get(i);
            int rankOne = getPlayerMMR(playerOne, gameType);
            int rangeOne = effectiveRange(playerOne);

            for (int j = i + 1; j < waiting.size(); j++) {
                String playerTwo = waiting.get(j);
                int rankTwo = getPlayerMMR(playerTwo, gameType);
                int rangeTwo = effectiveRange(playerTwo);
                int allowedRange = Math.max(rangeOne, rangeTwo);
                if (Math.abs(rankOne - rankTwo) <= allowedRange) {
                    queue.remove(playerOne);
                    queue.remove(playerTwo);
                    queueJoinTime.remove(playerOne);
                    queueJoinTime.remove(playerTwo);
                    return List.of(playerOne, playerTwo);
                }
            }
        }

        return List.of();
    }

    public boolean matchParties(String partyId, String gameType, int MaxPlayersForGame){
        List<String> members = partySystem.getPartyMembers(partyId);
        if(members.isEmpty()){
            return false;
        }
        if(members.size() == MaxPlayersForGame){
            String hostId = members.get(0);
            Room room = roomManager.createRoom(hostId, gameType, MaxPlayersForGame);
            for(int i = 1; i < members.size(); i++){
                room.addPlayer(members.get(i));
            }
            String eventId = UUID.randomUUID().toString();
            MatchFoundEvent event = new MatchFoundEvent(eventId, members, gameType, room.getRoomId());
            eventBus.publish(event);
            return true;
        }

        return false;
    }

    public int getQueuePosition(String playerId) {
        for (PlayerQueue queue : queues.values()) {
            int pos = queue.getPosition(playerId);
            if (pos >= 0) {
                return pos;
            }
        }
        return -1;
    }

    public int getQueueSize(String gameType) {
        PlayerQueue queue = queues.get(gameType);
        return queue != null ? queue.size() : 0;
    }

    public boolean isPlayerInQueue(String playerId) {
        return getQueuePosition(playerId) >= 0;
    }

    // Find the range depending on the interval waited
    private int effectiveRange(String playerId) {
        Long joinTime = queueJoinTime.get(playerId);
        if (joinTime == null) {
            return RANK_RANGE;
        }
        long waitMs = System.currentTimeMillis() - joinTime; // Finds how long been waiting
        int intervals = (int)(waitMs / EXPANSION_INTERVAL); //Finds how many intervals
        return RANK_RANGE + intervals * RANK_RANGE_EXPANSION;
    }

    private int getPlayerMMR(String playerId, String gameType) {
        return leaderboard.getPlayerMmr(playerId, gameType);
    }

    public PartySystem getPartySystem() { return partySystem; }
}
