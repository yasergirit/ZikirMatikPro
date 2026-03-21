package com.yasergirit.zikirmasterpro

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.yasergirit.zikirmasterpro.ui.theme.*
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.Locale

private data class PrayerToggleState(
    val key: String,
    val nameTr: String,
    val nameEn: String,
    val nameDe: String,
    val nameAr: String,
    val hasOnTime: Boolean = true,
    val onTimeDefault: Boolean = true,
    val beforeTimeDefault: Boolean = false
)

private val prayerItems = listOf(
    PrayerToggleState("fajr", "İmsak", "Fajr", "Fajr", "الفجر"),
    PrayerToggleState("sunrise", "Güneş", "Sunrise", "Sunrise", "الشروق", hasOnTime = false),
    PrayerToggleState("dhuhr", "Öğle", "Dhuhr", "Dhuhr", "الظهر", onTimeDefault = false, beforeTimeDefault = false),
    PrayerToggleState("asr", "İkindi", "Asr", "Asr", "العصر", onTimeDefault = false, beforeTimeDefault = false),
    PrayerToggleState("maghrib", "Akşam", "Maghrib", "Maghrib", "المغرب"),
    PrayerToggleState("isha", "Yatsı", "Isha", "Isha", "العشاء")
)

@Composable
fun NotificationSettingsScreen(
    selectedLanguage: String,
    onFinish: () -> Unit
) {
    fun t(tr: String, en: String, de: String = "", ar: String = "") = when (selectedLanguage) {
        "en" -> en; "de" -> de.ifEmpty { en }; "ar" -> ar.ifEmpty { en }; else -> tr
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Bildirim izni (Android 13+)
    var notifPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notifPermissionGranted = granted }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notifPermissionGranted) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Toggle state'leri
    val onTimeStates = remember {
        mutableStateMapOf<String, Boolean>().apply {
            prayerItems.forEach { p ->
                val key = booleanPreferencesKey("prayer_notif_${p.key}")
                val value = try {
                    runBlocking { context.dataStore.data.first()[key] ?: p.onTimeDefault }
                } catch (_: Exception) { p.onTimeDefault }
                put(p.key, value)
            }
        }
    }

    val beforeTimeStates = remember {
        mutableStateMapOf<String, Boolean>().apply {
            prayerItems.forEach { p ->
                val key = booleanPreferencesKey("prayer_notif_before_${p.key}")
                val value = try {
                    runBlocking { context.dataStore.data.first()[key] ?: p.beforeTimeDefault }
                } catch (_: Exception) { p.beforeTimeDefault }
                put(p.key, value)
            }
        }
    }

    // DataStore'a kaydetme fonksiyonları
    fun saveOnTime(prayerKey: String, enabled: Boolean) {
        onTimeStates[prayerKey] = enabled
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[booleanPreferencesKey("prayer_notif_$prayerKey")] = enabled
            }
        }
    }

    fun saveBeforeTime(prayerKey: String, enabled: Boolean) {
        beforeTimeStates[prayerKey] = enabled
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[booleanPreferencesKey("prayer_notif_before_$prayerKey")] = enabled
            }
        }
    }

    // ── Şehir adını al ──
    var cityName by remember { mutableStateOf("") }

    @SuppressLint("MissingPermission")
    fun fetchCityName() {
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        fusedClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                scope.launch {
                    val name = withContext(Dispatchers.IO) {
                        try {
                            val geocoder = Geocoder(context, Locale.getDefault())
                            @Suppress("DEPRECATION")
                            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                            addresses?.firstOrNull()?.let { addr ->
                                addr.subAdminArea ?: addr.adminArea ?: addr.locality ?: ""
                            } ?: ""
                        } catch (_: Exception) { "" }
                    }
                    cityName = name
                }
            }
        }
    }

    LaunchedEffect(Unit) { fetchCityName() }

    // ── UI ──
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF2C5F6E), Color(0xFF3A7B8C), Color(0xFF2C5F6E))
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Şehir adı
            if (cityName.isNotEmpty()) {
                Text(
                    text = cityName,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Alt başlık
            Text(
                text = t("Bildirim Ayarları", "Notification Settings", "Benachrichtigungseinstellungen", "إعدادات الإشعارات"),
                fontSize = if (cityName.isNotEmpty()) 16.sp else 24.sp,
                fontWeight = if (cityName.isNotEmpty()) FontWeight.Normal else FontWeight.Bold,
                color = if (cityName.isNotEmpty()) Color.White.copy(alpha = 0.8f) else Color.White,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Beyaz kart
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    // Tablo başlıkları
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Vakit ismi alanı
                        Spacer(modifier = Modifier.weight(1f))

                        // Vakit girişi
                        Text(
                            text = t("Vakit\nGirişi", "On Pray\nTime", "Zur\nGebetszeit", "وقت\nالصلاة"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Emerald,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp,
                            modifier = Modifier.width(90.dp)
                        )

                        // Vakitten önce
                        Text(
                            text = t("Vakitten\nÖnce", "Before Pray\nTime", "Vor der\nGebetszeit", "قبل وقت\nالصلاة"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Emerald,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp,
                            modifier = Modifier.width(90.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = Color(0xFFE0E0E0))

                    // Her vakit için satır
                    prayerItems.forEach { prayer ->
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Vakit ismi
                            Text(
                                text = when (selectedLanguage) {
                                    "en" -> prayer.nameEn
                                    "de" -> prayer.nameDe
                                    "ar" -> prayer.nameAr
                                    else -> prayer.nameTr
                                },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF333333),
                                modifier = Modifier.weight(1f)
                            )

                            // Vakit girişi toggle
                            Box(modifier = Modifier.width(90.dp), contentAlignment = Alignment.Center) {
                                if (prayer.hasOnTime) {
                                    Switch(
                                        checked = onTimeStates[prayer.key] ?: prayer.onTimeDefault,
                                        onCheckedChange = { saveOnTime(prayer.key, it) },
                                        colors = SwitchDefaults.colors(
                                            checkedTrackColor = Emerald,
                                            checkedThumbColor = Color.White
                                        )
                                    )
                                }
                            }

                            // Vakitten önce toggle
                            Box(modifier = Modifier.width(90.dp), contentAlignment = Alignment.Center) {
                                Switch(
                                    checked = beforeTimeStates[prayer.key] ?: prayer.beforeTimeDefault,
                                    onCheckedChange = { saveBeforeTime(prayer.key, it) },
                                    colors = SwitchDefaults.colors(
                                        checkedTrackColor = Emerald,
                                        checkedThumbColor = Color.White
                                    )
                                )
                            }
                        }

                        HorizontalDivider(color = Color(0xFFF0F0F0))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Bilgilendirme
                    Text(
                        text = t(
                            "\"Vakitten Önce\" seçili olan vakitler için 30 dakika öncesinden bildirim alırsınız.",
                            "You will receive a notification 30 minutes before for prayers with \"Before Pray Time\" enabled.",
                            "Sie erhalten 30 Minuten vorher eine Benachrichtigung für Gebete mit aktivierter Vorab-Benachrichtigung.",
                            "ستتلقى إشعاراً قبل 30 دقيقة من أوقات الصلاة المحددة."
                        ),
                        fontSize = 12.sp,
                        color = Color(0xFF999999),
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // OK butonu
            Button(
                onClick = onFinish,
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald)
            ) {
                Text(
                    text = t("TAMAM", "OK", "OK", "موافق"),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
