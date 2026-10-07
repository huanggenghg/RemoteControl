package com.lumostech.autoclick.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.lumostech.autoclick.R

/** XML and Compose share the same palette, including service-owned overlays. */
@Composable
fun AutoclickTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = colorResource(R.color.ac_primary),
            onPrimary = colorResource(R.color.ac_on_primary),
            primaryContainer = colorResource(R.color.ac_soft),
            onPrimaryContainer = colorResource(R.color.ac_primary_pressed),
            secondary = colorResource(R.color.ac_primary),
            background = colorResource(R.color.ac_background),
            onBackground = colorResource(R.color.ac_text),
            surface = colorResource(R.color.ac_surface),
            onSurface = colorResource(R.color.ac_text),
            surfaceVariant = colorResource(R.color.ac_soft),
            onSurfaceVariant = colorResource(R.color.ac_text_secondary),
            outline = colorResource(R.color.ac_border),
            outlineVariant = colorResource(R.color.ac_border),
            error = colorResource(R.color.ac_error)
        ),
        typography = Typography,
        shapes = Shapes(
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(24.dp)
        ),
        content = content
    )
}
