package com.nothing.glyphdraw

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont

val AmoledBlack = Color(0xFF000000)
val DarkSurface = Color(0xFF121212)
val SegmentOffColor = Color(0xFF1C1C1C)
val SegmentOffBorder = Color(0xFF2E2E2E)
val SegmentOnColor = Color(0xFFFFFFFF)
val ButtonDarkGray = Color(0xFF262626)

val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val googleSansFont = GoogleFont("Google Sans")

val GoogleSans = FontFamily(
    Font(googleFont = googleSansFont, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = googleSansFont, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = googleSansFont, fontProvider = fontProvider, weight = FontWeight.Bold)
)

private val DarkColorScheme = darkColorScheme(
    primary = SegmentOnColor,
    background = AmoledBlack,
    surface = DarkSurface,
    onPrimary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun GlyphDrawTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
