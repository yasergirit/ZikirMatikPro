package com.yasergirit.zikirmasterpro.qibla

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
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
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Surface
import android.view.WindowManager
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
import com.yasergirit.zikirmasterpro.ui.theme.LightCard
import kotlin.math.abs
import kotlin.math.roundToInt

// Kabe'nin kesin koordinatları
private const val KAABA_LATITUDE = 21.422487
private const val KAABA_LONGITUDE = 39.826206

@Composable
fun QiblaDirectionScreen(
    isDarkTheme: Boolean,
    selectedLanguage: String
) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.background
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val cardColor = if (isDarkTheme) DarkCard else LightCard

    fun t(tr: String, en: String, de: String = "", ar: String = "") = when (selectedLanguage) {
        "en" -> en
        "de" -> de.ifEmpty { en }
        "ar" -> ar.ifEmpty { en }
        else -> tr
    }

    // Ekranı dikey kullanıma sabitle
    DisposableEffect(context) {
        val activity = context as? Activity
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            if (previousOrientation != null) {
                activity.requestedOrientation = previousOrientation
            }
        }
    }

    // İzin ve Durum Değişkenleri
    var hasLocationPermission by remember { mutableStateOf(checkLocationPermission(context)) }
    var isLocationEnabled by remember { mutableStateOf(checkLocationEnabled(context)) }
    var userLocation by remember { mutableStateOf<Location?>(null) }
    var trueHeading by remember { mutableStateOf<Float?>(null) }
    var sensorAvailable by remember { mutableStateOf(true) }
    var sensorAccuracy by remember { mutableStateOf(SensorManager.SENSOR_STATUS_UNRELIABLE) }

    // Titreşim
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    // İzin İsteyici
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        isLocationEnabled = checkLocationEnabled(context)
    }

    // İzin yoksa hemen iste
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Konum Dinleyicisi
    DisposableEffect(hasLocationPermission, isLocationEnabled) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var listener: LocationListener? = null

        if (hasLocationPermission && isLocationEnabled) {
            userLocation = getBestLastLocation(locationManager)
            listener = object : LocationListener {
                override fun onLocationChanged(loc: Location) { userLocation = loc }
                override fun onProviderEnabled(provider: String) { isLocationEnabled = true }
                override fun onProviderDisabled(provider: String) { isLocationEnabled = checkLocationEnabled(context) }
                @Deprecated("Deprecated in Android")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }
            try {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 1f, listener)
                }
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 1f, listener)
                }
            } catch (e: SecurityException) {
                hasLocationPermission = false
            }
        }
        onDispose {
            listener?.let { locationManager.removeUpdates(it) }
        }
    }

    // Pusula Sensör Dinleyicisi (Sıfırdan yazılmış temiz mantık, Eğilim Tespiti - Tilt Compensation ile)
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        
        // Eğim düzeltmeli (Tilt-compensated) eski ama en stabil compass sensörü.
        @Suppress("DEPRECATION")
        val orientationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION)

        if (orientationSensor == null) {
            sensorAvailable = false
            return@DisposableEffect onDispose {}
        }

        var lastHeading = 0f

        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // TYPE_ORIENTATION doğrudan 0-360 arası manyetik azimut değerini verir
                // ve telefonun dikey/yatay eğimini donanımsal olarak hesaba katar.
                val azimuthDegrees = event.values[0]
                val normalizedAzimuth = (azimuthDegrees + 360) % 360

                // Düşük geçişli filtre (Yumuşatıcı)
                val diff = shortestAngleDiff(lastHeading, normalizedAzimuth)
                val smoothedAzimuth = (lastHeading + diff * 0.2f + 360) % 360
                lastHeading = smoothedAzimuth

                // Gerçek kuzeyi bulmak için manyetik sapmayı (declination) ekle
                val declination = if (userLocation != null) {
                    GeomagneticField(
                        userLocation!!.latitude.toFloat(),
                        userLocation!!.longitude.toFloat(),
                        userLocation!!.altitude.toFloat(),
                        System.currentTimeMillis()
                    ).declination
                } else 0f

                trueHeading = (smoothedAzimuth + declination + 360) % 360
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                sensorAccuracy = accuracy
            }
        }

        sensorManager.registerListener(sensorListener, orientationSensor, SensorManager.SENSOR_DELAY_UI)

        onDispose {
            sensorManager.unregisterListener(sensorListener)
        }
    }

    // Hesaplamalar
    val kaabaLocation = remember { Location("").apply { latitude = KAABA_LATITUDE; longitude = KAABA_LONGITUDE } }
    val qiblaBearing = userLocation?.bearingTo(kaabaLocation)?.let { (it + 360) % 360 } ?: 0f
    val currentHeading = trueHeading ?: 0f

    // Animasyonlu Açılar
    val animatedHeading by animateFloatAsState(targetValue = currentHeading, animationSpec = tween(150), label = "heading")

    val qiblaRelativeAngle = (qiblaBearing - animatedHeading + 360) % 360
    
    // Kabe'ye dönük mü? (Hassasiyet payı 3 derece)
    val diffToQibla = shortestAngleDiff(currentHeading, qiblaBearing)
    val isFacingQibla = abs(diffToQibla) <= 3f
    val isReady = hasLocationPermission && isLocationEnabled && userLocation != null && trueHeading != null

    // Titreşim Mantığı
    LaunchedEffect(isFacingQibla) {
        if (isFacingQibla && isReady) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(50)
                }
            } catch (e: Exception) {
                // Ignore vibration errors
            }
        }
    }

    // Arayüz
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = t("Kıble Bulucu", "Qibla Finder", "Qibla-Finder", "الباحث عن القبلة"),
            color = onSurface,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = t("Sarı ok tepe noktasıyla eşleştiğinde Kıble'desiniz", "When the gold arrow matches the top, you are facing Qibla", "Wenn der goldene Pfeil oben ist, schauen Sie zur Qibla", "عندما يتطابق السهم الذهبي مع الأعلى، فأنت تواجه القبلة"),
            color = onSurfaceVariant,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(30.dp))

        if (!sensorAvailable) {
            ErrorCard(icon = "🧭", text = t("Cihazınızda pusula sensörü bulunmuyor.", "Compass sensor not found on this device.", "Kompasssensor nicht gefunden.", "لم يتم العثور على مستشعر البوصلة."), cardColor, onSurface)
        } else if (!hasLocationPermission) {
            ErrorCard(icon = "📍", text = t("Kıbleyi hesaplamak için konum izni gerekli.", "Location permission required to calculate Qibla.", "Standortberechtigung erforderlich.", "إذن الموقع مطلوب لحساب القبلة."), cardColor, onSurface)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) {
                Text(t("İzin Ver", "Grant Permission", "Erlauben", "منح الإذن"))
            }
        } else if (!isLocationEnabled) {
            ErrorCard(icon = "🌍", text = t("Konum servisleri kapalı.", "Location services are disabled.", "Standortdienste sind deaktiviert.", "خدمات الموقع معطلة."), cardColor, onSurface)
        } else if (!isReady) {
            CircularProgressIndicator(color = primary, modifier = Modifier.padding(40.dp))
            Text(t("Hesaplanıyor...", "Calculating...", "Berechnung...", "جاري الحساب..."), color = onSurface)
        } else {
            // Pusula Çizimi
            Box(
                modifier = Modifier.size(300.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = size.width / 2 - 20f

                    // Dış Çember
                    drawCircle(color = cardColor, radius = radius, center = center)
                    drawCircle(color = if (isFacingQibla) Gold else primary.copy(alpha = 0.3f), radius = radius, center = center, style = Stroke(width = 4f))

                    // Sabit Tepe Çizgisi (Telefonun Baktığı Yön)
                    drawLine(
                        color = if (isFacingQibla) Gold else onSurface,
                        start = Offset(center.x, center.y - radius + 10f),
                        end = Offset(center.x, center.y - radius - 20f),
                        strokeWidth = 8f,
                        cap = StrokeCap.Round
                    )

                    // Dönen Kadran (Kuzey, Güney, Doğu, Batı)
                    rotate(-animatedHeading, center) {
                        // Derece çizgileri
                        for (i in 0 until 360 step 15) {
                            val angleRad = Math.toRadians(i.toDouble()).toFloat()
                            val lineLength = if (i % 90 == 0) 20f else 10f
                            val strokeWidth = if (i % 90 == 0) 4f else 2f
                            val color = if (i == 0) Color.Red else onSurfaceVariant.copy(alpha = 0.5f)
                            
                            drawLine(
                                color = color,
                                start = Offset(center.x, center.y - radius + 10f),
                                end = Offset(center.x, center.y - radius + 10f + lineLength),
                                strokeWidth = strokeWidth
                            )
                        }

                        // Yazılar
                        val paint = android.graphics.Paint().apply {
                            color = onSurfaceVariant.toArgb()
                            textSize = 40f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isFakeBoldText = true
                        }
                        drawContext.canvas.nativeCanvas.apply {
                            drawText("N", center.x, center.y - radius + 70f, paint.apply { color = android.graphics.Color.RED })
                            drawText("S", center.x, center.y + radius - 40f, paint.apply { color = onSurfaceVariant.toArgb() })
                            drawText("E", center.x + radius - 40f, center.y + 15f, paint)
                            drawText("W", center.x - radius + 40f, center.y + 15f, paint)
                        }
                    }

                    // Kabe Oku (Sadece Kabe yönünü gösterir)
                    rotate(qiblaRelativeAngle, center) {
                        val arrowColor = if (isFacingQibla) Gold else primary
                        val path = Path().apply {
                            moveTo(center.x, center.y - radius + 40f)
                            lineTo(center.x - 30f, center.y)
                            lineTo(center.x, center.y - 20f)
                            lineTo(center.x + 30f, center.y)
                            close()
                        }
                        drawPath(path, arrowColor)
                        
                        // Alt çizgi
                        drawLine(
                            color = arrowColor.copy(alpha = 0.5f),
                            start = Offset(center.x, center.y - 20f),
                            end = Offset(center.x, center.y + radius - 60f),
                            strokeWidth = 6f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Merkez Nokta
                Surface(
                    shape = CircleShape,
                    color = cardColor,
                    modifier = Modifier.size(60.dp),
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🕋", fontSize = 28.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Durum Paneli
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = cardColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isFacingQibla) {
                        Text(t("Doğru Yöndesiniz", "You are facing Qibla", "Sie schauen zur Qibla", "أنت تواجه القبلة"), color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    } else {
                        val turnDirection = if (qiblaRelativeAngle > 180) t("Sola", "Left", "Links", "يسار") else t("Sağa", "Right", "Rechts", "يمين")
                        val degreesToTurn = if (qiblaRelativeAngle > 180) 360 - qiblaRelativeAngle else qiblaRelativeAngle
                        Text("${degreesToTurn.roundToInt()}° $turnDirection Dönün", color = onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        InfoItem(t("Kıble", "Qibla", "Qibla", "القبلة"), "${qiblaBearing.roundToInt()}°", onSurface, onSurfaceVariant)
                        InfoItem(t("Yön", "Heading", "Richtung", "الاتجاه"), "${currentHeading.roundToInt()}°", onSurface, onSurfaceVariant)
                        val accText = if (sensorAccuracy >= SensorManager.SENSOR_STATUS_ACCURACY_HIGH) "İyi" else "Kalibre Et"
                        InfoItem(t("Sensör", "Sensor", "Sensor", "المستشعر"), accText, onSurface, onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoItem(label: String, value: String, onSurface: Color, onSurfaceVariant: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = onSurfaceVariant, fontSize = 12.sp)
        Text(value, color = onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ErrorCard(icon: String, text: String, cardColor: Color, onSurface: Color) {
    Surface(color = cardColor, shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 40.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(text, color = onSurface, textAlign = TextAlign.Center)
        }
    }
}

// Yardımcı Fonksiyonlar
private fun shortestAngleDiff(from: Float, to: Float): Float {
    var diff = to - from
    while (diff < -180f) diff += 360f
    while (diff > 180f) diff -= 360f
    return diff
}

private fun checkLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

private fun checkLocationEnabled(context: Context): Boolean {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}

private fun getBestLastLocation(locationManager: LocationManager): Location? {
    return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).roundToInt(),
    (red * 255).roundToInt(),
    (green * 255).roundToInt(),
    (blue * 255).roundToInt()
)
