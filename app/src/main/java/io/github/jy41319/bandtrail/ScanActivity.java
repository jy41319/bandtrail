package io.github.jy41319.bandtrail;

import android.annotation.SuppressLint;
import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.graphics.*;
import android.os.*;
import android.view.View;
import android.widget.*;
import java.util.*;

@SuppressLint("MissingPermission")
public final class ScanActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, ScanResult> results = new LinkedHashMap<>();
    private final Map<String, Button> rows = new LinkedHashMap<>();
    private Store store;
    private BluetoothLeScanner scanner;
    private boolean finding, active;
    private long started, lastSample, lastStart;
    private float filteredRssi = Float.NaN;
    private TextView status, strength;
    private LinearLayout list;
    private Radar radar;
    private final ScanCallback callback = new ScanCallback() {
        @Override public void onScanResult(int type, ScanResult result) {
            long now = SystemClock.elapsedRealtime();
            if (!active || LocationPolicy.staleObservation(now,result.getTimestampNanos()/1_000_000)) return;
            if (finding) {
                if (!result.getDevice().getAddress().equals(store.address())) return;
                lastSample=now;
                filteredRssi=Float.isNaN(filteredRssi)?result.getRssi():0.3f*result.getRssi()+0.7f*filteredRssi;
                int dbm=Math.round(filteredRssi);
                strength.setText(dbm+" dBm · "+(dbm>-60?"信号较强":dbm>-80?"信号中等":"信号较弱"));
                radar.level=dbm; radar.invalidate();
                radar.setContentDescription("蓝牙信号 "+dbm+" dBm");
            } else {
                String address=result.getDevice().getAddress();
                if (results.size()>=50 && !results.containsKey(address)) return;
                results.put(address,result);
                Button row=rows.get(address);
                if (row==null) {
                    row=Ui.button(ScanActivity.this,list,"",false,()->confirm(address)); rows.put(address,row);
                }
                row.setText(deviceName(result)+"\n"+address+"  ·  "+result.getRssi()+" dBm");
                status.setText("发现 "+results.size()+" 台广播设备 · 请选择你自己的手环");
            }
        }
        @Override public void onScanFailed(int error) { stop("扫描失败（"+error+"），请稍后重试或检查蓝牙权限"); }
    };
    private final Runnable timer=new Runnable() {
        @Override public void run() {
            if (!active) return;
            long now=SystemClock.elapsedRealtime();
            if (now-started>=120_000) { stop("本次扫描已结束，点击下方按钮可重新扫描"); return; }
            if (finding) {
                if (lastSample==0 || now-lastSample>10_000) {
                    strength.setText("等待新信号"); radar.level=-127; radar.invalidate(); filteredRssi=Float.NaN;
                }
                status.setText(lastSample==0?"正在寻找所选蓝牙地址…":"上次收到信号："+((now-lastSample)/1000)+" 秒前");
            }
            handler.postDelayed(this,1000);
        }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); store=new Store(this); finding=getIntent().getBooleanExtra("finder",false);
        LinearLayout root=Ui.page(this);
        Ui.button(this,root,"返回",false,this::finish);
        Ui.text(this,root,finding?"顺着信号，慢慢找。":"找到你的手环。",28,Ui.INK,true);
        Ui.note(this,root,finding?"慢慢移动手机，观察信号变化。数值越接近 0，通常信号越强。":"保持手环在身边，并保持小米运动健康的日常连接状态。");
        LinearLayout panel=Ui.card(this,root,Color.WHITE);
        radar=new Radar(); panel.addView(radar,new LinearLayout.LayoutParams(-1,Ui.dp(this,170)));
        strength=Ui.text(this,panel,finding?"等待新信号":"BLE · 附近广播",22,Ui.GREEN,true);
        strength.setGravity(android.view.Gravity.CENTER);
        status=Ui.text(this,panel,"准备扫描…",14,Ui.MUTED,false);
        Ui.button(this,root,"重新扫描 · 最长 2 分钟",true,this::start);
        list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); root.addView(list);
        Ui.note(this,root,finding?"信号会受墙壁、人体和朝向影响，不代表精确距离。离开此页面即停止高频寻找。":"列表包含附近的其他蓝牙设备，名称不能证明设备身份。请核对官方 App 的设备信息；只选择自己的设备。\n\n看不到手环？它可能在连接后停止广播。本版不会自动断开官方 App，也不会将其他同名设备当作你的手环。");
    }
    private String deviceName(ScanResult result) {
        String name=result.getScanRecord()==null?null:result.getScanRecord().getDeviceName();
        if(name==null || name.trim().isEmpty()) name=result.getDevice().getName();
        if(name==null || name.trim().isEmpty()) return "未命名蓝牙设备";
        return name.length()>60?name.substring(0,60):name;
    }
    private void confirm(String address) {
        ScanResult result=results.get(address); if(result==null) return;
        String name=deviceName(result);
        String uuids=result.getScanRecord()==null?"无":String.valueOf(result.getScanRecord().getServiceUuids());
        new AlertDialog.Builder(this).setTitle("确认这是你的设备")
            .setMessage(name+"\n"+address+"\n服务 UUID："+uuids+"\n\n本版按蓝牙地址识别，尚不能验证这是小米手环 11。请核对设备信息。更换设备会清除此前的位置和诊断记录。")
            .setPositiveButton("选择此设备",(d,w)->{
                if(GuardService.running) { Ui.toast(this,"请先暂停守护"); return; }
                stop("已选择"); store.select(name,address); store.event("用户选择目标设备"); finish();
            }).setNegativeButton("取消",null).show();
    }
    private void start() {
        long now=SystemClock.elapsedRealtime();
        if(now-lastStart<6000 && lastStart!=0) { Ui.toast(this,"请稍等几秒再重试，避免系统限制扫描"); return; }
        stop("");
        if(!Permissions.ready(this)) { status.setText("请返回首页授予附近设备与精确位置权限"); return; }
        if(finding && store.address().isEmpty()) { status.setText("请先添加手环"); return; }
        try {
            BluetoothManager manager=getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
            if(adapter==null || !adapter.isEnabled()) { status.setText("请先打开系统蓝牙"); return; }
            if(!getSystemService(android.location.LocationManager.class).isLocationEnabled()) { status.setText("请先打开系统定位"); return; }
            scanner=adapter.getBluetoothLeScanner();
            if(scanner==null) { status.setText("蓝牙扫描暂不可用"); return; }
            results.clear(); rows.clear(); list.removeAllViews();
            started=lastStart=now; lastSample=0; filteredRssi=Float.NaN;
            active=true;
            scanner.startScan(finding?Collections.singletonList(new ScanFilter.Builder().setDeviceAddress(store.address()).build()):null,
                new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).setReportDelay(0).build(),callback);
            status.setText("正在扫描附近广播…"); handler.post(timer);
        } catch(RuntimeException e) { stop("无法扫描："+e.getClass().getSimpleName()+"，请检查权限"); }
    }
    private void stop(String message) {
        handler.removeCallbacks(timer);
        if(scanner!=null && active) try { scanner.stopScan(callback); } catch(RuntimeException ignored) {}
        active=false;
        if(status!=null && !message.isEmpty()) status.setText(message);
    }
    @Override protected void onResume() { super.onResume(); start(); }
    @Override protected void onPause() { stop("扫描已暂停"); super.onPause(); }
    private final class Radar extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); int level=-127;
        Radar() { super(ScanActivity.this); setContentDescription("蓝牙扫描示意图，不表示设备方位或距离"); }
        @Override protected void onDraw(Canvas c) {
            float x=getWidth()/2f,y=getHeight()/2f,unit=Math.min(getWidth(),getHeight())/2.3f;
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Ui.dp(ScanActivity.this,1)); paint.setColor(Ui.PALE);
            for(int i=1;i<=3;i++) c.drawCircle(x,y,unit*i/3,paint);
            c.drawLine(x-unit,y,x+unit,y,paint); c.drawLine(x,y-unit,x,y+unit,paint);
            paint.setStyle(Paint.Style.FILL); paint.setColor(Ui.GREEN);
            float radius=level==-127?8:Math.max(10,(110+level)*0.5f);
            c.drawCircle(x,y,Ui.dp(ScanActivity.this,(int)radius),paint);
            paint.setColor(Color.WHITE); c.drawCircle(x,y,Ui.dp(ScanActivity.this,3),paint);
        }
    }
}
