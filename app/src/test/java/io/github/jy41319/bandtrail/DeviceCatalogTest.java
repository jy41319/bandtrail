package io.github.jy41319.bandtrail;

import org.junit.Test;
import static org.junit.Assert.*;

public class DeviceCatalogTest {
    private static final String ADDRESS="AA:BB:CC:DD:EE:FF";
    @Test public void unnamedBroadcastKeepsSystemAliasAndMergesSameDevice() {
        DeviceCatalog c=new DeviceCatalog(); c.system(ADDRESS,"我的手环 11","Xiaomi",true,false);
        c.scan(ADDRESS,null,null,null,-62,100);
        assertEquals(1,c.sorted().size()); assertEquals("我的手环 11",c.get(ADDRESS).name()); assertTrue(c.get(ADDRESS).bonded);
        assertEquals(100,c.get(ADDRESS).observed);
    }
    @Test public void aliasWinsOverCachedAndAdvertisingNames() {
        DeviceCatalog c=new DeviceCatalog(); c.system(ADDRESS,"自定义名称","系统缓存",true,true);
        c.scan(ADDRESS,null,"系统缓存","广播名",-50,10);
        assertEquals("自定义名称",c.get(ADDRESS).name());
    }
    @Test public void configuredDeviceDoesNotCountAsLiveObservation() {
        DeviceCatalog c=new DeviceCatalog(); DeviceCatalog.Entry e=c.system(ADDRESS,null,"Smart Band",true,true);
        assertEquals(-1,e.observed); assertTrue(e.saved());
    }
    @Test public void priorityIsConnectedThenBondedThenNamedThenUnknown() {
        DeviceCatalog c=new DeviceCatalog(); c.scan("00:11:22:33:44:01",null,null,null,-20,10);
        c.scan("00:11:22:33:44:02",null,null,"AAA",-30,10);
        c.system("00:11:22:33:44:03",null,"ZZZ",true,false);
        c.system("00:11:22:33:44:04",null,"ZZZ",false,true);
        assertEquals("00:11:22:33:44:04",c.sorted().get(0).address);
        assertEquals("00:11:22:33:44:03",c.sorted().get(1).address);
        assertEquals("00:11:22:33:44:02",c.sorted().get(2).address);
    }
    @Test public void noisyBroadcastsCannotHideSystemRecords() {
        DeviceCatalog c=new DeviceCatalog();
        for(int i=1;i<=120;i++) c.scan(String.format("00:11:22:33:44:%02X",i),null,null,null,-50,i);
        c.system(ADDRESS,null,"手环",true,false);
        assertEquals(101,c.sorted().size()); assertEquals(ADDRESS,c.sorted().get(0).address);
    }
    @Test public void manualAddressSupportsCopyFormatsAndRejectsInvalidValues() {
        assertEquals(ADDRESS,DeviceCatalog.normalize(" aa-bb-cc-dd-ee-ff "));
        assertEquals(ADDRESS,DeviceCatalog.normalize("aabbccddeeff"));
        assertNull(DeviceCatalog.normalize("手环11")); assertNull(DeviceCatalog.normalize("AA:BB:CC:DD:EE:GG"));
        assertNull(DeviceCatalog.normalize("00:00:00:00:00:00")); assertNull(DeviceCatalog.normalize("FF:FF:FF:FF:FF:FF"));
    }
    @Test public void sameNameDoesNotMergeDifferentDevices() {
        DeviceCatalog c=new DeviceCatalog(); c.system(ADDRESS,null,"手环",true,false);
        c.scan("00:11:22:33:44:01",null,null,"手环",-20,10);
        assertEquals(2,c.sorted().size());
    }
    @Test public void delayedScanDoesNotMoveLastSignalBackwards() {
        DeviceCatalog c=new DeviceCatalog(); c.scan(ADDRESS,null,null,"Band",-60,100);
        c.scan(ADDRESS,null,null,null,-90,50); assertEquals(100,c.get(ADDRESS).observed); assertEquals(-60,c.get(ADDRESS).rssi);
    }
}
