# Cinderblock UI and flows

As-built specification of every screen, control, and user flow in the Cinderblock app (PWA name: **CINDERBLOCK Foundation Strength**). This describes the product as it ships. Implementation notes live in `TECHNICAL_SPEC.md`.

Cinderblock is a local-first strength and running log. The home page is the dashboard. Training, running, body metrics, AI discussion, the program editor, and settings are separate routes. Signing in is optional except where cloud or AI features require a Supabase account.

## 1. Routes

Unknown paths redirect to `/`.

| Route | Screen | Notes |
|---|---|---|
| `/` | Home | Dashboard. Only screen with the global header. |
| `/login` | Sign in | Optional. Clears the “skipped login” flag on visit. |
| `/gym` | Training log, calendar | Date defaults to today. |
| `/gym/:date` | Training log, calendar | `date` is `YYYY-MM-DD`. Invalid dates replace to `/gym`. |
| `/gym/:date/select` | Workout picker | |
| `/gym/:date/workout` | Active session | Falls back to the picker if that date has no program workout. |
| `/gym/explore` | Workout list preview | Does not log sets. |
| `/gym/explore/:workoutKey` | One workout preview | |
| `/running` | Run plan | |
| `/running/session` | Live run timer | |
| `/metrics` | Body metrics | |
| `/settings` | Settings | |
| `/program-editor` | Manual program editor | Reached from Explore → Edit. |
| `/ai-chat/create`, `/ai-chat/edit`, `/ai-chat/discuss` | Discuss with AI | All three modes render the same discuss experience. |

Weeks start on Monday.

## 2. Shared chrome and behavior

### Shell

- Portrait-primary standalone PWA. Feature screens are a single column, `max-w-2xl`, except the program editor (`max-w-4xl`) and the home content column (`max-w-3xl`).
- Feature screens (training, running, metrics, settings) use a sticky top bar: **← CINDERBLOCK** (back to home), a centered title, and the date on the right. Running uses a chevron plus the same label.
- Home uses a fixed header: app mark and wordmark (links to `/`), an **ONLINE** pill from the `sm` breakpoint up, a sparkles button (**Discuss with AI**), and a menu button.
- The menu is a full-width dropdown under the header. Escape, the backdrop, or a route change closes it and restores body scroll.
- Menu groups:
  - **On this page** — Program, Schedule, Progression. These smooth-scroll to `#program`, `#schedule`, and `#progression` on home. On other routes those anchors do nothing visible.
  - **App** — Training, Running, Metrics, Discuss with AI, Settings.
  - Footer — auth panel and the label `FOUNDATION STRENGTH · v2.0.26`.
- Primary actions are full-width, at least 48px tall, uppercase, and orange. Destructive confirms use red. Secondary actions are outlined.
- Most controls fire haptics (`selection`, `success`, `warning`, `light`, `error`).

### Appearance

Settings apply to the whole app immediately and persist locally.

| Control | Options | Default |
|---|---|---|
| Theme | Dark: Orange, Green, Blue. Light: White, Cream. | Orange |
| Font size | Small, Medium, Large, Extra large | Medium |
| Font style | Cinderblock (Orbitron + Share Tech Mono), Readable (system), All mono | Cinderblock |

### Sounds and screen

- **Workout sounds** (default on, volume 70%): exercise changes, rest-timer completion, and run-phase alerts. A preview button plays the timer sound.
- **Screen awake** appears only where the Wake Lock API exists (run session, and the rest timer). Copy is **Screen awake**, **Screen awake (idle)**, or **Screen awake off**. Default is on. The lock is held only while a session is active.
- When the device is already playing music, **media track controls** appear on the run session and the rest timer. The run also publishes a media session (`CINDERBLOCK Run`) so lock-screen play/pause toggles the timer.

### Data and sync

- Gym logs, runs, body metrics, the active program, the default run plan, exercise video links, and settings are stored on the device.
- With a signed-in Supabase account, settings, the active plan, and training logs sync. Snapshot backups are per account and device. Live logs are per device; another device’s logs are imported explicitly.
- The app remains usable with no account. Cloud backup, device import, AI chat, and saving a program edit require sign-in.

## 3. Auth

### Gate

When Supabase is configured, a visitor who is not signed in and has not chosen **Continue without signing in** is sent to `/login`. The skip flag lives in `sessionStorage`, so it lasts for the browser session. Opening `/login` clears the skip. After a successful sign-in, register, or password update, the app replaces the route with `/`.

If cloud is not configured, the login screen explains that data stays on the device and offers only **Continue without signing in**.

### Sign-in screen

Wordmark, **Optional cloud sync**, and a short explanation of what an account does.

| Mode | Fields | Submit | Extra |
|---|---|---|---|
| Sign in | Email, password (min 8) | **Sign in** | **Forgot password?** |
| Register | Email, password (min 8) | **Create account** | Toggle with Sign in |
| Reset password | Email | **Send reset link** | Info: check email. **Back to sign in** |
| Set new password | New password, confirm (min 8) | **Update password** | Shown when a recovery link is open. No “continue locally”. Mismatch shows **Passwords do not match**. |

Errors render in red under the form. Success info renders in yellow. The submit button shows a spinner while the request is in flight. A signed-in user who is not in password recovery is redirected home.

### Header auth panel

Shown in the menu footer.

| State | UI |
|---|---|
| Cloud not configured | **LOCAL ONLY** (hidden below `sm`) |
| Signed out | **IN** → `/login` |
| Signed in, idle | **SYNCED** (from `sm` up) and **OUT** |
| Syncing | **SYNCING** with a spinner. Sync button disabled. |
| Sync error | **SYNC ERR**. The button title includes the error. |

**SYNCED** / **SYNC ERR** calls sync again. **OUT** signs out.

## 4. Home (`/`)

Sections, top to bottom.

### Today

- Date line (`EEEE, MMMM d`), program name, version label.
- Resume hints, only when relevant:
  - Orange pulse: unfinished gym session — workout name, date, **unfinished**.
  - Yellow pulse: resumable run — plan summary, current phase, **in progress** or **remaining · paused**.
- Primary row:
  - **Continue {workout}** if the latest non-rest gym day is still in progress, otherwise **Start training**. Continue opens `/gym/:date/workout` for that date. Start opens `/gym`.
  - **Continue run** (`/running/session`) or **Start run** (`/running`).
  - **Metrics** (`/metrics`).

An unfinished gym session is the most recent non-rest day whose exercises are not all marked done or skipped.

### This week

Monday–Sunday range. Empty copy: **No training logged yet this week.**

Otherwise a count line (workouts done, rest days if any, runs logged) and a chronological list:

- Gym rows are buttons. Pill is **Complete**, **In progress**, **Rest**, or **Empty**. The summary is the workout name plus progress. Tap opens that day’s workout (`/gym/:date/workout`).
- Run rows are not tappable. Pill is **Run**. Summary is the logged warmup · run · cooldown.

**Open training log** goes to `/gym`.

### Last run

Shown only after at least one completed run. Renders the same run-detail block used on the calendar: date, plan summary, and warmup / run / cooldown minutes.

### Stats

Hidden until there is at least one session, completed day, run, weight reading, or waist reading.

| Card | Value | Trend |
|---|---|---|
| Sessions | Count of logged gym days | This week vs last week |
| Completed | Days that are complete or rest | Same |
| Runs | Completed runs | Same |
| Weight | Latest kg | Delta vs a reading at least 28 days older, else the oldest earlier reading. Neutral color. **First entry** when there is no comparison. |
| Waist | Latest cm | Same comparison |

### Program goals

One card per goal on the active program, numbered `01`, `02`, …

Progress is inferred from the goal text:

| Goal text matches | Metric | Bar |
|---|---|---|
| run, running, cardio, endurance, aerobic | Runs this week vs an explicit `Nx` in the goal, else scheduled run days, else 3 | Yes, capped at 100%. Green at 100%, otherwise orange. |
| fat, weight, lean, cut, waist | Waist delta if present, else weight delta. **Log metrics to track** when neither has a comparison. | No |
| strength, strong, stronger, power | Completed sessions in the last 28 days vs planned gym days × 4 | Yes |
| Anything else | Workouts completed this week vs planned non-rest days (at least 1) | Yes |

### Weekly schedule

One row per schedule entry: index, day label (`day1` displays as **Day 1**), and the scheduled workout name.

### Explore workout

Short explanation, **Browse all workouts** (`/gym/explore`), then a two-column grid of every program workout. Each tile shows the name, schedule hint, and exercise count, and opens `/gym/explore/:key`.

### Progression

Method name, the double-progression rule (hit the top of the rep range on all sets, then add the smallest weight increment), and the program’s example exercise plus next step. Caption: **W1 → W2 → W3: add reps, then add weight**.

## 5. Training log

Title in the top bar is **TRAINING**, or **EXPLORE** with **Preview** on the right when the route is an explore URL.

### 5.1 Calendar — `/gym` and `/gym/:date`

- Title **TRAINING LOG**, program name, **Explore workout**.
- Stat chips when anything is logged: **GYM** (days with a log), **COMPLETED** (complete or rest), **RUNS** (if any).
- Month calendar. Today’s number is yellow unless selected. Selected day is orange. Each day can show a workout dot and a yellow run dot. A ring on the workout dot means that day is complete.
- Legend: Upper (orange), Lower (amber), Rest (muted), Run (yellow), Done (ring).
- Selected-day session card:
  - No log: date, **No workout logged**, **Log workout** → picker for that date.
  - Rest: workout label, **Rest day logged**, badge **Rest**, **Change workout** → picker.
  - Program workout: date, workout name, optional historical version pill, progress line, one row per exercise (name, set summary, status **Done** / **Skipped** / **Logged** / **Not started**), and **Continue workout** or **Review workout** (when complete) → `/gym/:date/workout`.
- Runs on the selected date render under the gym card.
- **Program Version History** (collapsed). Expanding loads up to 20 versions for the signed-in user. Each row shows version, time, note, and **Current** on the active one. **Revert** asks for confirmation, writes a new version based on the old program (`Reverted from v…`), and syncs the active plan. Unsigned users see **No version history available**. Failures show **Failed to load version history** or **Failed to revert version**.
- **PROGRAM GUIDE**: week ranges and tips from the program.
- **WEEKLY SCHEDULE**: seven compact day cells.
- Sticky **+ START TODAY'S WORKOUT** always opens today’s picker, even if another date is selected.

Changing the selected day updates the URL to `/gym/:date`.

### 5.2 Workout picker — `/gym/:date/select`

**Back** returns to the calendar for that date.

Heading **What's today's workout?** and the long date.

- Two-column grid of program workouts. Tap selects (does not start). Selected card shows an accent dot. Each card: name, schedule hint, exercise count.
- Full-width **REST** (or the program’s rest label).
- **Last workout**: the newest non-rest day, with the same session card. **Continue** / **Review** opens that older date’s session, not the date being planned.
- **GLOBAL NOTES**: first three program notes.
- Sticky footer. Helper line: **Log rest day**, or `{n} exercises · Resume session` when the selected key matches the log already on this date, or **Start fresh**.
- Button: **SELECT A WORKOUT** (disabled), **LOG REST DAY**, **RESUME WORKOUT →**, or **START WORKOUT →**.

Selecting rest writes `{ workoutKey: rest, exercises: {} }` and returns to the calendar. Selecting a program workout writes a fresh exercise log (and the current program version and workout name) when the key changed, then opens the session. Re-selecting the same key keeps the existing log.

### 5.3 Session — `/gym/:date/workout`

Requires a program-workout log. Otherwise the route is replaced with the picker.

Header: **Change workout** (back to the picker; does not delete the log), workout name, date, **Exercise {n} of {total}**, and `{completed} completed · {skipped} skipped`.

**Jump** is a horizontal strip of dots:

| Dot | Meaning |
|---|---|
| Wide orange pulse | Current exercise |
| Orange | Marked done |
| Muted with a ring | Skipped |
| Border | Not addressed |

Swipe horizontally between exercises (carousel). Scroll vertically inside an exercise. Changing the exercise plays the exercise-change sound when sounds are on.

#### Exercise step

- Name. Done: orange with a check and a **DONE** chip. Skipped: struck through, skip icon, **SKIPPED** chip.
- Target chips: set count, and reps or a timed-hold duration.
- **Reference** (the user’s saved video) and **Video** (the program `refVideo`), each opening the link.
- **This session**: **Set {logged} of {planned} logged**. A set counts as logged when a timed hold has seconds, or a rep exercise has both weight and reps.
- **Last session · {date}** when an earlier day has logged data for the same exercise name.
- **Log sets**. Only the first empty set is shown, plus the next blank after the last set that has data. Focusing a later set copies the previous set’s values. Typing in the last visible set reveals the next one, up to the planned count. Addressed exercises show every set and disable inputs.
  - Rep exercises: **Weight (kg)** and **Reps**.
  - Timed holds: **Seconds held**.
- **Muscles trained** chips.
- Rest timer (active slide only). See below.
- Collapsed **Exercise notes** (badge **This exercise**) and **Program reminders** (badge **Every workout**).

#### Rest timer

One shared countdown. Presets: 30s, 45s, 1m, plus the settings rest duration if it differs (default 2m). States: **Pick a preset**, **Running**, **Paused**, **Rest complete**. Controls: preset chips, Pause/Play, Reset.

If **Auto-start rest timer between sets** is on, completing a set (except the last) starts the settings duration. Auto-start fires once per set index for that exercise.

If the timer belongs to a different exercise, a floating bar above the bottom actions shows the owner and remaining time. Tap jumps back and opens that timer.

The timer block also shows **Screen awake** and media controls when those apply.

#### Bottom bar

Previous and next arrows. Previous is disabled on the first exercise; next is disabled on the last.

| Current exercise | Actions |
|---|---|
| Not addressed | **SKIP** and **MARK DONE →** |
| Addressed, others still open | **UNDO** and **NEXT PENDING →** (jumps to the first unaddressed exercise) |
| All addressed | **UNDO** and **FINISH WORKOUT** |

Skip and done both count as addressed. The slide also shows **ALL DONE**, or **WORKOUT COMPLETE** when any exercise was skipped.

**FINISH WORKOUT** returns to the calendar. The log stays. Marking done does not require every set to be filled.

### 5.4 Explore — `/gym/explore` and `/gym/explore/:key`

List:

- **Training log** back (to `/gym`).
- **EXPLORE WORKOUT**, program name, and **Edit** → `/program-editor`.
- One full-width row per workout: name, schedule hint, exercise count. Opens the detail route.

Detail:

- **All workouts** back.
- Eyebrow **Explore**, workout name, schedule hint, exercise count.
- One card per exercise: index, name, sets, reps or duration, video field, notes, muscles.
- Video field: URL input (placeholder `https://youtube.com/shorts/...`), save, clear. Invalid values show **Enter a valid http or https link**. A saved user link is per exercise name on this device. The program reference video is separate.
- Note: preview only, no sets logged.
- **Start training session** opens today’s picker.

## 6. Running

Top bar title **RUNNING**.

### 6.1 Plan — `/running`

- **Run session** and the summary `Warmup · run · cooldown — {plan}`.
- If a started session is still resumable:
  - **Session in progress**, plan, phase, and either **Timer was running — will catch up from last timestamp** or `{time} remaining · paused`.
  - **Continue run** opens `/running/session` with that plan.
  - **Discard** asks **Discard this run? Progress will not be saved.** Confirm clears the active session and reloads the saved default plan. Cancel returns to the banner.
- **Run preset** card: `warmup · run · cooldown` and total minutes. Pencil opens the editor.
  - Editor title **Edit run preset**, **Done** closes it.
  - Warmup presets: 3, 5, 10, 15 minutes.
  - Run presets: 15, 20, 30, 45, 60 minutes.
  - Cooldown presets: 3, 5, 10 minutes.
  - **Session total** updates live. The choice is saved as the default plan and synced with the active plan when an account is signed in.
  - Default plan is 5 · 30 · 5.
- **Start session**, or **Start new session** when a run is resumable. Starting new clears the in-progress session without a second confirm, then opens `/running/session`.
- **Recent runs**: up to three, date and `warmup · run · cooldown` minutes. Not tappable.

### 6.2 Session — `/running/session`

Phases, in order: **Warmup** (**Easy pace · loosen up**), **Run** (**Steady effort · stay relaxed**), **Cooldown** (**Slow down · recover**).

A restored in-progress session shows **Session restored** for five seconds and resumes from the saved timestamp (a running timer catches up; a paused timer keeps its remaining time).

While running:

- Three phase ticks (done, current, upcoming) and a session progress percent.
- Phase name and hint.
- Large `m:ss` countdown. Caption **Ready**, **In progress**, or **Paused**.
- Before start: **Start run**.
- After start: **Pause** / **Resume** and **Skip** (ends the phase immediately and advances).
- During the run phase, before an early end: **End run** → **End run now and go to cooldown?** Confirm jumps to cooldown and records the run minutes actually elapsed. The cooldown banner says **Run ended early — cooldown**.
- **Screen awake** and media controls when applicable.
- **Back to plan** leaves the timer running in storage. Home and the plan screen can resume it.

On the last phase hitting zero, or after the cooldown completes, the session is written to the run log and the active session is cleared.

Completion card:

- **RUN COMPLETE**, or **RUN ENDED** with **Run ended early · warmup and cooldown from your session plan**.
- Logged minutes for each phase.
- **Done** returns to the plan and refreshes recent runs.

## 7. Body metrics (`/metrics`)

Top bar **BODY METRICS**. Title **Body measurements**. Intro: log weekly.

### Latest readings

Shown after the first save. One card per filled field on the newest entry: Weight (kg), Waist (cm), Chest (cm), Hips (cm), Body fat (%). Delta versus the previous entry. Waist and body fat color a decrease as the accent; other fields color an increase. Date of the latest entry under the value.

### Log entry

Selected date (default today, max today) and the hint **Tap a history entry below to edit a past date**. Fields above, plus **Notes** (`Sleep, energy, diet notes...`). Each field shows **vs last** when a previous entry has that value.

Sticky **SAVE ENTRY**, or **UPDATE ENTRY** when that date already has a row. Disabled until a field or the notes have text. Saving replaces any entry for that date.

### History

Collapsed toggle **History ({n})**, open by default.

- Empty: **No entries yet. Log your first measurements above.**
- Each row: date, **TODAY** if applicable, delete (no confirm), chips for filled fields, notes. Tap the date to load it into the form. The selected row is outlined.

### Track weekly

Static tips: same time each week, waist at navel relaxed, stable or slightly increasing weight supports muscle gain.

## 8. Discuss with AI

Opened from the header sparkles button or the menu. Route `/ai-chat/discuss` (legacy `create` and `edit` are the same screen). Title **Discuss with AI**. Subtitle is the program name and version.

### Signed-out or cloud off

**AI workout chat requires a signed-in Supabase account so your plans can sync securely.** **Sign in to continue** opens `/login`. **Back** returns home.

### Chat

If Amplify is not configured: yellow banner telling the user to deploy the sandbox and set `CEREBRAS_API_KEY`.

Empty state: ask about the current plan or describe a change. The assistant confirms before rewriting. The user approves with **Confirm**, then **View Plan** and **Save**.

Messages:

- User bubbles, right, orange.
- Assistant bubbles, left, markdown. While streaming: **Thinking…** and a caret.
- System bubbles, left, yellow border.

Status:

- **Checking that these changes stay within what you asked for…** while the scope judge runs.
- Error banner. **Sign in again** when the session needs re-auth.
- Hard failure: message, **Your draft is still saved locally — you can keep chatting or retry Save**, and **Report for review**. After send: **Report sent ({id}…)**.

Composer placeholder: **Ask anything, or say what to change…** Enter sends. Shift+Enter inserts a line. Send is disabled while empty, streaming, judging, or saving. A failed send restores the draft. **Stop** cancels a stream. Judging replaces send with a spinner.

Prompt chips, only when they apply:

| Chip | When | Action |
|---|---|---|
| Confirm | The last message is a finished assistant reply | Sends the text `Confirm` |
| View Plan | A plaintext plan exists | Opens the preview sheet. An orange dot means a revision has not been opened yet. |
| Revert | A previous draft can be undone | Reverts the last AI changes in the local draft |
| Save | A draft exists and the plan is ready or revised | Saves, then returns home |

### Plan preview sheet

Bottom sheet, max 85% of the viewport. Backdrop or Escape closes it. Title **Revised plan (draft)** (**Not saved yet — review before saving**) or **Current plan** (program name and version). Body is the plaintext draft. Footer explains whether saving will replace the active program, the chat still needs to finish, or there are no AI changes yet. **Save** is enabled only when the draft can be saved. Success shows **Saved**, then navigates home. A failed save closes the sheet and leaves the error in the thread.

## 9. Program editor (`/program-editor`)

Reached from Explore → **Edit**. Settings points here with the sentence **To edit workouts, go to Training log → Explore → Edit.**

Top bar: **Back** (home), **EDIT PROGRAM**, **Save**. Save is disabled until the document differs from the active program, and shows **Saving...** while the request runs.

- **Program Name** text field.
- **Current version: v{n}**. While dirty, a yellow **→ v{next}** preview.
- Validation error banner. Copy is the first schema issue, **You must be logged in to save changes**, or **Failed to save program. Please try again.**
- One collapsed card per workout. Dot is orange when the key contains “upper”, otherwise amber. Header: name and exercise count.
- Expanded: exercises with up/down (disabled at the ends), name, `sets × reps` or duration, trash. Trash asks **Remove this exercise?**
- Expanding an exercise edits sets (1–10), reps or duration, muscles (comma-separated), notes (comma-separated), and an optional reference video URL.
- Empty workout: **No exercises yet**.
- **Add Exercise** opens a sheet.

### Add-exercise sheet

Title **Add Exercise**. Close dismisses it.

- Search (`Search exercises...`).
- Category chips: All, Chest, Back, Shoulders, Arms, Legs, Core, Cardio.
- Preset list. Choosing one appends it and closes the sheet.
- **Create custom** switches the sheet to **Create Custom Exercise**: name (required), sets (1–10), reps or a timed-hold toggle with duration, muscles, notes. Empty name does not save.

A dirty editor shows a floating **You have unsaved changes** banner. Save validates, bumps the version, writes the program locally, upserts the cloud plan, records history with the note **Manual program edit**, then returns home. Save requires a signed-in user even though local browsing of the editor does not.

## 10. Settings (`/settings`)

Top bar **SETTINGS**. Sections, in order:

### Program

Active program name, version, workout count, and the pointer to Explore → Edit.

### Training

- **Auto-start rest timer between sets** (default off).
- **Rest duration (minutes)**, number input, 0.5–10, step 0.5, default 2. Helper: the value is a rest-timer preset, and is the auto-start duration when that switch is on. The timer does not start after the final set.

### Appearance

Theme swatches (dark row, then light row), font size, font style, **Reset appearance to defaults**, and a live preview (**Preview heading**, secondary caption, **Accent text — MARK DONE · 3 sets × 8 reps**). Reset restores theme, type, and the other appearance defaults in settings; it does not clear training logs.

### Sounds

Toggle, volume slider 0–100% in steps of 5 (disabled while sounds are off), **Preview timer sound**.

### Notifications

Status: **Not supported**, **Blocked**, **Enabled**, **Ready to enable**, or **Initializing**, plus permission and service-worker state.

Copy: browser notifications for workout reminders. On iOS, add the app to the home screen first.

- **Enable notifications** when supported, not denied, and not yet enabled. If the service worker is not active: **Service worker not ready — try refreshing the page.** Success: **Notifications enabled.**
- **Send test notification** when permission is granted. Title **CINDERBLOCK**, body **Test notification — your alerts are working.** Failures name the missing permission or browser settings.
- Denied: **Notifications are blocked. Allow them in your browser or device settings, then refresh.**

### Amplify

**Server test** calls the Amplify test function with the Supabase session. Disabled until signed in and `amplify_outputs.json` has the function URL. Success reports the user and whether a settings row exists. Helper copy covers the signed-out and undeployed cases.

### Backup

**Download training logs** saves a JSON file of gym days, runs, metrics, and settings. Empty data still downloads. Confirmation lists the counts.

Signed in:

- **Cloud backups · this device + account**. Shows a shortened device id.
- **Upload current data**. Each snapshot lists label, time, gym days, and runs.
- **Restore** asks **Restore this backup over local data?** Confirm replaces local logs and settings.
- Trash deletes that cloud snapshot immediately.
- Empty: **No cloud backups yet.** Loading: **Loading cloud backups…**
- **Import from another device**. Other devices on the account list updated time and counts. **Import & merge** asks **Merge this device’s logs into this device?** Newer entries win. Empty: **No other devices with training logs on this account yet.**

Signed out: **Sign in with Supabase to upload and restore cloud backups, and import training logs from your other devices.**

### Workout history

Newest first. Empty: **No workouts logged yet.**

Each row: date, workout label, progress, badge **Done** / **Partial** / **Rest** / **Empty**. Expand to **Change workout** (same keys as the picker, including rest). Changing the type resets that day’s exercise progress and stamps the current program version. **Remove this entry** deletes immediately.

Bulk, behind a confirm row:

- **Remove workouts older than 30 days**
- **Clear all workout history**

### Run history

Empty: **No runs logged yet.** Deleting a run removes it from Home and the training log.

Each row: date, plan summary, total minutes, completion time, **Run** pill. Expanded: warmup / run / cooldown minutes and **Remove this run** (immediate).

Bulk confirms:

- **Remove runs older than 30 days**
- **Clear all run history**

## 11. Status model

### Gym day

| Status | Rule | Home pill | Settings badge | Calendar |
|---|---|---|---|---|
| Empty | No log, or a non-rest log with zero exercises | Empty | Empty | No workout dot |
| Rest | Rest key | Rest | Rest | Muted dot, treated as done for the completed count |
| In progress / Partial | At least one exercise is not done and not skipped | In progress | Partial | Workout dot, no ring |
| Complete | Every exercise is done or skipped | Complete | Done | Workout dot with a ring |

### Exercise inside a day

| Status | Rule |
|---|---|
| Not started | No meaningful set data, not done, not skipped |
| Logged | Has set data, not done, not skipped |
| Done | Marked done |
| Skipped | Marked skipped |

Home “completed sessions” counts days whose status is complete. It does not count rest. The calendar **COMPLETED** chip counts complete and rest.

### Run

A run is either an active session (plan, phase index, remaining seconds, running flag, optional early-end record) or a completed log row (id, date, plan, completed-at). There is no partial-run pill on Home; only completed runs appear in the week list.

## 12. Flows

### First visit

1. App loads. If Supabase is on and the session has not skipped login, route becomes `/login`.
2. User signs in, registers, or taps **Continue without signing in**.
3. Home shows the active program, empty week copy, goals, schedule, explore, and progression. Primary actions are **Start training**, **Start run**, and **Metrics**.

### Log a gym session

1. **Start training** or **+ START TODAY'S WORKOUT** opens today’s picker. Home **Continue** skips the picker when a partial day already exists.
2. User selects a workout and taps **START WORKOUT** (or **RESUME WORKOUT**).
3. User logs sets. Optional rest timer, notes, and reference video.
4. User marks each exercise done or skipped, or jumps ahead.
5. **FINISH WORKOUT** returns to the calendar. Home **This week** and stats update. The day can be reopened as **Review workout**.

### Log a rest day

1. Picker → **REST** → **LOG REST DAY**.
2. Calendar shows a rest dot and **Change workout**. The day counts as complete for calendar totals and as a rest day on Home.

### Change or delete history

1. Settings → Workout history → expand a day → **Change workout** or **Remove this entry**.
2. Or bulk-remove older than 30 days / clear all, after confirm.
3. Runs follow the same pattern under Run history. There is no edit of run minutes after the fact.

### Preview and edit the program by hand

1. Home **Browse all workouts** or a workout tile, or Training log **Explore workout**.
2. Open a workout, optionally save a personal video link, or tap **Start training session**.
3. **Edit** opens the program editor. Change the name, reorder, edit, add, or remove exercises.
4. **Save** (signed in) bumps the version and returns home. The new version shows on Home and in version history.
5. From the calendar, Program Version History → **Revert** restores an older document as a new version.

### Change the program with AI

1. Header sparkles or menu **Discuss with AI**. Sign in if needed.
2. Describe a change. Assistant replies. User sends **Confirm** when asked.
3. **View Plan** reviews the draft. **Revert** undoes the last local draft change.
4. **Save** replaces the active program and returns home.
5. On a hard failure, **Report for review** sends the thread for inspection. The local draft remains.

### Run

1. **Start run** opens the plan. Adjust presets if needed.
2. **Start session** → **Start run**. Pause, resume, skip phases, or **End run** early into cooldown.
3. **Back to plan** leaves it resumable. Home shows **Continue run**. The plan screen offers **Continue run** or **Discard**.
4. When cooldown finishes, **Done** writes the log. Home **Last run**, the week list, and the calendar’s run dot update.

### Body metrics

1. **Metrics**. Enter any of weight, waist, chest, hips, body fat, or notes. **SAVE ENTRY**.
2. Latest readings and Home weight/waist cards update. Goal cards that talk about fat or weight start showing a trend once a comparison exists.
3. Pick a past date or a history row to edit. **UPDATE ENTRY** overwrites that day. Trash deletes with no confirm.

### Back up and move devices

1. Settings → **Download training logs** for a local JSON file.
2. Signed in: **Upload current data**, then **Restore** on this device (overwrites local data after confirm).
3. On a second signed-in device: **Import & merge**. Newer entries win. This does not replace the manual snapshot restore.

### Notifications and appearance

1. Settings → theme, type, sounds, rest timer. Changes apply immediately.
2. **Enable notifications**, then **Send test notification**. If the browser has blocked permission, the screen tells the user to change device settings and refresh.

## 13. Empty, error, and edge states

| Surface | Empty or edge | What the user sees |
|---|---|---|
| Home week | No logs this week | **No training logged yet this week.** |
| Home stats | No training and no weight/waist | Section hidden |
| Home goals | Body-composition goal, no comparison | **Log metrics to track** |
| Calendar day | Nothing logged | **No workout logged** and **Log workout** |
| Workout URL | Date has no program workout | Replaced with the picker |
| Explore video | Bad URL | **Enter a valid http or https link** |
| Metrics history | No entries | **No entries yet. Log your first measurements above.** |
| Metrics save | Blank form | Button disabled |
| AI | Signed out | Sign-in wall |
| AI | Amplify not deployed | Yellow setup banner; composer still present |
| AI | Stream or judge failure | Red error; draft restored to the composer when the send did not stick |
| Program save | Signed out | **You must be logged in to save changes** |
| Program save | Invalid document | First validation message |
| Version history | Signed out or no rows | **No version history available** |
| Settings workouts / runs | None | **No workouts logged yet.** / **No runs logged yet.** |
| Cloud backups | None yet | **No cloud backups yet.** |
| Other devices | None | **No other devices with training logs on this account yet.** |
| Notifications | Denied or no service worker | Blocked copy, or refresh prompt |
| Login | Password mismatch on update | **Passwords do not match** |
| Login | Cloud off | Continue locally only |
| Run discard / early end / backup restore / history bulk / exercise remove / version revert | Destructive | Inline confirm, or a browser confirm for exercise removal and version revert |
| Set logging | Addressed exercise | Inputs disabled until **UNDO** |
| Rest timer | Final set | Does not auto-start |
| Deep link | Unknown path | Redirect to `/` |
| Deep link | Bad gym date | Redirect to `/gym` |