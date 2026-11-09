package com.wmt.app.ui.notes

import androidx.lifecycle.SavedStateHandle
import com.wmt.app.domain.model.Note
import com.wmt.app.domain.model.NoteList
import com.wmt.app.domain.model.NoteScope
import com.wmt.app.domain.repository.NoteRepository
import com.wmt.app.testing.MainDispatcherRule
import com.wmt.app.util.Resource
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The autosave, which is the only thing standing between typing and losing it.
 *
 * A note is never saved by a button, so every way out of the screen has to carry the
 * last second and a half of typing with it. Leaving already did; archiving did not, and
 * archiving is the worse one to get wrong because the note turns read-only on the way,
 * so the queued save is then refused by its own guard and the text goes nowhere.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NoteViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun note(id: Int = 7, archived: Boolean = false) = Note(
        id = id,
        title = "Site visit",
        content = "<p>As it stands</p>",
        owner = "Admin",
        isMine = true,
        archived = archived,
        role = null,
        canEdit = true,
        canArchive = true,
        canDelete = true,
        updatedAt = "2026-11-08T10:00:00Z",
    )

    private class FakeNotes(private var stored: Note) : NoteRepository {
        /** Every write in the order it happened, so "saved before archiving" is checkable. */
        val calls = mutableListOf<String>()
        val saved = mutableListOf<Pair<String, String>>()
        val created = mutableListOf<Pair<String, String>>()
        val archivedTo = mutableListOf<Boolean>()

        override suspend fun notes(scope: NoteScope, query: String?, folder: String?): Resource<NoteList> =
            error("the single-note screen does not list")

        override suspend fun note(id: Int): Resource<Note> = Resource.Success(stored)

        override suspend fun create(title: String, contentHtml: String): Resource<Note> {
            calls += "create"
            created += title to contentHtml
            stored = stored.copy(id = 99, title = title, content = contentHtml)
            return Resource.Success(stored)
        }

        override suspend fun save(id: Int, title: String, contentHtml: String): Resource<String?> {
            calls += "save"
            saved += title to contentHtml
            stored = stored.copy(title = title, content = contentHtml)
            return Resource.Success("2026-11-08T10:05:00Z")
        }

        override suspend fun setArchived(id: Int, archived: Boolean): Resource<Unit> {
            calls += "archive"
            archivedTo += archived
            stored = stored.copy(archived = archived)
            return Resource.Success(Unit)
        }

        override suspend fun delete(id: Int): Resource<Unit> {
            calls += "delete"
            return Resource.Success(Unit)
        }
    }

    private fun existing(repo: FakeNotes) =
        NoteViewModel(repo, SavedStateHandle(mapOf("noteId" to 7)))

    @Test
    fun `typing is saved once it stops, not while it is still going`() = runTest(mainDispatcherRule.scheduler) {
        val repo = FakeNotes(note())
        val vm = existing(repo)

        vm.onBody("half a thou")
        advanceTimeBy(1_000)
        assertTrue("a save at one second would fire on every keystroke", repo.saved.isEmpty())

        advanceTimeBy(700)
        assertEquals(1, repo.saved.size)
        assertTrue(repo.saved.single().second.contains("half a thou"))
    }

    @Test
    fun `a keystroke restarts the wait rather than adding a save`() = runTest(mainDispatcherRule.scheduler) {
        val repo = FakeNotes(note())
        val vm = existing(repo)

        vm.onBody("one")
        advanceTimeBy(1_000)
        vm.onBody("one two")
        advanceTimeBy(1_000)
        assertTrue("the first wait should have been cancelled", repo.saved.isEmpty())

        advanceTimeBy(700)
        assertEquals(1, repo.saved.size)
        assertTrue(repo.saved.single().second.contains("one two"))
    }

    @Test
    fun `leaving carries the pending change out with it`() = runTest(mainDispatcherRule.scheduler) {
        val repo = FakeNotes(note())
        val vm = existing(repo)
        var left = false

        vm.onBody("typed on the way out")
        vm.flush { left = true }
        advanceUntilIdle()

        assertTrue("back must not wait out the debounce to save", repo.saved.isNotEmpty())
        assertTrue(repo.saved.last().second.contains("typed on the way out"))
        assertTrue(left)
    }

    @Test
    fun `archiving writes what was just typed instead of losing it`() = runTest(mainDispatcherRule.scheduler) {
        val repo = FakeNotes(note())
        val vm = existing(repo)

        vm.onBody("the last thing typed")
        // No waiting: the menu is used while the autosave is still pending.
        vm.setArchived(true)
        advanceUntilIdle()

        assertEquals("saved, then archived", listOf("save", "archive"), repo.calls)
        assertTrue(repo.saved.single().second.contains("the last thing typed"))
        assertEquals(listOf(true), repo.archivedTo)
    }

    @Test
    fun `reloading drops a save queued against text it has just replaced`() = runTest(mainDispatcherRule.scheduler) {
        val repo = FakeNotes(note())
        val vm = existing(repo)

        vm.onBody("typed, then archived")
        vm.setArchived(true)
        advanceUntilIdle()
        val afterArchive = repo.calls.size

        // The archive reloaded the note; nothing is outstanding against the old text.
        advanceTimeBy(5_000)
        assertEquals("the reload left a stale save behind", afterArchive, repo.calls.size)
    }

    @Test
    fun `a new note is not created until something is in it`() = runTest(mainDispatcherRule.scheduler) {
        val repo = FakeNotes(note(id = 0))
        val vm = NoteViewModel(repo, SavedStateHandle())

        // Opened, touched, and left empty again: there is nothing worth a row.
        vm.onBody("x")
        vm.onBody("")
        advanceTimeBy(5_000)
        assertTrue("an empty note should not reach the server", repo.created.isEmpty())

        vm.onBody("now it says something")
        advanceTimeBy(1_700)
        assertEquals(1, repo.created.size)
    }
}
