package ca.ucalgary.seng300.core;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ca.ucalgary.seng300.matchmaking.Party;

/**
 * Comprehensive unit tests for the Party class.
 * Aims for maximum code coverage including edge cases.
 */
public class PartyTest {

    private Party party;
    private static final String PARTY_ID = "party-123";
    private static final String HOST_ID = "player-1";
    private static final String MEMBER_ID_2 = "player-2";
    private static final String MEMBER_ID_3 = "player-3";
    private static final String MEMBER_ID_4 = "player-4";
    private static final String NON_MEMBER_ID = "player-999";
    private static final int MAX_SIZE = 3;

    @BeforeEach
    void setUp() {
        party = new Party(PARTY_ID, HOST_ID, MAX_SIZE);
    }

    // ============== Constructor Tests ==============

    @Test
    void testConstructorInitializesPartyCorrectly() {
        assertEquals(PARTY_ID, party.getPartyId());
        assertEquals(HOST_ID, party.getHostId());
        assertEquals(MAX_SIZE, party.getMaxSize());
    }

    @Test
    void testConstructorAddsHostToMembers() {
        List<String> members = party.getMembers();
        assertEquals(1, members.size());
        assertTrue(members.contains(HOST_ID));
    }

    @Test
    void testConstructorWithDifferentMaxSizes() {
        Party smallParty = new Party("party-small", "host", 1);
        assertEquals(1, smallParty.getMaxSize());

        Party largeParty = new Party("party-large", "host", 10);
        assertEquals(10, largeParty.getMaxSize());
    }

    // ============== addMember Tests ==============

    @Test
    void testAddMemberSucceedsWhenPartyNotFull() {
        assertTrue(party.addMember(MEMBER_ID_2));
        assertTrue(party.getMembers().contains(MEMBER_ID_2));
    }

    @Test
    void testAddMultipleMembers() {
        assertTrue(party.addMember(MEMBER_ID_2));
        assertTrue(party.addMember(MEMBER_ID_3));

        List<String> members = party.getMembers();
        assertEquals(3, members.size());
        assertTrue(members.contains(HOST_ID));
        assertTrue(members.contains(MEMBER_ID_2));
        assertTrue(members.contains(MEMBER_ID_3));
    }

    @Test
    void testAddMemberFailsWhenPartyFull() {
        party.addMember(MEMBER_ID_2);
        party.addMember(MEMBER_ID_3);

        // Party is now full (3/3)
        assertFalse(party.addMember(MEMBER_ID_4));
    }

    @Test
    void testAddMemberFailsWhenAlreadyMember() {
        assertTrue(party.addMember(MEMBER_ID_2));
        assertFalse(party.addMember(MEMBER_ID_2)); // Try to add same member again
    }

    @Test
    void testAddHostAsNewMemberFails() {
        assertFalse(party.addMember(HOST_ID)); // Host is already a member
    }

    @Test
    void testAddMemberWhenAtMaxCapacity() {
        Party singleParty = new Party("party-1", "host", 1);
        assertTrue(singleParty.isFull()); // Host fills the party

        assertFalse(singleParty.addMember(MEMBER_ID_2));
        assertEquals(1, singleParty.getMembers().size());
    }

    // ============== removeMember Tests ==============

    @Test
    void testRemoveMemberSucceeds() {
        party.addMember(MEMBER_ID_2);
        assertTrue(party.removeMember(MEMBER_ID_2));
        assertFalse(party.getMembers().contains(MEMBER_ID_2));
    }

    @Test
    void testRemoveHostMember() {
        assertTrue(party.removeMember(HOST_ID));
        assertFalse(party.getMembers().contains(HOST_ID));
        assertEquals(0, party.getMembers().size());
    }

    @Test
    void testRemoveNonExistentMemberFails() {
        assertFalse(party.removeMember(NON_MEMBER_ID));
        assertEquals(1, party.getMembers().size());
    }

    @Test
    void testRemoveFromEmptyParty() {
        party.removeMember(HOST_ID); // Remove the only member
        assertFalse(party.removeMember(MEMBER_ID_2));
    }

    @Test
    void testRemoveMultipleMembers() {
        party.addMember(MEMBER_ID_2);
        party.addMember(MEMBER_ID_3);

        assertTrue(party.removeMember(MEMBER_ID_2));
        assertTrue(party.removeMember(MEMBER_ID_3));

        List<String> members = party.getMembers();
        assertEquals(1, members.size());
        assertTrue(members.contains(HOST_ID));
    }

    @Test
    void testRemoveMemberThenAddAgain() {
        party.addMember(MEMBER_ID_2);
        party.removeMember(MEMBER_ID_2);

        // Should be able to add them back
        assertTrue(party.addMember(MEMBER_ID_2));
        assertTrue(party.getMembers().contains(MEMBER_ID_2));
    }

    // ============== isFull Tests ==============

    @Test
    void testIsFullReturnsFalseWhenNotFull() {
        assertFalse(party.isFull()); // 1/3
        party.addMember(MEMBER_ID_2);
        assertFalse(party.isFull()); // 2/3
    }

    @Test
    void testIsFullReturnsTrueWhenFull() {
        party.addMember(MEMBER_ID_2);
        party.addMember(MEMBER_ID_3);
        assertTrue(party.isFull()); // 3/3
    }

    @Test
    void testIsFullAfterRemovingMember() {
        party.addMember(MEMBER_ID_2);
        party.addMember(MEMBER_ID_3);
        assertTrue(party.isFull());

        party.removeMember(MEMBER_ID_3);
        assertFalse(party.isFull()); // 2/3
    }

    @Test
    void testIsFullWithMaxSizeOne() {
        Party singleParty = new Party("party-1", "host", 1);
        assertTrue(singleParty.isFull());
    }

    @Test
    void testIsFullWithLargeMaxSize() {
        Party largeParty = new Party("party-large", "host", 100);
        assertFalse(largeParty.isFull());

        for (int i = 0; i < 99; i++) {
            largeParty.addMember("player-" + i);
        }
        assertTrue(largeParty.isFull());
    }

    // ============== getMembers Tests ==============

    @Test
    void testGetMembersReturnsDefensiveCopy() {
        List<String> members1 = party.getMembers();
        members1.add("fake-player"); // Modify the returned list

        List<String> members2 = party.getMembers();
        assertEquals(1, members2.size()); // Original should be unchanged
        assertFalse(members2.contains("fake-player"));
    }

    @Test
    void testGetMembersIncludesHost() {
        assertTrue(party.getMembers().contains(HOST_ID));
    }

    @Test
    void testGetMembersReturnsCorrectCount() {
        assertEquals(1, party.getMembers().size());

        party.addMember(MEMBER_ID_2);
        assertEquals(2, party.getMembers().size());

        party.addMember(MEMBER_ID_3);
        assertEquals(3, party.getMembers().size());
    }

    @Test
    void testGetMembersAfterRemoval() {
        party.addMember(MEMBER_ID_2);
        assertEquals(2, party.getMembers().size());

        party.removeMember(MEMBER_ID_2);
        assertEquals(1, party.getMembers().size());
        assertFalse(party.getMembers().contains(MEMBER_ID_2));
    }

    // ============== Getter Tests ==============

    @Test
    void testGetPartyId() {
        assertEquals(PARTY_ID, party.getPartyId());
    }

    @Test
    void testGetHostId() {
        assertEquals(HOST_ID, party.getHostId());
    }

    @Test
    void testGetMaxSize() {
        assertEquals(MAX_SIZE, party.getMaxSize());
    }

    @Test
    void testGettersWithDifferentValues() {
        String customPartyId = "custom-party";
        String customHostId = "custom-host";
        int customMaxSize = 5;

        Party customParty = new Party(customPartyId, customHostId, customMaxSize);

        assertEquals(customPartyId, customParty.getPartyId());
        assertEquals(customHostId, customParty.getHostId());
        assertEquals(customMaxSize, customParty.getMaxSize());
    }

    // ============== Integration Tests ==============

    @Test
    void testPartyLifecycle() {
        // Create party with host
        assertEquals(1, party.getMembers().size());
        assertFalse(party.isFull());

        // Add members
        assertTrue(party.addMember(MEMBER_ID_2));
        assertTrue(party.addMember(MEMBER_ID_3));
        assertTrue(party.isFull());
        assertEquals(3, party.getMembers().size());

        // Try to add when full
        assertFalse(party.addMember(MEMBER_ID_4));

        // Remove a member
        assertTrue(party.removeMember(MEMBER_ID_2));
        assertFalse(party.isFull());
        assertEquals(2, party.getMembers().size());

        // Add another member to fill again
        assertTrue(party.addMember(MEMBER_ID_4));
        assertTrue(party.isFull());
    }

    @Test
    void testPartyWithDuplicateAddAttempts() {
        party.addMember(MEMBER_ID_2);
        assertFalse(party.addMember(MEMBER_ID_2));
        assertFalse(party.addMember(MEMBER_ID_2));

        assertEquals(2, party.getMembers().size());
    }

    @Test
    void testEmptyPartyAfterRemovingAllMembers() {
        party.removeMember(HOST_ID);
        assertEquals(0, party.getMembers().size());
        assertFalse(party.isFull());

        assertTrue(party.addMember(MEMBER_ID_2));
        assertEquals(1, party.getMembers().size());
    }

    @Test
    void testPartyStateConsistency() {
        party.addMember(MEMBER_ID_2);
        party.addMember(MEMBER_ID_3);

        // Verify state is consistent
        List<String> members = party.getMembers();
        assertEquals(3, members.size());
        assertEquals(party.getMaxSize(), members.size());
        assertTrue(party.isFull());

        // All expected members are present
        assertTrue(members.contains(HOST_ID));
        assertTrue(members.contains(MEMBER_ID_2));
        assertTrue(members.contains(MEMBER_ID_3));
    }
}