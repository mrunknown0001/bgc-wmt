package com.wmt.app.ui.todos

import com.wmt.app.domain.model.PersonalTodo

/** Moving to-dos about, kept out of the ViewModel so it can be tested on its own. */
object TodoOrder {

    /** Open items first, by position; completed ones at the bottom, as the list shows them. */
    val comparator = compareBy<PersonalTodo>({ it.isCompleted }, { it.position })

    /**
     * Moves one open to-do up ([by] = -1) or down (+1) among the open ones, and renumbers
     * every item so positions match what is on screen. Completed items keep their place at
     * the bottom: moving among them is not a thing anyone asks for. Returns the list
     * unchanged when the move would go past either end.
     */
    fun move(todos: List<PersonalTodo>, id: Int, by: Int): List<PersonalTodo> {
        val sorted = todos.sortedWith(comparator)
        val open = sorted.filterNot { it.isCompleted }.toMutableList()
        val from = open.indexOfFirst { it.id == id }
        val to = from + by
        if (from < 0 || to !in open.indices) return sorted
        open.add(to, open.removeAt(from))
        return (open + sorted.filter { it.isCompleted }).mapIndexed { index, todo -> todo.copy(position = index) }
    }
}
