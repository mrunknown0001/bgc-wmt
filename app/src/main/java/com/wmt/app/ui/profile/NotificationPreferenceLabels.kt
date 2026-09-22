package com.wmt.app.ui.profile

/** One switch on the Profile screen: the server's key, a human label, and its state. */
data class NotificationPreferenceRow(
    val key: String,
    val label: String,
    val enabled: Boolean,
)

/**
 * Display names for the email notification switches.
 *
 * The server owns the key list — it sends a flat `{"email_task_assigned": true, …}` map —
 * so this only decorates what arrives: known keys get a written label and a fixed order,
 * and anything new the backend adds still renders (prettified from its key) instead of
 * silently vanishing from the screen.
 */
object NotificationPreferenceLabels {

    private val KNOWN = linkedMapOf(
        "email_task_assigned" to "Task assigned to me",
        "email_task_due_soon" to "Task due soon",
        "email_task_due_reminder" to "Task due reminder",
        "email_task_overdue" to "Task overdue",
        "email_task_comment" to "New comment on my task",
        "email_task_mention" to "I'm mentioned in a comment",
        "email_task_escalated" to "Task escalated",
        "email_comment_deleted" to "A comment is deleted",
    )

    fun label(key: String): String = KNOWN[key] ?: prettify(key)

    /** Known keys in their written order, then anything unrecognised, alphabetically. */
    fun ordered(preferences: Map<String, Boolean>): List<NotificationPreferenceRow> {
        val known = KNOWN.keys.filter { it in preferences }
        val unknown = preferences.keys.filterNot { it in KNOWN }.sorted()
        return (known + unknown).map { key ->
            NotificationPreferenceRow(
                key = key,
                label = label(key),
                enabled = preferences[key] == true,
            )
        }
    }

    private fun prettify(key: String): String =
        key.removePrefix("email_")
            .replace('_', ' ')
            .trim()
            .replaceFirstChar { it.uppercase() }
            .ifEmpty { key }
}
