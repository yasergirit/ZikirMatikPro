package com.yasergirit.zikirmasterpro

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yasergirit.zikirmasterpro.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// ── Data Models ──

private data class Surah(
    val id: Int,
    val nameSimple: String,
    val nameArabic: String,
    val versesCount: Int,
    val revelationPlace: String,
    val translatedName: String
)

private data class Verse(
    val verseNumber: Int,
    val verseKey: String,
    val textUthmani: String,
    val translationText: String
)

private data class Reciter(
    val id: Int,
    val name: String,
    val style: String
)

private val availableReciters = listOf(
    Reciter(7, "Mishari Rashid al-Afasy", ""),
    Reciter(2, "AbdulBaset AbdulSamad", "Murattal"),
    Reciter(1, "AbdulBaset AbdulSamad", "Mujawwad"),
    Reciter(3, "Abdur-Rahman as-Sudais", ""),
    Reciter(4, "Abu Bakr al-Shatri", ""),
    Reciter(5, "Hani ar-Rifai", ""),
    Reciter(6, "Mahmoud Khalil Al-Husary", ""),
    Reciter(10, "Sa'ud ash-Shuraym", ""),
    Reciter(12, "Mahmoud Khalil Al-Husary", "Muallim")
)

// ── API Client ──

private val quranHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .build()

private const val BASE_URL = "https://api.quran.com/api/v4"
private const val AUDIO_BASE_URL = "https://verses.quran.com/"

private fun getTranslationId(language: String): Int = when (language) {
    "tr" -> 77
    "en" -> 20
    "de" -> 27
    "ar" -> 78
    else -> 77
}

private fun getApiLanguage(language: String): String = when (language) {
    "tr" -> "tr"; "en" -> "en"; "de" -> "de"; "ar" -> "ar"; else -> "tr"
}

private suspend fun fetchSurahs(language: String): List<Surah> = withContext(Dispatchers.IO) {
    try {
        val lang = getApiLanguage(language)
        val url = "$BASE_URL/chapters?language=$lang"
        val request = Request.Builder().url(url).build()
        val response = quranHttpClient.newCall(request).execute()
        val body = response.body?.string() ?: return@withContext emptyList()

        val json = JSONObject(body)
        val chapters = json.getJSONArray("chapters")
        val list = mutableListOf<Surah>()

        for (i in 0 until chapters.length()) {
            val ch = chapters.getJSONObject(i)
            val translatedName = ch.optJSONObject("translated_name")?.optString("name", "") ?: ""
            list.add(
                Surah(
                    id = ch.getInt("id"),
                    nameSimple = ch.getString("name_simple"),
                    nameArabic = ch.getString("name_arabic"),
                    versesCount = ch.getInt("verses_count"),
                    revelationPlace = ch.getString("revelation_place"),
                    translatedName = translatedName
                )
            )
        }
        list
    } catch (e: Exception) {
        emptyList()
    }
}

private suspend fun fetchVerses(
    surahId: Int,
    language: String,
    page: Int = 1,
    perPage: Int = 50
): Pair<List<Verse>, Int> = withContext(Dispatchers.IO) {
    try {
        val translationId = getTranslationId(language)
        val url = "$BASE_URL/verses/by_chapter/$surahId?language=${getApiLanguage(language)}&words=false&fields=text_uthmani&translations=$translationId&per_page=$perPage&page=$page"
        val request = Request.Builder().url(url).build()
        val response = quranHttpClient.newCall(request).execute()
        val body = response.body?.string() ?: return@withContext Pair(emptyList(), 0)

        val json = JSONObject(body)
        val versesArray = json.getJSONArray("verses")
        val pagination = json.getJSONObject("pagination")
        val totalPages = pagination.getInt("total_pages")
        val list = mutableListOf<Verse>()

        for (i in 0 until versesArray.length()) {
            val v = versesArray.getJSONObject(i)
            val translations = v.optJSONArray("translations")
            val translationText = if (translations != null && translations.length() > 0) {
                translations.getJSONObject(0).getString("text").replace(Regex("<[^>]*>"), "")
            } else ""

            list.add(
                Verse(
                    verseNumber = v.getInt("verse_number"),
                    verseKey = v.getString("verse_key"),
                    textUthmani = v.optString("text_uthmani", ""),
                    translationText = translationText
                )
            )
        }
        Pair(list, totalPages)
    } catch (e: Exception) {
        Pair(emptyList(), 0)
    }
}

private suspend fun fetchAudioUrls(
    reciterId: Int,
    surahId: Int
): Map<String, String> = withContext(Dispatchers.IO) {
    try {
        val url = "$BASE_URL/recitations/$reciterId/by_chapter/$surahId?per_page=300"
        val request = Request.Builder().url(url).build()
        val response = quranHttpClient.newCall(request).execute()
        val body = response.body?.string() ?: return@withContext emptyMap()

        val json = JSONObject(body)
        val audioFiles = json.getJSONArray("audio_files")
        val map = mutableMapOf<String, String>()

        for (i in 0 until audioFiles.length()) {
            val af = audioFiles.getJSONObject(i)
            val verseKey = af.getString("verse_key")
            val relativeUrl = af.getString("url")
            map[verseKey] = "$AUDIO_BASE_URL$relativeUrl"
        }
        map
    } catch (e: Exception) {
        Log.e("QuranAudio", "Failed to fetch audio urls", e)
        emptyMap()
    }
}

// ── Cache ──

private object SurahCache {
    var surahs: List<Surah> = emptyList()
    var language: String = ""
}

// ── Audio Player Manager (Double Buffer) ──

private val audioAttributes = AudioAttributes.Builder()
    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .build()

private class QuranAudioPlayer {
    private var currentPlayer: MediaPlayer? = null
    private var nextPlayer: MediaPlayer? = null
    private var nextPreparedUrl: String? = null

    var isPlaying = mutableStateOf(false)
    var isPreparing = mutableStateOf(false)
    var currentVerseKey = mutableStateOf<String?>(null)

    private var onVerseComplete: (() -> Unit)? = null

    fun play(url: String, verseKey: String, onComplete: () -> Unit) {
        // Eğer sonraki ayet zaten hazırsa, onu direkt kullan
        if (nextPlayer != null && nextPreparedUrl == url) {
            releaseCurrentPlayer()
            currentPlayer = nextPlayer
            nextPlayer = null
            nextPreparedUrl = null
            currentVerseKey.value = verseKey
            onVerseComplete = onComplete
            isPreparing.value = false
            isPlaying.value = true
            val player = this
            currentPlayer?.setOnCompletionListener {
                player.isPlaying.value = false
                player.onVerseComplete?.invoke()
            }
            currentPlayer?.start()
            return
        }

        // Yoksa sıfırdan yükle
        releaseCurrentPlayer()
        releaseNextPlayer()
        isPreparing.value = true
        currentVerseKey.value = verseKey
        onVerseComplete = onComplete

        val player = this
        currentPlayer = MediaPlayer().apply {
            setAudioAttributes(audioAttributes)
            setDataSource(url)
            setOnPreparedListener {
                player.isPreparing.value = false
                player.isPlaying.value = true
                start()
            }
            setOnCompletionListener {
                player.isPlaying.value = false
                player.onVerseComplete?.invoke()
            }
            setOnErrorListener { _, _, _ ->
                player.isPreparing.value = false
                player.isPlaying.value = false
                player.currentVerseKey.value = null
                true
            }
            prepareAsync()
        }
    }

    /** Sonraki ayeti arka planda hazırla (preload) */
    fun preloadNext(url: String) {
        if (nextPreparedUrl == url) return // zaten hazırlanıyor/hazır
        releaseNextPlayer()
        nextPreparedUrl = url
        try {
            nextPlayer = MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                setDataSource(url)
                setOnPreparedListener { /* hazır, bekliyor */ }
                setOnErrorListener { _, _, _ ->
                    nextPlayer = null
                    nextPreparedUrl = null
                    true
                }
                prepareAsync()
            }
        } catch (_: Exception) {
            nextPlayer = null
            nextPreparedUrl = null
        }
    }

    fun pause() {
        currentPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                isPlaying.value = false
            }
        }
    }

    fun resume() {
        currentPlayer?.let {
            it.start()
            isPlaying.value = true
        }
    }

    fun stop() {
        releaseCurrentPlayer()
        releaseNextPlayer()
        isPlaying.value = false
        isPreparing.value = false
        currentVerseKey.value = null
        onVerseComplete = null
    }

    private fun releaseCurrentPlayer() {
        currentPlayer?.let {
            try { if (it.isPlaying) it.stop() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        currentPlayer = null
    }

    private fun releaseNextPlayer() {
        nextPlayer?.let {
            try { if (it.isPlaying) it.stop() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        nextPlayer = null
        nextPreparedUrl = null
    }
}

// ── QuranScreen (Main Entry) ──

@Composable
fun QuranScreen(
    isDarkTheme: Boolean,
    selectedLanguage: String
) {
    var selectedSurah by remember { mutableStateOf<Surah?>(null) }

    if (selectedSurah != null) {
        SurahDetailView(
            surah = selectedSurah!!,
            isDarkTheme = isDarkTheme,
            selectedLanguage = selectedLanguage,
            onBack = { selectedSurah = null }
        )
    } else {
        SurahListView(
            isDarkTheme = isDarkTheme,
            selectedLanguage = selectedLanguage,
            onSurahClick = { selectedSurah = it }
        )
    }
}

// ── Surah List View ──

@Composable
private fun SurahListView(
    isDarkTheme: Boolean,
    selectedLanguage: String,
    onSurahClick: (Surah) -> Unit
) {
    fun t(tr: String, en: String, de: String = "", ar: String = "") = when (selectedLanguage) {
        "en" -> en; "de" -> de.ifEmpty { en }; "ar" -> ar.ifEmpty { en }; else -> tr
    }

    val bg = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary

    var surahs by remember { mutableStateOf<List<Surah>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(selectedLanguage) {
        if (SurahCache.surahs.isNotEmpty() && SurahCache.language == selectedLanguage) {
            surahs = SurahCache.surahs
            isLoading = false
        } else {
            isLoading = true
            val result = fetchSurahs(selectedLanguage)
            surahs = result
            SurahCache.surahs = result
            SurahCache.language = selectedLanguage
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = surfaceColor,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "\uFDFD",
                    fontSize = 32.sp,
                    color = primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = t("Kur'an-i Kerim", "The Holy Quran", "Der Heilige Quran", "\u0627\u0644\u0642\u0631\u0622\u0646 \u0627\u0644\u0643\u0631\u064A\u0645"),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = t("114 Sure", "114 Surahs", "114 Suren", "\u0661\u0661\u0664 \u0633\u0648\u0631\u0629"),
                    fontSize = 13.sp,
                    color = onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                items(surahs, key = { it.id }) { surah ->
                    SurahRow(
                        surah = surah,
                        selectedLanguage = selectedLanguage,
                        onClick = { onSurahClick(surah) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SurahRow(
    surah: Surah,
    selectedLanguage: String,
    onClick: () -> Unit
) {
    fun t(tr: String, en: String, de: String = "", ar: String = "") = when (selectedLanguage) {
        "en" -> en; "de" -> de.ifEmpty { en }; "ar" -> ar.ifEmpty { en }; else -> tr
    }

    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    val placeText = if (surah.revelationPlace == "makkah")
        t("Mekke", "Makkah", "Mekka", "\u0645\u0643\u0629")
    else
        t("Medine", "Madinah", "Medina", "\u0627\u0644\u0645\u062F\u064A\u0646\u0629")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(14.dp),
        color = surfaceVariant.copy(alpha = 0.4f),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text("${surah.id}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = primary)
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(surah.nameSimple, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (surah.translatedName.isNotEmpty()) {
                    Text(surah.translatedName, fontSize = 12.sp, color = onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("$placeText  -  ${surah.versesCount} ${t("Ayet", "Verses", "Verse", "\u0622\u064A\u0629")}", fontSize = 11.sp, color = onSurfaceVariant.copy(alpha = 0.7f))
            }

            Text(surah.nameArabic, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = primary, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

// ── Surah Detail View ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SurahDetailView(
    surah: Surah,
    isDarkTheme: Boolean,
    selectedLanguage: String,
    onBack: () -> Unit
) {
    fun t(tr: String, en: String, de: String = "", ar: String = "") = when (selectedLanguage) {
        "en" -> en; "de" -> de.ifEmpty { en }; "ar" -> ar.ifEmpty { en }; else -> tr
    }

    val bg = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    var verses by remember { mutableStateOf<List<Verse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var currentPage by remember { mutableIntStateOf(1) }
    var totalPages by remember { mutableIntStateOf(1) }
    var isLoadingMore by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Audio state
    val audioPlayer = remember { QuranAudioPlayer() }
    var audioUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedReciter by remember { mutableStateOf(availableReciters[0]) }
    var isPlayingAll by remember { mutableStateOf(false) }
    var showReciterPicker by remember { mutableStateOf(false) }

    // Read audio state (triggers recomposition)
    val audioIsPlaying by audioPlayer.isPlaying
    val audioIsPreparing by audioPlayer.isPreparing
    val audioCurrentVerseKey by audioPlayer.currentVerseKey

    // Clean up on dispose
    DisposableEffect(Unit) {
        onDispose { audioPlayer.stop() }
    }

    // Fetch verses
    LaunchedEffect(surah.id, selectedLanguage) {
        isLoading = true
        currentPage = 1
        val (result, pages) = fetchVerses(surah.id, selectedLanguage, page = 1, perPage = 50)
        verses = result
        totalPages = pages
        isLoading = false
    }

    // Fetch audio urls when reciter changes
    LaunchedEffect(surah.id, selectedReciter.id) {
        audioUrls = fetchAudioUrls(selectedReciter.id, surah.id)
    }

    // Load more on scroll
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= verses.size - 5 && currentPage < totalPages && !isLoadingMore
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            isLoadingMore = true
            val nextPage = currentPage + 1
            val (result, _) = fetchVerses(surah.id, selectedLanguage, page = nextPage, perPage = 50)
            verses = verses + result
            currentPage = nextPage
            isLoadingMore = false
            // Also fetch audio for new pages
            val newAudioUrls = fetchAudioUrls(selectedReciter.id, surah.id)
            audioUrls = newAudioUrls
        }
    }

    // Sonraki ayeti arka planda hazırla
    fun preloadNextVerse(afterIndex: Int) {
        val nextIndex = afterIndex + 1
        if (nextIndex < verses.size) {
            val nextUrl = audioUrls[verses[nextIndex].verseKey]
            if (nextUrl != null) audioPlayer.preloadNext(nextUrl)
        }
    }

    // Play all sequentially
    fun playVerse(verseIndex: Int) {
        if (verseIndex >= verses.size) {
            isPlayingAll = false
            audioPlayer.stop()
            return
        }
        val verse = verses[verseIndex]
        val url = audioUrls[verse.verseKey]
        if (url != null) {
            audioPlayer.play(url, verse.verseKey) {
                if (isPlayingAll) {
                    playVerse(verseIndex + 1)
                }
            }
            // Sonraki ayeti hemen preload et
            preloadNextVerse(verseIndex)
        } else {
            if (isPlayingAll) playVerse(verseIndex + 1)
        }
    }

    fun startPlayAll(fromVerseIndex: Int = 0) {
        isPlayingAll = true
        playVerse(fromVerseIndex)
    }

    fun stopPlayAll() {
        isPlayingAll = false
        audioPlayer.stop()
    }

    // Auto-scroll to playing verse
    LaunchedEffect(audioCurrentVerseKey) {
        val key = audioCurrentVerseKey ?: return@LaunchedEffect
        val index = verses.indexOfFirst { it.verseKey == key }
        if (index >= 0) {
            // +1 offset for bismillah item (except surah 9)
            val itemIndex = if (surah.id != 9) index + 1 else index
            listState.animateScrollToItem(itemIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        // Top bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = surfaceColor,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { stopPlayAll(); onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = onSurface)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(surah.nameSimple, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = onSurface)
                    Text(
                        text = if (surah.translatedName.isNotEmpty()) surah.translatedName else "",
                        fontSize = 12.sp, color = onSurfaceVariant
                    )
                }
                Text(surah.nameArabic, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = primary)
            }
        }

        // Audio control bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (isDarkTheme) Color(0xFF1A2A1A) else Color(0xFFE8F5E9),
            shadowElevation = 1.dp
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Reciter selector
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        color = surfaceVariant.copy(alpha = 0.5f),
                        onClick = { showReciterPicker = true }
                    ) {
                        Text(
                            text = selectedReciter.name + if (selectedReciter.style.isNotEmpty()) " (${selectedReciter.style})" else "",
                            fontSize = 12.sp,
                            color = onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Playback controls
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Previous verse
                        IconButton(
                            onClick = {
                                val currentKey = audioCurrentVerseKey
                                val idx = verses.indexOfFirst { it.verseKey == currentKey }
                                if (idx > 0) {
                                    val wasPlayingAll = isPlayingAll
                                    audioPlayer.stop()
                                    if (wasPlayingAll) {
                                        isPlayingAll = true
                                        playVerse(idx - 1)
                                    } else {
                                        val url = audioUrls[verses[idx - 1].verseKey]
                                        if (url != null) audioPlayer.play(url, verses[idx - 1].verseKey) {}
                                    }
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
                        }

                        // Play/Pause all
                        if (audioIsPreparing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp).padding(6.dp),
                                color = primary,
                                strokeWidth = 2.dp
                            )
                        } else if (audioIsPlaying) {
                            IconButton(
                                onClick = { audioPlayer.pause() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = null, tint = primary)
                            }
                        } else if (audioCurrentVerseKey != null && !audioIsPlaying) {
                            // Paused state - resume
                            IconButton(
                                onClick = { audioPlayer.resume() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = primary)
                            }
                        } else {
                            // Not playing - start from beginning
                            IconButton(
                                onClick = { startPlayAll(0) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = primary)
                            }
                        }

                        // Next verse
                        IconButton(
                            onClick = {
                                val currentKey = audioCurrentVerseKey
                                val idx = verses.indexOfFirst { it.verseKey == currentKey }
                                if (idx >= 0 && idx < verses.size - 1) {
                                    val wasPlayingAll = isPlayingAll
                                    audioPlayer.stop()
                                    if (wasPlayingAll) {
                                        isPlayingAll = true
                                        playVerse(idx + 1)
                                    } else {
                                        val url = audioUrls[verses[idx + 1].verseKey]
                                        if (url != null) audioPlayer.play(url, verses[idx + 1].verseKey) {}
                                    }
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
                        }

                        // Stop
                        if (audioCurrentVerseKey != null || isPlayingAll) {
                            IconButton(
                                onClick = { stopPlayAll() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, tint = onSurfaceVariant, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                // Now playing indicator
                if (audioCurrentVerseKey != null) {
                    Text(
                        text = "${t("Okunan Ayet", "Now Playing", "Wird abgespielt", "\u064A\u062A\u0645 \u0627\u0644\u062A\u0634\u063A\u064A\u0644")}: $audioCurrentVerseKey",
                        fontSize = 11.sp,
                        color = primary,
                        modifier = Modifier.padding(start = 12.dp, bottom = 6.dp)
                    )
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = primary)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (surah.id != 9) {
                    item {
                        Text(
                            text = "\u0628\u0650\u0633\u0652\u0645\u0650 \u0671\u0644\u0644\u0651\u064E\u0647\u0650 \u0671\u0644\u0631\u0651\u064E\u062D\u0652\u0645\u064E\u0640\u0670\u0646\u0650 \u0671\u0644\u0631\u0651\u064E\u062D\u0650\u064A\u0645\u0650",
                            fontSize = 24.sp,
                            color = primary,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                        )
                    }
                }

                items(verses, key = { it.verseKey }) { verse ->
                    val isCurrentVerse = audioCurrentVerseKey == verse.verseKey
                    VerseCard(
                        verse = verse,
                        isHighlighted = isCurrentVerse,
                        isPlaying = isCurrentVerse && audioIsPlaying,
                        isPreparing = isCurrentVerse && audioIsPreparing,
                        primary = primary,
                        onSurface = onSurface,
                        onSurfaceVariant = onSurfaceVariant,
                        surfaceVariant = surfaceVariant,
                        onPlayClick = {
                            val url = audioUrls[verse.verseKey]
                            if (url != null) {
                                if (isCurrentVerse && audioIsPlaying) {
                                    audioPlayer.pause()
                                } else if (isCurrentVerse && !audioIsPlaying) {
                                    audioPlayer.resume()
                                } else {
                                    isPlayingAll = false
                                    audioPlayer.play(url, verse.verseKey) {}
                                }
                            }
                        },
                        onPlayFromHere = {
                            val idx = verses.indexOf(verse)
                            if (idx >= 0) startPlayAll(idx)
                        }
                    )
                }

                if (isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = primary, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }

    // Reciter picker dialog
    if (showReciterPicker) {
        AlertDialog(
            onDismissRequest = { showReciterPicker = false },
            title = {
                Text(
                    t("Kari Secin", "Select Reciter", "Rezitator wahlen", "\u0627\u062E\u062A\u0631 \u0627\u0644\u0642\u0627\u0631\u0626"),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                LazyColumn {
                    items(availableReciters) { reciter ->
                        val isSelected = reciter.id == selectedReciter.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) primary.copy(alpha = 0.12f) else Color.Transparent,
                            onClick = {
                                audioPlayer.stop()
                                isPlayingAll = false
                                selectedReciter = reciter
                                showReciterPicker = false
                            }
                        ) {
                            Text(
                                text = reciter.name + if (reciter.style.isNotEmpty()) " (${reciter.style})" else "",
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) primary else onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReciterPicker = false }) {
                    Text(t("Kapat", "Close", "Schliessen", "\u0625\u063A\u0644\u0627\u0642"))
                }
            }
        )
    }
}

// ── Verse Card ──

@Composable
private fun VerseCard(
    verse: Verse,
    isHighlighted: Boolean,
    isPlaying: Boolean,
    isPreparing: Boolean,
    primary: Color,
    onSurface: Color,
    onSurfaceVariant: Color,
    surfaceVariant: Color,
    onPlayClick: () -> Unit,
    onPlayFromHere: () -> Unit
) {
    val cardColor by animateColorAsState(
        targetValue = if (isHighlighted) primary.copy(alpha = 0.15f) else surfaceVariant.copy(alpha = 0.3f),
        animationSpec = tween(300),
        label = "verseHighlight"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = cardColor
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Verse number + play button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${verse.verseNumber}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = primary)
                }

                Row {
                    // Play from here (continuous)
                    IconButton(
                        onClick = onPlayFromHere,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play from here",
                            tint = primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Play single verse
                    if (isPreparing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp).padding(6.dp),
                            color = primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = onPlayClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (isHighlighted) primary else onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Arabic text
            Text(
                text = verse.textUthmani,
                fontSize = 26.sp,
                color = onSurface,
                textAlign = TextAlign.End,
                lineHeight = 46.sp,
                modifier = Modifier.fillMaxWidth()
            )

            // Translation
            if (verse.translationText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = onSurfaceVariant.copy(alpha = 0.15f), thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(verse.translationText, fontSize = 14.sp, color = onSurfaceVariant, lineHeight = 22.sp)
            }
        }
    }
}
