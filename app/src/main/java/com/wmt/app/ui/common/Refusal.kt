package com.wmt.app.ui.common

import com.wmt.app.util.AppError

/**
 * The refusal a form must say out loud, or null when every part of it already has a
 * field to sit under.
 *
 * A 422 names the fields it objected to, and a form shows those against the fields
 * themselves, which reads far better than a snackbar. The trap is treating "there are
 * field errors" as "a field is showing them": a form has a field for the keys it chose
 * to render, and the server is free to object to anything -- a value locked for this
 * person, a rule it keys its own way, a whole-request check like an overlap. Those have
 * nowhere to appear, and silence then looks exactly like a button that does not work.
 *
 * So: anything with nowhere to go is returned here, to be said plainly instead.
 *
 * @param shownKeys the error keys this form renders in place, exactly as the server
 *   spells them -- `"ends_on"`, `"values.12"`.
 */
fun refusalToAnnounce(error: AppError, shownKeys: Set<String>): String? {
    val validation = error as? AppError.Validation ?: return error.message
    // A validation error with no fields names nothing to sit against, so its summary is
    // all there is.
    if (validation.fieldErrors.isEmpty()) return error.message
    return validation.fieldErrors
        .filterKeys { key -> key !in shownKeys }
        .values
        .flatten()
        .firstOrNull()
}
