package dev.neurofocus.neurfocus_dnd.cactus

data class InsightUiState(
    val text: String,
    val loading: Boolean,
    val error: String?,
    val usedNativeModel: Boolean,
) {
    companion object {
        val Idle = InsightUiState(
            text = "Connect your headband for analytical insight.",
            loading = false,
            error = null,
            usedNativeModel = false,
        )
    }
}
