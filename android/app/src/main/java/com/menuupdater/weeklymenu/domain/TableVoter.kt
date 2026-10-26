package com.menuupdater.weeklymenu.domain

/**
 * Combines several OCR readings of the same menu image into one table by majority vote. The first
 * table is the reference: it decides the layout (days, meals, which items exist, their order). For
 * each of its dishes the other tables are searched for the same dish (nearest spelling within a
 * couple of jamo, in the same day and meal), and the spelling most readings agree on wins.
 *
 * A tie keeps the reference spelling, unless only a competing one is a known dish (see
 * [isKnown], normally the dish lexicon). A dish another reading dropped or invented does not
 * change the layout, because the reference governs it.
 */
object TableVoter {
    fun vote(tables: List<MenuTable>, isKnown: (String) -> Boolean = { false }): MenuTable {
        val reference = tables.first()
        if (tables.size == 1) return reference
        val others = tables.drop(1)

        val cafeteria = choose(
            reference.cafeteria,
            listOf(reference.cafeteria) + others.map { it.cafeteria },
            isKnown = { false },
        ).ifBlank { others.firstOrNull { it.cafeteria.isNotBlank() }?.cafeteria.orEmpty() }

        val days = reference.days.map { day ->
            day.copy(
                meals = day.meals.map { meal ->
                    val peers = others.mapNotNull { table ->
                        table.days.firstOrNull { it.month == day.month && it.day == day.day }
                            ?.meals?.firstOrNull { it.name == meal.name }?.items
                    }
                    meal.copy(items = voteItems(meal.items, peers, isKnown))
                },
            )
        }
        return MenuTable(cafeteria, days)
    }

    private fun voteItems(reference: List<String>, peers: List<List<String>>, isKnown: (String) -> Boolean): List<String> {
        val used = peers.map { BooleanArray(it.size) }
        return reference.map { item ->
            val candidates = mutableListOf(item)
            peers.forEachIndexed { p, items ->
                val index = nearest(item, items, used[p])
                if (index >= 0) {
                    used[p][index] = true
                    candidates += items[index]
                }
            }
            choose(item, candidates, isKnown)
        }
    }

    /** The unused item closest to [item] by jamo distance, or -1 when none is close enough. */
    private fun nearest(item: String, items: List<String>, used: BooleanArray): Int {
        val jamo = DishCorrector.jamo(item)
        val limit = maxOf(2, jamo.length / 8)
        var best = -1
        var bestDistance = limit + 1
        for (i in items.indices) {
            if (used[i]) continue
            val d = DishCorrector.distance(jamo, DishCorrector.jamo(items[i]), limit)
            if (d < bestDistance) {
                best = i
                bestDistance = d
            }
        }
        return best
    }

    private fun choose(reference: String, candidates: List<String>, isKnown: (String) -> Boolean): String {
        val counts = candidates.groupingBy { it }.eachCount()
        val top = counts.values.max()
        val leaders = counts.filterValues { it == top }.keys
        if (leaders.size == 1) return leaders.single()
        // A tie: keep the reference, unless it is unknown and exactly one rival is a known dish.
        if (reference in leaders && (isKnown(reference) || leaders.none { isKnown(it) })) return reference
        return leaders.firstOrNull { isKnown(it) } ?: reference
    }
}
