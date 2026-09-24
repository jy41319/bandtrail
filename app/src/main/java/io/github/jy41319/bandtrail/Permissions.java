package io.github.jy41319.bandtrail;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.ArrayList;
import java.util.List;

final class Permissions {
    static boolean has(Context c, String permission) { return c.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED; }
    static boolean canReadDevices(Context c) {
        return Build.VERSION.SDK_INT < 31 || has(c, Manifest.permission.BLUETOOTH_CONNECT);
    }
    static void requestDeviceRecords(Activity a) {
        if (Build.VERSION.SDK_INT >= 31) a.requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN}, 11);
    }
    static boolean ready(Context c) {
        return has(c, Manifest.permission.ACCESS_FINE_LOCATION) && (Build.VERSION.SDK_INT < 31 ||
            (has(c, Manifest.permission.BLUETOOTH_SCAN) && has(c, Manifest.permission.BLUETOOTH_CONNECT)));
    }
    static void request(Activity a) {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= 31) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        a.requestPermissions(permissions.toArray(new String[0]), 10);
    }
}
