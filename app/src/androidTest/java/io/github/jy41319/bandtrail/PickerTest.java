package io.github.jy41319.bandtrail;

import android.Manifest;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PickerTest {
    private TextView find(View v,String text) {
        if(v instanceof TextView && ((TextView)v).getText().toString().startsWith(text)) return (TextView)v;
        if(v instanceof ViewGroup) for(int n=0;n<((ViewGroup)v).getChildCount();n++) {
            TextView result=find(((ViewGroup)v).getChildAt(n),text); if(result!=null) return result;
        }
        return null;
    }
    @Test public void savedDeviceCanBeChosenWithoutAnAdvertisementAndAnonymousRowsAreCollapsed() throws Exception {
        Instrumentation i=InstrumentationRegistry.getInstrumentation();
        Store store=new Store(i.getTargetContext()); store.p.edit().clear().commit();
        if(Build.VERSION.SDK_INT>=31) i.getUiAutomation().grantRuntimePermission(i.getTargetContext().getPackageName(),Manifest.permission.BLUETOOTH_CONNECT);
        // Only the test supplies synthetic system records. There is no production demo mode.
        try(ActivityScenario<ScanActivity> scenario=ActivityScenario.launch(ScanActivity.class)) {
            scenario.onActivity(a->{
                try {
                    DeviceCatalog c=new DeviceCatalog();
                    c.system("AA:BB:CC:DD:EE:11","测试手环（模拟系统记录）","Smart Band",true,true);
                    c.scan("AA:BB:CC:DD:EE:22",null,null,null,-50,android.os.SystemClock.elapsedRealtime());
                    Field field=ScanActivity.class.getDeclaredField("catalog");field.setAccessible(true);field.set(a,c);
                    Method render=ScanActivity.class.getDeclaredMethod("renderPicker");render.setAccessible(true);render.invoke(a);
                    Field hint=ScanActivity.class.getDeclaredField("savedHint");hint.setAccessible(true);
                    ((TextView)hint.get(a)).setText("界面测试用模拟记录：可以直接选择，无需等待广播。");
                    View root=a.getWindow().getDecorView();
                    TextView saved=find(root,"测试手环（模拟系统记录）"); assertNotNull(saved);
                    assertTrue(saved.getText().toString().contains("尚未检测到广播"));
                    TextView unknown=find(root,"未命名设备 · EE:22"); assertNotNull(unknown); assertFalse(unknown.isShown());
                    assertNotNull(find(root,"查看未命名设备（1）"));
                } catch(ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            i.waitForIdleSync(); Thread.sleep(500);
            Bitmap image=i.getUiAutomation().takeScreenshot(); assertNotNull(image);
            try(FileOutputStream out=i.getTargetContext().openFileOutput("picker-system-fixture.png",0)) { image.compress(Bitmap.CompressFormat.PNG,100,out); }
            image.recycle();
            scenario.onActivity(a->find(a.getWindow().getDecorView(),"测试手环（模拟系统记录）").performClick());
            i.waitForIdleSync(); Thread.sleep(250);
            AccessibilityNodeInfo root=i.getUiAutomation().getRootInActiveWindow(); assertNotNull(root);
            java.util.List<AccessibilityNodeInfo> buttons=root.findAccessibilityNodeInfosByText("添加并返回");
            assertFalse(buttons.isEmpty()); assertTrue(buttons.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK));
            i.waitForIdleSync();
            for(int retry=0;retry<20 && store.address().isEmpty();retry++) Thread.sleep(100);
            assertEquals("AA:BB:CC:DD:EE:11",store.address());
            assertEquals(0,store.p.getLong("seen",0)); assertFalse(store.hasPoint());
        } finally { store.p.edit().clear().commit(); }
    }
}
