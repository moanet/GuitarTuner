# 吉他调音器（Android）

Kotlin + Jetpack Compose 原生 Android 调音器：通过麦克风实时采集琴弦声音，用 **YIN 算法** 检测音高，
与目标弦标准音高比对，以一屏界面显示音名、偏高/偏低音分、指针与稳定度。支持**手动选弦**与**自动识别**两种模式，
以及**标准/自定义/预设调弦**与 **A4 校准**（420–460Hz，含 432/440 快捷按钮）。
调准时提供**提示音 + 震动反馈**（可开关、持久化），并带**音分走势曲线**辅助判断稳定度。

- 最低支持 **API 26**（Android 8.0），目标 **API 35**（≥34）
- 语言：Kotlin；UI：Jetpack Compose（Material 3，深色主题）
- 音频：`AudioRecord`，44100Hz / 单声道 / PCM Float，缓冲区 = 最小缓冲区 × 2
- DSP：纯 Kotlin 实现的 YIN（含抛物线插值），DSP 层与 UI 完全解耦、可 JVM 单测

---

## 一、工程结构

```
guitar-tuner/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   └── java/com/example/guitartuner/
        │       ├── MainActivity.kt                 # 入口：权限申请、前后台生命周期
        │       ├── audio/
        │       │   ├── AudioRecorder.kt            # AudioRecord 封装、PCM 缓冲管理
        │       │   ├── AudioTunerEngine.kt         # 采集+检测流水线（后台线程）
        │       │   └── TunerFeedback.kt            # 调准提示音+震动（开关持久化）
        │       ├── dsp/
        │       │   ├── YinDetector.kt              # YIN 算法（差分/CMND/阈值/抛物线插值/可选增强）
        │       │   └── PitchUtils.kt               # 频率→MIDI→音名/音分（纯函数）
        │       ├── tuning/
        │       │   ├── InstrumentConfig.kt         # 参数化调弦模型
        │       │   ├── StandardTunings.kt          # 标准调弦 EADGBE
        │       │   ├── PresetTunings.kt            # 预设调弦（Drop D/Drop C/Eb/Open G）
        │       │   └── TuningRepository.kt         # 调弦仓库（内置+自定义，SharedPreferences 持久化）
        │       └── ui/
        │           ├── TunerScreen.kt              # 主界面 + 调弦列表/编辑/校准弹窗
        │           ├── TunerViewModel.kt           # 状态管理（UI 态、反馈触发、音分历史）
        │           ├── PermissionGuide.kt          # 权限拒绝引导
        │           ├── components/
        │           │   ├── TuningDial.kt           # 半圆形调音表（弹簧动画指针、调准辉光）
        │           │   └── StabilityRing.kt        # 稳定度环形指示
        │           └── theme/                      # 深色主题
        └── test/java/com/example/guitartuner/dsp/
            ├── YinDetectorTest.kt
            └── PitchUtilsTest.kt
```

---

## 二、最小可运行完整路径（第七节第 1 条）

核心链路按以下顺序逐层叠加，均已在代码中完整实现、可直接编译运行：

1. **Manifest 权限** → `AndroidManifest.xml` 声明 `RECORD_AUDIO`；
2. **权限申请** → `MainActivity.kt` 运行时动态申请，`PermissionGuide.kt` 在拒绝时给出引导（不崩溃）；
3. **AudioRecord 启动** → `AudioRecorder.kt`（44100Hz/单声道/Float，2×最小缓冲）；
4. **YIN 检测** → `AudioTunerEngine.kt` 后台线程循环读取 + `YinDetector.detect()`；
5. **Compose 调音表更新** → `TunerViewModel` 将读数与目标弦比对 → `TunerScreen` 驱动 `TuningDial` 指针/颜色/音分文字；
6. **停止释放** → `stop()` 释放 `AudioRecord`，前后台由 `onAppBackground/onAppForeground` 接管。

在此基础上叠加：手动/自动模式、标准/自定义调弦（持久化）、A4 校准、稳定度指示。

---

## 三、YIN 实现路径声明与取舍

**选择：自行实现 YIN（纯 Kotlin），不封装 TarsosDSP。**

| 方案 | 取舍 |
| --- | --- |
| 自行实现 YIN（本项目） | 无第三方依赖、完全可控、可 JVM 单测；对吉他低音弦八度错误鲁棒；延迟 50–100ms 量级（满足 <150ms）。 |
| TarsosDSP 封装 YIN | 可复用成熟实现，但引入依赖、逻辑黑盒、测试不便；本项目不采用。 |
| HYBRID_YIN_FFT（可选增强） | 用 FFT 求粗周期再 YIN 细化的交叉校验，极限精度更高，但延迟升至 80–150ms；默认关闭，作为可切换选项保留思路。 |

未采用通用 FFT 峰值法作为主路径：其八度错误率高、低音弦不稳，不符合调音精度要求。

### 关键参数（具名常量 + 取值依据）

| 参数 | 值 | 依据 |
| --- | --- | --- |
| `sampleRate` | 44100 | 吉他 60–1500Hz 远低于 Nyquist，且为 Android 全平台基本保证采样率 |
| `MIN_FREQUENCY_HZ` | 60 | 覆盖 E2(82.41Hz) 及降调，留余量；τmax=735 |
| `MAX_FREQUENCY_HZ` | 1500 | 覆盖高把位，留余量；τmin=29 |
| `YIN_THRESHOLD` | 0.1 | YIN 论文建议 0.1–0.15，取 0.1 过滤人声/噪声 |
| 缓冲区 | 最小缓冲 × 2 | 平衡延迟与稳定性 |
| 手动锁定窗口 | ±50 音分 | 抑制邻弦/说话声干扰 |
| 绿区 | |cents|≤5 | 调准判定（界面辅以颜色+指针+文字） |
| 橙区 | 5<|cents|≤12 | 接近未准 |

`useAdaptiveThreshold`、`useMultiPeriodVote` 为可选增强（默认关闭），在 `YinDetector` 构造参数暴露。

---

## 四、平台碎片化风险与应对（代码内注释）

- **麦克风增益 / AGC 差异**：`AudioTunerEngine` 用 RMS 门限（0.004≈-48dBFS）过滤静音，必要时可再加归一化。
- **PCM Float 支持差异**：个别设备对 `ENCODING_PCM_FLOAT` 初始化失败，`AudioRecorder.start()` 抛出可读异常，引擎捕获后回传空读数，不崩溃；如需可回退 PCM_16BIT 并缩放。
- **Android 版本对 AudioRecord 行为影响**：使用 `READ_BLOCKING` 语义统一，低版本部分设备可能早返回较短帧，检测以实际 `read` 长度为准（`detect(frame, read)`）。

---

## 五、验收标准自检对照

| 验收项 | 实现 |
| --- | --- |
| 六根弦音名/方向/绿色区间 | `TunerViewModel.computeReading` + 音分比对 + 颜色/指针/文字三通道反馈 |
| 手动模式邻弦不干扰 | ±50 音分锁定窗口，出窗不驱动指针并提示 |
| 自定义调弦重启保留 | `TuningRepository` SharedPreferences JSON 持久化 |
| 切后台恢复不崩溃 | `onAppBackground/onAppForeground` 停止/恢复采集，`stop()` 释放 AudioRecord |
| YIN 单测全通过、E2 无八度 | `YinDetectorTest`（见下）；Python 复刻验证误差 <0.02Hz |

### 单测（纯 JVM）
```
./gradlew :app:testDebugUnitTest
```
覆盖：A4=440 识别、E2=82.41 识别且无八度误判、G3=196 识别；音分 ±20 符号正确、MIDI 换算正确。

> 说明：工程已包含 Gradle Wrapper（`gradlew` / `gradlew.bat` / `gradle/wrapper/`，Gradle 8.7，与 AGP 8.5.2 匹配）。
> 命令行构建直接运行 `./gradlew :app:testDebugUnitTest` 或 `./gradlew assembleDebug`；本地已用 Python 复刻
> YIN/音分数学逻辑完成离线验证（440/82.41/196/110/329.63Hz 均命中，误差 <0.02Hz，无八度误判）。
> 离线环境下可把 `gradle-wrapper.properties` 的 `distributionUrl` 改为 `file:///` 指向本地已下载的发行包（与工程内注释一致）。

---

## 六、v2 新增能力

1. **调准提示音 + 震动**：进入绿色区间（|音分|≤5 且锁定）瞬间触发一次，离开后再进入才重响（防连响）；
   提示音为 AudioTrack 合成的双音上行（E5→A5），比系统单音柔和；主界面底部提供「提示音/震动」开关并持久化。
2. **音分走势曲线**：最近约 3 秒音分轨迹（Canvas 绘制），含 ±5 绿带 / ±10 橙带与末点高亮，直观观察稳定度与漂移。
3. **预设调弦**：内置 Drop D（DADGBE）、Drop C（CGCFAD）、半音降（Eb）、Open G（DGDGBD），
   与标准调弦同为只读内置项（不可编辑/删除），自定义调弦仍可新建/编辑/删除并持久化。
4. **UI 美化**：调准时音名放大 + 径向辉光、状态文字徽章（等待声音/已调准/接近/偏差较大）、
   调音表调准外圈辉光、弦位胶囊按钮（自动模式下识别到的弦以橙色高亮，点按即切手动并选中）、
   拨弦提示文案、主按钮投影、调弦/校准改为描边按钮。
5. **体验细节**：自动模式点击弦位按钮自动切换为手动模式；监听停止时清空走势曲线。
