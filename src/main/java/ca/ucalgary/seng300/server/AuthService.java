package ca.ucalgary.seng300.server;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issues and validates HMAC-SHA256 session tokens.
 * No external JWT library — uses standard Java crypto.
 *
 * Token format: base64(playerId:issuedAt) + "." + base64(HMAC)
 * Tokens expire after TOKEN_TTL_MS milliseconds of inactivity.
 */
public class AuthService {

    private static final String ALGORITHM  = "HmacSHA256";
    private static final long   TOKEN_TTL_MS = 10 * 60 * 1000L; // 10 minutes
    private final String secret;

    // playerId → last-seen timestamp (refreshed on AUTH_PING)
    private final Map<String, Long> lastSeen = new ConcurrentHashMap<>();
    // token → playerId (so we can look up who owns a token)
    private final Map<String, String> tokenToPlayer = new ConcurrentHashMap<>();

    public AuthService(String secret) {
        this.secret = secret;
    }

    // ── Token issuance ─────────────────────────────────────────────

    /**
     * Issue a new token for the given player.
     * Any previous token for this player is invalidated.
     */
    public String issue(String playerId) {
        long now = System.currentTimeMillis();
        String data = playerId + ":" + now;
        String encoded = Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8));
        String sig = hmac(data);
        String token = encoded + "." + sig;

        // Revoke old token if present
        tokenToPlayer.values().removeIf(pid -> pid.equals(playerId));

        tokenToPlayer.put(token, playerId);
        lastSeen.put(playerId, now);
        return token;
    }

    // ── Token validation ───────────────────────────────────────────

    /**
     * Validate the token and return the playerId it belongs to.
     * Returns null if the token is missing, malformed, or expired.
     */
    public String validate(String token) {
        if (token == null || !token.contains(".")) return null;

        String playerId = tokenToPlayer.get(token);
        if (playerId == null) return null;

        // Check expiry
        Long seen = lastSeen.get(playerId);
        if (seen == null || System.currentTimeMillis() - seen > TOKEN_TTL_MS) {
            revoke(token);
            return null;
        }
        return playerId;
    }

    /**
     * Refresh the last-seen timestamp for a player (call on AUTH_PING).
     * Returns false if the token is no longer valid.
     */
    public boolean refresh(String token) {
        String playerId = validate(token);
        if (playerId == null) return false;
        lastSeen.put(playerId, System.currentTimeMillis());
        return true;
    }

    /** Invalidate a token (called on logout or socket close). */
    public void revoke(String token) {
        String playerId = tokenToPlayer.remove(token);
        if (playerId != null) lastSeen.remove(playerId);
    }

    // ── HMAC helper ────────────────────────────────────────────────

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (Exception e) {
            throw new RuntimeException("HMAC failed", e);
        }
    }
}
