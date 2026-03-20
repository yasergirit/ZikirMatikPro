@file:OptIn(ExperimentalFoundationApi::class)

package com.yasergirit.zikirmasterpro

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import com.yasergirit.zikirmasterpro.ui.theme.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

data class OnboardingPage(
    val emoji: String,
    val titleTr: String,
    val titleEn: String,
    val titleDe: String = "",
    val titleAr: String = "",
    val descriptionTr: String,
    val descriptionEn: String,
    val descriptionDe: String = "",
    val descriptionAr: String = ""
)

private val onboardingPages = listOf(
    OnboardingPage(
        emoji = "📿",
        titleTr = "Zikir Sayacı",
        titleEn = "Dhikr Counter",
        titleDe = "Dhikr-Zähler",
        titleAr = "عدّاد الأذكار",
        descriptionTr = "Büyük sayaç butonuna dokunarak zikirlerinizi kolayca sayın. Her dokunuşta titreşim ve ses geri bildirimi alın.",
        descriptionEn = "Easily count your dhikr by tapping the large counter button. Get vibration and sound feedback with each tap.",
        descriptionDe = "Zählen Sie Ihre Dhikr einfach durch Tippen auf die große Zähltaste. Erhalten Sie bei jedem Tippen Vibrations- und Sound-Feedback.",
        descriptionAr = "عدّ أذكارك بسهولة بالضغط على زر العداد الكبير. احصل على اهتزاز وصوت مع كل ضغطة."
    ),
    OnboardingPage(
        emoji = "💾",
        titleTr = "Kaydet ve Takip Et",
        titleEn = "Save & Track",
        titleDe = "Speichern & Verfolgen",
        titleAr = "حفظ ومتابعة",
        descriptionTr = "Zikir sayınızı kaydedin ve geçmiş kayıtlarınızı görüntüleyin. İstatistiklerinizi günlük, haftalık ve aylık olarak takip edin.",
        descriptionEn = "Save your dhikr count and view your past records. Track your statistics daily, weekly, and monthly.",
        descriptionDe = "Speichern Sie Ihre Dhikr-Zählung und sehen Sie Ihre vergangenen Einträge. Verfolgen Sie Ihre Statistiken täglich, wöchentlich und monatlich.",
        descriptionAr = "احفظ عدد أذكارك واعرض سجلاتك السابقة. تابع إحصائياتك يومياً وأسبوعياً وشهرياً."
    ),
    OnboardingPage(
        emoji = "⚙️",
        titleTr = "Kişiselleştirin",
        titleEn = "Customize",
        titleDe = "Anpassen",
        titleAr = "تخصيص",
        descriptionTr = "Karanlık/aydınlık tema, ses efektleri, titreşim modu ve dil seçenekleriyle uygulamayı kendinize göre ayarlayın.",
        descriptionEn = "Customize the app with dark/light theme, sound effects, vibration mode, and language options.",
        descriptionDe = "Passen Sie die App mit Hell-/Dunkel-Design, Soundeffekten, Vibrationsmodus und Sprachoptionen an.",
        descriptionAr = "خصّص التطبيق مع المظهر الداكن/الفاتح والمؤثرات الصوتية والاهتزاز وخيارات اللغة."
    ),
    OnboardingPage(
        emoji = "🕌",
        titleTr = "Günlük Hatırlatma",
        titleEn = "Daily Reminder",
        titleDe = "Tägliche Erinnerung",
        titleAr = "تذكير يومي",
        descriptionTr = "Namaz vakitlerinde bildirim alın, günlük ayet ve hadis okuyun.",
        descriptionEn = "Get notified at prayer times, read daily verses and hadiths.",
        descriptionDe = "Erhalten Sie Benachrichtigungen zu Gebetszeiten, lesen Sie tägliche Verse und Hadithe.",
        descriptionAr = "احصل على إشعارات في أوقات الصلاة، اقرأ آيات وأحاديث يومية."
    ),
    OnboardingPage(
        emoji = "📍",
        titleTr = "Konum İzni",
        titleEn = "Location Permission",
        titleDe = "Standortberechtigung",
        titleAr = "إذن الموقع",
        descriptionTr = "Namaz vakitlerini ve kıble yönünü doğru gösterebilmemiz için konum bilginize ihtiyacımız var. Konum bilginiz yalnızca bu amaçla kullanılır.",
        descriptionEn = "We need your location to show accurate prayer times and qibla direction. Your location is only used for this purpose.",
        descriptionDe = "Wir benötigen Ihren Standort für genaue Gebetszeiten und die Qibla-Richtung. Ihr Standort wird nur für diesen Zweck verwendet.",
        descriptionAr = "نحتاج إلى موقعك لعرض أوقات الصلاة واتجاه القبلة بدقة. يُستخدم موقعك لهذا الغرض فقط."
    )
)

@Composable
fun OnboardingScreen(
    selectedLanguage: String,
    onFinish: () -> Unit
) {
    fun t(tr: String, en: String, de: String = "", ar: String = "") = when (selectedLanguage) {
        "en" -> en; "de" -> de.ifEmpty { en }; "ar" -> ar.ifEmpty { en }; else -> tr
    }

    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val coroutineScope = rememberCoroutineScope()
    val locationPageIndex = onboardingPages.size - 1

    var locationGranted by remember { mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    ) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        locationGranted = granted
    }

    val isOnLocationPage = pagerState.currentPage == locationPageIndex

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
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Skip button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onFinish) {
                    Text(
                        text = t("Atla", "Skip", "Überspringen", "تخطي"),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp
                    )
                }
            }

            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                val currentPage = onboardingPages[page]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Emoji icon
                    Text(
                        text = currentPage.emoji,
                        fontSize = 80.sp,
                        modifier = Modifier.padding(bottom = 32.dp)
                    )

                    // Title
                    Text(
                        text = when (selectedLanguage) {
                            "en" -> currentPage.titleEn
                            "de" -> currentPage.titleDe.ifEmpty { currentPage.titleEn }
                            "ar" -> currentPage.titleAr.ifEmpty { currentPage.titleEn }
                            else -> currentPage.titleTr
                        },
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Description
                    Text(
                        text = when (selectedLanguage) {
                            "en" -> currentPage.descriptionEn
                            "de" -> currentPage.descriptionDe.ifEmpty { currentPage.descriptionEn }
                            "ar" -> currentPage.descriptionAr.ifEmpty { currentPage.descriptionEn }
                            else -> currentPage.descriptionTr
                        },
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )

                    // Location permission button on last page
                    if (page == locationPageIndex) {
                        Spacer(modifier = Modifier.height(32.dp))
                        if (locationGranted) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Emerald.copy(alpha = 0.2f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text("✅", fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = t("Konum izni verildi", "Location permission granted", "Standortberechtigung erteilt", "تم منح إذن الموقع"),
                                        color = EmeraldLight,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        } else {
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
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                            ) {
                                Text(
                                    text = "📍  " + t("Konum İznini Ver", "Grant Location Access", "Standortzugriff erlauben", "منح إذن الموقع"),
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Page indicators
            Row(
                modifier = Modifier.padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(onboardingPages.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    val width by animateDpAsState(
                        targetValue = if (isSelected) 24.dp else 8.dp,
                        label = "indicator_width"
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(8.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) EmeraldLight
                                else EmeraldLight.copy(alpha = 0.4f)
                            )
                    )
                }
            }

            // Next / Start button
            Button(
                onClick = {
                    if (pagerState.currentPage == onboardingPages.size - 1) {
                        onFinish()
                    } else {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(56.dp)
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Emerald
                )
            ) {
                Text(
                    text = if (isOnLocationPage)
                        t("Başla", "Get Started", "Los geht's", "ابدأ")
                    else
                        t("İleri", "Next", "Weiter", "التالي"),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
