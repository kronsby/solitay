package com.mrwinston.solitay

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mrwinston.solitay.model.Card
import com.mrwinston.solitay.model.GameState
import com.mrwinston.solitay.model.Rank
import com.mrwinston.solitay.ui.PlayingCard
import com.mrwinston.solitay.ui.theme.SolitaireTheme
import kotlinx.coroutines.launch
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

// Holds all information about the current drag operation
internal data class DragInfo(
    val draggedCards: List<Card>,
    val sourcePile: CardPile,
    val dragStartPositionInWindow: Offset,
    val touchOffsetOnCard: Offset
)

// Enum to identify the different piles
internal enum class CardPileType {
    STOCK, WASTE, FOUNDATION, TABLEAU
}

// Data class to represent a specific pile
internal data class CardPile(
    val type: CardPileType,
    val index: Int = 0 // For foundation and tableau piles
)

@Composable
fun SolitaireGame(modifier: Modifier = Modifier) {
    val gameState = remember { GameState() }
    val stock = remember { gameState.stock.toMutableStateList() }
    val waste = remember { gameState.waste.toMutableStateList() }
    val foundations = remember { gameState.foundations.map { it.toMutableStateList() }.toMutableStateList() }
    val tableau = remember { gameState.tableau.map { it.toMutableStateList() }.toMutableStateList() }

    var dragInfo by remember { mutableStateOf<DragInfo?>(null) }
    val dragOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val coroutineScope = rememberCoroutineScope()

    val pileLayouts = remember { mutableMapOf<CardPile, Rect>() }

    val onCardDragStart: (Card, CardPile, Offset, Offset) -> Unit = { card, sourcePile, cardPosition, touchOffset ->
        val draggedCards = when (sourcePile.type) {
            CardPileType.TABLEAU -> {
                val pile = tableau[sourcePile.index]
                val cardIndex = pile.indexOf(card)
                if (cardIndex != -1) pile.subList(cardIndex, pile.size).toList() else emptyList()
            }
            CardPileType.WASTE -> listOf(card)
            else -> emptyList()
        }
        if (draggedCards.isNotEmpty()) {
            dragInfo = DragInfo(draggedCards, sourcePile, cardPosition, touchOffset)
        }
    }

    val onCardDrag: (Offset) -> Unit = { delta ->
        coroutineScope.launch {
            dragOffset.snapTo(dragOffset.value + delta)
        }
    }

    val onCardDragEnd: () -> Unit = {
        coroutineScope.launch {
            dragInfo?.let { info ->
                val dropPosition = info.dragStartPositionInWindow + info.touchOffsetOnCard + dragOffset.value
                val targetPile = findDropTarget(dropPosition, pileLayouts)

                if (targetPile != null && isValidMove(info.draggedCards, targetPile, foundations, tableau)) {
                    performMove(info.draggedCards, info.sourcePile, targetPile, waste, foundations, tableau)
                    dragInfo = null
                    dragOffset.snapTo(Offset.Zero)
                } else {
                    // Animate back to original position
                    dragOffset.animateTo(Offset.Zero)
                    dragInfo = null
                }
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val cardWidth = (maxWidth - 32.dp) / 7
        val cardHeight = cardWidth * 1.4f

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF2E7D32))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Row: Stock, Waste, Foundations
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StockPileView(
                    cards = stock,
                    cardWidth = cardWidth,
                    cardHeight = cardHeight,
                    onStockClick = {
                        if (stock.isNotEmpty()) {
                            val card = stock.removeAt(stock.lastIndex)
                            card.isFaceUp.value = true
                            waste.add(card)
                        } else {
                            stock.addAll(waste.reversed())
                            stock.forEach { it.isFaceUp.value = false }
                            waste.clear()
                        }
                    },
                    modifier = Modifier.onGloballyPositioned {
                        pileLayouts[CardPile(CardPileType.STOCK)] = it.boundsInWindow()
                        Log.d("Solitaire", "StockPile bounds: ${pileLayouts[CardPile(CardPileType.STOCK)]}")
                    }
                )

                WastePileView(
                    cards = waste,
                    cardWidth = cardWidth,
                    cardHeight = cardHeight,
                    dragInfo = dragInfo,
                    onCardDragStart = { card, cardPos, touchPos -> onCardDragStart(card, CardPile(CardPileType.WASTE), cardPos, touchPos) },
                    onCardDrag = onCardDrag,
                    onCardDragEnd = onCardDragEnd,
                    modifier = Modifier.onGloballyPositioned {
                        pileLayouts[CardPile(CardPileType.WASTE)] = it.boundsInWindow()
                        Log.d("Solitaire", "WastePile bounds: ${pileLayouts[CardPile(CardPileType.WASTE)]}")
                    }
                )

                Spacer(modifier = Modifier.width(16.dp))

                foundations.forEachIndexed { index, pile ->
                    FoundationPileView(
                        cards = pile,
                        cardWidth = cardWidth,
                        cardHeight = cardHeight,
                        modifier = Modifier.onGloballyPositioned {
                            pileLayouts[CardPile(CardPileType.FOUNDATION, index)] = it.boundsInWindow()
                            Log.d("Solitaire", "FoundationPile $index bounds: ${pileLayouts[CardPile(CardPileType.FOUNDATION, index)]}")
                        }
                    )
                }
            }

            // Bottom Row: Tableau
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tableau.forEachIndexed { index, pile ->
                    TableauPileView(
                        cards = pile,
                        cardWidth = cardWidth,
                        cardHeight = cardHeight,
                        dragInfo = dragInfo,
                        onCardDragStart = { card, cardPos, touchPos -> onCardDragStart(card, CardPile(CardPileType.TABLEAU, index), cardPos, touchPos) },
                        onCardDrag = onCardDrag,
                        onCardDragEnd = onCardDragEnd,
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned {
                                pileLayouts[CardPile(CardPileType.TABLEAU, index)] = it.boundsInWindow()
                                Log.d("Solitaire", "TableauPile $index bounds: ${pileLayouts[CardPile(CardPileType.TABLEAU, index)]}")
                            }
                    )
                }
            }
        }

        // Dragged card overlay
        dragInfo?.let { info ->
            Box(
                modifier = Modifier
                    .offset {
                        val start = info.dragStartPositionInWindow
                        val drag = dragOffset.value
                        IntOffset(
                            (start.x + drag.x).roundToInt(),
                            (start.y + drag.y).roundToInt()
                        )
                    }
                    .zIndex(10f)
            ) {
                info.draggedCards.forEachIndexed { index, card ->
                    PlayingCard(
                        card = card,
                        modifier = Modifier
                            .offset(y = (index * 20).dp)
                            .width(cardWidth)
                            .height(cardHeight)
                    )
                }
            }
        }
    }
}

private fun findDropTarget(dragPosition: Offset, layouts: Map<CardPile, Rect>): CardPile? {
    Log.d("Solitaire", "findDropTarget: dragPosition=$dragPosition")
    val target = layouts.entries
        .find { (_, rect) -> rect.contains(dragPosition) }
        ?.key
    Log.d("Solitaire", "findDropTarget: target=$target")
    return target
}

private fun isValidMove(
    draggedCards: List<Card>,
    targetPile: CardPile,
    foundations: List<List<Card>>,
    tableau: List<List<Card>>
): Boolean {
    if (draggedCards.isEmpty()) return false
    val firstDraggedCard = draggedCards.first()

    val result = when (targetPile.type) {
        CardPileType.FOUNDATION -> {
            if (draggedCards.size > 1) return false // Only single cards to foundation
            val foundation = foundations[targetPile.index]
            if (foundation.isEmpty()) {
                firstDraggedCard.rank == Rank.ACE
            } else {
                val topCard = foundation.last()
                firstDraggedCard.suit == topCard.suit && firstDraggedCard.rank.ordinal == topCard.rank.ordinal + 1
            }
        }
        CardPileType.TABLEAU -> {
            val tableauPile = tableau[targetPile.index]
            if (tableauPile.isEmpty()) {
                firstDraggedCard.rank == Rank.KING
            } else {
                val topCard = tableauPile.last()
                !topCard.isRed == firstDraggedCard.isRed && topCard.rank.ordinal == firstDraggedCard.rank.ordinal + 1
            }
        }
        else -> false // Cannot drop on stock or waste
    }
    Log.d("Solitaire", "isValidMove: dragged=$draggedCards, target=$targetPile, result=$result")
    return result
}

private fun performMove(
    draggedCards: List<Card>,
    sourcePile: CardPile,
    targetPile: CardPile,
    waste: SnapshotStateList<Card>,
    foundations: SnapshotStateList<SnapshotStateList<Card>>,
    tableau: SnapshotStateList<SnapshotStateList<Card>>
) {
    val sourceList = when (sourcePile.type) {
        CardPileType.WASTE -> waste
        CardPileType.TABLEAU -> tableau[sourcePile.index]
        else -> return // Should not happen
    }

    val targetList = when (targetPile.type) {
        CardPileType.FOUNDATION -> foundations[targetPile.index]
        CardPileType.TABLEAU -> tableau[targetPile.index]
        else -> return // Should not happen
    }

    // Safely remove cards from the source list
    if (sourcePile.type == CardPileType.TABLEAU) {
        repeat(draggedCards.size) {
            sourceList.removeAt(sourceList.lastIndex)
        }
    } else {
        sourceList.removeAll(draggedCards)
    }
    targetList.addAll(draggedCards.toList())

    // Flip card in source tableau pile if needed
    Log.d("Solitaire", "Checking flip condition for sourcePile: ${sourcePile.type}")
    if (sourcePile.type == CardPileType.TABLEAU && sourceList.isNotEmpty()) {
        val cardToFlip = sourceList.last()
        Log.d("Solitaire", "Card to potentially flip: $cardToFlip, isFaceUp: ${cardToFlip.isFaceUp.value}")
        if (!cardToFlip.isFaceUp.value) {
            cardToFlip.isFaceUp.value = true
            Log.d("Solitaire", "Card flipped: $cardToFlip, new isFaceUp: ${cardToFlip.isFaceUp.value}")
        } else {
            Log.d("Solitaire", "Card already face up or not a tableau pile.")
        }
    }
}

@Composable
private fun PileView(modifier: Modifier = Modifier, cards: List<Card>, cardWidth: Dp, cardHeight: Dp) {
    Box(
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight)
            .border(1.dp, Color.White.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        if (cards.isEmpty()) {
            Text("Empty", color = Color.White.copy(alpha = 0.5f))
        }
    }
}

@Composable
fun StockPileView(cards: List<Card>, cardWidth: Dp, cardHeight: Dp, onStockClick: () -> Unit, modifier: Modifier) {
    Box(modifier = modifier.clickable(onClick = onStockClick)) {
        PileView(cards = cards, cardWidth = cardWidth, cardHeight = cardHeight)
        if (cards.isNotEmpty()) {
            PlayingCard(card = cards.last(), modifier = Modifier.width(cardWidth).height(cardHeight))
        }
    }
}

@Composable
internal fun WastePileView(
    cards: List<Card>,
    cardWidth: Dp,
    cardHeight: Dp,
    dragInfo: DragInfo?,
    onCardDragStart: (Card, Offset, Offset) -> Unit,
    onCardDrag: (Offset) -> Unit,
    onCardDragEnd: () -> Unit,
    modifier: Modifier
) {
    Box(modifier = modifier) {
        PileView(cards = cards, cardWidth = cardWidth, cardHeight = cardHeight)
        cards.lastOrNull()?.let { card ->
            val isBeingDragged = dragInfo?.draggedCards?.contains(card) == true
            var cardPositionInWindow by remember { mutableStateOf(Offset.Zero) }
            Box(
                modifier = Modifier
                    .onGloballyPositioned { cardPositionInWindow = it.positionInWindow() }
                    .graphicsLayer(alpha = if (isBeingDragged) 0f else 1f)
            ) {
                PlayingCard(
                    card = card,
                    modifier = Modifier
                        .width(cardWidth)
                        .height(cardHeight)
                        .pointerInput(card) {
                            detectDragGestures(
                                onDragStart = { touchOffset -> onCardDragStart(card, cardPositionInWindow, touchOffset) },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onCardDrag(dragAmount)
                                },
                                onDragEnd = onCardDragEnd,
                                onDragCancel = onCardDragEnd
                            )
                        }
                )
            }
        }
    }
}

@Composable
fun FoundationPileView(cards: List<Card>, cardWidth: Dp, cardHeight: Dp, modifier: Modifier) {
    Box(modifier = modifier) {
        PileView(cards = cards, cardWidth = cardWidth, cardHeight = cardHeight)
        cards.lastOrNull()?.let {
            PlayingCard(card = it, modifier = Modifier.width(cardWidth).height(cardHeight))
        }
    }
}

@Composable
internal fun TableauPileView(
    cards: List<Card>,
    cardWidth: Dp,
    cardHeight: Dp,
    dragInfo: DragInfo?,
    onCardDragStart: (Card, Offset, Offset) -> Unit,
    onCardDrag: (Offset) -> Unit,
    onCardDragEnd: () -> Unit,
    modifier: Modifier
) {
    Box(modifier = modifier) {
        PileView(cards = cards, cardWidth = cardWidth, cardHeight = cardHeight * 2)
        cards.forEachIndexed { index, card ->
            val isBeingDragged = dragInfo?.draggedCards?.contains(card) == true
            var cardPositionInWindow by remember { mutableStateOf(Offset.Zero) }
            Box(
                modifier = Modifier
                    .offset(y = (index * 20).dp)
                    .onGloballyPositioned { cardPositionInWindow = it.positionInWindow() }
                    .graphicsLayer(alpha = if (isBeingDragged) 0f else 1f)
            ) {
                PlayingCard(
                    card = card,
                    modifier = Modifier
                        .width(cardWidth)
                        .height(cardHeight)
                        .pointerInput(card) {
                            if (card.isFaceUp.value) {
                                detectDragGestures(
                                    onDragStart = { touchOffset -> onCardDragStart(card, cardPositionInWindow, touchOffset) },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        onCardDrag(dragAmount)
                                    },
                                    onDragEnd = onCardDragEnd,
                                    onDragCancel = onCardDragEnd
                                )
                            }
                        }
                )
            }
        }
    }
}

private fun LayoutCoordinates.boundsInWindow(): Rect {
    val position = positionInWindow()
    val size = size
    return Rect(position.x, position.y, position.x + size.width, position.y + size.height)
}
