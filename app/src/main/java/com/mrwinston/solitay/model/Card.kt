package com.mrwinston.solitay.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

enum class Suit {
    HEARTS, DIAMONDS, CLUBS, SPADES
}

enum class Rank {
    ACE, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE, TEN, JACK, QUEEN, KING
}

data class Card(
    val suit: Suit,
    val rank: Rank,
    val isFaceUp: MutableState<Boolean> = mutableStateOf(false)
) {
    val isRed: Boolean
        get() = suit == Suit.HEARTS || suit == Suit.DIAMONDS

    fun copy(isFaceUp: MutableState<Boolean> = mutableStateOf(this.isFaceUp.value)): Card {
        return Card(suit, rank, isFaceUp)
    }
} 