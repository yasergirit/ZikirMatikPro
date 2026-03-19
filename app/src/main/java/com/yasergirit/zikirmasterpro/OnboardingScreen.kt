@file:OptIn(ExperimentalFoundationApi::class)

package com.yasergirit.zikirmasterpro

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
import androidx.compose.ui.text.font.FontWeight
import com.yasergirit.zikirmasterpro.ui.theme.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class OnboardingPage(
    val emoji: String,
    val titleTr: String,
    val titleEn: String,
    val descriptionTr: String,
    val descriptionEn: String
)

private val onboardingPages = listOf(
    OnboardingPage(
        emoji = "📿",
        titleTr = "Zikir Sayacı",
        titleEn = "Dhikr Counter",
        descriptionTr = "Büyük sayaç butonuna dokunarak zikirlerinizi kolayca sayın. Her dokunuşta titreşim ve ses geri bildirimi alın.",
        descriptionEn = "Easily count your dhikr by tapping the large counter button. Get vibration and sound feedback with each tap."
    ),
    OnboardingPage(
        emoji = "💾",
        titleTr = "Kaydet ve Takip Et",
        titleEn = "Save & Track",
        descriptionTr = "Zikir sayınızı kaydedin ve geçmiş kayıtlarınızı görüntüleyin. İstatistiklerinizi günlük, haftalık ve aylık olarak takip edin.",
        descriptionEn = "Save your dhikr count and view your past records. Track your statistics daily, weekly, and monthly."
    ),
    OnboardingPage(
        emoji = "⚙️",
        titleTr = "Kişiselleştirin",
        titleEn = "Customize",
        descriptionTr = "Karanlık/aydınlık tema, ses efektleri, titreşim modu ve dil seçenekleriyle uygulamayı kendinize göre ayarlayın.",
        descriptionEn = "Customize the app with dark/light theme, sound effects, vibration mode, and language options."
    ),
    OnboardingPage(
        emoji = "🕌",
        titleTr = "Günlük Hatırlatma",
        titleEn = "Daily Reminder",
        descriptionTr = "Her gün saat 20:00'de güzel bir ayet bildirimi alın. Manevi dünyanızı zenginleştirin.",
        descriptionEn = "Receive a beautiful verse notification every day at 8:00 PM. Enrich your spiritual world."
    )
)

@Composable
fun OnboardingScreen(
    selectedLanguage: String,
    onFinish: () -> Unit
) {
    fun t(tr: String, en: String) = if (selectedLanguage == "en") en else tr

    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val coroutineScope = rememberCoroutineScope()

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
                        text = t("Atla", "Skip"),
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
                        text = if (selectedLanguage == "en") currentPage.titleEn else currentPage.titleTr,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Description
                    Text(
                        text = if (selectedLanguage == "en") currentPage.descriptionEn else currentPage.descriptionTr,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )
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
                    text = if (pagerState.currentPage == onboardingPages.size - 1)
                        t("Başla", "Get Started")
                    else
                        t("İleri", "Next"),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
