package io.github.jy41319.bandtrail;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class UiTest {
    private static TextView find(View view,String value) {
        if(view instanceof TextView && ((TextView)view).getText().toString().equals(value)) return (TextView)view;
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
            TextView found=find(((ViewGroup)view).getChildAt(i),value); if(found!=null) return found;
        }
        return null;
    }
    private void capture(String file) throws Exception {
        Instrumentation i=InstrumentationRegistry.getInstrumentation(); i.waitForIdleSync();
        Thread.sleep(500);
        Bitmap image=i.getUiAutomation().takeScreenshot(); assertNotNull(image);
        try(FileOutputStream out=i.getTargetContext().openFileOutput(file,0)) { image.compress(Bitmap.CompressFormat.PNG,100,out); }
        image.recycle();
    }
    @Test public void emptyStateAndSettingsAreUsable() throws Exception {
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        new Store(instrumentation.getTargetContext()).p.edit().clear().commit();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a->{
                assertNotNull(find(a.getWindow().getDecorView(),"添加我的手环"));
                TextView map=find(a.getWindow().getDecorView(),"在地图中查看");
                assertNotNull(map); assertFalse(map.isEnabled());
            });
            capture("home.png");
            scenario.onActivity(a->find(a.getWindow().getDecorView(),"提醒与后台设置").performClick());
            capture("settings.png");
            instrumentation.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
        }
    }
}
