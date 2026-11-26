package com.bayonetbarontech.cinderblock.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bayonetbarontech.cinderblock.R
import com.bayonetbarontech.cinderblock.domain.WorkoutImageCatalog
import com.bayonetbarontech.cinderblock.domain.drawableFor
import kotlinx.serialization.json.Json

@Composable
fun CoverImage(
    @DrawableRes res: Int,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Image(
        painter = painterResource(res),
        contentDescription = description,
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}

@Composable
fun BrandMark(size: Dp = 44.dp, modifier: Modifier = Modifier) {
    CoverImage(
        R.drawable.logo,
        modifier.size(size).clip(CircleShape),
        "Cinderblock",
    )
}

@Composable
fun CoachMark(size: Dp = 44.dp, modifier: Modifier = Modifier) {
    CoverImage(
        R.drawable.coach,
        modifier.size(size).clip(CircleShape),
        "Coach",
    )
}

private val WorkoutImageJson = Json { ignoreUnknownKeys = true }

@Composable
fun rememberWorkoutCatalog(): WorkoutImageCatalog {
    val context = LocalContext.current
    return remember {
        runCatching {
            WorkoutImageJson.decodeFromString<WorkoutImageCatalog>(
                context.assets.open("workout-images.json").bufferedReader().use { it.readText() },
            )
        }.getOrElse { WorkoutImageCatalog() }
    }
}

@Composable
fun workoutDrawable(label: String): Int {
    val context = LocalContext.current
    val catalog = rememberWorkoutCatalog()
    val name = remember(label, catalog) { catalog.drawableFor(label) }
    val fallback = catalog.default.ifBlank { "push" }
    return remember(name, fallback) {
        fun lookup(drawable: String) = context.resources.getIdentifier(drawable, "drawable", context.packageName)
        lookup(name).takeIf { it != 0 } ?: lookup(fallback).takeIf { it != 0 } ?: R.drawable.push
    }
}

@Composable
fun WorkoutCover(name: String, description: String, modifier: Modifier = Modifier) {
    CoverImage(
        workoutDrawable(name),
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp)),
        description,
    )
}
