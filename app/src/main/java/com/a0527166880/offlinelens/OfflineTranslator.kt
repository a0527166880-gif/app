package com.a0527166880.offlinelens

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.File
import java.io.FileOutputStream
import java.nio.LongBuffer
import kotlin.math.min

class OfflineTranslator(private val context: android.content.Context) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val lock = Any()
    private var encoder: OrtSession? = null
    private var decoder: OrtSession? = null
    private var tokenizer: HuggingFaceTokenizer? = null

    private val langIds = mapOf(
        "עברית" to 256067L,
        "ערבית" to 256087L,
        "אנגלית" to 256047L,
        "סינית" to 256167L,
        "רוסית" to 256150L
    )

    fun translate(text: String, source: String, target: String): String {
        val clean = text.trim()
        if (clean.isEmpty() || source == target) return clean

        synchronized(lock) {
            ensureLoaded()

            val src = langIds[source] ?: return clean
            val tgt = langIds[target] ?: return clean

            val encoded = tokenizer!!.encode(clean, false, false).ids
            val tokenCount = min(encoded.size, 240)
            val body = LongArray(tokenCount + 2)
            body[0] = src
            for (i in 0 until tokenCount) body[i + 1] = encoded[i]
            body[body.size - 1] = 2L

            val encoderInputs = HashMap<String, OnnxTensor>()
            encoderInputs["input_ids"] = longTensor(body, longArrayOf(1, body.size.toLong()))
            encoderInputs["attention_mask"] = longTensor(
                LongArray(body.size) { 1L },
                longArrayOf(1, body.size.toLong())
            )

            val result = encoder!!.run(encoderInputs)
            val hidden = result[0] as OnnxTensor
            return try {
                decode(hidden, body.size, tgt)
            } finally {
                hidden.close()
                result.close()
                encoderInputs.values.forEach { it.close() }
            }
        }
    }

    private fun decode(hidden: OnnxTensor, sourceLength: Int, targetLangId: Long): String {
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
                    lower.contains("input_ids") -> {
                        val tensor = longTensor(
                            decoderIds,
                            longArrayOf(1, decoderIds.size.toLong())
                        )
                        inputs[name] = tensor
                        owned += tensor
                    }
                    lower.contains("encoder_hidden_states") -> inputs[name] = hidden
                    lower.contains("encoder_attention_mask") -> {
                        val tensor = longTensor(
                            LongArray(sourceLength) { 1L },
                            longArrayOf(1, sourceLength.toLong())
                        )
                        inputs[name] = tensor
                        owned += tensor
                    }
                    else -> {
                        val tensor = longTensor(longArrayOf(0L), longArrayOf(1))
                        inputs[name] = tensor
                        owned += tensor
                    }
                }
            }

            val result = session.run(inputs)
            val logitsIndex = outputNames.indexOfFirst {
                it.lowercase().contains("logits")
            }.let { if (it >= 0) it else 0 }
            val logits = result[logitsIndex] as OnnxTensor

            try {
                val next = argmaxLastStep(logits)
                val chosen = if (step == 0) targetLangId else next

                if (chosen == 2L) break

                generated += chosen
                decoderIds += chosen
            } finally {
                logits.close()
                result.close()
                owned.forEach { it.close() }
            }
        }

        return tokenizer!!.decode(generated.toLongArray(), true).trim()
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

    private fun ensureLoaded() {
        if (encoder != null && decoder != null && tokenizer != null) return

        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()

        val encFile = copyAsset(
            "models/encoder_model_int8.onnx",
            File(dir, "encoder_model_int8.onnx")
        )
        val decFile = copyAsset(
            "models/decoder_model_int8.onnx",
            File(dir, "decoder_model_int8.onnx")
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
