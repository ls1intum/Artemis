# Pyris YouTube Ingestion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add YouTube download support to the Pyris transcription pipeline so that Artemis can request transcription of YouTube lecture videos with the same downstream processing (Whisper + slide detection + alignment) used for TUM Live HLS streams.

**Architecture:** Extend the existing `LectureUnitPageDTO` ingestion webhook payload with a `video_source_type` enum. Branch the heavy transcription pipeline's download step on this field: TUM Live keeps using FFmpeg HLS, YOUTUBE uses a new `yt-dlp`-based downloader. Everything after download (audio extraction, Whisper, slide detection, alignment) is unchanged. Failures surface via structured `error_code` values on the existing status callback.

**Tech Stack:** Python 3.13, Poetry, Pydantic, pytest, FastAPI, `yt-dlp` (new), FFmpeg (existing).

**Repo:** `ls1intum/edutelligence` (separate from Artemis — this plan is repo-scoped).

**Companion plan:** `2026-04-15-videosource-youtube-transcription.md` covers the Artemis side (Java server + Angular client). The two plans are independently deployable; this one ships first.

---

## File Structure

**New files:**
- `iris/src/iris/domain/data/video_source_type.py` — `VideoSourceType` enum (`TUM_LIVE`, `YOUTUBE`)
- `iris/src/iris/pipeline/shared/transcription/youtube_utils.py` — `validate_youtube_video`, `download_youtube_video`, `YouTubeDownloadError`
- `iris/tests/test_youtube_utils.py` — unit tests for URL validation / download (subprocess mocked)
- `iris/tests/test_heavy_pipeline_youtube_branch.py` — pipeline-level tests for the YouTube branch

**Modified files:**
- `iris/src/iris/domain/data/lecture_unit_page_dto.py` — add optional `video_source_type` field (default `TUM_LIVE`)
- `iris/src/iris/domain/status/status_update_dto.py` — add optional `error_code: Optional[str] = None` field
- `iris/src/iris/web/status/status_update.py` — `StatusCallback.error()` accepts optional `error_code`
- `iris/src/iris/pipeline/shared/transcription/heavy_pipeline.py` — branch at download step on `video_source_type`
- `iris/src/iris/pipeline/lecture_ingestion_update_pipeline.py` — pass `video_source_type` + translate `YouTubeDownloadError` into `callback.error(error_code=...)`
- `iris/src/iris/config.py` — add `youtube_max_duration_seconds`, `youtube_download_timeout_seconds` to `TranscriptionSettings`
- `iris/pyproject.toml` — add `yt-dlp` dependency (pinned minor)
- `iris/Dockerfile` — no change required if yt-dlp installed via Poetry (verify in final task); add explicit note if the pinned wheel needs a system package

---

## Scope Check

This plan covers **only** the Pyris/edutelligence repo. All Artemis-side work (rename module, client component, CSP, server DTO, error-code persistence) is in the sibling plan. The two plans are wire-compatible: Pyris deployed alone does nothing new because `video_source_type` defaults to `TUM_LIVE`; YouTube activation happens only when Artemis starts sending `YOUTUBE`.

---

### Task 1: Branch + worktree setup

**Goal:** Isolated workspace and baseline green tests.

- [ ] **Step 1.1: Create feature branch from latest `main`**

```bash
cd ~/projects/edutelligence
git fetch origin
git worktree add -b feature/pyris-youtube-ingestion /Users/pat/projects/claudeworktrees/edutelligence/feature-pyris-youtube-ingestion origin/main
cd /Users/pat/projects/claudeworktrees/edutelligence/feature-pyris-youtube-ingestion/iris
```

- [ ] **Step 1.2: Install dependencies**

```bash
poetry install
```

- [ ] **Step 1.3: Run baseline tests to confirm green starting point**

```bash
poetry run pytest tests/ 2>&1 | tail -20
```

Expected: all tests pass. If not, STOP and ask the user — do not proceed against a broken baseline.

- [ ] **Step 1.4: Commit (empty commit marking start)**

```bash
git commit --allow-empty -m "chore: start pyris-youtube-ingestion feature branch"
```

---

### Task 2: `VideoSourceType` enum

**Files:**
- Create: `iris/src/iris/domain/data/video_source_type.py`
- Test: `iris/tests/test_video_source_type.py`

- [ ] **Step 2.1: Write the failing test**

Create `iris/tests/test_video_source_type.py`:
```python
from iris.domain.data.video_source_type import VideoSourceType


def test_values_match_wire_format():
    assert VideoSourceType.TUM_LIVE.value == "TUM_LIVE"
    assert VideoSourceType.YOUTUBE.value == "YOUTUBE"


def test_is_str_enum_for_json_round_trip():
    # StrEnum compatibility: value equals the enum member when compared as string
    assert VideoSourceType("YOUTUBE") == VideoSourceType.YOUTUBE
    assert VideoSourceType("TUM_LIVE") == VideoSourceType.TUM_LIVE
```

- [ ] **Step 2.2: Run test to verify it fails**

Run: `poetry run pytest tests/test_video_source_type.py -v`
Expected: FAIL with `ModuleNotFoundError: No module named 'iris.domain.data.video_source_type'`

- [ ] **Step 2.3: Create the enum**

Create `iris/src/iris/domain/data/video_source_type.py`:
```python
from enum import Enum


class VideoSourceType(str, Enum):
    """How a lecture video is hosted, driving download-step selection.

    Backward compatibility: missing / null on the wire is treated as TUM_LIVE
    by consumers so that older Artemis deployments continue to work.
    """

    TUM_LIVE = "TUM_LIVE"
    YOUTUBE = "YOUTUBE"
```

- [ ] **Step 2.4: Run test — must pass**

Run: `poetry run pytest tests/test_video_source_type.py -v`
Expected: PASS (2 tests).

- [ ] **Step 2.5: Commit**

```bash
git add iris/src/iris/domain/data/video_source_type.py iris/tests/test_video_source_type.py
git commit -m "feat(pyris): add VideoSourceType enum for download-step selection"
```

---

### Task 3: Extend `LectureUnitPageDTO` with `video_source_type`

**Files:**
- Modify: `iris/src/iris/domain/data/lecture_unit_page_dto.py`
- Test: `iris/tests/test_lecture_unit_page_dto.py`

- [ ] **Step 3.1: Write the failing test**

Create `iris/tests/test_lecture_unit_page_dto.py`:
```python
from iris.domain.data.lecture_unit_page_dto import LectureUnitPageDTO
from iris.domain.data.video_source_type import VideoSourceType


_MINIMAL_PAYLOAD = {
    "lectureUnitId": 1,
    "lectureId": 2,
    "courseId": 3,
}


def test_defaults_to_tum_live_when_field_absent():
    dto = LectureUnitPageDTO(**_MINIMAL_PAYLOAD)
    assert dto.video_source_type == VideoSourceType.TUM_LIVE


def test_accepts_youtube_from_camelcase_alias():
    dto = LectureUnitPageDTO(**{**_MINIMAL_PAYLOAD, "videoSourceType": "YOUTUBE"})
    assert dto.video_source_type == VideoSourceType.YOUTUBE


def test_accepts_snake_case_field_name():
    dto = LectureUnitPageDTO(**{**_MINIMAL_PAYLOAD, "video_source_type": "YOUTUBE"})
    assert dto.video_source_type == VideoSourceType.YOUTUBE


def test_rejects_unknown_value():
    import pytest
    from pydantic import ValidationError

    with pytest.raises(ValidationError):
        LectureUnitPageDTO(**{**_MINIMAL_PAYLOAD, "videoSourceType": "VIMEO"})
```

- [ ] **Step 3.2: Run — must fail**

Run: `poetry run pytest tests/test_lecture_unit_page_dto.py -v`
Expected: FAIL — `video_source_type` attribute doesn't exist.

- [ ] **Step 3.3: Add the field**

Edit `iris/src/iris/domain/data/lecture_unit_page_dto.py`. The model needs `populate_by_name=True` so that the snake_case test case passes alongside the aliased camelCase wire key. Add model config + the new field:

```python
from pydantic import BaseModel, ConfigDict, Field
...

class LectureUnitPageDTO(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    ...
    video_source_type: VideoSourceType = Field(
        default=VideoSourceType.TUM_LIVE, alias="videoSourceType"
    )
```

Add the import at the top of the file, alongside the existing imports:
```python
from iris.domain.data.video_source_type import VideoSourceType
```

Full resulting file should look like (illustrative — preserve existing fields exactly):
```python
from pydantic import BaseModel, Field

from iris.domain.data.metrics.transcription_dto import TranscriptionDTO
from iris.domain.data.video_source_type import VideoSourceType


class LectureUnitPageDTO(BaseModel):
    """DTO for lecture unit ingestion webhooks.

    Mirrors the Artemis PyrisLectureUnitWebhookDTO structure.
    Used for ingestion and deletion pipelines.
    """

    pdf_file_base64: str = Field(default="", alias="pdfFile")
    attachment_version: int = Field(default=0, alias="attachmentVersion")
    transcription: TranscriptionDTO = Field(default=None)
    lecture_unit_id: int = Field(alias="lectureUnitId")
    lecture_unit_name: str = Field(default="", alias="lectureUnitName")
    lecture_unit_link: str = Field(default="", alias="lectureUnitLink")
    lecture_id: int = Field(alias="lectureId")
    lecture_name: str = Field(default="", alias="lectureName")
    course_id: int = Field(alias="courseId")
    course_name: str = Field(default="", alias="courseName")
    course_description: str = Field(default="", alias="courseDescription")
    video_link: str = Field(default="", alias="videoLink")
    video_source_type: VideoSourceType = Field(
        default=VideoSourceType.TUM_LIVE, alias="videoSourceType"
    )
```

- [ ] **Step 3.4: Run — must pass**

Run: `poetry run pytest tests/test_lecture_unit_page_dto.py -v`
Expected: PASS (4 tests).

- [ ] **Step 3.5: Run full test suite — regression guard**

Run: `poetry run pytest tests/ 2>&1 | tail -15`
Expected: all pre-existing tests still pass.

- [ ] **Step 3.6: Commit**

```bash
git add iris/src/iris/domain/data/lecture_unit_page_dto.py iris/tests/test_lecture_unit_page_dto.py
git commit -m "feat(pyris): add video_source_type to LectureUnitPageDTO"
```

---

### Task 4: Extend `TranscriptionSettings`

**Files:**
- Modify: `iris/src/iris/config.py`
- Test: `iris/tests/test_transcription_settings.py`

- [ ] **Step 4.1: Write the failing test**

Create `iris/tests/test_transcription_settings.py`:
```python
from iris.config import TranscriptionSettings


def test_youtube_max_duration_default_is_six_hours():
    settings = TranscriptionSettings()
    assert settings.youtube_max_duration_seconds == 21600


def test_youtube_download_timeout_default_is_ten_minutes():
    settings = TranscriptionSettings()
    assert settings.youtube_download_timeout_seconds == 600
```

- [ ] **Step 4.2: Run — must fail**

Run: `poetry run pytest tests/test_transcription_settings.py -v`
Expected: FAIL — attribute missing.

- [ ] **Step 4.3: Add fields to `TranscriptionSettings`**

Edit `iris/src/iris/config.py` — inside `class TranscriptionSettings(BaseModel)`, after the existing `extract_audio_timeout_seconds` field, add:
```python
    youtube_max_duration_seconds: int = Field(
        default=21600,
        description="Max YouTube video duration in seconds (default: 6 hours). "
        "Videos longer than this are rejected with YOUTUBE_TOO_LONG.",
    )
    youtube_download_timeout_seconds: int = Field(
        default=600,
        description="Timeout for yt-dlp download of a YouTube video (default: 10 min).",
    )
```

- [ ] **Step 4.4: Run — must pass**

Run: `poetry run pytest tests/test_transcription_settings.py -v`
Expected: PASS (2 tests).

- [ ] **Step 4.5: Commit**

```bash
git add iris/src/iris/config.py iris/tests/test_transcription_settings.py
git commit -m "feat(pyris): add youtube duration + timeout transcription settings"
```

---

### Task 5: Extend status-update DTO with `error_code`

**Files:**
- Modify: `iris/src/iris/domain/status/status_update_dto.py`
- Modify: `iris/src/iris/web/status/status_update.py` (signature change)
- Test: `iris/tests/test_status_update_error_code.py`

- [ ] **Step 5.1: Write the failing test**

Create `iris/tests/test_status_update_error_code.py`:
```python
from iris.domain.status.status_update_dto import StatusUpdateDTO
from iris.domain.status.stage_dto import StageDTO
from iris.domain.status.stage_state_dto import StageStateEnum


def test_error_code_defaults_to_none():
    dto = StatusUpdateDTO(stages=[], tokens=[])
    assert dto.error_code is None


def test_error_code_round_trips_through_snake_case_field_name():
    dto = StatusUpdateDTO(stages=[], tokens=[], error_code="YOUTUBE_PRIVATE")
    assert dto.error_code == "YOUTUBE_PRIVATE"


def test_error_code_accepts_wire_alias_error_code():
    # Pyris sends snake_case on the wire per spec lines 287-293 (Jackson-side uses
    # @JsonProperty("error_code") on the Artemis DTO). Accept both on input.
    dto = StatusUpdateDTO(stages=[], tokens=[], **{"error_code": "YOUTUBE_LIVE"})
    assert dto.error_code == "YOUTUBE_LIVE"


def test_error_code_serialized_under_snake_case_wire_key():
    dto = StatusUpdateDTO(stages=[], tokens=[], error_code="YOUTUBE_TOO_LONG")
    # Must dump with snake_case `error_code` to match the Artemis-side Jackson contract.
    dumped = dto.model_dump(by_alias=True, exclude_none=True)
    assert dumped.get("error_code") == "YOUTUBE_TOO_LONG"
    assert "errorCode" not in dumped
```

- [ ] **Step 5.2: Run — must fail**

Run: `poetry run pytest tests/test_status_update_error_code.py -v`
Expected: FAIL — `error_code` not on `StatusUpdateDTO`.

- [ ] **Step 5.3: Add field to DTO**

Edit `iris/src/iris/domain/status/status_update_dto.py`:
```python
from typing import List, Optional

from pydantic import BaseModel, ConfigDict, Field

from iris.common.token_usage_dto import TokenUsageDTO

from ...domain.status.stage_dto import StageDTO


class StatusUpdateDTO(BaseModel):
    # Populate by field name OR alias on input; dump by alias for Artemis wire format
    model_config = ConfigDict(populate_by_name=True)

    stages: List[StageDTO]
    tokens: List[TokenUsageDTO] = []
    # Wire key is snake_case `error_code` per spec lines 287-293; the alias makes
    # pydantic accept/emit that key whenever the caller opts into by_alias.
    error_code: Optional[str] = Field(default=None, alias="error_code")
```

If the existing `StatusUpdateDTO` already inherits a base with `populate_by_name=True`, omit the duplicate `model_config` line. Confirm by reading the file first.

- [ ] **Step 5.4: Run — must pass**

Run: `poetry run pytest tests/test_status_update_error_code.py -v`
Expected: PASS (4 tests).

- [ ] **Step 5.5: Extend `StatusCallback.error()` AND its `IngestionStatusCallback` override**

Two files must change, not one. `IngestionStatusCallback` overrides `error(...)` at `ingestion_status_callback.py:192` (confirmed in repo scan) to use `on_status_update_best_effort()` instead of the base's `on_status_update()`. Python method resolution means the override's signature is what the callers actually hit — extending only the base class would leave ingestion callbacks without the new parameter.

**File A — `iris/src/iris/web/status/status_update.py` (base class):**

Find the `error` method (around line 221). Change signature and body:
```python
def error(
    self,
    message: str,
    exception=None,
    tokens: Optional[List[TokenUsageDTO]] = None,
    error_code: Optional[str] = None,
):
```

After `self.status.tokens = tokens or self.status.tokens` and before the SKIPPED loop, add:
```python
if error_code is not None and hasattr(self.status, "error_code"):
    self.status.error_code = error_code
```

**File B — `iris/src/iris/web/status/ingestion_status_callback.py` (override at line 192):**

Extend the override with the same parameter and the same status-assignment line:
```python
def error(
    self,
    message: str,
    exception=None,
    tokens=None,
    error_code: Optional[str] = None,
):
    """Send error status to Artemis (best-effort, never raises)."""
    failed_stage_name = self.stage.name
    self.stage.state = StageStateEnum.ERROR
    self.stage.message = message
    self.status.result = None
    if hasattr(self.status, "suggestions"):
        self.status.suggestions = None
    self.status.tokens = tokens or self.status.tokens
    if error_code is not None and hasattr(self.status, "error_code"):
        self.status.error_code = error_code
    # ... rest of existing body unchanged (SKIPPED loop, on_status_update_best_effort, logging, sentry) ...
```

Add `from typing import Optional` at the top of the file if not already imported.

Rationale: `self.status` on ingestion callbacks is `IngestionStatusUpdateDTO(StatusUpdateDTO)`, which inherits `error_code` from Task 5.3. The `hasattr` guard keeps non-ingestion callbacks using `StatusUpdateDTO` (which now also has `error_code`) working, and tolerates any other subclass that chooses not to expose the field.

- [ ] **Step 5.6: Add a callback-level integration test**

Append to `iris/tests/test_status_update_error_code.py`:
```python
from unittest.mock import MagicMock

from iris.web.status.ingestion_status_callback import IngestionStatusCallback


def test_ingestion_callback_error_sets_error_code(monkeypatch):
    # Stub out HTTP delivery so the test doesn't hit the network
    monkeypatch.setattr(
        "iris.web.status.ingestion_status_callback.http_requests.post",
        MagicMock(return_value=MagicMock(status_code=200)),
    )
    cb = IngestionStatusCallback(
        run_id="test-run",
        base_url="http://localhost",
        include_transcription_stages=True,
    )
    cb.error("video is private", error_code="YOUTUBE_PRIVATE")
    assert cb.status.error_code == "YOUTUBE_PRIVATE"
```

- [ ] **Step 5.7: Run — must pass**

Run: `poetry run pytest tests/test_status_update_error_code.py -v`
Expected: PASS (5 tests). The monkeypatch target path is exactly `iris.web.status.ingestion_status_callback.http_requests.post` because the module does `import requests as http_requests` at line 4 of `ingestion_status_callback.py` (confirmed in repo scan). Do not change it.

- [ ] **Step 5.8: Commit**

```bash
git add iris/src/iris/domain/status/status_update_dto.py iris/src/iris/web/status/status_update.py iris/tests/test_status_update_error_code.py
git commit -m "feat(pyris): propagate error_code on status callbacks"
```

---

### Task 6: `YouTubeDownloadError` exception

**Files:**
- Create: `iris/src/iris/pipeline/shared/transcription/youtube_utils.py` (first slice — exception only; validate/download added in Tasks 7 and 8)
- Test: `iris/tests/test_youtube_utils.py`

- [ ] **Step 6.1: Write the failing test**

Create `iris/tests/test_youtube_utils.py`:
```python
import pytest

from iris.pipeline.shared.transcription.youtube_utils import YouTubeDownloadError


def test_error_carries_structured_code_and_message():
    err = YouTubeDownloadError("YOUTUBE_PRIVATE", "video is private")
    assert err.error_code == "YOUTUBE_PRIVATE"
    assert str(err) == "video is private"


def test_error_is_raisable():
    with pytest.raises(YouTubeDownloadError) as excinfo:
        raise YouTubeDownloadError("YOUTUBE_LIVE", "live stream not supported")
    assert excinfo.value.error_code == "YOUTUBE_LIVE"
```

- [ ] **Step 6.2: Run — must fail**

Run: `poetry run pytest tests/test_youtube_utils.py -v`
Expected: FAIL — module not found.

- [ ] **Step 6.3: Create minimal module with the exception**

Create `iris/src/iris/pipeline/shared/transcription/youtube_utils.py`:
```python
"""YouTube-specific helpers: validation and download via yt-dlp.

Surfaces failures via structured error codes that Pyris propagates to
Artemis in the status-update callback for instructor-visible messaging.
"""


class YouTubeDownloadError(Exception):
    """Raised when yt-dlp cannot validate or download a YouTube video.

    Carries a structured ``error_code`` (one of YOUTUBE_PRIVATE,
    YOUTUBE_LIVE, YOUTUBE_TOO_LONG, YOUTUBE_UNAVAILABLE,
    YOUTUBE_DOWNLOAD_FAILED) so that upstream callers can attach it
    to the status callback verbatim.
    """

    def __init__(self, error_code: str, message: str) -> None:
        super().__init__(message)
        self.error_code = error_code
```

- [ ] **Step 6.4: Run — must pass**

Run: `poetry run pytest tests/test_youtube_utils.py -v`
Expected: PASS (2 tests).

- [ ] **Step 6.5: Commit**

```bash
git add iris/src/iris/pipeline/shared/transcription/youtube_utils.py iris/tests/test_youtube_utils.py
git commit -m "feat(pyris): add YouTubeDownloadError with structured error_code"
```

---

### Task 7: `validate_youtube_video`

**Files:**
- Modify: `iris/src/iris/pipeline/shared/transcription/youtube_utils.py`
- Modify: `iris/tests/test_youtube_utils.py`

- [ ] **Step 7.1: Write failing tests**

Append to `iris/tests/test_youtube_utils.py`:
```python
import json
import subprocess
from unittest.mock import patch

from iris.pipeline.shared.transcription.youtube_utils import validate_youtube_video


def _metadata_json(**overrides) -> str:
    base = {
        "id": "dQw4w9WgXcQ",
        "title": "Test Video",
        "duration": 120,
        "is_live": False,
        "availability": "public",
        "formats": [],
    }
    base.update(overrides)
    return json.dumps(base)


def _mock_run_ok(metadata_json: str):
    completed = subprocess.CompletedProcess(args=[], returncode=0, stdout=metadata_json, stderr="")
    return patch("subprocess.run", return_value=completed)


def _mock_run_fail(stderr: str, returncode: int = 1):
    err = subprocess.CalledProcessError(returncode=returncode, cmd=["yt-dlp"], stderr=stderr)
    return patch("subprocess.run", side_effect=err)


def test_valid_video_returns_metadata():
    with _mock_run_ok(_metadata_json()):
        meta = validate_youtube_video("https://youtu.be/dQw4w9WgXcQ", max_duration_seconds=3600)
    assert meta["id"] == "dQw4w9WgXcQ"
    assert meta["duration"] == 120


def test_live_stream_rejected():
    import pytest
    from iris.pipeline.shared.transcription.youtube_utils import YouTubeDownloadError

    with _mock_run_ok(_metadata_json(is_live=True)):
        with pytest.raises(YouTubeDownloadError) as excinfo:
            validate_youtube_video("https://youtu.be/X", max_duration_seconds=3600)
    assert excinfo.value.error_code == "YOUTUBE_LIVE"


def test_too_long_rejected():
    import pytest
    from iris.pipeline.shared.transcription.youtube_utils import YouTubeDownloadError

    with _mock_run_ok(_metadata_json(duration=10000)):
        with pytest.raises(YouTubeDownloadError) as excinfo:
            validate_youtube_video("https://youtu.be/X", max_duration_seconds=3600)
    assert excinfo.value.error_code == "YOUTUBE_TOO_LONG"


def test_private_video_rejected():
    import pytest
    from iris.pipeline.shared.transcription.youtube_utils import YouTubeDownloadError

    # yt-dlp marks private videos with a specific stderr pattern
    with _mock_run_fail("ERROR: [youtube] X: Private video. Sign in if you've been granted access to this video"):
        with pytest.raises(YouTubeDownloadError) as excinfo:
            validate_youtube_video("https://youtu.be/X", max_duration_seconds=3600)
    assert excinfo.value.error_code == "YOUTUBE_PRIVATE"


def test_unavailable_video_rejected():
    import pytest
    from iris.pipeline.shared.transcription.youtube_utils import YouTubeDownloadError

    with _mock_run_fail("ERROR: [youtube] X: Video unavailable"):
        with pytest.raises(YouTubeDownloadError) as excinfo:
            validate_youtube_video("https://youtu.be/X", max_duration_seconds=3600)
    assert excinfo.value.error_code == "YOUTUBE_UNAVAILABLE"


def test_unknown_yt_dlp_error_treated_as_download_failed():
    import pytest
    from iris.pipeline.shared.transcription.youtube_utils import YouTubeDownloadError

    # Novel stderr pattern — default to DOWNLOAD_FAILED (retryable per spec lines 88-91),
    # NOT UNAVAILABLE. An unknown stderr could be transient (network, yt-dlp bug);
    # terminalizing it as UNAVAILABLE would forbid retries forever.
    with _mock_run_fail("ERROR: some new yt-dlp error text"):
        with pytest.raises(YouTubeDownloadError) as excinfo:
            validate_youtube_video("https://youtu.be/X", max_duration_seconds=3600)
    assert excinfo.value.error_code == "YOUTUBE_DOWNLOAD_FAILED"


def test_timeout_raises_download_failed():
    import pytest
    from iris.pipeline.shared.transcription.youtube_utils import YouTubeDownloadError

    timeout_err = subprocess.TimeoutExpired(cmd=["yt-dlp"], timeout=30)
    with patch("subprocess.run", side_effect=timeout_err):
        with pytest.raises(YouTubeDownloadError) as excinfo:
            validate_youtube_video("https://youtu.be/X", max_duration_seconds=3600)
    assert excinfo.value.error_code == "YOUTUBE_DOWNLOAD_FAILED"
```

- [ ] **Step 7.2: Run — must fail**

Run: `poetry run pytest tests/test_youtube_utils.py -v`
Expected: FAIL — `validate_youtube_video` not defined.

- [ ] **Step 7.3: Implement `validate_youtube_video`**

Append to `iris/src/iris/pipeline/shared/transcription/youtube_utils.py`:
```python
import json
import re
import subprocess  # nosec B404
from typing import Any, Dict

from iris.common.logging_config import get_logger

logger = get_logger(__name__)

# Timeout for `yt-dlp --dump-json`. Metadata fetch is fast; 30 s is generous.
_VALIDATE_TIMEOUT_SECONDS = 30

_PRIVATE_PATTERNS = (
    re.compile(r"private video", re.IGNORECASE),
    re.compile(r"sign in", re.IGNORECASE),
)
_UNAVAILABLE_PATTERNS = (
    re.compile(r"video unavailable", re.IGNORECASE),
    re.compile(r"removed", re.IGNORECASE),
    re.compile(r"age[- ]restricted", re.IGNORECASE),
    re.compile(r"not available in your country", re.IGNORECASE),
)


def _classify_yt_dlp_error(stderr: str) -> str:
    """Map yt-dlp stderr to one of our structured error codes."""
    if any(p.search(stderr) for p in _PRIVATE_PATTERNS):
        return "YOUTUBE_PRIVATE"
    if any(p.search(stderr) for p in _UNAVAILABLE_PATTERNS):
        return "YOUTUBE_UNAVAILABLE"
    # Default: DOWNLOAD_FAILED (retryable per spec lines 88-91). An unrecognized
    # stderr could be a transient network/yt-dlp issue; UNAVAILABLE is terminal
    # and would block instructor-initiated retries.
    return "YOUTUBE_DOWNLOAD_FAILED"


def validate_youtube_video(url: str, max_duration_seconds: int) -> Dict[str, Any]:
    """Validate a YouTube URL by fetching metadata via ``yt-dlp --dump-json``.

    Performs no download; only network cost is the metadata fetch.

    Raises:
        YouTubeDownloadError: with a structured ``error_code`` on any failure.
    """
    command = ["yt-dlp", "--dump-json", "--no-warnings", url]
    try:
        result = subprocess.run(  # nosec B603
            command,
            capture_output=True,
            text=True,
            check=True,
            timeout=_VALIDATE_TIMEOUT_SECONDS,
        )
    except subprocess.TimeoutExpired as e:
        raise YouTubeDownloadError(
            "YOUTUBE_DOWNLOAD_FAILED",
            f"yt-dlp metadata fetch timed out after {_VALIDATE_TIMEOUT_SECONDS}s for {url}",
        ) from e
    except subprocess.CalledProcessError as e:
        stderr = (e.stderr or "")
        code = _classify_yt_dlp_error(stderr)
        raise YouTubeDownloadError(code, f"yt-dlp failed to validate {url}: {stderr.strip()}") from e
    except FileNotFoundError as e:
        # yt-dlp binary not installed — treat as download failure
        raise YouTubeDownloadError(
            "YOUTUBE_DOWNLOAD_FAILED", "yt-dlp binary not found on PATH"
        ) from e

    try:
        metadata = json.loads(result.stdout)
    except json.JSONDecodeError as e:
        raise YouTubeDownloadError(
            "YOUTUBE_DOWNLOAD_FAILED", f"yt-dlp returned non-JSON output for {url}"
        ) from e

    if metadata.get("is_live"):
        raise YouTubeDownloadError(
            "YOUTUBE_LIVE", "Live streams cannot be transcribed"
        )
    duration = metadata.get("duration")
    if duration is not None and duration > max_duration_seconds:
        raise YouTubeDownloadError(
            "YOUTUBE_TOO_LONG",
            f"Video duration {duration}s exceeds max {max_duration_seconds}s",
        )
    return metadata
```

- [ ] **Step 7.4: Run — must pass**

Run: `poetry run pytest tests/test_youtube_utils.py -v`
Expected: PASS (all 9 tests including the 2 from Task 6).

- [ ] **Step 7.5: Commit**

```bash
git add iris/src/iris/pipeline/shared/transcription/youtube_utils.py iris/tests/test_youtube_utils.py
git commit -m "feat(pyris): validate YouTube URLs via yt-dlp --dump-json"
```

---

### Task 8: `download_youtube_video`

**Files:**
- Modify: `iris/src/iris/pipeline/shared/transcription/youtube_utils.py`
- Modify: `iris/tests/test_youtube_utils.py`

- [ ] **Step 8.1: Write failing tests**

Append to `iris/tests/test_youtube_utils.py`:
```python
from pathlib import Path

from iris.pipeline.shared.transcription.youtube_utils import download_youtube_video


def test_download_success_returns_output_path(tmp_path, monkeypatch):
    output = tmp_path / "video.mp4"
    # Simulate yt-dlp: touching the file so the post-condition check passes
    def _fake_run(*args, **kwargs):
        output.write_bytes(b"\x00")
        return subprocess.CompletedProcess(args=args, returncode=0, stdout="", stderr="")
    monkeypatch.setattr("subprocess.run", _fake_run)
    result = download_youtube_video("https://youtu.be/X", output, timeout=600)
    assert result == output
    assert output.exists()


def test_download_timeout_raises_download_failed(tmp_path, monkeypatch):
    import pytest
    from iris.pipeline.shared.transcription.youtube_utils import YouTubeDownloadError

    timeout_err = subprocess.TimeoutExpired(cmd=["yt-dlp"], timeout=1)
    monkeypatch.setattr("subprocess.run", lambda *a, **kw: (_ for _ in ()).throw(timeout_err))
    with pytest.raises(YouTubeDownloadError) as excinfo:
        download_youtube_video("https://youtu.be/X", tmp_path / "out.mp4", timeout=1)
    assert excinfo.value.error_code == "YOUTUBE_DOWNLOAD_FAILED"


def test_download_nonzero_exit_raises_download_failed(tmp_path, monkeypatch):
    import pytest
    from iris.pipeline.shared.transcription.youtube_utils import YouTubeDownloadError

    err = subprocess.CalledProcessError(returncode=1, cmd=["yt-dlp"], stderr="network error")
    monkeypatch.setattr("subprocess.run", lambda *a, **kw: (_ for _ in ()).throw(err))
    with pytest.raises(YouTubeDownloadError) as excinfo:
        download_youtube_video("https://youtu.be/X", tmp_path / "out.mp4", timeout=600)
    assert excinfo.value.error_code == "YOUTUBE_DOWNLOAD_FAILED"


def test_download_output_missing_raises_download_failed(tmp_path, monkeypatch):
    import pytest
    from iris.pipeline.shared.transcription.youtube_utils import YouTubeDownloadError

    # subprocess returns success but no file materialized — yt-dlp quirk
    monkeypatch.setattr(
        "subprocess.run",
        lambda *a, **kw: subprocess.CompletedProcess(args=a, returncode=0, stdout="", stderr=""),
    )
    with pytest.raises(YouTubeDownloadError) as excinfo:
        download_youtube_video("https://youtu.be/X", tmp_path / "missing.mp4", timeout=600)
    assert excinfo.value.error_code == "YOUTUBE_DOWNLOAD_FAILED"
```

- [ ] **Step 8.2: Run — must fail**

Run: `poetry run pytest tests/test_youtube_utils.py -v -k download`
Expected: FAIL — `download_youtube_video` not defined.

- [ ] **Step 8.3: Implement `download_youtube_video`**

Append to `iris/src/iris/pipeline/shared/transcription/youtube_utils.py`:
```python
from pathlib import Path

from iris.tracing import observe


@observe(name="Download YouTube Video")
def download_youtube_video(
    url: str,
    output_path: Path,
    timeout: int,
) -> Path:
    """Download a YouTube video as an MP4 to ``output_path``.

    Uses ``yt-dlp -f bestvideo+bestaudio/best --merge-output-format mp4``.

    Raises:
        YouTubeDownloadError: with error_code="YOUTUBE_DOWNLOAD_FAILED" on
        timeout, non-zero exit, or a successful exit that nonetheless
        produced no output file.
    """
    output_path = Path(output_path)
    command = [
        "yt-dlp",
        "-f",
        "bestvideo+bestaudio/best",
        "--merge-output-format",
        "mp4",
        "--no-warnings",
        "-o",
        str(output_path),
        url,
    ]
    logger.info("Downloading YouTube video %s -> %s", url, output_path)
    try:
        subprocess.run(  # nosec B603
            command,
            capture_output=True,
            text=True,
            check=True,
            timeout=timeout,
        )
    except subprocess.TimeoutExpired as e:
        raise YouTubeDownloadError(
            "YOUTUBE_DOWNLOAD_FAILED",
            f"yt-dlp download timed out after {timeout}s for {url}",
        ) from e
    except subprocess.CalledProcessError as e:
        raise YouTubeDownloadError(
            "YOUTUBE_DOWNLOAD_FAILED",
            f"yt-dlp download failed (exit {e.returncode}): {(e.stderr or '').strip()}",
        ) from e

    if not output_path.exists() or output_path.stat().st_size == 0:
        raise YouTubeDownloadError(
            "YOUTUBE_DOWNLOAD_FAILED",
            f"yt-dlp reported success but no output at {output_path}",
        )
    return output_path
```

- [ ] **Step 8.4: Run — must pass**

Run: `poetry run pytest tests/test_youtube_utils.py -v`
Expected: PASS (all 13 tests).

- [ ] **Step 8.5: Commit**

```bash
git add iris/src/iris/pipeline/shared/transcription/youtube_utils.py iris/tests/test_youtube_utils.py
git commit -m "feat(pyris): implement download_youtube_video with structured errors"
```

---

### Task 9: Add `yt-dlp` dependency

**Files:**
- Modify: `iris/pyproject.toml`
- (Dockerfile revisit in Task 13 if necessary)

- [ ] **Step 9.1: Add yt-dlp to Poetry dependencies**

Edit `iris/pyproject.toml`. Find the `[tool.poetry.dependencies]` block and add:
```toml
yt-dlp = "^2025.9.26"
```

Rationale: caret-pinning accepts patch bumps (YouTube-side fixes) but not major bumps. Before running `poetry lock`, check the latest stable release with `curl -s https://pypi.org/pypi/yt-dlp/json | python -c "import json,sys;print(json.load(sys.stdin)['info']['version'])"` — if the newest stable is newer than `2025.9.26`, use the newer version as the caret base. Do NOT silently pick a different version; prefer the most recent release at execution time to avoid shipping with a known-broken yt-dlp against YouTube's current backend.

- [ ] **Step 9.2: Re-lock dependencies**

```bash
cd iris
poetry lock --no-update
poetry install
```

Expected: `poetry.lock` updated, no transitive conflict.

- [ ] **Step 9.3: Verify yt-dlp is importable and the CLI runs**

```bash
poetry run python -c "import yt_dlp; print(yt_dlp.version.__version__)"
poetry run yt-dlp --version
```

Expected: both commands print a version string.

- [ ] **Step 9.4: Rerun the full test suite**

```bash
poetry run pytest tests/ 2>&1 | tail -15
```

Expected: all tests pass (including the new ones from Tasks 2–8).

- [ ] **Step 9.5: Commit**

```bash
git add iris/pyproject.toml iris/poetry.lock
git commit -m "chore(pyris): add yt-dlp dependency"
```

---

### Task 10: Branch heavy pipeline on `video_source_type`

**Files:**
- Modify: `iris/src/iris/pipeline/shared/transcription/heavy_pipeline.py`
- Create: `iris/tests/test_heavy_pipeline_youtube_branch.py`

Note on scope: `HeavyTranscriptionPipeline` currently only takes `video_url`. Per the spec, we branch at the download step on `video_source_type`. The call site in `lecture_ingestion_update_pipeline.py` (`heavy(video_url, lecture_unit_id)`) needs the source type too.

- [ ] **Step 10.1: Write the failing test**

Create `iris/tests/test_heavy_pipeline_youtube_branch.py`:
```python
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

from iris.domain.data.video_source_type import VideoSourceType
from iris.pipeline.shared.transcription.heavy_pipeline import HeavyTranscriptionPipeline
from iris.pipeline.shared.transcription.youtube_utils import YouTubeDownloadError


@pytest.fixture
def mock_pipeline(tmp_path):
    """Fixture that does NOT pre-create the video file. The test passes only if
    the code under test actually invokes a download function that writes it.
    Stage-2 audio is fine to stub, but stage-1 video materialization is the
    behavior we're testing — don't fake it away."""
    callback = MagicMock()
    storage = MagicMock()
    storage.video_path = str(tmp_path / "video.mp4")
    storage.audio_path = str(tmp_path / "audio.m4a")
    # Stage 2 audio: stubbed separately; pre-create the audio file since we
    # don't exercise extract_audio's real behavior here.
    Path(storage.audio_path).write_bytes(b"\x00" * 1024)

    def _materialize_video(*args, **kwargs):
        # Both download_video (positional: url, path) and download_youtube_video
        # (positional: url, path) take path as the second arg. Write the file
        # only when the correct branch is actually invoked.
        target = Path(args[1]) if len(args) >= 2 else Path(kwargs.get("output_path", storage.video_path))
        target.write_bytes(b"\x00" * 1024)

    # Replace WhisperClient with a mock that returns a trivial transcript
    with patch("iris.pipeline.shared.transcription.heavy_pipeline.WhisperClient") as wc_cls:
        wc = MagicMock()
        wc.transcribe.return_value = {"segments": [], "language": "en"}
        wc_cls.return_value = wc
        with patch("iris.pipeline.shared.transcription.heavy_pipeline.extract_audio"):
            yield (
                HeavyTranscriptionPipeline(callback=callback, storage=storage),
                storage,
                callback,
                _materialize_video,
            )


def test_tum_live_branch_uses_ffmpeg_download(mock_pipeline):
    pipeline, storage, _, materialize = mock_pipeline
    with patch("iris.pipeline.shared.transcription.heavy_pipeline.download_video",
               side_effect=materialize) as dl_hls, \
         patch("iris.pipeline.shared.transcription.heavy_pipeline.download_youtube_video",
               side_effect=materialize) as dl_yt, \
         patch("iris.pipeline.shared.transcription.heavy_pipeline.validate_youtube_video") as v_yt:
        pipeline("https://live.rbg.tum.de/foo.m3u8", lecture_unit_id=1,
                 video_source_type=VideoSourceType.TUM_LIVE)
    dl_hls.assert_called_once()
    dl_yt.assert_not_called()
    v_yt.assert_not_called()


def test_youtube_branch_validates_then_downloads_via_yt_dlp(mock_pipeline):
    pipeline, storage, _, materialize = mock_pipeline
    with patch("iris.pipeline.shared.transcription.heavy_pipeline.download_video",
               side_effect=materialize) as dl_hls, \
         patch("iris.pipeline.shared.transcription.heavy_pipeline.download_youtube_video",
               side_effect=materialize) as dl_yt, \
         patch("iris.pipeline.shared.transcription.heavy_pipeline.validate_youtube_video") as v_yt:
        v_yt.return_value = {"duration": 120, "title": "t"}
        pipeline("https://youtu.be/dQw4w9WgXcQ", lecture_unit_id=1,
                 video_source_type=VideoSourceType.YOUTUBE)
    v_yt.assert_called_once()
    dl_yt.assert_called_once()
    dl_hls.assert_not_called()


def test_youtube_validation_failure_propagates(mock_pipeline):
    pipeline, _, _, _ = mock_pipeline
    with patch(
        "iris.pipeline.shared.transcription.heavy_pipeline.validate_youtube_video",
        side_effect=YouTubeDownloadError("YOUTUBE_PRIVATE", "private"),
    ):
        with pytest.raises(YouTubeDownloadError) as excinfo:
            pipeline("https://youtu.be/X", lecture_unit_id=1,
                     video_source_type=VideoSourceType.YOUTUBE)
        assert excinfo.value.error_code == "YOUTUBE_PRIVATE"


def test_missing_source_type_defaults_to_tum_live(mock_pipeline):
    """Backward compatibility: older callers that don't pass video_source_type."""
    pipeline, storage, _, materialize = mock_pipeline
    with patch("iris.pipeline.shared.transcription.heavy_pipeline.download_video",
               side_effect=materialize) as dl_hls, \
         patch("iris.pipeline.shared.transcription.heavy_pipeline.download_youtube_video",
               side_effect=materialize) as dl_yt:
        pipeline("https://live.rbg.tum.de/foo.m3u8", lecture_unit_id=1)
    dl_hls.assert_called_once()
    dl_yt.assert_not_called()
```

- [ ] **Step 10.2: Run — must fail**

Run: `poetry run pytest tests/test_heavy_pipeline_youtube_branch.py -v`
Expected: FAIL — `video_source_type` not a valid kwarg on `HeavyTranscriptionPipeline.__call__`, and the YouTube helpers are not imported into `heavy_pipeline`.

- [ ] **Step 10.3: Modify `heavy_pipeline.py`**

Edit `iris/src/iris/pipeline/shared/transcription/heavy_pipeline.py`:

Add imports near the existing `video_utils` import:
```python
from iris.domain.data.video_source_type import VideoSourceType
from iris.pipeline.shared.transcription.youtube_utils import (
    YouTubeDownloadError,
    download_youtube_video,
    validate_youtube_video,
)
```

Change the `__call__` signature from:
```python
def __call__(self, video_url: str, lecture_unit_id: int) -> Dict[str, Any]:
```
to:
```python
def __call__(
    self,
    video_url: str,
    lecture_unit_id: int,
    video_source_type: VideoSourceType = VideoSourceType.TUM_LIVE,
) -> Dict[str, Any]:
```

Replace the current Stage 1 block (which unconditionally calls `download_video`) with:
```python
        # Stage 1: Download video
        self.callback.in_progress("Downloading video...")
        logger.info("%s Downloading video to %s", prefix, self.storage.video_path)
        if video_source_type == VideoSourceType.YOUTUBE:
            metadata = validate_youtube_video(
                video_url,
                max_duration_seconds=settings.transcription.youtube_max_duration_seconds,
            )
            logger.info(
                "%s YouTube metadata OK: title=%r duration=%ss",
                prefix,
                metadata.get("title"),
                metadata.get("duration"),
            )
            from pathlib import Path as _Path
            download_youtube_video(
                video_url,
                _Path(self.storage.video_path),
                timeout=settings.transcription.youtube_download_timeout_seconds,
            )
        else:  # TUM_LIVE (default)
            download_video(
                video_url,
                self.storage.video_path,
                timeout=settings.transcription.download_timeout_seconds,
                lecture_unit_id=lecture_unit_id,
            )
```

Leave Stages 2 and 3 (`extract_audio`, Whisper) unchanged — they are source-agnostic.

- [ ] **Step 10.4: Run — must pass**

Run: `poetry run pytest tests/test_heavy_pipeline_youtube_branch.py -v`
Expected: PASS (4 tests).

- [ ] **Step 10.5: Run full suite — regression guard**

```bash
poetry run pytest tests/ 2>&1 | tail -15
```
Expected: all tests pass.

- [ ] **Step 10.6: Commit**

```bash
git add iris/src/iris/pipeline/shared/transcription/heavy_pipeline.py iris/tests/test_heavy_pipeline_youtube_branch.py
git commit -m "feat(pyris): branch heavy pipeline download step on video_source_type"
```

---

### Task 11: Wire `video_source_type` through the orchestrator + surface `error_code`

**Files:**
- Modify: `iris/src/iris/pipeline/lecture_ingestion_update_pipeline.py`
- Create: `iris/tests/test_ingestion_error_code_wiring.py`

- [ ] **Step 11.1: Write the failing test**

Create `iris/tests/test_ingestion_error_code_wiring.py`:
```python
"""Verify: YouTube-specific failures reach callback.error with the right code,
and generic transcription failures surface as TRANSCRIPTION_FAILED."""

from unittest.mock import MagicMock, patch

import pytest

from iris.pipeline.shared.transcription.youtube_utils import YouTubeDownloadError


@pytest.fixture
def callback_error_spy():
    spy = MagicMock()
    return spy


def test_youtube_error_forwarded_with_structured_code(callback_error_spy):
    # Simulate the handler Task 11 adds: YouTubeDownloadError caught at the
    # orchestrator level and forwarded to callback.error(error_code=...).
    from iris.pipeline.lecture_ingestion_update_pipeline import (
        _translate_transcription_exception_to_error_code,
    )
    err = YouTubeDownloadError("YOUTUBE_LIVE", "live stream")
    assert _translate_transcription_exception_to_error_code(err) == "YOUTUBE_LIVE"


def test_generic_exception_becomes_transcription_failed():
    from iris.pipeline.lecture_ingestion_update_pipeline import (
        _translate_transcription_exception_to_error_code,
    )
    assert _translate_transcription_exception_to_error_code(RuntimeError("whisper timeout")) == "TRANSCRIPTION_FAILED"


def test_slide_detection_only_branches_on_video_source_type():
    """Resume-from-checkpoint path must use yt-dlp for YouTube jobs, not FFmpeg/HLS."""
    from iris.domain.data.video_source_type import VideoSourceType

    with patch("iris.pipeline.lecture_ingestion_update_pipeline.download_video") as dl_hls, \
         patch("iris.pipeline.lecture_ingestion_update_pipeline.download_youtube_video") as dl_yt, \
         patch("iris.pipeline.lecture_ingestion_update_pipeline.validate_youtube_video") as v_yt, \
         patch("iris.pipeline.lecture_ingestion_update_pipeline.LightTranscriptionPipeline"), \
         patch("iris.pipeline.lecture_ingestion_update_pipeline.TranscriptionTempStorage") as storage_cls:
        storage_cls.return_value.__enter__.return_value = MagicMock()
        v_yt.return_value = {"duration": 120}

        lecture_unit = MagicMock()
        lecture_unit.lecture_unit_id = 1
        lecture_unit.video_link = "https://youtu.be/dQw4w9WgXcQ"
        lecture_unit.video_source_type = VideoSourceType.YOUTUBE
        lecture_unit.transcription.segments = []
        lecture_unit.transcription.language = "en"
        dto = MagicMock()
        dto.lecture_unit = lecture_unit

        from iris.pipeline.lecture_ingestion_update_pipeline import (
            LectureIngestionUpdatePipeline,
        )
        pipeline = LectureIngestionUpdatePipeline.__new__(LectureIngestionUpdatePipeline)
        pipeline.dto = dto
        pipeline._is_local = False

        with patch.object(pipeline, "_build_checkpoint", return_value={}):
            try:
                pipeline._run_slide_detection_only(MagicMock())
            except Exception:
                pass  # downstream mocks may raise; we only assert the download branch

        dl_yt.assert_called_once()
        dl_hls.assert_not_called()


def test_youtube_source_type_passed_through_to_heavy_pipeline():
    """The orchestrator reads dto.lecture_unit.video_source_type and forwards it."""
    from iris.domain.data.video_source_type import VideoSourceType

    # Build the smallest DTO-like stand-in the orchestrator will accept. If the
    # real orchestrator signature differs, adapt accordingly.
    lecture_unit = MagicMock()
    lecture_unit.lecture_unit_id = 1
    lecture_unit.video_link = "https://youtu.be/dQw4w9WgXcQ"
    lecture_unit.video_source_type = VideoSourceType.YOUTUBE

    dto = MagicMock()
    dto.lecture_unit = lecture_unit

    with patch(
        "iris.pipeline.lecture_ingestion_update_pipeline.HeavyTranscriptionPipeline"
    ) as heavy_cls:
        heavy = MagicMock()
        heavy.return_value = {"segments": [], "language": "en"}
        heavy_cls.return_value = heavy

        # Short-circuit the rest of the pipeline (light + ingestion) so we can
        # assert the heavy-phase invocation in isolation.
        with patch(
            "iris.pipeline.lecture_ingestion_update_pipeline.LightTranscriptionPipeline"
        ), patch(
            "iris.pipeline.lecture_ingestion_update_pipeline.TranscriptionTempStorage"
        ) as storage_cls:
            storage_cls.return_value.__enter__.return_value = MagicMock()

            from iris.pipeline.lecture_ingestion_update_pipeline import (
                LectureIngestionUpdatePipeline,
            )
            pipeline = LectureIngestionUpdatePipeline.__new__(LectureIngestionUpdatePipeline)
            pipeline.dto = dto
            pipeline._is_local = False
            # Only drive the method we care about; guard any ancillary work.
            with patch.object(pipeline, "_build_checkpoint", return_value={}):
                callback = MagicMock()
                try:
                    pipeline._run_full_transcription(callback)
                except Exception:
                    pass  # downstream MagicMock wiring may raise; we only care about the heavy-call kwargs

    assert heavy.called
    called_kwargs = heavy.call_args.kwargs
    called_args = heavy.call_args.args
    # The signature is heavy(video_url, lecture_unit_id, video_source_type=...)
    # Accept either positional or keyword forwarding:
    all_args = list(called_args) + list(called_kwargs.values())
    assert VideoSourceType.YOUTUBE in all_args
```

- [ ] **Step 11.2: Run — must fail**

Run: `poetry run pytest tests/test_ingestion_error_code_wiring.py -v`
Expected: FAIL — `_translate_transcription_exception_to_error_code` does not exist yet and orchestrator doesn't forward `video_source_type`.

- [ ] **Step 11.3: Add the translation helper + forward the source type + wrap the run**

Edit `iris/src/iris/pipeline/lecture_ingestion_update_pipeline.py`.

Near the top of the file, alongside existing imports, add:
```python
from iris.pipeline.shared.transcription.youtube_utils import YouTubeDownloadError


def _translate_transcription_exception_to_error_code(exc: BaseException) -> str:
    """Map any exception raised from the heavy/light phases to a wire error code.

    YouTubeDownloadError already carries a structured ``error_code``; everything
    else collapses to TRANSCRIPTION_FAILED so Artemis can surface a generic
    "transcript unavailable" message with a retry affordance.
    """
    if isinstance(exc, YouTubeDownloadError):
        return exc.error_code
    return "TRANSCRIPTION_FAILED"
```

In `_run_full_transcription`, replace the heavy-phase invocation
```python
            raw_transcript = heavy(video_url, lecture_unit_id)
```
with:
```python
            raw_transcript = heavy(
                video_url,
                lecture_unit_id,
                video_source_type=self.dto.lecture_unit.video_source_type,
            )
```

**Also fix the slide-detection-only retry branch.** The resume-from-checkpoint path at `_run_slide_detection_only` (around line 220) re-downloads the video via `download_video` (the FFmpeg/HLS path) — this will fail for YouTube jobs because yt-dlp is the correct tool there. Apply the same branching as `heavy_pipeline`:

Replace the existing `download_video(video_url, storage.video_path, ...)` call inside `_run_slide_detection_only` with:

```python
from iris.domain.data.video_source_type import VideoSourceType
from iris.pipeline.shared.transcription.youtube_utils import (
    download_youtube_video,
    validate_youtube_video,
)
from pathlib import Path

video_source_type = self.dto.lecture_unit.video_source_type
if video_source_type == VideoSourceType.YOUTUBE:
    validate_youtube_video(
        video_url,
        max_duration_seconds=settings.transcription.youtube_max_duration_seconds,
    )
    download_youtube_video(
        video_url,
        Path(storage.video_path),
        timeout=settings.transcription.youtube_download_timeout_seconds,
    )
else:  # TUM_LIVE (default, includes None)
    download_video(
        video_url,
        storage.video_path,
        timeout=settings.transcription.download_timeout_seconds,
        lecture_unit_id=lecture_unit_id,
    )
```

Preserve any `callback.in_progress(...)` / `callback.done(...)` wrapping already in place.

In the `run()` method (the public entry point — search for `callback.error(str(e), exception=e)` around line 140–150 shown in the scan), change:
```python
            callback.error(str(e), exception=e)
```
to:
```python
            error_code = _translate_transcription_exception_to_error_code(e)
            callback.error(str(e), exception=e, error_code=error_code)
```

Note: if the call site uses `self.callback` rather than a local `callback`, preserve that exact attribute access — do not change anything except the argument list.

- [ ] **Step 11.4: Run — must pass**

Run: `poetry run pytest tests/test_ingestion_error_code_wiring.py -v`
Expected: PASS (3 tests). If Step 11.3's positional/keyword forwarding diverges from the test assertion, adjust the test's "accept positional or keyword" check; do NOT weaken the passing-through guarantee.

- [ ] **Step 11.5: Full suite**

```bash
poetry run pytest tests/ 2>&1 | tail -15
```
Expected: all tests pass.

- [ ] **Step 11.6: Commit**

```bash
git add iris/src/iris/pipeline/lecture_ingestion_update_pipeline.py iris/tests/test_ingestion_error_code_wiring.py
git commit -m "feat(pyris): forward video_source_type + propagate structured error_code"
```

---

### Task 12: Dockerfile — yt-dlp runtime verification

**Files:**
- Possibly modify: `iris/Dockerfile`

`yt-dlp` installs as a pure-Python package through Poetry and ships its own binary under `poetry run yt-dlp`. The existing Dockerfile uses `poetry install --only main --no-root`, so yt-dlp lands in the venv automatically. The only concern is whether `yt-dlp` needs additional system packages at runtime (e.g. `ffmpeg` — already installed, but also `python3-mutagen` for some format merges).

- [ ] **Step 12.1: Build the current Docker image**

```bash
cd /Users/pat/projects/claudeworktrees/edutelligence/feature-pyris-youtube-ingestion/iris
docker build -t pyris-yt-check .
```

Expected: build succeeds.

- [ ] **Step 12.2: Verify yt-dlp is on PATH inside the image**

```bash
docker run --rm pyris-yt-check poetry run yt-dlp --version
```
Expected: a version string is printed.

- [ ] **Step 12.3: Verify a real metadata fetch works (requires network)**

```bash
docker run --rm pyris-yt-check poetry run yt-dlp --dump-json --no-warnings "https://www.youtube.com/watch?v=jNQXAC9IVRw" | head -c 200
```
Expected: valid JSON prefix (title, id, duration, …).

- [ ] **Step 12.4: Verify the actual MP4 download + merge path**

Metadata fetch alone does not prove the download/merge works — yt-dlp's MP4 merging needs FFmpeg from the image's PATH, which is the real runtime concern. Prove it end-to-end:

```bash
docker run --rm -v /tmp/pyris-yt-download:/out pyris-yt-check bash -c "mkdir -p /out && poetry run yt-dlp -f 'bestvideo+bestaudio/best' --merge-output-format mp4 --no-warnings -o '/out/test.mp4' 'https://www.youtube.com/watch?v=jNQXAC9IVRw'"
ls -la /tmp/pyris-yt-download/test.mp4
file /tmp/pyris-yt-download/test.mp4
```

Expected: the file exists, is >0 bytes, and `file` reports `ISO Media, MP4 Base Media`. If the merge step errors with a missing-codec or FFmpeg-not-found warning, add the missing apt package (typically `ffmpeg` is already present; `python3-mutagen` may be needed for some metadata writes) to the Dockerfile and rebuild.

Clean up afterwards:
```bash
rm -rf /tmp/pyris-yt-download
```

- [ ] **Step 12.5: Document any Dockerfile change**

If Steps 12.3 and 12.4 both succeeded with no Dockerfile edit, skip to 12.6. Otherwise, edit `iris/Dockerfile` to extend the `apt-get install` line with the required package and commit the change in this step.

- [ ] **Step 12.6: Commit (only if Dockerfile changed; otherwise record verification in the next task's commit)**

```bash
# Only if a Dockerfile change was needed:
git add iris/Dockerfile
git commit -m "chore(pyris): add runtime package required by yt-dlp"
```

---

### Task 13: Final verification

**Goal:** Prove the whole feature is green before offering a PR.

- [ ] **Step 13.1: Full test suite**

```bash
cd /Users/pat/projects/claudeworktrees/edutelligence/feature-pyris-youtube-ingestion/iris
poetry run pytest tests/ -v 2>&1 | tee /tmp/pyris-yt-final.log | tail -30
```
Expected: all tests pass. If any fail, read `/tmp/pyris-yt-final.log` for details and fix the root cause (never skip).

- [ ] **Step 13.2: Lint / type check (whatever the repo uses)**

Check the pyproject for configured tools and run them. Typical candidates:
```bash
poetry run ruff check iris/src iris/tests
poetry run mypy iris/src || true   # only if mypy is configured
poetry run black --check iris/src iris/tests
```
Fix findings. If none are configured, skip — but confirm there is truly nothing to run by grepping pyproject for `ruff`, `mypy`, `black`.

- [ ] **Step 13.3: Manual smoke against a real video (local run)**

Only if the environment has network + yt-dlp working:
```bash
poetry run python -c "
from pathlib import Path
from iris.pipeline.shared.transcription.youtube_utils import (
    validate_youtube_video, download_youtube_video,
)
meta = validate_youtube_video('https://www.youtube.com/watch?v=jNQXAC9IVRw', max_duration_seconds=3600)
print('metadata OK:', meta['title'], meta['duration'])
# Downloading optional; comment back in if you want to sanity-check the MP4 path
# download_youtube_video('https://www.youtube.com/watch?v=jNQXAC9IVRw', Path('/tmp/yt-smoke.mp4'), timeout=300)
"
```
Expected: metadata prints successfully.

- [ ] **Step 13.4: Push branch + open PR**

```bash
git push -u origin feature/pyris-youtube-ingestion
gh pr create --base main --title "feat: Pyris YouTube ingestion" --body "$(cat <<'EOF'
## Summary
- Add `yt-dlp`-based YouTube download path alongside the existing TUM Live FFmpeg path, gated by a new `video_source_type` field on the ingestion webhook payload.
- Surface YouTube-specific failures (private, live, too-long, unavailable, download-failed) and generic transcription failures as structured `error_code` values on the status callback, so Artemis can render user-actionable messages.
- Wire-compatible: `video_source_type` defaults to `TUM_LIVE`; older Artemis deployments are unaffected.

## Test plan
- [x] Unit tests for `YouTubeDownloadError`, `validate_youtube_video`, `download_youtube_video` (mocked subprocess)
- [x] Pipeline-level tests for the branch + error propagation through the orchestrator
- [x] DTO tests for `VideoSourceType` defaulting and alias handling
- [x] `error_code` round-trip test through `StatusUpdateDTO` + `IngestionStatusCallback`
- [x] Dockerfile verification: yt-dlp runnable; metadata fetch works against a real URL
EOF
)"
```

- [ ] **Step 13.5: Watch CI; fix any regressions**

```bash
gh pr checks --watch
```
If anything goes red, fix the root cause and push again. Do not merge or hand off until fully green.

---

## Self-Review Checklist (executed after plan writing — record findings inline)

- **Spec coverage:** Every section of `2026-04-14-youtube-transcription-design.md` under "Repository 1: Edutelligence (Pyris)" maps to a task:
  - `youtube_utils.py` (`validate_youtube_video`, `download_youtube_video`, `YouTubeDownloadError`) → Tasks 6–8
  - Structured error codes → Task 5 (DTO + callback) + Task 11 (translation helper)
  - `VideoSourceType` enum → Task 2
  - `LectureUnitPageDTO` field → Task 3 (note: spec said `LectureIngestionWebhookDTO`; the actual class name on-disk is `LectureUnitPageDTO` — plan uses the real name and calls this out)
  - `heavy_pipeline.py` branching → Task 10
  - `config.py` settings → Task 4
  - `pyproject.toml` yt-dlp → Task 9
  - Dockerfile → Task 12
  - Tests for `youtube_utils` and `heavy_pipeline` → Tasks 7, 8, 10
- **Placeholder scan:** No "TBD", no "add validation", no "write tests". Every code step has full code; every run step has commands + expected output.
- **Type consistency:** `YouTubeDownloadError(error_code, message)` positional order is the same in Task 6 (definition) and Tasks 7, 8, 11 (raises). `validate_youtube_video(url, max_duration_seconds)` signature consistent Task 7 ↔ Task 10 call site. `download_youtube_video(url, output_path, timeout)` consistent Task 8 ↔ Task 10.
- **Cross-task invariant:** Task 5's `error_code` field on `StatusUpdateDTO` serializes as snake_case `error_code` on the wire (matches spec lines 287-293). The Artemis-side DTO uses `@JsonProperty("error_code")` on a camelCase Java `errorCode` field. Task 9 of the Artemis plan adds a `WireFormatContractTest` that pins this shape from both ends.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-15-pyris-youtube-ingestion.md`. Two execution options:

1. **Subagent-Driven (recommended)** — Dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** — Execute in-session using `superpowers:executing-plans`, batch execution with checkpoints.

Which approach?
