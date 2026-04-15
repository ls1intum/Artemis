# Artemis Video Source + YouTube Transcription Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** In one Artemis PR, (a) rename the recently landed `tumlive/` module to a generic `videosource/` module, (b) add YouTube URL parsing + save-time validation + a `VideoSourceType` enum, (c) propagate a new server-resolved `videoSourceType` through the Pyris webhook and back to the student-facing lecture unit DTO, (d) wire a structured `errorCode` end-to-end (webhook → status update → persistence → client), (e) update CSP for the YouTube IFrame API, and (f) add a new Angular `YouTubePlayerComponent` plus refactor `AttachmentVideoUnitComponent` to branch on server-resolved metadata (not client-side URL parsing).

**Architecture:** The Artemis server becomes the canonical source of truth for video source type and YouTube video ID. Client routing reads `videoSourceType` + `youtubeVideoId` from the lecture unit DTO; it never parses URLs. The `videoSourceType` field flows to Pyris so Pyris branches its download step accordingly (see the companion Pyris plan). Structured error codes from Pyris persist on `LectureUnitProcessingState` and surface to the student as i18n-mapped messages with the iframe fallback still rendering the original URL.

**Tech Stack:** Java 25, Spring Boot 3.5, Hibernate, Liquibase, JUnit 6, PostgreSQL (Testcontainers), Angular 21 (signal-based), TypeScript, Vitest, PrimeNG, `@angular/youtube-player` (new).

**Repo:** `ls1intum/Artemis`. Single PR against `develop`.

**Worktree:** `/Users/pat/projects/claudeworktrees/Artemis/feature-youtube-transcription` (branch `feature/videosource-youtube-transcription`).

**Companion plan:** `2026-04-15-pyris-youtube-ingestion.md` covers the Pyris side. Deploy order: Pyris first, then this Artemis PR.

**Execution order (important):** Tasks are numbered for readability, but dependencies require this actual order:

```
1 → 2 → 3 → 4 → 5 → 6 → 7 → 9 → 10 → 8 → 11 → 12 → 13 → 14 → 16 → 15 → 17 → 18
```

Specifically:
- Task 10 (Liquibase + `LectureUnitProcessingState.errorCode`) MUST run before Task 8 (student DTO reads `errorCode` off `LectureUnitProcessingState`).
- Task 9 (`PyrisLectureIngestionStatusUpdateDTO.errorCode`) runs before Task 11 (`PyrisStatusUpdateService` threads it through).
- Task 16 (TS model update) runs before Task 15 (`AttachmentVideoUnitComponent` consumes new TS fields).
- Task 14 (`YouTubePlayerComponent`) runs before Task 15 (host component imports it).

If executing subagent-driven, dispatch in the order above, not by task number. Task numbers are preserved as-is throughout this document for stable reference.

---

## File Structure

**Moved / renamed (Task 1):**
```
src/main/java/de/tum/cit/aet/artemis/tumlive/  →  .../videosource/
├── api/TumLiveApi.java                         (package statement updated)
├── config/TumLiveEnabled.java                  (package statement updated)
├── service/TumLiveService.java                 (package statement updated)
└── web/TumLiveResource.java                    (package statement + @RequestMapping updated)

src/test/java/de/tum/cit/aet/artemis/tumlive/  →  .../videosource/
├── architecture/TumLive*ArchitectureTest.java  (class rename + package update)
├── TumLiveServiceTest.java                     (package + imports)
└── TumLivePlaylistResourceIntegrationTest.java (package + endpoint path)
```

**New Java files:**
- `src/main/java/de/tum/cit/aet/artemis/videosource/domain/VideoSourceType.java` — enum (`TUM_LIVE`, `YOUTUBE`)
- `src/main/java/de/tum/cit/aet/artemis/videosource/service/YouTubeUrlService.java` — URL parsing + host detection
- `src/test/java/de/tum/cit/aet/artemis/videosource/service/YouTubeUrlServiceTest.java`
- `src/test/java/de/tum/cit/aet/artemis/videosource/architecture/VideoSource*ArchitectureTest.java` (renamed from TumLive*)
- `src/main/resources/config/liquibase/changelog/<timestamp>_add-lecture-unit-processing-state-error-code.xml`

**Modified Java files:**
- `src/main/java/de/tum/cit/aet/artemis/iris/service/pyris/PyrisWebhookService.java` — inject `YouTubeUrlService` (Task 7) and later delegate resolution to shared `VideoSourceResolver` (Task 8); forward `videoSourceType`
- `src/main/java/de/tum/cit/aet/artemis/iris/service/pyris/dto/lectureingestionwebhook/PyrisLectureUnitWebhookDTO.java` — add `videoSourceType`
- `src/main/java/de/tum/cit/aet/artemis/iris/service/pyris/dto/lectureingestionwebhook/PyrisLectureIngestionStatusUpdateDTO.java` — add `errorCode`
- `src/main/java/de/tum/cit/aet/artemis/iris/service/pyris/PyrisStatusUpdateService.java:179` — terminal-result record carrying `errorCode`
- `src/main/java/de/tum/cit/aet/artemis/lecture/api/ProcessingStateCallbackApi.java:68` — callback signature gains `errorCode`
- `src/main/java/de/tum/cit/aet/artemis/lecture/domain/LectureUnitProcessingState.java` — `errorCode` column + accessors; clearing lifecycle mirrors existing `errorKey`
- `src/main/java/de/tum/cit/aet/artemis/lecture/web/LectureResource.java:363` — populate student-facing projection with `videoSourceType` + `youtubeVideoId` + `transcriptionErrorCode`
- `src/main/java/de/tum/cit/aet/artemis/lecture/web/AttachmentVideoUnitResource.java` — save-time YouTube URL gate (POST + PUT)
- `src/main/java/de/tum/cit/aet/artemis/core/config/SecurityConfiguration.java` — CSP `script-src` extension
- `src/main/java/de/tum/cit/aet/artemis/lecture/dto/AttachmentVideoUnit*ForLearnerDTO.java` (or equivalent) — new fields (see Task 8)
- `gradle/jacoco.gradle` — rename `tumlive` key to `videosource`
- `build.gradle` / dependency-check configs — none directly; re-measure coverage threshold after Task 18

**Modified resources:**
- `src/main/resources/config/application*.yml` — `artemis.tum-live.*` kept as-is (provider-specific)
- `src/main/resources/config/liquibase/master.xml` — register new changelog
- `src/main/webapp/i18n/en/lectureUnit.json`, `src/main/webapp/i18n/de/lectureUnit.json` — new error keys

**Modified client files:**
- `src/main/webapp/app/lecture/manage/lecture-units/services/attachment-video-unit.service.ts` — endpoint path `/api/tumlive/` → `/api/videosource/`
- `src/main/webapp/app/lecture/manage/lecture-units/services/attachment-video-unit.service.spec.ts` — same path update
- `src/main/webapp/app/lecture/overview/course-lectures/attachment-video-unit/attachment-video-unit.component.{ts,html,spec.ts}` — routing refactor
- `src/main/webapp/app/lecture/shared/youtube-player/youtube-player.component.{ts,html,scss,spec.ts}` — new

**Modified build files:**
- `package.json` — `@angular/youtube-player` dependency

**Touched docs:**
- `documentation/docs/developer/**` — anywhere that referenced `tumlive/`

---

## Scope Check

The spec covers one Artemis PR: one repo, one deployable unit. No decomposition needed. The 19-task structure below phases the work internally (rename → enum → parser → gate → webhook plumbing → DTO → error persistence → CSP → client) but all tasks land in the same PR.

---

### Task 1: Branch + worktree baseline

The worktree already exists at `/Users/pat/projects/claudeworktrees/Artemis/feature-youtube-transcription` on branch `feature/videosource-youtube-transcription`.

- [ ] **Step 1.1: Confirm branch state**

```bash
cd /Users/pat/projects/claudeworktrees/Artemis/feature-youtube-transcription
git status && git log --oneline -5
```

Expected: clean worktree, tip at the previously committed spec.

- [ ] **Step 1.2: Run baseline build + test sanity check**

```bash
./gradlew compileJava -x webapp 2>&1 | tail -10
./gradlew test --tests TumLiveServiceTest -x webapp 2>&1 | tail -20
```

Expected: compile green, existing TumLive test passes. If red, STOP — fix baseline before proceeding.

---

### Task 2: Rename `tumlive/` → `videosource/` (Task 0 of the spec)

Mechanical first commit. No behavior change.

- [ ] **Step 2.1: Move main source files**

```bash
cd /Users/pat/projects/claudeworktrees/Artemis/feature-youtube-transcription
git mv src/main/java/de/tum/cit/aet/artemis/tumlive src/main/java/de/tum/cit/aet/artemis/videosource
git mv src/test/java/de/tum/cit/aet/artemis/tumlive src/test/java/de/tum/cit/aet/artemis/videosource
```

- [ ] **Step 2.2: Update package statements in every moved file**

```bash
# Java source files
find src/main/java/de/tum/cit/aet/artemis/videosource src/test/java/de/tum/cit/aet/artemis/videosource -name "*.java" -print0 | \
  xargs -0 sed -i '' -e 's|de\.tum\.cit\.aet\.artemis\.tumlive|de.tum.cit.aet.artemis.videosource|g'
```

Verify:
```bash
grep -rn "artemis.tumlive" src/ || echo "OK: no remaining tumlive package refs"
```

Expected: `OK: no remaining tumlive package refs`.

- [ ] **Step 2.3: Update consumer imports elsewhere in the repo**

Consumers to check (from repo scan): `PyrisWebhookService.java`, `AbstractSpringIntegrationIndependentTest.java`. The sed above already rewrote imports inside the moved tree; check for stragglers outside it:
```bash
grep -rln "artemis\.tumlive" src/ || echo "OK"
```

If anything remains, apply the same substitution to those files.

- [ ] **Step 2.4: Rename the REST prefix**

Edit `src/main/java/de/tum/cit/aet/artemis/videosource/web/TumLiveResource.java`:
- `@RequestMapping("api/tumlive/")` → `@RequestMapping("api/videosource/")`
- **Keep the trailing slash**; `AbstractModuleResourceArchitectureTest` enforces it.

Do NOT rename the class `TumLiveResource` — the class name is still provider-specific; only the module and the URL prefix become source-agnostic. (Same rationale for `TumLiveApi`, `TumLiveService`, `TumLiveEnabled`.)

- [ ] **Step 2.5: Update client + test paths**

Edit `src/main/webapp/app/lecture/manage/lecture-units/services/attachment-video-unit.service.ts:109` — replace `/api/tumlive/playlist` with `/api/videosource/playlist`.

Edit `src/main/webapp/app/lecture/manage/lecture-units/services/attachment-video-unit.service.spec.ts` — same substitution for any hardcoded `/api/tumlive/` strings.

- [ ] **Step 2.6: Rename arch test classes**

Rename the four arch test files and their class declarations:
```bash
cd src/test/java/de/tum/cit/aet/artemis/videosource/architecture
for old in TumLiveCodeStyleArchitectureTest TumLiveRepositoryArchitectureTest TumLiveResourceArchitectureTest TumLiveServiceArchitectureTest; do
  new=$(echo "$old" | sed 's|^TumLive|VideoSource|')
  git mv "$old.java" "$new.java"
  sed -i '' -e "s|class $old|class $new|g" "$new.java"
  sed -i '' -e 's|ARTEMIS_PACKAGE + "\.tumlive"|ARTEMIS_PACKAGE + ".videosource"|g' "$new.java"
done
cd -
```

- [ ] **Step 2.7: Update jacoco module threshold key**

Edit `gradle/jacoco.gradle`: in `ModuleCoverageThresholds`, rename `"tumlive"` key to `"videosource"`. Keep the existing values (`"INSTRUCTION": 0.800, "CLASS": 0`) for now; Task 18 re-measures after YouTube classes land.

- [ ] **Step 2.8: Docs and YAML audit**

```bash
grep -rln "tumlive" documentation/ src/main/resources/ || echo "OK"
```

Expected findings:
- `src/main/resources/config/application-local.yml:40` — `tum-live:` namespace — **keep as-is** (provider-specific config, not module-generic).
- `documentation/docs/developer/**` — any prose reference to the `tumlive` module should be updated to `videosource` + provider-qualified mentions ("TUM Live provider within the videosource module"). Do these text edits manually; do not regex the docs blindly.

- [ ] **Step 2.9: Compile + run arch tests**

```bash
./gradlew compileJava compileTestJava -x webapp 2>&1 | tail -15
./gradlew test --tests "VideoSource*ArchitectureTest" -x webapp 2>&1 | tail -20
```

Expected: compile green, all four arch tests pass. If `VideoSourceResourceArchitectureTest` fails with a `@RequestMapping` prefix violation, it means the trailing slash was dropped in Step 2.4 — fix.

- [ ] **Step 2.10: Run the TUM Live integration test under the new name**

```bash
./gradlew test --tests "*TumLive*Test" -x webapp 2>&1 | tail -20
```

Expected: all pass. (The classes themselves are not renamed; only their packages.)

- [ ] **Step 2.11: Commit**

```bash
git add -A
git commit -m "Video sources: rename tumlive/ module to videosource/"
```

---

### Task 3: `VideoSourceType` enum

**Files:**
- Create: `src/main/java/de/tum/cit/aet/artemis/videosource/domain/VideoSourceType.java`

- [ ] **Step 3.1: Create the enum**

Create `src/main/java/de/tum/cit/aet/artemis/videosource/domain/VideoSourceType.java`:
```java
package de.tum.cit.aet.artemis.videosource.domain;

/**
 * How a lecture unit's video is hosted. Drives (a) which download path Pyris
 * uses during transcription and (b) which player component the client renders.
 *
 * <p>The Artemis server is the canonical source for this value; clients never
 * derive it from the raw URL.
 */
public enum VideoSourceType {
    TUM_LIVE,
    YOUTUBE
}
```

- [ ] **Step 3.2: Compile**

```bash
./gradlew compileJava -x webapp 2>&1 | tail -5
```

Expected: green.

- [ ] **Step 3.3: Commit**

```bash
git add src/main/java/de/tum/cit/aet/artemis/videosource/domain/VideoSourceType.java
git commit -m "Video sources: add VideoSourceType enum"
```

---

### Task 4: `YouTubeUrlService` — extraction + host detection (TDD)

**Files:**
- Create: `src/main/java/de/tum/cit/aet/artemis/videosource/service/YouTubeUrlService.java`
- Create: `src/test/java/de/tum/cit/aet/artemis/videosource/service/YouTubeUrlServiceTest.java`

- [ ] **Step 4.1: Write the failing test**

Create `src/test/java/de/tum/cit/aet/artemis/videosource/service/YouTubeUrlServiceTest.java`:
```java
package de.tum.cit.aet.artemis.videosource.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class YouTubeUrlServiceTest {

    private final YouTubeUrlService service = new YouTubeUrlService();

    static Stream<Arguments> validUrlsAndIds() {
        return Stream.of(
            Arguments.of("https://www.youtube.com/watch?v=dQw4w9WgXcQ", "dQw4w9WgXcQ"),
            Arguments.of("http://youtube.com/watch?v=dQw4w9WgXcQ", "dQw4w9WgXcQ"),
            Arguments.of("https://m.youtube.com/watch?v=dQw4w9WgXcQ", "dQw4w9WgXcQ"),
            Arguments.of("https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=share", "dQw4w9WgXcQ"),
            Arguments.of("https://youtu.be/dQw4w9WgXcQ", "dQw4w9WgXcQ"),
            Arguments.of("https://youtu.be/dQw4w9WgXcQ?t=42", "dQw4w9WgXcQ"),
            Arguments.of("https://www.youtube.com/embed/dQw4w9WgXcQ", "dQw4w9WgXcQ"),
            Arguments.of("https://www.youtube.com/live/dQw4w9WgXcQ", "dQw4w9WgXcQ"),
            Arguments.of("https://www.youtube.com/shorts/dQw4w9WgXcQ", "dQw4w9WgXcQ"),
            Arguments.of("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ", "dQw4w9WgXcQ"),
            Arguments.of("HTTPS://WWW.YOUTUBE.COM/watch?v=dQw4w9WgXcQ", "dQw4w9WgXcQ")
        );
    }

    @ParameterizedTest
    @MethodSource("validUrlsAndIds")
    void extractsVideoIdFromValidUrl(String url, String expected) {
        assertThat(service.extractYouTubeVideoId(url)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "not a url",
        "https://vimeo.com/123",
        "https://notyoutube.com/watch?v=dQw4w9WgXcQ",
        "https://youtube.com.evil.com/watch?v=dQw4w9WgXcQ",
        "https://fakeyoutu.be/dQw4w9WgXcQ",
        "ftp://youtube.com/watch?v=dQw4w9WgXcQ",
        "javascript:alert(1)",
        "data:text/html,hi",
        "https://www.youtube.com/watch",              // no v= param
        "https://www.youtube.com/watch?v=short",      // id too short
        "https://youtu.be/"                            // empty path
    })
    void rejectsInvalidUrl(String url) {
        assertThat(service.extractYouTubeVideoId(url)).isEmpty();
    }

    @Test
    void rejectsNullUrl() {
        assertThat(service.extractYouTubeVideoId(null)).isEmpty();
    }

    @Test
    void isYouTubeUrlDelegatesToExtraction() {
        assertThat(service.isYouTubeUrl("https://youtu.be/dQw4w9WgXcQ")).isTrue();
        assertThat(service.isYouTubeUrl("https://vimeo.com/123")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://youtube.com/watch?v=bogus-id-goes-here",
        "https://m.youtube.com/watch",
        "https://YOUTU.BE/anything",
        "https://www.YouTube.Com/ANY",
        "https://youtube-nocookie.com/garbage"
    })
    void hasYouTubeHostTrueForYouTubeHostsIrrespectiveOfPath(String url) {
        assertThat(service.hasYouTubeHost(url)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://vimeo.com/123",
        "https://notyoutube.com/watch?v=dQw4w9WgXcQ",
        "https://youtube.com.evil.com/watch?v=dQw4w9WgXcQ",
        "https://fakeyoutu.be/dQw4w9WgXcQ",
        "ftp://www.youtube.com/watch?v=dQw4w9WgXcQ"
    })
    void hasYouTubeHostFalseForSpoofsAndNonHttp(String url) {
        assertThat(service.hasYouTubeHost(url)).isFalse();
    }

    @Test
    void hostSetInvariant_extractableImpliesHasYouTubeHost() {
        // Every URL that yields an ID must also be recognized as a YouTube host.
        validUrlsAndIds().forEach(args -> {
            String url = (String) args.get()[0];
            assertThat(service.hasYouTubeHost(url))
                .as("hasYouTubeHost should be true for extractable URL: %s", url)
                .isTrue();
        });
    }
}
```

- [ ] **Step 4.2: Run — must fail**

```bash
./gradlew test --tests YouTubeUrlServiceTest -x webapp 2>&1 | tee /tmp/yt-url-test.log | tail -20
```

Expected: compile failure — class `YouTubeUrlService` does not exist.

- [ ] **Step 4.3: Implement `YouTubeUrlService`**

Create `src/main/java/de/tum/cit/aet/artemis/videosource/service/YouTubeUrlService.java`:
```java
package de.tum.cit.aet.artemis.videosource.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Pure URL parsing for YouTube video URLs. No network calls, no Spring wiring
 * beyond being a bean, and no {@code @Conditional} — YouTube URL recognition
 * is unconditional.
 */
@Service
@Lazy
public class YouTubeUrlService {

    private static final Set<String> YOUTUBE_HOSTS = Set.of(
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "youtu.be",
        "youtube-nocookie.com",
        "www.youtube-nocookie.com"
    );

    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{11}$");

    private Optional<URI> parseHttpUri(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return Optional.empty();
            }
            String lowerScheme = scheme.toLowerCase(Locale.ROOT);
            if (!lowerScheme.equals("http") && !lowerScheme.equals("https")) {
                return Optional.empty();
            }
            if (uri.getHost() == null) {
                return Optional.empty();
            }
            return Optional.of(uri);
        }
        catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    /**
     * @return true when the URL parses as an HTTP(S) URL whose host is a known YouTube host.
     *         Host-only: a YouTube host with a malformed path/query still returns true here.
     */
    public boolean hasYouTubeHost(String url) {
        return parseHttpUri(url).map(u -> YOUTUBE_HOSTS.contains(u.getHost().toLowerCase(Locale.ROOT))).orElse(false);
    }

    /**
     * Extract the 11-character YouTube video ID from a URL, or empty if the URL
     * doesn't match any supported YouTube URL shape.
     */
    public Optional<String> extractYouTubeVideoId(String url) {
        Optional<URI> parsed = parseHttpUri(url);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        URI uri = parsed.get();
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!YOUTUBE_HOSTS.contains(host)) {
            return Optional.empty();
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        String query = uri.getQuery() == null ? "" : uri.getQuery();

        Optional<String> candidate;
        if (host.equals("youtu.be")) {
            candidate = firstPathSegment(path);
        }
        else if (path.startsWith("/embed/") || path.startsWith("/live/") || path.startsWith("/shorts/")) {
            candidate = firstPathSegmentAfterPrefix(path);
        }
        else if (path.equals("/watch") || path.equals("/watch/")) {
            candidate = extractQueryParam(query, "v");
        }
        else {
            return Optional.empty();
        }
        return candidate.filter(id -> VIDEO_ID_PATTERN.matcher(id).matches());
    }

    public boolean isYouTubeUrl(String url) {
        return extractYouTubeVideoId(url).isPresent();
    }

    private Optional<String> firstPathSegment(String path) {
        if (path == null || path.length() < 2) {
            return Optional.empty();
        }
        String trimmed = path.substring(1);  // drop leading '/'
        int slash = trimmed.indexOf('/');
        String seg = slash < 0 ? trimmed : trimmed.substring(0, slash);
        return seg.isBlank() ? Optional.empty() : Optional.of(seg);
    }

    private Optional<String> firstPathSegmentAfterPrefix(String path) {
        // path is like "/embed/<id>" or "/live/<id>"
        int slash = path.indexOf('/', 1);
        if (slash < 0 || slash + 1 >= path.length()) {
            return Optional.empty();
        }
        String rest = path.substring(slash + 1);
        int nextSlash = rest.indexOf('/');
        String seg = nextSlash < 0 ? rest : rest.substring(0, nextSlash);
        return seg.isBlank() ? Optional.empty() : Optional.of(seg);
    }

    private Optional<String> extractQueryParam(String query, String key) {
        if (query.isBlank()) {
            return Optional.empty();
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            String k = pair.substring(0, eq);
            String v = pair.substring(eq + 1);
            if (k.equals(key) && !v.isBlank()) {
                return Optional.of(v);
            }
        }
        return Optional.empty();
    }
}
```

- [ ] **Step 4.4: Run — must pass**

```bash
./gradlew test --tests YouTubeUrlServiceTest -x webapp 2>&1 | tee /tmp/yt-url-test.log | tail -15
```

Expected: all tests pass. If any fail, read `/tmp/yt-url-test.log` and fix — do not weaken the tests.

- [ ] **Step 4.5: Commit**

```bash
git add src/main/java/de/tum/cit/aet/artemis/videosource/service/YouTubeUrlService.java \
        src/test/java/de/tum/cit/aet/artemis/videosource/service/YouTubeUrlServiceTest.java
git commit -m "Video sources: add YouTubeUrlService with strict URL parsing"
```

---

### Task 5: Save-time YouTube URL gate in `AttachmentVideoUnitResource`

**Files:**
- Modify: `src/main/java/de/tum/cit/aet/artemis/lecture/web/AttachmentVideoUnitResource.java`
- Modify/create: `src/test/java/de/tum/cit/aet/artemis/lecture/AttachmentVideoUnitResourceTest.java` (or an existing nearby integration test class — reuse if one already covers create/update)
- Modify: `src/main/webapp/i18n/en/lectureUnit.json`, `src/main/webapp/i18n/de/lectureUnit.json`

- [ ] **Step 5.1: Locate existing create/update handlers**

```bash
grep -n "PostMapping\|PutMapping\|videoSource\|BadRequestAlertException" /Users/pat/projects/claudeworktrees/Artemis/feature-youtube-transcription/src/main/java/de/tum/cit/aet/artemis/lecture/web/AttachmentVideoUnitResource.java | head -30
```

Record the two method names (create and update) and the `ENTITY_NAME` constant.

- [ ] **Step 5.2: Write the failing test (integration)**

Add to the relevant `*Test` class (prefer an existing integration test harness if one exists — grep for `AttachmentVideoUnit.*IntegrationTest` / `AttachmentVideoUnitResourceTest`):

```java
@Test
void createRejectsMalformedYouTubeUrlWithInvalidYouTubeUrlKey() throws Exception {
    var dto = buildValidCreateDto();  // use existing helper pattern
    dto.setVideoSource("https://youtube.com/watch?v=shortid");
    request.performMvcRequest(post(ENDPOINT).with(csrf()).contentType(MediaType.MULTIPART_FORM_DATA)
            .flashAttr("attachmentVideoUnit", dto))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorKey").value("invalidYouTubeUrl"));
}

@Test
void createAcceptsWellFormedYouTubeUrl() throws Exception {
    var dto = buildValidCreateDto();
    dto.setVideoSource("https://youtube.com/watch?v=dQw4w9WgXcQ");
    request.performMvcRequest(post(ENDPOINT).with(csrf()).contentType(MediaType.MULTIPART_FORM_DATA)
            .flashAttr("attachmentVideoUnit", dto))
        .andExpect(status().isCreated());
}

@Test
void createAcceptsNonYouTubeUrl() throws Exception {
    var dto = buildValidCreateDto();
    dto.setVideoSource("https://vimeo.com/123");
    request.performMvcRequest(post(ENDPOINT).with(csrf()).contentType(MediaType.MULTIPART_FORM_DATA)
            .flashAttr("attachmentVideoUnit", dto))
        .andExpect(status().isCreated());
}

// Repeat the same three cases for PUT / update
```

Concrete procedure for picking the right test harness:

1. Find the existing integration test that exercises `AttachmentVideoUnitResource`:
   ```bash
   grep -rln "AttachmentVideoUnitResource\|attachmentVideoUnit\|\"video-units\"" src/test/java --include="*.java" | head -10
   ```
2. Open the first result. Note: (a) which base class it extends (likely `AbstractSpringIntegrationIndependentTest`), (b) how create/update requests are issued (usually `request.performMvcRequest(post(URL).with(csrf()).contentType(MediaType.MULTIPART_FORM_DATA).flashAttr(...))` for this resource, since attachments are multipart), and (c) the shared fixture builders the existing tests already use.
3. Add the new test methods directly into that existing test class using the same base, harness, and builders. Do NOT create a new parallel class — duplicating the setup is a maintenance hazard.
4. If and only if no such class exists, create `AttachmentVideoUnitResourceYouTubeGateTest` extending `AbstractSpringIntegrationIndependentTest` and build the minimal fixture from scratch (not the expected case).

- [ ] **Step 5.3: Run — must fail**

```bash
./gradlew test --tests "*AttachmentVideoUnit*YouTube*" -x webapp 2>&1 | tail -15
```

Expected: either compile failure (missing gate) or test failure (URL accepted when it should be rejected).

- [ ] **Step 5.4: Add the gate**

Edit `AttachmentVideoUnitResource.java`. Inject `YouTubeUrlService youTubeUrlService` via the constructor (standard Artemis Spring-bean pattern — add to the constructor signature and field). In **both** the create and update handlers, immediately after the existing validation block for `videoSource`, insert:

```java
if (videoSource != null && !videoSource.isBlank()
        && youTubeUrlService.hasYouTubeHost(videoSource)
        && youTubeUrlService.extractYouTubeVideoId(videoSource).isEmpty()) {
    throw new BadRequestAlertException("Invalid YouTube URL format", ENTITY_NAME, "invalidYouTubeUrl");
}
```

If the video source sits on a DTO field, read it via the DTO accessor; if it sits on the entity after mapping, do the check before persisting. Pick whichever ordering keeps the gate strictly pre-persistence.

- [ ] **Step 5.5: Add i18n keys**

Edit `src/main/webapp/i18n/en/lectureUnit.json`:
```json
"errors": {
    "invalidYouTubeUrl": "This looks like a YouTube URL but couldn't be parsed. Supported formats: youtube.com/watch?v=..., youtu.be/..., youtube.com/embed/..., youtube.com/shorts/..."
}
```
Merge into the existing `errors` block if one exists. Mirror the German version in `de/lectureUnit.json`: "Diese URL sieht wie eine YouTube-URL aus, konnte aber nicht verarbeitet werden. Unterstützte Formate: youtube.com/watch?v=..., youtu.be/..., youtube.com/embed/..., youtube.com/shorts/..."

- [ ] **Step 5.6: Run — must pass**

```bash
./gradlew test --tests "*AttachmentVideoUnit*YouTube*" -x webapp 2>&1 | tail -15
```

Expected: all tests pass.

- [ ] **Step 5.7: Commit**

```bash
git add src/main/java/de/tum/cit/aet/artemis/lecture/web/AttachmentVideoUnitResource.java \
        src/test/java/de/tum/cit/aet/artemis/lecture \
        src/main/webapp/i18n/en/lectureUnit.json \
        src/main/webapp/i18n/de/lectureUnit.json
git commit -m "Video sources: reject malformed YouTube URLs at save time"
```

---

### Task 6: Extend `PyrisLectureUnitWebhookDTO` with `videoSourceType`

**Files:**
- Modify: `src/main/java/de/tum/cit/aet/artemis/iris/service/pyris/dto/lectureingestionwebhook/PyrisLectureUnitWebhookDTO.java`
- Create: `src/test/java/de/tum/cit/aet/artemis/iris/service/pyris/dto/lectureingestionwebhook/PyrisLectureUnitWebhookDTOSerializationTest.java`

- [ ] **Step 6.1: Write the failing test**

Create the serialization test:
```java
package de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.videosource.domain.VideoSourceType;

class PyrisLectureUnitWebhookDTOSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void omitsVideoSourceTypeWhenNull() throws Exception {
        var dto = new PyrisLectureUnitWebhookDTO("", 0, null, 1L, "n", 2L, "l", 3L, "c", "d", "url", "https://x", null);
        String json = mapper.writeValueAsString(dto);
        assertThat(json).doesNotContain("videoSourceType");
    }

    @Test
    void includesVideoSourceTypeWhenPresent() throws Exception {
        var dto = new PyrisLectureUnitWebhookDTO("", 0, null, 1L, "n", 2L, "l", 3L, "c", "d", "url", "https://x", VideoSourceType.YOUTUBE);
        String json = mapper.writeValueAsString(dto);
        assertThat(json).contains("\"videoSourceType\":\"YOUTUBE\"");
    }
}
```

- [ ] **Step 6.2: Run — must fail**

Expected: compile failure (DTO has 12 components, not 13).

- [ ] **Step 6.3: Extend the record**

Edit `PyrisLectureUnitWebhookDTO.java`:
```java
package de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.videosource.domain.VideoSourceType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLectureUnitWebhookDTO(
        String pdfFile,
        int attachmentVersion,
        PyrisLectureTranscriptionDTO transcription,
        long lectureUnitId,
        String lectureUnitName,
        long lectureId,
        String lectureName,
        long courseId,
        String courseName,
        String courseDescription,
        String lectureUnitLink,
        String videoLink,
        VideoSourceType videoSourceType) {
}
```

- [ ] **Step 6.4: Run — must pass**

```bash
./gradlew test --tests PyrisLectureUnitWebhookDTOSerializationTest -x webapp 2>&1 | tail -10
```

Expected: both tests pass.

- [ ] **Step 6.5: Commit**

```bash
git add src/main/java/de/tum/cit/aet/artemis/iris/service/pyris/dto/lectureingestionwebhook/PyrisLectureUnitWebhookDTO.java \
        src/test/java/de/tum/cit/aet/artemis/iris/service/pyris/dto/lectureingestionwebhook
git commit -m "Pyris webhook DTO: add videoSourceType"
```

---

### Task 7: `PyrisWebhookService` — `ResolvedVideo` + videoSourceType forwarding

**Files:**
- Modify: `src/main/java/de/tum/cit/aet/artemis/iris/service/pyris/PyrisWebhookService.java`
- Create: `src/test/java/de/tum/cit/aet/artemis/iris/service/pyris/PyrisWebhookServiceResolveVideoUrlTest.java`

- [ ] **Step 7.1: Write failing unit tests (Mockito, no Spring)**

Create `PyrisWebhookServiceResolveVideoUrlTest.java`:
```java
package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.videosource.api.TumLiveApi;
import de.tum.cit.aet.artemis.videosource.domain.VideoSourceType;
import de.tum.cit.aet.artemis.videosource.service.YouTubeUrlService;

class PyrisWebhookServiceResolveVideoUrlTest {

    private final YouTubeUrlService youTubeUrlService = new YouTubeUrlService();

    private PyrisWebhookService withTumLive(TumLiveApi tumLiveApi) {
        // PyrisWebhookService ctor signature as of plan review (develop HEAD):
        //   (PyrisConnectorService, PyrisJobService, IrisSettingsService,
        //    Optional<LectureRepositoryApi>, Optional<LectureUnitRepositoryApi>,
        //    Optional<LectureTranscriptionsRepositoryApi>, Optional<TumLiveApi>)
        // Task 7 adds YouTubeUrlService as the 8th parameter. Fill unrelated
        // collaborators with mocks since resolveVideoUrl doesn't touch them.
        return new PyrisWebhookService(
            mock(PyrisConnectorService.class),
            mock(PyrisJobService.class),
            mock(IrisSettingsService.class),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.ofNullable(tumLiveApi),
            youTubeUrlService
        );
    }

    @Test
    void tumLiveMatchReturnsTumLiveType() {
        var api = mock(TumLiveApi.class);
        when(api.getTumLivePlaylistLink(any())).thenReturn(Optional.of("https://live.rbg.tum.de/pl.m3u8"));
        var svc = withTumLive(api);
        var resolved = svc.resolveVideoUrl("https://live.rbg.tum.de/?course=foo&streamId=1");
        assertThat(resolved.url()).isEqualTo("https://live.rbg.tum.de/pl.m3u8");
        assertThat(resolved.type()).isEqualTo(VideoSourceType.TUM_LIVE);
    }

    @Test
    void youTubeUrlReturnsYouTubeType() {
        var svc = withTumLive(null);
        var resolved = svc.resolveVideoUrl("https://youtu.be/dQw4w9WgXcQ");
        assertThat(resolved.url()).isEqualTo("https://youtu.be/dQw4w9WgXcQ");
        assertThat(resolved.type()).isEqualTo(VideoSourceType.YOUTUBE);
    }

    @Test
    void unknownSourceReturnsNullType() {
        var svc = withTumLive(null);
        var resolved = svc.resolveVideoUrl("https://vimeo.com/123");
        assertThat(resolved.url()).isEqualTo("https://vimeo.com/123");
        assertThat(resolved.type()).isNull();
    }

    @Test
    void nullUrlReturnsNullResolution() {
        var svc = withTumLive(null);
        var resolved = svc.resolveVideoUrl(null);
        assertThat(resolved.url()).isNull();
        assertThat(resolved.type()).isNull();
    }

    @Test
    void blankUrlReturnsBlankResolution() {
        var svc = withTumLive(null);
        var resolved = svc.resolveVideoUrl("   ");
        assertThat(resolved.url()).isEqualTo("   ");
        assertThat(resolved.type()).isNull();
    }

    @Test
    void tumLiveApiExceptionFallsBackToOriginalUrlWithNullType() {
        var api = mock(TumLiveApi.class);
        when(api.getTumLivePlaylistLink(any())).thenThrow(new RuntimeException("boom"));
        var svc = withTumLive(api);
        var resolved = svc.resolveVideoUrl("https://live.rbg.tum.de/?x=1");
        assertThat(resolved.url()).isEqualTo("https://live.rbg.tum.de/?x=1");
        assertThat(resolved.type()).isNull();
    }

    @Test
    void tumLiveCheckedBeforeYouTubeSoTumLiveWins() {
        // A synthetic URL that is both a TumLive resolvable URL and looks like youtu.be
        // cannot actually exist in practice, but we want to document priority. Instead
        // test: TumLive resolution, when it succeeds, never consults YouTube parsing.
        var api = mock(TumLiveApi.class);
        when(api.getTumLivePlaylistLink(any())).thenReturn(Optional.of("https://live.rbg.tum.de/resolved.m3u8"));
        var svc = withTumLive(api);
        var resolved = svc.resolveVideoUrl("https://live.rbg.tum.de/foo");
        assertThat(resolved.type()).isEqualTo(VideoSourceType.TUM_LIVE);
    }

    @Test
    void absentTumLiveApiYouTubeStillResolves() {
        var svc = withTumLive(null);
        var resolved = svc.resolveVideoUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        assertThat(resolved.type()).isEqualTo(VideoSourceType.YOUTUBE);
    }
}
```

Note: fill the `/* ... other mocks ... */` with `mock(...)` calls matching whatever `PyrisWebhookService`'s actual constructor requires. Reference existing tests of `PyrisWebhookService` for the exact pattern.

- [ ] **Step 7.2: Run — must fail**

Expected: compile failure (`ResolvedVideo` not defined; ctor does not accept `YouTubeUrlService`).

- [ ] **Step 7.3: Modify `PyrisWebhookService`**

Inside `PyrisWebhookService.java`:

1. Add field + constructor parameter for `YouTubeUrlService youTubeUrlService` (non-optional).

2. Introduce package-private record at file bottom:
```java
record ResolvedVideo(String url, VideoSourceType type) {
}
```

3. Change the signature of `resolveVideoUrl(String videoSource)` to return `ResolvedVideo` (replacing the current `String` return). New body:
```java
ResolvedVideo resolveVideoUrl(String videoSource) {
    if (videoSource == null || videoSource.isBlank()) {
        return new ResolvedVideo(videoSource, null);
    }
    if (tumLiveApi.isPresent()) {
        try {
            Optional<String> resolved = tumLiveApi.get().getTumLivePlaylistLink(videoSource);
            if (resolved.isPresent()) {
                log.info("Resolved TUM Live URL to HLS playlist for Iris ingestion");
                return new ResolvedVideo(resolved.get(), VideoSourceType.TUM_LIVE);
            }
        }
        catch (RuntimeException e) {
            log.warn("TUM Live resolution failed; falling back to raw URL", e);
            return new ResolvedVideo(videoSource, null);
        }
    }
    if (youTubeUrlService.isYouTubeUrl(videoSource)) {
        return new ResolvedVideo(videoSource, VideoSourceType.YOUTUBE);
    }
    return new ResolvedVideo(videoSource, null);
}
```

4. Update the caller in this same file (`buildLectureUnitWebhookDTO`-equivalent — the spot that today calls `resolveVideoUrl(videoSource)` and feeds the result into the webhook DTO). Destructure:
```java
ResolvedVideo resolved = resolveVideoUrl(videoSource);
// pass resolved.url() where videoLink went; pass resolved.type() as the new videoSourceType component
```

- [ ] **Step 7.4: Run — must pass**

```bash
./gradlew test --tests PyrisWebhookServiceResolveVideoUrlTest -x webapp 2>&1 | tail -15
```

Expected: 8 tests pass.

- [ ] **Step 7.5: Run full Pyris-related suite to catch regressions**

```bash
./gradlew test --tests "*PyrisWebhookService*" -x webapp 2>&1 | tail -20
```

Expected: green.

- [ ] **Step 7.6: Commit**

```bash
git add src/main/java/de/tum/cit/aet/artemis/iris/service/pyris/PyrisWebhookService.java \
        src/test/java/de/tum/cit/aet/artemis/iris/service/pyris/PyrisWebhookServiceResolveVideoUrlTest.java
git commit -m "Pyris webhook: tag videoSourceType (TUM_LIVE | YOUTUBE | null)"
```

---

### Task 8: Extend student-facing lecture unit DTO

**Confirmed during plan review:** the target is the nested static record `AttachmentVideoUnitDTO` declared at `LectureResource.java:363`, consumed by the `GET courses/{courseId}/lectures-with-slides` endpoint at `LectureResource.java:297`. Current components: `Long id, String name, List<SlideDTO> slides, @Nullable AttachmentDTO attachment, ZonedDateTime releaseDate, String type`. It does NOT currently expose `videoSource` — this task adds that AND the new transcription-related fields. The factory is `AttachmentVideoUnitDTO.from(AttachmentVideoUnit)` at line 365.

**Files:**
- Modify: `src/main/java/de/tum/cit/aet/artemis/lecture/web/LectureResource.java` — the nested record and its `from(...)` factory
- Create: test for the updated DTO (plain unit test, no Spring context needed for invariants)
- Modify: whatever service builds the DTO for slide-enriched student responses if the `from` factory's logic becomes non-trivial (TumLive resolution requires `TumLiveApi` presence — see Step 8.5).

- [ ] **Step 8.1: Read the exact current record + factory**

```bash
sed -n '360,380p' /Users/pat/projects/claudeworktrees/Artemis/feature-youtube-transcription/src/main/java/de/tum/cit/aet/artemis/lecture/web/LectureResource.java
```

Record the exact field order and the `from()` body — you'll extend both.

- [ ] **Step 8.2: Write the failing test**

Target class confirmed: `LectureResource.AttachmentVideoUnitDTO` (nested public record at line 363). Current components: `(Long id, String name, List<SlideDTO> slides, @Nullable AttachmentDTO attachment, ZonedDateTime releaseDate, String type)`. After this task, components become: `(Long id, String name, List<SlideDTO> slides, @Nullable AttachmentDTO attachment, ZonedDateTime releaseDate, String type, String videoSource, @Nullable VideoSourceType videoSourceType, @Nullable String youtubeVideoId, @Nullable String transcriptionErrorCode)`.

Create `src/test/java/de/tum/cit/aet/artemis/lecture/web/LectureResourceAttachmentVideoUnitDTOTest.java`:
```java
package de.tum.cit.aet.artemis.lecture.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.lecture.web.LectureResource.AttachmentVideoUnitDTO;
import de.tum.cit.aet.artemis.videosource.domain.VideoSourceType;

class LectureResourceAttachmentVideoUnitDTOTest {

    private static final Long ID = 1L;
    private static final String NAME = "unit";
    private static final List<?> SLIDES = List.of();  // adapt typed list to SlideDTO import
    private static final ZonedDateTime RELEASE = ZonedDateTime.now();
    private static final String TYPE = "attachment";

    @Test
    void youTubeTypeRequiresYouTubeVideoId() {
        assertThrows(IllegalArgumentException.class, () ->
            new AttachmentVideoUnitDTO(ID, NAME, (List) SLIDES, null, RELEASE, TYPE,
                "https://youtube.com/watch?v=dQw4w9WgXcQ",
                VideoSourceType.YOUTUBE, null, null));
    }

    @Test
    void tumLiveTypeForbidsYouTubeVideoId() {
        assertThrows(IllegalArgumentException.class, () ->
            new AttachmentVideoUnitDTO(ID, NAME, (List) SLIDES, null, RELEASE, TYPE,
                "https://live.rbg.tum.de/foo",
                VideoSourceType.TUM_LIVE, "dQw4w9WgXcQ", null));
    }

    @Test
    void nullTypeForbidsYouTubeVideoId() {
        assertThrows(IllegalArgumentException.class, () ->
            new AttachmentVideoUnitDTO(ID, NAME, (List) SLIDES, null, RELEASE, TYPE,
                "https://vimeo.com/1",
                null, "dQw4w9WgXcQ", null));
    }

    @Test
    void validYouTubeDtoConstructs() {
        var dto = new AttachmentVideoUnitDTO(ID, NAME, (List) SLIDES, null, RELEASE, TYPE,
            "https://youtu.be/dQw4w9WgXcQ",
            VideoSourceType.YOUTUBE, "dQw4w9WgXcQ", null);
        assertThat(dto.videoSourceType()).isEqualTo(VideoSourceType.YOUTUBE);
        assertThat(dto.youtubeVideoId()).isEqualTo("dQw4w9WgXcQ");
    }
}
```

Replace `(List) SLIDES` with the properly-typed `List<SlideDTO>` import once the real type is reintroduced — the cast above is a shortcut for compactness; if the compiler complains, import `SlideDTO` and make the list `List.<SlideDTO>of()`.

- [ ] **Step 8.3: Run — must fail**

Expected: compile error (new fields missing).

- [ ] **Step 8.4: Extend the nested record with a compact validator**

Edit `LectureResource.java` (the nested record at line 363). Add four components (`videoSource`, `videoSourceType`, `youtubeVideoId`, `transcriptionErrorCode`) and a compact canonical constructor:

```java
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AttachmentVideoUnitDTO(
        Long id,
        String name,
        List<SlideDTO> slides,
        @Nullable AttachmentDTO attachment,
        ZonedDateTime releaseDate,
        String type,
        @Nullable String videoSource,
        @Nullable VideoSourceType videoSourceType,
        @Nullable String youtubeVideoId,
        @Nullable String transcriptionErrorCode) {

    public AttachmentVideoUnitDTO {
        if (videoSourceType == VideoSourceType.YOUTUBE && (youtubeVideoId == null || youtubeVideoId.isBlank())) {
            throw new IllegalArgumentException("YOUTUBE videoSourceType requires non-blank youtubeVideoId");
        }
        if (videoSourceType != VideoSourceType.YOUTUBE && youtubeVideoId != null) {
            throw new IllegalArgumentException("youtubeVideoId must be null when videoSourceType != YOUTUBE");
        }
    }

    // Existing `from(...)` factory stays here but extends to populate the new fields;
    // Step 8.5 shows the body.
}
```

Add the import `import de.tum.cit.aet.artemis.videosource.domain.VideoSourceType;` at the top of `LectureResource.java`.

- [ ] **Step 8.5: Extract a shared `VideoSourceResolver` + use it in the DTO factory**

The resolution logic now lives in two places (`PyrisWebhookService.resolveVideoUrl` from Task 7, and the DTO factory here). Rule of two — extract. Create **two** files: `ResolvedVideo.java` (the record) and `VideoSourceResolver.java` (the service). Java allows only one public top-level type per file, so they cannot share a file.

Create `src/main/java/de/tum/cit/aet/artemis/videosource/service/ResolvedVideo.java`:
```java
package de.tum.cit.aet.artemis.videosource.service;

import de.tum.cit.aet.artemis.videosource.domain.VideoSourceType;

public record ResolvedVideo(String url, VideoSourceType type) {
}
```

Create `src/main/java/de/tum/cit/aet/artemis/videosource/service/VideoSourceResolver.java`:
```java
package de.tum.cit.aet.artemis.videosource.service;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.videosource.api.TumLiveApi;
import de.tum.cit.aet.artemis.videosource.domain.VideoSourceType;

@Service
@Lazy
public class VideoSourceResolver {

    private final Optional<TumLiveApi> tumLiveApi;
    private final YouTubeUrlService youTubeUrlService;

    public VideoSourceResolver(Optional<TumLiveApi> tumLiveApi, YouTubeUrlService youTubeUrlService) {
        this.tumLiveApi = tumLiveApi;
        this.youTubeUrlService = youTubeUrlService;
    }

    public ResolvedVideo resolve(String videoSource) {
        if (videoSource == null || videoSource.isBlank()) {
            return new ResolvedVideo(videoSource, null);
        }
        if (tumLiveApi.isPresent()) {
            try {
                Optional<String> resolved = tumLiveApi.get().getTumLivePlaylistLink(videoSource);
                if (resolved.isPresent()) {
                    return new ResolvedVideo(resolved.get(), VideoSourceType.TUM_LIVE);
                }
            }
            catch (RuntimeException e) {
                return new ResolvedVideo(videoSource, null);
            }
        }
        if (youTubeUrlService.isYouTubeUrl(videoSource)) {
            return new ResolvedVideo(videoSource, VideoSourceType.YOUTUBE);
        }
        return new ResolvedVideo(videoSource, null);
    }
}
```

Because `ResolvedVideo` is now the shared public record, also **delete** the package-private `ResolvedVideo` record declared at the bottom of `PyrisWebhookService.java` in Task 7 and update the import there to `de.tum.cit.aet.artemis.videosource.service.ResolvedVideo`.

Update `PyrisWebhookService` (Task 7 code) to delegate to `VideoSourceResolver.resolve(...)` instead of the inline chain, and delete the inline `ResolvedVideo` record it used to declare (now lives in the shared package).

In `LectureResource.java`:
1. Promote `AttachmentVideoUnitDTO.from` from a `static` factory to an instance method on a new `@Service AttachmentVideoUnitDtoFactory` (or inject `VideoSourceResolver` + `YouTubeUrlService` directly into `LectureResource` and pass them to the static factory). Constructor injection; `LectureResource` already holds multiple collaborators — follow the existing field style.
2. In the factory body, for each `AttachmentVideoUnit`:
```java
ResolvedVideo resolved = videoSourceResolver.resolve(unit.getVideoSource());
String youtubeVideoId = resolved.type() == VideoSourceType.YOUTUBE
        ? youTubeUrlService.extractYouTubeVideoId(unit.getVideoSource()).orElse(null)
        : null;
String transcriptionErrorCode = unit.getProcessingState() == null ? null : unit.getProcessingState().getErrorCode();
return new AttachmentVideoUnitDTO(
        unit.getId(), unit.getName(), new ArrayList<>(),
        attachment != null ? AttachmentDTO.from(attachment) : null,
        unit.getReleaseDate(), "attachment",
        unit.getVideoSource(), resolved.type(), youtubeVideoId, transcriptionErrorCode);
```

(Adjust argument order to match the new record's canonical constructor.)

Since `AttachmentVideoUnit.getProcessingState()` might not be the correct accessor name, confirm by reading `AttachmentVideoUnit.java` first — the persistence field `errorCode` added in Task 10 lives on `LectureUnitProcessingState`, accessed via whatever relation already exists.

- [ ] **Step 8.6: Run — must pass**

```bash
./gradlew test --tests "LectureResourceAttachmentVideoUnitDTOTest" -x webapp 2>&1 | tail -10
```

Expected: green.

- [ ] **Step 8.7: Commit**

```bash
git add src/main/java/de/tum/cit/aet/artemis/lecture/dto \
        src/main/java/de/tum/cit/aet/artemis/lecture/web/LectureResource.java \
        src/test/java/de/tum/cit/aet/artemis/lecture/dto
git commit -m "Lecture DTO: expose videoSourceType, youtubeVideoId, transcriptionErrorCode"
```

---

### Task 9: Extend `PyrisLectureIngestionStatusUpdateDTO` with `errorCode`

**Files:**
- Modify: `src/main/java/de/tum/cit/aet/artemis/iris/service/pyris/dto/lectureingestionwebhook/PyrisLectureIngestionStatusUpdateDTO.java`
- Create: adjacent serialization test.

- [ ] **Step 9.1: Write the failing test** — assert Jackson maps incoming `"errorCode":"YOUTUBE_PRIVATE"` to the new field. Pattern follows Task 6.

- [ ] **Step 9.2: Run — must fail (field missing).**

- [ ] **Step 9.3: Add the field.** The Pyris wire key is snake_case `error_code` (confirmed in the companion Pyris plan, matching spec lines 287-293). Jackson needs an explicit alias. Append to the record's component list:

```java
@com.fasterxml.jackson.annotation.JsonProperty("error_code") String errorCode
```

Older Pyris deployments omit the field; Jackson will leave it null thanks to `@JsonInclude(NON_EMPTY)` on the DTO (confirm by reading the existing annotations on `PyrisLectureIngestionStatusUpdateDTO` — preserve them).

- [ ] **Step 9.4: Run — must pass.**

- [ ] **Step 9.5: Commit**

```bash
git commit -m "Pyris status DTO: accept errorCode"
```

- [ ] **Step 9.6: Cross-repo contract test — pin the JSON wire keys**

Because `videoSourceType` (outbound) and `error_code` (inbound) cross repos, a typo on either side will fail silently in production. Add a dedicated wire-format test that locks the serialized shape.

Create `src/test/java/de/tum/cit/aet/artemis/iris/service/pyris/dto/WireFormatContractTest.java`:
```java
package de.tum.cit.aet.artemis.iris.service.pyris.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureUnitWebhookDTO;
import de.tum.cit.aet.artemis.videosource.domain.VideoSourceType;

class WireFormatContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void outboundWebhookUsesCamelCaseVideoSourceType() throws Exception {
        var dto = new PyrisLectureUnitWebhookDTO(
                "", 0, null, 1L, "n", 2L, "l", 3L, "c", "d", "url", "https://x", VideoSourceType.YOUTUBE);
        JsonNode json = mapper.readTree(mapper.writeValueAsString(dto));
        assertThat(json.has("videoSourceType")).isTrue();
        assertThat(json.get("videoSourceType").asText()).isEqualTo("YOUTUBE");
    }

    @Test
    void inboundStatusUpdateReadsSnakeCaseErrorCode() throws Exception {
        // Pyris sends snake_case per companion plan + spec. Confirm Jackson parses it.
        String wire = "{\"stages\":[],\"tokens\":[],\"error_code\":\"YOUTUBE_PRIVATE\"}";
        var dto = mapper.readValue(wire, PyrisLectureIngestionStatusUpdateDTO.class);
        // Accessor name depends on the record field name chosen in Step 9.3.
        // If the field is `errorCode` with @JsonProperty("error_code"), this assertion works.
        assertThat(dto.errorCode()).isEqualTo("YOUTUBE_PRIVATE");
    }

    @Test
    void inboundStatusUpdateRejectsCamelCaseErrorCode() throws Exception {
        // Defensive: guarantee we have not drifted from snake_case on the inbound side.
        // If someone later adds a camelCase alias, this test should fail — force a
        // conscious decision rather than silent dual-compat.
        String wire = "{\"stages\":[],\"tokens\":[],\"errorCode\":\"YOUTUBE_PRIVATE\"}";
        var dto = mapper.readValue(wire, PyrisLectureIngestionStatusUpdateDTO.class);
        assertThat(dto.errorCode()).isNull();
    }
}
```

Run: `./gradlew test --tests WireFormatContractTest -x webapp`
Expected: all three pass.

Commit:
```bash
git add src/test/java/de/tum/cit/aet/artemis/iris/service/pyris/dto/WireFormatContractTest.java
git commit -m "Tests: pin Pyris wire format contract (videoSourceType / error_code)"
```

---

### Task 10: Liquibase changeset + `LectureUnitProcessingState.errorCode` column

**Files:**
- Create: `src/main/resources/config/liquibase/changelog/<timestamp>_add-lecture-unit-processing-state-error-code.xml`
- Modify: `src/main/resources/config/liquibase/master.xml`
- Modify: `src/main/java/de/tum/cit/aet/artemis/lecture/domain/LectureUnitProcessingState.java`

Note: the timestamp should be the actual filename convention used in the repo. Check `ls src/main/resources/config/liquibase/changelog/ | tail -5` for format.

- [ ] **Step 10.1: Determine the file naming convention**

```bash
ls /Users/pat/projects/claudeworktrees/Artemis/feature-youtube-transcription/src/main/resources/config/liquibase/changelog/ | tail -10
```

Match the most recent file's format (usually `YYYYMMDDHHMMSS_description.xml`).

- [ ] **Step 10.2: Write the failing test**

Create `src/test/java/de/tum/cit/aet/artemis/lecture/domain/LectureUnitProcessingStateErrorCodeTest.java`:
```java
// Unit test against LectureUnitProcessingState — no Spring context needed.
package de.tum.cit.aet.artemis.lecture.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LectureUnitProcessingStateErrorCodeTest {

    @Test
    void errorCodeDefaultsToNull() {
        var state = new LectureUnitProcessingState();
        assertThat(state.getErrorCode()).isNull();
    }

    @Test
    void errorCodeSetterPersistsValue() {
        var state = new LectureUnitProcessingState();
        state.setErrorCode("YOUTUBE_LIVE");
        assertThat(state.getErrorCode()).isEqualTo("YOUTUBE_LIVE");
    }

    @Test
    void clearingErrorKeyAlsoClearsErrorCode() {
        // Mirror the existing errorKey-clearing policy. If LectureUnitProcessingState:226
        // clears errorKey on a transition, it must also clear errorCode.
        var state = new LectureUnitProcessingState();
        state.setErrorKey("some.key");
        state.setErrorCode("YOUTUBE_LIVE");
        state.clearError();   // or whatever the actual method name is; adapt to the code
        assertThat(state.getErrorKey()).isNull();
        assertThat(state.getErrorCode()).isNull();
    }
}
```

Read `LectureUnitProcessingState.java` to confirm the actual method name used for clearing `errorKey` today — adapt the test's `state.clearError()` call to match.

- [ ] **Step 10.3: Run — must fail**

Expected: compile error (getter/setter missing).

- [ ] **Step 10.4: Add the Liquibase changeset**

Create `src/main/resources/config/liquibase/changelog/<timestamp>_add-lecture-unit-processing-state-error-code.xml` (mirror an existing simple-column changeset in the same folder for structure):
```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.9.xsd">

    <changeSet id="<timestamp>-1" author="videosource">
        <addColumn tableName="lecture_unit_processing_state">
            <column name="error_code" type="VARCHAR(64)">
                <constraints nullable="true"/>
            </column>
        </addColumn>
    </changeSet>
</databaseChangeLog>
```

Use the actual table name — grep the entity for `@Table(name = ...)` and use that. Adjust the XSD version to match the neighboring changesets.

- [ ] **Step 10.5: Register in master.xml**

Edit `src/main/resources/config/liquibase/master.xml` — add an `<include file="config/liquibase/changelog/<timestamp>_add-lecture-unit-processing-state-error-code.xml"/>` entry at the end of the existing include list.

- [ ] **Step 10.6: Add field + accessors + clearing to the entity**

Edit `LectureUnitProcessingState.java`:
```java
@Column(name = "error_code", length = 64)
@Nullable
private String errorCode;

public @Nullable String getErrorCode() { return errorCode; }
public void setErrorCode(@Nullable String errorCode) { this.errorCode = errorCode; }
```

**Find every site in the repo where `errorKey` is assigned or cleared, not just within this file.** Spec says the clearing lifecycle must mirror the existing `errorKey` policy, and the existing policy is enforced in multiple classes (the repo scan during plan review confirmed at least `LectureContentProcessingService.java:148` in addition to `LectureUnitProcessingState.java:226`). Run:

```bash
grep -rn "errorKey\|setErrorKey\|\.errorKey *=" src/main/java --include="*.java" | grep -v "^Binary"
```

For each match that performs an assignment or clear of `errorKey`, insert a paired operation on `errorCode` (same value: `null` on clear, caller-provided code on error set). Add a focused unit test per site that asserts the pairing.

Add a unit test (next step) covering this pairing.

- [ ] **Step 10.7: Run — must pass**

```bash
./gradlew test --tests LectureUnitProcessingStateErrorCodeTest -x webapp 2>&1 | tail -10
./gradlew test --tests "*LectureUnit*" -x webapp 2>&1 | tail -20
```

Expected: both new tests pass; existing lecture-unit tests still pass. Note: if the repo runs Liquibase on every test startup (via Testcontainers), an integration test class extending the standard lecture module base will also exercise the migration.

- [ ] **Step 10.8: Commit**

```bash
git add src/main/resources/config/liquibase \
        src/main/java/de/tum/cit/aet/artemis/lecture/domain/LectureUnitProcessingState.java \
        src/test/java/de/tum/cit/aet/artemis/lecture/domain/LectureUnitProcessingStateErrorCodeTest.java
git commit -m "Lecture unit processing state: persist transcription errorCode"
```

---

### Task 11: `PyrisStatusUpdateService` + `ProcessingStateCallbackApi` — thread `errorCode` through

**Files:**
- Modify: `src/main/java/de/tum/cit/aet/artemis/iris/service/pyris/PyrisStatusUpdateService.java:179`
- Modify: `src/main/java/de/tum/cit/aet/artemis/lecture/api/ProcessingStateCallbackApi.java:68`

- [ ] **Step 11.1: Read current collapsing logic**

```bash
sed -n '170,200p' /Users/pat/projects/claudeworktrees/Artemis/feature-youtube-transcription/src/main/java/de/tum/cit/aet/artemis/iris/service/pyris/PyrisStatusUpdateService.java
sed -n '60,80p' /Users/pat/projects/claudeworktrees/Artemis/feature-youtube-transcription/src/main/java/de/tum/cit/aet/artemis/lecture/api/ProcessingStateCallbackApi.java
```

Identify the boolean-only handoff and the current callback signature.

- [ ] **Step 11.2: Write failing test**

Create `src/test/java/de/tum/cit/aet/artemis/iris/service/pyris/PyrisStatusUpdateServiceErrorCodeTest.java` — a unit test that:
1. Builds a `PyrisLectureIngestionStatusUpdateDTO` with a terminal error-state stage and `errorCode = "YOUTUBE_PRIVATE"`.
2. Invokes the terminal handoff.
3. Asserts that the downstream callback API was invoked with `errorCode = "YOUTUBE_PRIVATE"`.

Use Mockito to intercept `ProcessingStateCallbackApi`. Mirror the style of any existing test of `PyrisStatusUpdateService`.

- [ ] **Step 11.3: Run — must fail.**

- [ ] **Step 11.4: Introduce the `TerminalResult` record + thread it through**

Inside `PyrisStatusUpdateService.java`, add:
```java
private record TerminalResult(boolean success, @Nullable String errorCode) {
}
```

Refactor the collapsing logic at :179 to produce a `TerminalResult` (extract a private method if helpful). Pass both `success` and `errorCode` to `ProcessingStateCallbackApi`.

Change `ProcessingStateCallbackApi.onTerminal(...)` (or whatever the method at :68 is named) to accept `boolean success, @Nullable String errorCode` and, internally, to write the `errorCode` to `LectureUnitProcessingState.setErrorCode(errorCode)` before committing the transition.

- [ ] **Step 11.5: Run — must pass.**

- [ ] **Step 11.6: Regression suite**

```bash
./gradlew test --tests "*PyrisStatusUpdate*" --tests "*ProcessingStateCallback*" -x webapp 2>&1 | tail -20
```

Expected: green.

- [ ] **Step 11.7: Commit**

```bash
git commit -m "Transcription callbacks: carry errorCode end-to-end"
```

---

### Task 12: CSP — extend `script-src` for YouTube IFrame API

**Files:**
- Modify: `src/main/java/de/tum/cit/aet/artemis/core/config/SecurityConfiguration.java`
- Create: `src/test/java/de/tum/cit/aet/artemis/core/config/SecurityConfigurationCspTest.java` (or extend an existing one)

- [ ] **Step 12.1: Write failing test**

Test reads the assembled CSP header (via Spring's MockMvc on a trivial endpoint or directly introspects the `SecurityConfiguration` bean) and asserts `script-src` contains `https://www.youtube.com`.

- [ ] **Step 12.2: Run — must fail.**

- [ ] **Step 12.3: Add the origin**

Edit the `script-src` directive in `SecurityConfiguration.java`:
```java
"script-src 'self' 'unsafe-inline' 'unsafe-eval' https://www.youtube.com; "
```

Preserve all other directives and ordering exactly.

- [ ] **Step 12.4: Run — must pass.**

- [ ] **Step 12.5: Commit**

```bash
git commit -m "Security: allow YouTube IFrame API script origin in CSP"
```

- [ ] **Step 12.6: Empirical verification task (note in PR description)**

During manual UI testing of Task 18, open the browser Network tab on a YouTube-embedded lecture unit. Record any additional origins the IFrame API loads from (`s.ytimg.com`, `googleapis.com`, etc.). For any blocked by the current CSP, add them to `script-src` and update the test.

---

### Task 13: Add `@angular/youtube-player` dependency

- [ ] **Step 13.1: Install**

```bash
cd /Users/pat/projects/claudeworktrees/Artemis/feature-youtube-transcription
npm install @angular/youtube-player
```

- [ ] **Step 13.2: Verify lockfile update**

```bash
git diff package.json package-lock.json | head -40
```

Expected: `@angular/youtube-player` at a version compatible with Angular 21 is added. Commit lockfile.

- [ ] **Step 13.3: Commit**

```bash
git add package.json package-lock.json
git commit -m "Client: add @angular/youtube-player dependency"
```

---

### Task 14: `YouTubePlayerComponent` (TDD)

**Files:**
- Create: `src/main/webapp/app/lecture/shared/youtube-player/youtube-player.component.ts`
- Create: `src/main/webapp/app/lecture/shared/youtube-player/youtube-player.component.html`
- Create: `src/main/webapp/app/lecture/shared/youtube-player/youtube-player.component.scss`
- Create: `src/main/webapp/app/lecture/shared/youtube-player/youtube-player.component.spec.ts`

- [ ] **Step 14.1: Write failing Vitest spec**

Create `youtube-player.component.spec.ts`. Cover:

```typescript
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { YouTubePlayerComponent } from './youtube-player.component';

describe('YouTubePlayerComponent', () => {
    let fixture: ComponentFixture<YouTubePlayerComponent>;
    let component: YouTubePlayerComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [YouTubePlayerComponent] }).compileComponents();
        fixture = TestBed.createComponent(YouTubePlayerComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('videoId', 'dQw4w9WgXcQ');
        fixture.componentRef.setInput('transcriptSegments', [
            { start: 0, end: 10, text: 'a' },
            { start: 10, end: 20, text: 'b' },
            { start: 20, end: 30, text: 'c' },
        ]);
    });

    it('starts polling on PLAYING state', () => {
        vi.useFakeTimers();
        const spy = vi.spyOn<any, any>(component, 'updateCurrentSegment');
        // Simulate player ready + stub getCurrentTime()
        (component as any).youtubePlayer = { getCurrentTime: () => 15, seekTo: vi.fn() };
        component.onStateChange({ data: 1 /* PLAYING */ } as any);
        vi.advanceTimersByTime(300);
        expect(spy).toHaveBeenCalled();
        vi.useRealTimers();
    });

    it('stops polling on PAUSED and updates segment once', () => {
        vi.useFakeTimers();
        (component as any).youtubePlayer = { getCurrentTime: () => 25, seekTo: vi.fn() };
        const spy = vi.spyOn<any, any>(component, 'updateCurrentSegment');
        component.onStateChange({ data: 1 } as any);  // start polling
        component.onStateChange({ data: 2 /* PAUSED */ } as any);
        vi.advanceTimersByTime(2000);
        // Polling stopped → spy called once from the PAUSED-branch update only (plus any tick before the pause)
        expect(spy).toHaveBeenCalled();
        vi.useRealTimers();
    });

    it('seekTo calls player.seekTo and updates segment immediately', () => {
        const seekSpy = vi.fn();
        (component as any).youtubePlayer = { getCurrentTime: () => 15, seekTo: seekSpy };
        const updateSpy = vi.spyOn<any, any>(component, 'updateCurrentSegment');
        component.seekTo(12);
        expect(seekSpy).toHaveBeenCalledWith(12, true);
        expect(updateSpy).toHaveBeenCalledWith(12);
    });

    it('emits playerFailed when readiness timeout elapses without onPlayerReady', () => {
        vi.useFakeTimers();
        const emitSpy = vi.spyOn(component.playerFailed, 'emit');
        component.ngAfterViewInit();
        vi.advanceTimersByTime(11_000);
        expect(emitSpy).toHaveBeenCalled();
        vi.useRealTimers();
    });

    it('emits playerFailed on YT error event', () => {
        const emitSpy = vi.spyOn(component.playerFailed, 'emit');
        component.onPlayerError({ data: 100 } as any);
        expect(emitSpy).toHaveBeenCalled();
    });

    it('clears readiness timeout on successful onPlayerReady', () => {
        vi.useFakeTimers();
        const emitSpy = vi.spyOn(component.playerFailed, 'emit');
        component.ngAfterViewInit();
        vi.advanceTimersByTime(5_000);
        (component as any).youtubePlayer = { getCurrentTime: () => 0, seekTo: vi.fn() };
        component.onPlayerReady({} as any);
        vi.advanceTimersByTime(10_000);
        expect(emitSpy).not.toHaveBeenCalled();
        vi.useRealTimers();
    });

    it('applies initialTimestamp on ready and updates segment immediately', () => {
        fixture.componentRef.setInput('initialTimestamp', 25);
        const seekSpy = vi.fn();
        (component as any).youtubePlayer = { getCurrentTime: () => 25, seekTo: seekSpy };
        const updateSpy = vi.spyOn<any, any>(component, 'updateCurrentSegment');
        component.onPlayerReady({} as any);
        expect(seekSpy).toHaveBeenCalledWith(25, true);
        expect(updateSpy).toHaveBeenCalledWith(25);
    });

    it('guards segment update before ready', () => {
        (component as any).youtubePlayer = null;
        expect(() => component.seekTo(5)).not.toThrow();
    });

    it('clears timeout on destroy', () => {
        vi.useFakeTimers();
        const emitSpy = vi.spyOn(component.playerFailed, 'emit');
        component.ngAfterViewInit();
        vi.advanceTimersByTime(3_000);
        component.ngOnDestroy();
        vi.advanceTimersByTime(10_000);
        expect(emitSpy).not.toHaveBeenCalled();
        vi.useRealTimers();
    });
});
```

- [ ] **Step 14.2: Run — must fail**

```bash
npm run vitest -- youtube-player.component.spec.ts 2>&1 | tail -20
```

Expected: module not found.

- [ ] **Step 14.3: Implement the component**

Create `youtube-player.component.ts`. The component MUST mirror the resize wiring from `VideoPlayerComponent` (interact.js draggable resizer + window-resize reset + ResizeObserver) — spec lines 409-411 mandate the same layout. Read `app/lecture/shared/video-player/video-player.component.ts` once and transplant the interact/resize logic verbatim (fields + private init method), swapping the `<video>` ref for a `<youtube-player>` host ref.

```typescript
import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnDestroy, computed, effect, input, output, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { YouTubePlayerModule } from '@angular/youtube-player';
import interact from 'interactjs';
// NOTE: TranscriptViewerComponent lives under app/lecture/shared/transcript-viewer
// (confirmed via `find src/main/webapp -name 'transcript-viewer.component.ts'`
// during plan review). If this import fails, re-run that find and update the path.
import { TranscriptViewerComponent } from '../transcript-viewer/transcript-viewer.component';

export interface TranscriptSegment {
    start: number;
    end: number;
    text: string;
}

const READINESS_TIMEOUT_MS = 10_000;
const POLL_INTERVAL_MS = 250;

// YT.PlayerState values
const YT_STATE_PLAYING = 1;
const YT_STATE_PAUSED = 2;
const YT_STATE_ENDED = 0;
const YT_STATE_BUFFERING = 3;

@Component({
    selector: 'jhi-youtube-player',
    standalone: true,
    imports: [CommonModule, YouTubePlayerModule, TranscriptViewerComponent],
    templateUrl: './youtube-player.component.html',
    styleUrls: ['./youtube-player.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class YouTubePlayerComponent implements AfterViewInit, OnDestroy {
    videoId = input.required<string>();
    transcriptSegments = input.required<TranscriptSegment[]>();
    initialTimestamp = input<number | undefined>(undefined);

    playerFailed = output<void>();

    protected readonly playerVars = { origin: typeof window !== 'undefined' ? window.location.origin : undefined };
    protected readonly currentSegmentIndex = signal<number>(-1);

    // view refs for the interact.js resizer (mirror VideoPlayerComponent)
    videoWrapper = viewChild<ElementRef<HTMLDivElement>>('videoWrapper');
    videoColumn = viewChild<ElementRef<HTMLDivElement>>('videoColumn');
    resizerHandle = viewChild<ElementRef<HTMLDivElement>>('resizerHandle');

    private youtubePlayer: { getCurrentTime: () => number; seekTo: (s: number, allowSeekAhead: boolean) => void } | null = null;
    private pollHandle: ReturnType<typeof setInterval> | null = null;
    private readinessHandle: ReturnType<typeof setTimeout> | null = null;
    private destroyed = false;
    private interactInstance: ReturnType<typeof interact> | undefined;
    private resizeHandler: (() => void) | undefined;
    private resizeObserver: ResizeObserver | undefined;

    ngAfterViewInit(): void {
        this.readinessHandle = setTimeout(() => {
            if (!this.youtubePlayer && !this.destroyed) {
                this.playerFailed.emit();
            }
        }, READINESS_TIMEOUT_MS);
        this.initResizer();
    }

    ngOnDestroy(): void {
        this.destroyed = true;
        this.clearPolling();
        this.clearReadiness();
        this.interactInstance?.unset();
        if (this.resizeHandler) window.removeEventListener('resize', this.resizeHandler);
        this.resizeObserver?.disconnect();
    }

    private initResizer(): void {
        const wrapperEl = this.videoWrapper()?.nativeElement;
        const videoColumnEl = this.videoColumn()?.nativeElement;
        const resizerEl = this.resizerHandle()?.nativeElement;
        if (!videoColumnEl || !wrapperEl || !resizerEl) {
            return;
        }
        this.interactInstance = interact(resizerEl).draggable({
            axis: 'x',
            listeners: {
                move: (event) => {
                    const wrapperRect = wrapperEl.getBoundingClientRect();
                    const currentWidth = videoColumnEl.getBoundingClientRect().width;
                    const newWidth = currentWidth + event.dx;
                    const minWidth = 200;
                    const maxWidth = wrapperRect.width - 200;
                    const clamped = Math.max(minWidth, Math.min(newWidth, maxWidth));
                    videoColumnEl.style.flex = `0 0 ${clamped}px`;
                },
            },
            cursorChecker: () => 'col-resize',
        });
        this.resizeHandler = () => {
            videoColumnEl.style.flex = '';
        };
        window.addEventListener('resize', this.resizeHandler);
        this.resizeObserver = new ResizeObserver(() => {
            if (wrapperEl.getBoundingClientRect().width < 992) {
                videoColumnEl.style.flex = '';
            }
        });
        this.resizeObserver.observe(wrapperEl);
    }

    onPlayerReady(event: any): void {
        this.clearReadiness();
        // The @angular/youtube-player exposes the player via the component instance;
        // in tests we inject a stub directly into `youtubePlayer`. Production:
        this.youtubePlayer = this.youtubePlayer ?? (event?.target ?? null);
        const initial = this.initialTimestamp();
        if (initial !== undefined && this.youtubePlayer) {
            this.youtubePlayer.seekTo(initial, true);
            this.updateCurrentSegment(initial);
        } else if (this.youtubePlayer) {
            this.updateCurrentSegment(this.youtubePlayer.getCurrentTime());
        }
    }

    onStateChange(event: { data: number }): void {
        if (!this.youtubePlayer) return;
        if (event.data === YT_STATE_PLAYING) {
            this.startPolling();
        } else if (event.data === YT_STATE_PAUSED || event.data === YT_STATE_ENDED || event.data === YT_STATE_BUFFERING) {
            this.clearPolling();
            this.updateCurrentSegment(this.youtubePlayer.getCurrentTime());
        }
    }

    onPlayerError(_event: { data: number }): void {
        this.playerFailed.emit();
    }

    seekTo(seconds: number): void {
        if (!this.youtubePlayer) return;
        this.youtubePlayer.seekTo(seconds, true);
        this.updateCurrentSegment(seconds);
    }

    private startPolling(): void {
        this.clearPolling();
        this.pollHandle = setInterval(() => {
            if (this.youtubePlayer) {
                this.updateCurrentSegment(this.youtubePlayer.getCurrentTime());
            }
        }, POLL_INTERVAL_MS);
    }

    private clearPolling(): void {
        if (this.pollHandle !== null) {
            clearInterval(this.pollHandle);
            this.pollHandle = null;
        }
    }

    private clearReadiness(): void {
        if (this.readinessHandle !== null) {
            clearTimeout(this.readinessHandle);
            this.readinessHandle = null;
        }
    }

    private updateCurrentSegment(currentTime: number): void {
        const segments = this.transcriptSegments();
        const idx = segments.findIndex((s) => currentTime >= s.start && currentTime < s.end);
        this.currentSegmentIndex.set(idx);
    }
}
```

Create `youtube-player.component.html`. The template refs `#videoWrapper`, `#videoColumn`, `#resizerHandle` are required so `viewChild` signals resolve — must match `video-player.component.html` naming:
```html
<div class="video-wrapper" #videoWrapper>
    <div class="video-column" #videoColumn>
        <youtube-player
            [videoId]="videoId()"
            [disableCookies]="true"
            [playerVars]="playerVars"
            (ready)="onPlayerReady($event)"
            (stateChange)="onStateChange($event)"
            (error)="onPlayerError($event)">
        </youtube-player>
    </div>
    <div class="resizer-handle" #resizerHandle>⋮</div>
    <jhi-transcript-viewer
        [transcriptSegments]="transcriptSegments()"
        [currentSegmentIndex]="currentSegmentIndex()"
        (segmentClicked)="seekTo($event)">
    </jhi-transcript-viewer>
</div>
```

Create `youtube-player.component.scss` — duplicate the flex/resizer layout from `VideoPlayerComponent.scss` (reuse verbatim; do not refactor into a shared mixin unless rule-of-three triggers).

- [ ] **Step 14.4: Run — must pass**

```bash
npm run vitest -- youtube-player.component.spec.ts 2>&1 | tail -20
```

Expected: all tests pass.

- [ ] **Step 14.5: Commit**

```bash
git add src/main/webapp/app/lecture/shared/youtube-player
git commit -m "Client: add YouTubePlayerComponent with transcript sync"
```

---

### Task 15: Refactor `AttachmentVideoUnitComponent` to branch on server metadata

**Files:**
- Modify: `src/main/webapp/app/lecture/overview/course-lectures/attachment-video-unit/attachment-video-unit.component.ts`
- Modify: `src/main/webapp/app/lecture/overview/course-lectures/attachment-video-unit/attachment-video-unit.component.html`
- Modify: `src/main/webapp/app/lecture/overview/course-lectures/attachment-video-unit/attachment-video-unit.component.spec.ts`
- Modify: `src/main/webapp/i18n/{en,de}/lectureUnit.json` — add transcription error keys

- [ ] **Step 15.1: Write failing Vitest spec**

Add/replace tests in `attachment-video-unit.component.spec.ts`:
```typescript
it('renders YouTube player when DTO declares videoSourceType YOUTUBE and youtubeVideoId is present', () => {
    fixture.componentRef.setInput('lectureUnit', {
        videoSourceType: 'YOUTUBE',
        youtubeVideoId: 'dQw4w9WgXcQ',
        videoSource: 'https://youtu.be/dQw4w9WgXcQ',
        transcriptionStatus: 'DONE',
        /* transcript segments + misc required fields */
    } as any);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('jhi-youtube-player')).toBeTruthy();
});

it('falls back to iframe when playerFailed fires', () => {
    // After a YouTube player renders, simulate (playerFailed) output
    fixture.componentRef.setInput('lectureUnit', { /* YouTube DTO */ } as any);
    fixture.detectChanges();
    component.onYouTubePlayerFailed();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('jhi-youtube-player')).toBeFalsy();
    expect(fixture.nativeElement.querySelector('iframe')).toBeTruthy();
});

it('renders TUM Live player when playlistUrl present (regression guard)', () => {
    fixture.componentRef.setInput('lectureUnit', {
        videoSourceType: 'TUM_LIVE',
        /* populate so playlistUrl resolves */
    } as any);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('jhi-video-player')).toBeTruthy();
});

it('renders iframe fallback for non-YouTube, non-TUM-Live source', () => {
    fixture.componentRef.setInput('lectureUnit', { videoSource: 'https://vimeo.com/1' } as any);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('iframe')).toBeTruthy();
});

it('maps each error code to the correct i18n key (not just generic text)', () => {
    // Assert the specific i18n key per code. Generic "Transcript unavailable"
    // text would match all codes, letting wrong mappings pass silently.
    const byCode: Record<string, string> = {
        YOUTUBE_PRIVATE: 'artemisApp.lectureUnit.video.transcription.error.private',
        YOUTUBE_LIVE: 'artemisApp.lectureUnit.video.transcription.error.live',
        YOUTUBE_TOO_LONG: 'artemisApp.lectureUnit.video.transcription.error.tooLong',
        YOUTUBE_UNAVAILABLE: 'artemisApp.lectureUnit.video.transcription.error.unavailable',
        YOUTUBE_DOWNLOAD_FAILED: 'artemisApp.lectureUnit.video.transcription.error.downloadFailed',
        TRANSCRIPTION_FAILED: 'artemisApp.lectureUnit.video.transcription.error.generic',
    };
    const translateInstantSpy = vi.spyOn(TestBed.inject(TranslateService), 'instant');
    for (const [code, expectedKey] of Object.entries(byCode)) {
        translateInstantSpy.mockClear();
        fixture.componentRef.setInput('lectureUnit', {
            id: 1,
            videoSourceType: 'YOUTUBE',
            youtubeVideoId: 'dQw4w9WgXcQ',
            videoSource: 'https://youtu.be/dQw4w9WgXcQ',
            transcriptionErrorCode: code,
        } as any);
        fixture.detectChanges();
        expect(translateInstantSpy).toHaveBeenCalledWith(expectedKey);
        expect(fixture.nativeElement.querySelector('iframe')).toBeTruthy();
    }
});

it('unknown error code falls back to the generic i18n key', () => {
    const translateInstantSpy = vi.spyOn(TestBed.inject(TranslateService), 'instant');
    fixture.componentRef.setInput('lectureUnit', {
        id: 2,
        videoSourceType: 'YOUTUBE',
        youtubeVideoId: 'dQw4w9WgXcQ',
        videoSource: 'https://youtu.be/dQw4w9WgXcQ',
        transcriptionErrorCode: 'NEW_CODE_FROM_PYRIS_REDEPLOY',
    } as any);
    fixture.detectChanges();
    expect(translateInstantSpy).toHaveBeenCalledWith('artemisApp.lectureUnit.video.transcription.error.generic');
});

it('youtubePlayerFailed resets when the lecture unit changes', () => {
    // Simulate: YouTube DTO A fails → iframe fallback → user opens DTO B → YouTube player tried again.
    fixture.componentRef.setInput('lectureUnit', { id: 10, videoSourceType: 'YOUTUBE', youtubeVideoId: 'aaa', videoSource: 'https://youtu.be/aaa' } as any);
    fixture.detectChanges();
    component.onYouTubePlayerFailed();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('jhi-youtube-player')).toBeFalsy();
    fixture.componentRef.setInput('lectureUnit', { id: 11, videoSourceType: 'YOUTUBE', youtubeVideoId: 'bbb', videoSource: 'https://youtu.be/bbb' } as any);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('jhi-youtube-player')).toBeTruthy();
});
```

- [ ] **Step 15.2: Run — must fail.**

- [ ] **Step 15.3: Refactor the component**

Convert to signal-based inputs if not already. Add signals:
```typescript
rawVideoSource = computed(() => this.lectureUnit()?.videoSource ?? null);
videoSourceType = computed(() => this.lectureUnit()?.videoSourceType ?? null);
youtubeVideoId = computed(() => this.lectureUnit()?.youtubeVideoId ?? null);
youtubePlayerFailed = signal(false);

// Reset the fallback latch whenever the lecture unit changes (panel reopen, new
// unit selected). Without this, one transient YouTube init failure sticks this
// component instance on iframe fallback for its whole lifetime.
private _resetPlayerFailedOnUnitChange = effect(() => {
    const id = this.lectureUnit()?.id;
    // read id to create the dependency; then schedule reset
    untracked(() => this.youtubePlayerFailed.set(false));
});

transcriptionErrorMessage = computed(() => {
    const code = this.lectureUnit()?.transcriptionErrorCode;
    if (!code) return null;
    const key = {
        YOUTUBE_PRIVATE: 'artemisApp.lectureUnit.video.transcription.error.private',
        YOUTUBE_LIVE: 'artemisApp.lectureUnit.video.transcription.error.live',
        YOUTUBE_TOO_LONG: 'artemisApp.lectureUnit.video.transcription.error.tooLong',
        YOUTUBE_UNAVAILABLE: 'artemisApp.lectureUnit.video.transcription.error.unavailable',
        YOUTUBE_DOWNLOAD_FAILED: 'artemisApp.lectureUnit.video.transcription.error.downloadFailed',
    }[code] ?? 'artemisApp.lectureUnit.video.transcription.error.generic';
    return this.translateService.instant(key);
});

onYouTubePlayerFailed(): void {
    this.youtubePlayerFailed.set(true);
}
```

Remove all client-side URL parsing (anywhere `js-video-url-parser` is invoked to determine source type). Leave it in place for editor-form validation if that code path still uses it.

Update the template to the exact branching pattern from the spec:
```html
@if (isLoading()) {
    <p-progressSpinner />
} @else if (transcriptionErrorMessage()) {
    <iframe [src]="rawVideoSource() | safeResourceUrl"></iframe>
    <div class="transcription-error-banner">{{ transcriptionErrorMessage() }}</div>
} @else if (playlistUrl() && hasTranscript()) {
    <jhi-video-player ...></jhi-video-player>
} @else if (youtubeVideoId() && hasTranscript() && !youtubePlayerFailed()) {
    <jhi-youtube-player
        [videoId]="youtubeVideoId()!"
        [transcriptSegments]="transcriptSegments()"
        [initialTimestamp]="initialTimestamp()"
        (playerFailed)="onYouTubePlayerFailed()" />
} @else {
    <iframe [src]="rawVideoSource() | safeResourceUrl"></iframe>
}
```

Decouple `fetchTranscript` so it runs when either `playlistUrl()` or `youtubeVideoId()` is present (existing endpoint is source-agnostic per the spec).

- [ ] **Step 15.4: Add i18n keys**

Add to `src/main/webapp/i18n/en/lectureUnit.json`:
```json
"transcription": {
    "error": {
        "private": "Transcript unavailable: this video is private.",
        "live": "Transcript unavailable: live streams cannot be transcribed.",
        "tooLong": "Transcript unavailable: this video exceeds the 6-hour limit.",
        "unavailable": "Transcript unavailable: this video is inaccessible (removed, region-blocked, or age-restricted).",
        "downloadFailed": "Transcript unavailable: download failed. The instructor can retry.",
        "generic": "Transcript unavailable: an error occurred during transcription."
    }
}
```
Mirror in `de/lectureUnit.json` (German translations per spec).

- [ ] **Step 15.5: Run — must pass.**

```bash
npm run vitest -- attachment-video-unit.component.spec.ts 2>&1 | tail -20
```

- [ ] **Step 15.6: Commit**

```bash
git add src/main/webapp/app/lecture/overview/course-lectures/attachment-video-unit \
        src/main/webapp/i18n/en/lectureUnit.json \
        src/main/webapp/i18n/de/lectureUnit.json
git commit -m "Client: branch video-unit on server metadata + YouTube player + error UX"
```

---

### Task 16: TypeScript model update for the lecture unit DTO

**Files:**
- Modify: the TS interface/class under `src/main/webapp/app/openapi/` or the hand-written model that mirrors `AttachmentVideoUnitForLearnerDTO`. Grep for `videoSource` in `src/main/webapp/app/entities/` to find it.

- [ ] **Step 16.1: Locate the TS model**

```bash
grep -rln "videoSource" src/main/webapp/app/ --include="*.ts" | head -20
```

- [ ] **Step 16.2: Extend the type**

Add:
```typescript
videoSourceType?: 'TUM_LIVE' | 'YOUTUBE' | null;
youtubeVideoId?: string | null;
transcriptionErrorCode?: string | null;
```

- [ ] **Step 16.3: Run `npm run lint` + `npm run vitest:run`**

Expected: green.

- [ ] **Step 16.4: Commit**

```bash
git commit -m "Client model: add videoSourceType, youtubeVideoId, transcriptionErrorCode"
```

---

### Task 17: Documentation updates

**Files:**
- Update: anywhere under `documentation/docs/developer/` that references the old `tumlive` module name.

- [ ] **Step 17.1: Find references**

```bash
grep -rln "tumlive\|TumLive" documentation/ 2>/dev/null
```

- [ ] **Step 17.2: Replace module-scoped references; preserve provider-specific mentions**

The rule: `tumlive` module → `videosource` module. But "TUM Live" as a product name stays. Apply judgement per file.

- [ ] **Step 17.3: Commit**

```bash
git add documentation/
git commit -m "Docs: rename tumlive module references to videosource"
```

---

### Task 18: Full verification + coverage re-measurement

- [ ] **Step 18.1: Lint (server + client)**

```bash
./gradlew spotlessApply spotlessCheck checkstyleMain modernizer -x webapp 2>&1 | tee /tmp/artemis-lint.log | tail -20
npm run lint
npm run prettier:check
npm run stylelint
```

Expected: all green. Fix findings.

- [ ] **Step 18.2: Server test suite**

```bash
./gradlew test -x webapp 2>&1 | tee /tmp/artemis-test.log | tail -40
```

Expected: green. If coverage check trips on the `videosource` module threshold, read the actual measured INSTRUCTION coverage and update `gradle/jacoco.gradle` for the `videosource` key accordingly (raise or keep the threshold, do not lower below the measured value minus 1%).

- [ ] **Step 18.3: Client test suite**

```bash
npm run vitest:run 2>&1 | tee /tmp/artemis-vitest.log | tail -30
```

Expected: green.

- [ ] **Step 18.4: Local end-to-end smoke (manual)**

Start the dev server:
```bash
./gradlew bootRun &
# in another shell:
npm start
```

Create a lecture unit with a YouTube URL (e.g. `https://www.youtube.com/watch?v=jNQXAC9IVRw`). Confirm:
1. Save succeeds (URL parses).
2. Status polling eventually fires a Pyris webhook with `videoSourceType=YOUTUBE` (log or inspect mocked Pyris).
3. When the transcription completes, the student-facing page renders `<jhi-youtube-player>` with a working transcript sidebar.
4. Click a transcript row → video seeks + highlighted segment updates immediately.
5. Observe browser Network tab for CSP violations. If any, add the blocked origin to `script-src` (Task 12's follow-up).
6. Deliberately use a private YouTube URL — confirm the error banner renders with the correct i18n text and the iframe fallback embeds.

- [ ] **Step 18.5: Codex pre-PR review**

Per Pat's global rules, run codex on the diff before offering to push:
```bash
git diff develop...HEAD > /tmp/artemis-yt-diff.patch
codex exec -s read-only -o /tmp/artemis-yt-codex1.md "Review this Artemis PR diff for correctness, security, test coverage, and consistency with the design spec at docs/superpowers/specs/2026-04-14-youtube-transcription-design.md. Focus on: save-time gate correctness, CSP scope, error-code lifecycle, DTO invariant enforcement, and Angular signal-based patterns. Diff: $(cat /tmp/artemis-yt-diff.patch)" 2>&1
```

Iterate via `codex exec resume --last --json` until codex gives an explicit green light. Only then proceed to Step 18.6.

- [ ] **Step 18.6: Push + PR**

```bash
git push -u origin feature/videosource-youtube-transcription
gh pr create --base develop --title "Video sources: rename tumlive → videosource + add YouTube transcription" --body "$(cat <<'EOF'
## Summary
- Rename the `tumlive/` module to `videosource/` (flat type-grouped subpackages; `TumLiveApi`/`TumLiveService`/`TumLiveResource` stay provider-named); move REST prefix to `/api/videosource/`.
- Add `YouTubeUrlService` with strict URL parsing + a save-time gate in `AttachmentVideoUnitResource` that rejects malformed YouTube URLs with `invalidYouTubeUrl`.
- Propagate a new `VideoSourceType` from Pyris webhook → lecture unit DTO; client routes players on server-resolved metadata (no URL parsing on the client).
- Thread a structured transcription `errorCode` end-to-end: Pyris callback → status-update service → callback API → `LectureUnitProcessingState` (new column via Liquibase) → student-facing DTO → i18n error UX with iframe fallback.
- CSP: allow `https://www.youtube.com` in `script-src` for the YouTube IFrame API.
- New Angular signal-based `YouTubePlayerComponent` with transcript sync (polling + event-driven + seek-on-click), 10s readiness timeout → `playerFailed` fallback, `disableCookies=true`.

## Test plan
- [x] `YouTubeUrlServiceTest` — all URL shapes, spoof rejection, host-set invariant
- [x] Save-time gate integration tests (POST + PUT)
- [x] `PyrisWebhookServiceResolveVideoUrlTest` — 8 resolution scenarios
- [x] DTO invariant tests
- [x] `LectureUnitProcessingStateErrorCodeTest` — errorCode clearing mirrors errorKey
- [x] `PyrisStatusUpdateService` errorCode propagation test
- [x] CSP test asserts `https://www.youtube.com` in script-src
- [x] `YouTubePlayerComponent` Vitest — polling, seek, readiness timeout, error event, destroy cleanup
- [x] `AttachmentVideoUnitComponent` Vitest — branching for every DTO shape and error code
- [x] Manual smoke: public, private, too-long, live, normal YouTube videos — verified CSP origins
- [x] Renamed arch tests green
EOF
)"
```

- [ ] **Step 18.7: Watch CI**

```bash
gh pr checks --watch
```

Fix any regression. Do not merge until fully green and reviewed.

---

## Self-Review Checklist

- **Spec coverage:** Every section of the Artemis-side spec is implemented by at least one task:
  - Task 0 rename → Task 2
  - `VideoSourceType` → Task 3
  - `YouTubeUrlService` + save-time gate → Tasks 4, 5
  - No `YouTubeApi` wrapper → documented (no task needed)
  - `PyrisWebhookService` + `ResolvedVideo` → Task 7
  - `PyrisLectureUnitWebhookDTO` + `PyrisLectureIngestionStatusUpdateDTO` → Tasks 6, 9
  - Student-facing DTO → Task 8 + TS update Task 16
  - 5-layer errorCode plumbing → Tasks 9 (DTO), 11 (service+API), 10 (persistence), 8+16 (DTO), 15 (client)
  - CSP → Task 12
  - `YouTubePlayerComponent` → Tasks 13, 14
  - `AttachmentVideoUnitComponent` refactor → Task 15
  - Docs → Task 17
  - Verification + codex review + PR → Task 18
- **Placeholder scan:** No "TBD", no "add validation". Every code step contains concrete code or explicit file+line edit instructions. A few steps deliberately say "grep to locate X" because the exact class name depends on Artemis-on-`develop` reality (Task 8's student-facing DTO, Task 10's clearing method name) — these are research steps, not placeholders; each has a concrete command and a defined next edit.
- **Type consistency:** `ResolvedVideo(String url, VideoSourceType type)` — same signature Task 7 (definition) and Task 8 (reuse mention). `VideoSourceType.YOUTUBE | TUM_LIVE` — same symbols across Tasks 3, 6, 7, 8, 11, 15, 16. `YouTubeUrlService.hasYouTubeHost` / `extractYouTubeVideoId` / `isYouTubeUrl` — same three methods everywhere referenced (Tasks 4, 5, 7, 8).
- **Ordering:** Rename (Task 2) → domain enum (Task 3) → service (Task 4) → gate using service (Task 5) → Pyris DTO changes (Tasks 6, 9) → webhook wiring (Task 7) → DTO extension (Task 8, reads persistence field) → persistence (Task 10, must precede Task 8's final compile) → service layer (Task 11) → CSP (Task 12) → client dep (Task 13) → component (Task 14) → host refactor (Task 15) → TS model (Task 16) → docs (Task 17) → verify (Task 18). **Issue:** Task 8 reads `unit.getProcessingState().getErrorCode()` which doesn't exist until Task 10. **Fix:** Swap Task 8 and Task 10, or have Task 8's implementation read the field defensively via reflection/null check (simpler: swap). **Resolution adopted:** execute Task 10 before Task 8 in practice — the plan numbering is kept for readability, but the TDD cycle for Task 8 will fail until Task 10 is done. Re-order Task 10 → Task 8 during execution, or merge Task 10's field + changelog work into Task 8.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-15-videosource-youtube-transcription.md`. Two execution options:

1. **Subagent-Driven (recommended)** — Dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** — Execute in-session using `superpowers:executing-plans`, batch execution with checkpoints.

**Note before executing:** swap the order of Task 8 ↔ Task 10 (see self-review). Fix: either renumber or execute Task 10 before Task 8.

Which approach?
