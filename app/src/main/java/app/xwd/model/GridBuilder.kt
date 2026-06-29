package app.xwd.model

/**
 * Builds the cell list and clue skeleton for a crossword from a row-major
 * solution string ('.' for blocks), applying standard American numbering.
 * Shared by the .puz parser and the photo importer.
 */
object GridBuilder {

    data class Start(val number: Int, val direction: Direction, val cellIndex: Int)

    data class Result(
        val cells: List<Cell>,
        /** Numbered answer starts in .puz order: by number, across before down. */
        val starts: List<Start>,
    )

    fun build(
        solution: String,
        width: Int,
        height: Int,
        circled: BooleanArray? = null,
        shaded: BooleanArray? = null,
    ): Result {
        require(solution.length == width * height) {
            "Grid is ${solution.length} cells, expected ${width}x$height"
        }
        val cells = ArrayList<Cell>(solution.length)
        val acrossStarts = ArrayList<Start>()
        val downStarts = ArrayList<Start>()
        var number = 1
        for (i in solution.indices) {
            val ch = solution[i]
            if (ch == '.') {
                cells.add(Cell(solution = null))
                continue
            }
            val row = i / width
            val col = i % width
            val blockLeft = col == 0 || solution[i - 1] == '.'
            val openRight = col + 1 < width && solution[i + 1] != '.'
            val blockUp = row == 0 || solution[i - width] == '.'
            val openDown = row + 1 < height && solution[i + width] != '.'
            val startsAcross = blockLeft && openRight
            val startsDown = blockUp && openDown
            var cellNumber = 0
            if (startsAcross || startsDown) {
                cellNumber = number++
                if (startsAcross) acrossStarts.add(Start(cellNumber, Direction.ACROSS, i))
                if (startsDown) downStarts.add(Start(cellNumber, Direction.DOWN, i))
            }
            cells.add(
                Cell(
                    solution = ch.uppercaseChar(),
                    number = cellNumber,
                    circled = circled?.get(i) ?: false,
                    shaded = shaded?.get(i) ?: false,
                ),
            )
        }
        val starts = (acrossStarts + downStarts)
            .sortedWith(compareBy({ it.number }, { it.direction }))
        return Result(cells, starts)
    }

    fun wordCells(start: Int, dir: Direction, solution: String, width: Int, height: Int): List<Int> {
        val step = if (dir == Direction.ACROSS) 1 else width
        val result = ArrayList<Int>()
        var i = start
        while (i < solution.length && solution[i] != '.') {
            result.add(i)
            if (dir == Direction.ACROSS && i % width == width - 1) break
            if (dir == Direction.DOWN && i / width == height - 1) break
            i += step
        }
        return result
    }
}
