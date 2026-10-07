package com.a0527166880.offlinelens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

private val Bg=Color(0xFF0D1016); private val Card=Color(0xFF171C25); private val Accent=Color(0xFF65D6C8)

class MainActivity: ComponentActivity(){ override fun onCreate(b:Bundle?){super.onCreate(b); setContent{LensApp()}} }

@Composable fun LensApp(){
 val ctx=LocalContext.current
 var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) }
 val ask=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted=it}
 var source by remember{mutableStateOf("ערבית")}; var target by remember{mutableStateOf("עברית")}; var text by remember{mutableStateOf("כוון את המצלמה אל הטקסט")}; var translated by remember{mutableStateOf("התרגום יופיע כאן")}
 val langs=listOf("עברית","ערבית","אנגלית","סינית","רוסית")
 LaunchedEffect(Unit){if(!granted)ask.launch(Manifest.permission.CAMERA)}
 Surface(color=Bg,modifier=Modifier.fillMaxSize()){
  Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
   Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()){Icon(Icons.Rounded.Translate,null,tint=Accent);Spacer(Modifier.width(10.dp));Column{Text("LensTranslate",color=Color.White,fontSize=25.sp);Text("תרגום מהיר • מקומי • ללא אינטרנט",color=Color(0xFF9AA4B2),fontSize=13.sp)}}
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){LanguageChip(source,langs){source=it};IconButton(onClick={val x=source;source=target;target=x}){Icon(Icons.Rounded.SwapHoriz,null,tint=Accent)};LanguageChip(target,langs){target=it}}
   Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black,RoundedCornerShape(26.dp))){
    if(granted) CameraBox{recognized->text=recognized; translated=offlineTranslate(recognized,source,target)} else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("נדרשת הרשאת מצלמה",color=Color.White)}
    Surface(color=Color(0xDD10131A),shape=RoundedCornerShape(18.dp),modifier=Modifier.align(Alignment.BottomCenter).padding(14.dp).fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(text,color=Color.White,fontSize=16.sp);Spacer(Modifier.height(7.dp));Text(translated,color=Accent,fontSize=20.sp)}}
   }
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){Icon(Icons.Rounded.CameraAlt,null,tint=Accent);Spacer(Modifier.width(8.dp));Text("הצג את הטקסט המתורגם מעל המקור",color=Color(0xFFB9C2CF),fontSize=13.sp)}
  }
 }
}

@Composable fun LanguageChip(value:String,items:List<String>,onPick:(String)->Unit){var open by remember{mutableStateOf(false)};Box{AssistChip(onClick={open=true},label={Text(value)},colors=AssistChipDefaults.assistChipColors(containerColor=Card,labelColor=Color.White));DropdownMenu(open,{open=false}){items.forEach{DropdownMenuItem(text={Text(it)},onClick={onPick(it);open=false})}}}}

@Composable fun CameraBox(onText:(String)->Unit){val ctx=LocalContext.current;val lifecycle=(ctx as ComponentActivity);AndroidView(factory={c->val pv=PreviewView(c);val future=ProcessCameraProvider.getInstance(c);future.addListener({val p=future.get();val preview=Preview.Builder().build().also{it.surfaceProvider=pv.surfaceProvider};val analyzer=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);analyzer.setAnalyzer(Executors.newSingleThreadExecutor()){image->val media=image.image;if(media==null){image.close();return@setAnalyzer};recognizer.process(InputImage.fromMediaImage(media,image.imageInfo.rotationDegrees)).addOnSuccessListener{r->if(r.text.isNotBlank())onText(r.text)}.addOnCompleteListener{image.close()}};p.unbindAll();p.bindToLifecycle(lifecycle,CameraSelector.DEFAULT_BACK_CAMERA,preview,analyzer)},ContextCompat.getMainExecutor(c));pv},Modifier.fillMaxSize())}

fun offlineTranslate(text:String,source:String,target:String):String{if(source==target)return text;return when{text.isBlank()->"";else->"תרגום מקומי מוכן — מנוע המודלים יופעל מחבילת האופליין."}}
