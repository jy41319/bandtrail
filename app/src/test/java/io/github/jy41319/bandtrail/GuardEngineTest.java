package io.github.jy41319.bandtrail;
import org.junit.Test;
import static org.junit.Assert.*;

public class GuardEngineTest {
    @Test public void neverSeenDoesNotTriggerLoss() {
        GuardEngine e=new GuardEngine(60_000); assertFalse(e.tick(9_000_000)); assertEquals(GuardEngine.State.WAITING,e.state());
    }
    @Test public void lossRequiresTimeoutAndFiresOnlyOnce() {
        GuardEngine e=new GuardEngine(60_000); e.seen(1_000);
        assertFalse(e.tick(60_999)); assertTrue(e.tick(61_000)); assertFalse(e.tick(99_000));
    }
    @Test public void reappearanceRearmsAlert() {
        GuardEngine e=new GuardEngine(30_000); e.seen(1_000); assertTrue(e.tick(31_000));
        e.seen(40_000); assertEquals(GuardEngine.State.NEARBY,e.state()); assertTrue(e.tick(70_000));
    }
    @Test public void permissionsOrBluetoothFailureIsNotALoss() {
        GuardEngine e=new GuardEngine(30_000); e.seen(0); e.interrupt();
        assertFalse(e.tick(100_000)); assertFalse(e.seen(100_001)); assertEquals(GuardEngine.State.INTERRUPTED,e.state());
    }
    @Test public void pauseSuppressesQueuedCallbacks() {
        GuardEngine e=new GuardEngine(30_000); e.seen(1000); e.pause(); assertFalse(e.seen(2000)); assertFalse(e.tick(100_000));
    }
    @Test public void delayedPacketCannotMoveClockBackwards() {
        GuardEngine e=new GuardEngine(30_000); e.seen(50_000); assertFalse(e.seen(40_000)); assertFalse(e.tick(79_999)); assertTrue(e.tick(80_000));
    }
    @Test public void newSessionDoesNotInheritHistoricalPresence() {
        GuardEngine old=new GuardEngine(30_000); old.seen(1000); old.interrupt();
        GuardEngine restarted=new GuardEngine(30_000); assertFalse(restarted.tick(1_000_000));
    }
}
