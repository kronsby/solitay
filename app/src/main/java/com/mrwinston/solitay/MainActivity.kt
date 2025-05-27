package com.mrwinston.solitay

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.zIndex
import com.mrwinston.solitay.model.Card
import com.mrwinston.solitay.model.GameState
import com.mrwinston.solitay.ui.PlayingCard
import com.mrwinston.solitay.ui.theme.SolitaireTheme
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SolitaireTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SolitaireGame(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun SolitaireGame(modifier: Modifier = Modifier) {
    // GameState instance for initial deal (state managed by observable lists below)
    val initialGameState = remember { GameState() }

    // Observable state for the game piles
    val stock = remember { mutableStateListOf<Card>().apply { addAll(initialGameState.getStock()) } }
    val waste = remember { mutableStateListOf<Card>().apply { addAll(initialGameState.getWaste()) } }
    val foundations = remember { initialGameState.getFoundations().map { it.toMutableStateList() } }
    val tableau = remember { initialGameState.getTableau().map { it.toMutableStateList() } }

    // State to track the currently dragged card
    var draggedCard by remember { mutableStateOf<Card?>(null) }
    var dragOffset by remember { mutableStateOf(IntOffset(0, 0)) }
    var dragSourcePile by remember { mutableStateOf<CardPile?>(null) }

    // Lambda to handle drag events for any card
    val onCardDragStart: (Card, IntOffset, CardPile) -> Unit = { card, startOffset, sourcePile ->
        Log.i("OMG", "Drag start: card=$card, offset=$startOffset, pile=$sourcePile")
        draggedCard = card
        dragOffset = IntOffset(0, 0)  // Start with zero offset
        dragSourcePile = sourcePile
    }

    val onCardDrag: (Card, IntOffset) -> Unit = { card, dragAmount ->
        Log.i("OMG", "Drag: card=$card, amount=$dragAmount, current offset=$dragOffset")
        dragOffset += dragAmount
    }

    val onCardDragEnd: (Card, IntOffset) -> Unit = { card, endOffset ->
        Log.i("OMG", "Drag end: card=$card, offset=$endOffset")
        // TODO: Implement drop target logic
        Log.i("OMG", "Card dragged and dropped.")

        // Reset drag state
        draggedCard = null
        dragOffset = IntOffset(0, 0)
        dragSourcePile = null
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Calculate card size based on available width and number of tableau piles
        val cardWidth = (maxWidth - 16.dp - (7 * 8.dp)) / 7  // 7 piles, 8dp spacing between them, 16dp total padding
        val cardHeight = cardWidth * 1.4f  // Maintain aspect ratio of 0.7 (1/1.4)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF2E7D32))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Stock Pile
                StockPile(
                    cards = stock,
                    modifier = Modifier
                        .weight(1f)
                        .height(cardHeight),
                    cardWidth = cardWidth,
                    cardHeight = cardHeight,
                    onStockClick = {
                        if (stock.isNotEmpty()) {
                            val cardToDraw = stock.removeAt(0)
                            cardToDraw.isFaceUp = true
                            waste.add(cardToDraw)
                        } else if (waste.isNotEmpty()) {
                            waste.forEach { it.isFaceUp = false }
                            stock.addAll(waste.asReversed())
                            waste.clear()
                        }
                    }
                )
                
                // Waste Pile
                WastePile(
                    cards = waste.filter { it != draggedCard },
                    modifier = Modifier
                        .weight(1f)
                        .height(cardHeight),
                    cardWidth = cardWidth,
                    cardHeight = cardHeight,
                    draggedCard = draggedCard,
                    onCardDragStart = { card, offset -> 
                        onCardDragStart(card, offset, CardPile(CardPileType.WASTE))
                    },
                    onCardDrag = onCardDrag,
                    onCardDragEnd = onCardDragEnd
                )
                
                // Foundation Piles
                foundations.forEach { foundation ->
                    FoundationPile(
                        cards = foundation,
                        modifier = Modifier
                            .weight(1f)
                            .height(cardHeight),
                        cardWidth = cardWidth,
                        cardHeight = cardHeight
                    )
                }
            }
            
            // Tableau piles row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight * 3),  // Allow for stacked cards
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                tableau.forEachIndexed { index, pile ->
                    TableauPile(
                        cards = pile,
                        modifier = Modifier.weight(1f),
                        cardWidth = cardWidth,
                        cardHeight = cardHeight,
                        draggedCard = draggedCard,
                        onCardDragStart = { card, offset -> 
                            onCardDragStart(card, offset, CardPile(CardPileType.TABLEAU, index))
                        },
                        onCardDrag = onCardDrag,
                        onCardDragEnd = onCardDragEnd
                    )
                }
            }
        }

        // Render the dragged card at the root level
        draggedCard?.let { card ->
            PlayingCard(
                card = card,
                modifier = Modifier
                    .width(cardWidth)
                    .height(cardHeight)
                    .offset { dragOffset }  // Just use the drag offset
                    .zIndex(1000f),
                onCardDragStart = { _, _ -> },  // No-op since we're already dragging
                onCardDrag = onCardDrag,
                onCardDragEnd = onCardDragEnd,
                currentOffset = dragOffset
            )
        }
    }
}

// Enum to track which pile a card is being dragged from
enum class CardPileType {
    WASTE,
    TABLEAU
}

data class CardPile(
    val type: CardPileType,
    val index: Int = -1
)

@Composable
private fun BaseCardPile(
    cards: List<Card>,
    modifier: Modifier = Modifier,
    content: @Composable (List<Card>) -> Unit
) {
    Box(
        modifier = modifier
            .zIndex(-1f),
        contentAlignment = Alignment.Center
    ) {
        // Draw the background and border in a separate Box
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(1.dp, Color.White)
                .background(Color(0xFF1B5E20))
        )
        
        if (cards.isEmpty()) {
            Text(
                text = "Empty",
                color = Color.White.copy(alpha = 0.5f)
            )
        } else {
            content(cards)
        }
    }
}

@Composable
fun StockPile(
    cards: List<Card>,
    modifier: Modifier = Modifier,
    cardWidth: Dp,
    cardHeight: Dp,
    onStockClick: () -> Unit
) {
    BaseCardPile(
        cards = cards,
        modifier = modifier.clickable(onClick = onStockClick)
    ) { cards ->
        val topCard = cards.last()
        PlayingCard(
            card = topCard,
            modifier = Modifier
                .width(cardWidth)
                .height(cardHeight)
        )
    }
}

@Composable
fun WastePile(
    cards: List<Card>,
    modifier: Modifier = Modifier,
    cardWidth: Dp,
    cardHeight: Dp,
    draggedCard: Card?,
    onCardDragStart: (Card, IntOffset) -> Unit,
    onCardDrag: (Card, IntOffset) -> Unit,
    onCardDragEnd: (Card, IntOffset) -> Unit
) {
    Box(modifier = modifier) {
        BaseCardPile(
            cards = cards,
            modifier = Modifier.matchParentSize()
        ) { cards ->
            if (cards.isNotEmpty()) {
                val topCard = cards.last()
                if (topCard.isFaceUp) {
                    Box(
                        modifier = Modifier
                            .onGloballyPositioned { coordinates ->
                                val cardGlobalPosition = coordinates.boundsInWindow().topLeft.toIntOffset()
                                if (draggedCard == topCard) {
                                    onCardDragStart(topCard, cardGlobalPosition)
                                }
                            }
                    ) {
                        if (draggedCard == topCard && cards.size > 1) {
                            val cardUnderneath = cards[cards.size - 2]
                            if (cardUnderneath.isFaceUp) {
                                PlayingCard(
                                    card = cardUnderneath,
                                    modifier = Modifier
                                        .width(cardWidth)
                                        .height(cardHeight)
                                )
                            }
                        }
                        
                        PlayingCard(
                            card = topCard,
                            modifier = Modifier
                                .width(cardWidth)
                                .height(cardHeight),
                            onCardDragStart = onCardDragStart,
                            onCardDrag = onCardDrag,
                            onCardDragEnd = onCardDragEnd,
                            currentOffset = IntOffset(0, 0)  // The dragged card is rendered at the root level
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FoundationPile(
    cards: List<Card>,
    modifier: Modifier = Modifier,
    cardWidth: Dp,
    cardHeight: Dp
) {
    BaseCardPile(
        cards = cards,
        modifier = modifier
    ) { cards ->
        val topCard = cards.last()
        PlayingCard(
            card = topCard,
            modifier = Modifier
                .width(cardWidth)
                .height(cardHeight)
        )
    }
}

@Composable
fun TableauPile(
    cards: List<Card>,
    modifier: Modifier = Modifier,
    cardWidth: Dp,
    cardHeight: Dp,
    draggedCard: Card?,
    onCardDragStart: (Card, IntOffset) -> Unit,
    onCardDrag: (Card, IntOffset) -> Unit,
    onCardDragEnd: (Card, IntOffset) -> Unit
) {
    BaseCardPile(
        cards = cards,
        modifier = modifier
    ) { cards ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            cards.forEachIndexed { index, card ->
                if (card != draggedCard) {  // Don't show the card if it's being dragged
                    Box(
                        modifier = Modifier
                            .onGloballyPositioned { coordinates ->
                                val cardGlobalPosition = coordinates.boundsInWindow().topLeft.toIntOffset()
                                if (draggedCard == card) {
                                    onCardDragStart(card, cardGlobalPosition)
                                }
                            }
                    ) {
                        PlayingCard(
                            card = card,
                            modifier = Modifier
                                .width(cardWidth)
                                .height(cardHeight)
                                .offset(y = (index * 12).dp)
                                .zIndex(index.toFloat()),
                            onCardDragStart = onCardDragStart,
                            onCardDrag = onCardDrag,
                            onCardDragEnd = onCardDragEnd,
                            currentOffset = IntOffset(0, 0)  // The dragged card is rendered at the root level
                        )
                    }
                }
            }
        }
    }
}

// Helper extension function to convert Offset to IntOffset
private fun androidx.compose.ui.geometry.Offset.toIntOffset() = IntOffset(x.roundToInt(), y.roundToInt())

// Helper extension function to convert IntOffset to Offset
private fun IntOffset.toOffset() = androidx.compose.ui.geometry.Offset(x.toFloat(), y.toFloat())
