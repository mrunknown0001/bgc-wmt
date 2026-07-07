# Prompt: Update the WMT backend API for the mobile app

Copy everything below this line into a Claude Code session running in the WMT Laravel repo (https://github.com/mrunknown0001/wmt.git) — ideally the working copy actually deployed on the server, since it may already contain changes that aren't on GitHub.

---

The WMT Android app talks to this Laravel backend over the Sanctum bearer-token API in `routes/api.php`. The app has grown faster than the API; implement the items below in priority order. The running server may already have some of this partially implemented (mobile clients have observed `project_id: null` in task payloads, which stock GitHub code cannot produce) — so **start with an audit, not with code**.

## Step 1 — Audit before changing anything

1. Run `php artisan route:list --path=api` and compare against the routes specified below; note which already exist.
2. Check `git status` / `git log` for uncommitted or unpushed changes that diverge from GitHub `714b746`.
3. Check whether `app/Rules/CommentAttachmentFile.php` exists (item 4 below may already be applied via a patch).
4. Report what you found, then implement only what's missing or broken.

## Critical conventions

1. **Route shadowing gotcha**: `routes/web.php` defines literal `/api/*` routes inside the web `auth` + CSRF middleware group (`/api/search`, `/api/notifications/recent`, `/api/device-tokens`, `/api/ai/*`). These SHADOW same-path routes in `routes/api.php` — mobile Bearer requests hit the web route and fail with 419. This already happened once; that's why `/api/mobile/device-tokens` exists. **Never register a new API route whose literal path collides with a web.php `/api/*` route; use `/api/mobile/...` or another distinct path.**
2. **Response shapes**: the mobile app does NOT expect a `{ "data": ... }` envelope except where Laravel pagination produces one (`/api/projects`, `/api/notifications`). Match the existing `app/Http/Controllers/Api/` style — plain JSON objects, `toIso8601String()` timestamps, users as `{id, name}`.
3. **`project_id` must always be serialized on every task** in every endpoint (`/api/dashboard` myRecentTasks, `/api/my-tasks` groups, project show, task show, patch/update responses). It may be `null` (personal tasks) but must never be omitted, and for project tasks must never be null. A mobile release crashed because a task in `myRecentTasks` carried `project_id: null` while belonging to a query that dropped the column — audit any custom `select(...)`/`map(...)` serialization for this.
4. **Auth**: everything goes in the existing `auth:sanctum` group in `routes/api.php`.
5. **Reuse web logic**: most features exist as web controllers (`SearchController`, `ActivityLogController`, `NotificationPreferenceController`, `TaskSectionController`, `ExecutiveDashboardController`, `ProjectAutomationRuleController`). Delegate or extract shared logic rather than duplicating.
6. Add feature tests per endpoint (auth required, happy path, authorization failure). Run the suite when done.

## Item 1 — Standalone (personal) tasks — THE APP SHIPS THIS NOW, highest priority

The Android app has a "+" button on My Tasks that creates project-less tasks and drives them through top-level task routes. Contract:

- Migration: `tasks.project_id` → nullable (`->nullable()->change()`, keep the FK). Audit everything that assumes `$task->project` exists (policies, notifications, activity/escalation logging, kanban position queries, `RecurringTaskService` — its `max(position)` groups by project_id) for null-safety.
- Routes (top-level, NOT nested under projects):
  - `POST /api/tasks` — body `{title, status?, priority?, description?, due_date?, start_date?, is_recurring?, recurrence_frequency?, recurrence_interval?}` → 201 `{task}`. Any authenticated user; `assigned_to` defaults to the creator (so it shows in their My Tasks); `created_by` = creator.
  - `GET /api/tasks/{task}` — same shape as the nested task `show`: `{task, comments, activities, members, subtasks}` (`members` = creator + assignee). 404 if the task belongs to a project. Creator/assignee/admin only.
  - `PUT /api/tasks/{task}` — same body/response as nested update. Creator/assignee/admin.
  - `PATCH /api/tasks/{task}/patch` — `{field, value}` → `{success, task}`, same fields as nested patch.
  - `DELETE /api/tasks/{task}` → 204. Creator/admin.
  - `POST /api/tasks/{task}/comments` — multipart `body` + `attachments[]`, same validation/response as nested storeComment; broadcast `comment.created` on `task.{id}`.
  - `DELETE /api/tasks/{task}/comments/{comment}` + the attachment download route, mirroring nested.
- `/api/my-tasks` and `/api/dashboard` must include personal tasks (assigned to the user) with `project_id: null` serialized explicitly.

## Item 2 — `project_id` serialization bug (see convention 3)

Mobile observed `project_id: null` on tasks in `/api/dashboard` `myRecentTasks[...]` and `/api/my-tasks` `taskGroups.*`. If item 1's migration is the cause, that's fine — but verify no query drops the column for *project* tasks and that every task-serializing endpoint includes it.

## Item 3 — Notification preferences — the app already calls these (currently 404)

- `GET /api/notification-preferences` → flat object `{"task_assigned": true, "comment_deleted": false, ...}` — same keys and defaults as the web `NotificationPreferenceController` (all on except `comment_deleted`).
- `POST /api/notification-preferences` — body `{"type": "<key>", "enabled": bool}` → persist for the user, return `{"message": "..."}`. Validate `type` against known keys.

## Item 4 — Comment attachment validation (skip if `app/Rules/CommentAttachmentFile.php` exists)

The app uploads videos (≤50MB), Excel/CSV, and JPEG-transcoded photos as comment attachments; stock validation only allows `mimes:jpg,jpeg,png,webp,pdf`.

- Create `App\Rules\CommentAttachmentFile`: videos (`mp4, mov, webm, 3gp, mkv` or `video/*` mime) fixed 50MB cap; images (`jpg, jpeg, png, webp, gif, heic, heif`), `pdf`, spreadsheets (`xlsx, xls, csv`) capped at `Setting::current()->max_upload_size` MB. Reject everything else. (HEIC matters — it's the default camera format on many phones.)
- Apply to `attachments.*` in BOTH `Api\TaskController::storeComment` (and the new personal-task comment route) AND the web `StoreTaskCommentRequest`.
- `docker/php/custom.ini`: `upload_max_filesize = 60M`, `post_max_size = 120M`, `max_file_uploads = 20`. Check any proxy `client_max_body_size`.

## Item 5 — Global search

- `GET /api/mobile/search?q=...` (distinct path — `/api/search` is shadowed by web.php) reusing `SearchController` logic → `{"projects": [{id, name, status}], "tasks": [{id, title, project_id, project_name, status, priority, due_date}]}`, scoped to what the user can see, ~20 per group.

## Item 6 — App settings for clients

- `GET /api/settings` → `{"app_name", "primary_color", "max_upload_size": <MB int>, "video_max_upload_size": 50}` so clients can validate sizes without hardcoding.

## Item 7 — Comment/activity pagination

`show` caps at the latest 20 with no way to load more.

- `GET /api/projects/{project}/tasks/{task}/comments?before_id=<id>&limit=20` → `{"comments": [...], "has_more": bool}` (same comment shape, newest-first). Same for `.../activities`. Mirror both on the personal-task routes.

## Item 8 — Task sections CRUD

Mirror the web `TaskSectionController` under the API:

- `POST /api/projects/{project}/sections` `{name}` → 201 `{section}`
- `PUT /api/projects/{project}/sections/{section}` `{name}` → `{section}`
- `DELETE /api/projects/{project}/sections/{section}` → 204 (match web behavior for orphaned tasks)
- `POST /api/projects/{project}/sections/reorder` `{ordered_ids: [...]}` → `{success: true}`
- Same authorization as web (project owner / manage-tasks).

## Item 9 — Org-wide activity log

- `GET /api/activity-log?page=&search=&project_id=` — paginated (paginator envelope OK), reusing `ActivityLogController` filters/authorization. Entries: `{id, description, field, old_value, new_value, user: {id,name}, task: {id, title, project_id}, project_name, created_at}`.

## Item 10 — Executive dashboard (role-gated)

- `GET /api/executive-dashboard` with `?division_id=` / `?department_id=` / `?team_id=` drill-downs mirroring `ExecutiveDashboardController`. Same roles as web (admin, supervisor, division_head, executive); 403 otherwise.

## Item 11 — Automation rules (read-only)

- `GET /api/projects/{project}/automation-rules` → `{rules: [{id, name, trigger, conditions, actions, is_active}]}` with the web visibility rules.

## Item 12 — Calendar feed (nice-to-have)

- `GET /api/calendar?month=YYYY-MM` → `{tasks: [Task]}` — tasks due that month that the user is assigned to OR collaborates on (mobile's calendar currently only sees assigned tasks), including personal tasks. Reuse `MyTaskController` serialization.

## After implementing

- Run the test suite.
- Output `php artisan route:list --path=api` in your summary, plus a list of every item you skipped as already-implemented and every deviation from the shapes above.
- Remind the operator to run migrations and restart the containers.
