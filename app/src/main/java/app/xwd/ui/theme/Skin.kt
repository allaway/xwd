package app.xwd.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.xwd.R

/**
 * The app's three visual skins, from the "xwd Skins" design. Same anatomy
 * everywhere — the skins change chrome, type, and tone, not functionality.
 */
enum class Skin(val label: String, val blurb: String) {
    MARGINS("Margins", "Warm analog — cream paper, pencil graphite, italic marginalia."),
    TERMINAL("Terminal", "Monospace phosphor on near-black; amber for anything you can press."),
    OVERPRINT("Overprint", "Two-ink riso zine — federal blue and fluoro pink, overprinting to purple."),
}

val LocalSkin = compositionLocalOf { Skin.MARGINS }

/* ---------- fonts ---------- */

@OptIn(ExperimentalTextApi::class)
private fun vf(res: Int, weight: FontWeight, style: FontStyle = FontStyle.Normal) = Font(
    resId = res,
    weight = weight,
    style = style,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Literata — Margins' bookish serif. */
val LiterataFamily = FontFamily(
    vf(R.font.literata, FontWeight.Normal),
    vf(R.font.literata, FontWeight.Medium),
    vf(R.font.literata, FontWeight.SemiBold),
    vf(R.font.literata, FontWeight.Bold),
    vf(R.font.literata_italic, FontWeight.Normal, FontStyle.Italic),
    vf(R.font.literata_italic, FontWeight.Medium, FontStyle.Italic),
    vf(R.font.literata_italic, FontWeight.SemiBold, FontStyle.Italic),
)

/** JetBrains Mono — Terminal's single mono family. */
val MonoFamily = FontFamily(
    vf(R.font.jetbrains_mono, FontWeight.Normal),
    vf(R.font.jetbrains_mono, FontWeight.Medium),
    vf(R.font.jetbrains_mono, FontWeight.Bold),
)

/** Bricolage Grotesque — Overprint's display grotesque. */
val BricolageFamily = FontFamily(
    vf(R.font.bricolage_grotesque, FontWeight.Normal),
    vf(R.font.bricolage_grotesque, FontWeight.Medium),
    vf(R.font.bricolage_grotesque, FontWeight.SemiBold),
    vf(R.font.bricolage_grotesque, FontWeight.Bold),
    vf(R.font.bricolage_grotesque, FontWeight.ExtraBold),
)

val Skin.fontFamily: FontFamily
    get() = when (this) {
        Skin.MARGINS -> LiterataFamily
        Skin.TERMINAL -> MonoFamily
        Skin.OVERPRINT -> BricolageFamily
    }

/* ---------- per-skin palettes (named straight from the design CSS) ---------- */

/** Margins — warm analog. `.d-margins` in the design. */
object MarginsT {
    val bg = Color(0xFFF3ECDC)
    val card = Color(0xFFFBF6EA)
    val cardBorder = Color(0xFFE2D7BF)
    val ink = Color(0xFF33271B)
    val body = Color(0xFF4C4337)
    val graphite = Color(0xFF5F564A)
    val muted = Color(0xFF8A7C66)
    val faint = Color(0xFFB9AC93)
    val ochre = Color(0xFFC08A2D)
    val ochreDeep = Color(0xFF9A6B14)
    val divider = Color(0xFFDDD2BB)
    val dotted = Color(0xFFC4B79C)
    val keyBorder = Color(0xFFDCD0B6)
    val keyShadow = Color(0xFFD5C8AB)
    val pillBorder = Color(0xFFCFC3A8)
}

/** Terminal — phosphor dark. `.d-term` in the design. */
object TermT {
    val bg = Color(0xFF0B0E0B)
    val panel = Color(0xFF101510)
    val keyBg = Color(0xFF121A12)
    val modeBg = Color(0xFF1A231A)
    val rowDivider = Color(0xFF1A221A)
    val border = Color(0xFF243024)
    val keyBorder = Color(0xFF283528)
    val gridBorder = Color(0xFF3A4D3A)
    val offText = Color(0xFF4A5848)
    val dim = Color(0xFF56715A)
    val muted = Color(0xFF7E937B)
    val green = Color(0xFF7FE08A)
    val glow = Color(0xFF8FE89A)
    val mid = Color(0xFF9DB89A)
    val ink = Color(0xFFBFD0BB)
    val bright = Color(0xFFD9E6D4)
    val amber = Color(0xFFE0B568)
}

/** Overprint — two-ink riso. `.d-riso` in the design. */
object RisoT {
    val bg = Color(0xFFFBF6EC)
    val paper = Color(0xFFFFFDF6)
    val blue = Color(0xFF0A57A8)
    val blueBody = Color(0xFF3E66A0)
    val blueMuted = Color(0xFF6B93C9)
    val blueFaint = Color(0xFF9FB9DC)
    val pink = Color(0xFFFF48B0)
    val pinkDeep = Color(0xFFC2308C)
    val pinkPale = Color(0xFFFFC9E6)
    val purple = Color(0xFF7A3FA8)
    val pinkShadow = Color(0x8CFF48B0) // rgba(255,72,176,.55)
}

/* ---------- crossword grid palette ---------- */

@Immutable
data class GridColors(
    val paper: Color,
    val block: Color,
    val line: Color,
    val selected: Color,
    val word: Color,
    val letter: Color,
    /** Letter color inside the selected cell (Terminal/Overprint invert it). */
    val selectedLetter: Color,
    val number: Color,
    val wrong: Color,
    val revealed: Color,
    /** When set, word cells are filled with halftone dots of this color (riso). */
    val wordHalftone: Color? = null,
)

val Skin.gridColors: GridColors
    get() = when (this) {
        Skin.MARGINS -> GridColors(
            paper = Color(0xFFFDF9EE),
            block = MarginsT.ink,
            line = Color(0xFF9A8B72),
            selected = Color(0xFFF1D38E),
            word = Color(0xFFEBE0C6),
            letter = MarginsT.body,
            selectedLetter = MarginsT.body,
            number = MarginsT.muted,
            wrong = Color(0xFFB3402A),
            revealed = MarginsT.ochreDeep,
        )
        Skin.TERMINAL -> GridColors(
            paper = TermT.panel,
            block = Color(0xFF000000),
            line = Color(0xFF2C3A2C),
            selected = TermT.green,
            word = Color(0xFF1D2B1D),
            letter = TermT.glow,
            selectedLetter = TermT.bg,
            number = TermT.dim,
            wrong = Color(0xFFFF7A66),
            revealed = TermT.amber,
        )
        Skin.OVERPRINT -> GridColors(
            paper = RisoT.paper,
            block = RisoT.blue,
            line = RisoT.blue,
            selected = RisoT.pink,
            word = RisoT.paper,
            letter = RisoT.blue,
            selectedLetter = RisoT.paper,
            number = RisoT.blueMuted,
            wrong = RisoT.pinkDeep,
            revealed = RisoT.purple,
            wordHalftone = RisoT.pink.copy(alpha = 0.55f),
        )
    }

/* ---------- Material bridge ---------- */

/**
 * A Material color scheme approximating each skin, so shared Material
 * surfaces (dialogs, sheets, menus, snackbars, date picker) follow along.
 * The skin chrome itself uses the palettes above directly.
 */
fun Skin.materialColors(): ColorScheme = when (this) {
    Skin.MARGINS -> lightColorScheme(
        primary = MarginsT.ochreDeep,
        onPrimary = MarginsT.card,
        primaryContainer = Color(0xFFF1D38E),
        onPrimaryContainer = MarginsT.ink,
        secondary = MarginsT.graphite,
        onSecondary = MarginsT.card,
        secondaryContainer = Color(0xFFEBE0C6),
        onSecondaryContainer = MarginsT.ink,
        tertiaryContainer = Color(0xFFF1D38E),
        onTertiaryContainer = MarginsT.ink,
        background = MarginsT.bg,
        onBackground = MarginsT.ink,
        surface = MarginsT.card,
        onSurface = MarginsT.ink,
        surfaceVariant = Color(0xFFEBE0C6),
        onSurfaceVariant = MarginsT.muted,
        surfaceContainer = MarginsT.card,
        surfaceContainerHigh = MarginsT.card,
        surfaceContainerHighest = MarginsT.card,
        outline = MarginsT.faint,
        outlineVariant = MarginsT.divider,
    )
    Skin.TERMINAL -> darkColorScheme(
        primary = TermT.green,
        onPrimary = TermT.bg,
        primaryContainer = Color(0xFF1D2B1D),
        onPrimaryContainer = TermT.glow,
        secondary = TermT.amber,
        onSecondary = TermT.bg,
        secondaryContainer = TermT.modeBg,
        onSecondaryContainer = TermT.mid,
        tertiaryContainer = TermT.modeBg,
        onTertiaryContainer = TermT.amber,
        background = TermT.bg,
        onBackground = TermT.ink,
        surface = TermT.panel,
        onSurface = TermT.ink,
        surfaceVariant = TermT.modeBg,
        onSurfaceVariant = TermT.muted,
        surfaceContainer = TermT.panel,
        surfaceContainerHigh = TermT.panel,
        surfaceContainerHighest = TermT.keyBg,
        outline = TermT.keyBorder,
        outlineVariant = TermT.rowDivider,
    )
    Skin.OVERPRINT -> lightColorScheme(
        primary = RisoT.blue,
        onPrimary = RisoT.paper,
        primaryContainer = RisoT.pinkPale,
        onPrimaryContainer = RisoT.blue,
        secondary = RisoT.pinkDeep,
        onSecondary = RisoT.paper,
        secondaryContainer = RisoT.pinkPale,
        onSecondaryContainer = RisoT.blue,
        tertiaryContainer = RisoT.pinkPale,
        onTertiaryContainer = RisoT.blue,
        background = RisoT.bg,
        onBackground = RisoT.blue,
        surface = RisoT.paper,
        onSurface = RisoT.blue,
        surfaceVariant = Color(0xFFEFE7D8),
        onSurfaceVariant = RisoT.blueBody,
        surfaceContainer = RisoT.paper,
        surfaceContainerHigh = RisoT.paper,
        surfaceContainerHighest = RisoT.paper,
        outline = RisoT.blue,
        outlineVariant = RisoT.blueFaint,
    )
}

/** The base Material typography re-set in the skin's font family. */
fun Skin.materialTypography(): Typography {
    val ff = fontFamily
    val base = Typography()
    fun androidx.compose.ui.text.TextStyle.f() = copy(fontFamily = ff)
    return Typography(
        displayLarge = base.displayLarge.f(),
        displayMedium = base.displayMedium.f(),
        displaySmall = base.displaySmall.f(),
        headlineLarge = base.headlineLarge.f(),
        headlineMedium = base.headlineMedium.f(),
        headlineSmall = base.headlineSmall.f(),
        titleLarge = base.titleLarge.f(),
        titleMedium = base.titleMedium.f(),
        titleSmall = base.titleSmall.f(),
        bodyLarge = base.bodyLarge.f(),
        bodyMedium = base.bodyMedium.f(),
        bodySmall = base.bodySmall.f(),
        labelLarge = base.labelLarge.f(),
        labelMedium = base.labelMedium.f(),
        labelSmall = base.labelSmall.f(),
    )
}
