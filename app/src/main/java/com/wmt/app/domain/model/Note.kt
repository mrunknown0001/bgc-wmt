package com.wmt.app.domain.model

enum class NoteScope(val raw: String, val label: String) {
    MINE("mine", "Mine"),
    SHARED("shared", "Shared"),
    ARCHIVED("archived", "Archived"),
}

data class NoteRow(
    val id: Int,
    val title: String,
    /** The matching passage while searching, else the start of the note. */
    val preview: String?,
    val folderName: String?,
    val owner: String?,
    val isMine: Boolean,
    val canEdit: Boolean,
    val updatedAt: String?,
)

data class NoteFolder(val id: Int, val name: String, val depth: Int, val noteCount: Int)

data class NoteList(
    val notes: List<NoteRow> = emptyList(),
    val folders: List<NoteFolder> = emptyList(),
    val counts: Map<NoteScope, Int> = emptyMap(),
)

data class Note(
    val id: Int,
    val title: String,
    /** HTML, as the web's editor writes it. */
    val content: String?,
    val owner: String?,
    val isMine: Boolean,
    val archived: Boolean,
    val role: String?,
    val canEdit: Boolean,
    val canArchive: Boolean,
    val canDelete: Boolean,
    val updatedAt: String?,
)
