package com.yasergirit.zikirmasterpro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.annotation.TargetApi
import android.app.Activity
import android.os.Vibrator
import android.os.VibrationEffect
import android.media.ToneGenerator
import android.media.AudioManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.request.CachePolicy
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.yasergirit.zikirmasterpro.ui.theme.ZikirMasterProTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

data class CounterSave(
    val value: Int,
    val timestamp: String
)

private val Context.dataStore by preferencesDataStore(name = "counter_data")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZikirMasterProTheme {
                CounterScreen(this)
            }
        }
    }
}

private fun saveCountersToDataStore(context: android.content.Context, counters: List<CounterSave>) {
    val gson = Gson()
    val json = gson.toJson(counters)
    try {
        val key = stringPreferencesKey("saved_counters")
        // This should be done in a coroutine, but for simplicity we'll use runBlocking
        kotlinx.coroutines.runBlocking {
            context.dataStore.edit { preferences ->
                preferences[key] = json
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private suspend fun loadCountersFromDataStore(context: android.content.Context): List<CounterSave> {
    val gson = Gson()
    val key = stringPreferencesKey("saved_counters")
    return try {
        val jsonString = context.dataStore.data.first()[key] ?: return emptyList()
        val type = object : TypeToken<List<CounterSave>>() {}.type
        gson.fromJson(jsonString, type) ?: emptyList()
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

private fun saveThemeToDataStore(context: android.content.Context, isDark: Boolean) {
    try {
        val key = androidx.datastore.preferences.core.booleanPreferencesKey("is_dark_theme")
        kotlinx.coroutines.runBlocking {
            context.dataStore.edit { preferences ->
                preferences[key] = isDark
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private suspend fun loadThemeFromDataStore(context: android.content.Context): Boolean {
    val key = androidx.datastore.preferences.core.booleanPreferencesKey("is_dark_theme")
    return try {
        context.dataStore.data.first()[key] ?: true // Default to dark theme
    } catch (e: Exception) {
        e.printStackTrace()
        true
    }
}

private fun saveSoundEnabledToDataStore(context: android.content.Context, isEnabled: Boolean) {
    try {
        val key = androidx.datastore.preferences.core.booleanPreferencesKey("sound_enabled")
        kotlinx.coroutines.runBlocking {
            context.dataStore.edit { preferences ->
                preferences[key] = isEnabled
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private suspend fun loadSoundEnabledFromDataStore(context: android.content.Context): Boolean {
    val key = androidx.datastore.preferences.core.booleanPreferencesKey("sound_enabled")
    return try {
        context.dataStore.data.first()[key] ?: true // Default to sound enabled
    } catch (e: Exception) {
        e.printStackTrace()
        true
    }
}

@TargetApi(26)
@Composable
private fun CounterScreen(activity: android.app.Activity) {
    var count by rememberSaveable { mutableIntStateOf(0) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var savedCounters by remember { mutableStateOf(listOf<CounterSave>()) }
    var isImageLoaded by remember { mutableStateOf(false) }
    var logTextColor by remember { mutableStateOf(Color(0xFF2C4350)) }
    var isDarkTheme by remember { mutableStateOf(true) }
    var isSoundEnabled by remember { mutableStateOf(true) }
    
    val context = LocalContext.current
    
    // Load saved counters from DataStore on app launch
    LaunchedEffect(Unit) {
        val loadedCounters = loadCountersFromDataStore(context)
        savedCounters = loadedCounters
        val loadedTheme = loadThemeFromDataStore(context)
        isDarkTheme = loadedTheme
        val loadedSound = loadSoundEnabledFromDataStore(context)
        isSoundEnabled = loadedSound
    }
    
    // Save counters to DataStore whenever they change
    LaunchedEffect(savedCounters) {
        saveCountersToDataStore(context, savedCounters)
    }
    
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

    if (showSettingsScreen) {
        // Settings Screen
        SettingsScreen(
            onBack = { showSettingsScreen = false },
            onDeleteAll = { showDeleteAllConfirm = true },
            savedCounters = savedCounters,
            isDarkTheme = isDarkTheme,
            onThemeChange = { 
                isDarkTheme = it
                saveThemeToDataStore(context, it)
            },
            isSoundEnabled = isSoundEnabled,
            onSoundChange = {
                isSoundEnabled = it
                saveSoundEnabledToDataStore(context, it)
            }
        )
    } else {
        // Main Screen
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Gradient background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                    Brush.verticalGradient(
                        colors = if (isDarkTheme) {
                            listOf(
                                Color(0xFF1A4D2E),
                                Color(0xFF4F9F6B),
                                Color(0xFF1A4D2E)
                            )
                        } else {
                            listOf(
                                Color(0xFFE8F5E9),
                                Color(0xFFA5D6A7),
                                Color(0xFFE8F5E9)
                            )
                        }
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
            // Top right icons (Settings and Power)
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { showSettingsScreen = true },
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Ayarlar",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                IconButton(
                    onClick = {
                        (context as? Activity)?.finish()
                    },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Kapat",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Spacer(modifier = Modifier.height(30.dp))
        
        // Ramadan decoration header image
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data("https://images.unsplash.com/photo-1609709228789-60af8e1a01cf")
                .allowHardware(false)
                .crossfade(true)
                .build(),
            contentDescription = "Hayırlı Ramazanlar",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(120.dp)
        )
        
        Spacer(modifier = Modifier.height(30.dp))
        
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
                    onClick = { 
                        count += 1
                        // Titreşim efekti
                        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(50)
                        }
                        // Ses efekti (sadece aktifse)
                        if (isSoundEnabled) {
                            try {
                                val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    toneGenerator.release()
                                }, 150)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White
                    ),
                    border = BorderStroke(2.dp, Color.White)
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
                    border = BorderStroke(2.dp, Color.White),
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
            border = BorderStroke(2.dp, Color.White)
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
                    text = "Kayıtlar",
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
                    items(savedCounters.reversed(), key = { "${it.timestamp}-${it.value}" }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${item.timestamp} - ${item.value}",
                                color = Color.Black,
                                fontSize = 12.sp
                            )
                            IconButton(
                                onClick = {
                                    savedCounters = savedCounters.filter { it != item }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Sil",
                                    tint = Color.Red,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
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

    // Delete All Confirmation Dialog
    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = {
                Text(text = "Tüm Kayıtları Sil")
            },
            text = {
                Text(text = "Tüm kayıtları silmek istediğinize emin misiniz? Bu işlem geri alınamaz.")
            },
            confirmButton = {
                Button(
                    onClick = { showDeleteAllConfirm = false }
                ) {
                    Text("Hayır")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        savedCounters = emptyList()
                        showDeleteAllConfirm = false
                        showSettingsScreen = false
                    }
                ) {
                    Text("Evet")
                }
            }
        )
    }
}

@Composable
private fun SettingsScreen(
    onBack: () -> Unit,
    onDeleteAll: () -> Unit,
    savedCounters: List<CounterSave>,
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    isSoundEnabled: Boolean,
    onSoundChange: (Boolean) -> Unit
) {
    // İstatistik hesaplama
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR"))
    val calendar = Calendar.getInstance()
    val today = Calendar.getInstance()
    
    // Bugün
    val todayCount = savedCounters.count { counter ->
        try {
            val counterDate = dateFormat.parse(counter.timestamp)
            if (counterDate != null) {
                calendar.time = counterDate
                calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                calendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
            } else false
        } catch (e: Exception) {
            false
        }
    }
    
    // Bu hafta
    val weekStart = Calendar.getInstance()
    weekStart.set(Calendar.DAY_OF_WEEK, weekStart.firstDayOfWeek)
    weekStart.set(Calendar.HOUR_OF_DAY, 0)
    weekStart.set(Calendar.MINUTE, 0)
    weekStart.set(Calendar.SECOND, 0)
    
    val weekCount = savedCounters.count { counter ->
        try {
            val counterDate = dateFormat.parse(counter.timestamp)
            counterDate != null && counterDate.after(weekStart.time)
        } catch (e: Exception) {
            false
        }
    }
    
    // Bu ay
    val monthStart = Calendar.getInstance()
    monthStart.set(Calendar.DAY_OF_MONTH, 1)
    monthStart.set(Calendar.HOUR_OF_DAY, 0)
    monthStart.set(Calendar.MINUTE, 0)
    monthStart.set(Calendar.SECOND, 0)
    
    val monthCount = savedCounters.count { counter ->
        try {
            val counterDate = dateFormat.parse(counter.timestamp)
            counterDate != null && counterDate.after(monthStart.time)
        } catch (e: Exception) {
            false
        }
    }
    
    // Tema renklerini ayarla
    val backgroundColor = if (isDarkTheme) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1A4D2E),
                Color(0xFF4F9F6B),
                Color(0xFF1A4D2E)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFE8F5E9),
                Color(0xFFA5D6A7),
                Color(0xFFE8F5E9)
            )
        )
    }
    val textColor = if (isDarkTheme) Color.White else Color(0xFF2C4350)
    val cardBackgroundColor = if (isDarkTheme) Color(0xFF2C4350).copy(alpha = 0.7f) else Color(0xFFF0F0F0)
    val accentColor = if (isDarkTheme) Color(0xFF4F9F6B) else Color(0xFF1A4D2E)
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // Back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Geri",
                        tint = textColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "Ayarlar",
                color = textColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // İstatistikler
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(bottom = 20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = cardBackgroundColor
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "İstatistikler",
                        color = textColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$todayCount",
                                color = accentColor,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Bugün",
                                color = textColor.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$weekCount",
                                color = accentColor,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Bu Hafta",
                                color = textColor.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$monthCount",
                                color = accentColor,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Bu Ay",
                                color = textColor.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "Toplam Kayıt: ${savedCounters.size}",
                        color = textColor.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Tema Seçimi
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = cardBackgroundColor
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Koyu Tema",
                        color = textColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = onThemeChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF1A4D2E),
                            checkedTrackColor = Color(0xFF4F9F6B),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.LightGray
                        )
                    )
                }
            }
            
            // Ses Efektleri
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(bottom = 20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = cardBackgroundColor
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ses Efektleri",
                        color = textColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Switch(
                        checked = isSoundEnabled,
                        onCheckedChange = onSoundChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF1A4D2E),
                            checkedTrackColor = Color(0xFF4F9F6B),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.LightGray
                        )
                    )
                }
            }

            // Delete All Button
            Button(
                onClick = onDeleteAll,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE74C3C)
                ),
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(50.dp)
            ) {
                Text("Tüm Kayıtları Sil", color = Color.White, fontSize = 16.sp)
            }

        }
    }
}

