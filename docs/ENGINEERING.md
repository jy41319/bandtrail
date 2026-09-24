# 实现取舍与开源研究

## 研究来源（2026-09-24）

| 来源 | 采用的思路 | 未直接采用的部分 |
|---|---|---|
| [s4ysolutions/itag](https://github.com/s4ysolutions/itag)，查看提交 `7596a0623d5e38a96ca4ad54be7051dfeda00fc8` 的 MainActivity / history/HistoryRecord | 最近观测与位置关联、外部地图 Intent、失联与 RSSI 界面 | 不复制代码；其 iTag 协议不证明手环兼容，避免带入 Google 服务及遥测依赖 |
| [Nordic Android Scanner Compat](https://github.com/nordicsemi/Android-Scanner-Compat-Library) README | 后台低功耗扫描；无过滤扫描在息屏时可能停止，因此守护必须指定目标过滤 | minSdk 29 可直接用平台 API，首版无需兼容旧 Android 的库 |
| [Gadgetbridge Xiaomi 设备资料](https://gadgetbridge.org/gadgets/wearables/xiaomi/) | 型号/固件兼容列表、区分设备发现与协议认证 | 不 fork 全项目、不接管健康数据，不根据旧手环推断手环 11 已支持 |
| [Android 后台 BLE](https://developer.android.com/develop/connectivity/bluetooth/ble/background) | 明确后台扫描和常驻连接的生命周期限制 | 不将普通后台定时器视为稳定守护；PendingIntent 与关联设备方案留待真实广播特征验证 |
| [Android 前台服务类型](https://developer.android.com/develop/background-work/services/fgs/service-types) | 用户在可见页面启动 connectedDevice + location 前台服务，声明并检查权限 | 不尝试开机后无条件拉起定位服务 |
| [高德地图标注](https://lbs.amap.com/api/amap-mobile/guide/android/marker) | 使用 `dev=1` 明确传入原始坐标，避免重复或缺失偏移转换 | 不内嵌地图 SDK |

应用代码为原创 MIT 实现。上述项目仅作为概念和平台用法参考，未复制 GPL/AGPL 实现或资源。Gradle Wrapper 来自 Gradle 8.11.1（Apache-2.0），发行包 SHA256 已固定。JUnit 仅用于测试（EPL-1.0）。

## 数据与状态

`ScanResult` → 过滤并检查时间 → `GuardEngine` → 与有效定位关联 → 私有存储 → 主界面/通知。

- WAITING：本次尚未看到设备，不发失联警报；两分钟没有观测转为诊断失败。
- NEARBY：最近有观测，仅表示检测到广播，不宣称 GATT 连接成功。
- MISSING：已见过，但持续超过阈值没有新广播，只提醒一次；重新见到后恢复。
- INTERRUPTED：权限、蓝牙、扫描错误或明显调度空档；不冒充设备丢失。
- PAUSED：用户停止守护。

所有超时使用 elapsedRealtime，避免系统时间校准触发误报。超过 15 秒的缓存扫描结果不用于证明当前在场。定位须发生在观测之前、距观测不超过 90 秒，且有有效坐标和 0–1000 米的精度值。最后观测和最后有定位的观测各自保留时间，后者不会被没有定位的新观测覆盖。

当前用一份最新定位缓存；如果较新的定位精度不可用，不会回退猜测位置。无有效位置则明确显示未获取。位置采样目标为 30 秒/10 米，设备和系统可能延迟；精度表示手机定位误差，不包含蓝牙范围和移动造成的全部误差。

## 身份与共存限制

首版按用户选择的确切广播地址过滤，不按名称或厂商编号认定设备身份。不解析私有协议、不连接 GATT、不伪造设备认证。隐私地址轮换、连接后停止广播时，观察链路可能失效。观察不到与真的离开在纯被动扫描下无法总是区分，因此界面使用“暂未检测到”，不能将此 Alpha 作为可靠防丢装置。

## 后台与耗电

仅在用户开启守护时运行前台服务；高频扫描限于寻找页面，最长两分钟。无自动重启、无常驻唤醒锁、无精确闹钟。进程终止后保留最后记录，但不承诺实时提醒；Doze 可延迟计时，超过 90 秒的执行空档结束本次守护并标记中断。耗电与息屏能力需要真机测量。

## 测试层次

- JVM：状态机、防重复提醒、重新出现、权限失败、乱序数据、定位新鲜度和边界。
- Android instrumentation：本地记录语义、诊断脱敏、日志上限。
- UI：模拟器启动、权限拒绝、无设备空状态及设置流程。
- 真机：广播共存、息屏稳定性、地图点位和耗电；模拟器结果不能替代。
