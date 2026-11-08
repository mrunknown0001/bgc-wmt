package com.wmt.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/*
 * Notes: /api/notes (WMT 54a6ae4). The index is the web page's own props, and it is not
 * paginated on either door -- the list is ranked by search relevance across everything
 * the user can reach. Sharing and folder management stay on the web.
 */

@JsonClass(generateAdapter = true)
data class NotesResponse(
    @Json(name = "notes") val notes: List<NoteRowDto> = emptyList(),
    @Json(name = "folders") val folders: List<NoteFolderDto> = emptyList(),
    @Json(name = "scope") val scope: String = "mine",
    @Json(name = "counts") val counts: NoteCountsDto = NoteCountsDto(),
)

@JsonClass(generateAdapter = true)
data class NoteRowDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "title") val title: String = "",
    @Json(name = "excerpt") val excerpt: String? = null,
    /** The matching passage when searching. */
    @Json(name = "snippet") val snippet: String? = null,
    @Json(name = "folder") val folder: IdNameDto? = null,
    /** A name, not an object. */
    @Json(name = "owner") val owner: String? = null,
    @Json(name = "is_mine") val isMine: Boolean = false,
    @Json(name = "archived") val archived: Boolean = false,
    /** owner, admin, editor or viewer. */
    @Json(name = "role") val role: String? = null,
    @Json(name = "can_edit") val canEdit: Boolean = false,
    @Json(name = "can_administer") val canAdminister: Boolean = false,
    @Json(name = "updated_at") val updatedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class NoteFolderDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "name") val name: String = "",
    @Json(name = "depth") val depth: Int = 0,
    @Json(name = "note_count") val noteCount: Int = 0,
)

@JsonClass(generateAdapter = true)
data class NoteCountsDto(
    @Json(name = "mine") val mine: Int = 0,
    @Json(name = "shared") val shared: Int = 0,
    @Json(name = "archived") val archived: Int = 0,
)

@JsonClass(generateAdapter = true)
data class NoteResponse(
    @Json(name = "note") val note: NoteDetailDto = NoteDetailDto(),
)

@JsonClass(generateAdapter = true)
data class NoteDetailDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "title") val title: String = "",
    @Json(name = "content") val content: String? = null,
    @Json(name = "owner") val owner: String? = null,
    @Json(name = "is_mine") val isMine: Boolean = false,
    @Json(name = "archived") val archived: Boolean = false,
    @Json(name = "role") val role: String? = null,
    @Json(name = "can_edit") val canEdit: Boolean = false,
    @Json(name = "updated_at") val updatedAt: String? = null,
    @Json(name = "note_folder_id") val noteFolderId: Int? = null,
    // Present on GET /api/notes/{id}; absent on a create or update reply, where they
    // default to the cautious answer until the note is read again.
    @Json(name = "can_archive") val canArchive: Boolean = false,
    @Json(name = "can_delete") val canDelete: Boolean = false,
)

/**
 * Title and body, always together. The server's update writes `content` whatever is
 * sent, so a request that left it out would empty the note -- there is deliberately no
 * way to build one here without a body.
 */
@JsonClass(generateAdapter = true)
data class NoteWriteRequest(
    @Json(name = "title") val title: String,
    @Json(name = "content") val content: String,
)

@JsonClass(generateAdapter = true)
data class NoteSavedResponse(
    @Json(name = "saved_at") val savedAt: String? = null,
)
