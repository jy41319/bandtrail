package io.github.jy41319.bandtrail;

import android.location.Location;
import android.test.AndroidTestCase;

@SuppressWarnings("deprecation")
public class StoreTest extends AndroidTestCase {
    private Store store;
    @Override protected void setUp() throws Exception { super.setUp(); store=new Store(getContext()); store.p.edit().clear().commit(); }
    @Override protected void tearDown() throws Exception { store.p.edit().clear().commit(); super.tearDown(); }
    public void testNewObservationWithoutLocationPreservesEarlierPointWithItsOwnTimestamp() {
        Location fix=new Location("gps"); fix.setLatitude(31.2); fix.setLongitude(121.4); fix.setAccuracy(15);
        fix.setElapsedRealtimeNanos(90_000_000_000L); fix.setTime(900_000);
        store.seen(1_000_000,100_000,-60,fix);
        store.seen(2_000_000,200_000,-80,null);
        assertEquals(2_000_000,store.p.getLong("seen",0)); assertEquals(1_000_000,store.p.getLong("pointSeen",0));
        assertEquals("31.2",store.p.getString("lat",""));
    }
    public void testExportDoesNotContainIdentifiersOrCoordinates() {
        store.select("secret device","AA:BB:CC:DD:EE:FF");
        store.p.edit().putString("lat","31.123456").putString("lon","121.987654").commit();
        store.event("开启守护");
        String export=store.diagnostic();
        assertFalse(export.contains("secret device")); assertFalse(export.contains("AA:BB")); assertFalse(export.contains("31.123456"));
    }
    public void testDiagnosticsAreBounded() {
        for(int i=0;i<150;i++) store.event("事件 "+i);
        assertEquals(100,store.events().split("\n").length);
    }
}
