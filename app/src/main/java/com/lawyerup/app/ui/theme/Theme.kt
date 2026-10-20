package com.lawyerup.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.lawyerup.app.R

val Teal=Color(0xFF157C78)
val Ink=Color(0xFF143F46)
val Gold=Color(0xFFE8CCA0)

private fun paletteScheme(choice:String,dark:Boolean):ColorScheme = when(choice) {
    "pink" -> if(dark) darkColorScheme(
        primary=Color(0xFFFFA5CE),onPrimary=Color(0xFF4B0030),primaryContainer=Color(0xFF7D2456),onPrimaryContainer=Color(0xFFFFD8E9),
        secondary=Color(0xFFE5B6D0),onSecondary=Color(0xFF47263B),secondaryContainer=Color(0xFF613C54),onSecondaryContainer=Color(0xFFFFD8EC),
        tertiary=Color(0xFFD6B8FF),onTertiary=Color(0xFF341653),background=Color(0xFF20151D),onBackground=Color(0xFFF4DDE8),
        surface=Color(0xFF20151D),onSurface=Color(0xFFF4DDE8),surfaceVariant=Color(0xFF4B3543),onSurfaceVariant=Color(0xFFE0C2D1),outline=Color(0xFFAA8799)
    ) else lightColorScheme(
        primary=Color(0xFFB52E78),onPrimary=Color.White,primaryContainer=Color(0xFFFFD8E9),onPrimaryContainer=Color(0xFF3E0021),
        secondary=Color(0xFF765066),onSecondary=Color.White,secondaryContainer=Color(0xFFF5D9E8),onSecondaryContainer=Color(0xFF2B1023),
        tertiary=Color(0xFF7950A0),onTertiary=Color.White,background=Color(0xFFFFF6FA),onBackground=Color(0xFF24171F),
        surface=Color(0xFFFFF9FB),onSurface=Color(0xFF24171F),surfaceVariant=Color(0xFFF4E2EB),onSurfaceVariant=Color(0xFF51404A),outline=Color(0xFF85717C)
    )
    "navy" -> if(dark) darkColorScheme(
        primary=Color(0xFFADC6FF),onPrimary=Color(0xFF082D61),primaryContainer=Color(0xFF204880),onPrimaryContainer=Color(0xFFD9E2FF),
        secondary=Color(0xFFBEC7E4),onSecondary=Color(0xFF28334D),secondaryContainer=Color(0xFF3E4964),onSecondaryContainer=Color(0xFFDAE2FF),
        tertiary=Color(0xFFB8CBEF),onTertiary=Color(0xFF17315A),background=Color(0xFF111A2A),onBackground=Color(0xFFE1E7F5),
        surface=Color(0xFF111A2A),onSurface=Color(0xFFE1E7F5),surfaceVariant=Color(0xFF333F54),onSurfaceVariant=Color(0xFFC4CDDF),outline=Color(0xFF8A96AE)
    ) else lightColorScheme(
        primary=Color(0xFF244D91),onPrimary=Color.White,primaryContainer=Color(0xFFD9E2FF),onPrimaryContainer=Color(0xFF071B3C),
        secondary=Color(0xFF53627F),onSecondary=Color.White,secondaryContainer=Color(0xFFDEE5F9),onSecondaryContainer=Color(0xFF111D33),
        tertiary=Color(0xFF476786),onTertiary=Color.White,background=Color(0xFFF4F6FC),onBackground=Color(0xFF161B25),
        surface=Color(0xFFFBFCFF),onSurface=Color(0xFF161B25),surfaceVariant=Color(0xFFE2E7F1),onSurfaceVariant=Color(0xFF444B5A),outline=Color(0xFF747C8D)
    )
    else -> if(dark) darkColorScheme(
        primary=Color(0xFF78D8CB),onPrimary=Color(0xFF003731),primaryContainer=Color(0xFF145D58),onPrimaryContainer=Color(0xFFB8F2E9),
        secondary=Color(0xFFE8CCA0),onSecondary=Color(0xFF3D2E18),secondaryContainer=Color(0xFF59462B),onSecondaryContainer=Color(0xFFFFDFAE),
        tertiary=Color(0xFFCABDFF),onTertiary=Color(0xFF32245B),background=Color(0xFF102327),onBackground=Color(0xFFDDECE9),
        surface=Color(0xFF193236),onSurface=Color(0xFFDDECE9),surfaceVariant=Color(0xFF274448),onSurfaceVariant=Color(0xFFB9D0CC),outline=Color(0xFF78928E)
    ) else lightColorScheme(
        primary=Teal,onPrimary=Color.White,primaryContainer=Color(0xFFB8F2E9),onPrimaryContainer=Color(0xFF00201C),
        secondary=Color(0xFF746249),onSecondary=Color.White,secondaryContainer=Color(0xFFF2E1C7),onSecondaryContainer=Color(0xFF2B2111),
        tertiary=Color(0xFF7862A3),onTertiary=Color.White,background=Color(0xFFF4F5EF),onBackground=Color(0xFF191D1A),
        surface=Color(0xFFFCFDF8),onSurface=Color(0xFF191D1A),surfaceVariant=Color(0xFFE4EDE5),onSurfaceVariant=Color(0xFF444A45),outline=Color(0xFF747A74)
    )
}

@Composable
fun LawyerUpTheme(themeChoice:String="teal",darkTheme:Boolean=isSystemInDarkTheme(),content:@Composable ()->Unit) {
    val font=FontFamily(Font(R.font.vazirmatn))
    val base=Typography()
    val typography=Typography(
        headlineLarge=base.headlineLarge.copy(fontFamily=font),headlineMedium=base.headlineMedium.copy(fontFamily=font),headlineSmall=base.headlineSmall.copy(fontFamily=font),
        titleLarge=base.titleLarge.copy(fontFamily=font),titleMedium=base.titleMedium.copy(fontFamily=font),titleSmall=base.titleSmall.copy(fontFamily=font),
        bodyLarge=base.bodyLarge.copy(fontFamily=font),bodyMedium=base.bodyMedium.copy(fontFamily=font),bodySmall=base.bodySmall.copy(fontFamily=font),
        labelLarge=base.labelLarge.copy(fontFamily=font),labelMedium=base.labelMedium.copy(fontFamily=font),labelSmall=base.labelSmall.copy(fontFamily=font))
    MaterialTheme(colorScheme=paletteScheme(themeChoice,darkTheme),typography=typography,shapes=Shapes(small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(20.dp),large=RoundedCornerShape(28.dp)),content=content)
}
