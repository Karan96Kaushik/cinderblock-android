package app.cinderblock.ui.components

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
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
import app.cinderblock.ui.theme.LocalColors
import app.cinderblock.ui.theme.LocalType
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
        fontSize = size * type.scale,
        textAlign = align,
        maxLines = maxLines,
        letterSpacing = 0.6.sp,
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
        fontSize = size * type.scale,
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
            .heightIn(min = 48.dp)
            .background(container, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = content, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        } else {
            Mono(text.uppercase(), color = content, size = 13.sp, weight = FontWeight.Bold, align = TextAlign.Center)
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
            .heightIn(min = 48.dp)
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Mono(text.uppercase(), color = color, size = 13.sp, weight = FontWeight.Bold, align = TextAlign.Center)
    }
}

@Composable
fun SectionHeader(label: String) {
    val colors = LocalColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Box(Modifier.weight(1f).height(1.dp).background(colors.border))
        Mono(label.uppercase(), color = colors.neonOrange, size = 11.sp, modifier = Modifier.padding(horizontal = 10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(colors.border))
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Mono(
        text.uppercase(),
        color = color,
        size = 10.sp,
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(99.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
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
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).clickable(onClick = onBack),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (chevron) {
                androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = colors.foreground)
                Mono(" CINDERBLOCK", color = colors.foreground, size = 12.sp)
            } else {
                Mono("← CINDERBLOCK", color = colors.foreground, size = 12.sp)
            }
        }
        DisplayText(title, size = 13.sp, modifier = Modifier.weight(1f), align = TextAlign.Center, maxLines = 1)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            when {
                end != null -> end()
                trailing != null -> Mono(trailing, color = colors.neonYellow, size = 11.sp)
                date != null -> Mono(date, size = 11.sp, maxLines = 1)
            }
        }
    }
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
            .background(colors.card, RoundedCornerShape(10.dp))
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .padding(14.dp),
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
