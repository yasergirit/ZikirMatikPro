package com.yasergirit.zikirmasterpro

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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

// In-memory cache for prayer times + weather + mosques (survives recomposition & tab switches)
internal object PrayerTimesCache {
    var prayerTimes: List<PrayerTimeItem> = emptyList()
    var hijriDate: String = ""
    var gregorianDate: String = ""
    var fetchedDateKey: String = "" // "yyyy-MM-dd" to refetch next day
    var cityName: String = ""
    var weatherDays: List<WeatherDay> = emptyList()
    var nearbyMosques: List<NearbyMosque> = emptyList()

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

@OptIn(ExperimentalMaterial3Api::class)
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
    var cityName by remember { mutableStateOf(PrayerTimesCache.cityName) }
    var weatherDays by remember { mutableStateOf(PrayerTimesCache.weatherDays) }
    var nearbyMosques by remember { mutableStateOf(PrayerTimesCache.nearbyMosques) }
    var selectedMosque by remember { mutableStateOf<NearbyMosque?>(null) }
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

    // Fetch city name, weather, and mosques (skip if cache valid)
    LaunchedEffect(hasLocationPermission, locationPermissionGranted) {
        if (!hasLocationPermission) return@LaunchedEffect
        // Cache tamamen doluysa API çağrısı yapma
        if (PrayerTimesCache.cityName.isNotEmpty() && PrayerTimesCache.nearbyMosques.isNotEmpty()) {
            cityName = PrayerTimesCache.cityName
            weatherDays = PrayerTimesCache.weatherDays
            nearbyMosques = PrayerTimesCache.nearbyMosques
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                // Konum alınamazsa 2 saniye bekleyip tekrar dene
                var loc = getLocationForHome(context)
                if (loc == null) {
                    delay(2000)
                    loc = getLocationForHome(context)
                }
                if (loc == null) return@withContext
                // City name via Geocoder
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(loc.first, loc.second, 1)
                    cityName = addresses?.firstOrNull()?.let { addr ->
                        addr.subAdminArea ?: addr.adminArea ?: addr.locality ?: ""
                    } ?: ""
                } catch (_: Exception) {}
                // 3-day weather via Open-Meteo (free, no key)
                try {
                    val weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=${loc.first}&longitude=${loc.second}&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=3"
                    val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build()
                    val resp = client.newCall(Request.Builder().url(weatherUrl).build()).execute()
                    val body = resp.body?.string()
                    if (resp.isSuccessful && body != null) {
                        val daily = JSONObject(body).getJSONObject("daily")
                        val dates = daily.getJSONArray("time")
                        val codes = daily.getJSONArray("weather_code")
                        val maxTemps = daily.getJSONArray("temperature_2m_max")
                        val minTemps = daily.getJSONArray("temperature_2m_min")
                        val days = mutableListOf<WeatherDay>()
                        val cal = Calendar.getInstance()
                        val langLocale = when (selectedLanguage) {
                            "en" -> Locale.ENGLISH
                            "de" -> Locale.GERMAN
                            "ar" -> Locale("ar")
                            else -> Locale("tr", "TR")
                        }
                        val dayFmt = SimpleDateFormat("EEE", langLocale)
                        for (i in 0 until minOf(3, dates.length())) {
                            val dateParts = dates.getString(i).split("-")
                            cal.set(dateParts[0].toInt(), dateParts[1].toInt() - 1, dateParts[2].toInt())
                            days.add(WeatherDay(
                                dayName = dayFmt.format(cal.time),
                                maxTemp = maxTemps.getDouble(i).toInt(),
                                minTemp = minTemps.getDouble(i).toInt(),
                                weatherCode = codes.getInt(i)
                            ))
                        }
                        weatherDays = days
                    }
                } catch (_: Exception) {}
                // En yakın camiler
                try {
                    val mosques = fetchNearbyMosques(loc.first, loc.second)
                    nearbyMosques = mosques
                    PrayerTimesCache.nearbyMosques = mosques
                } catch (_: Exception) {}
                // Cache'e kaydet
                PrayerTimesCache.cityName = cityName
                PrayerTimesCache.weatherDays = weatherDays
            } catch (_: Exception) {}
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
                    // ── Header: Şehir + Metod | Hava Durumu ──
                    if (cityName.isNotEmpty() || weatherDays.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            // Sol: Şehir + Hesaplama metodu
                            Column {
                                if (cityName.isNotEmpty()) {
                                    Text(
                                        text = cityName,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = onSurface
                                    )
                                }
                                Text(
                                    text = t("Diyanet Takvimi", "Diyanet Calendar", "Diyanet-Kalender", "تقويم الديانة"),
                                    fontSize = 12.sp,
                                    color = onSurfaceVariant
                                )
                            }
                            // Sağ: 3 günlük hava durumu
                            if (weatherDays.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    weatherDays.forEach { day ->
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = weatherCodeToEmoji(day.weatherCode),
                                                fontSize = 22.sp
                                            )
                                            Text(
                                                text = day.dayName,
                                                fontSize = 10.sp,
                                                color = onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

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

        // ── Yakındaki Camiler ──
        if (nearbyMosques.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDarkTheme) Color(0xFF1A2A1A) else Color(0xFFE8F5E9)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = t("Yakındaki Camiler", "Nearby Mosques", "Moscheen in der Nähe", "المساجد القريبة"),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    nearbyMosques.forEach { mosque ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedMosque = mosque }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🕌", fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = mosque.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = formatDistance(mosque.distance),
                                fontSize = 13.sp,
                                color = onSurfaceVariant
                            )
                        }
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
    // ── Cami Bottom Sheet ──
    if (selectedMosque != null) {
        val mosque = selectedMosque!!
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { selectedMosque = null },
            containerColor = if (isDarkTheme) Color(0xFF1A2A1A) else Color.White,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🕌", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = mosque.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = formatDistance(mosque.distance) + " • " + t("Yürüme mesafesi", "Walking distance", "Gehentfernung", "مسافة المشي"),
                    fontSize = 14.sp,
                    color = onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Yürüyerek yol tarifi butonu
                Button(
                    onClick = {
                        selectedMosque = null
                        val uri = android.net.Uri.parse(
                            "google.navigation:q=${mosque.lat},${mosque.lng}&mode=w"
                        )
                        val mapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                            setPackage("com.google.android.apps.maps")
                        }
                        try { context.startActivity(mapIntent) } catch (_: Exception) {
                            val webUri = android.net.Uri.parse(
                                "https://www.google.com/maps/dir/?api=1&destination=${mosque.lat},${mosque.lng}&travelmode=walking"
                            )
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, webUri))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primary)
                ) {
                    Text("🚶 ", fontSize = 18.sp)
                    Text(
                        text = t("Yürüyerek Yol Tarifi", "Walking Directions", "Fußweg-Navigation", "اتجاهات المشي"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Haritada göster butonu
                OutlinedButton(
                    onClick = {
                        selectedMosque = null
                        val webUri = android.net.Uri.parse(
                            "geo:${mosque.lat},${mosque.lng}?q=${mosque.lat},${mosque.lng}(${android.net.Uri.encode(mosque.name)})"
                        )
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, webUri))
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = t("Haritada Göster", "Show on Map", "Auf Karte anzeigen", "عرض على الخريطة"),
                        fontSize = 14.sp,
                        color = primary
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
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
                .clip(RoundedCornerShape(16.dp))
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

// ── Weather helpers ──

internal data class WeatherDay(
    val dayName: String,
    val maxTemp: Int,
    val minTemp: Int,
    val weatherCode: Int
)

@SuppressLint("MissingPermission")
private suspend fun getLocationForHome(context: Context): Pair<Double, Double>? {
    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
        ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
    ) return null
    val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    val loc = suspendCoroutine<android.location.Location?> { cont ->
        fusedClient.lastLocation
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resume(null) }
    } ?: return null
    return Pair(loc.latitude, loc.longitude)
}

// ── Nearby Mosques ──

internal data class NearbyMosque(
    val name: String,
    val distance: Int, // metre
    val lat: Double,
    val lng: Double
)

/**
 * Overpass API (OpenStreetMap) ile yakındaki camileri getirir.
 * Ücretsiz, API key gerektirmez.
 */
private fun fetchNearbyMosques(lat: Double, lng: Double): List<NearbyMosque> {
    return try {
        val query = """
            [out:json][timeout:10];
            (
              node["amenity"="place_of_worship"]["religion"="muslim"](around:2000,$lat,$lng);
              way["amenity"="place_of_worship"]["religion"="muslim"](around:2000,$lat,$lng);
            );
            out center tags;
        """.trimIndent()

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        val requestBody = okhttp3.FormBody.Builder()
            .add("data", query)
            .build()

        val request = Request.Builder()
            .url("https://overpass-api.de/api/interpreter")
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string()

        if (response.isSuccessful && body != null) {
            val elements = JSONObject(body).getJSONArray("elements")
            val mosques = mutableListOf<NearbyMosque>()

            for (i in 0 until elements.length()) {
                val el = elements.getJSONObject(i)
                val tags = el.optJSONObject("tags") ?: continue
                val name = tags.optString("name", "")
                    .ifEmpty { tags.optString("name:tr", "") }
                    .ifEmpty { tags.optString("name:en", "") }
                if (name.isEmpty()) continue
                // Koordinatlar: node -> lat/lon, way -> center.lat/center.lon
                val center = el.optJSONObject("center")
                val mLat = if (el.has("lat")) el.getDouble("lat") else center?.optDouble("lat") ?: continue
                val mLng = if (el.has("lon")) el.getDouble("lon") else center?.optDouble("lon") ?: continue
                val dist = haversineDistance(lat, lng, mLat, mLng)
                mosques.add(NearbyMosque(name, dist, mLat, mLng))
            }

            mosques.sortBy { it.distance }
            mosques.take(3)
        } else emptyList()
    } catch (e: Exception) {
        Log.e("HomeScreen", "Mosque fetch error", e)
        emptyList()
    }
}

/** Haversine formülü ile iki koordinat arası mesafe (metre). */
private fun haversineDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Int {
    val r = 6371000.0 // Dünya yarıçapı (metre)
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return (r * c).toInt()
}

/** Mesafeyi okunabilir formata çevirir: <1000m → "350m", >=1000m → "1.2 km" */
private fun formatDistance(meters: Int): String {
    return if (meters < 1000) "${meters}m"
    else "${"%.1f".format(meters / 1000.0)} km"
}

/**
 * WMO weather code'u hava durumu emojisine çevirir.
 * Emojiler: https://www.piliapp.com/emoji/list/weather/
 */
private fun weatherCodeToEmoji(code: Int): String = when (code) {
    0 -> "☀\uFE0F"             // Clear sky
    1 -> "\uD83C\uDF24\uFE0F"  // Mainly clear (sun small cloud)
    2 -> "⛅"                   // Partly cloudy
    3 -> "☁\uFE0F"             // Overcast
    45, 48 -> "\uD83C\uDF2B\uFE0F"  // Fog
    51, 53, 55 -> "\uD83C\uDF26\uFE0F" // Drizzle (sun behind rain cloud)
    56, 57 -> "\uD83C\uDF27\uFE0F"  // Freezing drizzle
    61, 63 -> "\uD83C\uDF27\uFE0F"  // Rain
    65 -> "☔"                   // Heavy rain
    66, 67 -> "\uD83C\uDF28\uFE0F"  // Freezing rain
    71, 73 -> "\uD83C\uDF28\uFE0F"  // Snow
    75 -> "❄\uFE0F"             // Heavy snow
    77 -> "\uD83C\uDF28\uFE0F"  // Snow grains
    80, 81 -> "\uD83C\uDF27\uFE0F" // Rain showers
    82 -> "☔"                   // Violent rain showers
    85, 86 -> "\uD83C\uDF28\uFE0F" // Snow showers
    95 -> "⛈\uFE0F"             // Thunderstorm
    96, 99 -> "\uD83C\uDF29\uFE0F" // Thunderstorm with hail
    else -> "\uD83C\uDF24\uFE0F"
}
