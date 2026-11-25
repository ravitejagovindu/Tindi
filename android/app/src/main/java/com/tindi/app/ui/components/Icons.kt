package com.tindi.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 24dp icons, 2dp stroke, round caps and joins. Paths are drawn on a 24 by 24 grid. */
enum class TIcon(private val path: String) {
    Search("M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0 -14 M21 21l-4.3 -4.3"),
    Plus("M12 5v14 M5 12h14"),
    ArrowLeft("M19 12H5 M12 19l-7 -7 7 -7"),
    ArrowRight("M5 12h14 M12 5l7 7 -7 7"),
    Close("M18 6L6 18 M6 6l12 12"),
    Moon("M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"),
    Sun(
        "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0 -8 M12 2v2 M12 20v2 M4.9 4.9l1.4 1.4 M17.7 17.7l1.4 1.4 " +
            "M2 12h2 M20 12h2 M4.9 19.1l1.4 -1.4 M17.7 6.3l1.4 -1.4",
    ),
    Library("M4 19.5A2.5 2.5 0 0 1 6.5 17H20 M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"),
    Grid("M3 3h7v7H3z M14 3h7v7h-7z M14 14h7v7h-7z M3 14h7v7H3z"),
    User("M20 21v-2a4 4 0 0 0 -4 -4H8a4 4 0 0 0 -4 4v2 M12 7a4 4 0 1 0 0 8a4 4 0 1 0 0 -8"),
    Play("M6 4l14 8 -14 8z"),
    Check("M20 6L9 17l-5 -5"),
    ChevronRight("M9 18l6 -6 -6 -6"),
    ChevronDown("M6 9l6 6 6 -6"),
    ChevronUp("M18 15l-6 -6 -6 6"),
    Edit("M12 20h9 M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1 -4z"),
    Trash(
        "M3 6h18 M19 6l-1 14a2 2 0 0 1 -2 2H8a2 2 0 0 1 -2 -2L5 6 M10 11v6 M14 11v6 " +
            "M9 6V4a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v2",
    ),
    External("M18 13v6a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2V8a2 2 0 0 1 2 -2h6 M15 3h6v6 M10 14L21 3"),
    Logout("M9 21H5a2 2 0 0 1 -2 -2V5a2 2 0 0 1 2 -2h4 M16 17l5 -5 -5 -5 M21 12H9"),
    Alert("M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0 -18 M12 8v5 M12 16.5v0.5"),
    Pen("M4 20h4L19 9l-4 -4L4 16z");

    val vector: ImageVector by lazy {
        ImageVector.Builder(
            name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).addPath(
            pathData = PathParser().parsePathString(path).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
    }
}

/** [contentDescription] null = decorative (the parent already carries the label). */
@Composable
fun TIconImage(
    icon: TIcon,
    tint: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Image(
        imageVector = icon.vector,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}
