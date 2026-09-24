package io.github.jy41319.bandtrail;

/** Pure state machine. All durations use monotonic elapsed time, never wall time. */
public final class GuardEngine {
    public enum State { WAITING, NEARBY, MISSING, INTERRUPTED, PAUSED }
    private final long timeout;
    private long lastSeen = -1;
    private State state = State.WAITING;
    public GuardEngine(long timeoutMillis) { timeout = Math.max(15000, timeoutMillis); }
    public State state() { return state; }
    public boolean seen(long elapsed) {
        if (state == State.PAUSED || state == State.INTERRUPTED || elapsed < lastSeen) return false;
        lastSeen = elapsed;
        state = State.NEARBY;
        return true;
    }
    /** Returns true once per disappearance, only after an observation in this session. */
    public boolean tick(long elapsed) {
        if (state == State.NEARBY && lastSeen >= 0 && elapsed - lastSeen >= timeout) {
            state = State.MISSING;
            return true;
        }
        return false;
    }
    public void interrupt() { state = State.INTERRUPTED; }
    public void pause() { state = State.PAUSED; }
}
