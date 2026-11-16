package com.example.guitartuner.tuning

import android.content.Context
import com.example.guitartuner.dsp.PitchUtils
import org.json.JSONArray
import org.json.JSONObject

/**
 * 调弦仓库：标准调弦 + 用户自定义调弦。
 * 自定义调弦以 JSON 持久化到 SharedPreferences，重启应用后仍存在。
 */
class TuningRepository(context: Context) {

    private val prefs =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val all = LinkedHashMap<String, InstrumentConfig>().apply {
        put(StandardTunings.EADGBE.id, StandardTunings.EADGBE)
        PresetTunings.all.forEach { put(it.id, it) }
    }

    init {
        loadCustom()
    }

    val standard: InstrumentConfig get() = StandardTunings.EADGBE

    fun allTunings(): List<InstrumentConfig> = all.values.toList()

    fun get(id: String): InstrumentConfig? = all[id]

    /** 是否为内置调弦（标准 + 预设）：不可编辑、不可删除。 */
    fun isBuiltIn(id: String): Boolean =
        id == StandardTunings.EADGBE.id || PresetTunings.all.any { it.id == id }

    /** 保存（新增或覆盖）自定义调弦并持久化。 */
    fun save(config: InstrumentConfig): InstrumentConfig {
        all[config.id] = config
        persist()
        return config
    }

    /** 删除自定义调弦（内置调弦不可删）。 */
    fun delete(id: String): Boolean {
        if (isBuiltIn(id)) return false
        if (all.remove(id) != null) {
            persist()
            return true
        }
        return false
    }

    /** 由 MIDI 列表构造自定义调弦配置；id 为空时自动生成唯一 id。 */
    fun buildCustom(id: String?, name: String, midis: List<Int>): InstrumentConfig {
        require(midis.isNotEmpty()) { "调弦至少包含一根弦" }
        val strings = midis.mapIndexed { index, midi ->
            val note = PitchUtils.midiToNoteName(midi.toDouble())
            val num = midis.size - index // index 0 = 最粗弦 = 第 N 弦
            TuningString(label = "${num}弦", noteName = note, midiNote = midi)
        }
        val configId = id ?: ("custom_" + System.currentTimeMillis())
        return InstrumentConfig(id = configId, name = name.ifBlank { "自定义调弦" }, strings = strings)
    }

    private fun persist() {
        val arr = JSONArray()
        all.values.filter { !isBuiltIn(it.id) }.forEach { c ->
            val strings = JSONArray()
            c.strings.forEach { s -> strings.put(JSONObject().put("midi", s.midiNote)) }
            arr.put(JSONObject()
                .put("id", c.id)
                .put("name", c.name)
                .put("strings", strings))
        }
        prefs.edit().putString(KEY_CONFIGS, arr.toString()).apply()
    }

    private fun loadCustom() {
        val json = prefs.getString(KEY_CONFIGS, null) ?: return
        runCatching {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val strings = obj.getJSONArray("strings")
                val list = mutableListOf<TuningString>()
                for (j in 0 until strings.length()) {
                    val midi = strings.getJSONObject(j).getInt("midi")
                    val note = PitchUtils.midiToNoteName(midi.toDouble())
                    list += TuningString(label = "${strings.length() - j}弦", noteName = note, midiNote = midi)
                }
                all[id] = InstrumentConfig(id, name, list)
            }
        }
    }

    private companion object {
        const val PREFS_NAME = "guitar_tuner"
        const val KEY_CONFIGS = "custom_tunings"
    }
}
