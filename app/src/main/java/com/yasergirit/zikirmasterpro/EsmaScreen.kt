package com.yasergirit.zikirmasterpro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// ── Data Model ──

internal data class EsmaName(
    val number: Int,
    val arabic: String,
    val transliteration: String,
    val meaningTr: String,
    val meaningEn: String,
    val meaningDe: String,
    val meaningAr: String,
    val detailTr: String = "",
    val detailEn: String = ""
)

// ── Pastel Colors ──

private val pastelColors = listOf(
    Color(0xFFFFE0D0), // Seftali
    Color(0xFFD0E8FF), // Mavi
    Color(0xFFFFD0E0), // Pembe
    Color(0xFFD0F0D0), // Yesil
    Color(0xFFE8D0FF), // Mor
    Color(0xFFFFF0D0), // Sari
    Color(0xFFD0F0F0), // Turkuaz
    Color(0xFFEDE0D0), // Bej
    Color(0xFFE0D8F0), // Lila
    Color(0xFFD0F0E8)  // Mint
)

private fun getCardColor(number: Int): Color = pastelColors[(number - 1) % pastelColors.size]

// ── Local Data ──

private val localEsmaNames = listOf(
    EsmaName(1, "\u0627\u0644\u0631\u064E\u0651\u062D\u0652\u0645\u064E\u0646\u064F", "Ar-Rahman", "Esirgeyen", "The Beneficent", "Der Allerbarmer", "\u0627\u0644\u0631\u062D\u0645\u0646",
        "Rahman, Allah'\u0131n s\u0131n\u0131rs\u0131z rahmetini ifade eden isimlerinden biridir. Bu isim, Allah'\u0131n b\u00fct\u00fcn yarat\u0131lm\u0131\u015flara kar\u015f\u0131 olan genel rahmetini anlat\u0131r. M\u00fcmin olsun, k\u00e2fir olsun, iyi olsun, k\u00f6t\u00fc olsun b\u00fct\u00fcn varl\u0131klara r\u0131z\u0131k verir, ya\u015fat\u0131r ve nimetlendirir.\n\nRahman ismi, d\u00fcnyada b\u00fct\u00fcn canl\u0131lara merhamet eden, onlar\u0131 besleyen ve koruyan anlam\u0131na gelir. G\u00fcne\u015f, hava, su gibi nimetler Rahman isminin tecellisidir.",
        "Ar-Rahman refers to Allah's all-encompassing mercy that extends to all creation without distinction. It signifies the One who bestows blessings upon all beings."),
    EsmaName(2, "\u0627\u0644\u0631\u064E\u0651\u062D\u0650\u064A\u0645\u064F", "Ar-Rahim", "Ba\u011f\u0131\u015flayan", "The Merciful", "Der Barmherzige", "\u0627\u0644\u0631\u062D\u064A\u0645",
        "Rah\u00eem, Allah'\u0131n \u00f6zellikle m\u00fcminlere y\u00f6nelik \u00f6zel rahmetini ifade eder. Rahman genel rahmeti kapsarken, Rah\u00eem ahirette m\u00fcminlere mahsus olan rahmeti anlat\u0131r.\n\nAllah, Rah\u00eem ismiyle kullar\u0131n\u0131n tevbelerini kabul eder, g\u00fcnahlar\u0131n\u0131 ba\u011f\u0131\u015flar ve onlara \u00f6zel l\u00fctufta bulunur. Bu isim, \u00fcmit ve ba\u011f\u0131\u015flanma kap\u0131s\u0131n\u0131n her zaman a\u00e7\u0131k oldu\u011funu hat\u0131rlat\u0131r.",
        "Ar-Rahim refers to Allah's special mercy reserved for the believers. While Ar-Rahman encompasses all creation, Ar-Rahim is specific to those who have faith."),
    EsmaName(3, "\u0627\u0644\u0652\u0645\u064E\u0644\u0650\u0643\u064F", "Al-Malik", "Mutlak H\u00fck\u00fcmdar", "The Sovereign", "Der Herrscher", "\u0627\u0644\u0645\u0644\u0643",
        "Melik, b\u00fct\u00fcn kainat\u0131n ger\u00e7ek sahibi ve h\u00fck\u00fcmdar\u0131 demektir. G\u00f6klerin ve yerin, g\u00f6r\u00fcnen ve g\u00f6r\u00fcnmeyen her \u015feyin mutlak h\u00fck\u00fcmdar\u0131 yaln\u0131zca Allah't\u0131r.\n\nHi\u00e7bir \u015fey O'nun m\u00fclk\u00fcnden d\u0131\u015far\u0131da de\u011fildir. B\u00fct\u00fcn varl\u0131klar O'nun m\u00fclk\u00fcnde ve O'nun tasarrufu alt\u0131ndad\u0131r.",
        "Al-Malik signifies the absolute Sovereign and King of all existence, the true Owner of everything in the heavens and the earth."),
    EsmaName(4, "\u0627\u0644\u0652\u0642\u064F\u062F\u064F\u0651\u0648\u0633\u064F", "Al-Quddus", "Tertemiz", "The Holy", "Der Heilige", "\u0627\u0644\u0642\u062F\u0648\u0633",
        "Kudd\u00fcs, her t\u00fcrl\u00fc eksiklikten, kusurdan, ayb\u0131ptan ve n\u00f6ksanl\u0131ktan uzak olan demektir. Allah, yarat\u0131lm\u0131\u015flara ait b\u00fct\u00fcn s\u0131fatlardan m\u00fcnezzehtir.\n\nO'nun zat\u0131, s\u0131fatlar\u0131 ve fiilleri her t\u00fcrl\u00fc kusurdan ar\u0131nm\u0131\u015ft\u0131r. Kudd\u00fcs ismi, Allah'\u0131n mutlak mukaddesli\u011fini ve y\u00fcceli\u011fini ifade eder.",
        "Al-Quddus means the One who is free from all imperfections, deficiencies, and shortcomings. He is absolutely pure and sacred."),
    EsmaName(5, "\u0627\u0644\u0633\u064E\u0651\u0644\u064E\u0627\u0645\u064F", "As-Salam", "Esenlik Veren", "The Source of Peace", "Der Friede", "\u0627\u0644\u0633\u0644\u0627\u0645",
        "Sel\u00e2m, esenlik, bar\u0131\u015f ve mutlulu\u011fun kayna\u011f\u0131 anlam\u0131ndad\u0131r. Hastal\u0131ktan, beladan, ay\u0131ptan ve kusurdan ar\u0131nm\u0131\u015f ve her t\u00fcrl\u00fc eksiklikten salim oldu\u011fu gibi, kullar\u0131n\u0131 da her t\u00fcrl\u00fc tehlikeden koruyand\u0131r.\n\nHer selametin kayna\u011f\u0131, kendisi ay\u0131ptan, kusurdan, eksiklikten, yokluktan k\u0131sacas\u0131 her tehlikeden s\u00e2lim oldu\u011fu gibi, selamet umulan, selamet arayanlar\u0131 selamete erdirecek olan da O'dur.",
        "As-Salam is the Source of Peace and Safety, free from all defects. He grants peace and security to His creation."),
    EsmaName(6, "\u0627\u0644\u0652\u0645\u064F\u0624\u0652\u0645\u0650\u0646\u064F", "Al-Mu'min", "G\u00fcven Veren", "The Guardian of Faith", "Der Glaubensbewahrer", "\u0627\u0644\u0645\u0624\u0645\u0646",
        "M\u00fc'min, kullar\u0131na g\u00fcven veren, onlar\u0131 korkulardan emin k\u0131lan demektir. Allah, vaadinde sadakt\u0131r ve kullar\u0131n\u0131 z\u00fclm\u00fcnden emin k\u0131lar.\n\nO, peygamberlerini mucizelerle tasdik eden, m\u00fcminleri iman nuruyla ayd\u0131nlatan ve onlara g\u00fcvenlik hissi verendir.",
        "Al-Mu'min is the One who grants security and confirms the faith of His servants. He fulfills His promises and protects from injustice."),
    EsmaName(7, "\u0627\u0644\u0652\u0645\u064F\u0647\u064E\u064A\u0652\u0645\u0650\u0646\u064F", "Al-Muhaymin", "G\u00f6zetip Koruyan", "The Protector", "Der Besch\u00fctzer", "\u0627\u0644\u0645\u0647\u064A\u0645\u0646",
        "M\u00fcheymin, her \u015feyi g\u00f6zetip kontrol eden, koruyan ve kollayan demektir. Allah, b\u00fct\u00fcn varl\u0131klar\u0131 g\u00f6zetimi alt\u0131nda tutar.\n\nHi\u00e7bir \u015fey O'nun bilgisi ve kontrolu d\u0131\u015f\u0131nda de\u011fildir. Her an her yerde haz\u0131r ve naz\u0131r olarak kullar\u0131n\u0131 g\u00f6zetir.",
        "Al-Muhaymin is the Guardian who watches over all creation, preserving and protecting everything under His care."),
    EsmaName(8, "\u0627\u0644\u0652\u0639\u064E\u0632\u0650\u064A\u0632\u064F", "Al-Aziz", "G\u00fc\u00e7l\u00fc ve Galip", "The Almighty", "Der Allm\u00e4chtige", "\u0627\u0644\u0639\u0632\u064A\u0632",
        "Az\u00eez, mutlak g\u00fc\u00e7 ve galebe sahibi demektir. Hi\u00e7bir kuvvet O'na kar\u015f\u0131 gelemez, hi\u00e7bir g\u00fc\u00e7 O'nu yenemez.\n\nAz\u00eez ismi, Allah'\u0131n yenilmez g\u00fcc\u00fcn\u00fc, e\u015fsiz izzetini ve mutlak \u00fcst\u00fcnl\u00fc\u011f\u00fcn\u00fc ifade eder. O, g\u00fcc\u00fc her \u015feye yeten, izzetiyle her \u015feye galip olan tek varl\u0131kt\u0131r.",
        "Al-Aziz signifies the Almighty, the One who possesses absolute power and honor that none can overcome or resist."),
    EsmaName(9, "\u0627\u0644\u0652\u062C\u064E\u0628\u064E\u0651\u0627\u0631\u064F", "Al-Jabbar", "D\u00fczeltip Yoluna Koyan", "The Compeller", "Der Unterwerfer", "\u0627\u0644\u062C\u0628\u0627\u0631",
        "Cebbar, k\u0131r\u0131klar\u0131 onaran, eksiklikleri tamamlayan ve iradesini her \u015feye ge\u00e7iren demektir. Allah, k\u0131r\u0131k kalpleri tamir eder, bozuk d\u00fczenleri d\u00fczeltir.\n\nAyn\u0131 zamanda O'nun emrine hi\u00e7bir \u015fey kar\u015f\u0131 koyamaz. Diledi\u011fini yapmaya muktedirdir ve hi\u00e7bir g\u00fc\u00e7 O'nun iradesine engel olamaz.",
        "Al-Jabbar is the Compeller who mends what is broken, completes what is lacking, and whose will is irresistible."),
    EsmaName(10, "\u0627\u0644\u0652\u0645\u064F\u062A\u064E\u0643\u064E\u0628\u064F\u0651\u0631\u064F", "Al-Mutakabbir", "B\u00fcy\u00fckl\u00fckte E\u015fsiz", "The Majestic", "Der Erhabene", "\u0627\u0644\u0645\u062A\u0643\u0628\u0631",
        "M\u00fctekebbir, b\u00fcy\u00fckl\u00fck ve ululukta e\u015fi benzeri olmayan demektir. Kibriya yaln\u0131zca Allah'a mahsustur.\n\nO, her t\u00fcrl\u00fc b\u00fcy\u00fckl\u00fck ve azametin tek sahibidir. Yarat\u0131lm\u0131\u015flar\u0131n O'na nispetle b\u00fcy\u00fckl\u00fck taslamalar\u0131 m\u00fcmk\u00fcn de\u011fildir.",
        "Al-Mutakabbir is the Supreme in Greatness, the One whose greatness is beyond comparison."),
    EsmaName(11, "\u0627\u0644\u0652\u062E\u064E\u0627\u0644\u0650\u0642\u064F", "Al-Khaliq", "Yaratan", "The Creator", "Der Sch\u00f6pfer", "\u0627\u0644\u062E\u0627\u0644\u0642",
        "H\u00e2l\u0131k, yoktan var eden, her \u015feyi takdir edip yaratan demektir. Allah, kainattaki her \u015feyi en m\u00fckemmel \u015fekilde yokluktan varl\u0131\u011fa \u00e7\u0131karm\u0131\u015ft\u0131r.\n\nO, yaratmay\u0131 planlayan, \u00f6l\u00e7\u00fc ve bi\u00e7im belirleyen tek varl\u0131kt\u0131r. Her yarat\u0131l\u0131\u015f O'nun sonsuz ilim ve kudretinin eseridir.",
        "Al-Khaliq is the Creator who brings everything into existence from nothingness with perfect planning and design."),
    EsmaName(12, "\u0627\u0644\u0652\u0628\u064E\u0627\u0631\u0650\u0626\u064F", "Al-Bari", "Kusursuz Yaratan", "The Evolver", "Der Erschaffer", "\u0627\u0644\u0628\u0627\u0631\u0626",
        "B\u00e2r\u00ee, her \u015feyi kusursuz ve birbirinden farkl\u0131 olarak yaratan demektir. Allah, her varl\u0131\u011f\u0131 birbirinden farkl\u0131, e\u015fsiz ve m\u00fckemmel bir \u015fekilde meydana getirmi\u015ftir.\n\nMilyarlarca insan\u0131 birbirinden farkl\u0131 yaratmas\u0131, her canl\u0131ya ayr\u0131 \u00f6zellikler vermesi B\u00e2r\u00ee isminin tecellisidir.",
        "Al-Bari is the Maker who creates all things distinct and flawless, giving each creation its unique characteristics."),
    EsmaName(13, "\u0627\u0644\u0652\u0645\u064F\u0635\u064E\u0648\u064F\u0651\u0631\u064F", "Al-Musawwir", "\u015eekil Veren", "The Fashioner", "Der Gestaltgeber", "\u0627\u0644\u0645\u0635\u0648\u0631",
        "Musavvir, her varl\u0131\u011fa kendine \u00f6zg\u00fc bir \u015fekil ve suret veren demektir. Allah, her canl\u0131y\u0131 e\u015fsiz bir g\u00f6r\u00fcn\u00fcmle donatm\u0131\u015ft\u0131r.\n\nAnne rahmindeki bebe\u011fe \u015fekil veren, her \u00e7i\u00e7e\u011fe farkl\u0131 renk ve desen kazand\u0131ran O'dur. Her varl\u0131k O'nun sanat\u0131n\u0131n bir \u015faheseridir.",
        "Al-Musawwir is the Fashioner who gives each creation its unique form, shape, and appearance."),
    EsmaName(14, "\u0627\u0644\u0652\u063A\u064E\u0641\u064E\u0651\u0627\u0631\u064F", "Al-Ghaffar", "\u00c7ok Ba\u011f\u0131\u015flayan", "The Forgiver", "Der Allvergebende", "\u0627\u0644\u063A\u0641\u0627\u0631",
        "Gaffar, g\u00fcnahlar\u0131 tekrar tekrar ba\u011f\u0131\u015flayan, kusurlar\u0131 \u00f6rten demektir. Kul ne kadar g\u00fcnah i\u015flerse i\u015flesin, samimi bir tevbeyle d\u00f6nd\u00fc\u011f\u00fcnde Allah onu ba\u011f\u0131\u015flar.\n\nGaffar ismi, Allah'\u0131n rahmetinin gazab\u0131n\u0131 ge\u00e7ti\u011fini m\u00fcjdeler. O, kullar\u0131n\u0131n g\u00fcnahlar\u0131n\u0131 sadece ba\u011f\u0131\u015flamakla kalmaz, ayn\u0131 zamanda \u00f6rter ve gizler.",
        "Al-Ghaffar is the One who forgives repeatedly and conceals the sins of His servants when they turn to Him in repentance."),
    EsmaName(15, "\u0627\u0644\u0652\u0642\u064E\u0647\u064E\u0651\u0627\u0631\u064F", "Al-Qahhar", "Kahredici G\u00fcce Sahip", "The Subduer", "Der Allbezwinger", "\u0627\u0644\u0642\u0647\u0627\u0631",
        "Kahhar, her \u015feye galip gelen, hi\u00e7bir g\u00fcc\u00fcn kar\u015f\u0131s\u0131nda duramayaca\u011f\u0131 kahredici g\u00fce sahip demektir.\n\nZalimleri, zorba ve m\u00fcstekbirleri kahredecek olan O'dur. Hi\u00e7bir g\u00fc\u00e7 O'nun kar\u015f\u0131s\u0131nda direnemez.",
        "Al-Qahhar is the Subduer whose power none can resist, who overcomes all tyrants and oppressors."),
    EsmaName(16, "\u0627\u0644\u0652\u0648\u064E\u0647\u064E\u0651\u0627\u0628\u064F", "Al-Wahhab", "Kar\u015f\u0131l\u0131ks\u0131z Veren", "The Bestower", "Der Schenkende", "\u0627\u0644\u0648\u0647\u0627\u0628",
        "Vehhab, kar\u015f\u0131l\u0131ks\u0131z, bol bol ve s\u00fcrekli olarak nimetler ba\u011f\u0131\u015flayan demektir. Allah'\u0131n vermesi kar\u015f\u0131l\u0131ks\u0131zd\u0131r, hi\u00e7bir beklentisi yoktur.\n\nO, diledi\u011fine diledi\u011fi kadar verir. Vermekle hazinesi t\u00fckenmez, c\u00f6mertli\u011fi sonsuz ve s\u0131n\u0131rs\u0131zd\u0131r.",
        "Al-Wahhab is the Bestower who gives abundantly without any expectation of return. His generosity is boundless."),
    EsmaName(17, "\u0627\u0644\u0631\u064E\u0651\u0632\u064E\u0651\u0627\u0642\u064F", "Ar-Razzaq", "R\u0131z\u0131k Veren", "The Provider", "Der Versorger", "\u0627\u0644\u0631\u0632\u0627\u0642",
        "Rezzak, b\u00fct\u00fcn canl\u0131lar\u0131n r\u0131zk\u0131n\u0131 veren, maddi ve manevi ihtiya\u00e7lar\u0131 kar\u015f\u0131layan demektir. Yery\u00fcz\u00fcndeki b\u00fct\u00fcn canl\u0131lar\u0131n r\u0131zk\u0131 Allah'\u0131n \u00fczerinedir.\n\nKar\u0131nca deli\u011findeki kar\u0131ncadan okyanusun dibindeki bal\u0131\u011fa kadar her canl\u0131ya r\u0131zk\u0131n\u0131 ula\u015ft\u0131ran O'dur.",
        "Ar-Razzaq is the Provider who sustains all creatures with their material and spiritual sustenance."),
    EsmaName(18, "\u0627\u0644\u0652\u0641\u064E\u062A\u064E\u0651\u0627\u062D\u064F", "Al-Fattah", "A\u00e7an, Fetih Veren", "The Opener", "Der Er\u00f6ffner", "\u0627\u0644\u0641\u062A\u0627\u062D",
        "Fettah, her t\u00fcrl\u00fc zorluklar\u0131 a\u00e7an, kapal\u0131 kap\u0131lar\u0131 a\u00e7an, sorunlara \u00e7\u00f6z\u00fcm getiren demektir.\n\nKalpleri iman nuruyla a\u00e7an, r\u0131z\u0131k kap\u0131lar\u0131n\u0131 a\u00e7an ve zorluklar\u0131 kolayla\u015ft\u0131ran O'dur. Her kapal\u0131 kap\u0131 O'nun izniyle a\u00e7\u0131l\u0131r.",
        "Al-Fattah is the Opener who removes all difficulties, opens closed doors, and provides solutions to every problem."),
    EsmaName(19, "\u0627\u0644\u0652\u0639\u064E\u0644\u0650\u064A\u0645\u064F", "Al-Alim", "Her \u015eeyi Bilen", "The All-Knowing", "Der Allwissende", "\u0627\u0644\u0639\u0644\u064A\u0645",
        "Al\u00eem, her \u015feyi en ince ayr\u0131nt\u0131s\u0131na kadar bilen, ilmi sonsuz olan demektir. Ge\u00e7mi\u015fi, \u015fimdiki zaman\u0131 ve gelece\u011fi eksiksiz bilir.\n\nKalplerdeki en gizli d\u00fc\u015f\u00fcnceler, yapraklar\u0131n hareketi, karanliktaki kar\u0131ncan\u0131n y\u00fcr\u00fcy\u00fc\u015f\u00fc bile O'nun bilgisi dahilindedir.",
        "Al-Alim is the All-Knowing whose knowledge encompasses everything past, present, and future without exception."),
    EsmaName(20, "\u0627\u0644\u0652\u0642\u064E\u0627\u0628\u0650\u0636\u064F", "Al-Qabid", "Daralt\u0131c\u0131", "The Constrictor", "Der Zur\u00fccknehmende", "\u0627\u0644\u0642\u0627\u0628\u0636",
        "Kab\u0131d, r\u0131z\u0131klar\u0131 ve kalpleri daraltan, s\u0131kan demektir. Allah, hikmetiyle diledi\u011finin r\u0131zk\u0131n\u0131 daralt\u0131r.\n\nBu isim, bolluk ve darl\u0131\u011f\u0131n Allah'\u0131n elinde oldu\u011funu hat\u0131rlat\u0131r. Darl\u0131k zamanlar\u0131nda sabretmek gerekti\u011fini \u00f6\u011fretir.",
        "Al-Qabid is the One who constricts and withholds sustenance and hearts according to His wisdom."),
    EsmaName(21, "\u0627\u0644\u0652\u0628\u064E\u0627\u0633\u0650\u0637\u064F", "Al-Basit", "Geni\u015fletici", "The Expander", "Der Ausbreitende", "\u0627\u0644\u0628\u0627\u0633\u0637",
        "Bas\u0131t, r\u0131z\u0131klar\u0131 ve kalpleri geni\u015fleten, a\u00e7an demektir. Allah, diledi\u011fine bol r\u0131z\u0131k verir ve kalplere ferah verir.\n\nKab\u0131d ve Bas\u0131t isimleri birlikte d\u00fc\u015f\u00fcn\u00fclmelidir. Darl\u0131k ve geni\u015flik Allah'\u0131n takdiriyledir.",
        "Al-Basit is the Expander who opens hearts and extends sustenance generously to whom He wills."),
    EsmaName(22, "\u0627\u0644\u0652\u062E\u064E\u0627\u0641\u0650\u0636\u064F", "Al-Khafid", "Al\u00e7alt\u0131c\u0131", "The Abaser", "Der Erniedrigende", "\u0627\u0644\u062E\u0627\u0641\u0636",
        "Haf\u0131d, kibirlenenleri al\u00e7altan, zalimleri a\u015fa\u011f\u0131 indiren demektir. Allah, b\u00fcy\u00fcklenenleri k\u00fc\u00e7\u00fclt\u00fcr.\n\nBu isim, tevazunun \u00f6nemini hat\u0131rlat\u0131r. Kibir ve b\u00fcy\u00fcklenme sonunda al\u00e7almaya yol a\u00e7ar.",
        "Al-Khafid is the Abaser who humbles the arrogant and brings down the oppressors."),
    EsmaName(23, "\u0627\u0644\u0631\u064E\u0651\u0627\u0641\u0650\u0639\u064F", "Ar-Rafi", "Y\u00fckseltici", "The Exalter", "Der Erh\u00f6hende", "\u0627\u0644\u0631\u0627\u0641\u0639",
        "Raf\u00ee, diledi\u011fini y\u00fccelten, \u015fereflendiren demektir. Allah, m\u00fctevaz\u0131 kullar\u0131n\u0131 y\u00fcceltir ve onlara \u015feref verir.\n\nManevi y\u00fckselme ancak Allah'\u0131n l\u00fctfuylad\u0131r. \u0130lim, iman ve g\u00fczel ahlak ile y\u00fcceltendir.",
        "Ar-Rafi is the Exalter who elevates and honors whom He wills, raising the humble and the righteous."),
    EsmaName(24, "\u0627\u0644\u0652\u0645\u064F\u0639\u0650\u0632\u064F\u0651", "Al-Mu'izz", "\u0130zzet Veren", "The Honourer", "Der Ehrende", "\u0627\u0644\u0645\u0639\u0632",
        "Muizz, \u015feref ve izzet veren demektir. Ger\u00e7ek izzet ve \u015feref yaln\u0131zca Allah'\u0131n vermesiyledir.\n\nAllah, diledi\u011fi kulunu aziz k\u0131lar, ona itibar ve sayg\u0131nl\u0131k verir. \u0130nsanlardan de\u011fil, Allah'tan izzet istemek gerekir.",
        "Al-Mu'izz is the One who grants honor and dignity to whom He wills. True honor comes only from Allah."),
    EsmaName(25, "\u0627\u0644\u0652\u0645\u064F\u0630\u0650\u0644\u064F\u0651", "Al-Mudhill", "Zillete D\u00fc\u015f\u00fcr\u00fcc\u00fc", "The Humiliator", "Der Dem\u00fctigende", "\u0627\u0644\u0645\u0630\u0644",
        "M\u00fczill, diledi\u011fini zelil k\u0131lan, al\u00e7altan demektir. Allah, haddini a\u015fanlar\u0131, zalimleri ve m\u00fcstekbirleri rezil eder.\n\nBu isim, hakk\u0131 tan\u0131mamakta \u0131srar edenlerin ak\u0131betini hat\u0131rlat\u0131r.",
        "Al-Mudhill is the One who abases and humiliates the arrogant and those who persist in wrongdoing."),
    EsmaName(26, "\u0627\u0644\u0633\u064E\u0651\u0645\u0650\u064A\u0639\u064F", "As-Sami", "Her \u015eeyi \u0130\u015fiten", "The All-Hearing", "Der Allh\u00f6rende", "\u0627\u0644\u0633\u0645\u064A\u0639",
        "Sem\u00ee, her \u015feyi i\u015fiten demektir. En gizli f\u0131s\u0131lt\u0131lardan kalplerin sessiz yakar\u0131\u015flar\u0131na kadar her sesi duyar.\n\nKulun dualar\u0131n\u0131, yakar\u0131\u015flar\u0131n\u0131 ve s\u00f6zlerini i\u015fitir. Hi\u00e7bir ses O'ndan gizli kalmaz.",
        "As-Sami is the All-Hearing who hears every sound, from the faintest whisper to the silent prayers of the heart."),
    EsmaName(27, "\u0627\u0644\u0652\u0628\u064E\u0635\u0650\u064A\u0631\u064F", "Al-Basir", "Her \u015eeyi G\u00f6ren", "The All-Seeing", "Der Allsehende", "\u0627\u0644\u0628\u0635\u064A\u0631",
        "Bas\u00eer, her \u015feyi g\u00f6ren demektir. Karanl\u0131\u011f\u0131n derinliklerindeki en k\u00fc\u00e7\u00fck varl\u0131ktan, kalplerin en gizli hallerine kadar her \u015feyi g\u00f6r\u00fcr.\n\nHi\u00e7bir \u015fey O'nun g\u00f6r\u00fc\u015f\u00fcnden gizlenemez. Kullar\u0131n\u0131n yapt\u0131klar\u0131n\u0131 ve niyetlerini g\u00f6r\u00fcr.",
        "Al-Basir is the All-Seeing who sees everything, from the smallest atom to the deepest secrets of the heart."),
    EsmaName(28, "\u0627\u0644\u0652\u062D\u064E\u0643\u064E\u0645\u064F", "Al-Hakam", "H\u00fckmedici", "The Judge", "Der Richter", "\u0627\u0644\u062D\u0643\u0645",
        "Hakem, mutlak h\u00fck\u00fcm sahibi, en adaletli h\u00fck\u00fcm veren demektir. O'nun h\u00fckm\u00fc kesindir ve asla haks\u0131zl\u0131k i\u00e7ermez.\n\nKullar aras\u0131ndaki anla\u015fmazl\u0131klarda son s\u00f6z\u00fc s\u00f6yleyecek olan O'dur.",
        "Al-Hakam is the absolute Judge whose rulings are final, just, and free from any injustice."),
    EsmaName(29, "\u0627\u0644\u0652\u0639\u064E\u062F\u0652\u0644\u064F", "Al-Adl", "Adaletli", "The Just", "Der Gerechte", "\u0627\u0644\u0639\u062F\u0644",
        "Adl, mutlak adalet sahibi demektir. Allah, hi\u00e7bir kuluna zerre kadar zulmetmez. Her \u015feyi yerli yerine koyar.\n\nO'nun adaleti kusursuzdur. K\u0131yamet g\u00fcn\u00fc herkesin hakk\u0131n\u0131 eksiksiz verecektir.",
        "Al-Adl is the embodiment of absolute justice, who never wrongs anyone even by an atom's weight."),
    EsmaName(30, "\u0627\u0644\u0644\u064E\u0651\u0637\u0650\u064A\u0641\u064F", "Al-Latif", "L\u00fctfeden", "The Subtle One", "Der Feinf\u00fchlige", "\u0627\u0644\u0644\u0637\u064A\u0641",
        "Lat\u00eef, l\u00fctuf ve incelik sahibi demektir. Allah, kullar\u0131na \u00f6yle ince ve nazik yollarla iyilik eder ki, insan fark\u0131na bile varamaz.\n\nO, en zor zamanlarda bile kullar\u0131 i\u00e7in hay\u0131rl\u0131 kap\u0131lar a\u00e7ar. G\u00f6r\u00fcnmeyen nice l\u00fctuflarla kullar\u0131n\u0131 korur.",
        "Al-Latif is the Subtle One who bestows kindness in the most delicate and imperceptible ways."),
    EsmaName(31, "\u0627\u0644\u0652\u062E\u064E\u0628\u0650\u064A\u0631\u064F", "Al-Khabir", "Her \u015eeyden Haberdar", "The All-Aware", "Der Kundige", "\u0627\u0644\u062E\u0628\u064A\u0631",
        "Hab\u00eer, her \u015feyden haberdar olan demektir. \u0130\u015flerin i\u00e7 y\u00fcz\u00fcn\u00fc, gizli taraflar\u0131n\u0131 ve ince s\u0131rlar\u0131n\u0131 bilir.\n\nHi\u00e7bir \u015fey O'na gizli kalmaz. Kalplerin en derin s\u0131rlar\u0131ndan bile haberdard\u0131r.",
        "Al-Khabir is the All-Aware who knows the inner nature and hidden aspects of all things."),
    EsmaName(32, "\u0627\u0644\u0652\u062D\u064E\u0644\u0650\u064A\u0645\u064F", "Al-Halim", "Yumu\u015fak Davranan", "The Forbearing", "Der Nachsichtige", "\u0627\u0644\u062D\u0644\u064A\u0645",
        "Hal\u00eem, g\u00fcnahkarlara hemen ceza vermeyen, m\u00fchlet tan\u0131yan, yumu\u015fak davranan demektir.\n\nAllah, kullar\u0131n\u0131n g\u00fcnahlar\u0131n\u0131 g\u00f6rd\u00fc\u011f\u00fc halde hemen cezaland\u0131rmaz, tevbe etmeleri i\u00e7in f\u0131rsat verir.",
        "Al-Halim is the Forbearing who does not hasten to punish, giving time for repentance."),
    EsmaName(33, "\u0627\u0644\u0652\u0639\u064E\u0638\u0650\u064A\u0645\u064F", "Al-Azim", "B\u00fcy\u00fckl\u00fc\u011fe Sahip", "The Magnificent", "Der Gewaltige", "\u0627\u0644\u0639\u0638\u064A\u0645",
        "Az\u00eem, b\u00fcy\u00fckl\u00fck ve azamet sahibi demektir. O'nun b\u00fcy\u00fckl\u00fc\u011f\u00fcn\u00fc akl\u0131n kavramaktan aciz kald\u0131\u011f\u0131 y\u00fcce varl\u0131kt\u0131r.\n\nKainat\u0131n b\u00fcy\u00fckl\u00fc\u011f\u00fc bile O'nun azameti yan\u0131nda hi\u00e7 h\u00fckm\u00fcndedir.",
        "Al-Azim is the Magnificent whose greatness is beyond the comprehension of the human mind."),
    EsmaName(34, "\u0627\u0644\u0652\u063A\u064E\u0641\u064F\u0648\u0631\u064F", "Al-Ghafur", "Ba\u011f\u0131\u015flayan", "The Forgiving", "Der Vergebende", "\u0627\u0644\u063A\u0641\u0648\u0631",
        "Gaf\u00fbr, \u00e7ok ba\u011f\u0131\u015flayan ve g\u00fcnahlar\u0131 \u00f6rten demektir. Kulun g\u00fcnahlar\u0131 ne kadar b\u00fcy\u00fck olursa olsun, samimi bir tevbeyle d\u00f6nd\u00fc\u011f\u00fcnde ba\u011f\u0131\u015flar.\n\nGaf\u00fbr ile Gaff\u00e2r aras\u0131ndaki fark: Gaff\u00e2r s\u00fcrekli ba\u011f\u0131\u015flay\u0131c\u0131l\u0131\u011f\u0131, Gaf\u00fbr ise ba\u011f\u0131\u015flaman\u0131n derinli\u011fini vurgular.",
        "Al-Ghafur is the All-Forgiving who pardons and conceals sins, no matter how great, when one repents sincerely."),
    EsmaName(35, "\u0627\u0644\u0634\u064E\u0651\u0643\u064F\u0648\u0631\u064F", "Ash-Shakur", "\u015e\u00fckr\u00fcn Kar\u015f\u0131l\u0131\u011f\u0131n\u0131 Veren", "The Grateful", "Der Dankbare", "\u0627\u0644\u0634\u0643\u0648\u0631",
        "\u015eek\u00fbr, az iyili\u011fe \u00e7ok kar\u015f\u0131l\u0131k veren, kullar\u0131n\u0131n \u015f\u00fckr\u00fcn\u00fc kabul edip m\u00fck\u00e2fat\u0131n\u0131 kat kat art\u0131ran demektir.\n\nAllah, k\u00fc\u00e7\u00fcc\u00fck bir iyili\u011fi bile zayi etmez. Bir iyili\u011fe on kat, hatta yedi y\u00fcz kat kar\u015f\u0131l\u0131k verir.",
        "Ash-Shakur rewards even the smallest good deeds abundantly, multiplying rewards many times over."),
    EsmaName(36, "\u0627\u0644\u0652\u0639\u064E\u0644\u0650\u064A\u064F\u0651", "Al-Ali", "Y\u00fcce", "The Most High", "Der Erhabene", "\u0627\u0644\u0639\u0644\u064A",
        "Aliyy, en y\u00fcce, en y\u00fcksek demektir. O'ndan daha y\u00fcce bir varl\u0131k yoktur.\n\nO'nun y\u00fcceli\u011fi hem zat\u0131nda, hem s\u0131fatlar\u0131nda, hem de kudretindedir. Her \u015fey O'nun alt\u0131nda, O her \u015feyin \u00fcst\u00fcndedir.",
        "Al-Ali is the Most High, supreme above all creation in His essence, attributes, and power."),
    EsmaName(37, "\u0627\u0644\u0652\u0643\u064E\u0628\u0650\u064A\u0631\u064F", "Al-Kabir", "B\u00fcy\u00fck", "The Great", "Der Gro\u00dfe", "\u0627\u0644\u0643\u0628\u064A\u0631",
        "Keb\u00eer, b\u00fcy\u00fckl\u00fck ve azamet sahibi demektir. O, her \u015feyden b\u00fcy\u00fckt\u00fcr ve b\u00fcy\u00fckl\u00fck yaln\u0131zca O'na mahsustur.\n\nHi\u00e7bir varl\u0131k O'nun b\u00fcy\u00fckl\u00fc\u011f\u00fcyle k\u0131yaslanamaz.",
        "Al-Kabir is the Great, whose greatness surpasses everything and is incomparable to any creation."),
    EsmaName(38, "\u0627\u0644\u0652\u062D\u064E\u0641\u0650\u064A\u0638\u064F", "Al-Hafiz", "Koruyan", "The Preserver", "Der Bewahrer", "\u0627\u0644\u062D\u0641\u064A\u0638",
        "Haf\u00eez, her \u015feyi koruyan, muhafaza eden demektir. G\u00f6kleri ve yeri d\u00fc\u015fmekten koruyan O'dur.\n\nKullar\u0131n\u0131n amellerini kaydeder, onlar\u0131 tehlikelerden korur ve kainat\u0131n d\u00fczenini muhafaza eder.",
        "Al-Hafiz is the Preserver who protects all creation, records all deeds, and maintains cosmic order."),
    EsmaName(39, "\u0627\u0644\u0652\u0645\u064F\u0642\u0650\u064A\u062A\u064F", "Al-Muqit", "G\u00fc\u00e7 ve Kudret Veren", "The Nourisher", "Der Ern\u00e4hrer", "\u0627\u0644\u0645\u0642\u064A\u062A",
        "Muk\u00eet, her canl\u0131n\u0131n g\u0131das\u0131n\u0131 veren, g\u00fc\u00e7 ve kuvvet bah\u015feden demektir.\n\nSadece bedeni de\u011fil, ruhani beslenmeyi de sa\u011flayan O'dur. Hem maddi hem manevi g\u0131day\u0131 verir.",
        "Al-Muqit is the Nourisher who provides sustenance for both body and soul to all living beings."),
    EsmaName(40, "\u0627\u0644\u0652\u062D\u064E\u0633\u0650\u064A\u0628\u064F", "Al-Hasib", "Hesaba \u00c7eken", "The Reckoner", "Der Abrechner", "\u0627\u0644\u062D\u0633\u064A\u0628",
        "Has\u00eeb, herkesin hesab\u0131n\u0131 g\u00f6recek olan ve her \u015feye yeten demektir.\n\nKullar\u0131n\u0131n yapt\u0131klar\u0131n\u0131n hesab\u0131n\u0131 en ince ayr\u0131nt\u0131s\u0131na kadar sorar. O ayn\u0131 zamanda kullar\u0131na yetendir, k\u00e2fi oland\u0131r.",
        "Al-Hasib is the Reckoner who holds all to account and is sufficient for His servants in all matters."),
    EsmaName(41, "\u0627\u0644\u0652\u062C\u064E\u0644\u0650\u064A\u0644\u064F", "Al-Jalil", "Celal Sahibi", "The Majestic", "Der Majest\u00e4tische", "\u0627\u0644\u062C\u0644\u064A\u0644",
        "Cel\u00eel, celal ve b\u00fcy\u00fckl\u00fck sahibi demektir. O'nun azameti ve heybeti s\u0131n\u0131rs\u0131zd\u0131r.\n\nCel\u00eel ismi, Allah'\u0131n hem kahhar hem de cemal sahibi olu\u015funu ifade eder.",
        "Al-Jalil is the Majestic, possessing infinite grandeur and sublime majesty."),
    EsmaName(42, "\u0627\u0644\u0652\u0643\u064E\u0631\u0650\u064A\u0645\u064F", "Al-Karim", "Kerem Sahibi", "The Generous", "Der Gro\u00dfz\u00fcgige", "\u0627\u0644\u0643\u0631\u064A\u0645",
        "Ker\u00eem, kerem ve c\u00f6mertlik sahibi demektir. O, sonsuz c\u00f6mertli\u011fiyle kullar\u0131na nimetler ihsan eder.\n\nAllah'\u0131n keremi, istenmeden de verilir. O, kullar\u0131n\u0131n hatalar\u0131n\u0131 ba\u011f\u0131\u015flar, iyiliklerine kat kat kar\u015f\u0131l\u0131k verir.",
        "Al-Karim is the Generous who bestows blessings abundantly, even without being asked."),
    EsmaName(43, "\u0627\u0644\u0631\u064E\u0651\u0642\u0650\u064A\u0628\u064F", "Ar-Raqib", "Murakabe Eden", "The Watchful", "Der Wachsame", "\u0627\u0644\u0631\u0642\u064A\u0628",
        "Rak\u00eeb, her an her \u015feyi g\u00f6zetleyen, kontrol eden demektir. Hi\u00e7bir \u015fey O'nun g\u00f6zetiminden ka\u00e7amaz.\n\nKullar\u0131n\u0131n her halini, her hareketini ve her d\u00fc\u015f\u00fcncesini bilir ve g\u00f6zetir.",
        "Ar-Raqib is the Watchful who observes every action, thought, and moment of His creation."),
    EsmaName(44, "\u0627\u0644\u0652\u0645\u064F\u062C\u0650\u064A\u0628\u064F", "Al-Mujib", "Kar\u015f\u0131l\u0131k Veren", "The Responsive", "Der Erh\u00f6rer", "\u0627\u0644\u0645\u062C\u064A\u0628",
        "M\u00fcc\u00eeb, dualara icabet eden, kullar\u0131n\u0131n isteklerine kar\u015f\u0131l\u0131k veren demektir.\n\nAllah, samimi kalple yap\u0131lan her duay\u0131 i\u015fitir ve en hay\u0131rl\u0131 \u015fekilde kar\u015f\u0131l\u0131k verir. Bazen istedi\u011fimizi, bazen daha hay\u0131rl\u0131s\u0131n\u0131 verir.",
        "Al-Mujib is the Responsive who answers the prayers of those who call upon Him sincerely."),
    EsmaName(45, "\u0627\u0644\u0652\u0648\u064E\u0627\u0633\u0650\u0639\u064F", "Al-Wasi", "Geni\u015f Rahmet Sahibi", "The All-Encompassing", "Der Allumfassende", "\u0627\u0644\u0648\u0627\u0633\u0639",
        "V\u00e2si, rahmeti ve ilmi her \u015feyi ku\u015fatan demektir. O'nun rahmeti, ilmi ve kudreti s\u0131n\u0131rs\u0131zd\u0131r.\n\nHi\u00e7bir ihtiya\u00e7 O'nun hazinesini t\u00fcketmez, hi\u00e7bir istek O'nu aciz b\u0131rakmaz.",
        "Al-Wasi is the All-Encompassing whose mercy, knowledge, and power have no limits."),
    EsmaName(46, "\u0627\u0644\u0652\u062D\u064E\u0643\u0650\u064A\u0645\u064F", "Al-Hakim", "Hikmet Sahibi", "The Wise", "Der Allweise", "\u0627\u0644\u062D\u0643\u064A\u0645",
        "Hak\u00eem, her i\u015fi hikmetle yapan demektir. O'nun yapt\u0131\u011f\u0131 her \u015feyde bir hikmet, bir sebep vard\u0131r.\n\nBazen anlam veremedi\u011fimiz olaylarda bile gizli hikmetler bulunur. Her \u015fey O'nun takdiriyle en g\u00fczel \u015fekilde ger\u00e7ekle\u015fir.",
        "Al-Hakim is the All-Wise who does everything with perfect wisdom and purpose, even when we cannot understand."),
    EsmaName(47, "\u0627\u0644\u0652\u0648\u064E\u062F\u064F\u0648\u062F\u064F", "Al-Wadud", "Seven", "The Loving", "Der Liebevolle", "\u0627\u0644\u0648\u062F\u0648\u062F",
        "Ved\u00fbd, \u00e7ok seven ve sevilen demektir. Allah, iman eden kullar\u0131n\u0131 sever ve onlar\u0131 sevilmeye lay\u0131k k\u0131lar.\n\nO'nun sevgisi, kar\u015f\u0131l\u0131ks\u0131z ve sonsuz bir sevgidir. Kullar\u0131na olan muhabbetiyle onlar\u0131 hay\u0131rla kusat\u0131r.",
        "Al-Wadud is the Most Loving, who loves His faithful servants and makes them beloved."),
    EsmaName(48, "\u0627\u0644\u0652\u0645\u064E\u062C\u0650\u064A\u062F\u064F", "Al-Majid", "\u015ean\u0131 Y\u00fcce", "The Glorious", "Der Glorreiche", "\u0627\u0644\u0645\u062C\u064A\u062F",
        "Mec\u00eed, \u015fan\u0131 y\u00fcce, ihsan\u0131 bol demektir. O'nun \u015fan ve \u015ferefi sonsuz, c\u00f6mertli\u011fi s\u0131n\u0131rs\u0131zd\u0131r.\n\nMec\u00eed ismi, Allah'\u0131n hem y\u00fcceli\u011fini hem de c\u00f6mertli\u011fini bir arada ifade eder.",
        "Al-Majid is the Glorious, possessing infinite honor, generosity, and sublime nobility."),
    EsmaName(49, "\u0627\u0644\u0652\u0628\u064E\u0627\u0639\u0650\u062B\u064F", "Al-Ba'ith", "Diriltici", "The Resurrector", "Der Auferweckende", "\u0627\u0644\u0628\u0627\u0639\u062B",
        "B\u00e2is, \u00f6l\u00fcleri dirilten, k\u0131yamet g\u00fcn\u00fc insanlar\u0131 yeniden yaratan demektir.\n\nAyn\u0131 zamanda peygamberler g\u00f6nderen, insanlara hidayet yolunu a\u00e7an da O'dur.",
        "Al-Ba'ith is the Resurrector who raises the dead and sends prophets to guide humanity."),
    EsmaName(50, "\u0627\u0644\u0634\u064E\u0651\u0647\u0650\u064A\u062F\u064F", "Ash-Shahid", "Her \u015eeye \u015eahit", "The Witness", "Der Zeuge", "\u0627\u0644\u0634\u0647\u064A\u062F",
        "\u015eeh\u00eed, her \u015feye tan\u0131k olan, hi\u00e7bir \u015feyin kendisinden gizli kalmad\u0131\u011f\u0131 demektir.\n\nO, her an her yerde olan biten her \u015feye \u015fahittir. K\u0131yamet g\u00fcn\u00fc kullar\u0131n\u0131n yapt\u0131klar\u0131na \u015fahitlik edecektir.",
        "Ash-Shahid is the Witness who observes everything and from whom nothing is hidden."),
    EsmaName(51, "\u0627\u0644\u0652\u062D\u064E\u0642\u064F\u0651", "Al-Haqq", "Ger\u00e7ek", "The Truth", "Die Wahrheit", "\u0627\u0644\u062D\u0642",
        "Hakk, varl\u0131\u011f\u0131 ger\u00e7ek, de\u011fi\u015fmeyen ve yok olmayan demektir. O, mutlak hakikattir. Her \u015fey fani olsa da O bakidir.\n\nO'nun dini hakt\u0131r, vaadi hakt\u0131r, k\u0131yameti hakt\u0131r, cenneti ve cehennemi hakt\u0131r.",
        "Al-Haqq is the Absolute Truth whose existence is eternal, unchanging, and undeniable."),
    EsmaName(52, "\u0627\u0644\u0652\u0648\u064E\u0643\u0650\u064A\u0644\u064F", "Al-Wakil", "G\u00fcvenilen Vekil", "The Trustee", "Der Sachwalter", "\u0627\u0644\u0648\u0643\u064A\u0644",
        "Vek\u00eel, i\u015fleri kendisine havale edenlerin i\u015flerini en g\u00fczel \u015fekilde y\u00fcr\u00fcten demektir.\n\nTevekkul eden kullar\u0131n\u0131n i\u015flerini en hay\u0131rl\u0131 sonuca ula\u015ft\u0131r\u0131r. O'na g\u00fcvenen asla h\u00fcsrana u\u011framaz.",
        "Al-Wakil is the Trustee who manages the affairs of those who rely on Him in the best way."),
    EsmaName(53, "\u0627\u0644\u0652\u0642\u064E\u0648\u0650\u064A\u064F\u0651", "Al-Qawi", "\u00c7ok G\u00fc\u00e7l\u00fc", "The Strong", "Der Starke", "\u0627\u0644\u0642\u0648\u064A",
        "Kav\u00ee, sonsuz g\u00fc\u00e7 sahibi demektir. O'nun g\u00fcc\u00fc asla azalmaz, zay\u0131flamaz ve bitmez.\n\nHi\u00e7bir g\u00fc\u00e7 O'nun g\u00fcc\u00fcne denk olamaz. Kainat\u0131 yaratan ve ayakta tutan O'nun sonsuz kudretidir.",
        "Al-Qawi is the Strong whose power never diminishes and cannot be matched by any force."),
    EsmaName(54, "\u0627\u0644\u0652\u0645\u064E\u062A\u0650\u064A\u0646\u064F", "Al-Matin", "\u00c7ok Sa\u011flam", "The Firm", "Der Feste", "\u0627\u0644\u0645\u062A\u064A\u0646",
        "Met\u00een, \u00e7ok sa\u011flam ve metin demektir. O'nun g\u00fcc\u00fc asla sars\u0131lmaz.\n\nKainat\u0131 ayakta tutan O'nun sars\u0131lmaz kudretidir. Hi\u00e7bir \u015fey O'nu zay\u0131flatamaz veya yoramaz.",
        "Al-Matin is the Firm and Steadfast whose power is unshakeable and never weakens."),
    EsmaName(55, "\u0627\u0644\u0652\u0648\u064E\u0644\u0650\u064A\u064F\u0651", "Al-Wali", "Dost ve Yard\u0131mc\u0131", "The Patron", "Der Schutzherr", "\u0627\u0644\u0648\u0644\u064A",
        "Vel\u00ee, dost, yard\u0131mc\u0131 ve koruyucu demektir. Allah, iman edenlerin dostudur.\n\nO, m\u00fcminlerin velisidir; onlar\u0131 karanl\u0131klardan ayd\u0131nl\u0131\u011fa \u00e7\u0131kar\u0131r, yard\u0131m eder ve korur.",
        "Al-Wali is the Patron and Protector who befriends and supports the believers."),
    EsmaName(56, "\u0627\u0644\u0652\u062D\u064E\u0645\u0650\u064A\u062F\u064F", "Al-Hamid", "\u00d6v\u00fclen", "The Praiseworthy", "Der Lobenswerte", "\u0627\u0644\u062D\u0645\u064A\u062F",
        "Ham\u00eed, her t\u00fcrl\u00fc \u00f6vg\u00fcye lay\u0131k olan demektir. O, zatinde, s\u0131fatlar\u0131nda ve fiillerinde hamdedilmeye lay\u0131kt\u0131r.\n\nB\u00fct\u00fcn g\u00fczellikler ve nimetler O'ndand\u0131r, bu y\u00fczden b\u00fct\u00fcn hamd O'na aittir.",
        "Al-Hamid is the Praiseworthy who deserves all praise for His essence, attributes, and actions."),
    EsmaName(57, "\u0627\u0644\u0652\u0645\u064F\u062D\u0652\u0635\u0650\u064A", "Al-Muhsi", "Her \u015eeyi Sayan", "The Counter", "Der Aufz\u00e4hlende", "\u0627\u0644\u0645\u062D\u0635\u064A",
        "Muhs\u00ee, her \u015feyi tek tek sayan, ilmiyle ku\u015fatan demektir.\n\nYa\u011fmurun her damlas\u0131n\u0131, yapraklar\u0131n say\u0131s\u0131n\u0131, y\u0131ld\u0131zlar\u0131n adedini bilen O'dur. Hi\u00e7bir \u015fey O'nun say\u0131m\u0131ndan ka\u00e7amaz.",
        "Al-Muhsi is the Counter who enumerates and knows the exact count of everything in creation."),
    EsmaName(58, "\u0627\u0644\u0652\u0645\u064F\u0628\u0652\u062F\u0650\u0626\u064F", "Al-Mubdi", "\u0130lk Yaratan", "The Originator", "Der Hervorbringer", "\u0627\u0644\u0645\u0628\u062F\u0626",
        "M\u00fcbd\u00ee, her \u015feyi \u00f6rneksiz olarak ilk defa yaratan demektir.\n\nO, kainat\u0131 ve i\u00e7indeki her \u015feyi daha \u00f6nce bir \u00f6rne\u011fi olmaks\u0131z\u0131n yoktan var etmi\u015ftir.",
        "Al-Mubdi is the Originator who creates everything for the first time without any prior model."),
    EsmaName(59, "\u0627\u0644\u0652\u0645\u064F\u0639\u0650\u064A\u062F\u064F", "Al-Mu'id", "Tekrar Yaratan", "The Restorer", "Der Wiederhersteller", "\u0627\u0644\u0645\u0639\u064A\u062F",
        "Mu\u00eed, \u00f6ld\u00fckten sonra tekrar yaratan, hayat\u0131 iade eden demektir.\n\nK\u0131yamet g\u00fcn\u00fc b\u00fct\u00fcn varl\u0131klar\u0131 yeniden diriltecek olan O'dur. Birinci yaratmaya g\u00fcc\u00fc yeten, ikinci kez yaratmaya da kadirdir.",
        "Al-Mu'id is the Restorer who will recreate all beings after death on the Day of Judgment."),
    EsmaName(60, "\u0627\u0644\u0652\u0645\u064F\u062D\u0652\u064A\u0650\u064A", "Al-Muhyi", "Dirilten", "The Giver of Life", "Der Lebensspender", "\u0627\u0644\u0645\u062D\u064A\u064A",
        "Muhy\u00ee, hayat veren, dirilten demektir. Canl\u0131lara hayat bahseden O'dur.\n\n\u00d6l\u00fc topra\u011f\u0131 ya\u011fmurla dirilten, \u00f6l\u00fcleri k\u0131yamette diriltecek olan da O'dur.",
        "Al-Muhyi is the Giver of Life who grants life to all living beings and will resurrect the dead."),
    EsmaName(61, "\u0627\u0644\u0652\u0645\u064F\u0645\u0650\u064A\u062A\u064F", "Al-Mumit", "\u00d6ld\u00fcren", "The Bringer of Death", "Der Todbringer", "\u0627\u0644\u0645\u0645\u064A\u062A",
        "M\u00fcm\u00eet, \u00f6l\u00fcm\u00fc yaratan demektir. Her canl\u0131n\u0131n eceli O'nun takdirindedir.\n\n\u00d6l\u00fcm, yok olu\u015f de\u011fil, bir ge\u00e7i\u015ftir. M\u00fcm\u00eet ismi, hayat\u0131n ge\u00e7icili\u011fini hat\u0131rlat\u0131r.",
        "Al-Mumit is the Bringer of Death who decrees the end of every life. Death is a transition, not annihilation."),
    EsmaName(62, "\u0627\u0644\u0652\u062D\u064E\u064A\u064F\u0651", "Al-Hayy", "Diri", "The Ever Living", "Der Lebendige", "\u0627\u0644\u062D\u064A",
        "Hayy, e\u015fsiz ve daimi bir hayata sahip olan demektir. O'nun hayat\u0131 ba\u015flang\u0131\u00e7s\u0131z ve sonsuz, uyku ve gaflet gibi hallerden m\u00fcnezzehtir.\n\nHer canl\u0131n\u0131n hayat\u0131 O'nun vermesiyledir. O, \u00f6l\u00fcms\u00fcz ve daima diri olan tek varl\u0131kt\u0131r.",
        "Al-Hayy is the Ever Living whose life is eternal, without beginning or end, free from sleep or heedlessness."),
    EsmaName(63, "\u0627\u0644\u0652\u0642\u064E\u064A\u064F\u0651\u0648\u0645\u064F", "Al-Qayyum", "Kaim", "The Self-Subsisting", "Der Best\u00e4ndige", "\u0627\u0644\u0642\u064A\u0648\u0645",
        "Kayy\u00fbm, kendi kendine var olan ve b\u00fct\u00fcn varl\u0131klar\u0131 ayakta tutan demektir. O hi\u00e7bir \u015feye muhta\u00e7 de\u011fildir ama her \u015fey O'na muhta\u00e7t\u0131r.\n\nKainat\u0131n d\u00fczeni, gezegenlerin hareketi, hayat\u0131n devam\u0131 hep O'nun kudretiyledir.",
        "Al-Qayyum is the Self-Subsisting who sustains all of creation. Everything depends on Him, while He depends on nothing."),
    EsmaName(64, "\u0627\u0644\u0652\u0648\u064E\u0627\u062C\u0650\u062F\u064F", "Al-Wajid", "Bulan", "The Finder", "Der Findende", "\u0627\u0644\u0648\u0627\u062C\u062F",
        "V\u00e2cid, her istedi\u011fini bulan, hi\u00e7bir \u015feye ihtiyac\u0131 olmayan demektir.\n\nO'nun \u00f6n\u00fcnde hi\u00e7bir engel yoktur. Diledi\u011fi her \u015feyi yapar ve her arad\u0131\u011f\u0131n\u0131 bulur.",
        "Al-Wajid is the Finder who lacks nothing and achieves everything He wills without any obstacle."),
    EsmaName(65, "\u0627\u0644\u0652\u0645\u064E\u0627\u062C\u0650\u062F\u064F", "Al-Majid", "\u015eerefli", "The Noble", "Der Ruhmreiche", "\u0627\u0644\u0645\u0627\u062C\u062F",
        "M\u00e2cid, \u015ferefi y\u00fcce, ba\u011f\u0131\u015f\u0131 bol demektir. O, \u015feref ve keremiyle her \u015feyin \u00fcst\u00fcndedir.\n\nKullar\u0131na olan ikram\u0131 ve ihsan\u0131 sonsuz ve s\u0131n\u0131rs\u0131zd\u0131r.",
        "Al-Majid is the Noble whose honor and generosity are exalted above all."),
    EsmaName(66, "\u0627\u0644\u0652\u0648\u064E\u0627\u062D\u0650\u062F\u064F", "Al-Wahid", "Tek", "The One", "Der Einzige", "\u0627\u0644\u0648\u0627\u062D\u062F",
        "V\u00e2hid, tek olan, e\u015fi ve benzeri bulunmayan demektir. O, zatinde, s\u0131fatlar\u0131nda ve fiillerinde tektir.\n\nHi\u00e7bir varl\u0131k O'na e\u015f ve ortak kosulamaz. O, benzersiz ve e\u015fsizdir.",
        "Al-Wahid is the One, unique in His essence, attributes, and actions, with no partner or equal."),
    EsmaName(67, "\u0627\u0644\u0623\u064E\u062D\u064E\u062F\u064F", "Al-Ahad", "Bir", "The Unique", "Der Eine", "\u0627\u0644\u0623\u062D\u062F",
        "Ehad, birli\u011finde e\u015fsiz olan demektir. V\u00e2hid say\u0131sal tekli\u011fi, Ehad ise mutlak birli\u011fi ifade eder.\n\nO, par\u00e7alanmaz, b\u00f6l\u00fcnmez ve ke\u015folunamaz. Ehad ismi, Allah'\u0131n mutlak ve benzersiz birli\u011fini vurgular.",
        "Al-Ahad is the Unique One whose oneness is absolute and indivisible, beyond comparison."),
    EsmaName(68, "\u0627\u0644\u0635\u064E\u0651\u0645\u064E\u062F\u064F", "As-Samad", "Muhta\u00e7 Olunulan", "The Eternal", "Der Unabh\u00e4ngige", "\u0627\u0644\u0635\u0645\u062F",
        "Samed, hi\u00e7bir \u015feye muhta\u00e7 olmayan ama her \u015feyin kendisine muhta\u00e7 oldu\u011fu demektir.\n\nB\u00fct\u00fcn ihtiya\u00e7lar i\u00e7in ba\u015fvurulacak tek mercii O'dur. O, yemez, i\u00e7mez, uyumaz; her \u015fey O'na muhta\u00e7t\u0131r.",
        "As-Samad is the Eternal Refuge, independent of all needs while all creation depends on Him."),
    EsmaName(69, "\u0627\u0644\u0652\u0642\u064E\u0627\u062F\u0650\u0631\u064F", "Al-Qadir", "G\u00fc\u00e7 Yetiren", "The Able", "Der Machthabende", "\u0627\u0644\u0642\u0627\u062F\u0631",
        "Kadir, her \u015feye g\u00fcc\u00fc yeten demektir. O'nun g\u00fcc\u00fc s\u0131n\u0131rs\u0131zd\u0131r ve diledi\u011fi her \u015feyi yapar.\n\nO, 'ol' der ve olur. Hi\u00e7bir \u015fey O'na zor gelmez, hi\u00e7bir i\u015f O'nu aciz b\u0131rakmaz.",
        "Al-Qadir is the All-Capable whose power is infinite. He merely says 'Be' and it becomes."),
    EsmaName(70, "\u0627\u0644\u0652\u0645\u064F\u0642\u0652\u062A\u064E\u062F\u0650\u0631\u064F", "Al-Muqtadir", "Kudret Sahibi", "The Powerful", "Der M\u00e4chtige", "\u0627\u0644\u0645\u0642\u062A\u062F\u0631",
        "Muktedir, tam ve mutlak kudret sahibi demektir. O'nun kudreti her \u015feyi kapsar.\n\nKainat\u0131n yarat\u0131l\u0131\u015f\u0131ndan y\u0131k\u0131l\u0131\u015f\u0131na kadar her \u015fey O'nun kudretinin eseridir.",
        "Al-Muqtadir possesses absolute and all-encompassing power over all creation."),
    EsmaName(71, "\u0627\u0644\u0652\u0645\u064F\u0642\u064E\u062F\u064F\u0651\u0645\u064F", "Al-Muqaddim", "\u00d6ne Ge\u00e7iren", "The Expediter", "Der Voranbringende", "\u0627\u0644\u0645\u0642\u062F\u0645",
        "Mukaddim, diledi\u011fini \u00f6ne ge\u00e7iren, y\u00fccelten demektir.\n\nKimini ilimle, kimini ibadetle, kimini hikmetle \u00f6ne ge\u00e7irir. \u00d6ne ge\u00e7me ve geri kalma O'nun takdirindedir.",
        "Al-Muqaddim is the Expediter who advances and brings forward whom He wills."),
    EsmaName(72, "\u0627\u0644\u0652\u0645\u064F\u0624\u064E\u062E\u064F\u0651\u0631\u064F", "Al-Mu'akhkhir", "Erteleyen", "The Delayer", "Der Aufhaltende", "\u0627\u0644\u0645\u0624\u062E\u0631",
        "Muahhir, diledi\u011fini geriye b\u0131rakan, erteleyen demektir.\n\nBaz\u0131 \u015feylerin ertelenmesinde de hikmet vard\u0131r. O, her \u015feyi en uygun zamanda ger\u00e7ekle\u015ftirir.",
        "Al-Mu'akhkhir is the Delayer who postpones matters according to His wisdom and perfect timing."),
    EsmaName(73, "\u0627\u0644\u0623\u064E\u0648\u064E\u0651\u0644\u064F", "Al-Awwal", "\u0130lk", "The First", "Der Erste", "\u0627\u0644\u0623\u0648\u0644",
        "Evvel, ba\u015flang\u0131c\u0131 olmayan, her \u015feyden \u00f6nce var olan demektir.\n\nO, ezelden beri vard\u0131r. Hi\u00e7bir \u015fey yokken O vard\u0131, kainat yarat\u0131lmadan \u00f6nce de O vard\u0131.",
        "Al-Awwal is the First who existed before everything else, with no beginning to His existence."),
    EsmaName(74, "\u0627\u0644\u0622\u062E\u0650\u0631\u064F", "Al-Akhir", "Son", "The Last", "Der Letzte", "\u0627\u0644\u0622\u062E\u0631",
        "\u00c2hir, sonu olmayan, her \u015feyden sonra da var olacak olan demektir.\n\nHer \u015fey fani olacak, ama O sonsuza dek baki kalacakt\u0131r. O'nun varl\u0131\u011f\u0131n\u0131n sonu yoktur.",
        "Al-Akhir is the Last who will remain after all else has perished, with no end to His existence."),
    EsmaName(75, "\u0627\u0644\u0638\u064E\u0651\u0627\u0647\u0650\u0631\u064F", "Az-Zahir", "Varl\u0131\u011f\u0131 A\u00e7\u0131k", "The Manifest", "Der Offenbare", "\u0627\u0644\u0638\u0627\u0647\u0631",
        "Z\u00e2hir, varl\u0131\u011f\u0131 a\u00e7\u0131k, delilleriyle a\u015fikar olan demektir.\n\nKainat\u0131n her k\u00f6\u015fesi O'nun varl\u0131\u011f\u0131n\u0131n delilidir. G\u00f6klerdeki y\u0131ld\u0131zlardan yerdeki \u00e7i\u00e7eklere kadar her \u015fey O'nu g\u00f6sterir.",
        "Az-Zahir is the Manifest whose existence is evident through the signs and wonders of creation."),
    EsmaName(76, "\u0627\u0644\u0652\u0628\u064E\u0627\u0637\u0650\u0646\u064F", "Al-Batin", "Gizli", "The Hidden", "Der Verborgene", "\u0627\u0644\u0628\u0627\u0637\u0646",
        "Bat\u0131n, g\u00f6zlerle g\u00f6r\u00fclemeyen, akl\u0131n tam olarak kavrayamad\u0131\u011f\u0131 demektir.\n\nO'nun zat\u0131 g\u00f6zlerden gizlidir ama eserleri her yerdedir. O, hem z\u00e2hir hem bat\u0131nd\u0131r.",
        "Al-Batin is the Hidden whose essence cannot be seen or fully comprehended, yet His works are everywhere."),
    EsmaName(77, "\u0627\u0644\u0652\u0648\u064E\u0627\u0644\u0650\u064A", "Al-Wali", "Y\u00f6neten", "The Governor", "Der Statthalter", "\u0627\u0644\u0648\u0627\u0644\u064A",
        "V\u00e2l\u00ee, b\u00fct\u00fcn kainat\u0131 y\u00f6neten, i\u015fleri \u00e7ekip \u00e7eviren demektir.\n\nG\u00f6klerin ve yerin y\u00f6netimi O'nun elindedir. Her \u015fey O'nun iradesi ve y\u00f6netimiyle i\u015fler.",
        "Al-Wali is the Governor who administers and manages all affairs of the universe."),
    EsmaName(78, "\u0627\u0644\u0652\u0645\u064F\u062A\u064E\u0639\u064E\u0627\u0644\u0650\u064A", "Al-Muta'ali", "Y\u00fcce", "The Most Exalted", "Der Hoch Erhabene", "\u0627\u0644\u0645\u062A\u0639\u0627\u0644\u064A",
        "M\u00fcte\u00e2l\u00ee, her \u015feyden y\u00fcce, b\u00fct\u00fcn noksan s\u0131fatlardan m\u00fcnezzeh demektir.\n\nO, akl\u0131n ula\u015fabilece\u011fi en y\u00fcce mertebeden de \u00f6tedir. Hi\u00e7bir varl\u0131k O'nun y\u00fcceli\u011fine ula\u015famaz.",
        "Al-Muta'ali is the Most Exalted, transcending all imperfections and beyond all comprehension."),
    EsmaName(79, "\u0627\u0644\u0652\u0628\u064E\u0631\u064F\u0651", "Al-Barr", "\u0130yilik Eden", "The Source of Goodness", "Der G\u00fctige", "\u0627\u0644\u0628\u0631",
        "Berr, iyilik ve ihsan sahibi demektir. O, kullar\u0131na kar\u015f\u0131 s\u0131n\u0131rs\u0131z iyilik ve l\u00fctuf sahibidir.\n\nO'nun ihsan ve ikram\u0131 her an devam eder. Kullar\u0131na olan \u015fefkat ve merhameti sonsuz bir iyilik kayna\u011f\u0131d\u0131r.",
        "Al-Barr is the Source of Goodness whose kindness and benevolence toward His creation are limitless."),
    EsmaName(80, "\u0627\u0644\u062A\u064E\u0651\u0648\u064E\u0651\u0627\u0628\u064F", "At-Tawwab", "Tevbeleri Kabul Eden", "The Acceptor of Repentance", "Der Reueannehmer", "\u0627\u0644\u062A\u0648\u0627\u0628",
        "Tevv\u00e2b, tevbeleri \u00e7ok\u00e7a kabul eden demektir. Kul her d\u00f6n\u00fc\u015f\u00fcnde O'nu ba\u011f\u0131\u015flay\u0131c\u0131 bulur.\n\nO, kullar\u0131n\u0131n tevbe etmesini sever ve tevbeleri kabul ederek g\u00fcnahlar\u0131n\u0131 siler. Tevbe kap\u0131s\u0131 her zaman a\u00e7\u0131kt\u0131r.",
        "At-Tawwab accepts repentance abundantly. He loves when His servants turn back to Him and forgives their sins."),
    EsmaName(81, "\u0627\u0644\u0652\u0645\u064F\u0646\u0652\u062A\u064E\u0642\u0650\u0645\u064F", "Al-Muntaqim", "Su\u00e7lular\u0131 Cezaland\u0131ran", "The Avenger", "Der R\u00e4cher", "\u0627\u0644\u0645\u0646\u062A\u0642\u0645",
        "M\u00fcntakim, zalimleri ve su\u00e7lular\u0131 adaletli bir \u015fekilde cezaland\u0131ran demektir.\n\nO, zulme asla raz\u0131 olmaz. Haklar\u0131 gasp edilenlerin intikam\u0131n\u0131 alacak olan O'dur.",
        "Al-Muntaqim is the Avenger who justly punishes the wrongdoers and oppressors."),
    EsmaName(82, "\u0627\u0644\u0652\u0639\u064E\u0641\u064F\u0648\u064F\u0651", "Al-Afuw", "Affedici", "The Pardoner", "Der Vergeber", "\u0627\u0644\u0639\u0641\u0648",
        "Af\u00fcv, g\u00fcnahlar\u0131 tamamen silen, affeden demektir. O, g\u00fcnahlar\u0131 sadece ba\u011f\u0131\u015flamakla kalmaz, tamamen siler.\n\nGaf\u00fbr g\u00fcnah\u0131 \u00f6rter, Af\u00fcv ise g\u00fcnah\u0131 k\u00f6k\u00fcnden siler ve yok eder. En y\u00fcce ba\u011f\u0131\u015flanma budur.",
        "Al-Afuw is the Pardoner who completely erases sins, going beyond mere forgiveness to total obliteration of wrongdoing."),
    EsmaName(83, "\u0627\u0644\u0631\u064E\u0651\u0624\u064F\u0648\u0641\u064F", "Ar-Ra'uf", "\u00c7ok \u015eefkatli", "The Compassionate", "Der Mitleidige", "\u0627\u0644\u0631\u0624\u0648\u0641",
        "Ra\u00fbf, \u00e7ok \u015fefkatli, ac\u0131yan demektir. O'nun \u015fefkati, rahmetinden bile daha \u00f6zel ve derindedir.\n\nKullar\u0131na olan \u015fefkatiyle onlar\u0131 s\u0131k\u0131nt\u0131lardan kurtar\u0131r, zorluklar\u0131 kolayla\u015ft\u0131r\u0131r.",
        "Ar-Ra'uf is the Most Compassionate whose tenderness and care for His servants is profoundly deep."),
    EsmaName(84, "\u0645\u064E\u0627\u0644\u0650\u0643\u064F \u0627\u0644\u0652\u0645\u064F\u0644\u0652\u0643\u0650", "Malik-ul-Mulk", "M\u00fclk\u00fcn Sahibi", "The Owner of Sovereignty", "Der Herr der Herrschaft", "\u0645\u0627\u0644\u0643 \u0627\u0644\u0645\u0644\u0643",
        "M\u00e2lik\u00fcl M\u00fclk, b\u00fct\u00fcn m\u00fclk\u00fcn ve saltanat\u0131n ger\u00e7ek sahibi demektir.\n\nM\u00fclk\u00fc diledi\u011fine verir, diledi\u011finden al\u0131r. Diledi\u011fini aziz k\u0131lar, diledi\u011fini zelil eder. Her t\u00fcrl\u00fc hay\u0131r O'nun elindedir.",
        "Malik-ul-Mulk is the Owner of all sovereignty who grants dominion to whom He wills and takes it from whom He wills."),
    EsmaName(85, "\u0630\u064F\u0648 \u0627\u0644\u0652\u062C\u064E\u0644\u064E\u0627\u0644\u0650 \u0648\u064E\u0627\u0644\u0652\u0625\u0650\u0643\u0652\u0631\u064E\u0627\u0645\u0650", "Dhul-Jalali wal-Ikram", "Celal ve \u0130kram Sahibi", "The Lord of Majesty and Bounty", "Der Herr der Majest\u00e4t und Gro\u00dfmut", "\u0630\u0648 \u0627\u0644\u062C\u0644\u0627\u0644 \u0648\u0627\u0644\u0625\u0643\u0631\u0627\u0645",
        "Z\u00fclcel\u00e2li vel \u0130kr\u00e2m, hem celal hem de ikram sahibi demektir. O, hem heybet ve azamet, hem de l\u00fctuf ve kerem sahibidir.\n\nBu isim, Allah'\u0131n hem kahhar hem de cemal s\u0131fatlar\u0131n\u0131 bir arada ifade eder.",
        "Dhul-Jalali wal-Ikram is the Lord of Majesty and Bounty, combining awe-inspiring greatness with generous kindness."),
    EsmaName(86, "\u0627\u0644\u0652\u0645\u064F\u0642\u0652\u0633\u0650\u0637\u064F", "Al-Muqsit", "Adaletli", "The Equitable", "Der Gerechte", "\u0627\u0644\u0645\u0642\u0633\u0637",
        "Muksit, adaletli davranan, hakk\u0131 yerine getiren demektir.\n\nO, k\u0131yamet g\u00fcn\u00fc adalet terazisini kuracak ve hi\u00e7 kimseye zerre kadar haks\u0131zl\u0131k yap\u0131lmayacakt\u0131r.",
        "Al-Muqsit is the Equitable who establishes justice and fairness, ensuring no one is wronged."),
    EsmaName(87, "\u0627\u0644\u0652\u062C\u064E\u0627\u0645\u0650\u0639\u064F", "Al-Jami", "Toplayan", "The Gatherer", "Der Versammler", "\u0627\u0644\u062C\u0627\u0645\u0639",
        "C\u00e2mi, her \u015feyi toplayan demektir. K\u0131yamet g\u00fcn\u00fc b\u00fct\u00fcn insanlar\u0131 mahser meydan\u0131nda toplayacak olan O'dur.\n\nAyn\u0131 zamanda z\u0131t \u015feyleri bir araya getiren de O'dur: gece ile g\u00fcnd\u00fcz\u00fc, s\u0131cak ile so\u011fugu, hayat ile \u00f6l\u00fcm\u00fc.",
        "Al-Jami is the Gatherer who will assemble all of humanity on the Day of Judgment."),
    EsmaName(88, "\u0627\u0644\u0652\u063A\u064E\u0646\u0650\u064A\u064F\u0651", "Al-Ghani", "Zengin", "The Self-Sufficient", "Der Gen\u00fcgsame", "\u0627\u0644\u063A\u0646\u064A",
        "Gan\u00ee, hi\u00e7bir \u015feye ihtiyac\u0131 olmayan, mutlak zenginlik sahibi demektir.\n\nO, hi\u00e7bir varl\u0131\u011fa muhta\u00e7 de\u011fildir ama her varl\u0131k O'na muhta\u00e7t\u0131r. \u0130badete de ihtiyac\u0131 yoktur; ibadet kulun ihtiyac\u0131d\u0131r.",
        "Al-Ghani is the Self-Sufficient who needs nothing from creation, while all creation needs Him."),
    EsmaName(89, "\u0627\u0644\u0652\u0645\u064F\u063A\u0652\u0646\u0650\u064A", "Al-Mughni", "Zenginlik Veren", "The Enricher", "Der Reichmachende", "\u0627\u0644\u0645\u063A\u0646\u064A",
        "Mu\u011fn\u00ee, diledi\u011fini zenginle\u015ftiren demektir. Maddi ve manevi zenginli\u011fi veren O'dur.\n\nGer\u00e7ek zenginlik g\u00f6n\u00fcl zenginli\u011fidir ve bunu da veren Allah't\u0131r.",
        "Al-Mughni is the Enricher who grants material and spiritual wealth to whom He wills."),
    EsmaName(90, "\u0627\u0644\u0652\u0645\u064E\u0627\u0646\u0650\u0639\u064F", "Al-Mani", "Engelleyici", "The Preventer", "Der Verhinderer", "\u0627\u0644\u0645\u0627\u0646\u0639",
        "M\u00e2ni, diledi\u011fi \u015feyi engelleyen demektir. O, baz\u0131 \u015feylerin olmas\u0131n\u0131 hikmetiyle engeller.\n\nBazen engellenen \u015feyler asl\u0131nda kulun lehine olan durumlard\u0131r. O, zararl\u0131 \u015feylerden kullar\u0131n\u0131 korur.",
        "Al-Mani is the Preventer who blocks what would harm His servants, even when they cannot see the wisdom."),
    EsmaName(91, "\u0627\u0644\u0636\u064E\u0651\u0627\u0631\u064F\u0651", "Ad-Darr", "Zarar Verici", "The Distresser", "Der Schadenbringende", "\u0627\u0644\u0636\u0627\u0631",
        "Darr, zarar ve s\u0131k\u0131nt\u0131 vermeye g\u00fcc\u00fc yeten demektir. Hi\u00e7bir g\u00fc\u00e7 O'nun izni olmadan zarar veremez.\n\nBu isim, N\u00e2fi ismiyle birlikte d\u00fc\u015f\u00fcn\u00fclmelidir. Zarar da fayda da O'nun elindedir.",
        "Ad-Darr is the One who can cause harm when He wills. No force can cause harm without His permission."),
    EsmaName(92, "\u0627\u0644\u0646\u064E\u0651\u0627\u0641\u0650\u0639\u064F", "An-Nafi", "Fayda Veren", "The Propitious", "Der N\u00fctzende", "\u0627\u0644\u0646\u0627\u0641\u0639",
        "N\u00e2fi, fayda veren, hayra ula\u015ft\u0131ran demektir. Ger\u00e7ek fayda yaln\u0131zca O'ndand\u0131r.\n\nHi\u00e7bir \u015fey O'nun izni olmadan fayda veremez. \u0130la\u00e7 O'nun izniyle \u015fifa verir, g\u0131da O'nun izniyle besler.",
        "An-Nafi is the One who grants benefit and goodness. True benefit comes only through His permission."),
    EsmaName(93, "\u0627\u0644\u0646\u064F\u0651\u0648\u0631\u064F", "An-Nur", "Nur", "The Light", "Das Licht", "\u0627\u0644\u0646\u0648\u0631",
        "N\u00fbr, g\u00f6kleri ve yeri ayd\u0131nlatan, kalplere iman \u0131\u015f\u0131\u011f\u0131 veren demektir.\n\nAllah, kainat\u0131n nurudur. G\u00fcne\u015fin, ay\u0131n ve y\u0131ld\u0131zlar\u0131n \u0131\u015f\u0131\u011f\u0131 O'nun nurunun bir yans\u0131mas\u0131d\u0131r. Kalpleri iman nuruyla ayd\u0131nlatan da O'dur.",
        "An-Nur is the Light who illuminates the heavens and the earth, and fills hearts with the light of faith."),
    EsmaName(94, "\u0627\u0644\u0652\u0647\u064E\u0627\u062F\u0650\u064A", "Al-Hadi", "Hidayet Veren", "The Guide", "Der F\u00fchrer", "\u0627\u0644\u0647\u0627\u062F\u064A",
        "H\u00e2d\u00ee, do\u011fru yola ula\u015ft\u0131ran, hidayet veren demektir. Ger\u00e7ek hidayet yaln\u0131zca Allah'\u0131n elindedir.\n\nO, diledi\u011fi kulunu do\u011fru yola iletir, kalbini imana a\u00e7ar ve hakikati g\u00f6sterir.",
        "Al-Hadi is the Guide who leads to the straight path and opens hearts to truth and faith."),
    EsmaName(95, "\u0627\u0644\u0652\u0628\u064E\u062F\u0650\u064A\u0639\u064F", "Al-Badi", "E\u015fsiz Yaratan", "The Originator", "Der Sch\u00f6pfer des Neuen", "\u0627\u0644\u0628\u062F\u064A\u0639",
        "Bed\u00ee, e\u015fi ve benzeri olmayan \u015feyleri yaratan, e\u015fsiz g\u00fczellikte icat eden demektir.\n\nKainat\u0131n e\u015fsiz g\u00fczellikleri, canl\u0131lar\u0131n muhte\u015fem tasar\u0131mlar\u0131 hep Bed\u00ee isminin tecellisidir.",
        "Al-Badi is the Originator who creates unprecedented wonders with unmatched beauty and perfection."),
    EsmaName(96, "\u0627\u0644\u0652\u0628\u064E\u0627\u0642\u0650\u064A", "Al-Baqi", "Ebedi", "The Everlasting", "Der Ewige", "\u0627\u0644\u0628\u0627\u0642\u064A",
        "B\u00e2k\u00ee, varl\u0131\u011f\u0131 sonsuz olan, asla yok olmayacak demektir.\n\nHer \u015fey fani olacak, yaln\u0131zca O baki kalacakt\u0131r. D\u00fcnya ve i\u00e7indekiler ge\u00e7icidir, kal\u0131c\u0131 olan yaln\u0131zca Allah't\u0131r.",
        "Al-Baqi is the Everlasting whose existence has no end. All things perish, but He remains forever."),
    EsmaName(97, "\u0627\u0644\u0652\u0648\u064E\u0627\u0631\u0650\u062B\u064F", "Al-Warith", "Her \u015eeyin Varisi", "The Inheritor", "Der Erbe", "\u0627\u0644\u0648\u0627\u0631\u062B",
        "V\u00e2ris, her \u015feyin ger\u00e7ek ve son varisi demektir. B\u00fct\u00fcn varl\u0131klar yok olduktan sonra m\u00fclk yaln\u0131zca O'na kal\u0131r.\n\nBug\u00fcn sahip oldu\u011fumuz her \u015fey emanettir ve sonunda ger\u00e7ek sahibine d\u00f6necektir.",
        "Al-Warith is the Inheritor to whom all sovereignty returns after all creation has perished."),
    EsmaName(98, "\u0627\u0644\u0631\u064E\u0651\u0634\u0650\u064A\u062F\u064F", "Ar-Rashid", "Yol G\u00f6steren", "The Guide to the Right Path", "Der Rechtleitende", "\u0627\u0644\u0631\u0634\u064A\u062F",
        "Re\u015f\u00eed, do\u011fru yolu g\u00f6steren, ir\u015fad eden demektir. O, b\u00fct\u00fcn i\u015fleri hikmetle y\u00f6netir ve kullar\u0131n\u0131 do\u011fruya y\u00f6nlendirir.\n\nO'nun g\u00f6sterdi\u011fi yol en do\u011fru yoldur. O'na tabi olan asla sap\u0131tmaz.",
        "Ar-Rashid is the Rightly-Guiding who directs all affairs with wisdom and leads to the right path."),
    EsmaName(99, "\u0627\u0644\u0635\u064E\u0651\u0628\u064F\u0648\u0631\u064F", "As-Sabur", "\u00c7ok Sabirlı", "The Patient", "Der Geduldige", "\u0627\u0644\u0635\u0628\u0648\u0631",
        "Sab\u00fbr, \u00e7ok sab\u0131rl\u0131, g\u00fcnahkarlara hemen ceza vermeyen demektir. O, kullar\u0131na m\u00fchlet verir ve acele etmez.\n\nAllah'\u0131n sabr\u0131 sonsuzdur. Kullar\u0131n isyanlar\u0131na ra\u011fmen onlara f\u0131rsat verir, tevbe etmelerini bekler ve affeder.",
        "As-Sabur is the Most Patient who does not hasten to punish, granting abundant time for repentance.")
)

// ── API ──

private val esmaHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

private object EsmaCache {
    var names: List<EsmaName> = emptyList()
    var fetched: Boolean = false
}

private suspend fun fetchEsmaFromApi(): List<EsmaName>? = withContext(Dispatchers.IO) {
    try {
        val url = "https://api.aladhan.com/v1/asmaAlHusna"
        val request = Request.Builder().url(url).build()
        val response = esmaHttpClient.newCall(request).execute()
        if (!response.isSuccessful) return@withContext null

        val body = response.body?.string() ?: return@withContext null
        val json = JSONObject(body)
        val data = json.getJSONArray("data")

        val apiNames = mutableListOf<EsmaName>()
        for (i in 0 until data.length()) {
            val item = data.getJSONObject(i)
            val number = item.getInt("number")
            val arabic = item.getString("name")
            val transliteration = item.getString("transliteration")
            val meaningEn = item.getJSONObject("en").getString("meaning")

            val local = localEsmaNames.find { it.number == number }
            apiNames.add(
                EsmaName(
                    number = number,
                    arabic = arabic,
                    transliteration = transliteration,
                    meaningTr = local?.meaningTr ?: "",
                    meaningEn = meaningEn,
                    meaningDe = local?.meaningDe ?: "",
                    meaningAr = local?.meaningAr ?: "",
                    detailTr = local?.detailTr ?: "",
                    detailEn = local?.detailEn ?: ""
                )
            )
        }
        apiNames
    } catch (_: Exception) {
        null
    }
}

// ── Helper ──

private fun t(tr: String, en: String, de: String, ar: String, lang: String): String = when (lang) {
    "en" -> en; "de" -> de; "ar" -> ar; else -> tr
}

// ── UI ──

@Composable
internal fun EsmaScreen(
    isDarkTheme: Boolean,
    selectedLanguage: String
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val bg = MaterialTheme.colorScheme.background

    var names by remember { mutableStateOf(EsmaCache.names.ifEmpty { localEsmaNames }) }
    var isLoading by remember { mutableStateOf(!EsmaCache.fetched) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedEsma by remember { mutableStateOf<EsmaName?>(null) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        if (!EsmaCache.fetched) {
            val apiResult = fetchEsmaFromApi()
            if (apiResult != null) {
                names = apiResult
                EsmaCache.names = apiResult
            } else {
                names = localEsmaNames
                EsmaCache.names = localEsmaNames
            }
            EsmaCache.fetched = true
            isLoading = false
        }
    }

    val filteredNames = remember(names, searchQuery) {
        if (searchQuery.isBlank()) names
        else {
            val q = searchQuery.lowercase()
            names.filter { name ->
                name.transliteration.lowercase().contains(q) ||
                name.arabic.contains(q) ||
                name.meaningTr.lowercase().contains(q) ||
                name.meaningEn.lowercase().contains(q) ||
                name.meaningDe.lowercase().contains(q) ||
                name.meaningAr.contains(q) ||
                name.number.toString() == q
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Grid View
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bg)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = {
                    Text(
                        t("Ara...", "Search...", "Suchen...", "\u0628\u062D\u062B...", selectedLanguage),
                        color = onSurfaceVariant
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = onSurfaceVariant) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = null, tint = onSurfaceVariant)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primary,
                    unfocusedBorderColor = surfaceVariant,
                    cursorColor = primary,
                    focusedTextColor = onSurface,
                    unfocusedTextColor = onSurface
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = primary)
                }
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalItemSpacing = 10.dp
                ) {
                    items(filteredNames, key = { it.number }) { esma ->
                        val cardColor = getCardColor(esma.number)
                        val displayColor = if (isDarkTheme) cardColor.copy(alpha = 0.25f) else cardColor

                        // Staggered height: alternate between tall and short
                        val isLarge = esma.number % 3 == 1

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isLarge) Modifier.height(200.dp)
                                    else Modifier.height(160.dp)
                                ),
                            shape = RoundedCornerShape(16.dp),
                            color = displayColor,
                            onClick = { selectedEsma = esma }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                            ) {
                                // Number badge
                                Text(
                                    text = "${esma.number}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDarkTheme) primary else primary.copy(alpha = 0.8f),
                                    modifier = Modifier.align(Alignment.TopStart)
                                )

                                // Arabic name (large, centered)
                                Text(
                                    text = esma.arabic,
                                    fontSize = if (isLarge) 36.sp else 30.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDarkTheme) Color.White.copy(alpha = 0.9f) else Color(0xFF1A1A2E),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .padding(top = if (isLarge) 8.dp else 0.dp)
                                )

                                // Transliteration
                                Text(
                                    text = esma.transliteration,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else Color(0xFF1A1A2E).copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Detail Overlay
        AnimatedVisibility(
            visible = selectedEsma != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            selectedEsma?.let { esma ->
                EsmaDetailView(
                    esma = esma,
                    isDarkTheme = isDarkTheme,
                    selectedLanguage = selectedLanguage,
                    onDismiss = { selectedEsma = null }
                )
            }
        }
    }
}

@Composable
private fun EsmaDetailView(
    esma: EsmaName,
    isDarkTheme: Boolean,
    selectedLanguage: String,
    onDismiss: () -> Unit
) {
    val bg = MaterialTheme.colorScheme.background
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val cardColor = getCardColor(esma.number)
    val headerColor = if (isDarkTheme) cardColor.copy(alpha = 0.2f) else cardColor.copy(alpha = 0.5f)

    val meaning = when (selectedLanguage) {
        "en" -> esma.meaningEn; "de" -> esma.meaningDe; "ar" -> esma.meaningAr; else -> esma.meaningTr
    }
    val detail = when (selectedLanguage) {
        "en" -> esma.detailEn.ifEmpty { esma.meaningEn }
        "de" -> esma.detailEn.ifEmpty { esma.meaningDe } // fallback to English detail
        "ar" -> esma.meaningAr
        else -> esma.detailTr.ifEmpty { esma.meaningTr }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = onSurface)
            }
            Text(
                text = t("Esma\u00fcl H\u00fcsna", "Names of Allah", "Namen Allahs", "\u0623\u0633\u0645\u0627\u0621 \u0627\u0644\u0644\u0647", selectedLanguage),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = onSurface
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Header card with Arabic name
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                shape = RoundedCornerShape(20.dp),
                color = headerColor
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Arabic name large
                    Text(
                        text = esma.arabic,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) Color.White else Color(0xFF1A1A2E),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Transliteration
                    Text(
                        text = esma.transliteration,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) Color.White.copy(alpha = 0.9f) else Color(0xFF1A1A2E),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Short meaning
                    Text(
                        text = meaning,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDarkTheme) Color.White.copy(alpha = 0.8f) else Color(0xFF1A1A2E).copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Detailed explanation
            if (detail.isNotEmpty() && detail != meaning) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = onSurfaceVariant.copy(alpha = 0.2f)
                )

                detail.split("\n\n").forEach { paragraph ->
                    if (paragraph.isNotBlank()) {
                        Text(
                            text = paragraph.trim(),
                            fontSize = 16.sp,
                            lineHeight = 26.sp,
                            color = onSurface,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
