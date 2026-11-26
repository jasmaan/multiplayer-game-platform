# Player Context Tracking Model

## Owner: Platform Core Sub-team

## Purpose

PlatformCore must know **where each logged-in player currently is** (dashboard, lobby, queue, in-game, results screen) so it can route notifications appropriately. A turn alert for a player who is already in the game view should update the board directly, while the same alert for a player browsing the dashboard should appear as a banner/badge.

## PlayerContext Enum

| Value | Description | Notification Routing |
|-------|-------------|---------------------|
| `DASHBOARD` | Player is on the main dashboard or browsing profiles/leaderboards | Banner or badge notification |
| `IN_LOBBY` | Player is in a room, waiting for the game to start | Lobby alert / room status update |
| `IN_QUEUE` | Player is in the matchmaking queue | Queue status update; match-found popup |
| `IN_GAME` | Player is actively in a game session | Direct game view update (board refresh, input toggle) |
| `VIEWING_RESULTS` | Player is on the post-game results screen | Stats update on results screen |

## Data Structure

```java
// Stored in PlatformCore
private final Map<String, PlayerContext> playerContexts = new HashMap<>();
```

- **Key:** `playerId` (String) — the unique player identifier
- **Value:** `PlayerContext` enum value — the player's current navigation state

## Context Transitions

Aligned with the Platform State Machine (`01.01 Platform SM (Refined)`):

```
login()              → DASHBOARD
joinRoom() / createRoom() → IN_LOBBY
leaveRoom()          → DASHBOARD
joinQueue()          → IN_QUEUE
leaveQueue()         → DASHBOARD
matchFound           → IN_GAME (via auto-join)
hostStartsGame       → IN_GAME
gameOver             → VIEWING_RESULTS
returnToDashboard()  → DASHBOARD
logout()             → (removed from map)
```

## Usage in PlatformCore

When PlatformCore receives a `GameStateUpdate` event, it iterates over the event's recipient list and checks each player's context:

```java
for (String playerId : update.getRecipients()) {
    PlayerContext ctx = playerContexts.getOrDefault(playerId, PlayerContext.DASHBOARD);
    switch (ctx) {
        case IN_GAME -> notificationService.notifyPlayer(playerId, "Board updated");
        case DASHBOARD, IN_LOBBY -> notificationService.createTurnAlert(playerId, update.getSessionId());
        // etc.
    }
}
```

## Constraints

- A player can only be in **one context at a time** (single active screen)
- Context is set/cleared by PlatformCore methods, not by external callers
- If a player is not in the map, they are not logged in
