package com.yasergirit.zikirmasterpro

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// ── Generic story item ──
data class StoryItem(
    val text: String,
    val source: String,
    val detail: String
)

enum class StoryCategory { VERSE, HADITH, QUOTE }

private const val STORY_DURATION_MS = 10_000
private const val STORY_COUNT = 2

// ── Public composables for each category ──

@Composable
fun VerseStoryOverlay(selectedLanguage: String, onDismiss: () -> Unit) {
    fun t(tr: String, en: String, de: String = "", ar: String = "") = when (selectedLanguage) {
        "en" -> en; "de" -> de.ifEmpty { en }; "ar" -> ar.ifEmpty { en }; else -> tr
    }
    StoryOverlay(
        title = t("Ayet", "Verse", "Vers", "آية"),
        gradientColors = listOf(Color(0xFF0D4B3C), Color(0xFF1A2A1A), Color(0xFF0A0A0A)),
        accentColor = Color(0xFFB8860B),
        decorativeText = "﷽",
        selectedLanguage = selectedLanguage,
        category = StoryCategory.VERSE,
        onDismiss = onDismiss
    )
}

@Composable
fun HadithStoryOverlay(selectedLanguage: String, onDismiss: () -> Unit) {
    fun t(tr: String, en: String, de: String = "", ar: String = "") = when (selectedLanguage) {
        "en" -> en; "de" -> de.ifEmpty { en }; "ar" -> ar.ifEmpty { en }; else -> tr
    }
    StoryOverlay(
        title = t("Hadis", "Hadith", "Hadith", "حديث"),
        gradientColors = listOf(Color(0xFF2D1B4E), Color(0xFF1A1A2E), Color(0xFF0A0A0A)),
        accentColor = Color(0xFFCE93D8),
        decorativeText = "ﷺ",
        selectedLanguage = selectedLanguage,
        category = StoryCategory.HADITH,
        onDismiss = onDismiss
    )
}

@Composable
fun QuoteStoryOverlay(selectedLanguage: String, onDismiss: () -> Unit) {
    fun t(tr: String, en: String, de: String = "", ar: String = "") = when (selectedLanguage) {
        "en" -> en; "de" -> de.ifEmpty { en }; "ar" -> ar.ifEmpty { en }; else -> tr
    }
    StoryOverlay(
        title = t("Özlü Sözler", "Wise Quotes", "Weisheiten", "حكم"),
        gradientColors = listOf(Color(0xFF4B3A0D), Color(0xFF2A1A0A), Color(0xFF0A0A0A)),
        accentColor = Color(0xFFFFB74D),
        decorativeText = "✨",
        selectedLanguage = selectedLanguage,
        category = StoryCategory.QUOTE,
        onDismiss = onDismiss
    )
}

// ── Generic Story Overlay ──

@Composable
private fun StoryOverlay(
    title: String,
    gradientColors: List<Color>,
    accentColor: Color,
    decorativeText: String,
    selectedLanguage: String,
    category: StoryCategory,
    onDismiss: () -> Unit
) {
    fun t(tr: String, en: String, de: String = "", ar: String = "") = when (selectedLanguage) {
        "en" -> en; "de" -> de.ifEmpty { en }; "ar" -> ar.ifEmpty { en }; else -> tr
    }

    var items by remember { mutableStateOf<List<StoryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // Fetch items in parallel
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val fetchFn: suspend () -> StoryItem? = {
                when (category) {
                    StoryCategory.VERSE -> fetchRandomVerse(selectedLanguage)
                    StoryCategory.HADITH -> fetchRandomHadith(selectedLanguage)
                    StoryCategory.QUOTE -> fetchRandomQuote(selectedLanguage)
                }
            }
            val deferred = (1..STORY_COUNT).map { async { fetchFn() } }
            val fetched = deferred.awaitAll().filterNotNull()
            items = fetched
            isLoading = false
        }
    }

    // Reset progress whenever story changes
    LaunchedEffect(currentIndex) {
        progress.snapTo(0f)
    }

    // Auto-advance timer with pause/resume support
    LaunchedEffect(currentIndex, isLoading, isPaused) {
        if (isLoading || items.isEmpty() || isPaused) return@LaunchedEffect
        val remaining = 1f - progress.value
        if (remaining <= 0.001f) return@LaunchedEffect
        val remainingMs = (remaining * STORY_DURATION_MS).toInt()
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = remainingMs, easing = LinearEasing)
        )
        // Only auto-advance if animation completed naturally (not stopped by pause)
        if (!isPaused && progress.value >= 1f) {
            if (currentIndex < items.size - 1) {
                currentIndex++
            } else {
                onDismiss()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = gradientColors))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPaused = true
                        tryAwaitRelease()
                        isPaused = false
                    },
                    onTap = { offset ->
                        val screenWidth = size.width
                        if (offset.x > screenWidth / 2) {
                            // Right tap: next story or dismiss on last
                            if (currentIndex < items.size - 1) {
                                currentIndex++
                            } else {
                                onDismiss()
                            }
                        } else {
                            // Left tap: previous story
                            if (currentIndex > 0) {
                                currentIndex--
                            }
                        }
                    }
                )
            }
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (items.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = t("Yüklenemedi", "Could not load", "Laden fehlgeschlagen", "تعذر التحميل"),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onDismiss) {
                    Text(t("Kapat", "Close", "Schließen", "إغلاق"), color = Color.White)
                }
            }
        } else {
            val item = items[currentIndex]

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 12.dp)
            ) {
                // Progress bars
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (i in 0 until items.size) {
                        val barProgress = when {
                            i < currentIndex -> 1f
                            i == currentIndex -> progress.value
                            else -> 0f
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.3f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = barProgress)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White)
                            )
                        }
                    }
                }

                // Title + Close
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Content centered
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(horizontal = 32.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Decorative symbol
                        Text(
                            text = decorativeText,
                            fontSize = 28.sp,
                            color = accentColor,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // Main text
                        Text(
                            text = "\"${item.text}\"",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            lineHeight = 32.sp
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Divider
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(2.dp)
                                .background(accentColor)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Source
                        Text(
                            text = item.source,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Detail
                        Text(
                            text = item.detail,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Page dots
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until items.size) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (i == currentIndex) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i == currentIndex) Color.White
                                    else Color.White.copy(alpha = 0.3f)
                                )
                        )
                    }
                }
            }
        }
    }
}

// ── Turkish Surah Names ──

private val surahNamesTr = mapOf(
    1 to "Fatiha", 2 to "Bakara", 3 to "Âl-i İmrân", 4 to "Nisâ", 5 to "Mâide",
    6 to "En'âm", 7 to "A'râf", 8 to "Enfâl", 9 to "Tevbe", 10 to "Yûnus",
    11 to "Hûd", 12 to "Yûsuf", 13 to "Ra'd", 14 to "İbrâhîm", 15 to "Hicr",
    16 to "Nahl", 17 to "İsrâ", 18 to "Kehf", 19 to "Meryem", 20 to "Tâ-Hâ",
    21 to "Enbiyâ", 22 to "Hac", 23 to "Mü'minûn", 24 to "Nûr", 25 to "Furkân",
    26 to "Şuarâ", 27 to "Neml", 28 to "Kasas", 29 to "Ankebût", 30 to "Rûm",
    31 to "Lokmân", 32 to "Secde", 33 to "Ahzâb", 34 to "Sebe'", 35 to "Fâtır",
    36 to "Yâsîn", 37 to "Sâffât", 38 to "Sâd", 39 to "Zümer", 40 to "Mü'min",
    41 to "Fussilet", 42 to "Şûrâ", 43 to "Zuhruf", 44 to "Duhân", 45 to "Câsiye",
    46 to "Ahkâf", 47 to "Muhammed", 48 to "Fetih", 49 to "Hucurât", 50 to "Kâf",
    51 to "Zâriyât", 52 to "Tûr", 53 to "Necm", 54 to "Kamer", 55 to "Rahmân",
    56 to "Vâkıa", 57 to "Hadîd", 58 to "Mücâdele", 59 to "Haşr", 60 to "Mümtehine",
    61 to "Saff", 62 to "Cum'a", 63 to "Münâfikûn", 64 to "Tegâbün", 65 to "Talâk",
    66 to "Tahrîm", 67 to "Mülk", 68 to "Kalem", 69 to "Hâkka", 70 to "Meâric",
    71 to "Nûh", 72 to "Cin", 73 to "Müzzemmil", 74 to "Müddessir", 75 to "Kıyâmet",
    76 to "İnsan", 77 to "Mürselât", 78 to "Nebe'", 79 to "Nâziât", 80 to "Abese",
    81 to "Tekvîr", 82 to "İnfitâr", 83 to "Mutaffifîn", 84 to "İnşikâk", 85 to "Bürûc",
    86 to "Târık", 87 to "A'lâ", 88 to "Gâşiye", 89 to "Fecr", 90 to "Beled",
    91 to "Şems", 92 to "Leyl", 93 to "Duhâ", 94 to "İnşirâh", 95 to "Tîn",
    96 to "Alak", 97 to "Kadr", 98 to "Beyyine", 99 to "Zilzâl", 100 to "Âdiyât",
    101 to "Kâria", 102 to "Tekâsür", 103 to "Asr", 104 to "Hümeze", 105 to "Fîl",
    106 to "Kureyş", 107 to "Mâûn", 108 to "Kevser", 109 to "Kâfirûn", 110 to "Nasr",
    111 to "Tebbet", 112 to "İhlâs", 113 to "Felak", 114 to "Nâs"
)

// ── API Fetch Functions ──

private val httpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .build()

/** AlQuran Cloud API - Random verse in selected language */
private fun fetchRandomVerse(lang: String): StoryItem? {
    return try {
        val edition = when (lang) {
            "en" -> "en.sahih"
            "de" -> "de.bubenheim"
            "ar" -> "quran-uthmani"
            else -> "tr.diyanet"
        }
        val request = Request.Builder()
            .url("https://api.alquran.cloud/v1/ayah/random/$edition?_=${System.currentTimeMillis()}")
            .cacheControl(CacheControl.FORCE_NETWORK)
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string()

        if (response.isSuccessful && body != null) {
            val data = JSONObject(body).getJSONObject("data")
            val text = data.optString("text", "")
            val surah = data.getJSONObject("surah")
            val englishName = surah.optString("englishName", "")
            val arabicName = surah.optString("name", "")
            val surahNumber = surah.optInt("number", 0)
            val ayahNumber = data.optInt("numberInSurah", 0)
            val turkishName = surahNamesTr[surahNumber] ?: englishName

            val source = when (lang) {
                "en" -> "Surah $englishName"
                "de" -> "Sure $englishName"
                "ar" -> "سورة ${arabicName.removePrefix("سورة ").trim()}"
                else -> "$turkishName Suresi"
            }
            val detail = when (lang) {
                "en" -> "Verse $ayahNumber"
                "de" -> "Vers $ayahNumber"
                "ar" -> "الآية $ayahNumber"
                else -> "${ayahNumber}. Ayet"
            }

            if (text.isNotEmpty()) {
                StoryItem(text = text, source = source, detail = detail)
            } else null
        } else null
    } catch (e: Exception) {
        Log.e("StoryFetch", "Verse API error", e)
        null
    }
}

/** Curated hadith collection - multilingual */
private data class MLHadith(
    val tr: String, val en: String, val de: String, val ar: String,
    val sourceTr: String, val sourceEn: String, val sourceAr: String,
    val detailNo: String = ""
)

private val mlHadiths = listOf(
    MLHadith("Ameller niyetlere göredir. Herkesin niyet ettiği ne ise eline geçecek olan odur.", "Actions are judged by intentions. Each person will get what they intended.", "Die Taten sind entsprechend den Absichten. Jedem Menschen steht das zu, was er beabsichtigt hat.", "إنما الأعمال بالنيات وإنما لكل امرئ ما نوى", "Sahih-i Buhari", "Sahih al-Bukhari", "صحيح البخاري", "1"),
    MLHadith("Müslüman, elinden ve dilinden Müslümanların güvende olduğu kimsedir.", "A Muslim is one from whose tongue and hand other Muslims are safe.", "Ein Muslim ist derjenige, vor dessen Zunge und Hand die anderen Muslime sicher sind.", "المسلم من سلم المسلمون من لسانه ويده", "Sahih-i Buhari", "Sahih al-Bukhari", "صحيح البخاري", "10"),
    MLHadith("Sizden biriniz, kendisi için istediğini kardeşi için de istemedikçe gerçek anlamda iman etmiş olmaz.", "None of you truly believes until he loves for his brother what he loves for himself.", "Keiner von euch ist wahrhaft gläubig, bis er für seinen Bruder wünscht, was er für sich selbst wünscht.", "لا يؤمن أحدكم حتى يحب لأخيه ما يحب لنفسه", "Sahih-i Buhari", "Sahih al-Bukhari", "صحيح البخاري", "13"),
    MLHadith("Birbirinize haset etmeyin, kin tutmayın, sırt çevirmeyin. Ey Allah'ın kulları, kardeş olun!", "Do not envy one another, do not bear grudges, do not turn away from each other. O servants of Allah, be brothers!", "Beneidet einander nicht, hegt keinen Groll, wendet euch nicht voneinander ab. O Diener Allahs, seid Brüder!", "لا تحاسدوا ولا تباغضوا ولا تدابروا وكونوا عباد الله إخوانا", "Sahih-i Müslim", "Sahih Muslim", "صحيح مسلم", "2559"),
    MLHadith("Kim Allah'a ve âhiret gününe inanıyorsa, ya hayır söylesin ya da sussun.", "Whoever believes in Allah and the Last Day, let him speak good or remain silent.", "Wer an Allah und den Jüngsten Tag glaubt, soll Gutes sprechen oder schweigen.", "من كان يؤمن بالله واليوم الآخر فليقل خيرا أو ليصمت", "Sahih-i Buhari", "Sahih al-Bukhari", "صحيح البخاري", "6018"),
    MLHadith("Güçlü kimse güreşte yenen değildir. Asıl güçlü, öfkelendiği zaman kendini tutan kimsedir.", "The strong person is not the one who can wrestle others. The strong person is the one who controls himself when angry.", "Der Starke ist nicht derjenige, der im Ringen siegt. Der Starke ist derjenige, der sich im Zorn beherrscht.", "ليس الشديد بالصرعة إنما الشديد الذي يملك نفسه عند الغضب", "Sahih-i Buhari", "Sahih al-Bukhari", "صحيح البخاري", "6114"),
    MLHadith("Kolaylaştırınız, zorlaştırmayınız. Müjdeleyiniz, nefret ettirmeyiniz.", "Make things easy and do not make them difficult. Give glad tidings and do not repel people.", "Macht es leicht und nicht schwer. Überbringt frohe Botschaft und schreckt nicht ab.", "يسروا ولا تعسروا وبشروا ولا تنفروا", "Sahih-i Buhari", "Sahih al-Bukhari", "صحيح البخاري", "69"),
    MLHadith("Temizlik imanın yarısıdır.", "Cleanliness is half of faith.", "Reinheit ist die Hälfte des Glaubens.", "الطهور شطر الإيمان", "Sahih-i Müslim", "Sahih Muslim", "صحيح مسلم", "223"),
    MLHadith("Allah sizin suretlerinize ve mallarınıza bakmaz; fakat kalplerinize ve amellerinize bakar.", "Allah does not look at your appearance or wealth, but rather He looks at your hearts and deeds.", "Allah schaut nicht auf euer Äußeres und euren Besitz, sondern auf eure Herzen und eure Taten.", "إن الله لا ينظر إلى صوركم وأموالكم ولكن ينظر إلى قلوبكم وأعمالكم", "Sahih-i Müslim", "Sahih Muslim", "صحيح مسلم", "2564"),
    MLHadith("Güleryüzle karşılaşmak da sadakadır.", "Smiling in the face of your brother is an act of charity.", "Deinem Bruder freundlich zu begegnen ist eine milde Gabe.", "تبسمك في وجه أخيك لك صدقة", "Tirmizi", "Tirmidhi", "الترمذي", "1956"),
    MLHadith("Komşusu açken tok yatan bizden değildir.", "He is not one of us who sleeps full while his neighbor goes hungry.", "Wer satt schlafen geht, während sein Nachbar hungert, gehört nicht zu uns.", "ليس المؤمن الذي يشبع وجاره جائع", "Sahih-i Buhari", "Sahih al-Bukhari", "صحيح البخاري"),
    MLHadith("İnsanların en hayırlısı, insanlara en çok faydası dokunandır.", "The best of people are those who are most beneficial to others.", "Die besten Menschen sind diejenigen, die anderen am meisten nützen.", "خير الناس أنفعهم للناس", "Taberâni", "At-Tabarani", "الطبراني"),
    MLHadith("Merhamet etmeyene merhamet olunmaz.", "He who shows no mercy will not be shown mercy.", "Wer keine Barmherzigkeit zeigt, dem wird keine Barmherzigkeit erwiesen.", "من لا يرحم لا يُرحم", "Sahih-i Buhari", "Sahih al-Bukhari", "صحيح البخاري", "5997"),
    MLHadith("Cennet annelerin ayakları altındadır.", "Paradise lies under the feet of mothers.", "Das Paradies liegt unter den Füßen der Mütter.", "الجنة تحت أقدام الأمهات", "Nesâi", "An-Nasa'i", "النسائي", "3104"),
    MLHadith("İlim öğrenmek her Müslümana farzdır.", "Seeking knowledge is an obligation upon every Muslim.", "Das Streben nach Wissen ist eine Pflicht für jeden Muslim.", "طلب العلم فريضة على كل مسلم", "İbn Mâce", "Ibn Majah", "ابن ماجه", "224"),
    MLHadith("Küçüklerimize merhamet etmeyen, büyüklerimize saygı göstermeyen bizden değildir.", "He is not one of us who does not show mercy to our young and respect to our elders.", "Wer unseren Jungen keine Barmherzigkeit und unseren Älteren keinen Respekt erweist, gehört nicht zu uns.", "ليس منا من لم يرحم صغيرنا ويوقر كبيرنا", "Tirmizi", "Tirmidhi", "الترمذي", "1919"),
    MLHadith("Bir yolda ilim aramak için yürüyen kimseye Allah cennet yolunu kolaylaştırır.", "Whoever takes a path in search of knowledge, Allah will make easy for him the path to Paradise.", "Wer einen Weg einschlägt, um Wissen zu erlangen, dem erleichtert Allah den Weg ins Paradies.", "من سلك طريقا يلتمس فيه علما سهل الله له به طريقا إلى الجنة", "Sahih-i Müslim", "Sahih Muslim", "صحيح مسلم", "2699"),
    MLHadith("Dünya müminin zindanı, kâfirin cennetidir.", "The world is a prison for the believer and a paradise for the disbeliever.", "Die Welt ist ein Gefängnis für den Gläubigen und ein Paradies für den Ungläubigen.", "الدنيا سجن المؤمن وجنة الكافر", "Sahih-i Müslim", "Sahih Muslim", "صحيح مسلم", "2956"),
    MLHadith("Kul, din kardeşinin yardımında olduğu müddetçe, Allah da onun yardımındadır.", "Allah helps His servant as long as the servant helps his brother.", "Allah hilft Seinem Diener, solange der Diener seinem Bruder hilft.", "والله في عون العبد ما كان العبد في عون أخيه", "Sahih-i Müslim", "Sahih Muslim", "صحيح مسلم", "2580"),
    MLHadith("Sabır, acı bir olayın ilk anında gösterilendir.", "Patience is shown at the first moment of calamity.", "Geduld zeigt sich im ersten Moment des Unglücks.", "إنما الصبر عند الصدمة الأولى", "Sahih-i Buhari", "Sahih al-Bukhari", "صحيح البخاري", "1283"),
    MLHadith("Midenin üçte birini yemeğe, üçte birini suya, üçte birini nefesine ayır.", "Fill one-third of your stomach with food, one-third with drink, and leave one-third for breathing.", "Fülle ein Drittel deines Magens mit Essen, ein Drittel mit Trinken und lasse ein Drittel für das Atmen.", "ثلث لطعامه وثلث لشرابه وثلث لنفسه", "Tirmizi", "Tirmidhi", "الترمذي", "2380"),
    MLHadith("Hayâ imandandır.", "Modesty is part of faith.", "Bescheidenheit ist ein Teil des Glaubens.", "الحياء من الإيمان", "Sahih-i Buhari", "Sahih al-Bukhari", "صحيح البخاري", "24"),
    MLHadith("Güzel söz sadakadır.", "A good word is an act of charity.", "Ein gutes Wort ist eine milde Gabe.", "الكلمة الطيبة صدقة", "Sahih-i Buhari", "Sahih al-Bukhari", "صحيح البخاري", "2989"),
    MLHadith("Zulümden sakının! Çünkü zulüm, kıyamet gününde karanlıklar olacaktır.", "Beware of injustice! For injustice will be darkness on the Day of Judgment.", "Hütet euch vor Ungerechtigkeit! Denn Ungerechtigkeit wird am Tag des Gerichts Finsternis sein.", "اتقوا الظلم فإن الظلم ظلمات يوم القيامة", "Sahih-i Müslim", "Sahih Muslim", "صحيح مسلم", "2578"),
    MLHadith("En hayırlınız, Kur'ân'ı öğrenen ve öğretendir.", "The best of you are those who learn the Quran and teach it.", "Die Besten unter euch sind diejenigen, die den Quran lernen und lehren.", "خيركم من تعلم القرآن وعلمه", "Sahih-i Buhari", "Sahih al-Bukhari", "صحيح البخاري", "5027"),
    MLHadith("Mümin, bir delikten iki kere sokulmaz.", "A believer is not stung from the same hole twice.", "Ein Gläubiger wird nicht zweimal aus demselben Loch gestochen.", "لا يلدغ المؤمن من جحر واحد مرتين", "Sahih-i Buhari", "Sahih al-Bukhari", "صحيح البخاري", "6133"),
    MLHadith("Bir kulun Allah'ın rızasını kazanmak için söylediği güzel bir söz, onu cennetteki yüksek derecelere ulaştırır.", "A good word spoken to please Allah can elevate a person to the highest ranks in Paradise.", "Ein gutes Wort, das gesprochen wird, um Allah zu gefallen, kann einen Menschen zu den höchsten Rängen im Paradies erheben.", "إن العبد ليتكلم بالكلمة من رضوان الله لا يلقي لها بالا يرفعه الله بها درجات", "Sahih-i Buhari", "Sahih al-Bukhari", "صحيح البخاري", "6478"),
    MLHadith("Kim bir müminin dünya sıkıntılarından birini giderirse, Allah da onun kıyamet sıkıntılarından birini giderir.", "Whoever relieves a believer of a worldly hardship, Allah will relieve him of a hardship on the Day of Judgment.", "Wer einem Gläubigen eine weltliche Not lindert, dem wird Allah eine Not am Tag des Gerichts lindern.", "من نفس عن مؤمن كربة من كرب الدنيا نفس الله عنه كربة من كرب يوم القيامة", "Sahih-i Müslim", "Sahih Muslim", "صحيح مسلم", "2699"),
    MLHadith("Allah'ım! Senden hidayet, takva, iffet ve gönül zenginliği isterim.", "O Allah! I ask You for guidance, piety, chastity, and contentment.", "O Allah! Ich bitte Dich um Rechtleitung, Frömmigkeit, Keuschheit und Zufriedenheit.", "اللهم إني أسألك الهدى والتقى والعفاف والغنى", "Sahih-i Müslim", "Sahih Muslim", "صحيح مسلم", "2721"),
    MLHadith("Her iyilik sadakadır.", "Every act of goodness is charity.", "Jede gute Tat ist eine milde Gabe.", "كل معروف صدقة", "Sahih-i Buhari", "Sahih al-Bukhari", "صحيح البخاري", "6021"),
    MLHadith("Bir ağaç dikin; ondan bir insan, kuş ya da hayvan yerse, bu sizin için sadaka olur.", "Plant a tree; if any person, bird, or animal eats from it, it becomes charity for you.", "Pflanzt einen Baum; wenn ein Mensch, Vogel oder Tier davon isst, ist es eine milde Gabe für euch.", "ما من مسلم يغرس غرسا فيأكل منه إنسان أو طير أو دابة إلا كان له صدقة", "Sahih-i Buhari", "Sahih al-Bukhari", "صحيح البخاري", "2320"),
    MLHadith("İki nimet vardır ki insanların çoğu bunlarda aldanmıştır: Sağlık ve boş vakit.", "There are two blessings which many people waste: health and free time.", "Es gibt zwei Segnungen, die viele Menschen verschwenden: Gesundheit und freie Zeit.", "نعمتان مغبون فيهما كثير من الناس الصحة والفراغ", "Sahih-i Buhari", "Sahih al-Bukhari", "صحيح البخاري", "6412"),
    MLHadith("Cennete ancak merhametli olanlar girer.", "Only the merciful will enter Paradise.", "Nur die Barmherzigen werden das Paradies betreten.", "لا يدخل الجنة إلا رحيم", "Tirmizi", "Tirmidhi", "الترمذي", "1924"),
    MLHadith("Utanmadıktan sonra dilediğini yap.", "If you have no shame, then do as you wish.", "Wenn du dich nicht schämst, dann tue was du willst.", "إذا لم تستح فاصنع ما شئت", "Sahih-i Buhari", "Sahih al-Bukhari", "صحيح البخاري", "3484"),
    MLHadith("İnsanlara teşekkür etmeyen, Allah'a da şükretmez.", "He who does not thank people does not thank Allah.", "Wer den Menschen nicht dankt, dankt Allah nicht.", "من لا يشكر الناس لا يشكر الله", "Tirmizi", "Tirmidhi", "الترمذي", "1954"),
    MLHadith("Sizin en güzel ahlaklı olanınız, en hayırlınızdır.", "The best of you are those with the best character.", "Die Besten unter euch sind diejenigen mit dem besten Charakter.", "إن من خياركم أحسنكم أخلاقا", "Sahih-i Buhari", "Sahih al-Bukhari", "صحيح البخاري", "3559"),
    MLHadith("Oruç kalkandır. Oruçlu kimse kötü söz söylemesin ve cahillik etmesin.", "Fasting is a shield. The fasting person should not speak ill or behave ignorantly.", "Das Fasten ist ein Schutzschild. Der Fastende soll nicht schlecht reden und nicht unwissend handeln.", "الصيام جنة فلا يرفث ولا يجهل", "Sahih-i Buhari", "Sahih al-Bukhari", "صحيح البخاري", "1894"),
    MLHadith("Kim Allah için tevazu gösterirse, Allah onu yükseltir.", "Whoever humbles himself for the sake of Allah, Allah will raise him.", "Wer sich um Allahs willen demütigt, den wird Allah erhöhen.", "من تواضع لله رفعه الله", "Sahih-i Müslim", "Sahih Muslim", "صحيح مسلم", "2588"),
    MLHadith("Yolda eziyet veren bir şeyi kaldırmak da sadakadır.", "Removing a harmful thing from the road is an act of charity.", "Etwas Schädliches vom Weg zu entfernen ist eine milde Gabe.", "إماطة الأذى عن الطريق صدقة", "Sahih-i Buhari", "Sahih al-Bukhari", "صحيح البخاري", "2989"),
    MLHadith("Bir Müslüman bir ağaç diker de ondan insan, hayvan veya kuş yerse, bu onun için kıyamete kadar sadaka olur.", "If a Muslim plants a tree and any person, animal, or bird eats from it, it will be counted as charity for him until the Day of Judgment.", "Wenn ein Muslim einen Baum pflanzt und ein Mensch, Tier oder Vogel davon isst, gilt es als milde Gabe bis zum Tag des Gerichts.", "ما من مسلم يغرس غرسا إلا كان ما أكل منه له صدقة إلى يوم القيامة", "Sahih-i Müslim", "Sahih Muslim", "صحيح مسلم", "1553")
)

private fun fetchRandomHadith(lang: String): StoryItem? {
    val h = mlHadiths.random()
    val text = when (lang) { "en" -> h.en; "de" -> h.de; "ar" -> h.ar; else -> h.tr }
    val source = when (lang) { "ar" -> h.sourceAr; "tr" -> h.sourceTr; else -> h.sourceEn }
    val detail = if (h.detailNo.isEmpty()) "" else when (lang) {
        "en" -> "Hadith ${h.detailNo}"; "de" -> "Hadith ${h.detailNo}"; "ar" -> "حديث رقم ${h.detailNo}"; else -> "Hadis No: ${h.detailNo}"
    }
    return StoryItem(text, source, detail)
}

/** Curated Sufi/Islamic wisdom quotes - multilingual */
private data class MLQuote(
    val tr: String, val en: String, val de: String, val ar: String,
    val sourceTr: String, val sourceEn: String, val sourceAr: String,
    val detailTr: String = "", val detailEn: String = "", val detailDe: String = "", val detailAr: String = ""
)

private val mlQuotes = listOf(
    MLQuote("Bir mum diğer mumu tutuşturmakla ışığından bir şey kaybetmez.", "A candle loses nothing by lighting another candle.", "Eine Kerze verliert nichts, wenn sie eine andere Kerze anzündet.", "لا تخسر الشمعة شيئاً من نورها إذا أضاءت شمعة أخرى", "Hz. Mevlana", "Rumi", "مولانا الرومي"),
    MLQuote("Dünle beraber gitti cancağızım, ne kadar söz varsa düne ait. Şimdi yeni şeyler söylemek lazım.", "Yesterday is gone, my dear. All words that belong to yesterday are gone. Now it is time to say new things.", "Das Gestern ist vergangen, mein Lieber. Alle Worte, die dem Gestern gehören, sind vorbei. Jetzt ist es Zeit, Neues zu sagen.", "ذهب الأمس يا حبيبي، كل ما قيل كان للأمس. الآن حان وقت الكلام الجديد", "Hz. Mevlana", "Rumi", "مولانا الرومي"),
    MLQuote("Yaratılanı severiz, Yaradan'dan ötürü.", "We love the created, for the sake of the Creator.", "Wir lieben die Geschöpfe, um des Schöpfers willen.", "نحب المخلوق من أجل الخالق", "Yunus Emre", "Yunus Emre", "يونس إمره"),
    MLQuote("İlim ilim bilmektir, ilim kendin bilmektir. Sen kendini bilmezsin, ya nice okumaktır.", "Knowledge is to know knowledge. Knowledge is to know yourself. If you do not know yourself, what good is all that reading?", "Wissen heißt, das Wissen zu kennen. Wissen heißt, sich selbst zu kennen. Wenn du dich nicht kennst, wozu dann all das Lesen?", "العلم أن تعرف العلم، والعلم أن تعرف نفسك. إن لم تعرف نفسك فما فائدة كل تلك القراءة؟", "Yunus Emre", "Yunus Emre", "يونس إمره"),
    MLQuote("Sevelim sevilelim, dünya kimseye kalmaz.", "Let us love and be loved, for this world remains for no one.", "Lasst uns lieben und geliebt werden, denn diese Welt bleibt für niemanden.", "لنُحبّ ونُحَبّ، فالدنيا لا تبقى لأحد", "Yunus Emre", "Yunus Emre", "يونس إمره"),
    MLQuote("Ben gelmedim dâvi için, benim işim sevi için. Dostun evi gönüllerdir, gönüller yapmaya geldim.", "I did not come for conflict; my work is about love. The home of the Friend is in hearts; I came to mend hearts.", "Ich kam nicht für Streit; meine Aufgabe ist die Liebe. Die Heimat des Freundes sind die Herzen; ich kam, um Herzen zu heilen.", "ما جئت للخصام، شغلي هو الحب. بيت الحبيب في القلوب، جئت لأصلح القلوب", "Yunus Emre", "Yunus Emre", "يونس إمره"),
    MLQuote("Söz ola kese savaşı, söz ola kestire başı. Söz ola ağulu aşı, yağ ile bal ede bir söz.", "A word can end a war, a word can cost a head. A word can turn poison into honey and butter.", "Ein Wort kann Kriege beenden, ein Wort kann Köpfe kosten. Ein Wort kann Gift in Honig und Butter verwandeln.", "كلمة قد تنهي حرباً، وكلمة قد تقطع رأساً. وكلمة قد تحوّل السمّ عسلاً وزبداً", "Yunus Emre", "Yunus Emre", "يونس إمره"),
    MLQuote("Dış görünüşe aldanma. Denizler bile uzaktan masmavi görünür.", "Do not be deceived by appearances. Even the seas look perfectly blue from afar.", "Lass dich nicht vom Äußeren täuschen. Selbst die Meere sehen aus der Ferne tiefblau aus.", "لا تنخدع بالمظاهر، فحتى البحار تبدو زرقاء صافية من بعيد", "Hz. Mevlana", "Rumi", "مولانا الرومي"),
    MLQuote("Sabır acıdır, fakat meyvesi tatlıdır.", "Patience is bitter, but its fruit is sweet.", "Geduld ist bitter, aber ihre Frucht ist süß.", "الصبر مرّ ولكن ثمرته حلوة", "Hz. Ali (r.a.)", "Ali ibn Abi Talib", "علي بن أبي طالب رضي الله عنه"),
    MLQuote("İnsanların en hayırlısı, insanlara faydalı olandır.", "The best of people are those who are most beneficial to others.", "Die besten Menschen sind diejenigen, die anderen am meisten nützen.", "خير الناس أنفعهم للناس", "Hz. Muhammed (s.a.v.)", "Prophet Muhammad (PBUH)", "النبي محمد ﷺ", "Hadis-i Şerif", "Hadith", "Hadith", "حديث شريف"),
    MLQuote("Bir saat tefekkür, bir yıl nafile ibadetten hayırlıdır.", "An hour of reflection is better than a year of voluntary worship.", "Eine Stunde des Nachdenkens ist besser als ein Jahr freiwilliger Anbetung.", "تفكّر ساعة خير من عبادة سنة", "İmam Gazali", "Imam Al-Ghazali", "الإمام الغزالي", "İhyâu Ulûmi'd-Dîn", "Ihya Ulum al-Din", "Ihya Ulum al-Din", "إحياء علوم الدين"),
    MLQuote("Kalp kırıklığı, bütün ibadetlerden ağırdır.", "A broken heart weighs more than all acts of worship.", "Ein gebrochenes Herz wiegt schwerer als alle Gottesdienste.", "انكسار القلب أثقل من جميع العبادات", "İmam Gazali", "Imam Al-Ghazali", "الإمام الغزالي"),
    MLQuote("Nefsin bilmek, Rabbini bilmenin anahtarıdır.", "Knowing yourself is the key to knowing your Lord.", "Sich selbst zu kennen ist der Schlüssel, seinen Herrn zu kennen.", "معرفة النفس مفتاح معرفة الرب", "İmam Gazali", "Imam Al-Ghazali", "الإمام الغزالي"),
    MLQuote("Gül, dikensiz olmaz. Ama dikenler arasında da güller açar.", "There is no rose without thorns. But among thorns, roses bloom.", "Es gibt keine Rose ohne Dornen. Aber zwischen Dornen erblühen Rosen.", "لا وردة بلا أشواك، ولكن بين الأشواك تتفتح الورود", "Hz. Mevlana", "Rumi", "مولانا الرومي"),
    MLQuote("Sen ne kadar bilirsen bil, anlatabildiklerin karşındakinin anlayabildiği kadardır.", "No matter how much you know, what you can convey is only as much as the other can understand.", "Egal wie viel du weißt, was du vermitteln kannst, ist nur so viel, wie der andere verstehen kann.", "مهما علمت، فإن ما تستطيع إيصاله هو بقدر ما يستطيع الآخر فهمه", "Hz. Mevlana", "Rumi", "مولانا الرومي"),
    MLQuote("Toprak ol ki gül bitiresin. Çünkü topraktan başka bir şeyden gül bitmez.", "Be like earth so that you may grow roses. For roses grow from nothing but earth.", "Sei wie Erde, damit du Rosen wachsen lässt. Denn Rosen wachsen aus nichts anderem als Erde.", "كن كالتراب لتنبت الورود، فالورود لا تنبت إلا من التراب", "Hz. Mevlana", "Rumi", "مولانا الرومي", "Mesnevi", "Masnavi", "Masnavi", "المثنوي"),
    MLQuote("İlim mecliste değil, yüreklerdedir.", "Knowledge is not in gatherings, but in hearts.", "Wissen ist nicht in Versammlungen, sondern in den Herzen.", "العلم ليس في المجالس بل في القلوب", "Şems-i Tebrizi", "Shams Tabrizi", "شمس التبريزي"),
    MLQuote("Gurur, insanı yalnızlaştıran en ağır yüktür.", "Pride is the heaviest burden that isolates a person.", "Stolz ist die schwerste Last, die einen Menschen vereinsamt.", "الكبرياء أثقل حمل يُوحّش الإنسان", "Şems-i Tebrizi", "Shams Tabrizi", "شمس التبريزي"),
    MLQuote("Gönlün ne denli geniş olursa, o denli çok insan sığar içine.", "The wider your heart, the more people it can hold.", "Je weiter dein Herz ist, desto mehr Menschen finden darin Platz.", "كلما اتسع قلبك، سَعِ فيه أناس أكثر", "Şems-i Tebrizi", "Shams Tabrizi", "شمس التبريزي"),
    MLQuote("Kibirden uzak dur, çünkü kibir cehennemin kapısıdır.", "Stay away from arrogance, for arrogance is the gate to hellfire.", "Halte dich fern vom Hochmut, denn Hochmut ist das Tor zur Hölle.", "ابتعد عن الكبر فإنه باب جهنم", "Hz. Ali (r.a.)", "Ali ibn Abi Talib", "علي بن أبي طالب رضي الله عنه"),
    MLQuote("İnsanların en âcizi dua etmeyen, en cimrisi selam vermeyendir.", "The most helpless of people is the one who does not pray, and the stingiest is the one who does not give greetings.", "Der hilfloseste Mensch ist der, der nicht betet, und der geizigste ist der, der nicht grüßt.", "أعجز الناس من لا يدعو، وأبخل الناس من لا يسلّم", "Hz. Ali (r.a.)", "Ali ibn Abi Talib", "علي بن أبي طالب رضي الله عنه"),
    MLQuote("İyilik yap denize at, balık bilmezse Hâlık bilir.", "Do good and cast it into the sea; if the fish does not know, the Creator knows.", "Tue Gutes und wirf es ins Meer; wenn der Fisch es nicht weiß, weiß es der Schöpfer.", "افعل الخير وألقه في البحر، إن لم يعرفه السمك يعرفه الخالق", "Atasözü", "Proverb", "مثل"),
    MLQuote("Bir kimsenin değeri, himmetinin büyüklüğü kadardır.", "A person's worth is measured by the greatness of their aspiration.", "Der Wert eines Menschen bemisst sich an der Größe seines Strebens.", "قيمة المرء بقدر همّته", "Hz. Ali (r.a.)", "Ali ibn Abi Talib", "علي بن أبي طالب رضي الله عنه"),
    MLQuote("Dünyada iki günün var: Biri lehine, biri aleyhine. Lehine olduğu gün şımarma, aleyhine olduğu gün sabret.", "You have two days in this world: one for you and one against you. When it is for you, do not be arrogant. When it is against you, be patient.", "Du hast zwei Tage auf dieser Welt: einen für dich und einen gegen dich. Wenn er für dich ist, sei nicht übermütig. Wenn er gegen dich ist, sei geduldig.", "لك في الدنيا يومان: يوم لك ويوم عليك. فإذا كان لك فلا تبطر، وإذا كان عليك فاصبر", "Hz. Ali (r.a.)", "Ali ibn Abi Talib", "علي بن أبي طالب رضي الله عنه"),
    MLQuote("Güzel ahlâk, sahibinin tacıdır.", "Good character is the crown of its owner.", "Guter Charakter ist die Krone seines Besitzers.", "حسن الخلق تاج صاحبه", "Hz. Ali (r.a.)", "Ali ibn Abi Talib", "علي بن أبي طالب رضي الله عنه"),
    MLQuote("Nefsini bilen Rabbini bilir.", "He who knows himself knows his Lord.", "Wer sich selbst kennt, kennt seinen Herrn.", "من عرف نفسه فقد عرف ربه", "Hz. Muhammed (s.a.v.)", "Prophet Muhammad (PBUH)", "النبي محمد ﷺ"),
    MLQuote("Her gece doğar bir sabah, her kıştan sonra gelir bir bahar.", "Every night gives birth to a morning, after every winter comes a spring.", "Jede Nacht gebiert einen Morgen, nach jedem Winter kommt ein Frühling.", "كل ليلة يولد صباح، وبعد كل شتاء يأتي ربيع", "Hz. Mevlana", "Rumi", "مولانا الرومي"),
    MLQuote("Aşk gelicek cümle eksikler biter.", "When love arrives, all imperfections come to an end.", "Wenn die Liebe kommt, enden alle Unvollkommenheiten.", "إذا جاء العشق انتهت كل النقائص", "Yunus Emre", "Yunus Emre", "يونس إمره"),
    MLQuote("Cahillikten kötü fakirlik, akıldan iyi mal, kibirden ağır yalnızlık yoktur.", "There is no poverty worse than ignorance, no wealth better than wisdom, and no loneliness heavier than arrogance.", "Es gibt keine schlimmere Armut als Unwissenheit, keinen besseren Reichtum als Weisheit und keine schwerere Einsamkeit als Hochmut.", "لا فقر أشد من الجهل، ولا مال أفضل من العقل، ولا وحشة أثقل من الكبر", "Hz. Ali (r.a.)", "Ali ibn Abi Talib", "علي بن أبي طالب رضي الله عنه"),
    MLQuote("Kendini büyük gören küçüktür, küçük gören büyüktür.", "He who sees himself as great is small; he who sees himself as small is great.", "Wer sich für groß hält, ist klein; wer sich für klein hält, ist groß.", "من رأى نفسه كبيراً فهو صغير، ومن رأى نفسه صغيراً فهو كبير", "İmam Gazali", "Imam Al-Ghazali", "الإمام الغزالي"),
    MLQuote("İnsanın süsü akıl, aklın süsü ilim, ilmin süsü doğruluktur.", "The adornment of a person is wisdom, the adornment of wisdom is knowledge, and the adornment of knowledge is truthfulness.", "Die Zierde des Menschen ist der Verstand, die Zierde des Verstandes ist das Wissen, und die Zierde des Wissens ist die Aufrichtigkeit.", "زينة الإنسان العقل، وزينة العقل العلم، وزينة العلم الصدق", "Hz. Ali (r.a.)", "Ali ibn Abi Talib", "علي بن أبي طالب رضي الله عنه"),
    MLQuote("Kötülük yapan kendine yapar; sen iyilik yap ve unut.", "He who does evil harms himself; do good and forget about it.", "Wer Böses tut, schadet sich selbst; tue Gutes und vergiss es.", "من يفعل الشر يضر نفسه، أنت افعل الخير وانسَه", "Hz. Mevlana", "Rumi", "مولانا الرومي"),
    MLQuote("Aç kalmışlara yemek vermek, namaz kılmaktan ve oruç tutmaktan daha faziletlidir.", "Feeding the hungry is more virtuous than prayer and fasting.", "Die Hungrigen zu speisen ist verdienstvoller als Gebet und Fasten.", "إطعام الجائع أفضل من الصلاة والصيام", "İmam Gazali", "Imam Al-Ghazali", "الإمام الغزالي"),
    MLQuote("Yara almadan olgunlaşan hiçbir gönül yoktur.", "No heart matures without being wounded.", "Kein Herz reift, ohne verwundet zu werden.", "ما من قلب ينضج دون أن يُجرح", "Hz. Mevlana", "Rumi", "مولانا الرومي", "Mesnevi", "Masnavi", "Masnavi", "المثنوي")
)

private fun fetchRandomQuote(lang: String): StoryItem? {
    val q = mlQuotes.random()
    val text = when (lang) { "en" -> q.en; "de" -> q.de; "ar" -> q.ar; else -> q.tr }
    val source = when (lang) { "ar" -> q.sourceAr; "tr" -> q.sourceTr; else -> q.sourceEn }
    val detail = when (lang) {
        "en" -> q.detailEn; "de" -> q.detailDe; "ar" -> q.detailAr; else -> q.detailTr
    }
    return StoryItem(text, source, detail)
}
