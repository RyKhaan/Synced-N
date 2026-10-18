package com.example.syncedn;

import org.junit.Test;
import static org.junit.Assert.*;

public class RosterRefreshGateTest {
    @Test public void overlappingPageVisitsShareOneRequest() {
        RosterRefreshGate gate = new RosterRefreshGate();
        long first = gate.begin("ABC123");
        assertTrue(first > 0);
        for (int i = 0; i < 100; i++) assertEquals(-1L, gate.begin(" abc123 "));
        assertTrue(gate.finish(first));
        assertFalse(gate.finish(first));
        assertTrue(gate.begin("ABC123") > first);
    }

    @Test public void oldTeamResponseCannotFinishNewTeamRequest() {
        RosterRefreshGate gate = new RosterRefreshGate();
        long oldTeam = gate.begin("OLD");
        long newTeam = gate.begin("NEW");
        assertFalse(gate.finish(oldTeam));
        assertEquals(-1L, gate.begin("NEW"));
        assertTrue(gate.finish(newTeam));
        assertTrue(gate.begin("NEW") > newTeam);
    }

    @Test public void emptyTeamAndInvalidTicketsAreIgnored() {
        RosterRefreshGate gate = new RosterRefreshGate();
        assertEquals(-1L, gate.begin(null));
        assertEquals(-1L, gate.begin(" "));
        assertFalse(gate.finish(-1));
        long request = gate.begin("TEAM");
        assertFalse(gate.finish(request + 1));
        assertTrue(gate.finish(request));
    }
}
