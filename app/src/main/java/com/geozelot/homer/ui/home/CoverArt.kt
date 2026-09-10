package com.geozelot.homer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import com.geozelot.homer.ui.components.CoverImage
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Surface1
import com.geozelot.homer.ui.theme.Surface2

// ── Cover art (title-forward placeholder) ─────────────────────────────────────

@Composable
internal fun CoverArt(model: Any?, modifier: Modifier = Modifier) {
    Box(modifier) {
        if (model != null) {
            CoverImage(model = model, modifier = Modifier.fillMaxSize())
        } else {
            // No art: a clear, deliberate placeholder (the real title shows on the card/row itself,
            // so it isn't crammed in here). Scales with the tile, so it works in grid and list.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(Surface2, Surface1))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    // Decorative, exactly like the real-cover case: announcing the title only when
                    // art happened to be missing made a row read differently for no reason.
                    contentDescription = null,
                    tint = Muted,
                    modifier = Modifier.fillMaxSize(0.34f),
                )
            }
        }
    }
}
