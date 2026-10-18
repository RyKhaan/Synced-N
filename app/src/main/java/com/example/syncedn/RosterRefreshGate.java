package com.example.syncedn;

/** One in-flight refresh per active team; late callbacks cannot finish newer requests. */
public final class RosterRefreshGate {
    private long generation;
    private long active = -1;
    private String team = "";

    public synchronized long begin(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.isEmpty() || (active != -1 && team.equals(normalized))) return -1;
        team = normalized;
        active = ++generation;
        return active;
    }

    public synchronized boolean finish(long ticket) {
        if (ticket == -1 || active != ticket) return false;
        active = -1;
        return true;
    }
}
