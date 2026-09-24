package io.github.jy41319.bandtrail;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.Date;

final class Store {
    final SharedPreferences p;
    Store(Context context) { p = context.getSharedPreferences("bandtrail", Context.MODE_PRIVATE); }
    String address() { return p.getString("address", ""); }
    String name() { return p.getString("name", "我的手环"); }
    void select(String name, String address) {
        p.edit().clear().putString("name", name).putString("address", address).apply();
    }
    void status(GuardEngine.State state, String detail) {
        p.edit().putString("state", state.name()).putString("detail", detail)
            .putLong("heartbeat", SystemClock.elapsedRealtime()).apply();
    }
    void heartbeat() { p.edit().putLong("heartbeat", SystemClock.elapsedRealtime()).apply(); }
    void seen(long time, long elapsed, int rssi, Location fix) {
        SharedPreferences.Editor e = p.edit().putLong("seen", time).putInt("rssi", rssi);
        if (fix != null && fix.hasAccuracy() && LocationPolicy.usable(elapsed, fix.getElapsedRealtimeNanos() / 1_000_000,
                fix.getAccuracy(), fix.getLatitude(), fix.getLongitude())) {
            e.putString("lat", Double.toString(fix.getLatitude())).putString("lon", Double.toString(fix.getLongitude()))
                .putFloat("accuracy", fix.getAccuracy()).putLong("fixTime", fix.getTime()).putLong("pointSeen", time);
        }
        e.apply();
    }
    boolean hasPoint() { return p.contains("lat") && p.contains("lon"); }
    synchronized void event(String message) {
        try {
            JSONArray old = new JSONArray(p.getString("events", "[]"));
            JSONArray next = new JSONArray();
            JSONObject row = new JSONObject().put("time", System.currentTimeMillis()).put("message", message);
            next.put(row);
            for (int i = 0; i < Math.min(old.length(), 99); i++) next.put(old.get(i));
            p.edit().putString("events", next.toString()).apply();
        } catch (org.json.JSONException ignored) { /* corrupt optional diagnostics do not stop guarding */ }
    }
    String events() {
        StringBuilder text = new StringBuilder();
        try {
            JSONArray events = new JSONArray(p.getString("events", "[]"));
            for (int i = 0; i < events.length(); i++) {
                JSONObject e = events.getJSONObject(i);
                text.append(time(e.getLong("time"))).append("  ").append(e.getString("message")).append('\n');
            }
        } catch (org.json.JSONException ignored) { return "诊断记录不可读"; }
        return text.length() == 0 ? "暂无事件。开启守护后会记录运行状态。" : text.toString();
    }
    String diagnostic() {
        // No device address, coordinates, device name or advertising payload in exported diagnostics.
        return "BandTrail " + BuildConfig.VERSION_NAME + "\nAndroid API " + android.os.Build.VERSION.SDK_INT
            + "\n状态：" + p.getString("state", "PAUSED") + "\n最近信号：" + p.getInt("rssi", -127)
            + " dBm\n\n" + events();
    }
    static String time(long value) {
        return value <= 0 ? "尚无记录" : DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(new Date(value));
    }
}
