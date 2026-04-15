# YouTube Video Transcription Support — Design Document

**Date:** 2026-04-15 (revised)
**Status:** Draft — ready for implementation planning
**Scope:** End-to-end feature across two repositories
**Repositories:** `ls1intum/Artemis`, `ls1intum/edutelligence`

> **History:** Original draft (2026-04-14) was blocked on Artemis-side Nebula cleanup. Nebula removal landed as PR #12537 (merged 2026-04-15), introducing a top-level `tumlive/` module. This revision folds in (a) the post-Nebula package structure and (b) a rename of `tumlive/` → `videosource/` so YouTube and TUM Live share a single "third-party video source" module. The earlier Artemis-side YouTube PR #12503 is being closed; its work is absorbed into this plan.

## Background

Artemis supports video lecture units with an integrated transcript viewer. Currently, this transcript experience only works for TUM Live HLS streams — YouTube videos fall through to a bare `<iframe>` embed with no transcript functionality.

Three prior PRs attempted to add YouTube support:

- **Artemis PR #12390** (closed) — stacked on the old Nebula polling architecture. Became obsolete when PR #12459 migrated Nebula-to-Pyris.
- **Edutelligence PR #417** (closed) — added `yt-dlp` and YouTube support, but used a separate webhook endpoint. Abandoned when PR #493 merged a different unified-ingestion-pipeline architecture.
- **Artemis PR #12503** (being closed) — targeted the post-migration architecture but was rooted in `nebula/` package paths that no longer exist on `develop` after PR #12537. Rebasing has higher cost than reimplementing; unresolved CodeRabbit threads on that PR are also absorbed here. Its conceptual contribution (server-side URL detection + webhook tagging) is preserved in this spec at the new `videosource/` locations.

This spec re-introduces YouTube transcription on top of the current `develop` architecture and extends the recently-introduced video-source module.

Client branching in `attachment-video-unit.component.html` currently gates the transcript viewer behind a successfully-resolved TUM Live playlist URL — so YouTube videos never get the transcript UI, even if Pyris transcribes them. This spec fixes that.

## Goals

- YouTube videos get the **same transcript experience** as TUM Live: synchronized transcript sidebar, click-to-seek, auto-highlighting of the active segment, slide numbers attached to segments.
- Transcription quality is equal to TUM Live: full video download, Whisper transcription, GPT Vision slide detection, segment-to-slide alignment.
- Each repository layer deploys independently and backward-compatibly.
- Failure modes (private video, live stream, exceeded duration, unavailable) surface as **structured, user-actionable errors** — not silent fallbacks.

## Non-Goals

- Supporting live YouTube streams (transcription requires the full recording).
- Supporting private or age-restricted YouTube videos (yt-dlp cannot fetch these without credentials).
- Supporting other third-party video platforms (Vimeo, etc.) — out of scope.
- Changing the transcript viewer UI or the HLS video player behavior.
- **Tightening `frame-src` CSP directive.** The Artemis CSP currently does not set `frame-src`, so iframes from any origin are allowed. This predates the YouTube feature and also protects existing iframe fallbacks for other video platforms (Vimeo, custom URLs). Tightening `frame-src` requires a codebase-wide audit of all iframe embed use cases and is out of scope here. This remains existing security debt; this feature does not regress CSP posture but also does not improve it.

## Architecture Overview

The feature spans three layers across two repositories:

```
Artemis client (Angular)
  └── New YouTubePlayerComponent + modified AttachmentVideoUnitComponent
          │
          ▼
Artemis server (Spring Boot)
  └── YouTubeUrlService for URL detection; PyrisWebhookService tags videoSourceType;
      LectureUnit DTO returns canonical video source metadata to client
          │
          ▼ POST /webhooks/lectures/ingest (includes videoSourceType)
          ▼
Edutelligence/Pyris (Python)
  └── Unified ingestion pipeline, branches download step on VideoSourceType:
       TUM_LIVE → FFmpeg HLS (existing)
       YOUTUBE  → yt-dlp full MP4 (new)
      Everything downstream (audio extraction, Whisper, slide detection,
      alignment, ingestion) is identical for both sources.
      Failures reported back via structured error codes.
```

**Key principle:** YouTube is just a different *download method*. Steps 2–6 of the pipeline stay unchanged.

**Key principle 2 (source-of-truth):** The Artemis **server** is the canonical source for video source type and video ID. The client never parses video URLs to determine source type; it reads server-resolved metadata. This avoids client/server split-brain.

## Repository 1: Edutelligence (Pyris)

### New file: `iris/src/iris/pipeline/shared/transcription/youtube_utils.py`

- `validate_youtube_video(url: str) -> dict` — uses `yt-dlp --dump-json` (no download) to:
  - Verify accessibility (rejects 401/403/404 with `YOUTUBE_UNAVAILABLE` or `YOUTUBE_PRIVATE`)
  - Reject live streams (`YOUTUBE_LIVE`)
  - Check duration against `youtube_max_duration_seconds` (`YOUTUBE_TOO_LONG`)
  - Return metadata dict (title, duration, formats)
  - Raises `YouTubeDownloadError(error_code, message)` on failure
- `download_youtube_video(url: str, output_path: Path, timeout: int) -> Path` — downloads full MP4 via `yt-dlp -f bestvideo+bestaudio/best --merge-output-format mp4`. Returns path to downloaded file. Raises `YouTubeDownloadError("YOUTUBE_DOWNLOAD_FAILED", ...)` on failure or timeout.
- `class YouTubeDownloadError(Exception)` — carries a structured `error_code` attribute (one of the codes above).

### Structured error reporting

The transcription pipeline's error callback (invoked on pipeline failure and sent back to Artemis via the existing status-update webhook) gains an `error_code` field alongside the human message. Codes introduced:

- `YOUTUBE_PRIVATE` — video is private or requires sign-in
- `YOUTUBE_LIVE` — video is a live stream
- `YOUTUBE_TOO_LONG` — exceeds configured max duration
- `YOUTUBE_UNAVAILABLE` — video not found, removed, region-blocked, or age-restricted
- `YOUTUBE_DOWNLOAD_FAILED` — yt-dlp failure not covered above (network, transient)
- `TRANSCRIPTION_FAILED` — generic downstream failure (Whisper, slide detection) — reused for both source types

Codes are terminal except `YOUTUBE_DOWNLOAD_FAILED` and `TRANSCRIPTION_FAILED`, which may be retried (retry semantics are owned by Artemis, not Pyris).

### New enum in the ingestion DTO

```python
class VideoSourceType(str, Enum):
    TUM_LIVE = "TUM_LIVE"
    YOUTUBE = "YOUTUBE"
```

Added as an optional field (default `TUM_LIVE`) to `LectureIngestionWebhookDTO`. Backward-compatible: requests without the field continue to work as before.

### Modified: `heavy_pipeline.py`

Branch at the download step:

```python
if webhook_dto.video_source_type == VideoSourceType.YOUTUBE:
    validate_youtube_video(webhook_dto.video_url)
    video_path = download_youtube_video(
        webhook_dto.video_url, temp_dir,
        settings.transcription.youtube_download_timeout_seconds,
    )
else:  # TUM_LIVE (default, includes None for backward compatibility)
    video_path = download_video(webhook_dto.video_url, temp_dir, timeout)
```

`YouTubeDownloadError` propagates upward and is caught by the pipeline's error handler, which reports the structured `error_code` back to Artemis via the status callback. Everything after download (audio extraction, transcription, slide detection, alignment, ingestion) remains unchanged.

### Modified: `config.py` (`TranscriptionSettings`)

Add:
- `youtube_max_duration_seconds: int = 21600` (6 hours)
- `youtube_download_timeout_seconds: int = 600` (10 minutes)

### Modified: `pyproject.toml`

Add `yt-dlp` dependency (pinned minor version).

### Modified: `Dockerfile`

Add `yt-dlp` installation. No system dependencies beyond FFmpeg (already present).

### Tests

- `test_youtube_utils.py` — URL validation (each error code), duration limits, live-stream rejection, private-video rejection, metadata extraction, download success/failure, timeout.
- `test_heavy_pipeline.py` — parametrized test cases for YouTube branch: validates `youtube_utils` is called, `YouTubeDownloadError` propagates with correct `error_code`, downstream steps behave identically to TUM Live path.

## Repository 2: Artemis Server (Java)

The server work spans three concerns: (a) rename the recently-landed `tumlive/` module to `videosource/` and reshape its internal subpackages, (b) add YouTube URL parsing as a sibling inside the renamed module, (c) extend DTOs + CSP + error-status plumbing.

### Task 0: Rename `tumlive/` → `videosource/` (prerequisite)

Mechanical rename executed as the first commit of the feature PR:

- Move every file under `src/main/java/de/tum/cit/aet/artemis/tumlive/**` to `src/main/java/de/tum/cit/aet/artemis/videosource/**`.
- Update package statements in every moved file (`package de.tum.cit.aet.artemis.tumlive.*` → `package de.tum.cit.aet.artemis.videosource.*`).
- Update every consumer import in the repo (primarily `iris/` `PyrisWebhookService`, `lecture/` consumers, test files).
- Rename test classes: `TumLiveCodeStyleArchitectureTest` → `VideoSourceCodeStyleArchitectureTest`, `TumLiveServiceArchitectureTest` → `VideoSourceServiceArchitectureTest`, etc. (4 arch test classes); move them to `src/test/java/de/tum/cit/aet/artemis/videosource/architecture/`. Each returns `ARTEMIS_PACKAGE + ".videosource"`.
- Rename integration/service test paths from `tumlive/` to `videosource/`.
- Update `gradle/jacoco.gradle`: replace the `"tumlive"` key with `"videosource"`; threshold values carry over unchanged pending re-measurement after YouTube classes land (re-measure before merge).
- Update module documentation (`documentation/docs/developer/**` — whatever references `tumlive`).
- Search-replace audit for any stragglers: SpEL strings, i18n keys, YAML references, `openapi.yaml` tags, comments referencing `tumlive`.

**REST endpoint path rename (required):** `AbstractModuleResourceArchitectureTest` enforces that every `@RequestMapping` in a module starts with `api/<module-name>/`. Renaming the module therefore forces the REST prefix to move too:

- `@RequestMapping("api/tumlive/")` on `TumLiveResource` (currently `src/main/java/.../tumlive/web/TumLiveResource.java`) → `@RequestMapping("api/videosource/")`. The **trailing slash is required** — `AbstractModuleResourceArchitectureTest` enforces it. Omitting it will fail the renamed resource arch test.
- The path stays flat — `/api/videosource/playlist` (or whatever suffix the current endpoint uses). Do **not** nest provider names in the URL (`/api/videosource/tumlive/...`) — that would reintroduce package-style nesting into the HTTP surface without benefit, and future non-TUM providers may or may not need helper endpoints at all.
- Update the client caller in `src/main/webapp/app/lecture/manage/lecture-units/services/attachment-video-unit.service.ts` (the path was recently changed to `/api/tumlive/...` as part of PR #12537 — move to `/api/videosource/...` in the same commit).
- Update any integration tests that hit the endpoint (`TumLivePlaylistResourceIntegrationTest` and similar) and Playwright fixtures if they hard-code the path.
- No external consumers: the endpoint was only introduced in PR #12537 (merged 2026-04-15), so there is no backward-compatibility window to preserve.

**Provider-specific config properties stay as-is (not renamed):** `artemis.tum-live.api-base-url` and any other `artemis.tum-live.*` properties in `application-local.yml` / `application.yml` / `TumLiveEnabled.java` / `TumLiveService.java` are **provider-specific**, not module-generic. Leave them untouched by the rename. If/when a second provider lands, it gets its own provider-named namespace (e.g. `artemis.other-provider.*`). A generic `artemis.videosource.*` namespace would imply shared config that does not exist.

**Flat subpackage convention** (matches every other Artemis module):

```
videosource/
├── api/          TumLiveApi (@Conditional TumLiveEnabled). No YouTubeApi — see below.
├── service/      TumLiveService (@Conditional), YouTubeUrlService (always on)
├── web/          TumLivePlaylistResource (@Conditional)
├── config/       TumLiveEnabled, TumLiveProperties (if any), other TumLive config
├── domain/       VideoSourceType (new, shared)
└── dto/          (existing TumLive DTOs, if any)
```

Providers are distinguished by filename prefix, not package nesting. This matches the pattern `iris/` uses for its many subsystems (flat `iris/service/` contains `IrisChatSessionService`, `IrisExerciseChatService`, etc.).

### New: `VideoSourceType.java` in `videosource/domain/`

```java
public enum VideoSourceType { TUM_LIVE, YOUTUBE }
```

`videosource/` owns the enum. Both `lecture/` and `iris/` import from it.

### New: `YouTubeUrlService.java` in `videosource/service/`

Plain `@Service` — **no `@Conditional`**, no feature flag. YouTube URL parsing is pure and always available; there is no runtime config or external dependency to gate.

- `extractYouTubeVideoId(String url) -> Optional<String>` — uses `java.net.URI` for strict host validation (rejects `notyoutube.com`, `youtube.com.evil.com`, `fakeyoutu.be`), normalizes host to lowercase, rejects non-HTTP(S) schemes, then applies a regex to extract the 11-character video ID.
  - Supported URL shapes: `youtube.com/watch?v=`, `www.youtube.com/watch?v=`, `m.youtube.com/watch?v=`, `youtube.com/embed/`, `youtube.com/live/`, `youtube.com/shorts/`, `youtu.be/`, `youtube-nocookie.com/embed/`.
  - **Host-set invariant:** the set of accepted hosts here must be a superset of the set matched by `hasYouTubeHost` (see save-time validation section below). Otherwise the save-time gate would reject valid URLs (e.g. `m.youtube.com`) that `hasYouTubeHost` correctly flags as YouTube-like. `YouTubeUrlServiceTest` asserts this pairing.
- `isYouTubeUrl(String url) -> boolean` — delegates to `extractYouTubeVideoId`.

**Server-as-source-of-truth:** The client never duplicates this parsing. The same service result flows to both the webhook payload (telling Pyris how to download) and the lecture-unit DTO (telling the client which player to render).

### New: Save-time YouTube URL format validation

`YouTubeUrlService`'s strict parser is also invoked as a **save-time gate** in `AttachmentVideoUnitResource`, not only for webhook routing. This prevents instructors from saving malformed YouTube URLs and then discovering minutes later that transcription silently produced nothing.

**New helper on `YouTubeUrlService`:**

```java
/**
 * True when the URL's host looks like a YouTube host (case-insensitive match on
 * youtube.com, www.youtube.com, m.youtube.com, youtu.be, youtube-nocookie.com).
 * Used to decide whether a URL should be subjected to strict YouTube format validation.
 */
boolean hasYouTubeHost(String url);
```

This is **host-only**; it does not attempt to extract a video ID. A URL with a YouTube host but a malformed path/query returns `true` here but `Optional.empty()` from `extractYouTubeVideoId` — exactly the combination that should fail the save.

**Gate logic in `AttachmentVideoUnitResource` (create + update endpoints):**

```
if (hasYouTubeHost(videoSource) && extractYouTubeVideoId(videoSource).isEmpty()) {
    throw new BadRequestAlertException("Invalid YouTube URL format", ENTITY_NAME, "invalidYouTubeUrl");
}
```

- Uses the existing Artemis `BadRequestAlertException` pattern (same as every other validation in `*Resource` classes). The error key `invalidYouTubeUrl` maps to an i18n entry on the client.
- **Only** rejects YouTube-host URLs that fail the strict parser. URLs on other hosts (TUM Live, custom embeds, arbitrary third parties) pass through untouched — not the concern of this gate.
- Rejection is at HTTP layer, before persistence; the DB never stores a malformed YouTube URL.

**Client-side error surfacing:**

- New i18n key: `artemisApp.lectureUnit.video.error.invalidYouTubeUrl` — English: "This looks like a YouTube URL but couldn't be parsed. Supported formats: `youtube.com/watch?v=...`, `youtu.be/...`, `youtube.com/embed/...`, `youtube.com/shorts/...`." German equivalent.
- The existing `BadRequestAlertException` → toast flow on the client already surfaces these keys; no new client wiring required beyond adding the translation.

**Why only YouTube hosts, not all URLs:** TUM Live URLs are validated separately (and resolved to playlists) by `TumLiveApi`. Custom/arbitrary embed URLs are out of scope — the spec intentionally doesn't extend iframe-fallback validation beyond today's behavior.

**Tests added:**
- `AttachmentVideoUnitResourceTest`:
  - `POST` with `videoSource=https://youtube.com/watch?v=shortid` → 400 with `invalidYouTubeUrl` key.
  - `POST` with `videoSource=https://youtube.com/watch?v=dQw4w9WgXcQ` → 201.
  - `POST` with `videoSource=https://vimeo.com/123` → 201 (non-YouTube host, not our concern).
  - `PUT` equivalents.
- `YouTubeUrlServiceTest`: `hasYouTubeHost` covers `youtube.com`, `www.youtube.com`, `m.youtube.com`, `youtu.be`, `youtube-nocookie.com`, case-insensitive, plus rejection of `notyoutube.com`, `youtube.com.evil.com`, `fakeyoutu.be`.

### No `YouTubeApi` cross-module wrapper

The `AbstractApi` / `Api`-wrapper pattern (e.g. `TumLiveApi`) exists to hide `@Conditional`-gated beans from consumers in other modules — the wrapper is required because the underlying service bean may not exist at runtime. `YouTubeUrlService` has no conditional, so consumers in `iris/` and `lecture/` inject it directly. Adding a wrapper would be ceremony without purpose.

### Modified: `PyrisWebhookService.java`

- Constructor gains a required `YouTubeUrlService youTubeUrlService` (alongside the existing `Optional<TumLiveApi> tumLiveApi`).
- `resolveVideoUrl()` return type changes from `String` to a new package-private record:
  ```java
  record ResolvedVideo(String url, VideoSourceType type) {}
  ```
- Resolution chain: TUM Live first (if present and matches) → YouTube second → passthrough with `null` type for unknown sources.
- `buildLectureUnitWebhookDTO()` forwards `videoSourceType` to the Pyris webhook DTO.

### Modified: `PyrisLectureUnitWebhookDTO.java`

Add `VideoSourceType videoSourceType` field. Annotate with `@JsonInclude(NON_EMPTY)` so `null` is omitted from the JSON payload — wire-compatible, safe to deploy Artemis before Pyris.

### Modified: Student-facing lecture unit DTO (the one `AttachmentVideoUnitComponent` reads)

**Which DTO to modify:** Two `AttachmentVideoUnit`-flavored DTOs exist in the codebase:

1. The **editor-side** DTO `src/main/java/de/tum/cit/aet/artemis/lecture/dto/AttachmentVideoUnitDTO.java` used by `AttachmentVideoUnitResource` for create/update endpoints. **Not modified by this spec** — editor-side code doesn't render the transcript viewer.
2. The **student-facing** `AttachmentVideoUnitForLearnerDTO` (or equivalent student projection) populated inside `LectureResource.java:363` for the `GET api/lecture/courses/{courseId}/lectures-with-slides` endpoint and consumed via `src/main/webapp/app/lecture/manage/services/lecture.service.ts`. **This is the one this spec extends.** If the exact DTO class name differs on-disk, use the student-facing projection that flows to `AttachmentVideoUnitComponent`.

The student-facing DTO gains:

- `videoSourceType: VideoSourceType | null` — canonical source type resolved server-side (via `YouTubeUrlService` + `TumLiveApi`, same resolution chain used by `PyrisWebhookService`)
- `youtubeVideoId: String | null` — populated when `videoSourceType == YOUTUBE`
- `videoSource: String | null` — **existing** raw URL the instructor entered. **Kept on the DTO** because the iframe fallback branch uses it as `<iframe src>` as-is (no client parsing). Only the *routing decision* is driven by `videoSourceType`; the URL itself still travels.
- `transcriptionStatus: TranscriptionStatus | null` — existing field (reused)
- `transcriptionErrorCode: String | null` — **new**, holds the Pyris error code when transcription failed

**Invariants enforced server-side at DTO construction:**
- When `videoSourceType == YOUTUBE`, `youtubeVideoId` MUST be present.
- When `videoSourceType == TUM_LIVE`, `youtubeVideoId` MUST be absent (`null`).
- When `videoSourceType == null` (unknown/third-party), neither is set.

**Forward compatibility:** Older clients that don't know about `videoSourceType` / `youtubeVideoId` / `transcriptionErrorCode` continue to work — they'll fall through to the iframe branch as they do today (driven by the existing `videoSource` field). Newer clients against an older server (new fields absent) also fall through to the iframe rather than breaking.

### Error-code propagation: webhook DTO → callback plumbing → persistence → client DTO

The current callback path collapses terminal failures into a boolean; there is no carrier for `error_code` today. Every layer below needs an explicit change.

**Layer 1 — Inbound webhook DTO from Pyris:**

- Extend `src/main/java/de/tum/cit/aet/artemis/iris/service/pyris/dto/lectureingestionwebhook/PyrisLectureIngestionStatusUpdateDTO.java` with a new optional field:
  ```java
  String errorCode   // nullable; present only on failure callbacks
  ```
- Jackson deserializes it from the `error_code` JSON key (`@JsonProperty("error_code")` or use field-naming strategy already in place). Backward-compatible: older Pyris deployments that omit the field continue to work.

**Layer 2 — Status update service:**

- `src/main/java/de/tum/cit/aet/artemis/iris/service/pyris/PyrisStatusUpdateService.java:179` currently collapses terminal callbacks to a boolean success/failure. Change the internal handoff to a small record `TerminalResult(boolean success, String errorCode)` (or equivalent) so the error code survives into `ProcessingStateCallbackApi`.

**Layer 3 — Callback API:**

- `src/main/java/de/tum/cit/aet/artemis/lecture/api/ProcessingStateCallbackApi.java:68`: extend the callback method signature to accept the error code (nullable) alongside the success flag. Update callers.

**Layer 4 — Persistence:**

- Add an `error_code VARCHAR(64) NULL` column to the lecture-unit-processing-state table (backing `src/main/java/de/tum/cit/aet/artemis/lecture/domain/LectureUnitProcessingState.java:74`). Justification: `LectureUnitProcessingState` already holds per-unit processing status/error and is what the student-facing DTO reads; extending it avoids adding a new entity.
- Add a Liquibase changeset (one per usual Artemis convention: `src/main/resources/config/liquibase/changelog/YYYYMMDDHHMMSS_add-lecture-unit-processing-state-error-code.xml`) adding the column as nullable with no default (existing rows keep `NULL`).
- Update the JPA entity: add `@Column(name = "error_code", length = 64) private String errorCode;` plus accessors.
- `LectureTranscription.java` is **not** extended — it records the transcript itself, not the processing outcome.

**Layer 5 — Server → client DTO:**

- The student-facing lecture unit DTO (see above) reads `errorCode` from `LectureUnitProcessingState` and exposes it as `transcriptionErrorCode` on the wire.

**Error code vocabulary (matches Pyris emitter):**

`YOUTUBE_PRIVATE`, `YOUTUBE_LIVE`, `YOUTUBE_TOO_LONG`, `YOUTUBE_UNAVAILABLE`, `YOUTUBE_DOWNLOAD_FAILED`, `TRANSCRIPTION_FAILED`. The persistence layer treats the string as opaque — no Java enum mapping on the server, to keep Pyris free to introduce new codes without forcing Artemis redeploys. Client-side i18n lookup falls back to the generic message when a code is unknown.

**Lifecycle — when `errorCode` is cleared:**

The existing `LectureUnitProcessingState` already clears `errorKey` on phase transitions (see `LectureUnitProcessingState.java:226`). The new `errorCode` column follows the **same policy, mirror-for-mirror**, wherever `errorKey` is cleared:

- On transition to a non-terminal processing phase (ingestion restart, retry start).
- On successful terminal completion (`errorCode` cleared to `NULL`; `errorKey` cleared to `NULL`).
- On any re-upload/re-trigger that resets the unit's processing state.

Implementation rule: every assignment or clear of `errorKey` in `LectureUnitProcessingState` must be paired with the same operation on `errorCode`. This prevents stale YouTube-era error codes from leaking into later successful or re-triggered states. A dedicated test (`LectureUnitProcessingStateTest`) asserts the pairing.

### Modified: `SecurityConfiguration.java` (CSP change)

Add `https://www.youtube.com` to the `script-src` CSP directive so the YouTube IFrame API script can load:

```java
"script-src 'self' 'unsafe-inline' 'unsafe-eval' https://www.youtube.com; "
```

**Caveat — initial entry only:** `https://www.youtube.com` is the baseline. The YouTube IFrame API may dynamically load additional parent-document scripts from origins like `https://s.ytimg.com` (Google CDN for static YouTube assets). This cannot be confirmed from public docs alone. **Implementation task:** empirically verify the full origin set in a browser with network inspector during implementation and expand `script-src` as needed. A CSP-blocked origin will surface as console errors and a failed player init — the runtime fallback (see Client section, Fix 3) will catch this.

**Additional player config:** Pass `origin: window.location.origin` as a `playerVar` to the YouTube IFrame API per Google's security recommendation. This prevents rogue pages from embedding our YouTube player context.

**Rationale for CSP change (not regression):**

- `frame-src` is not set, so iframes already default to unrestricted — the existing YouTube iframe fallback works today. No `frame-src` change is made in this spec (see Non-Goals).
- `frame-options: deny` is unaffected — it controls whether other sites can frame Artemis, not whether Artemis can frame YouTube.
- Scope is narrow: only `https://www.youtube.com` (plus any additional origins found during empirical verification), HTTPS-only, no wildcards. Google-maintained script.

### Tests

- `YouTubeUrlServiceTest.java` (in `test/.../videosource/service/`) — ~18 test cases covering all URL shapes, spoofed domains (`notyoutube.com`, `youtube.com.evil.com`, `fakeyoutu.be`), null/blank, mixed-case host, non-HTTP schemes (ftp, file, javascript, data).
- `PyrisWebhookServiceResolveVideoUrlTest.java` — ~12 unit tests (Mockito, no Spring context) covering: TUM Live resolution + tagging, YouTube passthrough, TUM Live priority over YouTube, unknown-source fallback, TumLiveApi exception handling, null/blank input, absent-TumLiveApi scenarios (YouTube still resolves without TumLive).
- Lecture unit DTO tests — verify `youtubeVideoId` presence when `videoSourceType == YOUTUBE`, absence otherwise (invariant enforcement).
- Transcription status update tests — verify `error_code` round-trips from Pyris callback to lecture unit DTO.
- Renamed arch tests (`VideoSourceCodeStyleArchitectureTest` etc.) continue to pass with the new package and cover both TumLive and YouTube classes uniformly.

## Repository 3: Artemis Client (Angular)

### New: `YouTubePlayerComponent`

**Location:** `src/main/webapp/app/lecture/shared/youtube-player/`

A new standalone component mirroring the layout contract of `VideoPlayerComponent` but using `@angular/youtube-player` instead of hls.js + native `<video>`.

**Inputs (same contract as `VideoPlayerComponent`):**
- `videoId = input.required<string>()` — the 11-character YouTube video ID **received from the server** (never parsed client-side).
- `transcriptSegments = input.required<TranscriptSegment[]>()`
- `initialTimestamp = input<number>()` — for deep-linking.

**Outputs:**
- `playerFailed = output<void>()` — emitted when player initialization fails (see Runtime Fallback below). The parent catches this and switches to iframe fallback.

**Template structure:**

```html
<div class="video-wrapper">
    <div class="video-column">
        <youtube-player
            [videoId]="videoId()"
            [disableCookies]="true"
            [playerVars]="playerVars"
            (ready)="onPlayerReady($event)"
            (stateChange)="onStateChange($event)"
            (error)="onPlayerError($event)" />
    </div>
    <div class="resizer-handle">⋮</div>
    <jhi-transcript-viewer
        [transcriptSegments]="transcriptSegments()"
        [currentSegmentIndex]="currentSegmentIndex()"
        (segmentClicked)="seekTo($event)" />
</div>
```

`playerVars` includes `{ origin: window.location.origin }` per Google's security recommendation.

**Key behaviors:**

- **Runtime fallback on player init failure (Fix 3):**
  - On `ngAfterViewInit`, set a 10-second readiness timeout. If `onPlayerReady` doesn't fire, emit `playerFailed`.
  - On `onPlayerReady`, clear the readiness timeout.
  - On `error` output (YT.OnErrorEvent with codes 2/5/100/101/150), emit `playerFailed`. **Do NOT** rely on `stateChange == -1` (that is the `UNSTARTED` state and is normal).
  - On `ngOnDestroy`, clear any pending timeout.
  - Parent component (`AttachmentVideoUnitComponent`) catches `playerFailed` and sets a local `youtubePlayerFailed` signal, which falls the template through to the iframe branch.

- **Time sync via polling + event-driven updates (Fix 4):**
  - On `onPlayerReady`: if `initialTimestamp()` is set, seek there; then immediately call `updateCurrentSegment(currentTime)` so the highlighted segment is correct before playback begins.
  - On `stateChange == PLAYING`: start `setInterval` polling `getCurrentTime()` every 250 ms.
  - On `stateChange == PAUSED | ENDED | BUFFERING`: stop the polling interval **and** call `updateCurrentSegment(currentTime)` once, so the highlight stays accurate if the user scrubbed.
  - On `seekTo(seconds)` (from transcript click): call `youtubePlayer.seekTo(seconds, true)`, then immediately call `updateCurrentSegment(seconds)` without waiting for the next poll tick — this handles paused-state seeks.
  - All segment updates guard against calling player methods before `onPlayerReady` has fired or after destroy.

- **Resizable layout.** Reuse the flexbox layout and interact.js resizer pattern from `VideoPlayerComponent`. SCSS can largely be shared or duplicated.
- **Responsive.** Same breakpoint as HLS player: at `max-width: 992px`, switch to column layout and hide the resizer.
- **Privacy.** `[disableCookies]="true"` uses `youtube-nocookie.com` instead of `youtube.com` for the embed iframe.

### Modified: `AttachmentVideoUnitComponent`

**No more client-side URL parsing.** The component reads `videoSourceType` and `youtubeVideoId` from the lecture unit DTO returned by the server.

Template branching:

```html
@if (isLoading())
    → spinner
@else if (transcriptionErrorMessage())
    → <iframe [src]="rawVideoSource() | safeResourceUrl"> + error banner      // NEW — structured error UX
@else if (playlistUrl() && hasTranscript())
    → <jhi-video-player>             // TUM Live HLS (existing)
@else if (youtubeVideoId() && hasTranscript() && !youtubePlayerFailed())
    → <jhi-youtube-player (playerFailed)="onYouTubePlayerFailed()">
@else
    → <iframe [src]="rawVideoSource() | safeResourceUrl">                   // fallback (existing)
```

**Component class changes:**

- New signals:
  - `rawVideoSource = computed(...)` — reads the existing `videoSource` field straight from the DTO (the raw URL the instructor entered). Used as-is for `<iframe src>`. **Never parsed client-side.**
  - `videoSourceType = computed(...)` — reads from the DTO. Drives the routing branches above.
  - `youtubeVideoId = computed(...)` — reads from the DTO (server-populated when `videoSourceType == YOUTUBE`).
  - `youtubePlayerFailed = signal(false)` — local, flips to `true` on `playerFailed` event from `YouTubePlayerComponent`.
  - `transcriptionErrorMessage = computed(...)` — derives from `transcriptionErrorCode` via i18n lookup.
- Removed: client-side URL parsing via `js-video-url-parser` for source-type detection. The library is still used for URL validation at creation-time (separate concern — editor form, not viewer).
- **The viewer never parses the URL.** Routing decisions read only `videoSourceType` and `youtubeVideoId`. The raw `videoSource` is used only as an iframe `src` value (piped through the existing `safeResourceUrl` sanitizer, same as today) when the iframe branch wins. This is the server-as-source-of-truth guarantee end-to-end; the sanitizer contract is preserved.
- Decoupled `fetchTranscript()`: runs when *either* a playlist URL *or* a YouTube video ID is available (plus no terminal error is set). The endpoint is already source-agnostic.
- `onYouTubePlayerFailed()` handler — sets `youtubePlayerFailed.set(true)`, triggering iframe fallback.

### Error messaging (i18n)

New i18n keys (English + German per AGENTS.md):

- `artemisApp.lectureUnit.video.transcription.error.private` — "Transcript unavailable: this video is private."
- `artemisApp.lectureUnit.video.transcription.error.live` — "Transcript unavailable: live streams cannot be transcribed."
- `artemisApp.lectureUnit.video.transcription.error.tooLong` — "Transcript unavailable: this video exceeds the 6-hour limit."
- `artemisApp.lectureUnit.video.transcription.error.unavailable` — "Transcript unavailable: this video is inaccessible (removed, region-blocked, or age-restricted)."
- `artemisApp.lectureUnit.video.transcription.error.downloadFailed` — "Transcript unavailable: download failed. The instructor can retry."
- `artemisApp.lectureUnit.video.transcription.error.generic` — "Transcript unavailable: an error occurred during transcription."

A computed signal maps `transcriptionErrorCode` → message.

### Dependency: `@angular/youtube-player`

Add to `package.json` via `npm install @angular/youtube-player`. Official Angular package, part of `@angular/components`, versioned in lockstep with Angular. No transitive dependencies.

### Unchanged

- `VideoPlayerComponent` — stays HLS-only.
- `TranscriptViewerComponent` — already player-agnostic.
- `TranscriptSegment` model.

### Tests

- `youtube-player.component.spec.ts` (Vitest):
  - Polling starts on `PLAYING`, stops on `PAUSED`/`ENDED`/`BUFFERING` with immediate `updateCurrentSegment` call on stop.
  - `seekTo()` from transcript click calls `youtubePlayer.seekTo(seconds, true)` and immediately updates the segment index.
  - `initialTimestamp` seeks on `onPlayerReady` and updates segment index immediately after.
  - Readiness timeout emits `playerFailed` if `onPlayerReady` does not fire within 10s.
  - `error` output emits `playerFailed`.
  - Timeout cleared on destroy and on successful ready.
  - Segment updates are no-ops if called before ready or after destroy.
- `attachment-video-unit.component.spec.ts`:
  - DTO with `videoSourceType=YOUTUBE` and `youtubeVideoId` present → `<jhi-youtube-player>` rendered.
  - `playerFailed` event → iframe fallback.
  - Non-YouTube, non-TUM-Live DTO (no source type) → iframe fallback.
  - TUM Live DTO still renders `<jhi-video-player>` (regression guard).
  - Each `transcriptionErrorCode` value → correct i18n message + iframe fallback.

## Deployment Order

1. **Edutelligence first.** Add `yt-dlp` + YouTube download path + error-code reporting. Safe because `VideoSourceType` defaults to `TUM_LIVE`; YouTube only activates when the field is explicitly sent. Error codes are additive in the callback payload.
2. **Artemis server second.** Single PR against `develop` that (a) renames `tumlive/` → `videosource/`, (b) adds `YouTubeUrlService` + `VideoSourceType`, (c) adds CSP change, DTO extensions, error-code persistence. Wire-compatible because `videoSourceType` is `@JsonInclude(NON_EMPTY)` — omitted from JSON when `null`, so older Pyris deployments still accept the payload. New DTO fields (`youtubeVideoId`, `transcriptionErrorCode`) are optional — older clients ignore them.
3. **Artemis client third.** New `YouTubePlayerComponent` + modified `AttachmentVideoUnitComponent`. Gracefully falls back when DTO fields are absent (old server) or when the YouTube player initialization fails (CSP, ad-blocker, etc.).

Each layer is independently deployable and backward-compatible.

## Risks and Open Questions

- **CSP origin set incomplete.** `https://www.youtube.com` is the starting allowlist entry; empirical verification may surface additional origins (e.g., `s.ytimg.com`). Implementation task documented. A missing origin will manifest as a console CSP violation and trigger the runtime iframe fallback — users see the video embed without transcript, not a broken page.
- **Ad blockers blocking the YouTube IFrame API script.** Some users have uBlock Origin or similar blocking `youtube.com/iframe_api`. The runtime fallback (timeout + `error` event) catches this and renders the iframe — same degraded experience they'd get today, just without transcript sync. Acceptable.
- **yt-dlp is a moving target.** YouTube occasionally changes internals, breaking yt-dlp until a new release lands. Pin a recent version and accept that this dependency needs occasional bumps. Mitigation: `YOUTUBE_DOWNLOAD_FAILED` error surfaces with retry semantics; UI offers a manual retry path (separate, minor enhancement).
- **Storage/bandwidth for full video downloads.** yt-dlp downloading full MP4 is heavier than audio-only. The existing TUM Live pipeline already downloads full video, so the infrastructure handles this.
- **Public/unlisted only.** Private and age-restricted YouTube videos cannot be transcribed. `YOUTUBE_PRIVATE` and `YOUTUBE_UNAVAILABLE` codes surface a clear instructor-facing message.
- **Absorbed CodeRabbit findings from PR #12503.** The closed PR had unresolved CodeRabbit threads. Their substance is folded into the implementation plan derived from this spec; the PR itself is closed rather than rebased.
- **Existing CSP debt on `frame-src`.** Not scoped into this feature, called out in Non-Goals.

## Summary of Reused Components

- `TranscriptViewerComponent` — already player-agnostic.
- `TranscriptSegment` model.
- `HeavyTranscriptionPipeline` downstream steps (audio extraction, Whisper, slide detection, alignment).
- `WhisperClient`, `audio_utils`, `alignment`, `slide_turn_detector`.
- TUM Live resolution path (unchanged).
- Existing transcription status callback infrastructure (gains `error_code` field).
