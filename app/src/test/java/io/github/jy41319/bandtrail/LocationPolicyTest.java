package io.github.jy41319.bandtrail;
import org.junit.Test;
import static org.junit.Assert.*;

public class LocationPolicyTest {
    @Test public void freshFixIsUsable() { assertTrue(LocationPolicy.usable(100_000,90_000,25,31.2,121.4)); }
    @Test public void staleFixCannotMasqueradeAsCurrentPosition() { assertFalse(LocationPolicy.usable(100_001,10_000,25,31,121)); }
    @Test public void fixAfterObservationMustNeverOverwriteLostLocation() { assertFalse(LocationPolicy.usable(100_000,100_001,25,31,121)); }
    @Test public void rejectsInvalidCoordinatesAndAccuracy() {
        assertFalse(LocationPolicy.usable(100,90,Float.NaN,31,121));
        assertFalse(LocationPolicy.usable(100,90,0,31,121));
        assertFalse(LocationPolicy.usable(100,90,1001,31,121));
        assertFalse(LocationPolicy.usable(100,90,25,91,121));
        assertFalse(LocationPolicy.usable(100,90,25,31,181));
        assertFalse(LocationPolicy.usable(100,90,25,Double.NaN,121));
    }
    @Test public void excludesDelayedAndFutureScanResults() {
        assertTrue(LocationPolicy.staleObservation(100_000,84_999));
        assertTrue(LocationPolicy.staleObservation(100_000,100_001));
        assertFalse(LocationPolicy.staleObservation(100_000,99_999));
    }
}
