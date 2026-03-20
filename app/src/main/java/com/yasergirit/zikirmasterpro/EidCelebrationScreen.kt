package com.yasergirit.zikirmasterpro

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun EidCelebrationScreen(
    selectedLanguage: String,
    onDismiss: () -> Unit
) {
    fun t(tr: String, en: String, de: String = "", ar: String = "") = when (selectedLanguage) {
        "en" -> en; "de" -> de.ifEmpty { en }; "ar" -> ar.ifEmpty { en }; else -> tr
    }

    // Auto-dismiss after 5 seconds
    LaunchedEffect(Unit) {
        delay(5000)
        onDismiss()
    }

    // Animations
    val infiniteTransition = rememberInfiniteTransition(label = "eid")

    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "shimmer"
    )

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val titleAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(1200, delayMillis = 300), label = "titleAlpha"
    )
    val titleScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.5f,
        animationSpec = tween(1200, delayMillis = 300, easing = EaseOutBack), label = "titleScale"
    )
    val messageAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(1000, delayMillis = 800), label = "msgAlpha"
    )
    val bottomAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(1000, delayMillis = 1500), label = "bottomAlpha"
    )

    // Sparkle particles
    val sparkles = remember {
        List(30) {
            SparkleParticle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                size = Random.nextFloat() * 4f + 2f,
                speed = Random.nextFloat() * 0.3f + 0.1f,
                phase = Random.nextFloat() * 6.28f
            )
        }
    }

    val sparkleTime by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing)
        ), label = "sparkleTime"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A1628),
                        Color(0xFF0D2137),
                        Color(0xFF0F3A2E),
                        Color(0xFF1A4A3A),
                        Color(0xFF0D2137),
                        Color(0xFF0A1628)
                    )
                )
            )
    ) {
        // Sparkle canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            sparkles.forEach { s ->
                val alpha = ((sin(sparkleTime + s.phase) + 1f) / 2f) * 0.8f + 0.1f
                val px = s.x * size.width
                val py = (s.y + sin(sparkleTime * s.speed + s.phase) * 0.02f) * size.height
                drawCircle(
                    color = Color(0xFFFFD700).copy(alpha = alpha),
                    radius = s.size,
                    center = Offset(px, py)
                )
            }
        }

        // Decorative geometric pattern (top)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val topY = size.height * 0.08f
            val radius = size.width * 0.3f
            // Crescent moon shape
            drawCircle(
                color = Color(0xFFFFD700).copy(alpha = 0.15f * shimmer),
                radius = radius,
                center = Offset(cx, topY + radius)
            )
            drawCircle(
                color = Color(0xFF0A1628),
                radius = radius * 0.82f,
                center = Offset(cx + radius * 0.3f, topY + radius - radius * 0.1f)
            )
            // Star near crescent
            val starCx = cx - radius * 0.15f
            val starCy = topY + radius * 0.9f
            for (i in 0..4) {
                val angle = Math.toRadians((i * 72 - 90).toDouble())
                val innerAngle = Math.toRadians((i * 72 + 36 - 90).toDouble())
                val outerR = radius * 0.12f
                val innerR = outerR * 0.45f
                val ox = starCx + cos(angle).toFloat() * outerR
                val oy = starCy + sin(angle).toFloat() * outerR
                drawCircle(
                    color = Color(0xFFFFD700).copy(alpha = 0.35f * shimmer),
                    radius = 3f,
                    center = Offset(ox, oy)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(0.3f))

            // Mosque emoji
            Text(
                text = "🕌",
                fontSize = 56.sp,
                modifier = Modifier
                    .alpha(titleAlpha)
                    .scale(titleScale)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Bayram title
            Text(
                text = t(
                    "Ramazan Bayramınız\nMübarek Olsun!",
                    "Eid al-Fitr\nMubarak!",
                    "Eid al-Fitr\nMubarak!",
                    "عيد الفطر\nالمبارك!"
                ),
                style = TextStyle(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD700),
                    textAlign = TextAlign.Center,
                    lineHeight = 42.sp,
                    shadow = Shadow(
                        color = Color(0xFFFFD700).copy(alpha = 0.4f),
                        offset = Offset(0f, 2f),
                        blurRadius = 12f
                    )
                ),
                modifier = Modifier
                    .alpha(titleAlpha)
                    .scale(titleScale)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Decorative divider
            Text(
                text = "✦  ✦  ✦",
                color = Color(0xFFFFD700).copy(alpha = shimmer * 0.7f),
                fontSize = 18.sp,
                modifier = Modifier.alpha(messageAlpha)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Message
            Text(
                text = t(
                    "Tüm İslam aleminin\nRamazan Bayramı kutlu olsun!\nBu mübarek günlerde dualarınız kabul,\nsofranız bereketli olsun.",
                    "Wishing the entire Muslim Ummah\na blessed Eid al-Fitr!\nMay your prayers be accepted\nand your tables abundant.",
                    "Wir wünschen der gesamten muslimischen Ummah\nein gesegnetes Eid al-Fitr!\nMögen eure Gebete erhört\nund eure Tische reichhaltig sein.",
                    "كل عام وأنتم بخير\nأعاده الله علينا وعلى الأمة الإسلامية\nبالخير واليمن والبركات\nتقبل الله منا ومنكم صالح الأعمال"
                ),
                style = TextStyle(
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    lineHeight = 26.sp
                ),
                modifier = Modifier.alpha(messageAlpha)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Decorative line
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.alpha(bottomAlpha)
            ) {
                Text("☪", fontSize = 20.sp, color = Color(0xFFFFD700).copy(alpha = shimmer))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Sofi App",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("☪", fontSize = 20.sp, color = Color(0xFFFFD700).copy(alpha = shimmer))
            }

            Spacer(modifier = Modifier.weight(0.3f))
        }
    }
}

private data class SparkleParticle(
    val x: Float,
    val y: Float,
    val size: Float,
    val speed: Float,
    val phase: Float
)
