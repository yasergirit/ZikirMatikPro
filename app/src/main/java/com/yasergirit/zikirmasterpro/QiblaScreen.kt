package com.yasergirit.zikirmasterpro

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Brush
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
import kotlin.math.*

// Kabe koordinatları
private const val KAABA_LAT = 21.4225
private const val KAABA_LNG = 39.8262

/**
 * Kullanıcının konumuna göre Kıble açısını hesaplar (derece cinsinden, kuzeyden saat yönünde).
 */
private fun calculateQiblaDirection(userLat: Double, userLng: Double): Double {
    val lat1 = Math.toRadians(userLat)
    val lat2 = Math.toRadians(KAABA_LAT)
    val dlng = Math.toRadians(KAABA_LNG - userLng)

    val x = sin(dlng) * cos(lat2)
    val y = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dlng)

    var bearing = Math.toDegrees(atan2(x, y))
    bearing = (bearing + 360) % 360
    return bearing
}

/**
 * Pusula açısını en kısa yoldan yumuşatmak için fark hesaplayan yardımcı.
 */
private fun shortestAngleDiff(from: Float, to: Float): Float {
    var diff = (to - from) % 360f
    if (diff > 180f) diff -= 360f
    if (diff < -180f) diff += 360f
    return diff
}

@Composable
fun QiblaTab(
    isDarkTheme: Boolean,
    selectedLanguage: String
) {
    fun t(tr: String, en: String, de: String = "", ar: String = "") = when (selectedLanguage) {
        "en" -> en; "de" -> de.ifEmpty { en }; "ar" -> ar.ifEmpty { en }; else -> tr
    }

    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val tertiary = MaterialTheme.colorScheme.tertiary
    val bg = MaterialTheme.colorScheme.background
    val cardColor = if (isDarkTheme) DarkCard else LightCard

    // ── State ──
    var compassDegree by remember { mutableFloatStateOf(0f) }
    var qiblaDegree by remember { mutableFloatStateOf(0f) }
    var hasLocation by remember { mutableStateOf(false) }
    var hasCompass by remember { mutableStateOf(false) }
    var locationPermissionGranted by remember { mutableStateOf(false) }
    var userLat by remember { mutableDoubleStateOf(0.0) }
    var userLng by remember { mutableDoubleStateOf(0.0) }

    // Smooth animation for compass
    var targetCompassDegree by remember { mutableFloatStateOf(0f) }
    var displayedDegree by remember { mutableFloatStateOf(0f) }

    // Update displayed degree with shortest path smoothing
    LaunchedEffect(targetCompassDegree) {
        displayedDegree += shortestAngleDiff(displayedDegree, targetCompassDegree)
    }

    val animatedCompass by animateFloatAsState(
        targetValue = displayedDegree,
        animationSpec = tween(durationMillis = 300),
        label = "compass"
    )

    // ── İzin kontrolü ──
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    // İlk çalıştırmada izin kontrol et
    LaunchedEffect(Unit) {
        locationPermissionGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!locationPermissionGranted) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // ── Sensör dinleyicisi ──
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        hasCompass = accelerometer != null && magnetometer != null

        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var hasGravity = false
        var hasMagnetic = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        // Low-pass filter
                        for (i in 0..2) gravity[i] = gravity[i] * 0.8f + event.values[i] * 0.2f
                        hasGravity = true
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        for (i in 0..2) geomagnetic[i] = geomagnetic[i] * 0.8f + event.values[i] * 0.2f
                        hasMagnetic = true
                    }
                }

                if (hasGravity && hasMagnetic) {
                    val r = FloatArray(9)
                    val i = FloatArray(9)
                    if (SensorManager.getRotationMatrix(r, i, gravity, geomagnetic)) {
                        val orientation = FloatArray(3)
                        SensorManager.getOrientation(r, orientation)
                        val azimuthRad = orientation[0]
                        var azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
                        azimuthDeg = (azimuthDeg + 360f) % 360f
                        compassDegree = azimuthDeg
                        targetCompassDegree = azimuthDeg
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (hasCompass) {
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    // ── Konum dinleyicisi ──
    DisposableEffect(locationPermissionGranted) {
        var locationListenerRef: LocationListener? = null
        var locationManagerRef: LocationManager? = null

        if (locationPermissionGranted) {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locationManagerRef = locationManager

            val locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    userLat = location.latitude
                    userLng = location.longitude
                    qiblaDegree = calculateQiblaDirection(userLat, userLng).toFloat()
                    hasLocation = true
                }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
                @Deprecated("Deprecated") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }
            locationListenerRef = locationListener

            try {
                // Son bilinen konumu kullan
                val lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (lastKnown != null) {
                    userLat = lastKnown.latitude
                    userLng = lastKnown.longitude
                    qiblaDegree = calculateQiblaDirection(userLat, userLng).toFloat()
                    hasLocation = true
                }

                // GPS güncelleme
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, 5000L, 10f, locationListener
                    )
                }
                // Network güncelleme
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER, 5000L, 10f, locationListener
                    )
                }
            } catch (_: SecurityException) { }
        }

        onDispose {
            val lm = locationManagerRef
            val ll = locationListenerRef
            if (lm != null && ll != null) {
                try { lm.removeUpdates(ll) } catch (_: Exception) { }
            }
        }
    }

    // Kıble yönü (pusula açısı düşüldüğünde)
    val qiblaRelative = (qiblaDegree - animatedCompass + 360f) % 360f
    val isPointingQibla = hasLocation && (qiblaRelative < 5f || qiblaRelative > 355f)

    // ── UI ──
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = t("Kıble Pusulası", "Qibla Compass", "Qibla-Kompass", "بوصلة القبلة"),
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = onSurface
        )

        if (!hasCompass) {
            // Pusula sensörü yok
            Spacer(modifier = Modifier.weight(1f))
            Text("🧭", fontSize = 56.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                t("Bu cihazda pusula sensörü bulunamadı.", "Compass sensor not found on this device.", "Kompasssensor auf diesem Gerät nicht gefunden.", "لم يتم العثور على مستشعر البوصلة."),
                color = onSurfaceVariant,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
        } else if (!locationPermissionGranted) {
            // Konum izni verilmedi
            Spacer(modifier = Modifier.weight(1f))
            Text("📍", fontSize = 56.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                t("Kıble yönünü hesaplamak için konum iznine ihtiyaç var.", "Location permission is required to calculate Qibla direction.", "Standortberechtigung wird für die Qibla-Berechnung benötigt.", "إذن الموقع مطلوب لحساب اتجاه القبلة."),
                color = onSurfaceVariant,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }) {
                Text(t("İzin Ver", "Grant Permission", "Erlauben", "منح الإذن"))
            }
            Spacer(modifier = Modifier.weight(1f))
        } else {
            // Derece bilgisi
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (hasLocation) "${animatedCompass.toInt()}°" else "---",
                fontSize = 16.sp,
                color = onSurfaceVariant
            )

            Spacer(modifier = Modifier.weight(0.2f))

            // ── Pusula ──
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(320.dp)
            ) {
                Canvas(modifier = Modifier.size(300.dp)) {
                    drawCompass(
                        compassRotation = -animatedCompass,
                        qiblaAngle = if (hasLocation) qiblaRelative else null,
                        isPointingQibla = isPointingQibla,
                        primary = primary,
                        tertiary = tertiary,
                        surfaceVariant = surfaceVariant,
                        onSurface = onSurface,
                        onSurfaceVariant = onSurfaceVariant,
                        isDarkTheme = isDarkTheme
                    )
                }

                // Kıble ok üstünde Kabe emoji
                if (hasLocation) {
                    // Merkez noktası göstergesi
                    Surface(
                        shape = CircleShape,
                        color = if (isPointingQibla) Gold else cardColor,
                        shadowElevation = 4.dp,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "🕋",
                                fontSize = 28.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.15f))

            // Durum kartı
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isPointingQibla) Gold.copy(alpha = 0.15f) else cardColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isPointingQibla) {
                        Text(
                            text = t("Kıble Yönündesiniz ✓", "Facing Qibla ✓", "Qibla-Richtung ✓", "أنت في اتجاه القبلة ✓"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Gold
                        )
                    } else if (hasLocation) {
                        Text(
                            text = t("Kıble Yönü: ${qiblaDegree.toInt()}°", "Qibla Direction: ${qiblaDegree.toInt()}°", "Qibla-Richtung: ${qiblaDegree.toInt()}°", "اتجاه القبلة: ${qiblaDegree.toInt()}°"),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = t("Cihazı Kıble yönüne çevirin", "Turn device toward Qibla", "Gerät Richtung Qibla drehen", "وجّه الجهاز نحو القبلة"),
                            fontSize = 13.sp,
                            color = onSurfaceVariant
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = t("Konum alınıyor...", "Getting location...", "Standort wird ermittelt...", "جاري تحديد الموقع..."),
                            fontSize = 14.sp,
                            color = onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.15f))
        }
    }
}


// ── Canvas çizim fonksiyonu ──
private fun DrawScope.drawCompass(
    compassRotation: Float,
    qiblaAngle: Float?,
    isPointingQibla: Boolean,
    primary: Color,
    tertiary: Color,
    surfaceVariant: Color,
    onSurface: Color,
    onSurfaceVariant: Color,
    isDarkTheme: Boolean
) {
    val cx = size.width / 2
    val cy = size.height / 2
    val radius = size.minDimension / 2 - 16f

    // Dış halka
    drawCircle(
        color = surfaceVariant,
        radius = radius + 8f,
        center = Offset(cx, cy),
        style = Stroke(width = 2f)
    )

    rotate(compassRotation) {
        // Derece çizgileri
        for (i in 0 until 360 step 5) {
            val isCardinal = i % 90 == 0
            val isMajor = i % 30 == 0
            val lineLength = when {
                isCardinal -> 24f
                isMajor -> 16f
                else -> 8f
            }
            val lineWidth = when {
                isCardinal -> 3f
                isMajor -> 2f
                else -> 1f
            }
            val lineColor = when {
                isCardinal -> onSurface
                isMajor -> onSurfaceVariant
                else -> onSurfaceVariant.copy(alpha = 0.4f)
            }

            val angle = Math.toRadians(i.toDouble()).toFloat()
            val startR = radius - lineLength
            val endR = radius

            drawLine(
                color = lineColor,
                start = Offset(cx + startR * sin(angle), cy - startR * cos(angle)),
                end = Offset(cx + endR * sin(angle), cy - endR * cos(angle)),
                strokeWidth = lineWidth,
                cap = StrokeCap.Round
            )
        }

        // Yön harfleri
        val textPaint = android.graphics.Paint().apply {
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            textSize = 20f
            isFakeBoldText = true
        }

        val directions = listOf(
            Triple(0f, "N", Color(0xFFE74C3C)),    // Kuzey: Kırmızı
            Triple(90f, "E", onSurfaceVariant),
            Triple(180f, "S", onSurfaceVariant),
            Triple(270f, "W", onSurfaceVariant)
        )

        for ((angle, label, color) in directions) {
            val rad = Math.toRadians(angle.toDouble()).toFloat()
            val textR = radius - 40f
            val tx = cx + textR * sin(rad)
            val ty = cy - textR * cos(rad) + 8f

            textPaint.color = android.graphics.Color.argb(
                (color.alpha * 255).toInt(),
                (color.red * 255).toInt(),
                (color.green * 255).toInt(),
                (color.blue * 255).toInt()
            )
            drawContext.canvas.nativeCanvas.drawText(label, tx, ty, textPaint)
        }

        // Kuzey ok ucu (küçük kırmızı üçgen)
        val northArrowPath = Path().apply {
            val arrowTipY = cy - radius + 2f
            moveTo(cx, arrowTipY)
            lineTo(cx - 8f, arrowTipY + 18f)
            lineTo(cx + 8f, arrowTipY + 18f)
            close()
        }
        drawPath(northArrowPath, Color(0xFFE74C3C))
    }

    // ── Kıble iğnesi (pusula dönüşünden bağımsız) ──
    if (qiblaAngle != null) {
        val qiblaRad = Math.toRadians(qiblaAngle.toDouble()).toFloat()
        val arrowColor = if (isPointingQibla) Gold else primary

        // Dış ok çizgi
        val arrowStartR = 50f
        val arrowEndR = radius - 50f

        drawLine(
            color = arrowColor,
            start = Offset(cx + arrowStartR * sin(qiblaRad), cy - arrowStartR * cos(qiblaRad)),
            end = Offset(cx + arrowEndR * sin(qiblaRad), cy - arrowEndR * cos(qiblaRad)),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )

        // Ok ucu
        val tipR = arrowEndR + 4f
        val tipX = cx + tipR * sin(qiblaRad)
        val tipY = cy - tipR * cos(qiblaRad)

        val wingAngle1 = qiblaRad - 0.3f
        val wingAngle2 = qiblaRad + 0.3f
        val wingR = arrowEndR - 16f

        val arrowPath = Path().apply {
            moveTo(tipX, tipY)
            lineTo(cx + wingR * sin(wingAngle1), cy - wingR * cos(wingAngle1))
            lineTo(cx + wingR * sin(wingAngle2), cy - wingR * cos(wingAngle2))
            close()
        }
        drawPath(arrowPath, arrowColor)

        // Karşı tarafta ince çizgi
        val oppositeRad = qiblaRad + Math.PI.toFloat()
        drawLine(
            color = arrowColor.copy(alpha = 0.3f),
            start = Offset(cx + arrowStartR * sin(oppositeRad), cy - arrowStartR * cos(oppositeRad)),
            end = Offset(cx + (arrowEndR * 0.6f) * sin(oppositeRad), cy - (arrowEndR * 0.6f) * cos(oppositeRad)),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
    }

    // Merkez noktası
    drawCircle(
        color = surfaceVariant,
        radius = 36f,
        center = Offset(cx, cy)
    )
}
