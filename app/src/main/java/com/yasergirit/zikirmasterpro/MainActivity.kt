package com.yasergirit.zikirmasterpro

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.view.MotionEvent
import android.annotation.TargetApi
import android.app.Activity
import android.os.Vibrator
import android.os.VibrationEffect
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.yasergirit.zikirmasterpro.ui.theme.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.isSystemInDarkTheme
import com.yasergirit.zikirmasterpro.billing.BillingManager
import java.util.Calendar as JavaCalendar

data class CounterSave(
    val value: Int,
    val timestamp: String,
    val dhikrName: String = ""
)

data class DhikrType(
    val nameTr: String,
    val nameEn: String,
    val arabicText: String,
    val defaultTarget: Int,
    val soundResId: Int? = null
)

private val dhikrTypes = listOf(
    DhikrType("Serbest Sayaç", "Free Counter", "", 0),
    DhikrType("Sübhanallah", "SubhanAllah", "سُبْحَانَ ٱللَّٰهِ", 33, R.raw.subhanallah),
    DhikrType("Elhamdülillah", "Alhamdulillah", "ٱلْحَمْدُ لِلَّٰهِ", 33, R.raw.elhamdulillah),
    DhikrType("Allahu Ekber", "Allahu Akbar", "ٱللَّٰهُ أَكْبَرُ", 33, R.raw.allahuekber),
    DhikrType("Lâ ilâhe illallah", "La ilaha illallah", "لَا إِلَٰهَ إِلَّا ٱللَّٰهُ", 100, R.raw.lailaheillallah),
    DhikrType("Estağfirullah", "Astaghfirullah", "أَسْتَغْفِرُ ٱللَّٰهَ", 100, R.raw.estagfirullah),
    DhikrType("Salavat", "Salawat", "ٱللَّٰهُمَّ صَلِّ عَلَىٰ مُحَمَّدٍ", 100, R.raw.salavat),
)

internal val Context.dataStore by preferencesDataStore(name = "counter_data")

class MainActivity : ComponentActivity() {

    // Quote detail state (set from intent)

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            EzanService.stop(this)
        }
        return super.dispatchTouchEvent(ev)
    }

    internal var onLocationPermissionResult: ((Boolean) -> Unit)? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val granted = fineGranted || coarseGranted
        if (granted) {
            schedulePrayerTimesIfEnabled()
        }
        onLocationPermissionResult?.invoke(granted)
    }

    internal fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            schedulePrayerTimesIfEnabled()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bildirim izni kontrolü
        checkNotificationPermissionAndSchedule()

        // Prefetch prayer times in background so HomeTab loads instantly
        prefetchPrayerTimes()

        setContent {
            CounterScreen(this)
        }
    }

    private fun prefetchPrayerTimes() {
        if (PrayerTimesCache.isValid()) return
        val hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasLocation) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = fetchPrayerTimesForHome(this@MainActivity)
                if (result != null) {
                    PrayerTimesCache.update(result.first, result.second, result.third)
                }
            } catch (_: Exception) { }
        }
    }

    
    private fun checkNotificationPermissionAndSchedule() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    schedulePrayerTimesIfEnabled()
                }
                else -> {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            schedulePrayerTimesIfEnabled()
        }
    }

    private fun schedulePrayerTimesIfEnabled() {
        val key = booleanPreferencesKey("prayer_notif_enabled")
        val enabled = try {
            kotlinx.coroutines.runBlocking {
                dataStore.data.first()[key] ?: true
            }
        } catch (e: Exception) { true }

        if (enabled) {
            BootReceiver.schedulePrayerTimesWorker(this)
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

private fun saveVibrationEnabledToDataStore(context: android.content.Context, isEnabled: Boolean) {
    try {
        val key = androidx.datastore.preferences.core.booleanPreferencesKey("vibration_enabled")
        kotlinx.coroutines.runBlocking {
            context.dataStore.edit { preferences ->
                preferences[key] = isEnabled
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private suspend fun loadVibrationEnabledFromDataStore(context: android.content.Context): Boolean {
    val key = androidx.datastore.preferences.core.booleanPreferencesKey("vibration_enabled")
    return try {
        context.dataStore.data.first()[key] ?: true // Default to vibration enabled
    } catch (e: Exception) {
        e.printStackTrace()
        true
    }
}

private fun saveLanguageToDataStore(context: android.content.Context, languageCode: String) {
    try {
        val key = stringPreferencesKey("app_language")
        kotlinx.coroutines.runBlocking {
            context.dataStore.edit { preferences ->
                preferences[key] = languageCode
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private suspend fun loadLanguageFromDataStore(context: android.content.Context): String {
    val key = stringPreferencesKey("app_language")
    return try {
        context.dataStore.data.first()[key] ?: "tr"
    } catch (e: Exception) {
        e.printStackTrace()
        "tr"
    }
}

// Theme mode: "dark", "light", "system"
private fun saveThemeModeToDataStore(context: android.content.Context, mode: String) {
    try {
        val key = stringPreferencesKey("theme_mode")
        kotlinx.coroutines.runBlocking {
            context.dataStore.edit { preferences ->
                preferences[key] = mode
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private suspend fun loadThemeModeFromDataStore(context: android.content.Context): String {
    val key = stringPreferencesKey("theme_mode")
    return try {
        context.dataStore.data.first()[key] ?: "system"
    } catch (e: Exception) {
        e.printStackTrace()
        "system"
    }
}

// Onboarding completed state
private fun saveOnboardingCompletedToDataStore(context: android.content.Context, completed: Boolean) {
    try {
        val key = booleanPreferencesKey("onboarding_completed")
        kotlinx.coroutines.runBlocking {
            context.dataStore.edit { preferences ->
                preferences[key] = completed
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private suspend fun loadOnboardingCompletedFromDataStore(context: android.content.Context): Boolean {
    val key = booleanPreferencesKey("onboarding_completed")
    return try {
        context.dataStore.data.first()[key] ?: false
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

// ── Dhikr seçimi DataStore ──
private fun saveDhikrIndexToDataStore(context: android.content.Context, index: Int) {
    try {
        val key = stringPreferencesKey("selected_dhikr_index")
        kotlinx.coroutines.runBlocking {
            context.dataStore.edit { preferences ->
                preferences[key] = index.toString()
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
}

private suspend fun loadDhikrIndexFromDataStore(context: android.content.Context): Int {
    val key = stringPreferencesKey("selected_dhikr_index")
    return try {
        context.dataStore.data.first()[key]?.toIntOrNull() ?: 0
    } catch (e: Exception) { 0 }
}

// ── Generic boolean pref helpers (namaz vakti bildirimleri vb.) ──
private fun saveBooleanPref(context: android.content.Context, key: String, value: Boolean) {
    try {
        val prefKey = booleanPreferencesKey(key)
        kotlinx.coroutines.runBlocking {
            context.dataStore.edit { preferences ->
                preferences[prefKey] = value
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
}

private suspend fun loadBooleanPref(context: android.content.Context, key: String, default: Boolean): Boolean {
    val prefKey = booleanPreferencesKey(key)
    return try {
        context.dataStore.data.first()[prefKey] ?: default
    } catch (e: Exception) { default }
}

private fun saveStringPref(context: android.content.Context, key: String, value: String) {
    try {
        val prefKey = stringPreferencesKey(key)
        kotlinx.coroutines.runBlocking {
            context.dataStore.edit { preferences ->
                preferences[prefKey] = value
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
}

private suspend fun loadStringPref(context: android.content.Context, key: String, default: String): String {
    val prefKey = stringPreferencesKey(key)
    return try {
        context.dataStore.data.first()[prefKey] ?: default
    } catch (e: Exception) { default }
}

// ──────────────────────────────────────────────
// Ana Ekran Composable
// ──────────────────────────────────────────────
@TargetApi(26)
@Composable
private fun CounterScreen(activity: android.app.Activity) {
    // ── State ──
    var count by rememberSaveable { mutableIntStateOf(0) }
    var currentTab by rememberSaveable { mutableIntStateOf(0) } // 0=AnaSayfa, 1=Sayaç, 2=Kıble, 3=Geçmiş, 4=Ayarlar
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var savedCounters by remember { mutableStateOf(listOf<CounterSave>()) }
    var isDarkTheme by remember { mutableStateOf(true) }
    var themeMode by remember { mutableStateOf("system") }
    var isSoundEnabled by remember { mutableStateOf(true) }
    var isVibrationEnabled by remember { mutableStateOf(true) }
    var selectedLanguage by remember { mutableStateOf("tr") }
    var showOnboarding by remember { mutableStateOf(false) }
    // Eid check runs immediately (no DataStore dependency)
    val eidCal = remember { java.util.Calendar.getInstance() }
    var showEidCelebration by remember { mutableStateOf(
        eidCal.get(java.util.Calendar.YEAR) == 2026 &&
        eidCal.get(java.util.Calendar.MONTH) == java.util.Calendar.MARCH &&
        eidCal.get(java.util.Calendar.DAY_OF_MONTH) == 20
    ) }
    var isDataLoaded by remember { mutableStateOf(false) }
    var selectedDhikrIndex by remember { mutableIntStateOf(0) }
    var showDhikrPicker by remember { mutableStateOf(false) }
    var locationPermissionGranted by remember { mutableStateOf(
        ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    ) }

    val context = LocalContext.current
    val systemIsDark = isSystemInDarkTheme()
    fun t(tr: String, en: String, de: String = "", ar: String = "") = when (selectedLanguage) {
        "en" -> en
        "de" -> de.ifEmpty { en }
        "ar" -> ar.ifEmpty { en }
        else -> tr
    }

    isDarkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemIsDark
    }

    // ── Load ──
    LaunchedEffect(Unit) {
        savedCounters = loadCountersFromDataStore(context)
        themeMode = loadThemeModeFromDataStore(context)
        isSoundEnabled = loadSoundEnabledFromDataStore(context)
        isVibrationEnabled = loadVibrationEnabledFromDataStore(context)
        selectedLanguage = loadLanguageFromDataStore(context)
        selectedDhikrIndex = loadDhikrIndexFromDataStore(context)
        showOnboarding = !loadOnboardingCompletedFromDataStore(context)
        isDataLoaded = true
    }

    LaunchedEffect(savedCounters, isDataLoaded) {
        if (isDataLoaded) saveCountersToDataStore(context, savedCounters)
    }

    val dateLocale = when (selectedLanguage) {
        "en" -> Locale.US
        "de" -> Locale.GERMAN
        "ar" -> Locale("ar")
        else -> Locale("tr", "TR")
    }
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", dateLocale)
    val selectedDhikr = dhikrTypes.getOrElse(selectedDhikrIndex) { dhikrTypes[0] }
    val dhikrName = if (selectedLanguage == "en" || selectedLanguage == "de") selectedDhikr.nameEn else if (selectedLanguage == "ar") selectedDhikr.arabicText.ifEmpty { selectedDhikr.nameEn } else selectedDhikr.nameTr
    val target = selectedDhikr.defaultTarget

    // ── Theme wrapper ──
    ZikirMasterProTheme(darkTheme = isDarkTheme) {
        val bg = MaterialTheme.colorScheme.background
        val surfaceColor = MaterialTheme.colorScheme.surface
        val onSurface = MaterialTheme.colorScheme.onSurface
        val primary = MaterialTheme.colorScheme.primary
        val tertiary = MaterialTheme.colorScheme.tertiary
        val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
        val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

        // Konum izni için launcher (Compose seviyesinde)
        val locationLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            locationPermissionGranted = granted
        }

        if (showEidCelebration) {
            // Eid screen shows immediately - no DataStore wait
            EidCelebrationScreen(
                selectedLanguage = selectedLanguage,
                onDismiss = { showEidCelebration = false }
            )
        } else if (!isDataLoaded) {
            // Dark splash while loading preferences
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A1628)))
        } else if (showOnboarding) {
            OnboardingScreen(
                selectedLanguage = selectedLanguage,
                onFinish = {
                    showOnboarding = false
                    saveOnboardingCompletedToDataStore(context, true)
                    locationPermissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                }
            )
        } else if (!locationPermissionGranted) {
            // Konum izni verilmediği sürece tam ekran overlay
            LocationPermissionGate(
                selectedLanguage = selectedLanguage,
                isDarkTheme = isDarkTheme,
                onRequestPermission = {
                    locationLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            )
        } else {
            Scaffold(
                containerColor = bg,
                bottomBar = {
                    NavigationBar(
                        containerColor = surfaceColor,
                        tonalElevation = 0.dp
                    ) {
                        val navColors = NavigationBarItemDefaults.colors(
                            selectedIconColor = primary,
                            selectedTextColor = primary,
                            indicatorColor = primary.copy(alpha = 0.12f),
                            unselectedIconColor = onSurfaceVariant,
                            unselectedTextColor = onSurfaceVariant
                        )
                        NavigationBarItem(
                            selected = currentTab == 0,
                            onClick = { currentTab = 0 },
                            icon = { Icon(Icons.Default.Home, contentDescription = null) },
                            label = { Text(t("Ana Sayfa", "Home", "Start", "الرئيسية"), fontSize = 11.sp) },
                            colors = navColors
                        )
                        NavigationBarItem(
                            selected = currentTab == 1,
                            onClick = { currentTab = 1 },
                            icon = { Icon(Icons.Outlined.TouchApp, contentDescription = null) },
                            label = { Text(t("Sayaç", "Counter", "Zähler", "عدّاد"), fontSize = 11.sp) },
                            colors = navColors
                        )
                        NavigationBarItem(
                            selected = currentTab == 2,
                            onClick = { currentTab = 2 },
                            icon = { Icon(Icons.Default.Explore, contentDescription = null) },
                            label = { Text(t("Kıble", "Qibla", "Qibla", "القبلة"), fontSize = 11.sp) },
                            colors = navColors
                        )
                        NavigationBarItem(
                            selected = currentTab == 3,
                            onClick = { currentTab = 3 },
                            icon = { Icon(Icons.Default.History, contentDescription = null) },
                            label = { Text(t("Geçmiş", "History", "Verlauf", "السجل"), fontSize = 11.sp) },
                            colors = navColors
                        )
                        NavigationBarItem(
                            selected = currentTab == 4,
                            onClick = { currentTab = 4 },
                            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            label = { Text(t("Ayarlar", "Settings", "Einstellungen", "الإعدادات"), fontSize = 11.sp) },
                            colors = navColors
                        )
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    when (currentTab) {
                        0 -> HomeTab(
                            isDarkTheme = isDarkTheme,
                            selectedLanguage = selectedLanguage,
                            locationPermissionGranted = locationPermissionGranted
                        )
                        1 -> CounterTab(
                            count = count,
                            onCountChange = { count = it },
                            dhikrName = dhikrName,
                            arabicText = selectedDhikr.arabicText,
                            target = target,
                            isSoundEnabled = isSoundEnabled,
                            onSoundEnabledChange = { isSoundEnabled = it; saveSoundEnabledToDataStore(context, it) },
                            isVibrationEnabled = isVibrationEnabled,
                            isDarkTheme = isDarkTheme,
                            selectedLanguage = selectedLanguage,
                            selectedDhikrIndex = selectedDhikrIndex,
                            onSave = {
                                val timestamp = dateFormat.format(Date())
                                savedCounters = savedCounters + CounterSave(count, timestamp, dhikrName)
                            },
                            onPickDhikr = { showDhikrPicker = true },
                            context = context
                        )
                        2 -> QiblaTab(
                            isDarkTheme = isDarkTheme,
                            selectedLanguage = selectedLanguage
                        )
                        3 -> HistoryTab(
                            savedCounters = savedCounters,
                            isDarkTheme = isDarkTheme,
                            selectedLanguage = selectedLanguage,
                            onDelete = { item -> savedCounters = savedCounters.filter { it != item } },
                            onDeleteAll = { showDeleteAllConfirm = true }
                        )
                        4 -> SettingsTab(
                            isDarkTheme = isDarkTheme,
                            themeMode = themeMode,
                            onThemeModeChange = { themeMode = it; saveThemeModeToDataStore(context, it) },
                            isSoundEnabled = isSoundEnabled,
                            onSoundChange = { isSoundEnabled = it; saveSoundEnabledToDataStore(context, it) },
                            isVibrationEnabled = isVibrationEnabled,
                            onVibrationChange = { isVibrationEnabled = it; saveVibrationEnabledToDataStore(context, it) },
                            selectedLanguage = selectedLanguage,
                            onLanguageChange = { selectedLanguage = it; saveLanguageToDataStore(context, it) },
                            savedCounters = savedCounters,
                            activity = context as? android.app.Activity
                        )
                    }
                }
            }

            // Dhikr picker dialog
            if (showDhikrPicker) {
                AlertDialog(
                    onDismissRequest = { showDhikrPicker = false },
                    title = { Text(t("Zikir Seçin", "Select Dhikr", "Dhikr wählen", "اختر الذكر"), fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            dhikrTypes.forEachIndexed { index, dhikr ->
                                val name = if (selectedLanguage == "en") dhikr.nameEn else dhikr.nameTr
                                val isSelected = index == selectedDhikrIndex
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            selectedDhikrIndex = index
                                            saveDhikrIndexToDataStore(context, index)
                                            count = 0
                                            showDhikrPicker = false
                                        },
                                    color = if (isSelected) primary.copy(alpha = 0.15f) else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontSize = 15.sp)
                                            if (dhikr.arabicText.isNotEmpty()) {
                                                Text(dhikr.arabicText, fontSize = 14.sp, color = onSurfaceVariant)
                                            }
                                        }
                                        if (dhikr.defaultTarget > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = tertiary.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    "${dhikr.defaultTarget}",
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    fontSize = 12.sp,
                                                    color = tertiary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showDhikrPicker = false }) {
                            Text(t("Kapat", "Close", "Schließen", "إغلاق"))
                        }
                    }
                )
            }

            // Delete All Confirmation
            if (showDeleteAllConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteAllConfirm = false },
                    title = { Text(t("Tüm Kayıtları Sil", "Delete All Records", "Alle Einträge löschen", "حذف جميع السجلات"), fontWeight = FontWeight.Bold) },
                    text = { Text(t("Tüm kayıtları silmek istediğinize emin misiniz?", "Are you sure you want to delete all records?", "Möchten Sie wirklich alle Einträge löschen?", "هل أنت متأكد من حذف جميع السجلات؟")) },
                    confirmButton = {
                        TextButton(onClick = { showDeleteAllConfirm = false }) {
                            Text(t("Hayır", "No", "Nein", "لا"))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            savedCounters = emptyList()
                            showDeleteAllConfirm = false
                        }) {
                            Text(t("Evet, Sil", "Yes, Delete", "Ja, löschen", "نعم، احذف"), color = ErrorRed)
                        }
                    }
                )
            }

        }
    }
}


// ──────────────────────────────────────────────
// KONUM İZNİ EKRANI
// ──────────────────────────────────────────────
@Composable
private fun LocationPermissionGate(
    selectedLanguage: String,
    isDarkTheme: Boolean,
    onRequestPermission: () -> Unit
) {
    fun t(tr: String, en: String, de: String = "", ar: String = "") = when (selectedLanguage) {
        "en" -> en; "de" -> de.ifEmpty { en }; "ar" -> ar.ifEmpty { en }; else -> tr
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DarkBackground,
                        EmeraldDark,
                        DarkBackground
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Text(text = "📍", fontSize = 72.sp)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = t("Konum İzni Gerekli", "Location Permission Required", "Standortberechtigung erforderlich", "إذن الموقع مطلوب"),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = t(
                    "Namaz vakitlerini ve kıble yönünü doğru gösterebilmemiz için konum bilginize ihtiyacımız var. Konum bilginiz yalnızca bu amaçla kullanılır ve üçüncü kişilerle paylaşılmaz.",
                    "We need your location to show accurate prayer times and qibla direction. Your location is only used for this purpose and is not shared with third parties.",
                    "Wir benötigen Ihren Standort, um genaue Gebetszeiten und die Qibla-Richtung anzuzeigen. Ihr Standort wird nur für diesen Zweck verwendet.",
                    "نحتاج إلى موقعك لعرض أوقات الصلاة واتجاه القبلة بدقة. يُستخدم موقعك لهذا الغرض فقط."
                ),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onRequestPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald)
            ) {
                Text(
                    text = t("Konum İznini Ver", "Grant Location Permission", "Standortzugriff erlauben", "منح إذن الموقع"),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ──────────────────────────────────────────────
// SAYAÇ TAB
// ──────────────────────────────────────────────
@Composable
private fun CounterTab(
    count: Int,
    onCountChange: (Int) -> Unit,
    dhikrName: String,
    arabicText: String,
    target: Int,
    isSoundEnabled: Boolean,
    onSoundEnabledChange: (Boolean) -> Unit,
    isVibrationEnabled: Boolean,
    isDarkTheme: Boolean,
    selectedLanguage: String,
    selectedDhikrIndex: Int,
    onSave: () -> Unit,
    onPickDhikr: () -> Unit,
    context: android.content.Context
) {
    fun t(tr: String, en: String, de: String = "", ar: String = "") = when (selectedLanguage) {
        "en" -> en; "de" -> de.ifEmpty { en }; "ar" -> ar.ifEmpty { en }; else -> tr
    }
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val tertiary = MaterialTheme.colorScheme.tertiary
    val bg = MaterialTheme.colorScheme.background

    // MediaPlayer for dhikr sounds
    val currentMediaPlayer = remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            currentMediaPlayer.value?.release()
        }
    }

    val progress = if (target > 0) (count.toFloat() / target).coerceAtMost(1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 400),
        label = "progress"
    )
    val isCompleted = target > 0 && count >= target

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Dhikr seçici chip
        Surface(
            onClick = onPickDhikr,
            shape = RoundedCornerShape(20.dp),
            color = surfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dhikrName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = onSurface
                )
                if (target > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = tertiary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "$target",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 12.sp,
                            color = tertiary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text("▼", fontSize = 10.sp, color = onSurfaceVariant)
            }
        }

        // Arapça metin
        if (arabicText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = arabicText,
                fontSize = 22.sp,
                color = tertiary,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }

        Spacer(modifier = Modifier.weight(0.3f))

        // ── Progress Ring + Counter + ON/OFF toggle ──
        Box(
            contentAlignment = Alignment.Center
        ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(240.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onCountChange(count + 1)
                    if (isVibrationEnabled) {
                        try {
                            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                            } else {
                                @Suppress("DEPRECATION") vibrator.vibrate(30)
                            }
                        } catch (_: Exception) {}
                    }
                    if (isSoundEnabled) {
                        try {
                            val dhikr = dhikrTypes.getOrElse(selectedDhikrIndex) { dhikrTypes[0] }
                            val resId = dhikr.soundResId
                            if (resId != null) {
                                currentMediaPlayer.value?.release()
                                val mp = MediaPlayer.create(context, resId)
                                mp?.setOnCompletionListener { it.release() }
                                mp?.start()
                                currentMediaPlayer.value = mp
                            } else {
                                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                am.playSoundEffect(AudioManager.FX_KEY_CLICK)
                            }
                        } catch (_: Exception) {}
                    }
                }
        ) {
            // Arka halka
            Canvas(modifier = Modifier.size(220.dp)) {
                drawArc(
                    color = surfaceVariant,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            // İlerleme halkası
            if (target > 0) {
                val ringColor = if (isCompleted) tertiary else primary
                Canvas(modifier = Modifier.size(220.dp)) {
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = animatedProgress * 360f,
                        useCenter = false,
                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
            // İç daire + sayı
            Surface(
                shape = CircleShape,
                color = if (isDarkTheme) DarkCard else LightCard,
                shadowElevation = 8.dp,
                modifier = Modifier.size(180.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$count",
                            fontSize = 56.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCompleted) tertiary else primary
                        )
                        if (target > 0) {
                            Text(
                                text = "/ $target",
                                fontSize = 16.sp,
                                color = onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

            // ON/OFF Sound Toggle - sağ üst köşe
            val toggleBg = if (isDarkTheme) surfaceVariant else Color(0xFFE0E0E0)
            val toggleTextColor = if (isDarkTheme) Color.White else Color(0xFF212121)
            Surface(
                onClick = { onSoundEnabledChange(!isSoundEnabled) },
                shape = RoundedCornerShape(20.dp),
                color = toggleBg,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 20.dp, y = (-10).dp)
                    .height(34.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isSoundEnabled) {
                        Text(
                            text = t("Açık", "ON", "AN", "مفعّل"),
                            color = toggleTextColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(primary)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(ErrorRed)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = t("Kapalı", "OFF", "AUS", "معطّل"),
                            color = toggleTextColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } // outer Box end

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = t("Saymak için dokunun", "Tap to count", "Tippen zum Zählen", "اضغط للعد"),
            fontSize = 13.sp,
            color = onSurfaceVariant
        )

        Spacer(modifier = Modifier.weight(0.3f))

        // ── Alt butonlar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Sıfırla
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledTonalIconButton(
                    onClick = { onCountChange(0) },
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = surfaceVariant
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = t("Sıfırla", "Reset", "Zurücksetzen", "إعادة"), tint = onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(t("Sıfırla", "Reset", "Zurücksetzen", "إعادة"), fontSize = 11.sp, color = onSurfaceVariant)
            }
            // Kaydet
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledIconButton(
                    onClick = onSave,
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = primary
                    )
                ) {
                    Icon(Icons.Default.Save, contentDescription = t("Kaydet", "Save", "Speichern", "حفظ"), tint = Color.White)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(t("Kaydet", "Save", "Speichern", "حفظ"), fontSize = 11.sp, color = onSurfaceVariant)
            }
            // Geri Al
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledTonalIconButton(
                    onClick = { if (count > 0) onCountChange(count - 1) },
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = surfaceVariant
                    )
                ) {
                    Text("−1", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(t("Geri Al", "Undo", "Rückgängig", "تراجع"), fontSize = 11.sp, color = onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ──────────────────────────────────────────────
// GEÇMİŞ TAB
// ──────────────────────────────────────────────
@Composable
private fun HistoryTab(
    savedCounters: List<CounterSave>,
    isDarkTheme: Boolean,
    selectedLanguage: String,
    onDelete: (CounterSave) -> Unit,
    onDeleteAll: () -> Unit
) {
    fun t(tr: String, en: String, de: String = "", ar: String = "") = when (selectedLanguage) {
        "en" -> en; "de" -> de.ifEmpty { en }; "ar" -> ar.ifEmpty { en }; else -> tr
    }
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val tertiary = MaterialTheme.colorScheme.tertiary
    val bg = MaterialTheme.colorScheme.background
    val cardColor = if (isDarkTheme) DarkCard else LightCard

    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", if (selectedLanguage == "en") Locale.US else Locale("tr", "TR"))
    val calendar = JavaCalendar.getInstance()
    val today = JavaCalendar.getInstance()

    // İstatistikler
    val todayCount = savedCounters.count { c ->
        try {
            val d = dateFormat.parse(c.timestamp) ?: return@count false
            calendar.time = d
            calendar.get(JavaCalendar.YEAR) == today.get(JavaCalendar.YEAR) &&
            calendar.get(JavaCalendar.DAY_OF_YEAR) == today.get(JavaCalendar.DAY_OF_YEAR)
        } catch (_: Exception) { false }
    }
    val todayTotal = savedCounters.filter { c ->
        try {
            val d = dateFormat.parse(c.timestamp) ?: return@filter false
            calendar.time = d
            calendar.get(JavaCalendar.YEAR) == today.get(JavaCalendar.YEAR) &&
            calendar.get(JavaCalendar.DAY_OF_YEAR) == today.get(JavaCalendar.DAY_OF_YEAR)
        } catch (_: Exception) { false }
    }.sumOf { it.value }

    val weekStart = JavaCalendar.getInstance().apply {
        set(JavaCalendar.DAY_OF_WEEK, firstDayOfWeek)
        set(JavaCalendar.HOUR_OF_DAY, 0); set(JavaCalendar.MINUTE, 0); set(JavaCalendar.SECOND, 0)
    }
    val weekTotal = savedCounters.filter { c ->
        try { val d = dateFormat.parse(c.timestamp); d != null && d.after(weekStart.time) } catch (_: Exception) { false }
    }.sumOf { it.value }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        // Header
        Text(
            text = t("Geçmiş", "History", "Verlauf", "السجل"),
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = onSurface
        )

        // İstatistik kartları
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                label = t("Bugün", "Today", "Heute", "اليوم"),
                value = "$todayTotal",
                subLabel = "${todayCount} ${t("kayıt", "saves", "Einträge", "سجلات")}",
                accentColor = primary,
                cardColor = cardColor,
                textColor = onSurface,
                dimColor = onSurfaceVariant
            )
            StatCard(
                modifier = Modifier.weight(1f),
                label = t("Bu Hafta", "This Week", "Diese Woche", "هذا الأسبوع"),
                value = "$weekTotal",
                subLabel = "${savedCounters.size} ${t("toplam", "total", "gesamt", "إجمالي")}",
                accentColor = tertiary,
                cardColor = cardColor,
                textColor = onSurface,
                dimColor = onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Kayıt listesi
        if (savedCounters.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📿", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        t("Henüz kayıt yok", "No records yet", "Noch keine Einträge", "لا توجد سجلات"),
                        color = onSurfaceVariant,
                        fontSize = 16.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(savedCounters.reversed()) { index, item ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = cardColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Sol: değer badge
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = primary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "${item.value}",
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = primary
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                if (item.dhikrName.isNotEmpty()) {
                                    Text(
                                        item.dhikrName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    item.timestamp,
                                    fontSize = 12.sp,
                                    color = onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onDelete(item) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = t("Sil", "Delete", "Löschen", "حذف"),
                                    tint = ErrorRed.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Tümünü sil butonu
            if (savedCounters.size > 1) {
                TextButton(
                    onClick = onDeleteAll,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        t("Tüm Kayıtları Sil", "Delete All Records", "Alle Einträge löschen", "حذف جميع السجلات"),
                        color = ErrorRed,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    label: String,
    value: String,
    subLabel: String,
    accentColor: Color,
    cardColor: Color,
    textColor: Color,
    dimColor: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = cardColor
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 12.sp, color = dimColor, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = accentColor)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subLabel, fontSize = 11.sp, color = dimColor)
        }
    }
}

// ──────────────────────────────────────────────
// AYARLAR TAB
// ──────────────────────────────────────────────
@Composable
private fun SettingsTab(
    isDarkTheme: Boolean,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    isSoundEnabled: Boolean,
    onSoundChange: (Boolean) -> Unit,
    isVibrationEnabled: Boolean,
    onVibrationChange: (Boolean) -> Unit,
    selectedLanguage: String,
    onLanguageChange: (String) -> Unit,
    savedCounters: List<CounterSave>,
    activity: android.app.Activity? = null
) {
    fun t(tr: String, en: String, de: String = "", ar: String = "") = when (selectedLanguage) {
        "en" -> en; "de" -> de.ifEmpty { en }; "ar" -> ar.ifEmpty { en }; else -> tr
    }
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val bg = MaterialTheme.colorScheme.background
    val cardColor = if (isDarkTheme) DarkCard else LightCard

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = t("Ayarlar", "Settings", "Einstellungen", "الإعدادات"),
            modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 20.dp),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = onSurface
        )

        // ── Dil ──
        SettingsSectionCard(cardColor = cardColor) {
            Text(t("Dil", "Language", "Sprache", "اللغة"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = onSurface)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SegmentButton(
                    label = "Türkçe",
                    selected = selectedLanguage == "tr",
                    onClick = { onLanguageChange("tr") },
                    modifier = Modifier.weight(1f)
                )
                SegmentButton(
                    label = "English",
                    selected = selectedLanguage == "en",
                    onClick = { onLanguageChange("en") },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SegmentButton(
                    label = "Deutsch",
                    selected = selectedLanguage == "de",
                    onClick = { onLanguageChange("de") },
                    modifier = Modifier.weight(1f)
                )
                SegmentButton(
                    label = "العربية",
                    selected = selectedLanguage == "ar",
                    onClick = { onLanguageChange("ar") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Tema ──
        SettingsSectionCard(cardColor = cardColor) {
            Text(t("Tema", "Theme", "Design", "المظهر"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = onSurface)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SegmentButton(
                    label = t("Açık", "Light", "Hell", "فاتح"),
                    selected = themeMode == "light",
                    onClick = { onThemeModeChange("light") },
                    modifier = Modifier.weight(1f)
                )
                SegmentButton(
                    label = t("Koyu", "Dark", "Dunkel", "داكن"),
                    selected = themeMode == "dark",
                    onClick = { onThemeModeChange("dark") },
                    modifier = Modifier.weight(1f)
                )
                SegmentButton(
                    label = t("Sistem", "System", "System", "النظام"),
                    selected = themeMode == "system",
                    onClick = { onThemeModeChange("system") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Ses & Titreşim ──
        SettingsSectionCard(cardColor = cardColor) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(t("Ses Efektleri", "Sound Effects", "Soundeffekte", "المؤثرات الصوتية"), fontWeight = FontWeight.Medium, fontSize = 15.sp, color = onSurface)
                Switch(
                    checked = isSoundEnabled,
                    onCheckedChange = onSoundChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = primary,
                        uncheckedThumbColor = onSurfaceVariant,
                        uncheckedTrackColor = surfaceVariant
                    )
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = surfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(t("Titreşim", "Vibration", "Vibration", "الاهتزاز"), fontWeight = FontWeight.Medium, fontSize = 15.sp, color = onSurface)
                Switch(
                    checked = isVibrationEnabled,
                    onCheckedChange = onVibrationChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = primary,
                        uncheckedThumbColor = onSurfaceVariant,
                        uncheckedTrackColor = surfaceVariant
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Namaz Vakti Bildirimleri ──
        var prayerNotifEnabled by remember { mutableStateOf(true) }
        var ezanSoundEnabled by remember { mutableStateOf(true) }
        var prayerFajr by remember { mutableStateOf(true) }
        var prayerSunrise by remember { mutableStateOf(false) }
        var prayerDhuhr by remember { mutableStateOf(true) }
        var prayerAsr by remember { mutableStateOf(true) }
        var prayerMaghrib by remember { mutableStateOf(true) }
        var prayerIsha by remember { mutableStateOf(true) }
        var prayerMethod by remember { mutableStateOf("13") }
        var methodDropdownExpanded by remember { mutableStateOf(false) }

        data class MethodOption(val id: String, val nameTr: String, val nameEn: String)
        val methodOptions = listOf(
            MethodOption("13", "Türkiye Diyanet", "Turkey Diyanet"),
            MethodOption("3", "Müslüman Dünya Ligi", "Muslim World League"),
            MethodOption("2", "Kuzey Amerika İslam Cem.", "Islamic Society of North America"),
            MethodOption("4", "Ümmü'l-Kura Üniversitesi", "Umm Al-Qura University"),
            MethodOption("5", "Mısır Genel Fetva Kurumu", "Egyptian General Authority"),
            MethodOption("1", "Karaçi İslam Bilimleri Ünv.", "University of Islamic Sciences, Karachi"),
            MethodOption("7", "Tahran Jeofizik Enstitüsü", "Institute of Geophysics, Tehran"),
            MethodOption("8", "Körfez Bölgesi", "Gulf Region"),
            MethodOption("9", "Kuveyt", "Kuwait"),
            MethodOption("10", "Katar", "Qatar"),
            MethodOption("11", "Singapur MUIS", "Singapore MUIS"),
            MethodOption("12", "Fransa UOIF", "France UOIF"),
            MethodOption("14", "Rusya", "Russia"),
            MethodOption("15", "Moonsighting Komitesi", "Moonsighting Committee"),
            MethodOption("17", "Malezya JAKIM", "Malaysia JAKIM"),
            MethodOption("18", "Tunus", "Tunisia"),
            MethodOption("19", "Cezayir", "Algeria"),
            MethodOption("20", "Endonezya KEMENAG", "Indonesia KEMENAG"),
            MethodOption("21", "Fas", "Morocco"),
            MethodOption("22", "Portekiz", "Portugal"),
            MethodOption("23", "Ürdün", "Jordan"),
        )

        // Load prayer prefs
        LaunchedEffect(Unit) {
            prayerNotifEnabled = loadBooleanPref(context, "prayer_notif_enabled", true)
            ezanSoundEnabled = loadBooleanPref(context, "ezan_sound_enabled", true)
            prayerMethod = loadStringPref(context, "prayer_method", "13")
            prayerFajr = loadBooleanPref(context, "prayer_notif_fajr", true)
            prayerSunrise = loadBooleanPref(context, "prayer_notif_sunrise", false)
            prayerDhuhr = loadBooleanPref(context, "prayer_notif_dhuhr", true)
            prayerAsr = loadBooleanPref(context, "prayer_notif_asr", true)
            prayerMaghrib = loadBooleanPref(context, "prayer_notif_maghrib", true)
            prayerIsha = loadBooleanPref(context, "prayer_notif_isha", true)
        }

        fun savePrayerPref(key: String, value: Boolean) {
            saveBooleanPref(context, key, value)
            if (prayerNotifEnabled) BootReceiver.runOnce(context)
        }

        SettingsSectionCard(cardColor = cardColor) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        t("Namaz Vakti Bildirimleri", "Prayer Time Notifications", "Gebetszeit-Benachrichtigungen", "إشعارات أوقات الصلاة"),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = onSurface
                    )
                    Text(
                        t("Namaz vakitlerinde bildirim al", "Get notified at prayer times", "Benachrichtigungen zu Gebetszeiten erhalten", "تلقي إشعارات في أوقات الصلاة"),
                        fontSize = 11.sp,
                        color = onSurfaceVariant
                    )
                }
                Switch(
                    checked = prayerNotifEnabled,
                    onCheckedChange = { enabled ->
                        prayerNotifEnabled = enabled
                        saveBooleanPref(context, "prayer_notif_enabled", enabled)
                        if (enabled) {
                            BootReceiver.schedulePrayerTimesWorker(context)
                            BootReceiver.runOnce(context)
                        } else {
                            BootReceiver.cancelPrayerTimesWorker(context)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = primary,
                        uncheckedThumbColor = onSurfaceVariant,
                        uncheckedTrackColor = surfaceVariant
                    )
                )
            }

            if (prayerNotifEnabled) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = surfaceVariant)

                // Ezan Sesi toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            t("Ezan Sesi", "Adhan Sound", "Gebetsruf", "صوت الأذان"),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = onSurface
                        )
                        Text(
                            t("Ayasofya ezan sesi", "Hagia Sophia adhan", "Hagia Sophia Gebetsruf", "أذان آيا صوفيا"),
                            fontSize = 11.sp,
                            color = onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = ezanSoundEnabled,
                        onCheckedChange = {
                            ezanSoundEnabled = it
                            saveBooleanPref(context, "ezan_sound_enabled", it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = primary,
                            uncheckedThumbColor = onSurfaceVariant,
                            uncheckedTrackColor = surfaceVariant
                        )
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = surfaceVariant)

                data class PrayerToggle(val key: String, val nameTr: String, val nameEn: String, val nameDe: String = "", val nameAr: String = "", val value: Boolean, val onChange: (Boolean) -> Unit)
                val toggles = listOf(
                    PrayerToggle("prayer_notif_fajr", "İmsak", "Fajr", "Fajr", "الفجر", prayerFajr) { prayerFajr = it; savePrayerPref("prayer_notif_fajr", it) },
                    PrayerToggle("prayer_notif_sunrise", "Güneş", "Sunrise", "Sunrise", "الشروق", prayerSunrise) { prayerSunrise = it; savePrayerPref("prayer_notif_sunrise", it) },
                    PrayerToggle("prayer_notif_dhuhr", "Öğle", "Dhuhr", "Dhuhr", "الظهر", prayerDhuhr) { prayerDhuhr = it; savePrayerPref("prayer_notif_dhuhr", it) },
                    PrayerToggle("prayer_notif_asr", "İkindi", "Asr", "Asr", "العصر", prayerAsr) { prayerAsr = it; savePrayerPref("prayer_notif_asr", it) },
                    PrayerToggle("prayer_notif_maghrib", "Akşam", "Maghrib", "Maghrib", "المغرب", prayerMaghrib) { prayerMaghrib = it; savePrayerPref("prayer_notif_maghrib", it) },
                    PrayerToggle("prayer_notif_isha", "Yatsı", "Isha", "Isha", "العشاء", prayerIsha) { prayerIsha = it; savePrayerPref("prayer_notif_isha", it) },
                )

                toggles.forEach { toggle ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            t(toggle.nameTr, toggle.nameEn, toggle.nameDe, toggle.nameAr),
                            fontSize = 14.sp,
                            color = onSurface,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                        Switch(
                            checked = toggle.value,
                            onCheckedChange = toggle.onChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = primary,
                                uncheckedThumbColor = onSurfaceVariant,
                                uncheckedTrackColor = surfaceVariant
                            )
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = surfaceVariant)

                // ── Hesaplama Metodu Dropdown ──
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        t("Hesaplama Metodu", "Calculation Method", "Berechnungsmethode", "طريقة الحساب"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = onSurface
                    )
                    Text(
                        t("Namaz vakitlerinin hesaplanma yöntemi", "Method used to calculate prayer times", "Methode zur Berechnung der Gebetszeiten", "طريقة حساب أوقات الصلاة"),
                        fontSize = 11.sp,
                        color = onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box {
                        val selectedMethod = methodOptions.find { it.id == prayerMethod } ?: methodOptions[0]
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { methodDropdownExpanded = true },
                            shape = RoundedCornerShape(12.dp),
                            color = surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = t(selectedMethod.nameTr, selectedMethod.nameEn),
                                    fontSize = 14.sp,
                                    color = onSurface
                                )
                                Text(
                                    text = "▼",
                                    fontSize = 12.sp,
                                    color = onSurfaceVariant
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = methodDropdownExpanded,
                            onDismissRequest = { methodDropdownExpanded = false },
                            modifier = Modifier.heightIn(max = 300.dp)
                        ) {
                            methodOptions.forEach { method ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = t(method.nameTr, method.nameEn),
                                            fontSize = 14.sp,
                                            fontWeight = if (method.id == prayerMethod) FontWeight.Bold else FontWeight.Normal,
                                            color = if (method.id == prayerMethod) primary else onSurface
                                        )
                                    },
                                    onClick = {
                                        prayerMethod = method.id
                                        saveStringPref(context, "prayer_method", method.id)
                                        methodDropdownExpanded = false
                                        // Re-trigger prayer times fetch
                                        if (prayerNotifEnabled) {
                                            BootReceiver.runOnce(context)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Bize Destek Ol ──
        val billingManager = remember { BillingManager(context.applicationContext) }
        LaunchedEffect(Unit) { billingManager.start() }
        DisposableEffect(Unit) { onDispose { billingManager.end() } }

        val supportProducts by billingManager.products.collectAsState()
        val billingReady by billingManager.isReady.collectAsState()
        val billingError by billingManager.connectionError.collectAsState()
        val purchaseMsg by billingManager.purchaseMessage.collectAsState()
        var showSupportDialog by remember { mutableStateOf(false) }
        var selectedProductId by remember { mutableStateOf<String?>(null) }

        // Satın alma mesajı göster
        LaunchedEffect(purchaseMsg) {
            if (purchaseMsg != null) {
                kotlinx.coroutines.delay(4000)
                billingManager.clearMessage()
            }
        }

        // Sabit tutar seviyeleri (Google Play Console'da bu ID'lerle ürün tanımla)
        data class SupportTier(val id: String, val emoji: String, val label: String, val amount: String)
        val tiers = listOf(
            SupportTier("support_10", "☕", t("Çay", "Tea", "Tee", "شاي"), "₺10"),
            SupportTier("support_25", "🧃", t("Kahve", "Coffee", "Kaffee", "قهوة"), "₺25"),
            SupportTier("support_50", "🍕", t("Yemek", "Meal", "Mahlzeit", "وجبة"), "₺50"),
            SupportTier("support_100", "🎁", t("Hediye", "Gift", "Geschenk", "هدية"), "₺100"),
            SupportTier("support_200", "🌟", t("Büyük Destek", "Big Support", "Große Hilfe", "دعم كبير"), "₺200"),
        )

        SettingsSectionCard(cardColor = cardColor) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("❤️", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    t("Bize Destek Ol", "Support Us", "Unterstütze uns", "ادعمنا"),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = onSurface
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                t(
                    "Bir tutar seçerek uygulamanın gelişimine katkı sağlayın. İstediğiniz kadar tekrar destek olabilirsiniz.",
                    "Choose an amount to support app development. You can support multiple times."
                ),
                fontSize = 12.sp,
                color = onSurfaceVariant,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Teşekkür mesajı
            if (purchaseMsg != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = EmeraldLight.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        purchaseMsg ?: "",
                        modifier = Modifier.padding(12.dp),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = EmeraldLight,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Tutar butonları
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tiers.take(3).forEach { tier ->
                    val isSelected = selectedProductId == tier.id
                    Surface(
                        onClick = { selectedProductId = if (isSelected) null else tier.id },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) primary.copy(alpha = 0.18f) else surfaceVariant.copy(alpha = 0.5f),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, primary) else null
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(tier.emoji, fontSize = 20.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                tier.amount,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (isSelected) primary else onSurface
                            )
                            Text(
                                tier.label,
                                fontSize = 10.sp,
                                color = if (isSelected) primary.copy(alpha = 0.8f) else onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tiers.drop(3).forEach { tier ->
                    val isSelected = selectedProductId == tier.id
                    Surface(
                        onClick = { selectedProductId = if (isSelected) null else tier.id },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) primary.copy(alpha = 0.18f) else surfaceVariant.copy(alpha = 0.5f),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, primary) else null
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(tier.emoji, fontSize = 20.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                tier.amount,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (isSelected) primary else onSurface
                            )
                            Text(
                                tier.label,
                                fontSize = 10.sp,
                                color = if (isSelected) primary.copy(alpha = 0.8f) else onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Destek Ol butonu
            Button(
                onClick = {
                    val pid = selectedProductId ?: return@Button
                    if (!billingManager.isInstalledFromPlayStore()) {
                        showSupportDialog = true
                    } else {
                        val pd = supportProducts.find { it.productId == pid }
                        if (pd != null && activity != null) {
                            billingManager.launchPurchase(activity, pd)
                        } else if (billingError != null || supportProducts.isEmpty()) {
                            showSupportDialog = true
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedProductId != null,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primary,
                    contentColor = Color.White,
                    disabledContainerColor = surfaceVariant.copy(alpha = 0.5f),
                    disabledContentColor = onSurfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                val selectedTier = tiers.find { it.id == selectedProductId }
                Text(
                    if (selectedTier != null)
                        "${t("Destek Ol", "Support", "Unterstützen", "دعم")} • ${selectedTier.amount}"
                    else
                        t("Tutar Seçin", "Select Amount", "Betrag wählen", "اختر المبلغ"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        // Hata / bilgi dialogu
        if (showSupportDialog) {
            AlertDialog(
                onDismissRequest = { showSupportDialog = false },
                title = {
                    Text(
                        t("Bilgi", "Info", "Info", "معلومات"),
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("⚠️", fontSize = 32.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (!billingManager.isInstalledFromPlayStore()) {
                                t(
                                    "Destek özelliği yalnızca Google Play Store'dan indirilen sürümde kullanılabilir. Lütfen uygulamayı Play Store'dan indirin.",
                                    "Support feature is only available in the Play Store version. Please download the app from Play Store."
                                )
                            } else {
                                billingError ?: t(
                                    "Şu anda bir bağlantı sorunu var. Lütfen internet bağlantınızı kontrol edip tekrar deneyin.",
                                    "There is a connection issue. Please check your internet and try again."
                                )
                            },
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSupportDialog = false }) {
                        Text(t("Tamam", "OK", "OK", "حسناً"))
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Versiyon ──
        Text(
            "Sofi App v2.0.1",
            fontSize = 13.sp,
            color = onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsSectionCard(
    cardColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
private fun SegmentButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) primary.copy(alpha = 0.15f) else surfaceVariant.copy(alpha = 0.5f),
        border = if (selected) null else null
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp,
            color = if (selected) primary else onSurfaceVariant
        )
    }
}

