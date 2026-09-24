package io.github.jy41319.bandtrail;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import java.util.Locale;

public final class MainActivity extends Activity {
    private Store store;
    private TextView state, detail, lastSeen, point, permissions;
    private Button guard, map;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() { render(); handler.postDelayed(this,2000); }
    };
    @Override public void onCreate(Bundle saved) { super.onCreate(saved); store = new Store(this); build(); }
    private void build() {
        LinearLayout root = Ui.page(this);
        Ui.text(this,root,"BANDTRAIL   /   环迹",13,Ui.GREEN,true);
        Ui.text(this,root,"留住最后的线索。",30,Ui.INK,true);
        Ui.note(this,root,"手环最后位置助手 · 本地记录 · 无需账号");
        LinearLayout hero = Ui.card(this,root,Ui.GREEN);
        Ui.text(this,hero,store.address().isEmpty() ? "从你的手环开始" : store.name(),22,Color.WHITE,true);
        state = Ui.text(this,hero,"尚未开启守护",28,Color.WHITE,true);
        detail = Ui.text(this,hero,"",14,Color.rgb(222,237,221),false);
        lastSeen = Ui.text(this,hero,"",14,Color.WHITE,false);
        guard = Ui.button(this,root,"开启守护",true,this::toggleGuard);
        Ui.button(this,root,store.address().isEmpty() ? "添加我的手环" : "更换设备",false,() -> {
            if (GuardService.running) { Ui.toast(this,"请先暂停守护，再更换设备"); return; }
            if (ensurePermissions()) startActivity(new Intent(this,ScanActivity.class));
        });
        LinearLayout location = Ui.card(this,root,Color.WHITE);
        Ui.text(this,location,"最后记录的位置",19,Ui.INK,true);
        point = Ui.text(this,location,"",15,Ui.INK,false);
        map = Ui.button(this,location,"在地图中查看",false,this::openMap);
        Ui.note(this,location,"记录的是当时手机的位置，可作为寻找起点；不代表手环的实时位置或准确掉落点。");
        LinearLayout tools = Ui.card(this,root,Color.WHITE);
        Ui.text(this,tools,"找回与守护",19,Ui.INK,true);
        Ui.button(this,tools,"附近寻找 · 看信号强弱",false,() -> {
            if (store.address().isEmpty()) { Ui.toast(this,"请先添加手环"); return; }
            if (ensurePermissions()) startActivity(new Intent(this,ScanActivity.class).putExtra("finder",true));
        });
        Ui.button(this,tools,"提醒与后台设置",false,this::settings);
        Ui.button(this,tools,"兼容性诊断",false,this::diagnostics);
        permissions = Ui.text(this,tools,"",13,Ui.MUTED,false);
        Ui.note(this,root,"实验版 0.1 · 手环 11 尚待真机验证\n仅按所选蓝牙地址识别。广播暂停或地址变化会影响守护；请先完成诊断，再将它用于日常辅助。");
        Ui.button(this,root,"使用说明与隐私",false,this::help);
        render();
    }
    private boolean ensurePermissions() {
        if (Permissions.ready(this)) return true;
        new AlertDialog.Builder(this).setTitle("让环迹留住位置线索")
            .setMessage("需要附近设备和精确位置权限，用于发现手环并记录当时手机的位置。数据只存手机本地。请在定位授权中选择精确位置；拒绝后仍可查看已有记录。")
            .setPositiveButton("授予权限",(d,w) -> Permissions.request(this)).setNegativeButton("暂时不用",null).show();
        return false;
    }
    private void toggleGuard() {
        if (GuardService.running) { startService(new Intent(this,GuardService.class).setAction(GuardService.STOP)); return; }
        if (store.address().isEmpty()) { Ui.toast(this,"先点击“添加我的手环”，选择你自己的设备"); return; }
        if (!ensurePermissions()) return;
        try { startForegroundService(new Intent(this,GuardService.class)); }
        catch (RuntimeException e) { store.status(GuardEngine.State.INTERRUPTED,"系统未允许启动，请检查权限后重试"); render(); }
    }
    private void render() {
        if (state == null) return;
        String s = store.p.getString("state","PAUSED");
        boolean interrupted = !GuardService.running && !(s.equals("PAUSED") || s.equals("INTERRUPTED"));
        long beat = store.p.getLong("heartbeat",0);
        if (GuardService.running && (SystemClock.elapsedRealtime() - beat > 90_000 || beat > SystemClock.elapsedRealtime())) interrupted = true;
        String title;
        if (interrupted) title = "守护已中断";
        else switch (s) {
            case "WAITING": title="等待首次检测"; break;
            case "NEARBY": title="最近检测到手环"; break;
            case "MISSING": title="暂未检测到手环"; break;
            case "INTERRUPTED": title="守护不可用"; break;
            default: title="尚未开启守护";
        }
        state.setText(title);
        detail.setText(interrupted ? "系统可能停止了服务。最后记录仍在，请重新开启守护。" : store.p.getString("detail","先添加手环，再开启守护。"));
        lastSeen.setText("最后检测  " + Store.time(store.p.getLong("seen",0)));
        guard.setText(GuardService.running ? "暂停守护" : "开启守护");
        if (store.hasPoint()) {
            point.setText(String.format(Locale.getDefault(),"%s, %s\n手机定位精度约 ±%.0f 米\n对应检测：%s\n定位采样：%s%s",store.p.getString("lat",""),store.p.getString("lon",""),
                store.p.getFloat("accuracy",0), Store.time(store.p.getLong("pointSeen",0)),Store.time(store.p.getLong("fixTime",0)),
                store.p.getLong("pointSeen",0) < store.p.getLong("seen",0) ? "\n较新的检测没有有效定位，保留了此前位置。" : ""));
        } else point.setText("还没有位置记录\n开启守护后，需同时收到手环广播和有效手机定位。室内可能暂时无法定位。");
        map.setEnabled(store.hasPoint());
        boolean notifications = getSystemService(NotificationManager.class).areNotificationsEnabled();
        permissions.setText((Permissions.ready(this) ? "蓝牙与定位权限已授予" : "需要授予蓝牙与精确位置权限")
            + (notifications ? "" : "\n通知未开启，失联提醒可能无法显示"));
    }
    private void openMap() {
        new AlertDialog.Builder(this).setTitle("打开最后记录的位置")
            .setItems(new String[]{"高德地图（原始 GPS 坐标）","百度地图（WGS84 坐标）","其他地图（请确认支持 WGS84）","复制原始坐标"},(dialog,which) -> {
                String lat=store.p.getString("lat",""), lon=store.p.getString("lon","");
                if (which==3) { copy("WGS84: " + lat + ", " + lon); return; }
                String uri;
                if (which==0) uri="androidamap://viewMap?sourceApplication=BandTrail&poiname=" + Uri.encode("手环最后检测位置") + "&lat="+lat+"&lon="+lon+"&dev=1";
                else if (which==1) uri="baidumap://map/marker?location="+lat+","+lon+"&title="+Uri.encode("手环最后检测位置")+"&coord_type=wgs84&src="+getPackageName();
                else uri="geo:"+lat+","+lon+"?q="+lat+","+lon+"("+Uri.encode("手环最后检测位置")+")";
                try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(uri))); }
                catch (ActivityNotFoundException e) { Ui.toast(this,"未安装对应地图，可以选择复制坐标"); }
            }).setNegativeButton("取消",null).show();
    }
    private void copy(String value) {
        getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("BandTrail",value));
        Ui.toast(this,"已复制");
    }
    private void settings() {
        String[] options={"失联提醒："+(store.p.getBoolean("alerts",true)?"已开启":"已关闭"),"失联等待："+store.p.getInt("timeout",60)+" 秒","静音 30 分钟","恢复提醒声音","系统权限与后台设置","清除设备和所有记录"};
        new AlertDialog.Builder(this).setTitle("守护设置").setItems(options,(d,w) -> {
            switch(w) {
                case 0: store.p.edit().putBoolean("alerts",!store.p.getBoolean("alerts",true)).apply(); break;
                case 1: new AlertDialog.Builder(this).setTitle("持续未检测到多久后提醒？")
                    .setItems(new String[]{"30 秒 · 更敏感","60 秒 · 默认","120 秒 · 减少误报"},(a,b) -> {
                        store.p.edit().putInt("timeout",new int[]{30,60,120}[b]).apply();
                        Ui.toast(this,"已保存，下次开启守护生效");
                    }).show(); break;
                case 2: store.p.edit().putLong("muteUntil",System.currentTimeMillis()+30*60_000L).apply(); getSystemService(NotificationManager.class).cancel(2); Ui.toast(this,"已静音 30 分钟"); break;
                case 3: store.p.edit().putLong("muteUntil",0).putBoolean("alerts",true).apply(); Ui.toast(this,"已恢复；声音与震动遵循系统通知设置"); break;
                case 4: new AlertDialog.Builder(this).setTitle("允许后台运行")
                    .setMessage("在系统设置里允许通知和精确定位，并检查电池设置。HyperOS 可检查后台无限制与自启动；其他品牌名称不同。重启或强制停止后，需要重新打开环迹开启守护。")
                    .setPositiveButton("应用设置",(a,b) -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))))
                    .setNeutralButton("电池设置",(a,b) -> {
                        try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
                        catch (ActivityNotFoundException e) { Ui.toast(this,"请在系统设置中查找电池管理"); }
                    }).setNegativeButton("关闭",null).show(); break;
                case 5:
                    if (GuardService.running) { Ui.toast(this,"请先暂停守护，再删除记录"); return; }
                    new AlertDialog.Builder(this).setTitle("删除本地记录？").setMessage("将删除所选设备、最后位置和诊断记录，无法恢复。")
                        .setPositiveButton("删除",(a,b)->{ store.p.edit().clear().apply(); build(); }).setNegativeButton("取消",null).show(); break;
                default: break;
            }
        }).show();
    }
    private void diagnostics() {
        TextView content = new TextView(this); content.setText(store.diagnostic()); content.setTextIsSelectable(true); content.setTextSize(14); content.setPadding(30,15,30,15);
        ScrollView scroll = new ScrollView(this); scroll.addView(content);
        new AlertDialog.Builder(this).setTitle("兼容性诊断").setView(scroll)
            .setPositiveButton("复制脱敏诊断",(d,w)->copy(store.diagnostic())).setNegativeButton("关闭",null).show();
    }
    private void help() {
        new AlertDialog.Builder(this).setTitle("先验证，再放心使用")
            .setMessage("1. 保持小米运动健康正常连接，在环迹里扫描并确认自己的手环。\n2. 开启守护，确认页面出现检测时间和位置。\n3. 锁屏走开再返回，检查诊断记录和提醒。\n\n环迹只被动读取广播，不接管官方 App 的连接。若手环不广播或地址变化，本版可能无法识别，请重新选择设备；无法保证所有型号兼容。\n\n本应用没有联网权限、广告和统计 SDK。数据只保存在应用私有目录，不参与系统备份。仅在你主动打开外部地图或复制信息时，数据才交给相应应用。\n\n监测必须提前开启；手机重启、强制停止或系统限制会造成空档。省电休眠可能延迟提醒，不能作为可靠的防丢保证。")
            .setPositiveButton("知道了",null).show();
    }
    @Override public void onRequestPermissionsResult(int code,String[] perms,int[] results) {
        super.onRequestPermissionsResult(code,perms,results); render();
        if (!Permissions.ready(this)) Ui.toast(this,"请授予附近设备和精确位置权限；可在系统应用设置中修改");
        else Ui.toast(this,"权限已就绪，请继续添加手环或开启守护");
    }
    @Override protected void onResume() { super.onResume(); build(); handler.removeCallbacks(refresh); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
