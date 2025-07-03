package com.mrwinston.solitay

import android.graphics.Rect as AndroidRect
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
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
        WindowCompat.setDecorFitsSystemWindows(window, false)
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
    var gameState by remember { mutableStateOf(GameState()) }
    var isGameWon by remember { mutableStateOf(false) }
    var showNewGameDialog by remember { mutableStateOf(false) }

    var dragInfo by remember { mutableStateOf<DragInfo?>(null) }
    val dragOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val coroutineScope = rememberCoroutineScope()

    val pileLayouts = remember { mutableMapOf<CardPile, Rect>() }
    var targetPile by remember { mutableStateOf<CardPile?>(null) }
    var gestureExclusionRect by remember { mutableStateOf<Rect?>(null) }

    val history = remember { mutableStateListOf(gameState) }
    var historyIndex by remember { mutableIntStateOf(0) }

    fun checkWinCondition(state: GameState): Boolean {
        return state.foundations.all { it.size == 13 }
    }

    val onCardDragStart: (Card, CardPile, Offset, Offset) -> Unit = { card, sourcePile, cardPosition, touchOffset ->
        val draggedCards = when (sourcePile.type) {
            CardPileType.TABLEAU -> {
                val pile = gameState.tableau[sourcePile.index]
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
            dragInfo?.let { info ->
                val dropPosition = info.dragStartPositionInWindow + info.touchOffsetOnCard + dragOffset.value
                targetPile = findDropTarget(dropPosition, pileLayouts)
            }
        }
    }

    val updateGameState: (GameState) -> Unit = { newState ->
        gameState = newState
        if (historyIndex < history.lastIndex) {
            history.removeRange(historyIndex + 1, history.size)
        }
        history.add(newState)
        historyIndex++
        if (checkWinCondition(newState)) {
            isGameWon = true
        }
    }

    val onCardDragEnd: () -> Unit = {
        coroutineScope.launch {
            val currentTarget = targetPile
            val currentDragInfo = dragInfo
            if (currentTarget != null && currentDragInfo != null && isValidMove(
                    currentDragInfo.draggedCards,
                    currentTarget,
                    gameState.foundations,
                    gameState.tableau
                )
            ) {
                val newGameState = performMove(
                    gameState,
                    currentDragInfo.draggedCards,
                    currentDragInfo.sourcePile,
                    currentTarget
                )
                updateGameState(newGameState)
                dragInfo = null
                dragOffset.snapTo(Offset.Zero)
            } else {
                // Animate back to original position
                dragOffset.animateTo(Offset.Zero)
                dragInfo = null
            }
            targetPile = null
        }
    }

    val newGame: () -> Unit = {
        val newGameState = GameState()
        gameState = newGameState
        history.clear()
        history.add(newGameState)
        historyIndex = 0
        isGameWon = false
        showNewGameDialog = false
    }

    val undo: () -> Unit = {
        if (historyIndex > 0) {
            historyIndex--
            gameState = history[historyIndex]
        }
    }

    val redo: () -> Unit = {
        if (historyIndex < history.lastIndex) {
            historyIndex++
            gameState = history[historyIndex]
        }
    }

    if (showNewGameDialog) {
        AlertDialog(
            onDismissRequest = { showNewGameDialog = false },
            title = { Text("Start a New Game?") },
            text = { Text("Please confirm you want to start a new game, your current game will be lost forever?") },
            confirmButton = {
                Button(onClick = newGame) {
                    Text("That game was too hard, give me a new one")
                }
            },
            dismissButton = {
                Button(onClick = { showNewGameDialog = false }) {
                    Text("Oh shit, I shouldn't have hit that")
                }
            }
        )
    }

    var boxBoundsInWindow by remember { mutableStateOf<Rect?>(null) }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { boxBoundsInWindow = it.boundsInWindow() }
            .systemGestureExclusion(listOfNotNull(gestureExclusionRect))
    ) {
        val cardWidth = (maxWidth - 32.dp) / 7
        val cardHeight = cardWidth * 1.4f
        val view = LocalView.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF2E7D32))
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Row: Stock, Waste, Foundations
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StockPileView(
                        cards = gameState.stock,
                        cardWidth = cardWidth,
                        cardHeight = cardHeight,
                        onStockClick = {
                            val newGameState = gameState.deepCopy()
                            if (newGameState.stock.isNotEmpty()) {
                                val numToDraw = minOf(3, newGameState.stock.size)
                                for (i in 0 until numToDraw) {
                                    val card =
                                        newGameState.stock.removeAt(newGameState.stock.lastIndex)
                                    card.isFaceUp.value = true
                                    newGameState.waste.add(card)
                                }
                            } else {
                                newGameState.stock.addAll(newGameState.waste.reversed())
                                newGameState.stock.forEach { it.isFaceUp.value = false }
                                newGameState.waste.clear()
                            }
                            updateGameState(newGameState)
                        },
                        modifier = Modifier.onGloballyPositioned {
                            pileLayouts[CardPile(CardPileType.STOCK)] = it.boundsInWindow()
                            Log.d(
                                "Solitaire",
                                "StockPile bounds: ${pileLayouts[CardPile(CardPileType.STOCK)]}"
                            )
                        }
                    )

                    WastePileView(
                        cards = gameState.waste,
                        cardWidth = cardWidth,
                        cardHeight = cardHeight,
                        dragInfo = dragInfo,
                        onCardDragStart = { card, cardPos, touchPos ->
                            onCardDragStart(
                                card,
                                CardPile(CardPileType.WASTE),
                                cardPos,
                                touchPos
                            )
                        },
                        onCardDrag = onCardDrag,
                        onCardDragEnd = onCardDragEnd,
                        modifier = Modifier.onGloballyPositioned {
                            pileLayouts[CardPile(CardPileType.WASTE)] = it.boundsInWindow()
                            Log.d(
                                "Solitaire",
                                "WastePile bounds: ${pileLayouts[CardPile(CardPileType.WASTE)]}"
                            )
                        }
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    gameState.foundations.forEachIndexed { index, pile ->
                        FoundationPileView(
                            cards = pile,
                            cardWidth = cardWidth,
                            cardHeight = cardHeight,
                            isTargeted = targetPile == CardPile(CardPileType.FOUNDATION, index),
                            modifier = Modifier.onGloballyPositioned {
                                pileLayouts[CardPile(CardPileType.FOUNDATION, index)] =
                                    it.boundsInWindow()
                                Log.d(
                                    "Solitaire",
                                    "FoundationPile $index bounds: ${
                                        pileLayouts[CardPile(
                                            CardPileType.FOUNDATION,
                                            index
                                        )]
                                    }"
                                )
                            }
                        )
                    }
                }
            }

            // Bottom Row: Tableau
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { layoutCoordinates ->
                        val windowBounds = layoutCoordinates.boundsInWindow()
                        gestureExclusionRect = Rect(
                            left = 0f,
                            top = windowBounds.top,
                            right = view.width.toFloat(),
                            bottom = windowBounds.top + 1000f
                        )
                    },
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                gameState.tableau.forEachIndexed { index, pile ->
                    TableauPileView(
                        cards = pile,
                        cardWidth = cardWidth,
                        cardHeight = cardHeight,
                        dragInfo = dragInfo,
                        onCardDragStart = { card, cardPos, touchPos -> onCardDragStart(card, CardPile(CardPileType.TABLEAU, index), cardPos, touchPos) },
                        onCardDrag = onCardDrag,
                        onCardDragEnd = onCardDragEnd,
                        isTargeted = targetPile == CardPile(CardPileType.TABLEAU, index),
                        onPlaceholderPositioned = { layoutCoordinates ->
                            val tableauBounds = layoutCoordinates.boundsInWindow()
                            pileLayouts[CardPile(CardPileType.TABLEAU, index)] =
                                boxBoundsInWindow?.let { boxBounds ->
                                    tableauBounds.copy(bottom = boxBounds.bottom)
                                } ?: tableauBounds
                            Log.d(
                                "Solitaire",
                                "TableauPile $index bounds: ${pileLayouts[CardPile(CardPileType.TABLEAU, index)]}"
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
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
        // Undo/Redo buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
        ) {
            Button(onClick = undo, enabled = historyIndex > 0) {
                Text("Undo")
            }
            Button(onClick = { showNewGameDialog = true }) {
                Text("New Game")
            }
            Button(onClick = redo, enabled = historyIndex < history.lastIndex) {
                Text("Redo")
            }
        }

        if (isGameWon) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .zIndex(20f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("You Win!", color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { showNewGameDialog = true }) {
                        Text("New Game")
                    }
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
    currentState: GameState,
    draggedCards: List<Card>,
    sourcePile: CardPile,
    targetPile: CardPile
): GameState {
    val newGameState = currentState.deepCopy()

    val sourceList = when (sourcePile.type) {
        CardPileType.WASTE -> newGameState.waste
        CardPileType.TABLEAU -> newGameState.tableau[sourcePile.index]
        else -> return newGameState // Should not happen
    }

    val targetList = when (targetPile.type) {
        CardPileType.FOUNDATION -> newGameState.foundations[targetPile.index]
        CardPileType.TABLEAU -> newGameState.tableau[targetPile.index]
        else -> return newGameState // Should not happen
    }

    // Find the card references in the new state
    val draggedCardIds = draggedCards.map { it.suit to it.rank }
    val cardsToMove = sourceList.filter { it.suit to it.rank in draggedCardIds }

    sourceList.removeAll(cardsToMove)
    targetList.addAll(cardsToMove)

    // Flip card in source tableau pile if needed
    if (sourcePile.type == CardPileType.TABLEAU && sourceList.isNotEmpty()) {
        val cardToFlip = sourceList.last()
        if (!cardToFlip.isFaceUp.value) {
            cardToFlip.isFaceUp.value = true
        }
    }
    return newGameState
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
            val density = LocalDensity.current
            // Calculate a suitable font size based on the cardWidth
            // You might need to adjust the multiplier (e.g., 0.6f) to find what works best for your design
            val fontSize = with(density) { (cardWidth.toPx() * 0.25f).toSp() }

            Text(
                "Empty",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = fontSize,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis // This will add "..." if text still overflows after scaling
            )
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
        val wasteToDisplay = cards.takeLast(3)
        wasteToDisplay.forEachIndexed { index, card ->
            val isBeingDragged = dragInfo?.draggedCards?.contains(card) == true
            var cardPositionInWindow by remember { mutableStateOf(Offset.Zero) }
            Box(
                modifier = Modifier
                    .offset(x = (index * 15).dp)
                    .onGloballyPositioned { cardPositionInWindow = it.positionInWindow() }
                    .graphicsLayer(alpha = if (isBeingDragged) 0f else 1f)
            ) {
                PlayingCard(
                    card = card,
                    modifier = Modifier
                        .width(cardWidth)
                        .height(cardHeight)
                        .pointerInput(card) {
                            if (index == wasteToDisplay.lastIndex) {
                                detectDragGestures(
                                    onDragStart = { touchOffset ->
                                        onCardDragStart(
                                            card,
                                            cardPositionInWindow,
                                            touchOffset
                                        )
                                    },
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

@Composable
fun FoundationPileView(
    cards: List<Card>,
    cardWidth: Dp,
    cardHeight: Dp,
    modifier: Modifier,
    isTargeted: Boolean
) {
    Box(modifier = modifier) {
        PileView(
            cards = cards,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            modifier = if (isTargeted && cards.isEmpty()) {
                Modifier.border(2.dp, Color.Yellow)
            } else {
                Modifier
            }
        )
        cards.lastOrNull()?.let {
            PlayingCard(
                card = it,
                modifier = Modifier
                    .width(cardWidth)
                    .height(cardHeight)
                    .then(
                        if (isTargeted) {
                            Modifier.border(2.dp, Color.Yellow)
                        } else {
                            Modifier
                        }
                    )
            )
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
    modifier: Modifier,
    isTargeted: Boolean,
    onPlaceholderPositioned: (LayoutCoordinates) -> Unit
) {
    Box(
        modifier = modifier
    ) {
        PileView(
            cards = cards,
            cardWidth = cardWidth,
            cardHeight = cardHeight * 2,
            modifier = Modifier
                .onGloballyPositioned(onPlaceholderPositioned)
                .then(
                    if (isTargeted && cards.isEmpty()) {
                        Modifier.border(2.dp, Color.Yellow)
                    } else {
                        Modifier
                    }
                )
        )
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
                                    onDragStart = { touchOffset ->
                                        onCardDragStart(
                                            card,
                                            cardPositionInWindow,
                                            touchOffset
                                        )
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        onCardDrag(dragAmount)
                                    },
                                    onDragEnd = onCardDragEnd,
                                    onDragCancel = onCardDragEnd
                                )
                            }
                        }
                        .then(
                            if (isTargeted && index == cards.lastIndex) {
                                Modifier.border(2.dp, Color.Yellow)
                            } else {
                                Modifier
                            }
                        )
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

fun Modifier.systemGestureExclusion(exclusionRects: List<Rect>) = composed {
    val view = LocalView.current
    LaunchedEffect(view, exclusionRects) {
        view.systemGestureExclusionRects = exclusionRects.map {
            AndroidRect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt())
        }
    }
    this
}