# WMT Android – Test Plan

Manual test cases for the WMT Android app (`com.mrunknown0001.wmt`). Each case lists the steps and the expected result. The expected results come from the app's code: view models, repositories and `WmtApi`. Where the server makes the decision, the case says so.

How to use it: copy the tables into a sheet or tick the boxes inline. Record build number, device, Android version and server for each run.

---

## 0. Setup

### 0.1 Environment

| Item | Value |
|---|---|
| Build | Debug (pre-fills staging URL) and Release (empty URL, https only) |
| Devices | One phone on a current Android version, and one older or small-screen device |
| Server | Staging WMT instance with the mobile API enabled |
| Web access | WMT web, logged in as an admin, to create projects and check results |

### 0.2 Test accounts

| Account | Purpose |
|---|---|
| **Admin** | Creates projects on the web, manages users and approval cover |
| **Member A** | Regular project member, assignee of most test tasks |
| **Member B** | Second member, for collaborators, comments from another user and realtime checks |
| **Approver** | Approver on an approval project's chain |
| **Requestor** | Can raise requests but has no approver access (pure requestor) |
| **Head** | Heads or leads a unit in the org chart (for My people) |
| **Non-approval user** | No approval access at all (checks the Approvals tab is hidden) |

### 0.3 Test data (create on the web before the run)

- **Project P1**: active, with Member A and Member B as members, at least two sections, and the task clock switched on.
- **Project P2**: active, task clock off, with at least one custom field of each type and one field locked to admins.
- One task in P1 that **depends on an unfinished task**, for the blocked-status check.
- **Approval project AP1**: a two-step chain, with custom fields that include a formula field.
- Two or more pages of notifications for Member A (more than 20).

> Projects cannot be created, edited or deleted in the app. `WmtApi` has no project write endpoints by design, so project creation is tested on the web and the app is tested to pick the project up.

---

## 1. Server setup

| ID | Steps | Expected |
|---|---|---|
| SET-01 | Fresh install, Release build. Open the app. | Server Setup screen shows with an empty URL field. |
| SET-02 | Fresh install, Debug build. | URL field is pre-filled with the staging URL. |
| SET-03 | Leave the URL empty and tap Connect. | Error: "Please enter a server URL." No network call. |
| SET-04 | Release build: enter `http://<server>` and tap Connect. | Error: "This app only connects over a secure connection. Use the server's https:// address." |
| SET-05 | Enter the host without a scheme (`wmt.example.com`). | The app prepends `https://`, the health check passes, and Login opens. |
| SET-06 | Enter an unreachable or misspelled host. | Loading spinner, then the error from the health check. Stays on Setup. |
| SET-07 | Connect successfully, kill the app and reopen it. | Setup is skipped because the URL was saved. |

## 2. Login and session

| ID | Steps | Expected |
|---|---|---|
| AUTH-01 | Tap Sign in with both fields empty. | "Email is required" and "Password is required" under the fields. No request is sent. |
| AUTH-02 | Type in a field that shows an error. | That field's error clears. |
| AUTH-03 | Enter a valid email and a wrong password. | The server's message is shown. Stays on Login. |
| AUTH-04 | Enter an email with leading or trailing spaces and the right password. | Login succeeds because the email is trimmed. |
| AUTH-05 | Valid credentials. | Lands on Home (Dashboard). The greeting uses the first name. |
| AUTH-06 | After AUTH-05, send a push from the web (for example, assign a task). | A push arrives, which shows the FCM token was registered. |
| AUTH-07 | Kill and reopen the app. | Stays signed in and opens on Home. |
| AUTH-08 | Profile → Log out. | Returns to Login. Reopening the app shows Login. |
| AUTH-09 | Revoke the token on the web, then pull to refresh in the app. | The app returns to Login and does not loop on errors. |
| AUTH-10 | Leave the app signed in past the token refresh point. | The token refreshes silently and no screen shows an auth error. |

## 3. Projects

### 3.1 Creating a project (web) and seeing it in the app

| ID | Steps | Expected |
|---|---|---|
| PRJ-01 | On the web, create project P3 and add Member A. In the app, go to the Projects tab. | P3 appears without reinstalling. If the tab was already open, it appears after switching tabs or pulling to refresh. |
| PRJ-02 | On the web, create P4 **without** Member A. | P4 does not appear for Member A. |
| PRJ-03 | On the web, change P3's name or status. Return to the Projects tab. | The new name or status badge shows (the list refetches when the screen becomes visible again). |
| PRJ-04 | On the web, archive P3. Refresh the app. | P3 is shown or hidden according to the server's list rules. Note which one happens. |
| PRJ-05 | Look for create, edit or delete project actions in the app. | None exist. That work is web-only. |

### 3.2 Project list

| ID | Steps | Expected |
|---|---|---|
| PRJ-10 | Open Projects. | Spinner on the first load, then the list with task counts and progress. |
| PRJ-11 | Type part of a project name in search. | Results update about 300 ms after typing stops. Matching is done by the server. |
| PRJ-12 | Clear the search. | The full list returns. |
| PRJ-13 | Search, open a project, then go back. | The search results stay. They are not replaced by the full list. |
| PRJ-14 | Pull to refresh. | The refresh indicator shows and the list updates. |
| PRJ-15 | Turn on airplane mode and reopen Projects. | The offline banner shows, along with the cached list if there is one. |

### 3.3 Project detail

| ID | Steps | Expected |
|---|---|---|
| PRJ-20 | Open P1. | Tasks are grouped by section. Tasks without a section sit in the "Other" group. |
| PRJ-21 | Tap a task's complete control. | The row flips to Done at once and confetti plays, then the project reloads. |
| PRJ-22 | Tap it again. | It goes back to To Do. |
| PRJ-23 | Tap complete on the task with an unfinished dependency. | The row flips, then reverts on reload. **Known gap:** project detail does not show the server's refusal message, unlike My Tasks (TASK-34). |
| PRJ-24 | With P1 open on the phone, have Member B create, edit and delete tasks in P1 on the web. | The list updates live through the realtime channel, with no manual refresh. |
| PRJ-25 | Open a task, change it, then go back. | The project reflects the change. |

---

## 4. Creating tasks

### 4.1 Project task (New Task dialog in project detail)

| ID | Steps | Expected |
|---|---|---|
| TASK-01 | Open the New Task dialog with the title empty. | Create is disabled. |
| TASK-02 | Type only spaces in Title. | Create stays disabled. |
| TASK-03 | Enter a title only and tap Create. | The task is created with status To Do, priority Medium, no assignee, no section and no due date. The dialog closes and the task appears in the list. |
| TASK-04 | Fill in every field: title, description, status, priority, assignee, section, due date and two collaborators. | All of the values show in task detail and on the web. |
| TASK-05 | Open the Section dropdown. | Only real sections and "No section" are listed. The synthetic "Other" group is not offered. |
| TASK-06 | Open the dialog in a project with no sections. | No Section dropdown is shown. |
| TASK-07 | Open the dialog in a project with no assignable users. | No Assignee dropdown is shown. |
| TASK-08 | Description of only spaces. | Saved as no description (null), not as blank text. |
| TASK-09 | Set recurrence to weekly with interval 2. | Saved as recurring, weekly, every 2. |
| TASK-10 | Set the recurrence interval to 0, then 999, then letters. | The interval is clamped to 1–365. Letters are sent as no interval, which defaults to 1. |
| TASK-11 | Make the server reject the task (for example, an over-long title). | The server's message shows in red in the dialog, which stays open. |
| TASK-12 | Tap Create, then try to dismiss the dialog while it is saving. | The dialog cannot be dismissed and Cancel is disabled while the spinner shows. |
| TASK-13 | Create a task assigned to Member B. | Member B gets a push and an inbox notification. |

### 4.2 Personal task (My Tasks)

| ID | Steps | Expected |
|---|---|---|
| TASK-20 | In My Tasks, create a task with title, priority and due date. | It is created through `/api/tasks` with no project, and appears in My Tasks under the right due-date group. |
| TASK-21 | Create a personal recurring task. | Recurrence is saved. The interval defaults to 1 if left empty. |
| TASK-22 | Open the personal task. | Task detail works. There is no task clock (the clock is project-only). |
| TASK-23 | Make the server reject the create. | The error shows in the form and the list is unchanged. |

### 4.3 Subtasks

| ID | Steps | Expected |
|---|---|---|
| TASK-25 | In task detail, add a subtask with a title. | The subtask appears under the task with status To Do and priority Medium. |
| TASK-26 | Try to add a subtask with an empty or whitespace title. | Nothing is sent. |
| TASK-27 | Open a subtask and look for the subtask composer. | The composer is replaced by the server's reason (only one level of nesting is allowed). |
| TASK-28 | Tap Add twice quickly. | Only one subtask is created. |

## 5. Monitoring tasks

### 5.1 Dashboard (Home)

| ID | Steps | Expected |
|---|---|---|
| MON-01 | Open Home. | The greeting shows the first name, with stats and task lists. |
| MON-02 | Reopen the app with cached data. | The cached dashboard shows at once, then updates from the network. |
| MON-03 | Have Member B assign a task to Member A on the web while Home is open. | Home refreshes on its own (inbox realtime event or foreground push). |
| MON-04 | Complete a task from task detail and go back. | The Home stats reflect it. |
| MON-05 | Pull to refresh while offline. | The error shows and cached data stays on screen. |

### 5.2 My Tasks list

| ID | Steps | Expected |
|---|---|---|
| TASK-30 | Prepare tasks due yesterday, today, in 3 days, in 20 days, and with no due date. Open My Tasks. | The groups appear in this order: Overdue, Due Today, Upcoming (7 days), Later, No Due Date. Empty groups are hidden. |
| TASK-31 | Check the stats row. | Open counts tasks that are not Done or Cancelled. Overdue and Due Today count only open tasks. |
| TASK-32 | Filter by status, by priority, and search by title (case-insensitive). | The list narrows. The stats still count every task, not only the filtered ones. |
| TASK-33 | Clear the filters. | The full list returns. |
| TASK-34 | Tick complete on the task with an unfinished dependency. | The row flips, then reverts, and the server's message shows. No confetti. |
| TASK-35 | Tick complete on a normal task. | The row flips, confetti plays once the server accepts, and the list refreshes. |
| TASK-36 | Untick a done task. | It goes back to To Do. |
| TASK-37 | Filters return nothing. | The empty state offers to clear the filters. |

### 5.3 Calendar

| ID | Steps | Expected |
|---|---|---|
| CAL-01 | Switch My Tasks to calendar view. | Tasks show on their due dates. |
| CAL-02 | Add Member A as a collaborator (not the assignee) on a task due this month. | The task shows in the calendar (from `/api/calendar`), but not in the My Tasks list. |
| CAL-03 | Move to the next and previous months. | Each month loads its own tasks. |
| CAL-04 | Go offline and change month. | The calendar keeps showing the assigned tasks it already has, with no error. |

---

## 6. Updating tasks (task detail)

### 6.1 Status and "submitting" work

"Submitting" a task here means moving it to **In Review** or **Done**.

| ID | Steps | Expected |
|---|---|---|
| UPD-01 | Change status to In Progress, then In Review. | The badge updates. The web shows the same status. Activity logs each change. |
| UPD-02 | Change status to Done. | Confetti plays once. Setting Done again on a task that is already Done does not replay it. |
| UPD-03 | Change status while the server refuses (dependency, or no permission). | The status stays as it was and the server's message shows. |
| UPD-04 | Tick a subtask done. | Confetti plays and the subtask shows Done after the refresh. |
| UPD-05 | Tick a subtask the server refuses. | It snaps back and the server's message shows (not a silent revert). |
| UPD-06 | Set Cancelled. | The task leaves the Open count in My Tasks. |

### 6.2 Edit task

| ID | Steps | Expected |
|---|---|---|
| UPD-10 | Edit the title, description, priority, assignee, start date and due date, then save. | The dialog closes and the detail reloads with the new values. |
| UPD-11 | Remove every collaborator and save. | The web shows no collaborators. An empty list is sent and synced. |
| UPD-12 | Turn recurrence off. | The task is no longer recurring. |
| UPD-13 | Save an invalid edit (for example, due date before start date, if the server checks it). | The server's error shows in the dialog, which stays open. |
| UPD-14 | Reassign to Member B. | Member B is notified. Member A's My Tasks drops the task after a refresh. |

### 6.3 Delete task

| ID | Steps | Expected |
|---|---|---|
| UPD-20 | Delete a task and confirm. | The app goes back. The task is gone from the project, My Tasks and the web. |
| UPD-21 | Delete without permission (as a member who is not the owner, if the server forbids it). | The error shows and the task remains. |

### 6.4 Comments and attachments

| ID | Steps | Expected |
|---|---|---|
| UPD-30 | Post a text comment. | It appears at the top. Member B sees it live in their open task detail. |
| UPD-31 | Tap Send with an empty composer and no attachments. | Nothing is sent. |
| UPD-32 | Post an attachment with no text. | Allowed. The attachment shows and opens. |
| UPD-33 | Attach an image, a PDF, an Excel file and a video. Take a photo with the camera. | All upload and preview or open. |
| UPD-34 | Attach a file larger than the general cap (server setting, default 10 MB). | The app refuses it before upload and names the limit. |
| UPD-35 | Attach a video between the general cap and the video cap (default 50 MB). | Accepted. A video above the video cap is refused. |
| UPD-36 | Change the upload cap on the web and reopen the task. | The new cap applies. |
| UPD-37 | Create more than 20 comments. Scroll and load older ones. | Older comments append with no duplicates. The button stops when no more remain. |
| UPD-38 | Delete your own comment. | It disappears, including from the paged-in older list. |
| UPD-39 | Look at Member B's comment. | No delete option. A forced attempt returns 403. |
| UPD-40 | Mention @Member B. | Member B gets a "mentioned" notification. |

### 6.5 Tags, custom fields, activity

| ID | Steps | Expected |
|---|---|---|
| UPD-50 | Open the tag editor. | Suggestions load from tags already in use. |
| UPD-51 | Add and remove tags and save. | The task holds exactly those tags, as the server stored them. |
| UPD-52 | Try to add more than 20 tags. | The server refuses and the message shows. |
| UPD-53 | In P2, edit each custom field type and save. | The values save and the detail reloads from the server. |
| UPD-54 | Save with no changes. | The editor closes and nothing is sent. |
| UPD-55 | Enter a value the server rejects (422 on `values.<id>`). | The editor stays open with the message next to that field. |
| UPD-56 | As a member, look for the admin-locked field. | It is not editable. If the server names it in a 422, the message shows as a notice. |
| UPD-57 | Trigger an automation rule that fails. | The save succeeds and a snackbar shows the server's automation warning. |
| UPD-58 | Open the activity for a custom-field change with previous text. | A sheet shows the full previous value. |
| UPD-59 | Load more than 20 activity rows. | Older rows append and the button disappears at the end. |

---

## 7. Task clock (P1, clock on)

| ID | Steps | Expected |
|---|---|---|
| CLK-01 | Open a P1 task assigned to you. | The clock strip shows Start. |
| CLK-02 | Open a P2 task (clock off) and a personal task. | No clock strip. |
| CLK-03 | Tap Start. | The clock runs, the task moves to In Progress, and the detail reloads. |
| CLK-04 | Tap Pause. | The dialog opens with the minutes the server suggests (from pause-preview). The phone does not count them itself. |
| CLK-05 | Change the minutes, add a note, confirm. | The clock pauses. A time log for today with those minutes appears in the timesheet. |
| CLK-06 | Open the pause dialog and dismiss it. | The clock keeps running and nothing is recorded. |
| CLK-07 | Tap Resume. | The clock runs again. |
| CLK-08 | Tap Start or Pause twice quickly. | One request is sent. The buttons wait while one is in flight. |
| CLK-09 | Start the clock on the web, then open the task in the app. | The app shows the clock running. |
| CLK-10 | Make the server refuse a clock action (for example, as a non-assignee). | The server's message shows and the clock state is unchanged. |

## 8. Timesheet and time corrections

| ID | Steps | Expected |
|---|---|---|
| TS-01 | Open the timesheet from task detail. | Entries and the task total are listed. |
| TS-02 | Delete an entry somebody typed. | "Entry removed." and the sheet reloads. |
| TS-03 | Delete an entry the clock wrote. | Refused with the server's explanation (422). The entry stays. |
| TS-04 | Amend an entry with `1.5`, then `1:30`, then `90m`. | Each is accepted as typed. The server parses it. |
| TS-05 | Amend as someone who does not sign off corrections. | "Correction sent for a decision." The figure is unchanged until decided. |
| TS-06 | Amend as the person who signs off. | "Entry updated." The figure changes at once. |
| TS-07 | Amend with an invalid duration (`abc`). | The field error shows under the duration box. |
| TS-08 | Add an entry for a past day that has none. | "Request sent for a decision." or "Entry added.", depending on who asks. |
| TS-09 | Add an entry for a future date. | Refused. |
| TS-10 | As the approver, approve a pending correction. | "Correction approved." The figure and the total update. |
| TS-11 | Reject a correction with a note. | "Correction rejected." The figure is unchanged and the requester sees the outcome. |
| TS-12 | Open Time corrections. | The To decide and Mine tabs show as the server allows. Status filter and paging work. |
| TS-13 | Pull to refresh offline with the sheet loaded. | The sheet stays and the error shows as a message. |

## 9. Approvals

### 9.1 Access

| ID | Steps | Expected |
|---|---|---|
| APR-01 | Sign in as the non-approval user. | No Approvals tab. The bar has four tabs. |
| APR-02 | Sign in as the Approver. | The Approvals tab shows with badge counts. |
| APR-03 | As the Requestor, start a new request. | AP1 is offered (from the available-projects list), even though the full project list is refused. |

### 9.2 Raising (submitting) a request

| ID | Steps | Expected |
|---|---|---|
| APR-10 | Fill in title, description, section and every field type, then submit. | The request is created as Pending and shows in My requests. The formula field is read-only and not sent. |
| APR-11 | Submit with a required field empty. | The server's 422 shows next to the field. |
| APR-12 | Attach 5 files, then try a sixth. | Five are accepted. The server caps attachments at 5. |
| APR-13 | Edit your own request where the server allows it. | The changes save. |

### 9.3 Deciding

| ID | Steps | Expected |
|---|---|---|
| APR-20 | As Approver, approve a request from the queue with a comment. | The buttons stay disabled until the server answers. The server's message shows, the request moves to step 2, and the timeline reloads. |
| APR-21 | Reject a request. | Status becomes Rejected and the Requestor is notified. |
| APR-22 | Two approvers open the same request. One decides, then the other decides. | The second sees the server's refusal and the screen reloads to the real state. |
| APR-23 | Tap Approve twice quickly. | One decision is recorded. |

### 9.4 Requestor actions

| ID | Steps | Expected |
|---|---|---|
| APR-30 | Resubmit a request that was returned (changes requested or rejected). | "Request resubmitted." A fresh attempt shows in the timeline. |
| APR-31 | Open a request that is still moving through its chain. | Resubmit is not offered. |
| APR-32 | Cancel a request. | The screen closes and the request is gone from the lists. |
| APR-33 | Comment on a request. | The comment shows at once, with no duplicate after a refresh. |

### 9.5 Lists and cover

| ID | Steps | Expected |
|---|---|---|
| APR-40 | Open the decision trail. Filter by decision and project, search, and page. | Results match the web. |
| APR-41 | Open Shared requests. | Requests others shared with you, newest share first. |
| APR-42 | Arrange approval cover for a date range, then remove it. | Creating reports how many pending approvals the stand-in got. Removing reports how many were taken off. Only users who manage users may arrange cover for others. |

## 10. Inbox

| ID | Steps | Expected |
|---|---|---|
| INB-01 | Open Inbox. | Notifications show, newest first. |
| INB-02 | Scroll past 20 items. | The next page loads with no duplicates, until the server says there are no more. |
| INB-03 | Switch between All, Unread, Mentions, Bookmarked and Archived. | Each filter shows the server's list. |
| INB-04 | Tap an unread notification. | It is marked read at once and opens its task, project or request. |
| INB-05 | Swipe to mark read. Then tap Mark all read. | Rows show as read and the tab badge drops. |
| INB-06 | In Bookmarked, remove a bookmark. | The row leaves the list. |
| INB-07 | Archive from All. Unarchive from Archived. | The row leaves the current list each time. |
| INB-08 | Go offline and bookmark a row. | The row reverts and "That bookmark did not save." shows. The scroll position stays. |
| INB-09 | Scroll to page 3, then receive a new notification. | It appears at the top without jumping back to page 1. |
| INB-10 | Compare the badge with the web's unread count. | They match. |

## 11. Personal to-dos

| ID | Steps | Expected |
|---|---|---|
| TODO-01 | Add, complete, uncomplete, rename and delete a to-do. | Each change persists after reopening. |
| TODO-02 | Reorder to-dos and reopen the screen. | The order is kept. |
| TODO-03 | Clear completed, then clear all. | Only the matching items are removed. |

## 12. Notes

| ID | Steps | Expected |
|---|---|---|
| NOTE-01 | Create a note. | It is created on first save and shows in Mine. |
| NOTE-02 | Edit a note and leave the screen. | Title and body are saved together. The web shows the same text. |
| NOTE-03 | Search notes and filter by folder or unfiled. | Results match the web. |
| NOTE-04 | Open a note shared with you. | It shows in the shared scope. |
| NOTE-05 | Archive, unarchive and delete a note. | Each takes effect on the web. |

## 13. Search, profile, people

| ID | Steps | Expected |
|---|---|---|
| SRCH-01 | Global search for a project name and a task title. | Both kinds of result show. Tapping one opens it. |
| PRF-01 | Change password with a wrong current password, then mismatched confirmation, then valid values. | The server's errors show, then its success message. |
| PRF-02 | Log out other devices with your password. | The other device is signed out. This one stays signed in. |
| PRF-03 | Toggle an email notification switch. Repeat offline. | It saves. Offline, it rolls back. |
| PRF-04 | Switch theme between system, light and dark, then restart. | The choice is kept and the first frame does not flash the wrong theme. |
| PRF-05 | Change the accent colour on the web and reopen the app. | The app uses the new accent. |
| PPL-01 | Sign in as Head, then as Member A. | Head sees My people. Member A does not. |
| PPL-02 | As Head, open the overdue list and a person's overview. | Both load. A person the server refuses shows its refusal. |

## 14. Push, realtime, offline

| ID | Steps | Expected |
|---|---|---|
| PUSH-01 | With the app in the background, assign Member A a task from the web. | A system notification shows. Tapping it opens the task. |
| PUSH-02 | Repeat with the app open on Home, My Tasks and Inbox. | Each screen refreshes on its own. |
| PUSH-03 | Deny notification permission (Android 13+). | The app still works and inbox data still loads. |
| OFF-01 | Go offline with cached data, then open Home, My Tasks, Projects and Inbox. | Cached data shows with the offline banner. |
| OFF-02 | Try any write action offline. | It fails with a message and no data is lost. |
| OFF-03 | Reconnect. | Screens refresh and the banner disappears. |

## 15. Automated tests

Run these before each manual pass:

- Unit tests: `./gradlew testDebugUnitTest`
- Instrumented tests (device or emulator): `./gradlew connectedDebugAndroidTest`

## 16. Run log

| Date | Build | Device / Android | Server | Tester | Failed IDs | Notes |
|---|---|---|---|---|---|---|
| | | | | | | |
