# Recorder harness

Records a browser flow as a real video (webm, plus H.264 mp4 when ffmpeg is present) with a visible cursor and click ripples, through Playwright's `recordVideo`. Use it when the chrome-devtools screencast tools are missing or broken (see `skills/browser-qa/reference/recording.md`). It cannot reuse a browser session someone signed into by hand.

## Setup, once per run

Work in a dir outside the checkout under test:

```bash
mkdir -p <run>/recorder && cd <run>/recorder
npm init -y >/dev/null
npm i --no-fund --no-audit playwright-core ffmpeg-static
command cp -f <artemis-checkout>/skills/browser-qa/assets/recorder/recorder.mjs .
```

Chromium comes from `CHROME_PATH`, else the browser the installed `playwright-core` launches by itself, its headless shell when headless (`npx playwright-core install chromium`), else the newest `chromium-*` in the Playwright cache: `$PLAYWRIGHT_BROWSERS_PATH`, else `~/.cache/ms-playwright` on Linux, `~/Library/Caches/ms-playwright` on macOS, or `%LOCALAPPDATA%\ms-playwright` on Windows. ffmpeg comes from `ffmpeg-static`, else the system `ffmpeg`, else the run delivers webm only and notes it in the events file.

## API

```js
import { startSession, prepareAuth, login, humanClick, humanType, pause, probeToasts } from '<run>/recorder/recorder.mjs';

const base = 'http://localhost:9000'; // the client URL under test
const state = await prepareAuth('artemis_test_user_16', 'artemis_test_user_16', base); // off camera
const s = await startSession({ name: '<batch>', outDir: '<run>/video', baseURL: base, storageState: state, user: 'artemis_test_user_16' });
await s.page.goto('/course-management/<courseId>/lectures');
await humanClick(s.page, 'a:has-text("Edit")');
await humanType(s.page, '#title', 'Renamed');
await probeToasts(s.page, '<case>-after-save', s.events);
s.note('<case> permission check, 403 expected');
const { mp4, webm, events } = await s.finish();
```

- `startSession` records the whole browser context, so sign in first with `prepareAuth`, which handles the two-step sign-in form and suppresses the passkey reminder. Given a `storageState`, `startSession` needs the planned account as `user`, and throws unless the state signs in as that user on its own `baseURL`. Use the same host for both. To record a sign-in on camera, call `login(page, username, password)`.
- `prepareAuth` and `login` refuse to type credentials into a page on another origin than their `baseURL`, and block navigation to another origin while they type. `login` takes the `startSession` `baseURL` unless you pass one as the fourth argument. `humanType` does no such check.
- Certificate errors fail by default. For a local stack with a self-signed certificate, pass `{ ignoreHTTPSErrors: true }` as the fourth argument of `prepareAuth` and as an option of `startSession`.
- `humanClick` moves the cursor visibly, then clicks. `humanType` clears the field and types key by key. Add `pause(page, 800..1500)` after each state change so it stays readable.
- `s.events` records console errors and warnings, page errors, and fetch, XHR and document response statuses. `finish()` writes them to `<name>.events.json` next to the video. Authentication and account responses are excluded.
- Before each case, call `s.captureNetwork('<case-id>', request => new URL(request.url()).pathname === '<expected-path>')`. Include all expected paths in the predicate. After the case's requests complete, call `s.stopNetworkCapture()`. The event records the case ID, method, URL, status, content type, and paths to the captured request and response bodies under `network/`. Inspect each expected event and its body files; a `captureError` or missing response body blocks any assertion that needs it. `finish()` waits for pending captures before closing the browser.
- Capture only the paths needed for the case. Network files can contain private data; keep them in the private run directory and never capture credentials or publish them without review.
- Each session runs its own headless browser at 1440x810.
