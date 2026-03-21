package com.yasergirit.zikirmasterpro

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yasergirit.zikirmasterpro.ui.theme.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

internal data class PrayerTimeItem(
    val key: String,
    val nameTr: String,
    val nameEn: String,
    val nameDe: String = "",
    val nameAr: String = "",
    val time: String
)

// In-memory cache for prayer times (survives recomposition & tab switches)
internal object PrayerTimesCache {
    var prayerTimes: List<PrayerTimeItem> = emptyList()
    var hijriDate: String = ""
    var gregorianDate: String = ""
    var fetchedDateKey: String = "" // "yyyy-MM-dd" to refetch next day

    fun isValid(): Boolean {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return prayerTimes.isNotEmpty() && fetchedDateKey == today
    }

    fun update(times: List<PrayerTimeItem>, hijri: String, greg: String) {
        prayerTimes = times
        hijriDate = hijri
        gregorianDate = greg
        fetchedDateKey = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
}

@Composable
fun HomeTab(
    isDarkTheme: Boolean,
    selectedLanguage: String,
    locationPermissionGranted: Boolean = false
) {
    val context = LocalContext.current
    fun t(tr: String, en: String, de: String = "", ar: String = "") = when (selectedLanguage) {
        "en" -> en; "de" -> de.ifEmpty { en }; "ar" -> ar.ifEmpty { en }; else -> tr
    }

    var showVerseStory by remember { mutableStateOf(false) }
    var showHadithStory by remember { mutableStateOf(false) }
    var showQuoteStory by remember { mutableStateOf(false) }
    // Use cached data if available, otherwise start loading
    val cacheValid = PrayerTimesCache.isValid()
    var prayerTimes by remember { mutableStateOf(PrayerTimesCache.prayerTimes) }
    var hijriDate by remember { mutableStateOf(PrayerTimesCache.hijriDate) }
    var gregorianDate by remember { mutableStateOf(PrayerTimesCache.gregorianDate) }
    var isLoading by remember { mutableStateOf(!cacheValid) }
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var hasLocationPermission by remember { mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    ) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
    }

    // Fetch prayer times only if cache is not valid
    LaunchedEffect(hasLocationPermission, locationPermissionGranted) {
        if (!hasLocationPermission) {
            isLoading = false
            return@LaunchedEffect
        }
        // Skip API call if cache is still valid for today
        if (PrayerTimesCache.isValid()) {
            prayerTimes = PrayerTimesCache.prayerTimes
            hijriDate = PrayerTimesCache.hijriDate
            gregorianDate = PrayerTimesCache.gregorianDate
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        withContext(Dispatchers.IO) {
            val times = fetchPrayerTimesForHome(context)
            if (times != null) {
                PrayerTimesCache.update(times.first, times.second, times.third)
                prayerTimes = times.first
                hijriDate = times.second
                gregorianDate = times.third
            }
            isLoading = false
        }
    }

    // Update clock every second
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTimeMillis = System.currentTimeMillis()
        }
    }

    val currentPrayer = findCurrentPrayer(prayerTimes, currentTimeMillis)
    val nextPrayer = findNextPrayer(prayerTimes, currentTimeMillis)
    val countdown = calculateCountdown(nextPrayer, currentTimeMillis)

    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Prayer Times Section ──
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = primary)
            }
        } else if (!hasLocationPermission) {
            // No permission - show permission request
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDarkTheme) Color(0xFF1A2A1A) else Color(0xFFE8F5E9)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "📍", fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = t(
                            "Namaz vakitlerini görmek için konum izni gerekli",
                            "Location permission required to show prayer times",
                            "Standortberechtigung erforderlich für Gebetszeiten",
                            "إذن الموقع مطلوب لعرض أوقات الصلاة"
                        ),
                        color = onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primary)
                    ) {
                        Text(
                            text = t("Konum İzni Ver", "Grant Location Permission", "Standortzugriff erlauben", "منح إذن الموقع"),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        } else if (prayerTimes.isNotEmpty()) {
            // ── Prayer Times Card ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDarkTheme) Color(0xFF1A2A1A) else Color(0xFFE8F5E9)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Current/Next Prayer Name
                    val displayPrayerName = if (currentPrayer != null) {
                        when (selectedLanguage) { "en" -> currentPrayer.nameEn; "de" -> currentPrayer.nameDe.ifEmpty { currentPrayer.nameEn }; "ar" -> currentPrayer.nameAr.ifEmpty { currentPrayer.nameEn }; else -> currentPrayer.nameTr }
                    } else if (nextPrayer != null) {
                        when (selectedLanguage) { "en" -> nextPrayer.nameEn; "de" -> nextPrayer.nameDe.ifEmpty { nextPrayer.nameEn }; "ar" -> nextPrayer.nameAr.ifEmpty { nextPrayer.nameEn }; else -> nextPrayer.nameTr }
                    } else ""

                    Text(
                        text = displayPrayerName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = primary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Countdown label
                    Text(
                        text = if (currentPrayer != null) t("Vaktin Çıkmasına", "Time Remaining", "Verbleibende Zeit", "الوقت المتبقي")
                        else t("Vakte Kalan", "Time Until", "Zeit bis", "الوقت حتى"),
                        fontSize = 13.sp,
                        color = onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Countdown Timer
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = countdown.hours,
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Bold,
                            color = onSurface
                        )
                        Text(
                            text = ":",
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Bold,
                            color = onSurface
                        )
                        Text(
                            text = countdown.minutes,
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Bold,
                            color = onSurface
                        )
                        Text(
                            text = ":${countdown.seconds}",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Medium,
                            color = onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Dates
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = gregorianDate,
                            fontSize = 13.sp,
                            color = onSurfaceVariant
                        )
                        Text(
                            text = hijriDate,
                            fontSize = 13.sp,
                            color = onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Prayer Times Row ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        prayerTimes.forEach { prayer ->
                            val isActive = prayer.key == currentPrayer?.key || prayer.key == nextPrayer?.key
                            val textColor by animateColorAsState(
                                targetValue = if (isActive) primary else onSurfaceVariant,
                                animationSpec = tween(300),
                                label = "prayer_color"
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = when (selectedLanguage) { "en" -> prayer.nameEn; "de" -> prayer.nameDe.ifEmpty { prayer.nameEn }; "ar" -> prayer.nameAr.ifEmpty { prayer.nameEn }; else -> prayer.nameTr },
                                    fontSize = 11.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    color = textColor
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = prayer.time,
                                    fontSize = 14.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isActive) primary else onSurface
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Permission granted but API failed - show error with retry
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDarkTheme) Color(0xFF1A2A1A) else Color(0xFFE8F5E9)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "🕌", fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = t(
                            "Namaz vakitleri yüklenemedi. Lütfen tekrar deneyin.",
                            "Could not load prayer times. Please try again.",
                            "Gebetszeiten konnten nicht geladen werden. Bitte versuchen Sie es erneut.",
                            "تعذر تحميل أوقات الصلاة. يرجى المحاولة مرة أخرى."
                        ),
                        color = onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    var retryTrigger by remember { mutableStateOf(0) }
                    LaunchedEffect(retryTrigger) {
                        if (retryTrigger == 0) return@LaunchedEffect
                        isLoading = true
                        withContext(Dispatchers.IO) {
                            val times = fetchPrayerTimesForHome(context)
                            if (times != null) {
                                PrayerTimesCache.update(times.first, times.second, times.third)
                                prayerTimes = times.first
                                hijriDate = times.second
                                gregorianDate = times.third
                            }
                            isLoading = false
                        }
                    }
                    Button(
                        onClick = { retryTrigger++ },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primary)
                    ) {
                        Text(
                            text = t("Tekrar Dene", "Retry", "Wiederholen", "إعادة المحاولة"),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Categories (always visible) ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CategoryCircle(
                emoji = "📖",
                label = t("Ayet", "Verse", "Vers", "آية"),
                bgColor = if (isDarkTheme) Color(0xFF1B3A2A) else Color(0xFFE0F2E9),
                onClick = { showVerseStory = true }
            )
            CategoryCircle(
                emoji = "☪️",
                label = t("Hadis", "Hadith", "Hadith", "حديث"),
                bgColor = if (isDarkTheme) Color(0xFF2A1B3A) else Color(0xFFEDE0F2),
                onClick = { showHadithStory = true }
            )
            CategoryCircle(
                emoji = "✨",
                label = t("Özlü Sözler", "Wise Quotes", "Weisheiten", "حكم"),
                bgColor = if (isDarkTheme) Color(0xFF3A2A1B) else Color(0xFFF2EDE0),
                onClick = { showQuoteStory = true }
            )
        }

    }

    // Story Overlays (full screen on top)
    if (showVerseStory) {
        VerseStoryOverlay(
            selectedLanguage = selectedLanguage,
            onDismiss = { showVerseStory = false }
        )
    }
    if (showHadithStory) {
        HadithStoryOverlay(
            selectedLanguage = selectedLanguage,
            onDismiss = { showHadithStory = false }
        )
    }
    if (showQuoteStory) {
        QuoteStoryOverlay(
            selectedLanguage = selectedLanguage,
            onDismiss = { showQuoteStory = false }
        )
    }
    } // Box
}

@Composable
private fun CategoryCircle(
    emoji: String,
    label: String,
    bgColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emoji,
                fontSize = 32.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ── Data classes ──

private data class CountdownTime(
    val hours: String,
    val minutes: String,
    val seconds: String
)

// ── Helper functions ──

private fun parseTimeToCalendar(timeStr: String): Calendar? {
    val clean = timeStr.split(" ")[0]
    val parts = clean.split(":")
    if (parts.size < 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}

private fun findCurrentPrayer(prayers: List<PrayerTimeItem>, now: Long): PrayerTimeItem? {
    if (prayers.isEmpty()) return null
    // Find the last prayer whose time has passed
    var current: PrayerTimeItem? = null
    for (prayer in prayers) {
        val cal = parseTimeToCalendar(prayer.time) ?: continue
        if (cal.timeInMillis <= now) {
            current = prayer
        }
    }
    return current
}

private fun findNextPrayer(prayers: List<PrayerTimeItem>, now: Long): PrayerTimeItem? {
    if (prayers.isEmpty()) return null
    for (prayer in prayers) {
        val cal = parseTimeToCalendar(prayer.time) ?: continue
        if (cal.timeInMillis > now) {
            return prayer
        }
    }
    // All prayers passed, next is tomorrow's first prayer
    return prayers.firstOrNull()
}

private fun calculateCountdown(nextPrayer: PrayerTimeItem?, now: Long): CountdownTime {
    if (nextPrayer == null) return CountdownTime("00", "00", "00")
    val cal = parseTimeToCalendar(nextPrayer.time) ?: return CountdownTime("00", "00", "00")

    var diff = cal.timeInMillis - now
    if (diff < 0) {
        // Next day
        diff += 24 * 60 * 60 * 1000
    }

    val totalSeconds = diff / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return CountdownTime(
        hours = String.format("%02d", hours),
        minutes = String.format("%02d", minutes),
        seconds = String.format("%02d", seconds)
    )
}

internal suspend fun fetchPrayerTimesForHome(context: Context): Triple<List<PrayerTimeItem>, String, String>? {
    // Get location
    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        != PackageManager.PERMISSION_GRANTED &&
        ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        != PackageManager.PERMISSION_GRANTED
    ) {
        return null
    }

    // 1) Hızlı: Cache'li konum (anında döner)
    val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    val cachedLocation = try {
        suspendCoroutine<android.location.Location?> { cont ->
            fusedClient.lastLocation
                .addOnSuccessListener { loc -> cont.resume(loc) }
                .addOnFailureListener { cont.resume(null) }
        }
    } catch (e: Exception) { null }

    // 2) Cache yoksa LocationManager dene (yine hızlı)
    val fallbackLocation = cachedLocation ?: try {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
    } catch (e: Exception) { null }

    // 3) Hiç cache yoksa aktif konum al (yavaş ama gerekli)
    val location = fallbackLocation ?: try {
        suspendCoroutine<android.location.Location?> { cont ->
            fusedClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                CancellationTokenSource().token
            ).addOnSuccessListener { loc -> cont.resume(loc) }
                .addOnFailureListener { cont.resume(null) }
        }
    } catch (e: Exception) { null }
        ?: return null

    val lat = location.latitude
    val lng = location.longitude

    // Load prayer method preference
    val method = try {
        val key = stringPreferencesKey("prayer_method")
        context.dataStore.data.first()[key] ?: "13"
    } catch (e: Exception) { "13" }

    return try {
        val url = "https://islamicapi.com/api/v1/prayer-time/?lat=$lat&lon=$lng&method=$method&school=1&api_key=9ych8xGEPXNqi1SQHny2zXBJK34Jym1FAPdOpp7HLyW6qYgZ"

        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val body = response.body?.string()

        if (response.isSuccessful && body != null) {
            val json = JSONObject(body)
            val data = json.getJSONObject("data")
            val timings = data.getJSONObject("times")

            val prayerList = listOf(
                PrayerTimeItem("Fajr", "İmsak", "Fajr", "Fajr", "الفجر", timings.getString("Fajr")),
                PrayerTimeItem("Sunrise", "Güneş", "Sunrise", "Sunrise", "الشروق", timings.getString("Sunrise")),
                PrayerTimeItem("Dhuhr", "Öğle", "Dhuhr", "Dhuhr", "الظهر", timings.getString("Dhuhr")),
                PrayerTimeItem("Asr", "İkindi", "Asr", "Asr", "العصر", timings.getString("Asr")),
                PrayerTimeItem("Maghrib", "Akşam", "Maghrib", "Maghrib", "المغرب", timings.getString("Maghrib")),
                PrayerTimeItem("Isha", "Yatsı", "Isha", "Isha", "العشاء", timings.getString("Isha"))
            )

            // Hijri date
            val hijri = data.getJSONObject("date").getJSONObject("hijri")
            val hijriDay = hijri.getString("day")
            val hijriMonthAr = hijri.getJSONObject("month").getString("ar")
            val hijriYear = hijri.getString("year")
            val hijriStr = "$hijriDay $hijriMonthAr $hijriYear"

            // Gregorian date
            val gregorian = data.getJSONObject("date").getJSONObject("gregorian")
            val gregDay = gregorian.getString("day")
            val gregYear = gregorian.getString("year")

            val cal = Calendar.getInstance()
            val monthName = SimpleDateFormat("MMMM", Locale("tr", "TR")).format(cal.time)
            val dayName = SimpleDateFormat("EEEE", Locale("tr", "TR")).format(cal.time)
            val gregStr = "$gregDay $monthName $gregYear $dayName"

            Triple(prayerList, hijriStr, gregStr)
        } else null
    } catch (e: Exception) {
        Log.e("HomeScreen", "API error", e)
        null
    }
}
