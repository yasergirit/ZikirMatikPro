package com.yasergirit.zikirmasterpro

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.request.CachePolicy
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.yasergirit.zikirmasterpro.ui.theme.ZikirMasterProTheme
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

data class CounterSave(
    val value: Int,
    val timestamp: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZikirMasterProTheme {
                CounterScreen()
            }
        }
    }
}

@Composable
private fun CounterScreen() {
    var count by rememberSaveable { mutableIntStateOf(0) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var savedCounters by remember { mutableStateOf(listOf<CounterSave>()) }
    var isImageLoaded by remember { mutableStateOf(false) }
    var logTextColor by remember { mutableStateOf(Color(0xFF2C4350)) }
    
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("tr", "TR"))
    val islamicImages = listOf(
        "https://images.unsplash.com/photo-1542816417-0983c9c9ad53",
        "https://images.unsplash.com/photo-1591604129939-f1efa4d9f7fa",
        "https://images.unsplash.com/photo-1564769625905-50e93615e769",
        "https://images.unsplash.com/photo-1580418827493-f2b22c0a76cb",
        "https://images.unsplash.com/photo-1584286595398-a59f21d7620b"
    )
    val randomImage = remember { islamicImages.random() }
    
    val imageRequest = ImageRequest.Builder(context)
        .data(randomImage)
        .allowHardware(false)
        .crossfade(true)
        .diskCachePolicy(CachePolicy.ENABLED)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .build()

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Gradient background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A4D2E),
                            Color(0xFF4F9F6B),
                            Color(0xFF1A4D2E)
                        )
                    )
                )
        )
        
        // Background image
        SubcomposeAsyncImage(
            model = imageRequest,
            contentDescription = "İslami Arkaplan",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.5f,
            onSuccess = { success ->
                val bitmap = (success.result.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val safeBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
                        bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    } else {
                        bitmap
                    }
                    val dominant = Palette.from(safeBitmap)
                        .generate()
                        .getDominantColor(AndroidColor.BLACK)
                    val isDark = ColorUtils.calculateLuminance(dominant) < 0.5
                    logTextColor = if (isDark) Color.White else Color.Black
                }
                isImageLoaded = true
            },
            onError = {
                isImageLoaded = true
            }
        )
        
        // Loading or Main Content
        if (!isImageLoaded) {
            // Loading Screen
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(60.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Yükleniyor...",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            // Main Content
        Box(modifier = Modifier.fillMaxSize()) {
            // Settings icon at top-right
            IconButton(
                onClick = { /* TODO: Open settings */ },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Ayarlar",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Spacer(modifier = Modifier.height(200.dp))
        
        // Digital display
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(80.dp)
                .background(Color(0xFFA8C5B8), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = count.toString().padStart(0, '0'),
                color = Color(0xFF2C4350),
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Buttons row
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.size(160.dp)) {
                // Main counter button
                Button(
                    onClick = { count += 1 },
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White
                    ),
                    border = BorderStroke(2.dp, Color(0xFF2C4350))
                ) {
                    // Empty button - just the circle
                }

                // Reset button
                Button(
                    onClick = { showResetConfirm = true },
                    modifier = Modifier
                        .size(30.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 12.dp, y = 0.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF8FA9B3)
                    ),
                    border = BorderStroke(2.dp, Color(0xFF2C4350)),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "R",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Save button
        Button(
            onClick = {
                val timestamp = dateFormat.format(Date())
                savedCounters = savedCounters + CounterSave(count, timestamp)
            },
            modifier = Modifier
                .fillMaxWidth(0.50f)
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF8FA9B3)
            ),
            border = BorderStroke(2.dp, Color(0xFF2C4350))
        ) {
            Text(
                text = "KAYDET",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(30.dp))

        // Saved counters list header and list
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .weight(1f)
                .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .padding(vertical = 12.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Kaydedilen Zikirler",
                    color = logTextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(savedCounters.reversed()) { item ->
                        Text(
                            text = "${item.timestamp} - ${item.value}",
                            color = logTextColor,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        }
        }
        }
    }

    // Reset confirmation dialog
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = {
                Text(text = "Sıfırla")
            },
            text = {
                Text(text = "Sıfırlamak istediğinize emin misiniz?")
            },
            confirmButton = {
                Button(
                    onClick = { showResetConfirm = false }
                ) {
                    Text("Hayır")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        count = 0
                        showResetConfirm = false
                    }
                ) {
                    Text("Evet")
                }
            }
        )
    }
}

