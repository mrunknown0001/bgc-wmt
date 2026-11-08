package com.wmt.app.ui.todos

import com.wmt.app.domain.model.PersonalTodo
import org.junit.Assert.assertEquals
import org.junit.Test

/** Moving to-dos: open ones swap places, completed ones stay at the bottom, positions follow the screen. */
class TodoOrderTest {

    private fun todo(id: Int, position: Int, done: Boolean = false) = PersonalTodo(id, "T$id", done, position)

    private val list = listOf(todo(1, 0), todo(2, 1), todo(3, 2), todo(9, 3, done = true))

    @Test
    fun `moving down swaps with the next open item and renumbers everything`() {
        val moved = TodoOrder.move(list, id = 1, by = 1)

        assertEquals(listOf(2, 1, 3, 9), moved.map { it.id })
        assertEquals(listOf(0, 1, 2, 3), moved.map { it.position })
    }

    @Test
    fun `moving past either end changes nothing`() {
        assertEquals(listOf(1, 2, 3, 9), TodoOrder.move(list, id = 1, by = -1).map { it.id })
        // The last open item cannot slip below a completed one.
        assertEquals(listOf(1, 2, 3, 9), TodoOrder.move(list, id = 3, by = 1).map { it.id })
    }

    @Test
    fun `completed items do not move`() {
        assertEquals(listOf(1, 2, 3, 9), TodoOrder.move(list, id = 9, by = -1).map { it.id })
    }
}
