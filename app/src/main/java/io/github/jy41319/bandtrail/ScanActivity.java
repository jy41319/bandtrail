package io.github.jy41319.bandtrail;

import android.annotation.SuppressLint;
import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import java.util.*;

@SuppressLint("MissingPermission")
public final class ScanActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private DeviceCatalog catalog = new DeviceCatalog();
    private final Map<String, Button> rows = new LinkedHashMap<>();
    private Store store;
    private BluetoothLeScanner scanner;
    private boolean finding, active, showAnonymous;
    private long started, lastSample, lastStart;
    private float filteredRssi = Float.NaN;
    private TextView status, strength, savedHint, nearbyHint;
    private LinearLayout savedList, namedList, anonymousList;
    private Button anonymousToggle, recordPermission;
    private Radar radar;
    private final ScanCallback callback = new ScanCallback() {
        @Override public void onScanResult(int type, ScanResult result) {
            long now = SystemClock.elapsedRealtime();
            if (!active || LocationPolicy.staleObservation(now,result.getTimestampNanos()/1_000_000)) return;
            try {
                if (finding) {
                    if (!result.getDevice().getAddress().equals(store.address())) return;
                    lastSample=now;
                    filteredRssi=Float.isNaN(filteredRssi)?result.getRssi():0.3f*result.getRssi()+0.7f*filteredRssi;
                    int dbm=Math.round(filteredRssi);
                    strength.setText(dbm+" dBm · "+(dbm>-60?"信号较强":dbm>-80?"信号中等":"信号较弱"));
                    radar.level=dbm; radar.invalidate(); radar.setContentDescription("蓝牙信号 "+dbm+" dBm");
                } else {
                    BluetoothDevice device=result.getDevice();
                    String advertised=result.getScanRecord()==null?null:result.getScanRecord().getDeviceName();
                    catalog.scan(device.getAddress(),alias(device),device.getName(),advertised,result.getRssi(),now);
                }
            } catch (SecurityException e) { stop("蓝牙权限已变更，请重新授权"); }
        }
        @Override public void onScanFailed(int error) { stop("扫描失败（"+error+"）"+(finding?"，请稍后重试":"，仍可选择手机已有记录")); }
    };
    private final Runnable timer=new Runnable() {
        @Override public void run() {
            if (!active) return;
            long now=SystemClock.elapsedRealtime();
            if (!finding) renderPicker();
            if (now-started>=120_000) { stop(finding?"本次寻找已结束，可重新扫描":"本次扫描已结束；手机记录仍可选择"); return; }
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
        Ui.text(this,root,finding?"顺着信号，慢慢找。":"从手机记录里选。",28,Ui.INK,true);
        Ui.note(this,root,finding?"慢慢移动手机，观察信号变化。数值越接近 0，通常信号越强。":"优先显示手机已配对或当前已连接的蓝牙设备，使用系统保存的名称。");
        if (finding) {
            LinearLayout panel=Ui.card(this,root,Color.WHITE);
            radar=new Radar(); panel.addView(radar,new LinearLayout.LayoutParams(-1,Ui.dp(this,170)));
            strength=Ui.text(this,panel,"等待新信号",22,Ui.GREEN,true);
            strength.setGravity(android.view.Gravity.CENTER);
            status=Ui.text(this,panel,"准备扫描…",14,Ui.MUTED,false);
            Ui.button(this,root,"重新扫描 · 最长 2 分钟",true,this::start);
            Ui.note(this,root,"信号会受墙壁、人体和朝向影响，不代表精确距离。离开此页面即停止高频寻找。");
            return;
        }
        LinearLayout known=Ui.card(this,root,Color.WHITE);
        Ui.text(this,known,"手机已有的设备",20,Ui.INK,true);
        savedHint=Ui.text(this,known,"正在读取手机蓝牙记录…",14,Ui.MUTED,false);
        savedList=column(known);
        recordPermission=Ui.button(this,known,"授权读取手机蓝牙记录",true,()->Permissions.requestDeviceRecords(this));
        Ui.button(this,known,"刷新手机记录",false,this::refreshRecords);
        Ui.button(this,known,"打开系统蓝牙设置",false,()->{
            try { startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); }
            catch(ActivityNotFoundException e) { Ui.toast(this,"请在手机设置里打开蓝牙页面"); }
        });
        Ui.note(this,known,"这里只显示可能支持低功耗蓝牙的设备；已配对不代表此刻在附近，也不代表已经能收到广播。");
        LinearLayout nearby=Ui.card(this,root,Color.WHITE);
        Ui.text(this,nearby,"其他附近设备",20,Ui.INK,true);
        status=Ui.text(this,nearby,"",14,Ui.MUTED,false);
        Ui.button(this,nearby,"扫描附近设备 · 2 分钟",false,this::start);
        nearbyHint=Ui.text(this,nearby,"附近有名称的设备会显示在这里。",14,Ui.MUTED,false);
        namedList=column(nearby);
        anonymousToggle=Ui.button(this,nearby,"查看未命名设备（0）",false,()->{
            showAnonymous=!showAnonymous; renderPicker();
        });
        anonymousList=column(nearby);
        Ui.button(this,root,"输入手环蓝牙地址",false,this::manualEntry);
        Ui.note(this,root,"手机列表没有手环？部分手环只绑定在“小米运动健康”，其私有绑定记录不能由环迹直接读取。可在官方 App 或手环关于页面查找蓝牙地址后输入。请保持原有绑定，无需解绑。\n\n记录识别成功后，仍需开启守护验证广播是否可见。");
    }
    private LinearLayout column(LinearLayout parent) {
        LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); parent.addView(list); return list;
    }
    private String alias(BluetoothDevice device) { return Build.VERSION.SDK_INT>=30?device.getAlias():null; }
    private void systemDevice(BluetoothDevice device,boolean bonded,boolean connected) {
        if (device.getType()==BluetoothDevice.DEVICE_TYPE_CLASSIC) return;
        catalog.system(device.getAddress(),alias(device),device.getName(),bonded,connected);
    }
    private void refreshRecords() {
        if (finding) return;
        stop("附近扫描尚未开始"); catalog=new DeviceCatalog(); rows.clear(); savedList.removeAllViews(); namedList.removeAllViews(); anonymousList.removeAllViews();
        boolean canRead=Permissions.canReadDevices(this);
        recordPermission.setVisibility(canRead?View.GONE:View.VISIBLE);
        if (!canRead) {
            savedHint.setText("允许“附近设备”权限，即可读取手机已有记录。不需要先授权定位。");
            status.setText("附近扫描尚未开始"); renderPicker(); return;
        }
        try {
            BluetoothManager manager=getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
            if (adapter==null || !adapter.isEnabled()) { savedHint.setText("请先打开系统蓝牙，再刷新手机记录。"); renderPicker(); return; }
            for (BluetoothDevice device:adapter.getBondedDevices()) systemDevice(device,true,false);
            boolean connectedRead=true;
            try { for (BluetoothDevice device:manager.getConnectedDevices(BluetoothProfile.GATT)) systemDevice(device,false,true); }
            catch (RuntimeException e) { connectedRead=false; }
            savedHint.setText((catalog.sorted().isEmpty()?"系统暂未返回手环记录。你仍可扫描附近设备，或输入手环蓝牙地址。":"可直接选择，无需等它再次广播。连接状态是本次读取的系统记录。")
                +(connectedRead?"":"\n当前连接记录读取不可用，已保留配对列表。"));
            status.setText("点击扫描可确认哪些设备正在广播。"); renderPicker();
        } catch (RuntimeException e) {
            savedHint.setText("无法读取手机记录，请检查附近设备权限后重试。"); renderPicker();
        }
    }
    private void renderPicker() {
        if (finding) return;
        int unnamed=0, named=0;
        for (DeviceCatalog.Entry e:catalog.sorted()) {
            LinearLayout parent=e.saved()?savedList:e.name().isEmpty()?anonymousList:namedList;
            if(!e.saved()) { if(e.name().isEmpty()) unnamed++; else named++; }
            Button row=rows.get(e.address);
            if(row==null) { row=Ui.button(this,parent,"",false,()->confirm(e)); rows.put(e.address,row); }
            else if(row.getParent()!=parent) { ((LinearLayout)row.getParent()).removeView(row); parent.addView(row); }
            String source=e.connected?"系统已连接（读取时）":e.bonded?"手机已配对":"附近广播";
            String signal=e.observed<0?"尚未检测到广播":Math.max(0,(SystemClock.elapsedRealtime()-e.observed)/1000)+" 秒前收到广播 · "+e.rssi+" dBm";
            row.setText(e.label()+"\n"+source+"\n"+signal+"\n"+e.address);
        }
        nearbyHint.setVisibility(named==0?View.VISIBLE:View.GONE);
        anonymousToggle.setText((showAnonymous?"收起":"查看")+"未命名设备（"+unnamed+"）");
        anonymousList.setVisibility(showAnonymous?View.VISIBLE:View.GONE);
    }
    private void confirm(DeviceCatalog.Entry entry) {
        String name=entry.name().isEmpty()?"我的手环":entry.name();
        choose(name,entry.address,entry.observed<0?"已从手机记录找到该设备，但尚未收到它的广播。添加后需要开启守护验证。":"已收到该地址的广播，请确认它是你的手环。");
    }
    private void choose(String name,String address,String explanation) {
        boolean changing=!store.address().isEmpty()&&!store.address().equalsIgnoreCase(address);
        new AlertDialog.Builder(this).setTitle("添加这台设备？")
            .setMessage(name+"\n"+address+"\n\n"+explanation+(changing?"\n\n更换设备会清除此前的位置和诊断记录。":""))
            .setPositiveButton("添加并返回",(d,w)->{
                if(GuardService.running) { Ui.toast(this,"请先暂停守护"); return; }
                stop("已选择"); store.select(name,address); store.event("用户选择目标设备"); finish();
            }).setNegativeButton("取消",null).show();
    }
    private void manualEntry() {
        LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(Ui.dp(this,24),Ui.dp(this,12),Ui.dp(this,24),0);
        Ui.note(this,form,"从小米运动健康的设备信息或手环“关于”页面复制蓝牙地址。请勿填写手机自身的地址。");
        EditText name=new EditText(this); name.setHint("手环名称（可选）"); name.setSingleLine(true); form.addView(name);
        EditText address=new EditText(this); address.setHint("例如 AA:BB:CC:DD:EE:FF"); address.setSingleLine(true);
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        form.addView(address);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("输入手环蓝牙地址").setView(form)
            .setPositiveButton("下一步",null).setNegativeButton("取消",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String normalized=DeviceCatalog.normalize(address.getText().toString());
            if(normalized==null) { address.setError("请输入 12 位十六进制蓝牙地址，例如 AA:BB:CC:DD:EE:FF"); return; }
            String label=DeviceCatalog.clean(name.getText().toString()); dialog.dismiss();
            choose(label.isEmpty()?"我的手环":label,normalized,"地址尚未验证。添加后开启守护，确认能否持续收到手环广播。");
        }));
        dialog.show();
    }
    private void start() {
        long now=SystemClock.elapsedRealtime();
        if(now-lastStart<6000 && lastStart!=0) { Ui.toast(this,"请稍等几秒再重试，避免系统限制扫描"); return; }
        stop("");
        if(!Permissions.ready(this)) {
            status.setText("附近扫描还需要精确位置与附近设备权限；读取手机记录不需要定位。");
            Permissions.request(this); return;
        }
        if(finding && store.address().isEmpty()) { status.setText("请先添加手环"); return; }
        try {
            BluetoothManager manager=getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
            if(adapter==null || !adapter.isEnabled()) { status.setText("请先打开系统蓝牙"); return; }
            if(!getSystemService(android.location.LocationManager.class).isLocationEnabled()) { status.setText("附近扫描需要打开系统定位；已有记录仍可选择"); return; }
            scanner=adapter.getBluetoothLeScanner();
            if(scanner==null) { status.setText("蓝牙扫描暂不可用"); return; }
            started=lastStart=now; lastSample=0; filteredRssi=Float.NaN; active=true;
            scanner.startScan(finding?Collections.singletonList(new ScanFilter.Builder().setDeviceAddress(store.address()).build()):null,
                new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).setReportDelay(0).build(),callback);
            status.setText("正在扫描附近广播…"); handler.post(timer);
        } catch(RuntimeException e) { stop("无法扫描，请检查蓝牙与权限；手机记录仍可选择"); }
    }
    private void stop(String message) {
        handler.removeCallbacks(timer);
        if(scanner!=null && active) try { scanner.stopScan(callback); } catch(RuntimeException ignored) {}
        active=false;
        if(status!=null && !message.isEmpty()) status.setText(message);
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(code,permissions,results);
        if(!finding) refreshRecords();
        if(code==10 && Permissions.ready(this)) start();
    }
    @Override protected void onResume() { super.onResume(); if(finding) start(); else refreshRecords(); }
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
