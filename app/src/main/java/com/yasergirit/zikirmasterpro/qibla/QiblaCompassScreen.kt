package com.yasergirit.zikirmasterpro.qibla

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.yasergirit.zikirmasterpro.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.math.cos

/**
 * Qibla Compass screen.
 *
 * Rotation logic:
 *   - Compass DIAL rotates by -azimuth so "N" always points to real north
 *   - Qibla NEEDLE drawn at (qiblaBearing - azimuth) so it always points toward Kaaba
 *   - When device faces Qibla, needle points straight up (angle = 0)
 */
@Composable
fun QiblaCompassScreen(
    isDarkTheme: Boolean,
    selectedLanguage: String
) {
    fun t(tr: String, en: String, de: String = "", ar: String = "") = when (selectedLanguage) {
        "en" -> en; "de" -> de.ifEmpty { en }; "ar" -> ar.ifEmpty { en }; else -> tr
    }

    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val bg = MaterialTheme.colorScheme.background
    val cardColor = if (isDarkTheme) DarkCard else LightCard

    // ── State ──
    var locationPermissionGranted by remember { mutableStateOf(false) }
    var azimuth by remember { mutableFloatStateOf(0f) }
    var hasAzimuth by remember { mutableStateOf(false) }
    var qiblaBearing by remember { mutableFloatStateOf(0f) }
    var hasLocation by remember { mutableStateOf(false) }
    var isSensorAvailable by remember { mutableStateOf(true) }
    var isLocationEnabled by remember { mutableStateOf(true) }

    // ── Sensor & Location managers (remembered, not recreated) ──
    val compassSensor = remember { CompassSensorManager(context) }
    val locationProvider = remember { QiblaLocationProvider(context) }

    // Check sensor availability
    LaunchedEffect(Unit) {
        isSensorAvailable = compassSensor.isSensorAvailable
    }

    // ── Collect sensor azimuth ──
    val azimuthState by compassSensor.azimuth.collectAsState()
    LaunchedEffect(azimuthState) {
        azimuthState?.let {
            azimuth = it
            hasAzimuth = true
        }
    }

    // ── Collect location ──
    val locationState by locationProvider.location.collectAsState()
    LaunchedEffect(locationState) {
        locationState?.let { loc ->
            qiblaBearing = QiblaCalculator.calculateQiblaBearing(loc.latitude, loc.longitude)
            hasLocation = true
        }
    }

    // ── Permission flow ──
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        locationPermissionGranted = granted
        if (granted) {
            isLocationEnabled = locationProvider.isLocationEnabled()
            locationProvider.startUpdates()
        }
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        locationPermissionGranted = granted
        if (granted) {
            isLocationEnabled = locationProvider.isLocationEnabled()
            locationProvider.startUpdates()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // ── Lifecycle: start/stop sensors ──
    DisposableEffect(Unit) {
        compassSensor.start()
        onDispose {
            compassSensor.stop()
            locationProvider.stopUpdates()
        }
    }

    // ── Computed values ──
    val relativeAngle = if (hasLocation && hasAzimuth) {
        QiblaCalculator.relativeQiblaAngle(qiblaBearing, azimuth)
    } else 0f
    val isFacingQibla = hasLocation && hasAzimuth && QiblaCalculator.isFacingQibla(relativeAngle)
    val isLoading = locationPermissionGranted && !hasLocation

    // ── Smooth animation ──
    var prevDialRotation by remember { mutableFloatStateOf(0f) }
    var prevNeedleRotation by remember { mutableFloatStateOf(0f) }

    val dialTarget = QiblaCalculator.sanitizeForAnimation(
        QiblaCalculator.normalizeDegrees(-azimuth), prevDialRotation
    )
    val needleTarget = QiblaCalculator.sanitizeForAnimation(relativeAngle, prevNeedleRotation)

    LaunchedEffect(dialTarget) { prevDialRotation = dialTarget }
    LaunchedEffect(needleTarget) { prevNeedleRotation = needleTarget }

    val animatedDial by animateFloatAsState(
        targetValue = dialTarget,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "dial"
    )
    val animatedNeedle by animateFloatAsState(
        targetValue = needleTarget,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "needle"
    )

    // ── UI ──
    Column(
        modifier = Modifier.fillMaxSize().background(bg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        when {
            !isSensorAvailable -> {
                ErrorState(emoji = "🧭", message = t(
                    "Bu cihazda pusula sensörü bulunamadı.",
                    "Compass sensor not found on this device.",
                    "Kompasssensor auf diesem Gerät nicht gefunden.",
                    "لم يتم العثور على مستشعر البوصلة."
                ), color = onSurfaceVariant)
            }
            !locationPermissionGranted -> {
                ErrorState(emoji = "📍", message = t(
                    "Kıble yönünü hesaplamak için konum iznine ihtiyaç var.",
                    "Location permission is required to calculate Qibla direction.",
                    "Standortberechtigung wird für die Qibla-Berechnung benötigt.",
                    "إذن الموقع مطلوب لحساب اتجاه القبلة."
                ), color = onSurfaceVariant, buttonText = t("İzin Ver", "Grant Permission", "Erlauben", "منح الإذن"), onButtonClick = {
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                })
            }
            !isLocationEnabled -> {
                ErrorState(emoji = "📍", message = t(
                    "Konum servisleri kapalı. Lütfen cihaz ayarlarından açın.",
                    "Location services are off. Please enable them in device settings.",
                    "Standortdienste sind deaktiviert.",
                    "خدمات الموقع معطلة."
                ), color = onSurfaceVariant)
            }
            isLoading -> {
                Spacer(modifier = Modifier.weight(1f))
                CircularProgressIndicator(modifier = Modifier.size(48.dp), color = primary, strokeWidth = 3.dp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(t("Konum alınıyor...", "Getting location...", "Standort wird ermittelt...", "جاري تحديد الموقع..."), fontSize = 16.sp, color = onSurfaceVariant)
                Spacer(modifier = Modifier.weight(1f))
            }
            else -> {
                // Yön gösterici: sola/sağa dönün + yanıp sönen ok
                val dirAngle = QiblaCalculator.normalizeDegrees(relativeAngle)
                var blinkVisible by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(500)
                        blinkVisible = !blinkVisible
                    }
                }
                val blinkAlpha by animateFloatAsState(
                    targetValue = if (blinkVisible) 1f else 0.15f,
                    animationSpec = tween(400),
                    label = "blinkAlpha"
                )

                Spacer(modifier = Modifier.height(4.dp))

                when {
                    dirAngle in 5f..180f -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t("Sağa dönün", "Turn right", "Nach rechts drehen", "انعطف يميناً"), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("→", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = primary.copy(alpha = blinkAlpha))
                        }
                    }
                    dirAngle > 180f && dirAngle <= 355f -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("←", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = primary.copy(alpha = blinkAlpha))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(t("Sola dönün", "Turn left", "Nach links drehen", "انعطف يساراً"), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = primary)
                        }
                    }
                    else -> {
                        Text("✓ " + t("Kıble yönündesiniz", "Facing Qibla", "Qibla-Richtung", "أنت في اتجاه القبلة"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Gold)
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text("${QiblaCalculator.normalizeDegrees(azimuth).toInt()}°", fontSize = 14.sp, color = onSurfaceVariant)
                Spacer(modifier = Modifier.weight(0.2f))

                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(320.dp)) {
                    Canvas(modifier = Modifier.size(300.dp)) {
                        drawCompassDial(animatedDial, animatedNeedle, isFacingQibla, primary, surfaceVariant, onSurface, onSurfaceVariant, isDarkTheme)
                    }
                    Surface(shape = CircleShape, color = if (isFacingQibla) Gold else cardColor, shadowElevation = 4.dp, modifier = Modifier.size(64.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text("🕋", fontSize = 28.sp) }
                    }
                }

                Spacer(modifier = Modifier.weight(0.15f))

                Surface(shape = RoundedCornerShape(16.dp), color = if (isFacingQibla) Gold.copy(alpha = 0.15f) else cardColor, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isFacingQibla) {
                            Text(t("Kıble Yönündesiniz ✓", "Facing Qibla ✓", "Qibla-Richtung ✓", "أنت في اتجاه القبلة ✓"), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Gold)
                        } else {
                            Text(t("Kıble Yönü: ${qiblaBearing.toInt()}°", "Qibla Direction: ${qiblaBearing.toInt()}°", "Qibla-Richtung: ${qiblaBearing.toInt()}°", "اتجاه القبلة: ${qiblaBearing.toInt()}°"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = onSurface)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(t("Telefonunuzu 45° açıyla tutun ve ok işaretinin gösterdiği yöne doğru dönün", "Hold your phone at a 45° angle and turn toward the direction the arrow points", "Halten Sie Ihr Telefon in einem 45°-Winkel und drehen Sie sich in die Richtung des Pfeils", "أمسك هاتفك بزاوية 45° واستدر نحو الاتجاه الذي يشير إليه السهم"), fontSize = 13.sp, color = onSurfaceVariant, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, textAlign = TextAlign.Center)
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(0.15f))
            }
        }
    }
}

@Composable
private fun ColumnScope.ErrorState(emoji: String, message: String, color: Color, buttonText: String? = null, onButtonClick: (() -> Unit)? = null) {
    Spacer(modifier = Modifier.weight(1f))
    Text(emoji, fontSize = 56.sp)
    Spacer(modifier = Modifier.height(12.dp))
    Text(message, color = color, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
    if (buttonText != null && onButtonClick != null) {
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onButtonClick) { Text(buttonText) }
    }
    Spacer(modifier = Modifier.weight(1f))
}

private fun DrawScope.drawCompassDial(dialRotation: Float, needleAngle: Float, isFacingQibla: Boolean, primary: Color, surfaceVariant: Color, onSurface: Color, onSurfaceVariant: Color, isDarkTheme: Boolean) {
    val cx = size.width / 2; val cy = size.height / 2; val radius = size.minDimension / 2 - 16f

    drawCircle(color = surfaceVariant, radius = radius + 8f, center = Offset(cx, cy), style = Stroke(width = 2f))

    rotate(dialRotation) {
        for (i in 0 until 360 step 5) {
            val isCardinal = i % 90 == 0; val isMajor = i % 30 == 0
            val lineLen = when { isCardinal -> 24f; isMajor -> 16f; else -> 8f }
            val lineW = when { isCardinal -> 3f; isMajor -> 2f; else -> 1f }
            val lineC = when { isCardinal -> onSurface; isMajor -> onSurfaceVariant; else -> onSurfaceVariant.copy(alpha = 0.4f) }
            val a = Math.toRadians(i.toDouble()).toFloat()
            drawLine(lineC, Offset(cx + (radius - lineLen) * sin(a), cy - (radius - lineLen) * cos(a)), Offset(cx + radius * sin(a), cy - radius * cos(a)), lineW, StrokeCap.Round)
        }
        val tp = android.graphics.Paint().apply { textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true; textSize = 20f; isFakeBoldText = true }
        listOf(Triple(0f, "N", Color(0xFFE74C3C)), Triple(90f, "E", onSurfaceVariant), Triple(180f, "S", onSurfaceVariant), Triple(270f, "W", onSurfaceVariant)).forEach { (ang, lbl, clr) ->
            val r = Math.toRadians(ang.toDouble()).toFloat(); val tr = radius - 40f
            tp.color = android.graphics.Color.argb((clr.alpha * 255).toInt(), (clr.red * 255).toInt(), (clr.green * 255).toInt(), (clr.blue * 255).toInt())
            drawContext.canvas.nativeCanvas.drawText(lbl, cx + tr * sin(r), cy - tr * cos(r) + 8f, tp)
        }
        val np = Path().apply { val ty = cy - radius + 2f; moveTo(cx, ty); lineTo(cx - 8f, ty + 18f); lineTo(cx + 8f, ty + 18f); close() }
        drawPath(np, Color(0xFFE74C3C))
    }

    val qr = Math.toRadians(needleAngle.toDouble()).toFloat()
    val ac = if (isFacingQibla) Gold else primary
    val sr = 50f; val er = radius - 50f
    drawLine(ac, Offset(cx + sr * sin(qr), cy - sr * cos(qr)), Offset(cx + er * sin(qr), cy - er * cos(qr)), 4f, StrokeCap.Round)
    val tipR = er + 4f; val tipX = cx + tipR * sin(qr); val tipY = cy - tipR * cos(qr); val wr = er - 16f
    drawPath(Path().apply { moveTo(tipX, tipY); lineTo(cx + wr * sin(qr - 0.3f), cy - wr * cos(qr - 0.3f)); lineTo(cx + wr * sin(qr + 0.3f), cy - wr * cos(qr + 0.3f)); close() }, ac)
    val or2 = qr + Math.PI.toFloat()
    drawLine(ac.copy(alpha = 0.3f), Offset(cx + sr * sin(or2), cy - sr * cos(or2)), Offset(cx + (er * 0.6f) * sin(or2), cy - (er * 0.6f) * cos(or2)), 2f, StrokeCap.Round)
    drawCircle(surfaceVariant, 36f, Offset(cx, cy))
}
