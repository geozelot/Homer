package com.geozelot.homer.ui.theme

import androidx.compose.ui.graphics.Color

// ── Candlelit palette (design-locked 2026-07-09) ─────────────────────────────
// Warm, dark-first: a reading lamp on a near-black ground. A single amber accent
// carries interaction (rings, play, active); sage marks finished/offline only.

val Ground = Color(0xFF15110E)      // app background
val Studio = Color(0xFF0E0B09)      // deepest ground (gradient base, scrim)
val Surface0 = Color(0xFF1A1511)    // pinned chrome — a step off the ground, below a card
val Surface1 = Color(0xFF211B16)    // cards, bars
val Surface2 = Color(0xFF2C241D)    // raised surfaces, menus, track backgrounds
val Line = Color(0xFF3A2F26)        // hairline borders / dividers
val LineShelf = Color(0xFF4C3E31)   // a shelf's border — one step up from Line, so a stack of books
                                    // is distinguishable from a book without being a second accent

val Parchment = Color(0xFFEFE6D6)   // primary text
val Muted = Color(0xFFA2917B)       // secondary text
val Faint = Color(0xFF6E6153)       // tertiary text / disabled

val Amber = Color(0xFFE3A85A)       // the accent — progress, play, active
val AmberDeep = Color(0xFFC98A3C)   // amber shadow / pressed
val AmberSoft = Color(0x24E3A85A)   // ~14% amber — active pills, highlights
val OnAmber = Color(0xFF201509)     // text/icons on amber fills

val Sage = Color(0xFF86B27A)        // finished / offline status ONLY
val SageSoft = Color(0x2486B27A)    // ~14% sage — offline tag background
val Danger = Color(0xFFD98A7A)      // destructive menu actions
