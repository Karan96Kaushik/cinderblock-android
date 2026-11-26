package com.bayonetbarontech.cinderblock.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bayonetbarontech.cinderblock.ui.nav.MenuButton
import com.bayonetbarontech.cinderblock.ui.theme.LocalColors
import com.bayonetbarontech.cinderblock.ui.theme.LocalType
import kotlinx.coroutines.delay

@Composable
fun DisplayText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 16.sp,
    color: Color = LocalColors.current.foreground,
    weight: FontWeight = FontWeight.Bold,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    val type = LocalType.current
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontFamily = type.display,
        fontWeight = weight,
        fontSize = size,
        textAlign = align,
        maxLines = maxLines,
        lineHeight = size * 1.15f,
    )
}

@Composable
fun Mono(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 12.sp,
    color: Color = LocalColors.current.mutedForeground,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign? = null,
    decoration: TextDecoration? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    val type = LocalType.current
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontFamily = type.body,
        fontWeight = weight,
        fontSize = size,
        textAlign = align,
        textDecoration = decoration,
        maxLines = maxLines,
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    loading: Boolean = false,
) {
    val colors = LocalColors.current
    val container = when {
        !enabled -> colors.muted
        destructive -> colors.destructive
        else -> colors.neonOrange
    }
    val content = if (enabled) colors.primaryForeground else colors.mutedForeground
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(container, RoundedCornerShape(26.dp))
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = content, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        } else {
            DisplayText(text, color = content, size = 15.sp, weight = FontWeight.Bold, align = TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalColors.current.neonOrange,
) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        DisplayText(text, color = color, size = 15.sp, weight = FontWeight.Bold, align = TextAlign.Center, maxLines = 2)
    }
}

@Composable
fun SectionHeader(label: String) {
    DisplayText(label, size = 18.sp, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp))
}

@Composable
fun Pill(text: String, color: Color) {
    Mono(
        text,
        color = color,
        size = 11.sp,
        weight = FontWeight.Bold,
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(99.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun FeatureBar(
    title: String,
    onBack: () -> Unit,
    date: String? = null,
    trailing: String? = null,
    chevron: Boolean = false,
    end: @Composable (() -> Unit)? = null,
) {
    val colors = LocalColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.foreground)
        }
        Column(Modifier.weight(1f)) {
            DisplayText(title.asHeading(), size = 18.sp, maxLines = 1)
            if (date != null) Mono(date, size = 12.sp, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                end != null -> end()
                trailing != null -> Mono(trailing, color = colors.primary, size = 13.sp, modifier = Modifier.padding(end = 4.dp))
            }
            MenuButton()
        }
    }
}

private fun String.asHeading(): String =
    lowercase().split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
    }

@Composable
fun ScreenColumn(
    maxWidth: Dp = 672.dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(modifier.widthIn(max = maxWidth).fillMaxWidth(), content = content)
    }
}

@Composable
fun PulseDot(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(modifier.size(8.dp).alpha(alpha).background(color, RoundedCornerShape(99.dp)))
}

@Composable
fun CardBlock(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalColors.current
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.card, RoundedCornerShape(22.dp))
            .border(1.dp, colors.border.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    return now
}

@Composable
fun ConfirmBlock(prompt: String, confirm: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val colors = LocalColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Mono(prompt, color = colors.foreground, size = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { OutlineButton("Cancel", onCancel) }
            Box(Modifier.weight(1f)) { PrimaryButton(confirm, onConfirm, destructive = true) }
        }
    }
}
