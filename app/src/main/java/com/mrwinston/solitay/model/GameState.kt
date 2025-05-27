package com.mrwinston.solitay.model

class GameState {
    // Stock pile (where cards are drawn from)
    private val stock = mutableListOf<Card>()
    
    // Waste pile (where cards from stock are placed)
    private val waste = mutableListOf<Card>()
    
    // Foundation piles (where cards are built up by suit)
    private val foundations = List(4) { mutableListOf<Card>() }
    
    // Tableau piles (where cards are built down by alternating colors)
    private val tableau = List(7) { mutableListOf<Card>() }
    
    init {
        initializeGame()
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
                if (j == i) card.isFaceUp = true
                tableau[i].add(card)
            }
        }
        
        // Remaining cards go to stock
        stock.addAll(deck)
    }
    
    // Getters for the game state
    fun getStock(): List<Card> = stock.toList()
    fun getWaste(): List<Card> = waste.toList()
    fun getFoundations(): List<List<Card>> = foundations.map { it.toList() }
    fun getTableau(): List<List<Card>> = tableau.map { it.toList() }
} 