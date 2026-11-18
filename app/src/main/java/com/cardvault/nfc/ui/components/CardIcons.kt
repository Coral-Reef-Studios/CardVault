package com.cardvault.nfc.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Train
import androidx.compose.ui.graphics.vector.ImageVector

val CARD_ICONS: List<Pair<String, ImageVector>> = listOf(
    "card" to Icons.Filled.CreditCard,
    "flight" to Icons.Filled.Flight,
    "ticket" to Icons.Filled.ConfirmationNumber,
    "music" to Icons.Filled.MusicNote,
    "sport" to Icons.Filled.SportsSoccer,
    "gift" to Icons.Filled.CardGiftcard,
    "subway" to Icons.Filled.Subway,
    "train" to Icons.Filled.Train,
    "bus" to Icons.Filled.DirectionsBus,
    "shop" to Icons.Filled.ShoppingCart,
    "coffee" to Icons.Filled.LocalCafe,
    "pharmacy" to Icons.Filled.LocalPharmacy,
    "gym" to Icons.Filled.FitnessCenter,
    "library" to Icons.Filled.LocalLibrary,
    "school" to Icons.Filled.School,
    "badge" to Icons.Filled.Badge,
    "key" to Icons.Filled.Key,
    "nfc" to Icons.Filled.Nfc,
    "star" to Icons.Filled.Star,
)

fun iconFor(id: String): ImageVector = CARD_ICONS.firstOrNull { it.first == id }?.second ?: Icons.Filled.CreditCard
