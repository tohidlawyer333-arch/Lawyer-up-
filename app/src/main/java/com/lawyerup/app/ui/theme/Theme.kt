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
private val light=lightColorScheme(primary=Teal,onPrimary=Color.White,secondary=Color(0xFF746249),tertiary=Color(0xFF7862A3),background=Color(0xFFF4F5EF),surface=Color(0xFFFCFDF8),surfaceVariant=Color(0xFFE4EDE5))
private val dark=darkColorScheme(primary=Color(0xFF78D8CB),secondary=Gold,tertiary=Color(0xFFCABDFF),background=Color(0xFF102327),surface=Color(0xFF193236),surfaceVariant=Color(0xFF274448))

@Composable
fun LawyerUpTheme(darkTheme:Boolean=isSystemInDarkTheme(),content:@Composable ()->Unit) {
    val font=FontFamily(Font(R.font.vazirmatn))
    val base=Typography()
    val typography=Typography(
        headlineLarge=base.headlineLarge.copy(fontFamily=font),headlineMedium=base.headlineMedium.copy(fontFamily=font),headlineSmall=base.headlineSmall.copy(fontFamily=font),
        titleLarge=base.titleLarge.copy(fontFamily=font),titleMedium=base.titleMedium.copy(fontFamily=font),titleSmall=base.titleSmall.copy(fontFamily=font),
        bodyLarge=base.bodyLarge.copy(fontFamily=font),bodyMedium=base.bodyMedium.copy(fontFamily=font),bodySmall=base.bodySmall.copy(fontFamily=font),
        labelLarge=base.labelLarge.copy(fontFamily=font),labelMedium=base.labelMedium.copy(fontFamily=font),labelSmall=base.labelSmall.copy(fontFamily=font))
    MaterialTheme(colorScheme=if(darkTheme)dark else light,typography=typography,shapes=Shapes(small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(20.dp),large=RoundedCornerShape(28.dp)),content=content)
}
