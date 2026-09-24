package io.github.jy41319.bandtrail;

import android.location.Location;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class StoreTest {
    private Store store;
    @Before public void setUp() { store=new Store(InstrumentationRegistry.getInstrumentation().getTargetContext()); store.p.edit().clear().commit(); }
    @After public void tearDown() { store.p.edit().clear().commit(); }
    @Test public void testNewObservationWithoutLocationPreservesEarlierPointWithItsOwnTimestamp() {
        Location fix=new Location("gps"); fix.setLatitude(31.2); fix.setLongitude(121.4); fix.setAccuracy(15);
        fix.setElapsedRealtimeNanos(90_000_000_000L); fix.setTime(900_000);
        store.seen(1_000_000,100_000,-60,fix);
        store.seen(2_000_000,200_000,-80,null);
        assertEquals(2_000_000,store.p.getLong("seen",0)); assertEquals(1_000_000,store.p.getLong("pointSeen",0));
        assertEquals("31.2",store.p.getString("lat",""));
    }
    @Test public void testExportDoesNotContainIdentifiersOrCoordinates() {
        store.select("secret device","AA:BB:CC:DD:EE:FF");
        store.p.edit().putString("lat","31.123456").putString("lon","121.987654").commit();
        store.event("开启守护");
        String export=store.diagnostic();
        assertFalse(export.contains("secret device")); assertFalse(export.contains("AA:BB")); assertFalse(export.contains("31.123456"));
    }
    @Test public void selectingSameDevicePreservesLastLocationAndPreferences() {
        store.select("旧名称", "AA:BB:CC:DD:EE:FF");
        store.p.edit().putLong("seen",12345).putString("lat","31.2").putString("lon","121.4").putInt("timeout",120).commit();
        store.select("系统保存名称", "AA:BB:CC:DD:EE:FF");
        assertEquals("系统保存名称", store.name()); assertEquals(12345,store.p.getLong("seen",0));
        assertTrue(store.hasPoint()); assertEquals(120,store.p.getInt("timeout",0));
    }
    @Test public void testDiagnosticsAreBounded() {
        for(int i=0;i<150;i++) store.event("事件 "+i);
        assertEquals(100,store.events().split("\n").length);
    }
}
