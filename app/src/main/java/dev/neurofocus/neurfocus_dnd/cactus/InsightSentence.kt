package dev.neurofocus.neurfocus_dnd.cactus

/**
 * Keeps UI copy to a single short sentence (first sentence boundary, else hard cap).
 */
fun firstSentenceOnly(text: String, maxChars: Int = 240): String {
    val t = text.trim().replace('\n', ' ')
    if (t.isEmpty()) return t
    var depth = 0
    for (i in t.indices) {
        when (t[i]) {
            '(' -> depth++
            ')' -> if (depth > 0) depth--
        }
        if (depth == 0 && (t[i] == '.' || t[i] == '!' || t[i] == '?')) {
            return t.substring(0, i + 1).trim()
        }
    }
    return if (t.length <= maxChars) t else t.take(maxChars).trimEnd() + "…"
}
