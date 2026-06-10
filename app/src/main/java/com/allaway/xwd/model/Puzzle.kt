package com.allaway.xwd.model

import kotlinx.serialization.Serializable

enum class Direction { ACROSS, DOWN }

fun Direction.opposite(): Direction =
    if (this == Direction.ACROSS) Direction.DOWN else Direction.ACROSS

/** A single grid square. [solution] is null for black squares. */
@Serializable
data class Cell(
    val solution: Char?,
    val number: Int = 0,
    val circled: Boolean = false,
) {
    val isBlock: Boolean get() = solution == null
}

@Serializable
data class Clue(
    val number: Int,
    val direction: Direction,
    val text: String,
    /** Row-major indices of the cells this answer occupies, in order. */
    val cells: List<Int>,
)

@Serializable
data class Puzzle(
    val title: String,
    val author: String,
    val copyright: String,
    val notes: String = "",
    val width: Int,
    val height: Int,
    val cells: List<Cell>,
    val clues: List<Clue>,
    /** True if the .puz solution is scrambled (locked); check/reveal are unavailable. */
    val scrambled: Boolean = false,
) {
    fun cellAt(row: Int, col: Int): Cell = cells[row * width + col]

    fun rowOf(index: Int): Int = index / width
    fun colOf(index: Int): Int = index % width

    val whiteCellCount: Int by lazy { cells.count { !it.isBlock } }

    /** The clue whose answer passes through [index] in [direction], or null. */
    fun clueAt(index: Int, direction: Direction): Clue? =
        clues.firstOrNull { it.direction == direction && index in it.cells }

    fun cluesFor(direction: Direction): List<Clue> =
        clues.filter { it.direction == direction }.sortedBy { it.number }

    /** Whether the cell participates in any word of the given direction. */
    fun hasWord(index: Int, direction: Direction): Boolean = clueAt(index, direction) != null

    fun nextClue(clue: Clue): Clue {
        val ordered = orderedClues
        val i = ordered.indexOf(clue)
        return ordered[(i + 1) % ordered.size]
    }

    fun previousClue(clue: Clue): Clue {
        val ordered = orderedClues
        val i = ordered.indexOf(clue)
        return ordered[(i - 1 + ordered.size) % ordered.size]
    }

    private val orderedClues: List<Clue> by lazy {
        cluesFor(Direction.ACROSS) + cluesFor(Direction.DOWN)
    }
}
