package com.yasergirit.zikirmasterpro.qibla

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.yasergirit.zikirmasterpro.ui.theme.DarkCard
import com.yasergirit.zikirmasterpro.ui.theme.Gold
import com.yasergirit.zikirmasterpro.ui.theme.GoldLight
import com.yasergirit.zikirmasterpro.ui.theme.LightCard
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val FACING_QIBLA_TOLERANCE = 4f
private const val SENSOR_SMOOTHING = 0.18f

@Composable
fun QiblaCompassScreen(
    isDarkTheme: Boolean,
    selectedLanguage: String
) {
    fun t(tr: String, en: String, de: String = "", ar: String = "") = when (selectedLanguage) {
        "en" -> en
        "de" -> de.ifEmpty { en }
        "ar" -> ar.ifEmpty { en }
        else -> tr
    }

    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val background = MaterialTheme.colorScheme.background
    val cardColor = if (isDarkTheme) DarkCard else LightCard

    var permissionGranted by remember { mutableStateOf(hasLocationPermission(context)) }
    var locationEnabled by remember { mutableStateOf(isDeviceLocationEnabled(context)) }
    var location by remember { mutableStateOf<Location?>(null) }
    var magneticHeading by remember { mutableStateOf<Float?>(null) }
    var sensorAvailable by remember { mutableStateOf(true) }
    var sensorAccuracy by remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        locationEnabled = isDeviceLocationEnabled(context)
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            locationEnabled = isDeviceLocationEnabled(context)
            location = getBestKnownLocation(context)
        }
    }

    DisposableEffect(permissionGranted, locationEnabled) {
        var locationManagerRef: LocationManager? = null
        var listenerRef: LocationListener? = null

        if (permissionGranted && locationEnabled) {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locationManagerRef = locationManager

            val listener = object : LocationListener {
                override fun onLocationChanged(newLocation: Location) {
                    location = newLocation
                }

                override fun onProviderEnabled(provider: String) {
                    locationEnabled = true
                }

                override fun onProviderDisabled(provider: String) {
                    locationEnabled = isDeviceLocationEnabled(context)
                }

                @Deprecated("Deprecated in Android")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            }
            listenerRef = listener

            try {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2_500L, 1f, listener)
                }
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2_500L, 1f, listener)
                }
            } catch (_: SecurityException) {
                permissionGranted = false
            }
        }

        onDispose {
            val locationManager = locationManagerRef
            val listener = listenerRef
            if (locationManager != null && listener != null) {
                runCatching { locationManager.removeUpdates(listener) }
            }
        }
    }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        sensorAvailable = rotationVector != null || (accelerometer != null && magnetometer != null)

        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var hasGravity = false
        var hasGeomagnetic = false
        var smoothedHeading: Float? = null

        fun publishHeading(raw: Float) {
            val current = smoothedHeading
            smoothedHeading = if (current == null) {
                raw
            } else {
                QiblaCalculator.normalizeDegrees(
                    current + shortestAngleDifference(current, raw) * SENSOR_SMOOTHING
                )
            }
            magneticHeading = smoothedHeading
        }

        fun headingFromRotationMatrix(matrix: FloatArray): Float {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(matrix, orientation)
            return QiblaCalculator.normalizeDegrees(
                Math.toDegrees(orientation[0].toDouble()).toFloat()
            )
        }

        fun updateFallbackHeading() {
            if (!hasGravity || !hasGeomagnetic) return
            val matrix = FloatArray(9)
            if (SensorManager.getRotationMatrix(matrix, null, gravity, geomagnetic)) {
                publishHeading(headingFromRotationMatrix(matrix))
            }
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        val matrix = FloatArray(9)
                        SensorManager.getRotationMatrixFromVector(matrix, event.values)
                        publishHeading(headingFromRotationMatrix(matrix))
                    }

                    Sensor.TYPE_ACCELEROMETER -> {
                        lowPass(event.values, gravity)
                        hasGravity = true
                        updateFallbackHeading()
                    }

                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        lowPass(event.values, geomagnetic)
                        hasGeomagnetic = true
                        updateFallbackHeading()
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD || sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
                    sensorAccuracy = accuracy
                }
            }
        }

        if (rotationVector != null) {
            sensorManager.registerListener(listener, rotationVector, SensorManager.SENSOR_DELAY_GAME)
        } else {
            if (accelerometer != null) {
                sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            }
            if (magnetometer != null) {
                sensorManager.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_GAME)
            }
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    val trueHeading = magneticHeading?.let { toTrueNorth(it, location) }
    val qiblaBearing = location?.let { QiblaCalculator.calculateQiblaBearing(it.latitude, it.longitude) }
    val qiblaArrowAngle = if (qiblaBearing != null && trueHeading != null) {
        QiblaCalculator.relativeQiblaAngle(qiblaBearing, trueHeading)
    } else {
        0f
    }
    val turnAmount = if (qiblaArrowAngle > 180f) 360f - qiblaArrowAngle else qiblaArrowAngle
    val turnRight = qiblaArrowAngle <= 180f
    val isFacingQibla = qiblaBearing != null && trueHeading != null && turnAmount <= FACING_QIBLA_TOLERANCE
    val isReady = sensorAvailable && permissionGranted && locationEnabled && qiblaBearing != null && trueHeading != null
    val accuracyText = when (sensorAccuracy) {
        SensorManager.SENSOR_STATUS_UNRELIABLE -> t("Kalibre edin", "Calibrate", "Kalibrieren", "عاير")
        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> t("Düşük", "Low", "Niedrig", "منخفض")
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> t("Orta", "Medium", "Mittel", "متوسط")
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> t("Yüksek", "High", "Hoch", "مرتفع")
        else -> t("Bekleniyor", "Waiting", "Warten", "انتظار")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            !sensorAvailable -> QiblaInfoState(
                icon = "🧭",
                title = t("Pusula sensörü yok", "No compass sensor", "Kein Kompasssensor", "لا يوجد مستشعر بوصلة"),
                message = t(
                    "Bu cihaz Kıble pusulasını canlı çalıştırmak için gerekli sensöre sahip değil.",
                    "This device does not have the sensor needed for a live Qibla compass.",
                    "Dieses Gerät hat keinen Sensor für einen Live-Qibla-Kompass.",
                    "هذا الجهاز لا يحتوي على المستشعر المطلوب لبوصلة القبلة."
                ),
                color = onSurfaceVariant
            )

            !permissionGranted -> QiblaPermissionState(
                title = t("Konum izni gerekli", "Location permission required", "Standortberechtigung erforderlich", "إذن الموقع مطلوب"),
                message = t(
                    "Kabe yönünü hesaplamak için konum izni verin.",
                    "Grant location permission to calculate the Kaaba direction.",
                    "Erlauben Sie den Standort, um die Kaaba-Richtung zu berechnen.",
                    "امنح إذن الموقع لحساب اتجاه الكعبة."
                ),
                buttonText = t("İzin Ver", "Grant Permission", "Erlauben", "منح الإذن"),
                color = onSurfaceVariant,
                buttonColor = primary,
                onClick = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            )

            !locationEnabled -> QiblaInfoState(
                icon = "📍",
                title = t("Konum kapalı", "Location is off", "Standort ist aus", "الموقع مغلق"),
                message = t(
                    "Kıble yönünü hesaplamak için cihaz konumunu açın.",
                    "Turn on device location to calculate the Qibla direction.",
                    "Aktivieren Sie den Standort, um die Qibla-Richtung zu berechnen.",
                    "شغّل الموقع لحساب اتجاه القبلة."
                ),
                color = onSurfaceVariant
            )

            !isReady -> {
                Spacer(modifier = Modifier.height(96.dp))
                CircularProgressIndicator(modifier = Modifier.size(44.dp), color = primary, strokeWidth = 3.dp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = t("Kıble hazırlanıyor...", "Preparing Qibla...", "Qibla wird vorbereitet...", "جاري تجهيز القبلة..."),
                    color = onSurfaceVariant,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }

            else -> {
                Text(
                    text = if (isFacingQibla) {
                        t("Kıble yönündesiniz", "You are facing Qibla", "Sie sind in Qibla-Richtung", "أنت في اتجاه القبلة")
                    } else if (turnRight) {
                        t("Sağa ${turnAmount.roundToInt()}° dönün", "Turn right ${turnAmount.roundToInt()}°", "Drehen Sie ${turnAmount.roundToInt()}° nach rechts", "انعطف يميناً ${turnAmount.roundToInt()}°")
                    } else {
                        t("Sola ${turnAmount.roundToInt()}° dönün", "Turn left ${turnAmount.roundToInt()}°", "Drehen Sie ${turnAmount.roundToInt()}° nach links", "انعطف يساراً ${turnAmount.roundToInt()}°")
                    },
                    color = if (isFacingQibla) Gold else onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = t(
                        "Sarı ok her zaman Kabe yönünü gösterir",
                        "The yellow arrow always points to the Kaaba",
                        "Der gelbe Pfeil zeigt immer zur Kaaba",
                        "السهم الأصفر يشير دائماً إلى الكعبة"
                    ),
                    color = onSurfaceVariant,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(18.dp))

                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(324.dp)) {
                    Canvas(modifier = Modifier.size(314.dp)) {
                        drawKaabaCompass(
                            compassRotation = QiblaCalculator.normalizeDegrees(-(trueHeading ?: 0f)),
                            qiblaArrowAngle = qiblaArrowAngle,
                            isFacingQibla = isFacingQibla,
                            primary = primary,
                            cardColor = cardColor,
                            surfaceVariant = surfaceVariant,
                            onSurface = onSurface,
                            onSurfaceVariant = onSurfaceVariant,
                            isDarkTheme = isDarkTheme
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = if (isFacingQibla) GoldLight else cardColor,
                        shadowElevation = 8.dp,
                        modifier = Modifier.size(76.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("🕋", fontSize = 28.sp)
                            Text(
                                "${turnAmount.roundToInt()}°",
                                color = if (isFacingQibla) Color(0xFF2A2110) else onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isFacingQibla) Gold.copy(alpha = 0.16f) else cardColor,
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isFacingQibla) {
                                t("Namaza hazırsınız", "You are aligned for prayer", "Sie sind zum Gebet ausgerichtet", "أنت مستعد للصلاة")
                            } else {
                                t("Telefonu düz tutun ve yavaşça dönün", "Hold the phone flat and turn slowly", "Halten Sie das Telefon flach und drehen Sie sich langsam", "أمسك الهاتف بشكل مستو ودر ببطء")
                            },
                            color = onSurfaceVariant,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            CompassMetric(t("Kıble", "Qibla", "Qibla", "القبلة"), "${qiblaBearing?.roundToInt() ?: 0}°", onSurface, onSurfaceVariant)
                            CompassMetric(t("Yön", "Heading", "Richtung", "الاتجاه"), "${trueHeading?.roundToInt() ?: 0}°", onSurface, onSurfaceVariant)
                            CompassMetric(t("Sensör", "Sensor", "Sensor", "المستشعر"), accuracyText, onSurface, onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QiblaInfoState(
    icon: String,
    title: String,
    message: String,
    color: Color
) {
    Spacer(modifier = Modifier.height(80.dp))
    Text(icon, fontSize = 54.sp)
    Spacer(modifier = Modifier.height(12.dp))
    Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(modifier = Modifier.height(8.dp))
    Text(message, color = color, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun QiblaPermissionState(
    title: String,
    message: String,
    buttonText: String,
    color: Color,
    buttonColor: Color,
    onClick: () -> Unit
) {
    QiblaInfoState(icon = "📍", title = title, message = message, color = color)
    Spacer(modifier = Modifier.height(18.dp))
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
    ) {
        Text(buttonText)
    }
}

@Composable
private fun CompassMetric(
    label: String,
    value: String,
    onSurface: Color,
    onSurfaceVariant: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = onSurfaceVariant, fontSize = 11.sp)
        Text(value, color = onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
    }
}

private fun DrawScope.drawKaabaCompass(
    compassRotation: Float,
    qiblaArrowAngle: Float,
    isFacingQibla: Boolean,
    primary: Color,
    cardColor: Color,
    surfaceVariant: Color,
    onSurface: Color,
    onSurfaceVariant: Color,
    isDarkTheme: Boolean
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension / 2f - 18f

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                if (isDarkTheme) Color(0xFF193229) else Color.White,
                cardColor,
                surfaceVariant.copy(alpha = 0.68f)
            ),
            center = center,
            radius = radius + 22f
        ),
        center = center,
        radius = radius + 12f
    )
    drawCircle(color = primary.copy(alpha = 0.32f), radius = radius + 8f, center = center, style = Stroke(width = 3f))
    drawCircle(color = onSurfaceVariant.copy(alpha = 0.14f), radius = radius - 34f, center = center, style = Stroke(width = 1.5f))

    rotate(compassRotation, pivot = center) {
        for (degree in 0 until 360 step 5) {
            val isCardinal = degree % 90 == 0
            val isMajor = degree % 30 == 0
            val tickLength = when {
                isCardinal -> 28f
                isMajor -> 17f
                else -> 8f
            }
            val angle = Math.toRadians(degree.toDouble()).toFloat()
            val startRadius = radius - tickLength

            drawLine(
                color = when {
                    isCardinal -> onSurface.copy(alpha = 0.78f)
                    isMajor -> onSurfaceVariant.copy(alpha = 0.72f)
                    else -> onSurfaceVariant.copy(alpha = 0.34f)
                },
                start = Offset(center.x + startRadius * sin(angle), center.y - startRadius * cos(angle)),
                end = Offset(center.x + radius * sin(angle), center.y - radius * cos(angle)),
                strokeWidth = if (isCardinal) 3.8f else if (isMajor) 2.2f else 1.1f,
                cap = StrokeCap.Round
            )
        }
        drawDialLabels(center, radius, onSurfaceVariant)
    }

    rotate(qiblaArrowAngle, pivot = center) {
        val arrowStart = 58f
        val arrowEnd = radius - 54f
        val arrowColor = if (isFacingQibla) GoldLight else Gold

        drawLine(
            color = arrowColor,
            start = Offset(center.x, center.y - arrowStart),
            end = Offset(center.x, center.y - arrowEnd),
            strokeWidth = 8f,
            cap = StrokeCap.Round
        )

        val head = Path().apply {
            moveTo(center.x, center.y - arrowEnd - 18f)
            lineTo(center.x - 20f, center.y - arrowEnd + 23f)
            lineTo(center.x + 20f, center.y - arrowEnd + 23f)
            close()
        }
        drawPath(head, arrowColor)
    }

    drawCircle(color = cardColor, radius = 44f, center = center)
    drawCircle(
        color = if (isFacingQibla) Gold.copy(alpha = 0.75f) else primary.copy(alpha = 0.24f),
        radius = 44f,
        center = center,
        style = Stroke(width = 3f)
    )
}

private fun DrawScope.drawDialLabels(
    center: Offset,
    radius: Float,
    labelColor: Color
) {
    val paint = android.graphics.Paint().apply {
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
        textSize = 23f
        isFakeBoldText = true
        color = labelColor.toArgb()
    }

    listOf(
        0f to "N",
        90f to "E",
        180f to "S",
        270f to "W"
    ).forEach { (degree, label) ->
        val angle = Math.toRadians(degree.toDouble()).toFloat()
        val textRadius = radius - 49f
        drawContext.canvas.nativeCanvas.drawText(
            label,
            center.x + textRadius * sin(angle),
            center.y - textRadius * cos(angle) + 9f,
            paint
        )
    }

    paint.textSize = 17f
    paint.isFakeBoldText = false
    paint.color = labelColor.copy(alpha = 0.62f).toArgb()
    listOf(30, 60, 120, 150, 210, 240, 300, 330).forEach { degree ->
        val angle = Math.toRadians(degree.toDouble()).toFloat()
        val textRadius = radius - 46f
        drawContext.canvas.nativeCanvas.drawText(
            degree.toString(),
            center.x + textRadius * sin(angle),
            center.y - textRadius * cos(angle) + 7f,
            paint
        )
    }
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).roundToInt(),
    (red * 255).roundToInt(),
    (green * 255).roundToInt(),
    (blue * 255).roundToInt()
)

private fun lowPass(input: FloatArray, output: FloatArray) {
    val alpha = 0.86f
    for (index in input.indices) {
        output[index] = output[index] * alpha + input[index] * (1f - alpha)
    }
}

private fun shortestAngleDifference(from: Float, to: Float): Float {
    var diff = QiblaCalculator.normalizeDegrees(to) - QiblaCalculator.normalizeDegrees(from)
    if (diff > 180f) diff -= 360f
    if (diff < -180f) diff += 360f
    return diff
}

private fun toTrueNorth(magneticHeading: Float, location: Location?): Float {
    if (location == null) return QiblaCalculator.normalizeDegrees(magneticHeading)

    val field = GeomagneticField(
        location.latitude.toFloat(),
        location.longitude.toFloat(),
        location.altitude.toFloat(),
        System.currentTimeMillis()
    )
    return QiblaCalculator.normalizeDegrees(magneticHeading + field.declination)
}

private fun hasLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

private fun isDeviceLocationEnabled(context: Context): Boolean {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}

@SuppressLint("MissingPermission")
private fun getBestKnownLocation(context: Context): Location? {
    if (!hasLocationPermission(context)) return null

    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val lastProviderLocation = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }

    return lastProviderLocation
}
