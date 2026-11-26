package com.bayonetbarontech.cinderblock.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bayonetbarontech.cinderblock.R

fun workoutArtwork(group: String): Int =
    if (group == "lower") R.drawable.lower_body_card else R.drawable.strength_card

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

@Composable
fun WorkoutCover(group: String, description: String, modifier: Modifier = Modifier) {
    CoverImage(
        workoutArtwork(group),
        modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(16.dp)),
        description,
    )
}
