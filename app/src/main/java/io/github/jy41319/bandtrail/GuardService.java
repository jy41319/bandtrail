package io.github.jy41319.bandtrail;

import android.annotation.SuppressLint;
import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.location.*;
import android.os.*;
import java.util.Collections;

/** User-started foreground session. Never connects to or writes to the wearable. */
@SuppressLint("MissingPermission")
public final class GuardService extends Service {
    static final String STOP = "stop";
    static volatile boolean running;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Store store;
    private GuardEngine engine;
    private BluetoothLeScanner scanner;
    private LocationManager locations;
    private Location fix;
    private boolean registered, scanning, ending;
    private long started, lastTick, lastWrite, lastDiagnostic, maxGap, lastObserved = -1;
    private int samples;
    private final LocationListener locationListener = new LocationListener() {
        @Override public void onLocationChanged(Location location) {
            if (fix == null || location.getElapsedRealtimeNanos() > fix.getElapsedRealtimeNanos()) fix = location;
        }
        @Override public void onProviderEnabled(String provider) {}
        @Override public void onProviderDisabled(String provider) {
            if (locations != null && !locations.isLocationEnabled()) fail("系统定位已关闭，请重新开启守护");
        }
        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    };
    private final BroadcastReceiver bluetoothState = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) {
            if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF) != BluetoothAdapter.STATE_ON)
                fail("蓝牙已关闭，监测中断");
        }
    };
    private final ScanCallback callback = new ScanCallback() {
        @Override public void onScanResult(int type, ScanResult result) {
            if (ending || engine == null) return;
            long now = SystemClock.elapsedRealtime();
            long observed = result.getTimestampNanos() / 1_000_000;
            if (LocationPolicy.staleObservation(now, observed) || !engine.seen(observed)) return;
            if (lastObserved >= 0) maxGap = Math.max(maxGap, observed - lastObserved);
            lastObserved = observed;
            samples++;
            if (now - lastWrite >= 2000 || lastWrite == 0) {
                store.seen(System.currentTimeMillis() - (now - observed), observed, result.getRssi(), fix);
                lastWrite = now;
            }
            if (!"NEARBY".equals(store.p.getString("state", ""))) {
                store.event("重新检测到目标广播");
                store.status(engine.state(), "最近检测到目标广播");
                getSystemService(NotificationManager.class).cancel(2);
                updateNotification("正在守护 · 最近检测到手环");
            }
        }
        @Override public void onScanFailed(int errorCode) { fail("蓝牙扫描失败（" + errorCode + "），请稍后重新开启守护"); }
    };
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (ending) return;
            long now = SystemClock.elapsedRealtime();
            if (!Permissions.ready(GuardService.this)) { fail("权限已变更，请重新授权"); return; }
            // A long scheduler gap cannot be interpreted as proof of a missing wearable.
            if (now - lastTick > 90_000) { fail("系统暂停监测过久，请重新开启守护"); return; }
            lastTick = now;
            store.heartbeat();
            if (engine.tick(now)) {
                store.status(engine.state(), "持续未检测到目标；广播暂停或距离变远均可能造成此状态");
                store.event("持续未检测到目标，已保存最后记录");
                updateNotification("暂未检测到手环 · 点此查看最后位置");
                if (store.p.getBoolean("alerts", true) && System.currentTimeMillis() >= store.p.getLong("muteUntil", 0)) {
                    getSystemService(NotificationManager.class).notify(2, notification("暂未检测到你的手环", "最后记录已保留。请检查手环是否在身边。", "alerts"));
                }
            }
            if (engine.state() == GuardEngine.State.WAITING && now - started > 120_000) {
                fail("两分钟内未收到目标广播；请检查设备地址或广播兼容性"); return;
            }
            if (now - lastDiagnostic >= 60_000) {
                store.event("扫描回调 " + samples + " 次；最大观测间隔 " + maxGap / 1000 + " 秒");
                lastDiagnostic = now;
            }
            handler.postDelayed(this, 5000);
        }
    };
    @Override public void onCreate() {
        super.onCreate();
        store = new Store(this);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("guard", "守护状态", NotificationManager.IMPORTANCE_LOW));
        NotificationChannel alerts = new NotificationChannel("alerts", "手环失联提醒", NotificationManager.IMPORTANCE_HIGH);
        alerts.enableVibration(true);
        nm.createNotificationChannel(alerts);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) {
            ending = true;
            if (engine != null) engine.pause();
            store.status(GuardEngine.State.PAUSED, "守护已暂停");
            store.event("用户暂停守护");
            stopSelf();
            return START_NOT_STICKY;
        }
        if (running) return START_NOT_STICKY;
        try {
            startForeground(1, notification("环迹正在守护", "正在等待目标广播", "guard"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE | ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            if (!Permissions.ready(this) || store.address().isEmpty()) { fail("需要选择设备并授予蓝牙及精确位置权限"); return START_NOT_STICKY; }
            BluetoothManager manager = getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            if (adapter == null || !adapter.isEnabled()) { fail("请先打开蓝牙"); return START_NOT_STICKY; }
            locations = getSystemService(LocationManager.class);
            if (!locations.isLocationEnabled()) { fail("请先打开系统定位"); return START_NOT_STICKY; }
            engine = new GuardEngine(store.p.getInt("timeout", 60) * 1000L);
            started = lastTick = lastDiagnostic = SystemClock.elapsedRealtime();
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(bluetoothState, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), Context.RECEIVER_EXPORTED);
            else registerReceiver(bluetoothState, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
            registered = true;
            for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (locations.isProviderEnabled(provider)) {
                    Location cached = locations.getLastKnownLocation(provider);
                    if (cached != null) locationListener.onLocationChanged(cached);
                    locations.requestLocationUpdates(provider, 30_000, 10, locationListener, Looper.getMainLooper());
                }
            }
            scanner = adapter.getBluetoothLeScanner();
            if (scanner == null) { fail("蓝牙扫描不可用"); return START_NOT_STICKY; }
            // Nonempty address filter is essential for screen-off BLE scanning.
            scanner.startScan(Collections.singletonList(new ScanFilter.Builder().setDeviceAddress(store.address()).build()),
                new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).setReportDelay(0).build(), callback);
            scanning = true;
            running = true;
            store.status(GuardEngine.State.WAITING, "等待首次检测；检测成功后才启用失联判断");
            store.event("开启守护：地址过滤、低功耗扫描，阈值 " + store.p.getInt("timeout", 60) + " 秒");
            handler.postDelayed(tick, 5000);
        } catch (RuntimeException e) { fail("启动失败：" + e.getClass().getSimpleName() + "，请检查权限和系统设置"); }
        return START_NOT_STICKY;
    }
    private Notification notification(String title, String text, String channel) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder = new Notification.Builder(this, channel).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
            .setContentIntent(open).setOngoing(channel.equals("guard")).setAutoCancel(!channel.equals("guard"));
        if (channel.equals("guard")) builder.addAction(new Notification.Action.Builder(null, "暂停守护",
            PendingIntent.getService(this, 1, new Intent(this, GuardService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)).build());
        return builder.build();
    }
    private void updateNotification(String text) { getSystemService(NotificationManager.class).notify(1, notification("环迹正在守护", text, "guard")); }
    private void fail(String reason) {
        ending = true;
        if (engine != null) engine.interrupt();
        store.status(GuardEngine.State.INTERRUPTED, reason);
        store.event(reason);
        getSystemService(NotificationManager.class).cancel(2);
        stopSelf();
    }
    @Override public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        try { if (scanner != null && scanning) scanner.stopScan(callback); } catch (RuntimeException ignored) {}
        try { if (locations != null) locations.removeUpdates(locationListener); } catch (RuntimeException ignored) {}
        if (registered) unregisterReceiver(bluetoothState);
        if (!ending) { store.status(GuardEngine.State.INTERRUPTED, "守护服务已停止，请重新开启"); store.event("守护服务已停止"); }
        getSystemService(NotificationManager.class).cancel(2);
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
