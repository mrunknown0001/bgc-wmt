package com.wmt.app.data.repository

import com.squareup.moshi.Moshi
import com.wmt.app.data.remote.api.WmtApi
import com.wmt.app.data.remote.dto.NoteWriteRequest
import com.wmt.app.data.remote.dto.toDomain
import com.wmt.app.data.remote.safeApiCall
import com.wmt.app.domain.model.Note
import com.wmt.app.domain.model.NoteList
import com.wmt.app.domain.model.NoteScope
import com.wmt.app.domain.repository.NoteRepository
import com.wmt.app.util.Resource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val api: WmtApi,
    private val moshi: Moshi,
) : NoteRepository {

    override suspend fun notes(scope: NoteScope, query: String?, folder: String?): Resource<NoteList> =
        safeApiCall(moshi) {
            // Folders only narrow your own notes, as on the web.
            api.notes(scope.raw, query?.trim()?.ifBlank { null }, folder.takeIf { scope == NoteScope.MINE }).toDomain()
        }

    override suspend fun note(id: Int): Resource<Note> = safeApiCall(moshi) { api.note(id).note.toDomain() }

    override suspend fun create(title: String, contentHtml: String): Resource<Note> =
        safeApiCall(moshi) { api.createNote(NoteWriteRequest(title, contentHtml)).note.toDomain() }

    override suspend fun save(id: Int, title: String, contentHtml: String): Resource<String?> =
        safeApiCall(moshi) { api.autosaveNote(id, NoteWriteRequest(title, contentHtml)).savedAt }

    override suspend fun setArchived(id: Int, archived: Boolean): Resource<Unit> =
        safeApiCall(moshi) { if (archived) api.archiveNote(id) else api.unarchiveNote(id); Unit }

    override suspend fun delete(id: Int): Resource<Unit> = safeApiCall(moshi) { api.deleteNote(id) }
}
