package com.a0527166880.offlinelens

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.max
import kotlin.math.min

class OfflineTranslator(private val context: android.content.Context) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val lock = Any()
    private var encoder: OrtSession? = null
    private var decoder: OrtSession? = null
    private var tokenizer: HuggingFaceTokenizer? = null

    // SMaLL-100 / M2M-100 language ids used by the tokenizer vocabulary.
    private val targetLangIds = mapOf(
        "ערבית" to 128006L,
        "עברית" to 128035L,
        "אנגלית" to 128022L,
        "סינית" to 128102L,
        "רוסית" to 128077L
    )

    fun translate(text: String, source: String, target: String): String {
        val clean = text.trim()
        if (clean.isEmpty() || source == target) return clean

        synchronized(lock) {
            ensureLoaded()
            val targetId = targetLangIds[target] ?: return clean

            // SMaLL-100 selects the target language by prepending its language token
            // to the SOURCE sequence. The tokenizer itself appends EOS only when
            // special tokens are requested, so we append EOS explicitly here.
            val encoded = tokenizer!!.encode(clean, false, false).ids
            val tokenCount = min(encoded.size, 160)
            val body = LongArray(tokenCount + 2)
            body[0] = targetId
            for (i in 0 until tokenCount) body[i + 1] = encoded[i]
            body[body.lastIndex] = 2L

            val inputIds = longTensor(body, longArrayOf(1L, body.size.toLong()))
            val attentionMask = longTensor(
                LongArray(body.size) { 1L },
                longArrayOf(1L, body.size.toLong())
            )
            val encoderInputs = hashMapOf<String, OnnxTensor>(
                "input_ids" to inputIds,
                "attention_mask" to attentionMask
            )

            val encoderResult = encoder!!.run(encoderInputs)
            val hidden = encoderResult[0] as OnnxTensor

            return try {
                decodeGreedy(hidden, body.size)
            } finally {
                hidden.close()
                encoderResult.close()
                inputIds.close()
                attentionMask.close()
            }
        }
    }

    private fun decodeGreedy(hidden: OnnxTensor, sourceLength: Int): String {
        val session = decoder!!
        val inputNames = session.inputInfo.keys.toList()
        val outputNames = session.outputInfo.keys.toList()
        val generated = ArrayList<Long>(48)
        var decoderIds = longArrayOf(2L)

        for (step in 0 until 48) {
            val inputs = HashMap<String, OnnxTensor>()
            val owned = ArrayList<OnnxTensor>()

            for (name in inputNames) {
                val lower = name.lowercase()
                when {
                    lower == "input_ids" -> {
                        val t = longTensor(
                            decoderIds,
                            longArrayOf(1L, decoderIds.size.toLong())
                        )
                        inputs[name] = t
                        owned += t
                    }

                    lower == "encoder_hidden_states" -> {
                        inputs[name] = hidden
                    }

                    lower == "encoder_attention_mask" -> {
                        val t = longTensor(
                            LongArray(sourceLength) { 1L },
                            longArrayOf(1L, sourceLength.toLong())
                        )
                        inputs[name] = t
                        owned += t
                    }

                    lower == "use_cache_branch" -> {
                        inputs[name] = booleanTensor(false)
                    }

                    lower.startsWith("past_key_values.") -> {
                        val t = emptyPastTensor(name, sourceLength)
                        inputs[name] = t
                        owned += t
                    }

                    else -> {
                        // Keep the merged decoder happy if an export exposes an
                        // additional attention mask. The current SMaLL-100 export
                        // uses only the standard inputs handled above.
                        val t = longTensor(longArrayOf(1L), longArrayOf(1L))
                        inputs[name] = t
                        owned += t
                    }
                }
            }

            val result = session.run(inputs)
            val logitsIndex = outputNames.indexOfFirst {
                it.lowercase() == "logits"
            }.let { if (it >= 0) it else 0 }
            val logits = result[logitsIndex] as OnnxTensor

            try {
                val next = argmaxLastStep(logits)
                if (next == 2L) break
                generated += next
                decoderIds += next
            } finally {
                logits.close()
                result.close()
                owned.forEach { it.close() }
            }
        }

        return tokenizer!!.decode(generated.toLongArray(), true).trim()
    }

    private fun emptyPastTensor(name: String, sourceLength: Int): OnnxTensor {
        val info = decoder!!.inputInfo[name]!!.info as TensorInfo
        val rawShape = info.shape
        val isEncoderCache = name.contains(".encoder.")
        val shape = LongArray(rawShape.size)

        for (i in rawShape.indices) {
            val fallback = when (i) {
                0 -> 1L
                1 -> 16L
                rawShape.lastIndex -> 64L
                else -> 0L
            }
            var dim = rawShape[i]
            if (dim < 0L) dim = fallback
            if (i == 2 && rawShape.size >= 4) {
                dim = if (isEncoderCache) sourceLength.toLong() else 0L
            }
            shape[i] = max(0L, dim)
        }

        val elements = shape.fold(1L) { a, b -> a * b }.toInt()
        return OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(FloatArray(elements)),
            shape
        )
    }

    private fun argmaxLastStep(tensor: OnnxTensor): Long {
        val shape = (tensor.info as TensorInfo).shape
        val vocab = shape.last().toInt()
        val total = shape.fold(1L) { acc, dim -> acc * dim }.toInt()
        val data = tensor.floatBuffer
        data.rewind()
        if (total > vocab) data.position(total - vocab)

        var best = 0
        var bestValue = Float.NEGATIVE_INFINITY
        for (i in 0 until vocab) {
            val value = data.get()
            if (value > bestValue) {
                bestValue = value
                best = i
            }
        }
        return best.toLong()
    }

    private fun longTensor(values: LongArray, shape: LongArray): OnnxTensor =
        OnnxTensor.createTensor(env, LongBuffer.wrap(values), shape)

    private fun booleanTensor(value: Boolean): OnnxTensor =
        OnnxTensor.createTensor(env, booleanArrayOf(value))

    private fun ensureLoaded() {
        if (encoder != null && decoder != null && tokenizer != null) return

        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()

        val encFile = copyAsset(
            "models/encoder_model.onnx",
            File(dir, "encoder_model.onnx")
        )
        val decFile = copyAsset(
            "models/decoder_model_merged.onnx",
            File(dir, "decoder_model_merged.onnx")
        )
        val tokFile = copyAsset(
            "models/tokenizer.json",
            File(dir, "tokenizer.json")
        )

        encoder = env.createSession(encFile.absolutePath, OrtSession.SessionOptions())
        decoder = env.createSession(decFile.absolutePath, OrtSession.SessionOptions())
        tokenizer = HuggingFaceTokenizer.newInstance(tokFile.toPath())
    }

    private fun copyAsset(asset: String, target: File): File {
        if (!target.exists() || target.length() == 0L) {
            context.assets.open(asset).use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output, 1024 * 1024)
                }
            }
        }
        return target
    }

    override fun close() {
        synchronized(lock) {
            tokenizer?.close()
            tokenizer = null
            decoder?.close()
            decoder = null
            encoder?.close()
            encoder = null
        }
    }
}
