package ca.ucalgary.seng300.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ca.ucalgary.seng300.validation.Validation;

import static org.junit.jupiter.api.Assertions.*;

class PlatformCoreTest {

    private PlatformCore platform;

    @BeforeEach
    void setUp() {
        platform = new PlatformCore();
        platform.registerPlayer("john", "doe", "Password1!");
    }

    // ---- validateCredentials() ----

    @Test
    void validateCredentials_validInputs_returnsTrue() {
        Validation result = platform.validateCredentials("john", "doe", "Password1!");
        assertTrue(result.isValid());
    }

    @Test
    void validateCredentials_nameStartsWithDigit_returnsFalse() {
        Validation result = platform.validateCredentials("1john", "doe", "Password1!");
        assertFalse(result.isValid());
    }

    @Test
    void validateCredentials_passwordNoSpecialChar_returnsFalse() {
        Validation result = platform.validateCredentials("john", "doe", "Password123");
        assertFalse(result.isValid());
    }

    // ---- hashPassword() (tested indirectly via login) ----

    @Test
    void hashPassword_correctPasswordAfterRegister_loginSucceeds() {
        String username = platform.registerPlayer("alice", "smith", "Secure99@");
        assertTrue(platform.login(username, "Secure99@"));
    }

    @Test
    void hashPassword_wrongPasswordAfterRegister_loginFails() {
        String username = platform.registerPlayer("alice", "smith", "Secure99@");
        assertFalse(platform.login(username, "WrongPass1!"));
    }

    @Test
    void hashPassword_differentPasswordsDoNotMatch_loginFails() {
        String username = platform.registerPlayer("bob", "jones", "Original1@");
        assertFalse(platform.login(username, "Different1@"));
    }

    // ---- generateUsername() ----

    @Test
    void generateUsername_validNames_returnsFirstInitialPlusLastNameLowercased() {
        assertEquals("jdoe", platform.generateUsername("john", "doe"));
    }

    @Test
    void generateUsername_nullFirstName_returnsNull() {
        assertNull(platform.generateUsername(null, "doe"));
    }

    @Test
    void generateUsername_nullLastName_returnsNull() {
        assertNull(platform.generateUsername("john", null));
    }

// ---- updateUsername() ----

    @Test
    void updateUsername_validNewUsername_returnsTrue() {
        String username = platform.registerPlayer("john", "doe", "Password1!");
        platform.login(username, "Password1!");
        String newName = "john" + System.currentTimeMillis();
        assertTrue(platform.updateUsername(username, newName));
    }

    @Test
    void updateUsername_updatesCurrentUser() {
        String username = platform.registerPlayer("john", "doe", "Password1!");
        platform.login(username, "Password1!");
        String newName = "john" + System.currentTimeMillis();
        platform.updateUsername(username, newName);
        assertEquals(newName, platform.getCurrentUser());
    }

    @Test
    void updateUsername_blankNewUsername_returnsFalse() {
        String username = platform.registerPlayer("john", "doe", "Password1!");
        platform.login(username, "Password1!");
        assertFalse(platform.updateUsername(username, ""));
    }

    @Test
    void updateUsername_nullNewUsername_returnsFalse() {
        String username = platform.registerPlayer("john", "doe", "Password1!");
        platform.login(username, "Password1!");
        assertFalse(platform.updateUsername(username, null));
    }

    @Test
    void updateUsername_duplicateUsername_returnsFalse() {
        String username = platform.registerPlayer("john", "doe", "Password1!");
        String alice = platform.registerPlayer("alice", "smith", "Secure99@");
        platform.login(username, "Password1!");
        assertFalse(platform.updateUsername(username, alice));
    }

    @Test
    void updateUsername_sameUsername_returnsTrue() {
        String username = platform.registerPlayer("john", "doe", "Password1!");
        platform.login(username, "Password1!");
        assertTrue(platform.updateUsername(username, username));
    }

    @Test
    void updateUsername_invalidFormat_returnsFalse() {
        String username = platform.registerPlayer("john", "doe", "Password1!");
        platform.login(username, "Password1!");
        assertFalse(platform.updateUsername(username, "invalid username"));
    }

    // ---- registerPlayer() ----

    @Test
    void registerPlayer_validCredentials_returnsUsername() {
        String username = platform.registerPlayer("morgan", "reyes", "Password1!");
        assertEquals("mreyes1", username);
    }

    @Test
    void registerPlayer_invalidPassword_returnsNull() {
        assertNull(platform.registerPlayer("morgan", "reyes", "weakpass"));
    }

    @Test
    void registerPlayer_invalidName_returnsNull() {
        assertNull(platform.registerPlayer("123invalid", "reyes", "Password1!"));
    }

    // ---- logout() ----

    @Test
    void logout_loggedInPlayer_isNoLongerLoggedIn() {
        String username = platform.registerPlayer("alice", "smith", "Secure99@");
        platform.login(username, "Secure99@");
        assertTrue(platform.isPlayerLoggedIn(username));

        platform.logout(username);

        assertFalse(platform.isPlayerLoggedIn(username));
    }

    @Test
    void logout_loggedInPlayer_clearsCurrentUser() {
        String username = platform.registerPlayer("alice", "smith", "Secure99@");
        platform.login(username, "Secure99@");
        assertEquals(username, platform.getCurrentUser());

        platform.logout(username);

        assertNull(platform.getCurrentUser());
    }

    @Test
    void logout_notLoggedInPlayer_doesNotThrow() {
        // Should silently return without error
        assertDoesNotThrow(() -> platform.logout("nonexistent_user"));
    }

    @Test
    void logout_alreadyLoggedOutPlayer_doesNotThrow() {
        String username = platform.registerPlayer("alice", "smith", "Secure99@");
        platform.login(username, "Secure99@");
        platform.logout(username);

        // Second logout should be a no-op
        assertDoesNotThrow(() -> platform.logout(username));
    }

    @Test
    void logout_playerCanLoginAgainAfterLogout() {
        String username = platform.registerPlayer("alice", "smith", "Secure99@");
        platform.login(username, "Secure99@");
        platform.logout(username);

        assertTrue(platform.login(username, "Secure99@"));
        assertTrue(platform.isPlayerLoggedIn(username));
    }

    @Test
    void logout_nonCurrentPlayer_doesNotClearCurrentUser() {
        String alice = platform.registerPlayer("alice", "smith", "Secure99@");
        String bob   = platform.registerPlayer("bob",   "jones", "Secure88@");
        // alice logs in last — she becomes currentUser
        platform.login(bob,   "Secure88@");
        platform.login(alice, "Secure99@");
        assertEquals(alice, platform.getCurrentUser());

        // Log out bob — alice is currentUser, should remain
        platform.logout(bob);

        assertEquals(alice, platform.getCurrentUser());
    }

    // ---- Player context transitions ----

    @Test
    void login_setsPlayerContextToDashboard() {
        String username = platform.registerPlayer("alice", "smith", "Secure99@");
        platform.login(username, "Secure99@");

        assertEquals(PlayerContext.DASHBOARD, platform.getPlayerContext(username));
    }

    @Test
    void logout_removesPlayerContext() {
        String username = platform.registerPlayer("alice", "smith", "Secure99@");
        platform.login(username, "Secure99@");
        platform.logout(username);

        assertNull(platform.getPlayerContext(username));
    }

    @Test
    void joinQueue_transitionsPlayerToInQueue() {
        String username = platform.registerPlayer("alice", "smith", "Secure99@");
        platform.login(username, "Secure99@");
        assertEquals(PlayerContext.DASHBOARD, platform.getPlayerContext(username));

        platform.joinQueue(username, "ConnectFour");

        assertEquals(PlayerContext.IN_QUEUE, platform.getPlayerContext(username));
    }

    @Test
    void leaveQueue_transitionsPlayerBackToDashboard() {
        String username = platform.registerPlayer("alice", "smith", "Secure99@");
        platform.login(username, "Secure99@");
        platform.joinQueue(username, "ConnectFour");
        assertEquals(PlayerContext.IN_QUEUE, platform.getPlayerContext(username));

        platform.leaveQueue(username);

        assertEquals(PlayerContext.DASHBOARD, platform.getPlayerContext(username));
    }

    @Test
    void logout_whileInQueue_removesContext() {
        String username = platform.registerPlayer("alice", "smith", "Secure99@");
        platform.login(username, "Secure99@");
        platform.joinQueue(username, "ConnectFour");

        platform.logout(username);

        assertNull(platform.getPlayerContext(username));
    }

    @Test
    void notLoggedIn_playerHasNoContext() {
        String username = platform.registerPlayer("alice", "smith", "Secure99@");
        // registered but never logged in
        assertNull(platform.getPlayerContext(username));
    }

    // ---- Avatar testing ----
    @Test
    void  avatarSavePersists() {
        PlatformCore core = new PlatformCore();

        String username = core.registerPlayer("test", "user", "Pass@1234");
        core.login(username, "Pass@1234");

        PlayerProfile profile = core.getCurrentProfile();
        profile.setAvatarPath("/images/avatar2.png");
        core.getUserRepository().save(profile);

        PlayerProfile reloaded = core.getPlayerProfile(username);

        assertNotNull(reloaded);
        assertEquals("/images/avatar2.png", reloaded.getAvatarPath());
    }

    @Test
    void avatarUpdateOverridesPrevious() {
        PlatformCore core = new PlatformCore();

        String username = core.registerPlayer("avatar", "user", "Pass@1234");
        core.login(username, "Pass@1234");

        PlayerProfile profile = core.getCurrentProfile();
        profile.setAvatarPath("/images/avatar1.png");
        core.getUserRepository().save(profile);

        profile.setAvatarPath("/images/avatar3.png");
        core.getUserRepository().save(profile);

        PlayerProfile reloaded = core.getPlayerProfile(username);

        assertNotNull(reloaded);
        assertEquals("/images/avatar3.png", reloaded.getAvatarPath());
    }

    @Test
    void avatarDefault() {
        PlatformCore core = new PlatformCore();
        String username = core.registerPlayer("Defaulttest", "Avatar",
                "Pass@1234");
        core.login(username, "Pass@1234");

        PlayerProfile profile = core.getCurrentProfile();
        assertEquals("/images/gameDinoLogo.png", profile.getAvatarPath());
    }

    @Test
    void avatarCanBeCleared() {
        PlatformCore core = new PlatformCore();
        String username = core.registerPlayer("user", "test", "Pass@1234");
        core.login(username, "Pass@1234");

        PlayerProfile profile = core.getCurrentProfile();
        profile.setAvatarPath("/images/avatar.png");
        core.getUserRepository().save(profile);

        profile.setAvatarPath(null);
        core.getUserRepository().save(profile);

        PlayerProfile reloaded = core.getPlayerProfile(username);
        assertNull(reloaded.getAvatarPath());
    }

    @Test
    void avatarIsIndependentPerUser() {
        PlatformCore core = new PlatformCore();

        String user1 = core.registerPlayer("Alice", "Smith", "Pass@1234");
        String user2 = core.registerPlayer("Bob", "Smith", "Pass@1234");

        assertNotNull(user1);
        assertNotNull(user2);

        core.login(user1, "Pass@1234");
        PlayerProfile profile1 = core.getCurrentProfile();
        assertNotNull(profile1);
        profile1.setAvatarPath("/images/avatar1.png");
        core.getUserRepository().save(profile1);

        core.login(user2, "Pass@1234");
        PlayerProfile profile2 = core.getCurrentProfile();
        assertNotNull(profile2);
        profile2.setAvatarPath("/images/avatar2.png");
        core.getUserRepository().save(profile2);

        assertEquals("/images/avatar1.png",
                core.getPlayerProfile(user1).getAvatarPath());
        assertEquals("/images/avatar2.png",
                core.getPlayerProfile(user2).getAvatarPath());
    }

    // ---- verifyPassword() ----

    @Test
    void verifyPassword_correctPassword_returnsTrue() {
        String username = platform.registerPlayer("john", "doe", "Password1!");
        assertTrue(platform.verifyPassword(username, "Password1!"));
    }

    @Test
    void verifyPassword_incorrectPassword_returnsFalse() {
        String username = platform.registerPlayer("john", "doe", "Password1");
        assertFalse(platform.verifyPassword(username, "WrongPass1"));
    }

    @Test
    void verifyPassword_nonExistentUser_returnsFalse() {
        assertFalse(platform.verifyPassword("nonexistent", "Password1"));
    }

    @Test
    void verifyPassword_nullPassword_ThrowsException() {
        String username = platform.registerPlayer("john", "doe", "Password1!");
        assertThrows(RuntimeException.class, () -> platform.verifyPassword(username, null));
    }

    // ---- updatePassword() ----

    @Test
    void updatePassword_validNewPassword_returnsTrue() {
        String username = platform.registerPlayer("john", "doe", "Password1");
        assertTrue(platform.updatePassword(username, "NewPassword2!"));
    }

    @Test
    void updatePassword_newPasswordTooShort_returnsFalse() {
        String username = platform.registerPlayer("john", "doe", "Password1!");
        assertFalse(platform.updatePassword(username, "short"));
    }

    @Test
    void updatePassword_nullPassword_returnsFalse() {
        String username = platform.registerPlayer("john", "doe", "Password1");
        assertFalse(platform.updatePassword(username, null));
    }

    @Test
    void updatePassword_noUppercase_returnsFalse() {
        String username = platform.registerPlayer("john", "doe", "Password1!");
        assertFalse(platform.updatePassword(username, "nouppercase1$"));
    }

    @Test
    void updatePassword_noSpecialChar_returnsFalse() {
        String username = platform.registerPlayer("john", "doe", "Password1");
        assertFalse(platform.updatePassword(username, "NoSpecial123"));
    }

    @Test
    void updatePassword_success_LoginWithNewPass() {
        String username = platform.registerPlayer("john", "doe", "Password1!");
        platform.updatePassword(username, "NewPass2#");
        platform.login(username,"NewPass2#");
        assertEquals(username, platform.getCurrentUser());
    }

    // ---- friend system ----

    @Test
    void sendFriendRequest_validRequest_returnsNull() {
        String alice = platform.registerPlayer("alice", "smith", "Password1!");
        String bob = platform.registerPlayer("bob", "smith", "Password2!");
        platform.login(alice, "Password1!");
        assertNull(platform.sendFriendRequest(bob)); // null  implies SUCCESS (according to method)
    }

    @Test
    void sendFriendRequest_toSelf_returnsNotNull() {
        String alice = platform.registerPlayer("alice", "smith", "Password1!");
        platform.login(alice, "Password1!");
        assertNotNull(platform.sendFriendRequest(alice));
    }

    @Test
    void acceptFriendRequest_validRequest_returnsTrue() {
        String alice = platform.registerPlayer("alice", "smith", "Password1!");
        String bob = platform.registerPlayer("bob", "smith", "Password2!");
        platform.login(alice, "Password1!");
        platform.sendFriendRequest(bob);
        platform.logout(alice);
        platform.login(bob, "Password2!");
        assertTrue(platform.acceptFriendRequest(alice));
        assertTrue(platform.isFriend(alice));
    }

    @Test
    void declineFriendRequest_validRequest_returnsTrue() {
        String alice = platform.registerPlayer("alice", "smith", "Password1@");
        String bob = platform.registerPlayer("bob", "smith", "Password2!");
        platform.login(alice, "Password1@");
        platform.sendFriendRequest(bob);
        platform.logout(alice);
        platform.login(bob, "Password2!");
        assertTrue(platform.declineFriendRequest(alice));
        assertFalse(platform.isFriend(alice));
    }

    @Test
    void removeFriend_existingFriend_returnsTrue() {
        String alice = platform.registerPlayer("alice", "smith", "Password1@");
        String bob = platform.registerPlayer("bob", "smith", "Password2!");
        platform.login(alice, "Password1@");
        platform.sendFriendRequest(bob);
        platform.logout(alice);
        platform.login(bob, "Password2!");
        platform.acceptFriendRequest(alice);
        assertTrue(platform.removeFriend(alice));
        assertFalse(platform.isFriend(alice));
    }




}
