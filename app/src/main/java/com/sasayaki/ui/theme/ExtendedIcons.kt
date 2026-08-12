package com.sasayaki.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Material icons that are not part of material-icons-core, inlined as path data.
 *
 * Depending on material-icons-extended for a handful of glyphs would cost far more than
 * the app itself, so the paths live here instead. Anything already in the core set is
 * used straight from [androidx.compose.material.icons.Icons] rather than duplicated here.
 *
 * All paths use the standard 24x24 Material viewport; mixing in Material Symbols (960)
 * artwork makes the stroke weights disagree in the same row.
 */
object SasayakiIcons {
    val Mic: ImageVector by lazy {
        iconFromPath(
            "Mic",
            "M12 14c1.66 0 2.99-1.34 2.99-3L15 5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 " +
                "3zm5.3-3c0 3-2.54 5.1-5.3 5.1S6.7 14 6.7 11H5c0 3.41 2.72 6.23 6 6.72V21h2v-3.28c3.28-.48 " +
                "6-3.3 6-6.72h-1.7z"
        )
    }

    val StopCircle: ImageVector by lazy {
        iconFromPath(
            "StopCircle",
            "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm4 14H8V8h8v8z"
        )
    }

    /** Nav icon for the transcript list. */
    val Description: ImageVector by lazy {
        iconFromPath(
            "Description",
            "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z"
        )
    }

    /** Nav icon for dictation profiles, which are tunable presets rather than accounts. */
    val Tune: ImageVector by lazy {
        iconFromPath(
            "Tune",
            "M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z"
        )
    }

    /** Bulk clear, distinct from deleting a single entry. */
    val DeleteSweep: ImageVector by lazy {
        iconFromPath(
            "DeleteSweep",
            "M15 16h4v2h-4zm0-8h7v2h-7zm0 4h6v2h-6zM3 18c0 1.1.9 2 2 2h6c1.1 0 2-.9 2-2V8H3v10zM14 5h-3l-1-1H6L5 5H2v2h12z"
        )
    }

    val ContentCopy: ImageVector by lazy {
        iconFromPath(
            "ContentCopy",
            "M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z"
        )
    }

    private fun iconFromPath(name: String, pathData: String, autoMirror: Boolean = false): ImageVector {
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = autoMirror
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                fill = SolidColor(Color.Black)
            )
        }.build()
    }
}
