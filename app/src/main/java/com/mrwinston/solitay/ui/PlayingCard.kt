package com.mrwinston.solitay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrwinston.solitay.model.Card
import com.mrwinston.solitay.model.Rank
import com.mrwinston.solitay.model.Suit

@Composable
fun PlayingCard(
    card: Card,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(0.7f) // Standard playing card ratio
            .background(Color.White)
            .border(1.dp, Color.Black)
            .padding(4.dp),
        contentAlignment = Alignment.TopStart
    ) {
        if (card.isFaceUp.value) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top left corner: rank
                Text(
                    text = getRankSymbol(card.rank),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (card.isRed) Color.Red else Color.Black,
                    modifier = Modifier.align(Alignment.TopStart)
                )
                // Center: smaller suit symbol
                Text(
                    text = getSuitSymbol(card.suit),
                    fontSize = 18.sp,
                    color = if (card.isRed) Color.Red else Color.Black,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        } else {
            // Card back design
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1B5E20))
                    .border(1.dp, Color.White)
            )
        }
    }
}

private fun getRankSymbol(rank: Rank): String {
    return when (rank) {
        Rank.ACE -> "A"
        Rank.TWO -> "2"
        Rank.THREE -> "3"
        Rank.FOUR -> "4"
        Rank.FIVE -> "5"
        Rank.SIX -> "6"
        Rank.SEVEN -> "7"
        Rank.EIGHT -> "8"
        Rank.NINE -> "9"
        Rank.TEN -> "10"
        Rank.JACK -> "J"
        Rank.QUEEN -> "Q"
        Rank.KING -> "K"
    }
}

private fun getSuitSymbol(suit: Suit): String {
    return when (suit) {
        Suit.HEARTS -> "♥"
        Suit.DIAMONDS -> "♦"
        Suit.CLUBS -> "♣"
        Suit.SPADES -> "♠"
    }
} 