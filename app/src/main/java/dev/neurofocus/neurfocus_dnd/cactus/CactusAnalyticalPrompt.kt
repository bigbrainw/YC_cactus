package dev.neurofocus.neurfocus_dnd.cactus

import dev.neurofocus.neurfocus_dnd.brain.domain.BrainState
import dev.neurofocus.neurfocus_dnd.brain.domain.EegBand
import org.json.JSONArray
import org.json.JSONObject

private const val SYSTEM_PROMPT =
    "Reply with exactly one short sentence, max 18 words. " +
        "Describe relative EEG band trends only; no diagnosis, treatment, or medical claims."

fun cactusAnalyticalMessagesJson(live: BrainState.Live): String {
    val p = live.bandPowers
    fun v(b: EegBand) = p[b] ?: 0f
    val summary = buildString {
        append("Fp1 proxy — relative power ")
        EegBand.entries.forEach { b ->
            append(b.name)
            append('=')
            append("%.2f".format(v(b)))
            append(' ')
        }
        append("focusHeuristic=")
        append("%.2f".format(live.focus.value))
    }
    val arr = JSONArray()
    arr.put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
    arr.put(JSONObject().put("role", "user").put("content", summary))
    return arr.toString()
}

fun cactusAnalyticalOptionsJson(): String =
    JSONObject()
        .put("max_tokens", 28)
        .put("temperature", 0.2)
        .toString()
