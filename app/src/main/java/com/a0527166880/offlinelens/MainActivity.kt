package com.a0527166880.offlinelens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.toBitmap
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private val Bg = Color(0xFF0D1016)
private val Card = Color(0xFF171C25)
private val Accent = Color(0xFF65D6C8)

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent { LensApp() }
    }
}

@Composable
fun LensApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val ask = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    var source by remember { mutableStateOf("ערבית") }
    var target by remember { mutableStateOf("עברית") }
    var original by remember { mutableStateOf("") }
    var translated by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("כוון את המצלמה אל טקסט") }
    var busy by remember { mutableStateOf(false) }

    val translator = remember {
        OfflineTranslator(context.applicationContext)
    }

    val langs = listOf("עברית", "ערבית", "אנגלית", "סינית", "רוסית")

    DisposableEffect(Unit) {
        onDispose { translator.close() }
    }

    LaunchedEffect(Unit) {
        if (!granted) ask.launch(Manifest.permission.CAMERA)
    }

    Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Translate, null, tint = Accent)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("LensTranslate", color = Color.White, fontSize = 25.sp)
                    Text(
                        "תרגום מצלמה • 100% אופליין",
                        color = Color(0xFF9AA4B2),
                        fontSize = 13.sp
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                LanguageChip(source, langs) {
                    source = it
                    original = ""
                    translated = ""
                    status = "מחליף שפת מקור…"
                }

                IconButton(
                    onClick = {
                        val x = source
                        source = target
                        target = x
                        original = ""
                        translated = ""
                    }
                ) {
                    Icon(Icons.Rounded.SwapHoriz, null, tint = Accent)
                }

                LanguageChip(target, langs) {
                    target = it
                    translated = ""
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black, RoundedCornerShape(26.dp))
            ) {
                if (granted) {
                    key(source) {
                        CameraBox(source) { recognized ->
                            if (recognized.isBlank() || recognized == original) {
                                return@CameraBox
                            }

                            original = recognized
                            status = "טקסט זוהה"
                            busy = true

                            scope.launch(Dispatchers.Default) {
                                val result = runCatching {
                                    translator.translate(recognized, source, target)
                                }.getOrElse {
                                    "שגיאה במודל האופליין: " + (it.message ?: "לא ידוע")
                                }

                                launch(Dispatchers.Main) {
                                    translated = result
                                    status = "תרגום אופליין מוכן"
                                    busy = false
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("נדרשת הרשאת מצלמה", color = Color.White)
                    }
                }

                Surface(
                    color = Color(0xE610131A),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(18.dp)
                        .fillMaxWidth(0.92f)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            if (original.isBlank()) status else original,
                            color = Color.White,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (translated.isNotBlank()) {
                            Spacer(Modifier.height(9.dp))
                            Text(
                                translated,
                                color = Accent,
                                fontSize = 21.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (busy) {
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = Accent
                            )
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.CameraAlt, null, tint = Accent)
                Spacer(Modifier.width(8.dp))
                Text(
                    "OCR מקומי • תרגום מקומי • ללא רשת",
                    color = Color(0xFFB9C2CF),
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun LanguageChip(
    value: String,
    items: List<String>,
    onPick: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }

    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(value) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = Card,
                labelColor = Color.White
            )
        )

        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false }
        ) {
            items.forEach {
                DropdownMenuItem(
                    text = { Text(it) },
                    onClick = {
                        onPick(it)
                        open = false
                    }
                )
            }
        }
    }
}

@Composable
fun CameraBox(
    sourceLanguage: String,
    onText: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycle = context as ComponentActivity
    val executor = remember { Executors.newSingleThreadExecutor() }
    val lastRun = remember { mutableLongStateOf(0L) }
    val ocr = remember { OfflineOcr(context.applicationContext) }
    val currentOnText by rememberUpdatedState(onText)

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdownNow()
            ocr.close()
        }
    }

    AndroidView(
        factory = { c ->
            val previewView = PreviewView(c)
            val future = ProcessCameraProvider.getInstance(c)

            future.addListener({
                val provider = future.get()

                val preview = Preview.Builder()
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }

                val analyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analyzer.setAnalyzer(executor) { image ->
                    val now = System.currentTimeMillis()
                    if (now - lastRun.longValue < 900L) {
                        image.close()
                        return@setAnalyzer
                    }

                    lastRun.longValue = now

                    try {
                        val bitmap = image.toBitmap()
                        val scaled = if (bitmap.width > 1280) {
                            bitmap.scale(
                                1280,
                                (bitmap.height * 1280f / bitmap.width).toInt()
                            )
                        } else {
                            bitmap
                        }

                        val text = ocr.recognize(scaled, sourceLanguage)
                        if (text.isNotBlank()) {
                            ContextCompat.getMainExecutor(c).execute {
                                currentOnText(text)
                            }
                        }

                        if (scaled !== bitmap) scaled.recycle()
                        bitmap.recycle()
                    } catch (_: Throwable) {
                    } finally {
                        image.close()
                    }
                }

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycle,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analyzer
                )
            }, ContextCompat.getMainExecutor(c))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}
