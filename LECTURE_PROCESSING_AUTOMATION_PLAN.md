# Lecture Processing Automation Implementation Plan

## Executive Summary

This document outlines the implementation plan for automating the lecture content processing pipeline in Artemis. The goal is to create a fully automated system where instructors don't need to manually trigger transcription or ingestion - the server handles the entire flow automatically.

## Current State Analysis

### Transcription Flow (Nebula)
- **Trigger**: Manual checkbox in client + separate API call after unit save
- **Polling**: `NebulaTranscriptionPollingScheduler` polls every 30 seconds
- **Status**: `TranscriptionStatus` enum (PENDING, PROCESSING, COMPLETED, FAILED)
- **Retry**: 3 failures before marking as FAILED
- **No cancellation**: Cannot cancel ongoing Nebula jobs

### Ingestion Flow (Pyris)
- **Trigger**: Manual "Send to Iris" button OR auto-trigger with 2-minute backoff
- **Status**: `IngestionState` enum (NOT_STARTED, IN_PROGRESS, DONE, ERROR, PARTIALLY_INGESTED)
- **Job tracking**: Uses Hazelcast distributed map with 1-hour timeout
- **Already exists**: `IrisLectureUnitAutoIngestionService` for scheduling

### Client-Side Manual Triggers (to be removed)
1. **Transcription**:
   - Checkbox in `attachment-video-unit-form.component.ts`
   - "Generate Transcription" button in `lecture-unit-management.component.ts`
   - Transcription logic in create/edit components

2. **Ingestion**:
   - "Send to Iris" button in `lecture.component.ts` (bulk)
   - "Send to Iris" button in `lecture-detail.component.ts`
   - Per-unit ingest button in `lecture-unit-management.component.ts`

## Target Architecture

### New Service: `LectureContentProcessingService`

A centralized service that orchestrates the entire lecture content processing lifecycle:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    LectureContentProcessingService                       │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │                    Processing State Machine                        │   │
│  │                                                                    │   │
│  │   IDLE ──► CHECKING_PLAYLIST ──► TRANSCRIBING ──► INGESTING ──► DONE │
│  │    │              │                   │               │           │   │
│  │    └──────────────┴───────────────────┴───────────────┴──► FAILED │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│  Features:                                                               │
│  • Automatic transcription triggering for video units                   │
│  • Retry logic (3 attempts)                                             │
│  • Recovery on node restart                                             │
│  • Cancellation on unit deletion/video change                           │
│  • Status tracking for UI display                                       │
└─────────────────────────────────────────────────────────────────────────┘
```

### New Database Entity: `LectureUnitProcessingState`

```java
@Entity
@Table(name = "lecture_unit_processing_state")
public class LectureUnitProcessingState extends DomainObject {

    @OneToOne
    @JoinColumn(name = "lecture_unit_id", unique = true)
    private LectureUnit lectureUnit;

    @Enumerated(EnumType.STRING)
    private ProcessingPhase phase; // IDLE, CHECKING_PLAYLIST, TRANSCRIBING, INGESTING, DONE, FAILED

    @Column(name = "retry_count")
    private int retryCount = 0;

    @Column(name = "video_source_hash")
    private String videoSourceHash; // To detect video URL changes

    @Column(name = "attachment_version")
    private Integer attachmentVersion; // To detect PDF changes

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "last_updated")
    private ZonedDateTime lastUpdated;

    @Column(name = "started_at")
    private ZonedDateTime startedAt;
}
```

### New Enum: `ProcessingPhase`

```java
public enum ProcessingPhase {
    IDLE,               // Not processing, waiting for trigger
    CHECKING_PLAYLIST,  // Checking if TUM Live playlist available
    TRANSCRIBING,       // Transcription in progress with Nebula
    INGESTING,          // Ingestion in progress with Pyris
    DONE,               // Successfully processed
    FAILED              // Failed after max retries
}
```

## Implementation Steps

### Phase 1: Database and Domain Models

1. Create `LectureUnitProcessingState` entity
2. Create `ProcessingPhase` enum
3. Create `LectureUnitProcessingStateRepository`
4. Create Liquibase migration for new table
5. Update `LectureTranscription` to link with processing state

### Phase 2: Core Processing Service

1. Create `LectureContentProcessingService`:
   - `triggerProcessing(AttachmentVideoUnit)` - Main entry point
   - `cancelProcessing(Long lectureUnitId)` - Cancel ongoing processing
   - `handleTranscriptionComplete(LectureTranscription)` - Called when transcription finishes
   - `handleIngestionComplete(Long lectureUnitId)` - Called when ingestion finishes
   - `retryFailedProcessing(Long lectureUnitId)` - Manual retry

2. Create `LectureContentProcessingScheduler`:
   - Poll stuck processing jobs
   - Resume after node restart
   - Clean up stale states

3. Modify `LectureTranscriptionService`:
   - Add callback to `LectureContentProcessingService` on completion
   - Track processing state changes

4. Modify `PyrisStatusUpdateService`:
   - Add callback to `LectureContentProcessingService` on completion

### Phase 3: Integration Points

1. **On AttachmentVideoUnit Create**:
   - In `AttachmentVideoUnitService.saveAttachmentVideoUnit()`
   - Trigger processing if has video source or PDF

2. **On AttachmentVideoUnit Update**:
   - In `AttachmentVideoUnitService.updateAttachmentVideoUnit()`
   - Detect video URL change → cancel + restart
   - Detect PDF change → re-ingest

3. **On LectureUnit Delete**:
   - In `LectureUnitService.removeLectureUnit()`
   - Cancel any ongoing processing
   - Delete processing state
   - (Already deletes from Pyris via `irisLectureApi.deleteLectureFromPyrisDB()`)

4. **On Lecture Import**:
   - In `LectureUnitImportService.importLectureUnits()`
   - Trigger processing for imported units

5. **On Slide Processing Complete**:
   - In `SlideSplitterService.splitAttachmentVideoUnitIntoSingleSlides()`
   - Trigger ingestion after slides are ready

### Phase 4: Remove Client-Side Triggers

#### Client Files to Modify:

1. **attachment-video-unit-form.component.ts/html**:
   - Remove `generateTranscript` checkbox and form control
   - Remove `shouldShowTranscriptCheckbox()` logic
   - Remove transcription warnings
   - Keep transcription status display (read-only)

2. **create-attachment-video-unit.component.ts**:
   - Remove transcription triggering logic (lines 92-128)
   - Server handles it automatically

3. **edit-attachment-video-unit.component.ts**:
   - Remove transcription triggering logic (lines 141-182)
   - Remove transcription fetching on load

4. **lecture-unit-management.component.ts/html**:
   - Remove "Generate Transcription" button
   - Add single "Retry Processing" button (for errors)
   - Update status badges to show processing state

5. **lecture.component.ts/html**:
   - Remove bulk "Ingest Lectures" button
   - Keep ingestion status column (read-only)

6. **lecture-detail.component.ts/html**:
   - Remove "Send to Iris" button

7. **lecture-units.component.ts**:
   - Remove transcription handling in inline form

#### Services to Modify:

1. **lecture-transcription.service.ts**:
   - Remove `ingestTranscription()` method
   - Remove `createTranscription()` method (keep for manual override if needed)
   - Keep `getTranscription()` and `getTranscriptionStatus()`

2. **attachment-video-unit.service.ts**:
   - Remove `startTranscription()` method
   - Keep `getPlaylistUrl()` for display purposes only

3. **lecture.service.ts**:
   - Remove `ingestLecturesInPyris()` method
   - Keep `getIngestionState()` for display

4. **lecture-unit.service.ts**:
   - Remove `ingestLectureUnitInPyris()` method
   - Keep `getIngestionState()` for display

### Phase 5: Server-Side API Cleanup

#### Endpoints to Remove/Deprecate:

1. `POST /api/nebula/{lectureId}/lecture-unit/{lectureUnitId}/transcriber` - Remove (auto-triggered now)
2. `POST /api/lecture/courses/{courseId}/ingest` - Remove (auto-triggered now)
3. `POST /api/lecture/lectures/{lectureId}/lecture-units/{lectureUnitId}/ingest` - Replace with retry endpoint

#### New Endpoints:

1. `POST /api/lecture/lectures/{lectureId}/lecture-units/{lectureUnitId}/processing/retry`
   - Manual retry for failed processing
   - Only visible when processing failed

2. `GET /api/lecture/lectures/{lectureId}/lecture-units/{lectureUnitId}/processing-state`
   - Get current processing state for a unit

### Phase 6: Status Display

#### Processing State Badge Colors:
- **IDLE**: Gray (not started)
- **CHECKING_PLAYLIST**: Blue (spinner)
- **TRANSCRIBING**: Blue (spinner)
- **INGESTING**: Blue (spinner)
- **DONE**: Green (checkmark)
- **FAILED**: Red (with retry button)

#### UI Updates:
1. In `lecture-unit-management.component.html`:
   - Show processing phase badge
   - Show "Retry" button only when FAILED
   - Show progress indicator for active processing

2. Status text mapping:
   - IDLE: "Not processed"
   - CHECKING_PLAYLIST: "Checking video availability..."
   - TRANSCRIBING: "Generating transcript..."
   - INGESTING: "Processing for Iris..."
   - DONE: "Ready for Iris"
   - FAILED: "Processing failed" + retry button

### Phase 7: Recovery and Resilience

1. **Node Restart Recovery**:
   - `LectureContentProcessingScheduler` checks for stuck states on startup
   - States in CHECKING_PLAYLIST, TRANSCRIBING, INGESTING for > 1 hour → retry
   - Maintains `startedAt` timestamp for timeout detection

2. **Retry Logic**:
   - Max 3 retry attempts
   - Exponential backoff: 1 min, 5 min, 15 min
   - After 3 failures → mark as FAILED, require manual intervention

3. **Idempotency**:
   - Processing state hash includes video URL hash + attachment version
   - Re-processing only if content actually changed

## Detailed Flow Diagrams

### Flow 1: Video Unit Created with TUM Live URL

```
AttachmentVideoUnit Created with videoSource = "https://live.rbg.tum.de/w/course/12345"
    │
    ▼
AttachmentVideoUnitService.saveAttachmentVideoUnit()
    │
    ▼
LectureContentProcessingService.triggerProcessing(unit)
    │
    ├──► Create LectureUnitProcessingState (phase=CHECKING_PLAYLIST)
    │
    ▼
TumLiveService.getTumLivePlaylistLink(videoSource)
    │
    ├──► Playlist NOT found → phase=IDLE (no transcription needed, just ingest)
    │         │
    │         ▼
    │    Schedule ingestion → phase=INGESTING
    │
    └──► Playlist FOUND → phase=TRANSCRIBING
              │
              ▼
         LectureTranscriptionService.startNebulaTranscription()
              │
              ▼
         [Polling continues via NebulaTranscriptionPollingScheduler]
              │
              ▼
         Transcription COMPLETED
              │
              ▼
         LectureContentProcessingService.handleTranscriptionComplete()
              │
              ▼
         phase=INGESTING
              │
              ▼
         PyrisWebhookService.addLectureUnitToPyrisDB() [with transcription]
              │
              ▼
         [Webhook callback from Pyris]
              │
              ▼
         LectureContentProcessingService.handleIngestionComplete()
              │
              ▼
         phase=DONE ✓
```

### Flow 2: Video URL Changed

```
AttachmentVideoUnit.videoSource changed from "url1" to "url2"
    │
    ▼
AttachmentVideoUnitService.updateAttachmentVideoUnit()
    │
    ▼
LectureContentProcessingService.triggerProcessing(unit)
    │
    ├──► Detect URL change (hash mismatch)
    │
    ▼
Cancel existing processing
    ├──► Delete existing LectureTranscription
    ├──► Delete from Pyris (PyrisWebhookService.deleteLectureFromPyrisDB)
    │
    ▼
Restart processing from CHECKING_PLAYLIST
    │
    ▼
[Same flow as Flow 1]
```

### Flow 3: Unit Deleted

```
LectureUnit deleted
    │
    ▼
LectureUnitService.removeLectureUnit()
    │
    ▼
LectureContentProcessingService.cancelProcessing(unitId)
    │
    ├──► Mark processing state as cancelled
    │
    ▼
Delete LectureUnitProcessingState
    │
    ▼
[Cascade deletes LectureTranscription]
    │
    ▼
irisLectureApi.deleteLectureFromPyrisDB() [existing]
```

### Flow 4: Retry After Failure

```
User clicks "Retry Processing" button on FAILED unit
    │
    ▼
POST /api/lecture/.../processing/retry
    │
    ▼
LectureContentProcessingService.retryFailedProcessing(unitId)
    │
    ├──► Reset retryCount to 0
    ├──► Reset phase to IDLE
    │
    ▼
triggerProcessing(unit)
    │
    ▼
[Same flow as Flow 1]
```

## Migration Plan

### Database Migration

```xml
<!-- 20251210_add_processing_state.xml -->
<changeSet id="20251210120000" author="artemis">
    <createTable tableName="lecture_unit_processing_state">
        <column name="id" type="BIGINT" autoIncrement="true">
            <constraints primaryKey="true"/>
        </column>
        <column name="lecture_unit_id" type="BIGINT">
            <constraints unique="true" references="lecture_unit(id)"
                         foreignKeyName="fk_processing_state_unit"/>
        </column>
        <column name="phase" type="VARCHAR(50)" defaultValue="IDLE"/>
        <column name="retry_count" type="INT" defaultValue="0"/>
        <column name="video_source_hash" type="VARCHAR(64)"/>
        <column name="attachment_version" type="INT"/>
        <column name="error_message" type="TEXT"/>
        <column name="last_updated" type="TIMESTAMP"/>
        <column name="started_at" type="TIMESTAMP"/>
    </createTable>
</changeSet>
```

### Existing Data Migration

For existing AttachmentVideoUnits:
1. Create processing state with phase=DONE if already ingested in Pyris
2. Create processing state with phase=IDLE if not ingested
3. Trigger processing for IDLE units (can be done gradually)

## Testing Strategy

### Unit Tests
- `LectureContentProcessingServiceTest` - Core processing logic
- `LectureContentProcessingSchedulerTest` - Recovery and scheduling
- Test all state transitions

### Integration Tests
- End-to-end flow: create unit → transcribe → ingest
- Video URL change handling
- Unit deletion handling
- Node restart recovery
- Retry after failure

### Manual Testing
- Verify client UI shows correct states
- Verify "Retry" button works
- Verify automatic processing triggers

## Rollback Plan

If issues arise:
1. Feature flag to disable automatic processing
2. Re-enable manual trigger buttons via configuration
3. Endpoints kept but marked deprecated (not deleted immediately)

## Timeline Estimate

| Phase | Description | Complexity |
|-------|-------------|------------|
| 1 | Database and Domain Models | Low |
| 2 | Core Processing Service | High |
| 3 | Integration Points | Medium |
| 4 | Remove Client-Side Triggers | Medium |
| 5 | Server-Side API Cleanup | Low |
| 6 | Status Display | Medium |
| 7 | Recovery and Resilience | Medium |

## Success Criteria

1. ✅ Users don't need to manually trigger transcription
2. ✅ Users don't need to manually trigger ingestion
3. ✅ Video URL changes automatically restart processing
4. ✅ Unit deletion properly cleans up
5. ✅ Node restart doesn't lose processing progress
6. ✅ Clear status display for instructors
7. ✅ Manual retry available for failed processing
8. ✅ Works for new units, edited units, imported units, and auto-split units
