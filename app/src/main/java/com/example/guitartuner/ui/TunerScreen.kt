package com.example.guitartuner.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.guitartuner.dsp.PitchUtils
import com.example.guitartuner.tuning.InstrumentConfig
import com.example.guitartuner.tuning.StandardTunings
import com.example.guitartuner.ui.components.StabilityRing
import com.example.guitartuner.ui.components.TuningDial
import com.example.guitartuner.ui.theme.Accent
import com.example.guitartuner.ui.theme.Danger
import com.example.guitartuner.ui.theme.DialTick
import com.example.guitartuner.ui.theme.Neutral
import com.example.guitartuner.ui.theme.TextPrimary
import com.example.guitartuner.ui.theme.TextSecondary
import com.example.guitartuner.ui.theme.TunerBackground
import com.example.guitartuner.ui.theme.TunerSurface
import com.example.guitartuner.ui.theme.Warn
import kotlin.math.abs
import kotlin.math.round
import com.example.guitartuner.BuildConfig



/** 主界面：一屏完成调音。ViewModel 由 MainActivity 注入。 */
@Composable
fun TunerScreen(vm: TunerViewModel) {
    val r = vm.reading

    // 监听期间保持屏幕常亮（谱架场景）
    val view = LocalView.current
    DisposableEffect(vm.isListening) {
        if (vm.isListening) view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // 计算界面状态
    val hasSignal = r.hasSignal
    val usable = if (vm.mode == TunerMode.MANUAL) r.locked else !r.octaveHint
    val status = when {
        !hasSignal || !usable -> TunerStatus.WAITING
        abs(r.cents) <= 5.0 -> TunerStatus.IN_TUNE
        abs(r.cents) <= 12.0 -> TunerStatus.CLOSE
        else -> TunerStatus.OFF
    }
    val dialCents = if (hasSignal && usable) r.cents else 0.0
    val statusColor = when (status) {
        TunerStatus.WAITING -> Neutral
        TunerStatus.IN_TUNE -> Accent
        TunerStatus.CLOSE -> Warn
        TunerStatus.OFF -> Danger
    }
    val statusLabel = when (status) {
        TunerStatus.WAITING -> "等待声音"
        TunerStatus.IN_TUNE -> "已调准"
        TunerStatus.CLOSE -> "接近"
        TunerStatus.OFF -> "偏差较大"
    }
    val highlightIndex = if (vm.mode == TunerMode.MANUAL) vm.selectedStringIndex else r.detectedStringIndex

    // 弹窗开关
    var showTuningList by remember { mutableStateOf(false) }
    var showCalibration by remember { mutableStateOf(false) }
    var editorTarget by remember { mutableStateOf<EditorTarget?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TunerBackground)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 顶部：标题 + 模式切换
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("调音器", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(vm.tuning.name, color = TextSecondary, fontSize = 13.sp, maxLines = 1)
            }
            ModeSwitch(vm.mode, onChange = { vm.mode = it })

            Spacer(Modifier.weight(1f))   // 把版本信息推到屏幕底部

            Text(
                text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Spacer(Modifier.height(2.dp))

        // 音名（调准时放大 + 辉光）
        NoteDisplay(note = r.noteName, color = statusColor, inTune = status == TunerStatus.IN_TUNE)
        Spacer(Modifier.height(2.dp))

        // 状态徽章（文字语义，不依赖颜色）
        StatusBadge(label = statusLabel, color = statusColor)
        Spacer(Modifier.height(6.dp))

        // 半圆形调音表（调准时辉光）
        TuningDial(
            cents = dialCents,
            active = status != TunerStatus.WAITING,
            inTune = status == TunerStatus.IN_TUNE,
            modifier = Modifier.fillMaxWidth(0.94f)
        )

        // 音分偏差 + 频率
        Text(text = formatCents(r, status), color = statusColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            text = if (hasSignal) String.format("%.1f Hz", r.frequency) else "等待声音…",
            color = TextSecondary,
            fontSize = 13.sp
        )

        // 拨弦提示 / 八度 / 锁定提示
        if (!hasSignal) {
            Text(
                if (vm.mode == TunerMode.MANUAL)
                    "拨动 ${vm.tuning.strings[vm.selectedStringIndex].label}（${vm.tuning.strings[vm.selectedStringIndex].noteName}）"
                else "拨动任意一根弦",
                color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)
            )
        } else if (vm.mode == TunerMode.AUTO && r.octaveHint) {
            Text("检测不稳定，请靠近琴弦", color = Warn, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        } else if (vm.mode == TunerMode.MANUAL && !r.locked) {
            Text(
                "偏离锁定范围，请拨 ${vm.tuning.strings[vm.selectedStringIndex].noteName}",
                color = Neutral, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            Spacer(Modifier.height(4.dp))
        }

        // 稳定度 + 音分走势
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StabilityRing(stability = r.stability, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("稳定度", color = TextSecondary, fontSize = 11.sp)
                Text(
                    if (hasSignal) "${(r.stability * 100).toInt()}% · 置信 ${(r.confidence * 100).toInt()}%"
                    else "—",
                    color = if (r.stability > 0.6f) Accent else TextSecondary,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            CentsHistoryCard(
                history = vm.centsHistory,
                modifier = Modifier.weight(1.6f).height(48.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        // 弦位选择（手动高亮选中；自动高亮识别到的弦；自动模式下点击即切到手动并选中）
        StringSelector(
            config = vm.tuning,
            highlight = highlightIndex,
            isManual = vm.mode == TunerMode.MANUAL,
            onSelect = { i ->
                if (vm.mode == TunerMode.AUTO) vm.mode = TunerMode.MANUAL
                vm.selectString(i)
            }
        )

        Spacer(Modifier.weight(1f))

        // 反馈开关（提示音 / 震动）
        FeedbackRow(vm)
        Spacer(Modifier.height(8.dp))

        // 开始/停止
        Button(
            onClick = vm::toggleListening,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (vm.isListening) Danger else Accent,
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Text(if (vm.isListening) "停止监听" else "开始监听", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }

        // 调弦选择 / 校准入口
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            OutlinedButton(onClick = { showTuningList = true }) { Text("调弦") }
            OutlinedButton(onClick = { showCalibration = true }) { Text("校准 · ${vm.a4Hz.toInt()} Hz") }
        }
    }

    if (showTuningList) {
        TuningListDialog(
            current = vm.tuning,
            tunings = vm.allTunings(),
            isBuiltIn = vm::isBuiltIn,
            onSelect = { vm.selectTuning(it) },
            onCreate = {
                showTuningList = false
                editorTarget = EditorTarget(
                    editing = null,
                    midis = StandardTunings.EADGBE.strings.map { it.midiNote }
                )
            },
            onEdit = { t ->
                showTuningList = false
                editorTarget = EditorTarget(editing = t, midis = t.strings.map { it.midiNote })
            },
            onDismiss = { showTuningList = false }
        )
    }

    if (showCalibration) {
        CalibrationDialog(a4Hz = vm.a4Hz, onSet = vm::setA4, onDismiss = { showCalibration = false })
    }

    editorTarget?.let { target ->
        TuningEditorDialog(
            editing = target.editing,
            initialMidis = target.midis,
            onSave = { id, name, midis ->
                vm.saveCustom(id, name, midis)
                editorTarget = null
            },
            onDelete = target.editing?.let { editing ->
                { id -> vm.deleteTuning(id); editorTarget = null }
            },
            onDismiss = { editorTarget = null }
        )
    }
}

/** 编辑器目标：新建（editing=null）或编辑既有自定义调弦。 */
private data class EditorTarget(val editing: InstrumentConfig?, val midis: List<Int>)

// ---------- 音名显示（调准辉光 + 放大） ----------

@Composable
private fun NoteDisplay(note: String, color: Color, inTune: Boolean, modifier: Modifier = Modifier) {
    val glow by animateFloatAsState(
        targetValue = if (inTune) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "noteGlow"
    )
    val scale by animateFloatAsState(
        targetValue = if (inTune) 1.1f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "noteScale"
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        if (glow > 0.02f) {
            Box(
                Modifier
                    .size(150.dp)
                    .graphicsLayer { alpha = glow * 0.9f }
                    .background(
                        Brush.radialGradient(listOf(color.copy(alpha = 0.30f), Color.Transparent)),
                        CircleShape
                    )
            )
        }
        Text(
            note,
            color = color,
            fontSize = 52.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
        )
    }
}

// ---------- 状态徽章 ----------

@Composable
private fun StatusBadge(label: String, color: Color) {
    Text(
        label,
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 3.dp)
    )
}

// ---------- 音分走势曲线 ----------

@Composable
private fun CentsHistoryCard(history: List<Float>, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(TunerSurface, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text("音分走势", color = TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val w = size.width
            val h = size.height
            // ±10 橙带 / ±5 绿带（0 音分 = 中线）
            drawRect(Warn.copy(alpha = 0.10f), topLeft = Offset(0f, h / 2f - 0.10f * h), size = Size(w, 0.20f * h))
            drawRect(Accent.copy(alpha = 0.15f), topLeft = Offset(0f, h / 2f - 0.05f * h), size = Size(w, 0.10f * h))
            drawLine(DialTick, Offset(0f, h / 2f), Offset(w, h / 2f), strokeWidth = 1f)

            if (history.size >= 2) {
                val n = history.size
                val path = Path()
                history.forEachIndexed { i, c ->
                    val x = if (n == 1) w / 2f else w * i / (n - 1).toFloat()
                    val y = (h / 2f - (c / 50f) * (h / 2f)).coerceIn(0f, h)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                val lastCents = history.last()
                val lineColor = when {
                    abs(lastCents) <= 5f -> Accent
                    abs(lastCents) <= 12f -> Warn
                    else -> Danger
                }
                drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                // 末点
                val lx = w * (n - 1) / (n - 1).toFloat()
                val ly = (h / 2f - (history.last() / 50f) * (h / 2f)).coerceIn(0f, h)
                drawCircle(lineColor, radius = 3.dp.toPx(), center = Offset(lx, ly))
            }
        }
    }
}

// ---------- 工具 ----------

private fun formatCents(r: PitchReading, status: TunerStatus): String {
    if (!r.hasSignal || status == TunerStatus.WAITING) return "—"
    val c = round(r.cents).toInt()
    return when {
        c == 0 -> "0 cents"
        c > 0 -> "+$c cents"
        else -> "$c cents"
    }
}

// ---------- 模式切换 ----------

@Composable
private fun ModeSwitch(mode: TunerMode, onChange: (TunerMode) -> Unit) {
    Row(Modifier.background(TunerSurface, RoundedCornerShape(20.dp)).padding(4.dp)) {
        val options = listOf(TunerMode.MANUAL to "手动", TunerMode.AUTO to "自动")
        options.forEach { (m, label) ->
            val selected = mode == m
            Box(
                modifier = Modifier
                    .background(if (selected) Accent.copy(alpha = 0.9f) else Color.Transparent, RoundedCornerShape(16.dp))
                    .clickable { onChange(m) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (selected) TunerBackground else TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

// ---------- 弦位选择 ----------

@Composable
private fun StringSelector(
    config: InstrumentConfig,
    highlight: Int?,
    isManual: Boolean,
    onSelect: (Int) -> Unit
) {
    val highlightColor = if (isManual) Accent else Warn
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        config.strings.forEachIndexed { i, s ->
            val isHighlight = i == highlight
            Box(
                modifier = Modifier
                    .clickable { onSelect(i) }
                    .graphicsLayer { alpha = if (isHighlight) 1f else 0.4f }
                    .background(
                        if (isHighlight) highlightColor.copy(alpha = 0.22f) else Color.Transparent,
                        RoundedCornerShape(12.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isHighlight) highlightColor.copy(alpha = 0.65f) else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        s.label,
                        color = if (isHighlight) highlightColor else TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(s.noteName, color = TextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}

// ---------- 反馈开关 ----------

@Composable
private fun FeedbackRow(vm: TunerViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(TunerSurface, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("提示音", color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            Switch(
                checked = vm.soundEnabled,
                onCheckedChange = { vm.updateSoundEnabled(it) }
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("震动", color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            Switch(
                checked = vm.vibrateEnabled,
                onCheckedChange = { vm.updateVibrateEnabled(it) }
            )
        }
    }
}

// ---------- 调弦列表弹窗 ----------

@Composable
private fun TuningListDialog(
    current: InstrumentConfig,
    tunings: List<InstrumentConfig>,
    isBuiltIn: (String) -> Boolean,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    onEdit: (InstrumentConfig) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = TunerSurface) {
            Column(Modifier.padding(16.dp)) {
                Text("调弦", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(tunings, key = { it.id }) { t ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(t.id) }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                t.name,
                                modifier = Modifier.weight(1f),
                                color = if (t.id == current.id) Accent else TextPrimary,
                                fontWeight = if (t.id == current.id) FontWeight.Bold else FontWeight.Normal
                            )
                            if (t.id == current.id) Text("✓", color = Accent)
                            if (!isBuiltIn(t.id)) {
                                TextButton(onClick = { onEdit(t) }) { Text("编辑") }
                            }
                        }
                    }
                }
                HorizontalDivider(color = TextSecondary.copy(alpha = 0.3f))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onCreate) { Text("新建调弦", color = Accent) }
                }
            }
        }
    }
}

// ---------- 校准弹窗 ----------

@Composable
private fun CalibrationDialog(a4Hz: Double, onSet: (Double) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(a4Hz) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = TunerSurface) {
            Column(Modifier.padding(20.dp)) {
                Text("A4 参考频率", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("${value.toInt()} Hz", color = Accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = value.toFloat(),
                    onValueChange = { value = it.toDouble() },
                    valueRange = 420f..460f,
                    steps = 39
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { value = 432.0 },
                        modifier = Modifier.border(1.dp, if (value == 432.0) Accent else TextSecondary.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    ) { Text("432", color = if (value == 432.0) Accent else TextPrimary) }
                    TextButton(
                        onClick = { value = 440.0 },
                        modifier = Modifier.border(1.dp, if (value == 440.0) Accent else TextSecondary.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    ) { Text("440", color = if (value == 440.0) Accent else TextPrimary) }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { onSet(value); onDismiss() }) { Text("确定") }
                }
            }
        }
    }
}

// ---------- 自定义调弦编辑器 ----------

private const val MIN_MIDI = 28  // E1 附近，容纳降调
private const val MAX_MIDI = 88  // E6 附近，容纳高把位

@Composable
private fun TuningEditorDialog(
    editing: InstrumentConfig?,
    initialMidis: List<Int>,
    onSave: (String?, String, List<Int>) -> Unit,
    onDelete: ((String) -> Unit)?,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(editing?.name ?: "") }
    val midis = remember { initialMidis.toMutableStateList() }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = TunerSurface) {
            Column(Modifier.padding(16.dp)) {
                Text(if (editing == null) "新建自定义调弦" else "编辑调弦", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("调弦名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                midis.forEachIndexed { i, midi ->
                    val note = PitchUtils.midiToNoteName(midi.toDouble())
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${midis.size - i}弦", color = TextSecondary, modifier = Modifier.width(52.dp))
                        Text(note, color = Accent, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { midis[i] = (midi - 1).coerceIn(MIN_MIDI, MAX_MIDI) }) { Text("−", fontSize = 18.sp) }
                        Text("$midi", color = TextPrimary, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                        TextButton(onClick = { midis[i] = (midi + 1).coerceIn(MIN_MIDI, MAX_MIDI) }) { Text("+", fontSize = 18.sp) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = TextSecondary.copy(alpha = 0.3f))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.weight(1f))
                    if (onDelete != null && editing != null) {
                        TextButton(onClick = { onDelete(editing.id) }) { Text("删除", color = Danger) }
                    }
                    Button(onClick = { onSave(editing?.id, name, midis.toList()) }) { Text("保存") }
                }
            }
        }
    }
}
