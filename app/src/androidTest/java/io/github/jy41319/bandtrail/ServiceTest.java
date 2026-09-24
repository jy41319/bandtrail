package io.github.jy41319.bandtrail;

import android.Manifest;
import android.app.Instrumentation;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

@android.annotation.SuppressLint("MissingPermission")
@RunWith(AndroidJUnit4.class)
public class ServiceTest {
    @Test public void userStartedForegroundSessionWaitsForFreshEvidenceAndStopsCleanly() throws Exception {
        Instrumentation i=InstrumentationRegistry.getInstrumentation();
        Context context=i.getTargetContext();
        i.getUiAutomation().grantRuntimePermission(context.getPackageName(),Manifest.permission.ACCESS_COARSE_LOCATION);
        i.getUiAutomation().grantRuntimePermission(context.getPackageName(),Manifest.permission.ACCESS_FINE_LOCATION);
        if(Build.VERSION.SDK_INT>=31) {
            i.getUiAutomation().grantRuntimePermission(context.getPackageName(),Manifest.permission.BLUETOOTH_SCAN);
            i.getUiAutomation().grantRuntimePermission(context.getPackageName(),Manifest.permission.BLUETOOTH_CONNECT);
        }
        if(Build.VERSION.SDK_INT>=33) i.getUiAutomation().grantRuntimePermission(context.getPackageName(),Manifest.permission.POST_NOTIFICATIONS);
        BluetoothManager manager=context.getSystemService(BluetoothManager.class);
        assumeTrue("This lifecycle check requires an enabled Bluetooth adapter",manager!=null && manager.getAdapter()!=null && manager.getAdapter().isEnabled());
        Store store=new Store(context);
        store.select("Synthetic test target","00:11:22:33:44:55");
        store.seen(1000,1000,-65,null);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a->a.startForegroundService(new Intent(a,GuardService.class)));
            for(int retry=0;retry<30 && !GuardService.running;retry++) Thread.sleep(100);
            assertTrue("Foreground service should start from the visible activity: "+store.p.getString("detail",""),GuardService.running);
            assertEquals("WAITING",store.p.getString("state",""));
            scenario.onActivity(a->a.startService(new Intent(a,GuardService.class).setAction(GuardService.STOP)));
            for(int retry=0;retry<30 && GuardService.running;retry++) Thread.sleep(100);
            assertFalse(GuardService.running);
            assertEquals("PAUSED",store.p.getString("state",""));
            assertEquals(1000,store.p.getLong("seen",0));
        } finally {
            context.stopService(new Intent(context,GuardService.class));
            i.waitForIdleSync();
            store.p.edit().clear().commit();
        }
    }
}
