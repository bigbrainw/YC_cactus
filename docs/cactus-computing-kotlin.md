This `llms.txt` file is designed to give an LLM (or a developer) a high-density, structured overview of the **Cactus** library for on-device AI. It strips away the prose and focuses on architecture, integration requirements, and API signatures.

---

# Cactus: On-Device AI for Android & Kotlin Multiplatform (KMP)

Cactus is a high-performance library for running AI models on-device using a Kotlin API built over a C FFI. It supports LLMs, Vision-Language Models (VLM), Audio models, and Vector Databases.

## Architecture & Integration

### Core Components
* **Engine:** C-based FFI. Handles are passed as `Long` values (pointers).
* **Weights:** Pre-converted models available at `huggingface.co/Cactus-Compute`.
* **Build Output:** `libcactus.so` (Android) and `libcactus.a` (iOS/Static).

### Android Integration
1.  Place `libcactus.so` in `app/src/main/jniLibs/arm64-v8a/`.
2.  Copy `Cactus.kt` to `app/src/main/java/com/cactus/`.
3.  **Requirements:** API 21+, arm64-v8a.

### KMP Integration
* **Android:** Same as above.
* **iOS:** Link `libcactus-device.a` via `cinterop`.
* **Source Sets:**
    * `commonMain`: `Cactus.common.kt`
    * `androidMain`: `Cactus.android.kt`
    * `iosMain`: `Cactus.ios.kt`

---

## Core API Reference

### 1. Lifecycle Management
All sessions must be manually initialized and destroyed to prevent memory leaks.

```kotlin
fun cactusInit(modelPath: String, corpusDir: String?, cacheIndex: Boolean): Long
fun cactusDestroy(model: Long)
fun cactusReset(model: Long) // Clears KV cache/state
fun cactusStop(model: Long)  // Interrupts current inference
```

### 2. LLM & Multimodal Generation
Handles text completion, streaming, and tool use. Input/Output is primarily JSON.

**Basic Completion:**
`cactusComplete(model, messagesJson, optionsJson, toolsJson, callback, pcmData)`

* **Vision/Audio:** For VLMs or Audio-native models, include `"images": ["path"]` or `"audio": ["path"]` inside the message JSON.
* **Prefill:** Use `cactusPrefill(...)` to process system prompts or long contexts into the KV cache before generation to reduce latency.

### 3. Audio & Speech
Supports Whisper, Moonshine, and Parakeet models.

* **Transcription:** `cactusTranscribe` (File or PCM 16kHz mono).
* **Streaming:** 1. `cactusStreamTranscribeStart(model, options)` -> returns `streamHandle`.
    2. `cactusStreamTranscribeProcess(streamHandle, pcmData)`.
    3. `cactusStreamTranscribeStop(streamHandle)`.
* **VAD/Diarization:** `cactusVad` and `cactusDiarize` return JSON with timestamps and speaker labels.
* **Speaker ID:** `cactusEmbedSpeaker` returns a 256-dim embedding for voice matching.

### 4. Vector Index & RAG
Local vector database for retrieval-augmented generation.

```kotlin
fun cactusIndexInit(indexDir: String, embeddingDim: Int): Long
fun cactusIndexAdd(index, ids, documents, embeddings, metadatas): Int
fun cactusIndexQuery(index, embedding, optionsJson): String
```

---

## Technical Constraints & Standards

### Data Formats
* **Messages:** Standard OpenAI-style JSON: `[{"role": "user", "content": "..."}]`.
* **Options:** JSON string (e.g., `{"temperature": 0.7, "max_tokens": 256}`).
* **Audio:** Raw PCM must be 16 kHz mono.

### Memory & Performance
* **Handles:** Always `Long`.
* **Error Handling:** Use `cactusGetLastError(): String` if an operation fails or `cactusInit` throws.
* **Threading:** Inference is blocking; use `cactusStop` from a separate thread to cancel.

### Build Commands
```bash
# Setup environment
source ./setup
# Build for Android
cactus build --android
# Override libcurl path if necessary
CACTUS_CURL_ROOT=/path/to/curl cactus build --android
```

---

## Usage Patterns

### Tool Use (Function Calling)
1. Provide `toolsJson` defining the function schema.
2. The model returns `<|tool_call_start|>...<|tool_call_end|>`.
3. Execute logic and return a message with `"role": "tool"`.

### Streaming Transcription
```kotlin
val stream = cactusStreamTranscribeStart(model, "{\"custom_vocabulary\": [\"Cactus\"]}")
val partialText = cactusStreamTranscribeProcess(stream, audioChunk)
val finalResult = cactusStreamTranscribeStop(stream)
```

---

This documentation should provide sufficient context for an LLM to assist you in writing integration code or troubleshooting Cactus-based projects. Given your experience with BCI and the Model Context Protocol (MCP), you'll likely find the `Long` handle and FFI structure familiar.

Is there a specific part of the integration (like the `cinterop` setup for KMP) you'd like to dive deeper into?