package com.yasergirit.zikirmasterpro

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.yasergirit.zikirmasterpro.ui.theme.*
import kotlin.math.sqrt

// Renkli geometrik desen renkleri
private val PatternOrange = Color(0xFFE8734A)
private val PatternTeal = Color(0xFF2AA9B0)
private val PatternNavy = Color(0xFF1B2838)
private val PatternPink = Color(0xFFE8A0B4)
private val PatternYellow = Color(0xFFF5C842)
private val PatternCoral = Color(0xFFE85D4A)
private val PatternMint = Color(0xFF5CD6B8)
private val PatternDarkTeal = Color(0xFF0E7C66)

@Composable
fun OnboardingScreen(
    selectedLanguage: String,
    onLanguageChange: (String) -> Unit = {},
    onFinish: () -> Unit
) {
    fun t(tr: String, en: String, de: String = "", ar: String = "") = when (selectedLanguage) {
        "en" -> en; "de" -> de.ifEmpty { en }; "ar" -> ar.ifEmpty { en }; else -> tr
    }

    val context = LocalContext.current

    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    var showNotificationSettings by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationGranted) showNotificationSettings = true
    }

    var agreedToTerms by remember { mutableStateOf(true) }

    // Konum izni verildiyse bildirim ayarları ekranını göster
    if (showNotificationSettings) {
        NotificationSettingsScreen(
            selectedLanguage = selectedLanguage,
            onFinish = onFinish
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PatternNavy)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Üst: Geometrik desen ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.38f)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawGeometricPattern()
                }
            }

            // ── Alt: Beyaz kart ──
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.62f),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(48.dp))

                    // Selam mesajı
                    Text(
                        text = t("Es Selamu Aleyküm", "As Salaam Alaikum", "As Salaam Alaikum", "السلام عليكم"),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldDark,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Uygulama ismi
                    Text(
                        text = "Sofi App",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF333333),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(36.dp))

                    // Dil seçici (dropdown)
                    var languageMenuExpanded by remember { mutableStateOf(false) }
                    val languages = listOf(
                        "tr" to "Türkçe",
                        "en" to "English",
                        "de" to "Deutsch",
                        "ar" to "العربية"
                    )

                    Box {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { languageMenuExpanded = true },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF2F2F2)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 18.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = languages.firstOrNull { it.first == selectedLanguage }?.second ?: "Türkçe",
                                    fontSize = 16.sp,
                                    color = Color(0xFF333333)
                                )
                                Text(
                                    text = if (languageMenuExpanded) "‹" else "›",
                                    fontSize = 24.sp,
                                    color = Color(0xFF999999)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = languageMenuExpanded,
                            onDismissRequest = { languageMenuExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.7f)
                        ) {
                            languages.forEach { (code, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = label,
                                            fontWeight = if (code == selectedLanguage) FontWeight.Bold else FontWeight.Normal,
                                            color = if (code == selectedLanguage) Emerald else Color(0xFF333333)
                                        )
                                    },
                                    onClick = {
                                        onLanguageChange(code)
                                        languageMenuExpanded = false
                                    },
                                    trailingIcon = {
                                        if (code == selectedLanguage) {
                                            Text("✓", color = Emerald, fontSize = 16.sp)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Kullanım şartları onay
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = agreedToTerms,
                            onCheckedChange = { agreedToTerms = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Emerald,
                                checkmarkColor = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = t(
                                "Uygulamanın Kullanım Şartlarını ve Gizlilik Politikasını kabul ediyorum.",
                                "I agree to the Terms of Use and the Privacy Policy of the Application.",
                                "Ich stimme den Nutzungsbedingungen und der Datenschutzrichtlinie zu.",
                                "أوافق على شروط الاستخدام وسياسة الخصوصية."
                            ),
                            fontSize = 13.sp,
                            color = Color(0xFF666666),
                            lineHeight = 18.sp
                        )
                    }

                    // Konum butonu
                    Button(
                        onClick = {
                            if (locationGranted) {
                                showNotificationSettings = true
                            } else {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        },
                        enabled = agreedToTerms,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Emerald,
                            disabledContainerColor = Emerald.copy(alpha = 0.4f)
                        )
                    ) {
                        Text(
                            text = if (locationGranted)
                                t("BAŞLA", "GET STARTED", "STARTEN", "ابدأ")
                            else
                                t("KONUMUMU BUL", "FIND MY LOCATION", "STANDORT FINDEN", "البحث عن موقعي"),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

/**
 * Canvas'a renkli İslami geometrik desen çizer.
 * Görseldeki gibi daireler, kareler, elmaslar ve geometrik şekillerden oluşur.
 */
private fun DrawScope.drawGeometricPattern() {
    val w = size.width
    val h = size.height
    val cellW = w / 5f
    val cellH = h / 3f

    // Arka plan
    drawRect(PatternNavy)

    // ── Satır 1 ──
    // Büyük turuncu daire (sol üst)
    drawCircle(PatternOrange, radius = cellW * 0.7f, center = Offset(cellW * 0.8f, cellH * 0.5f))
    // Küçük beyaz hilal
    drawCircle(Color.White, radius = cellW * 0.25f, center = Offset(cellW * 0.3f, cellH * 0.8f))
    // Teal daire (orta)
    drawCircle(PatternTeal, radius = cellW * 0.55f, center = Offset(cellW * 2f, cellH * 0.4f))
    // Küçük turuncu daire (orta üst)
    drawCircle(PatternOrange, radius = cellW * 0.2f, center = Offset(cellW * 2.8f, cellH * 0.2f))
    // Coral dikdörtgen
    drawRoundRect(PatternCoral, Offset(cellW * 3f, 0f), Size(cellW * 1.2f, cellH * 0.9f), CornerRadius(12f))
    // Pembe kare (sağ)
    drawRoundRect(PatternPink, Offset(cellW * 3.5f, cellH * 0.3f), Size(cellW * 0.6f, cellW * 0.6f), CornerRadius(8f))
    // Sarı daire (sağ üst köşe)
    drawCircle(PatternYellow, radius = cellW * 0.45f, center = Offset(w - cellW * 0.3f, cellH * 0.3f))
    // Teal büyük daire (sağ üst)
    drawCircle(PatternTeal, radius = cellW * 0.6f, center = Offset(w - cellW * 0.7f, cellH * 0.1f))

    // ── Satır 2 ──
    // Turuncu elmas (sol)
    val diamondCx = cellW * 0.5f
    val diamondCy = cellH * 1.5f
    val dSize = cellW * 0.35f
    drawPath(Path().apply {
        moveTo(diamondCx, diamondCy - dSize)
        lineTo(diamondCx + dSize, diamondCy)
        lineTo(diamondCx, diamondCy + dSize)
        lineTo(diamondCx - dSize, diamondCy)
        close()
    }, PatternOrange)
    // Küçük beyaz elmas (sol iç)
    val ds = dSize * 0.4f
    drawPath(Path().apply {
        moveTo(diamondCx, diamondCy - ds)
        lineTo(diamondCx + ds, diamondCy)
        lineTo(diamondCx, diamondCy + ds)
        lineTo(diamondCx - ds, diamondCy)
        close()
    }, Color.White)

    // Yeşil daire (sol-orta)
    drawCircle(PatternDarkTeal, radius = cellW * 0.5f, center = Offset(cellW * 1.5f, cellH * 1.3f))
    // Sarı daire (orta)
    drawCircle(PatternYellow, radius = cellW * 0.55f, center = Offset(cellW * 2.5f, cellH * 1.5f))
    // Turuncu daire (orta)
    drawCircle(PatternOrange, radius = cellW * 0.35f, center = Offset(cellW * 2.5f, cellH * 1.5f))
    // Teal daire (sağ-orta)
    drawCircle(PatternTeal, radius = cellW * 0.45f, center = Offset(cellW * 3.5f, cellH * 1.6f))
    // Pembe blok (sağ)
    drawRoundRect(PatternPink, Offset(cellW * 4f, cellH * 1.1f), Size(cellW, cellH * 0.8f), CornerRadius(12f))
    // Küçük sarı yaprak (sağ)
    drawCircle(PatternYellow, radius = cellW * 0.15f, center = Offset(cellW * 4.5f, cellH * 1.3f))

    // ── Satır 3 ──
    // Koyu daire (sol alt)
    drawCircle(PatternNavy.copy(alpha = 0.7f), radius = cellW * 0.5f, center = Offset(cellW * 0.5f, cellH * 2.5f))
    // Turuncu daire (sol alt)
    drawCircle(PatternOrange, radius = cellW * 0.3f, center = Offset(cellW * 1.2f, cellH * 2.3f))
    // Coral daire
    drawCircle(PatternCoral, radius = cellW * 0.25f, center = Offset(cellW * 1.8f, cellH * 2.6f))
    // Mint yeşil alan
    drawRoundRect(PatternMint, Offset(cellW * 2.2f, cellH * 2.1f), Size(cellW * 1.5f, cellH * 0.7f), CornerRadius(16f))
    // Sarı daire (sağ alt)
    drawCircle(PatternYellow, radius = cellW * 0.3f, center = Offset(cellW * 3.8f, cellH * 2.4f))
    // Teal daire (sağ alt köşe)
    drawCircle(PatternTeal, radius = cellW * 0.4f, center = Offset(w - cellW * 0.4f, cellH * 2.6f))
    // Navy daire (sağ alt)
    drawCircle(PatternNavy, radius = cellW * 0.55f, center = Offset(w - cellW * 0.2f, cellH * 2.2f))
}
