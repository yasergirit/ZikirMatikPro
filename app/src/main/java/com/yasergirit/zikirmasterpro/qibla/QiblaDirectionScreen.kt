package com.yasergirit.zikirmasterpro.qibla

import android.Manifest
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
import android.os.Build
import android.os.Bundle
import android.view.Surface as ViewSurface
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val KAABA_LATITUDE = 21.422487
private const val KAABA_LONGITUDE = 39.826206
private const val FACING_QIBLA_TOLERANCE = 5f
private const val HEADING_SMOOTHING = 0.18f
private const val SENSOR_FILTER_ALPHA = 0.96f

@Composable
fun QiblaDirectionScreen(
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
    val background = MaterialTheme.colorScheme.background
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val cardColor = if (isDarkTheme) DarkCard else LightCard

    var permissionGranted by remember { mutableStateOf(hasLocationPermission(context)) }
    var locationEnabled by remember { mutableStateOf(isLocationEnabled(context)) }
    var location by remember { mutableStateOf<Location?>(null) }
    var magneticHeading by remember { mutableStateOf<Float?>(null) }
    var displayedHeading by remember { mutableFloatStateOf(0f) }
    var sensorAvailable by remember { mutableStateOf(true) }
    var sensorAccuracy by remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        locationEnabled = isLocationEnabled(context)
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

    LaunchedEffect(magneticHeading) {
        val heading = magneticHeading ?: return@LaunchedEffect
        displayedHeading += shortestAngleDifference(displayedHeading, heading)
    }

    val animatedHeading by animateFloatAsState(
        targetValue = displayedHeading,
        animationSpec = tween(durationMillis = 180),
        label = "qibla-direction-heading"
    )

    DisposableEffect(permissionGranted, locationEnabled) {
        var locationManagerRef: LocationManager? = null
        var listenerRef: LocationListener? = null

        if (permissionGranted && locationEnabled) {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locationManagerRef = locationManager
            location = getBestKnownLocation(locationManager)

            val listener = object : LocationListener {
                override fun onLocationChanged(newLocation: Location) {
                    location = newLocation
                }

                override fun onProviderEnabled(provider: String) {
                    locationEnabled = true
                }

                override fun onProviderDisabled(provider: String) {
                    locationEnabled = isLocationEnabled(context)
                }

                @Deprecated("Deprecated in Android")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            }
            listenerRef = listener

            try {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2_000L, 1f, listener)
                }
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2_000L, 1f, listener)
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
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val hasMagneticCompass = accelerometer != null && magnetometer != null
        sensorAvailable = hasMagneticCompass || rotationVector != null

        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        val rotationMatrix = FloatArray(9)
        val remappedMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        var hasGravity = false
        var hasGeomagnetic = false
        var smoothedHeading: Float? = null

        fun publishHeading(rawHeading: Float) {
            val current = smoothedHeading
            smoothedHeading = if (current == null) {
                rawHeading
            } else {
                normalizeDegrees(current + shortestAngleDifference(current, rawHeading) * HEADING_SMOOTHING)
            }
            magneticHeading = smoothedHeading
        }

        fun publishMatrix(matrix: FloatArray) {
            val (axisX, axisY) = rotationAxes(displayRotation(context))
            SensorManager.remapCoordinateSystem(matrix, axisX, axisY, remappedMatrix)
            SensorManager.getOrientation(remappedMatrix, orientation)
            publishHeading(normalizeDegrees(Math.toDegrees(orientation[0].toDouble()).toFloat()))
        }

        fun updateMagneticHeading() {
            if (!hasGravity || !hasGeomagnetic) return
            if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                publishMatrix(rotationMatrix)
            }
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        lowPass(event.values, gravity, hasGravity)
                        hasGravity = true
                        updateMagneticHeading()
                    }

                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        lowPass(event.values, geomagnetic, hasGeomagnetic)
                        hasGeomagnetic = true
                        updateMagneticHeading()
                    }

                    Sensor.TYPE_ROTATION_VECTOR -> {
                        if (!hasMagneticCompass) {
                            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                            publishMatrix(rotationMatrix)
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD || sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
                    sensorAccuracy = accuracy
                }
            }
        }

        if (hasMagneticCompass) {
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            sensorManager.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_GAME)
        } else if (rotationVector != null) {
            sensorManager.registerListener(listener, rotationVector, SensorManager.SENSOR_DELAY_GAME)
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    val heading = magneticHeading?.let { normalizeDegrees(animatedHeading) }
    val qiblaTrueBearing = location?.let { calculateQiblaBearing(it.latitude, it.longitude) }
    val qiblaCompassBearing = qiblaTrueBearing?.let { normalizeDegrees(it - magneticDeclination(location)) }
    val qiblaRelativeAngle = if (heading != null && qiblaCompassBearing != null) {
        normalizeDegrees(qiblaCompassBearing - heading)
    } else {
        0f
    }
    val turnAmount = if (qiblaRelativeAngle > 180f) 360f - qiblaRelativeAngle else qiblaRelativeAngle
    val turnRight = qiblaRelativeAngle <= 180f
    val isFacingQibla = heading != null && qiblaCompassBearing != null && turnAmount <= FACING_QIBLA_TOLERANCE
    val isReady = sensorAvailable && permissionGranted && locationEnabled && location != null && heading != null
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
        Text(
            text = t("Kabe Yönü", "Kaaba Direction", "Kaaba-Richtung", "اتجاه الكعبة"),
            color = onSurface,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = t(
                "Namazdan önce sarı oku takip edin",
                "Follow the gold arrow before prayer",
                "Folgen Sie vor dem Gebet dem goldenen Pfeil",
                "اتبع السهم الذهبي قبل الصلاة"
            ),
            color = onSurfaceVariant,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(18.dp))

        when {
            !sensorAvailable -> QiblaStateMessage(
                icon = "🧭",
                title = t("Pusula sensörü yok", "No compass sensor", "Kein Kompasssensor", "لا يوجد مستشعر بوصلة"),
                message = t(
                    "Bu cihaz canlı Kabe yönü göstermek için gerekli sensöre sahip değil.",
                    "This device does not have the sensor needed to show live Kaaba direction.",
                    "Dieses Gerät hat keinen Sensor für die Live-Kaaba-Richtung.",
                    "هذا الجهاز لا يحتوي على المستشعر المطلوب لعرض اتجاه الكعبة."
                ),
                onSurface = onSurface,
                onSurfaceVariant = onSurfaceVariant
            )

            !permissionGranted -> QiblaPermissionMessage(
                title = t("Konum izni gerekli", "Location permission required", "Standortberechtigung erforderlich", "إذن الموقع مطلوب"),
                message = t(
                    "Kabe yönünü bulunduğunuz konuma göre hesaplamak için konum izni verin.",
                    "Grant location permission to calculate the Kaaba direction from your position.",
                    "Erlauben Sie den Standort, um die Kaaba-Richtung zu berechnen.",
                    "امنح إذن الموقع لحساب اتجاه الكعبة من موقعك."
                ),
                buttonText = t("İzin Ver", "Grant Permission", "Erlauben", "منح الإذن"),
                onSurface = onSurface,
                onSurfaceVariant = onSurfaceVariant,
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

            !locationEnabled -> QiblaStateMessage(
                icon = "📍",
                title = t("Konum kapalı", "Location is off", "Standort ist aus", "الموقع مغلق"),
                message = t(
                    "Kabe yönünü hesaplamak için cihaz konumunu açın.",
                    "Turn on device location to calculate the Kaaba direction.",
                    "Aktivieren Sie den Standort, um die Kaaba-Richtung zu berechnen.",
                    "شغّل الموقع لحساب اتجاه الكعبة."
                ),
                onSurface = onSurface,
                onSurfaceVariant = onSurfaceVariant
            )

            else -> {
                Box(
                    modifier = Modifier.size(326.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(316.dp)) {
                        drawQiblaDirectionCompass(
                            compassRotation = normalizeDegrees(-(heading ?: 0f)),
                            qiblaArrowAngle = qiblaRelativeAngle,
                            isReady = isReady,
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
                        modifier = Modifier.size(82.dp),
                        shape = CircleShape,
                        color = if (isFacingQibla) GoldLight else cardColor,
                        shadowElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("🕋", fontSize = 30.sp)
                            Text(
                                text = if (isReady) "${turnAmount.roundToInt()}°" else "...",
                                color = if (isFacingQibla) Color(0xFF2A2110) else onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                QiblaStatusPanel(
                    isReady = isReady,
                    isFacingQibla = isFacingQibla,
                    turnRight = turnRight,
                    turnAmount = turnAmount,
                    qiblaBearing = qiblaCompassBearing,
                    heading = heading,
                    accuracyText = accuracyText,
                    cardColor = cardColor,
                    primary = primary,
                    onSurface = onSurface,
                    onSurfaceVariant = onSurfaceVariant,
                    t = ::t
                )
            }
        }
    }
}

@Composable
private fun QiblaStateMessage(
    icon: String,
    title: String,
    message: String,
    onSurface: Color,
    onSurfaceVariant: Color
) {
    Spacer(modifier = Modifier.height(64.dp))
    Text(icon, fontSize = 54.sp)
    Spacer(modifier = Modifier.height(14.dp))
    Text(title, color = onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = message,
        color = onSurfaceVariant,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 18.dp)
    )
}

@Composable
private fun QiblaPermissionMessage(
    title: String,
    message: String,
    buttonText: String,
    onSurface: Color,
    onSurfaceVariant: Color,
    buttonColor: Color,
    onClick: () -> Unit
) {
    QiblaStateMessage(
        icon = "📍",
        title = title,
        message = message,
        onSurface = onSurface,
        onSurfaceVariant = onSurfaceVariant
    )
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
private fun QiblaStatusPanel(
    isReady: Boolean,
    isFacingQibla: Boolean,
    turnRight: Boolean,
    turnAmount: Float,
    qiblaBearing: Float?,
    heading: Float?,
    accuracyText: String,
    cardColor: Color,
    primary: Color,
    onSurface: Color,
    onSurfaceVariant: Color,
    t: (String, String, String, String) -> String
) {
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
            if (!isReady) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp, color = primary)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = t("Yön hazırlanıyor...", "Preparing direction...", "Richtung wird vorbereitet...", "جاري تجهيز الاتجاه..."),
                    color = onSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = t("Telefonu düz tutun", "Hold the phone flat", "Halten Sie das Telefon flach", "أمسك الهاتف بشكل مستو"),
                    color = onSurfaceVariant,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            } else if (isFacingQibla) {
                Text(
                    text = t("Kabe yönündesiniz", "You are facing the Kaaba", "Sie schauen zur Kaaba", "أنت باتجاه الكعبة"),
                    color = Gold,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = t("Namaza hazırsınız", "You are ready for prayer", "Sie sind bereit zum Gebet", "أنت مستعد للصلاة"),
                    color = onSurfaceVariant,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = if (turnRight) {
                        t("Sağa ${turnAmount.roundToInt()}° dönün", "Turn right ${turnAmount.roundToInt()}°", "Drehen Sie ${turnAmount.roundToInt()}° nach rechts", "انعطف يميناً ${turnAmount.roundToInt()}°")
                    } else {
                        t("Sola ${turnAmount.roundToInt()}° dönün", "Turn left ${turnAmount.roundToInt()}°", "Drehen Sie ${turnAmount.roundToInt()}° nach links", "انعطف يساراً ${turnAmount.roundToInt()}°")
                    },
                    color = onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = t("Sarı ok Kabe yönünü gösterir", "The gold arrow points to the Kaaba", "Der goldene Pfeil zeigt zur Kaaba", "السهم الذهبي يشير إلى الكعبة"),
                    color = onSurfaceVariant,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QiblaMetric(t("Kabe", "Kaaba", "Kaaba", "الكعبة"), "${qiblaBearing?.roundToInt() ?: 0}°", onSurface, onSurfaceVariant)
                QiblaMetric(t("Yön", "Heading", "Richtung", "الاتجاه"), "${heading?.roundToInt() ?: 0}°", onSurface, onSurfaceVariant)
                QiblaMetric(t("Sensör", "Sensor", "Sensor", "المستشعر"), accuracyText, onSurface, onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun QiblaMetric(
    label: String,
    value: String,
    onSurface: Color,
    onSurfaceVariant: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = onSurfaceVariant, fontSize = 11.sp)
        Text(text = value, color = onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private fun DrawScope.drawQiblaDirectionCompass(
    compassRotation: Float,
    qiblaArrowAngle: Float,
    isReady: Boolean,
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
                if (isDarkTheme) Color(0xFF183229) else Color.White,
                cardColor,
                surfaceVariant.copy(alpha = 0.7f)
            ),
            center = center,
            radius = radius + 22f
        ),
        radius = radius + 12f,
        center = center
    )
    drawCircle(color = primary.copy(alpha = 0.3f), radius = radius + 8f, center = center, style = Stroke(width = 3f))
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
            val tickWidth = when {
                isCardinal -> 3.8f
                isMajor -> 2.2f
                else -> 1.1f
            }
            val angle = Math.toRadians(degree.toDouble()).toFloat()
            val startRadius = radius - tickLength

            drawLine(
                color = when {
                    degree == 0 -> Color(0xFFE05A47)
                    isCardinal -> onSurface.copy(alpha = 0.78f)
                    isMajor -> onSurfaceVariant.copy(alpha = 0.72f)
                    else -> onSurfaceVariant.copy(alpha = 0.34f)
                },
                start = Offset(center.x + startRadius * sin(angle), center.y - startRadius * cos(angle)),
                end = Offset(center.x + radius * sin(angle), center.y - radius * cos(angle)),
                strokeWidth = tickWidth,
                cap = StrokeCap.Round
            )
        }
        drawCompassLabels(center, radius, onSurfaceVariant)
    }

    if (isReady) {
        rotate(qiblaArrowAngle, pivot = center) {
            val arrowEnd = radius - 52f
            val arrowStart = 58f
            val arrowColor = if (isFacingQibla) GoldLight else Gold

            drawLine(
                color = arrowColor.copy(alpha = 0.32f),
                start = Offset(center.x, center.y + arrowStart),
                end = Offset(center.x, center.y + arrowEnd * 0.56f),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = arrowColor,
                start = Offset(center.x, center.y - arrowStart),
                end = Offset(center.x, center.y - arrowEnd),
                strokeWidth = 8f,
                cap = StrokeCap.Round
            )

            val tip = Path().apply {
                moveTo(center.x, center.y - arrowEnd - 18f)
                lineTo(center.x - 19f, center.y - arrowEnd + 22f)
                lineTo(center.x + 19f, center.y - arrowEnd + 22f)
                close()
            }
            drawPath(tip, arrowColor)
        }
    }

    drawCircle(color = cardColor, radius = 46f, center = center)
    drawCircle(
        color = if (isFacingQibla) Gold.copy(alpha = 0.76f) else primary.copy(alpha = 0.24f),
        radius = 46f,
        center = center,
        style = Stroke(width = 3f)
    )
}

private fun DrawScope.drawCompassLabels(
    center: Offset,
    radius: Float,
    labelColor: Color
) {
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
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
        val textRadius = radius - 50f
        drawContext.canvas.nativeCanvas.drawText(
            label,
            center.x + textRadius * sin(angle),
            center.y - textRadius * cos(angle) + 9f,
            paint
        )
    }
}

private fun lowPass(input: FloatArray, output: FloatArray, initialized: Boolean) {
    for (index in input.indices) {
        output[index] = if (initialized) {
            output[index] * SENSOR_FILTER_ALPHA + input[index] * (1f - SENSOR_FILTER_ALPHA)
        } else {
            input[index]
        }
    }
}

private fun rotationAxes(rotation: Int): Pair<Int, Int> = when (rotation) {
    ViewSurface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
    ViewSurface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
    ViewSurface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
}

private fun displayRotation(context: Context): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display?.rotation ?: ViewSurface.ROTATION_0
    } else {
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
    }
}

private fun calculateQiblaBearing(latitude: Double, longitude: Double): Float {
    val userLat = Math.toRadians(latitude)
    val kaabaLat = Math.toRadians(KAABA_LATITUDE)
    val longitudeDiff = Math.toRadians(KAABA_LONGITUDE - longitude)
    val x = sin(longitudeDiff) * cos(kaabaLat)
    val y = cos(userLat) * sin(kaabaLat) - sin(userLat) * cos(kaabaLat) * cos(longitudeDiff)

    return normalizeDegrees(Math.toDegrees(atan2(x, y)).toFloat())
}

private fun magneticDeclination(location: Location?): Float {
    if (location == null) return 0f
    return GeomagneticField(
        location.latitude.toFloat(),
        location.longitude.toFloat(),
        location.altitude.toFloat(),
        System.currentTimeMillis()
    ).declination
}

private fun shortestAngleDifference(from: Float, to: Float): Float {
    var diff = normalizeDegrees(to) - normalizeDegrees(from)
    if (diff > 180f) diff -= 360f
    if (diff < -180f) diff += 360f
    return diff
}

private fun normalizeDegrees(value: Float): Float {
    var normalized = value % 360f
    if (normalized < 0f) normalized += 360f
    return normalized
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).roundToInt(),
    (red * 255).roundToInt(),
    (green * 255).roundToInt(),
    (blue * 255).roundToInt()
)

private fun hasLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

private fun isLocationEnabled(context: Context): Boolean {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}

private fun getBestKnownLocation(locationManager: LocationManager): Location? {
    return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }
}
