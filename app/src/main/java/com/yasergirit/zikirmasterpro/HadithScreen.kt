package com.yasergirit.zikirmasterpro

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.yasergirit.zikirmasterpro.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// ── Data Models ──

private data class HadithCollection(
    val key: String,
    val nameTr: String,
    val nameEn: String,
    val nameAr: String,
    val hadithCount: String
)

private data class HadithSection(
    val number: Int,
    val name: String,
    val hadithFirst: Int,
    val hadithLast: Int
)

private data class HadithItem(
    val number: Int,
    val text: String,
    val arabicText: String
)

private val hadithCollections = listOf(
    HadithCollection("bukhari", "Sahih-i Buhari", "Sahih al-Bukhari", "\u0635\u062D\u064A\u062D \u0627\u0644\u0628\u062E\u0627\u0631\u064A", "7563"),
    HadithCollection("muslim", "Sahih-i Muslim", "Sahih Muslim", "\u0635\u062D\u064A\u062D \u0645\u0633\u0644\u0645", "7500"),
    HadithCollection("tirmidhi", "Jami Tirmizi", "Jami at-Tirmidhi", "\u062C\u0627\u0645\u0639 \u0627\u0644\u062A\u0631\u0645\u0630\u064A", "3956"),
    HadithCollection("abudawud", "Sunen Ebu Davud", "Sunan Abu Dawud", "\u0633\u0646\u0646 \u0623\u0628\u064A \u062F\u0627\u0648\u062F", "5274"),
    HadithCollection("nasai", "Sunen Nesai", "Sunan an-Nasai", "\u0633\u0646\u0646 \u0627\u0644\u0646\u0633\u0627\u0626\u064A", "5758"),
    HadithCollection("ibnmajah", "Sunen Ibn Mace", "Sunan Ibn Majah", "\u0633\u0646\u0646 \u0627\u0628\u0646 \u0645\u0627\u062C\u0647", "4341"),
    HadithCollection("malik", "Muvatta Malik", "Muwatta Malik", "\u0645\u0648\u0637\u0623 \u0645\u0627\u0644\u0643", "1832"),
    HadithCollection("nawawi", "40 Hadis (Nevevi)", "40 Hadith an-Nawawi", "\u0627\u0644\u0623\u0631\u0628\u0639\u0648\u0646 \u0627\u0644\u0646\u0648\u0648\u064A\u0629", "42"),
    HadithCollection("qudsi", "40 Kutsi Hadis", "40 Hadith Qudsi", "\u0627\u0644\u0623\u062D\u0627\u062F\u064A\u062B \u0627\u0644\u0642\u062F\u0633\u064A\u0629", "40")
)

// ── API ──

private val hadithHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

private const val HADITH_CDN = "https://cdn.jsdelivr.net/gh/fawazahmed0/hadith-api@1"

private fun getLangCode(language: String): String = when (language) {
    "tr" -> "tur"
    "en" -> "eng"
    "de" -> "eng"
    "ar" -> "ara"
    else -> "tur"
}

private fun fetchJson(url: String): String? {
    return try {
        val request = Request.Builder().url(url).build()
        val response = hadithHttpClient.newCall(request).execute()
        if (response.isSuccessful) response.body?.string() else null
    } catch (_: Exception) { null }
}

/**
 * Fetch sections from full collection endpoint.
 * API uses "section" (singular) and "section_detail" (singular) keys.
 * Single hadith endpoint only returns its own section, so we need the full collection.
 */
private suspend fun fetchSections(
    collectionKey: String,
    language: String
): List<HadithSection> = withContext(Dispatchers.IO) {
    try {
        val langCode = getLangCode(language)
        val url = "$HADITH_CDN/editions/$langCode-$collectionKey.min.json"
        val body = fetchJson(url) ?: return@withContext emptyList()

        val json = JSONObject(body)
        val metadata = json.getJSONObject("metadata")
        // API uses "section" (singular), not "sections"
        val sections = metadata.optJSONObject("section") ?: metadata.optJSONObject("sections") ?: return@withContext emptyList()
        val sectionDetails = metadata.optJSONObject("section_detail") ?: metadata.optJSONObject("section_details")
        val list = mutableListOf<HadithSection>()

        val keys = sections.keys().asSequence().toList().sortedBy { it.toIntOrNull() ?: 0 }
        for (key in keys) {
            val num = key.toIntOrNull() ?: continue
            val name = sections.optString(key, "")
            if (name.isEmpty()) continue
            val detail = sectionDetails?.optJSONObject(key)
            val first = detail?.optInt("hadithnumber_first", 0) ?: 0
            val last = detail?.optInt("hadithnumber_last", 0) ?: 0
            list.add(HadithSection(num, name, first, last))
        }
        list
    } catch (_: Exception) { emptyList() }
}

private suspend fun fetchHadithsBySection(
    collectionKey: String,
    sectionNumber: Int,
    language: String
): List<HadithItem> = coroutineScope {
    withContext(Dispatchers.IO) {
        try {
            val langCode = getLangCode(language)
            val url = "$HADITH_CDN/editions/$langCode-$collectionKey/sections/$sectionNumber.min.json"
            val araUrl = "$HADITH_CDN/editions/ara-$collectionKey/sections/$sectionNumber.min.json"

            // Parallel fetch: translation + Arabic
            val translationDeferred = async { fetchJson(url) }
            val arabicDeferred = if (language != "ar") async { fetchJson(araUrl) } else null

            val body = translationDeferred.await()
            val araBody = arabicDeferred?.await()

        if (body == null) return@withContext emptyList()

        val json = JSONObject(body)
        val hadiths = json.getJSONArray("hadiths")

        val arabicMap = mutableMapOf<Int, String>()
        if (araBody != null) {
            try {
                val araJson = JSONObject(araBody)
                val araHadiths = araJson.getJSONArray("hadiths")
                for (i in 0 until araHadiths.length()) {
                    val h = araHadiths.getJSONObject(i)
                    arabicMap[h.getInt("hadithnumber")] = h.getString("text")
                }
            } catch (_: Exception) {}
        }

        val list = mutableListOf<HadithItem>()
        for (i in 0 until hadiths.length()) {
            val h = hadiths.getJSONObject(i)
            val num = h.getInt("hadithnumber")
            val text = h.getString("text")
            val arabicText = if (language == "ar") "" else (arabicMap[num] ?: "")
            list.add(HadithItem(num, text, arabicText))
        }
            list
        } catch (_: Exception) { emptyList() }
    }
}

// ── Cache ──

private object HadithCache {
    // Section cache per collection+language
    private val sectionsMap = mutableMapOf<String, List<HadithSection>>()
    // Hadith cache per collection+section+language
    private val hadithsMap = mutableMapOf<String, List<HadithItem>>()

    fun getSections(key: String): List<HadithSection>? = sectionsMap[key]
    fun putSections(key: String, sections: List<HadithSection>) { sectionsMap[key] = sections }

    fun getHadiths(key: String): List<HadithItem>? = hadithsMap[key]
    fun putHadiths(key: String, hadiths: List<HadithItem>) { hadithsMap[key] = hadiths }
}

// ── Search ──

private data class SearchResult(
    val hadithNumber: Int,
    val text: String,
    val collectionName: String,
    val collectionKey: String
)

// Searchable collections (small, fast to download)
private val searchableCollections = listOf("nawawi", "qudsi")

private suspend fun searchHadiths(
    query: String,
    language: String
): List<SearchResult> = coroutineScope {
    withContext(Dispatchers.IO) {
        if (query.length < 2) return@withContext emptyList()

        val langCode = getLangCode(language)
        val queryLower = query.lowercase()
        val results = mutableListOf<SearchResult>()

        // Search across small collections in parallel
        val deferreds = searchableCollections.map { collKey ->
            async {
                try {
                    val cacheKey = "search-$langCode-$collKey"
                    val cached = HadithCache.getHadiths(cacheKey)
                    val hadiths = if (cached != null) {
                        cached
                    } else {
                        val url = "$HADITH_CDN/editions/$langCode-$collKey.min.json"
                        val body = fetchJson(url) ?: return@async emptyList()
                        val json = JSONObject(body)
                        val arr = json.getJSONArray("hadiths")
                        val list = mutableListOf<HadithItem>()
                        for (i in 0 until arr.length()) {
                            val h = arr.getJSONObject(i)
                            list.add(HadithItem(h.getInt("hadithnumber"), h.getString("text"), ""))
                        }
                        HadithCache.putHadiths(cacheKey, list)
                        list
                    }

                    val collName = hadithCollections.find { it.key == collKey }
                    hadiths.filter { it.text.lowercase().contains(queryLower) }
                        .map { SearchResult(it.number, it.text, collName?.nameTr ?: collKey, collKey) }
                } catch (_: Exception) { emptyList() }
            }
        }

        deferreds.forEach { results.addAll(it.await()) }
        results.take(50)
    }
}

// Suggestion keywords
private fun getSuggestions(language: String): List<String> = when (language) {
    "tr" -> listOf("niyet", "namaz", "oruç", "zekat", "hac", "iman", "ihsan", "sabır", "şükür", "tövbe", "helal", "haram", "ilim", "amel", "dua", "sadaka", "komşu", "anne", "baba", "cennet", "cehennem", "kalp", "günah", "merhamet", "adalet")
    "ar" -> listOf("نية", "صلاة", "صوم", "زكاة", "حج", "إيمان", "إحسان", "صبر", "شكر", "توبة", "حلال", "حرام", "علم", "عمل", "دعاء", "صدقة", "جار", "أم", "جنة", "نار", "قلب", "ذنب", "رحمة", "عدل")
    else -> listOf("intention", "prayer", "fasting", "charity", "faith", "patience", "gratitude", "repentance", "knowledge", "deeds", "supplication", "neighbor", "paradise", "hellfire", "heart", "sin", "mercy", "justice", "mother", "father")
}

// ── Main Entry ──

@Composable
fun HadithScreen(
    isDarkTheme: Boolean,
    selectedLanguage: String
) {
    var selectedCollection by remember { mutableStateOf<HadithCollection?>(null) }
    var selectedSection by remember { mutableStateOf<HadithSection?>(null) }
    var showSearchResults by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    when {
        showSearchResults -> {
            SearchResultsView(
                query = searchQuery,
                isDarkTheme = isDarkTheme,
                selectedLanguage = selectedLanguage,
                onBack = { showSearchResults = false }
            )
        }
        selectedSection != null && selectedCollection != null -> {
            HadithListView(
                collection = selectedCollection!!,
                section = selectedSection!!,
                isDarkTheme = isDarkTheme,
                selectedLanguage = selectedLanguage,
                onBack = { selectedSection = null }
            )
        }
        selectedCollection != null -> {
            SectionListView(
                collection = selectedCollection!!,
                isDarkTheme = isDarkTheme,
                selectedLanguage = selectedLanguage,
                onSectionClick = { selectedSection = it },
                onBack = { selectedCollection = null }
            )
        }
        else -> {
            CollectionListView(
                isDarkTheme = isDarkTheme,
                selectedLanguage = selectedLanguage,
                onCollectionClick = { selectedCollection = it },
                onSearch = { query ->
                    searchQuery = query
                    showSearchResults = true
                }
            )
        }
    }
}

// ── Collection List ──

@Composable
private fun CollectionListView(
    isDarkTheme: Boolean,
    selectedLanguage: String,
    onCollectionClick: (HadithCollection) -> Unit,
    onSearch: (String) -> Unit
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

    var searchText by remember { mutableStateOf("") }
    var showSuggestions by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val suggestions = remember(selectedLanguage) { getSuggestions(selectedLanguage) }
    val filteredSuggestions by remember(searchText, suggestions) {
        derivedStateOf {
            if (searchText.length >= 1) {
                suggestions.filter { it.lowercase().startsWith(searchText.lowercase()) }.take(5)
            } else emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        // Search bar + subtitle
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = surfaceColor,
            shadowElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                // Search bar
                OutlinedTextField(
                    value = searchText,
                    onValueChange = {
                        searchText = it
                        showSuggestions = it.isNotEmpty()
                    },
                    placeholder = {
                        Text(
                            t("Hadislerde ara...", "Search hadiths...", "Hadithe suchen...", "\u0627\u0628\u062D\u062B \u0641\u064A \u0627\u0644\u0623\u062D\u0627\u062F\u064A\u062B..."),
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = onSurfaceVariant) },
                    trailingIcon = {
                        if (searchText.isNotEmpty()) {
                            IconButton(onClick = { searchText = ""; showSuggestions = false }) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = onSurfaceVariant, modifier = Modifier.size(20.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primary,
                        unfocusedBorderColor = surfaceVariant,
                        focusedContainerColor = surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = surfaceVariant.copy(alpha = 0.3f)
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (searchText.length >= 2) {
                                focusManager.clearFocus()
                                showSuggestions = false
                                onSearch(searchText)
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                )

                // Autocomplete suggestions
                if (showSuggestions && filteredSuggestions.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                        color = surfaceColor,
                        shadowElevation = 4.dp
                    ) {
                        Column {
                            filteredSuggestions.forEach { suggestion ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            searchText = suggestion
                                            showSuggestions = false
                                            focusManager.clearFocus()
                                            onSearch(suggestion)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = onSurfaceVariant, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(suggestion, fontSize = 14.sp, color = onSurface)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = t("Spesifik bir konu ile ilgili hadis arayin...", "Search for hadiths on a specific topic...", "Suchen Sie nach Hadithen zu einem bestimmten Thema...", "\u0627\u0628\u062D\u062B \u0639\u0646 \u0623\u062D\u0627\u062F\u064A\u062B \u062D\u0648\u0644 \u0645\u0648\u0636\u0648\u0639 \u0645\u0639\u064A\u0646..."),
                    fontSize = 12.sp,
                    color = onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            items(hadithCollections) { collection ->
                val name = when (selectedLanguage) {
                    "en", "de" -> collection.nameEn
                    "ar" -> collection.nameAr
                    else -> collection.nameTr
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = surfaceVariant.copy(alpha = 0.4f),
                    onClick = { onCollectionClick(collection) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "\u0635",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = primary
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${collection.hadithCount} ${t("Hadis", "Hadiths", "Hadithe", "\u062D\u062F\u064A\u062B")}",
                                fontSize = 12.sp,
                                color = onSurfaceVariant
                            )
                        }

                        if (selectedLanguage != "ar") {
                            Text(
                                text = collection.nameAr,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = primary.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Section (Chapter) List ──

@Composable
private fun SectionListView(
    collection: HadithCollection,
    isDarkTheme: Boolean,
    selectedLanguage: String,
    onSectionClick: (HadithSection) -> Unit,
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

    var sections by remember { mutableStateOf<List<HadithSection>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val cacheKey = "${selectedLanguage}-${collection.key}"

    LaunchedEffect(cacheKey) {
        val cached = HadithCache.getSections(cacheKey)
        if (cached != null) {
            sections = cached
            isLoading = false
        } else {
            isLoading = true
            val result = fetchSections(collection.key, selectedLanguage)
            sections = result
            if (result.isNotEmpty()) HadithCache.putSections(cacheKey, result)
            isLoading = false
        }
    }

    val collectionName = when (selectedLanguage) {
        "en", "de" -> collection.nameEn
        "ar" -> collection.nameAr
        else -> collection.nameTr
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
                    .padding(start = 4.dp, top = 8.dp, bottom = 8.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = onSurface)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(collectionName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = onSurface)
                    Text(
                        text = t("Bolumler", "Chapters", "Kapitel", "\u0627\u0644\u0623\u0628\u0648\u0627\u0628"),
                        fontSize = 12.sp,
                        color = onSurfaceVariant
                    )
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = t("Bolumler yukleniyor...", "Loading chapters...", "Kapitel werden geladen...", "\u062C\u0627\u0631\u064A \u062A\u062D\u0645\u064A\u0644 \u0627\u0644\u0623\u0628\u0648\u0627\u0628..."),
                        fontSize = 13.sp,
                        color = onSurfaceVariant
                    )
                }
            }
        } else if (sections.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = t("Bolum bulunamadi", "No chapters found", "Keine Kapitel gefunden", "\u0644\u0645 \u064A\u062A\u0645 \u0627\u0644\u0639\u062B\u0648\u0631 \u0639\u0644\u0649 \u0623\u0628\u0648\u0627\u0628"),
                    fontSize = 14.sp,
                    color = onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                items(sections) { section ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = surfaceVariant.copy(alpha = 0.4f),
                        onClick = { onSectionClick(section) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${section.number}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primary
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = section.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (section.hadithFirst > 0) {
                                    Text(
                                        text = "${t("Hadis", "Hadith", "Hadith", "\u062D\u062F\u064A\u062B")} ${section.hadithFirst} - ${section.hadithLast}",
                                        fontSize = 11.sp,
                                        color = onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Hadith List (by Section) ──

@Composable
private fun HadithListView(
    collection: HadithCollection,
    section: HadithSection,
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

    var hadiths by remember { mutableStateOf<List<HadithItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val hadithCacheKey = "${selectedLanguage}-${collection.key}-${section.number}"

    LaunchedEffect(hadithCacheKey) {
        val cached = HadithCache.getHadiths(hadithCacheKey)
        if (cached != null) {
            hadiths = cached
            isLoading = false
        } else {
            isLoading = true
            val result = fetchHadithsBySection(collection.key, section.number, selectedLanguage)
            hadiths = result
            if (result.isNotEmpty()) HadithCache.putHadiths(hadithCacheKey, result)
            isLoading = false
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
                    .padding(start = 4.dp, top = 8.dp, bottom = 8.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = onSurface)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = section.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when (selectedLanguage) {
                            "en", "de" -> collection.nameEn
                            "ar" -> collection.nameAr
                            else -> collection.nameTr
                        },
                        fontSize = 12.sp,
                        color = onSurfaceVariant
                    )
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = primary)
            }
        } else if (hadiths.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = t("Hadis bulunamadi", "No hadiths found", "Keine Hadithe gefunden", "\u0644\u0645 \u064A\u062A\u0645 \u0627\u0644\u0639\u062B\u0648\u0631 \u0639\u0644\u0649 \u0623\u062D\u0627\u062F\u064A\u062B"),
                    fontSize = 14.sp,
                    color = onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                items(hadiths, key = { it.number }) { hadith ->
                    HadithCard(
                        hadith = hadith,
                        selectedLanguage = selectedLanguage,
                        primary = primary,
                        onSurface = onSurface,
                        onSurfaceVariant = onSurfaceVariant,
                        surfaceVariant = surfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HadithCard(
    hadith: HadithItem,
    selectedLanguage: String,
    primary: androidx.compose.ui.graphics.Color,
    onSurface: androidx.compose.ui.graphics.Color,
    onSurfaceVariant: androidx.compose.ui.graphics.Color,
    surfaceVariant: androidx.compose.ui.graphics.Color
) {
    fun t(tr: String, en: String, de: String = "", ar: String = "") = when (selectedLanguage) {
        "en" -> en; "de" -> de.ifEmpty { en }; "ar" -> ar.ifEmpty { en }; else -> tr
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Hadith number
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${hadith.number}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = primary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${t("Hadis", "Hadith", "Hadith", "\u062D\u062F\u064A\u062B")} #${hadith.number}",
                    fontSize = 12.sp,
                    color = onSurfaceVariant
                )
            }

            // Arabic text (if not already Arabic language)
            if (hadith.arabicText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = hadith.arabicText,
                    fontSize = 20.sp,
                    color = onSurface,
                    textAlign = TextAlign.End,
                    lineHeight = 36.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = onSurfaceVariant.copy(alpha = 0.15f), thickness = 0.5.dp)
            }

            // Translation text
            Spacer(modifier = Modifier.height(if (hadith.arabicText.isNotEmpty()) 8.dp else 12.dp))
            Text(
                text = hadith.text,
                fontSize = 14.sp,
                color = if (selectedLanguage == "ar") onSurface else onSurfaceVariant,
                lineHeight = 22.sp,
                textAlign = if (selectedLanguage == "ar") TextAlign.End else TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ── Search Results View ──

@Composable
private fun SearchResultsView(
    query: String,
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

    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(query, selectedLanguage) {
        isLoading = true
        results = searchHadiths(query, selectedLanguage)
        isLoading = false
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
                    .padding(start = 4.dp, top = 8.dp, bottom = 8.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = onSurface)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "\"$query\"",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isLoading)
                            t("Aranıyor...", "Searching...", "Suche...", "\u062C\u0627\u0631\u064A \u0627\u0644\u0628\u062D\u062B...")
                        else
                            "${results.size} ${t("sonuç", "results", "Ergebnisse", "\u0646\u062A\u064A\u062C\u0629")}",
                        fontSize = 12.sp,
                        color = onSurfaceVariant
                    )
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = primary)
            }
        } else if (results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = t("Sonuç bulunamadı", "No results found", "Keine Ergebnisse", "\u0644\u0627 \u062A\u0648\u062C\u062F \u0646\u062A\u0627\u0626\u062C"),
                        fontSize = 16.sp,
                        color = onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = t(
                            "Nevevi 40 Hadis ve Kutsi Hadislerde arandı",
                            "Searched in Nawawi 40 and Qudsi Hadiths",
                            "Gesucht in Nawawi 40 und Qudsi Hadithen",
                            "\u062A\u0645 \u0627\u0644\u0628\u062D\u062B \u0641\u064A \u0627\u0644\u0623\u0631\u0628\u0639\u0648\u0646 \u0627\u0644\u0646\u0648\u0648\u064A\u0629 \u0648\u0627\u0644\u0623\u062D\u0627\u062F\u064A\u062B \u0627\u0644\u0642\u062F\u0633\u064A\u0629"
                        ),
                        fontSize = 12.sp,
                        color = onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                items(results, key = { "${it.collectionKey}-${it.hadithNumber}" }) { result ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = surfaceVariant.copy(alpha = 0.3f)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(primary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${result.hadithNumber}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = primary)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = result.collectionName,
                                    fontSize = 12.sp,
                                    color = primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = result.text,
                                fontSize = 14.sp,
                                color = onSurface,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
