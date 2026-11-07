# Driver brief

This IS the requested execution. You drive a running local Artemis in a browser and record what happened, case by case. You do not fix code, you do not judge whether a failure is "pre-existing", and you do not skip a case because it looks covered.

Inputs the orchestrator gives you: the absolute path of the checkout that holds this skill, the client URL, the server port, the run dir, the batch id, the account to use, the target evidence, the recording method, and the cases of that batch from `plan.json`. Skill paths below are relative to that checkout.

Every path below is relative to the run dir. Pass absolute paths to tools, and create `shots/ net/ console/ results/ video/ api/` with `mkdir -p` first.

## Before the first case

1. Check the target evidence supplied by the orchestrator. If the client, server, target commit or required session is not verified, return `blocked` for affected cases with the missing evidence.
2. Call `list_pages` and remember which pages exist. Open a new page with `isolatedContext` set to the batch id, so no other session's cookies leak in. Resize it to 1440x900.
3. Sign in through the UI as the given account. The sign-in form is two steps: username, Continue, then password.
4. Go to the first case's start screen and start the recording the orchestrator named (`skills/browser-qa/reference/recording.md`). With the screencast tools: `screencast_start` with `filePath` `<run>/video/<batch>.webm`. With the recorder harness: drive the whole batch through a harness script instead of the browser tools, as `skills/browser-qa/assets/recorder/README.md` shows. Note the clip time when each case starts.

An `api` batch has no browser and no video. Skip to **API cases** below.

## Each case

1. Reach the case's start screen and save a screenshot: `shots/<case>-0-start.png`.
2. Do every step as written, with clicks and real typing (`click`, `type_text`, `press_key`). Use `fill` only on plain inputs with no custom handler. After a typed value, press `Tab` so blur handlers run. Before a download click, follow `skills/browser-qa/reference/downloads.md` for capture, native Save dialogs, and file assertions.
3. After every step that saves, navigates, reloads, or switches tabs, read the toast layer at once, before it fades:

   ```js
   () => [...document.querySelectorAll('jhi-alert-overlay .alert-inner')].map(e => (e.getAttribute('data-alert-type') || e.className) + ' :: ' + (e.querySelector('.message')?.textContent ?? '').trim())
   ```

4. Save a screenshot after each state change: `shots/<case>-<n>-<what>.png`.
5. With chrome-devtools, save the network list (`list_network_requests`, `resourceTypes` `["fetch","xhr","document"]`) to `net/<case>.txt` and console errors and warnings to `console/<case>.txt`. For each expected request, save its details and bodies. With the recorder fallback, start per-case capture before the action as `skills/browser-qa/assets/recorder/README.md` explains; after finishing the recording, inspect its events and captured bodies and attach them to the case. Mark assertions blocked when their required capture is missing or failed.
6. When the case has `expect.persisted`, reload the page and check it again after the reload.
7. Write `results/<case>.json` in the shape `skills/browser-qa/reference/artifacts.md` gives, with `video` and `video_at`. `pass` only when every `expect` line was observed. An explicitly expected error or warning toast can pass a negative or permission case when its text and severity match the plan. Record every toast. An unexpected error or warning toast, a missing or mismatched expected toast, or an app console error (an app source file in its stack) makes it `fail`.

Read entity fields one at a time in `evaluate_script`. Serializing a whole entity throws on circular references, and that error is yours, not the app's.

When a step cannot be done as written (the button is missing, the precondition does not hold), try once more after a fresh snapshot. Then record the deviation, do the nearest step a user would do, and say so in `deviations`. A missing precondition you cannot create through the UI makes the case `blocked`, with the step number and what was missing.

## API cases

Run each step with `skills/browser-qa/scripts/api.sh` as the case says (usage in `skills/browser-qa/reference/api-cases.md`). Before each call that writes, save the GET of the target so you can compare after. Read the saved `api/<name>.txt` and inspect `api/<name>.body` with a suitable parser, such as Node's JSON parser, checking each `expect` line field by field. `pass` only when status and fields match. A 500, a 200 where the case expects a refusal, or a field the role must not see makes it `fail`. Write `results/<case>.json` with `evidence` pointing at the `api/*.txt` files.

## After the last case

Stop the recording (`screencast_stop`, or `finish()` in the harness) and check the file is larger than 0 bytes. A 0-byte file: record the batch once more; if that fails too, say so in your return. Close every page you opened, never one that existed before. Return:

```
DRIVER RETURN
batch: <id>
cases: <pass>/<fail>/<blocked>
fails: <case: one line each>
blocked: <case: missing precondition>
video: <path, size>
files: <count written under shots/, net/, console/, results/, api/>
```
