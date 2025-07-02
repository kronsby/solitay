package com.mrwinston.solitay.model

class GameState(initialize: Boolean = true) {
    // Stock pile (where cards are drawn from)
    val stock = mutableListOf<Card>()

    // Waste pile (where cards from stock are placed)
    val waste = mutableListOf<Card>()

    // Foundation piles (where cards are built up by suit)
    val foundations = List(4) { mutableListOf<Card>() }

    // Tableau piles (where cards are built down by alternating colors)
    val tableau = List(7) { mutableListOf<Card>() }

    init {
        if (initialize) {
            initializeGame()
        }
    }

    private fun initializeGame() {
        // Create a deck of 52 cards
        val deck = mutableListOf<Card>()
        for (suit in Suit.entries) {
            for (rank in Rank.entries) {
                deck.add(Card(suit, rank))
            }
        }

        // Shuffle the deck
        deck.shuffle()

        // Deal cards to tableau piles
        for (i in 0..6) {
            for (j in 0..i) {
                val card = deck.removeAt(0)
                if (j == i) card.isFaceUp.value = true
                tableau[i].add(card)
            }
        }

        // Remaining cards go to stock
        stock.addAll(deck)
    }

    fun deepCopy(): GameState {
        val newGameState = GameState(false)
        newGameState.stock.addAll(stock.map { it.copy() })
        newGameState.waste.addAll(waste.map { it.copy() })
        foundations.forEachIndexed { index, pile ->
            newGameState.foundations[index].addAll(pile.map { it.copy() })
        }
        tableau.forEachIndexed { index, pile ->
            newGameState.tableau[index].addAll(pile.map { it.copy() })
        }
        return newGameState
    }
}