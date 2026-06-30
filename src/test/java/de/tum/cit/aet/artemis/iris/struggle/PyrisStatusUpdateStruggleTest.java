package de.tum.cit.aet.artemis.iris.struggle;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.iris.service.AutonomousTutorService;
import de.tum.cit.aet.artemis.iris.service.IrisCompetencyGenerationService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisStatusUpdateService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleInterventionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.StruggleInterventionJob;
import de.tum.cit.aet.artemis.iris.service.session.IrisChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisStruggleInterventionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisTutorSuggestionSessionService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisWebsocketService;
import de.tum.cit.aet.artemis.lecture.api.ProcessingStateCallbackApi;

/**
 * Plain Mockito unit test for the idempotent struggle-intervention dispatch in
 * {@link PyrisStatusUpdateService#handleStatusUpdate(StruggleInterventionJob, PyrisStruggleInterventionStatusUpdateDTO)}.
 * <p>
 * The scenarios encode the exactly-once contract (spec §5.4 / §11):
 * <ol>
 * <li>decision callback ({@code action != null}): job removed, decision dispatched, marker released last;</li>
 * <li>non-decision keep-alive ({@code action == null}, an {@code IN_PROGRESS} stage): job updated, marker held;</li>
 * <li>non-decision terminal ({@code action == null}, an {@code ERROR} stage): job removed, marker released;</li>
 * <li>non-decision with empty stages: vacuously-terminal guard holds the job for the real decision callback.</li>
 * </ol>
 */
class PyrisStatusUpdateStruggleTest {

    private PyrisJobService pyrisJobService;

    private IrisStruggleInterventionService irisStruggleInterventionService;

    private PyrisStatusUpdateService service;

    // jobId "t", courseId 7, exerciseId 42, userId 3 (decide / legacy intent)
    private final StruggleInterventionJob job = new StruggleInterventionJob("t", 7L, 42L, 3L, null, null, null, null);

    // A11 mode jobs: intent carries the routing key; action is null on the response (deadlock guard)
    private final StruggleInterventionJob confirmCloseJob = new StruggleInterventionJob("cc", 7L, 42L, 3L, "confirm_close", "ep-cc", "progress", null);

    private final StruggleInterventionJob staleCheckJob = new StruggleInterventionJob("sc", 7L, 42L, 3L, "stale_check", "ep-sc", null, null);

    @BeforeEach
    void setUp() {
        pyrisJobService = mock(PyrisJobService.class);
        irisStruggleInterventionService = mock(IrisStruggleInterventionService.class);

        service = new PyrisStatusUpdateService(pyrisJobService, mock(IrisChatSessionService.class), mock(IrisCompetencyGenerationService.class),
                mock(IrisTutorSuggestionSessionService.class), mock(AutonomousTutorService.class), Optional.<ProcessingStateCallbackApi>empty(), mock(IrisWebsocketService.class),
                irisStruggleInterventionService);
    }

    @Test
    void decisionCallback_removesJobThenDispatchesThenReleasesMarker() {
        var update = new PyrisStruggleInterventionStatusUpdateDTO("hint", "active", 0.8, "FM", List.of(), List.of(), null, null, null, null, null, null, null, null);

        service.handleStatusUpdate(job, update);

        var inOrder = inOrder(pyrisJobService, irisStruggleInterventionService);
        inOrder.verify(pyrisJobService).removeJob(job);                                 // remove the JOB-MAP entry FIRST so the trailing duplicate 403s
        inOrder.verify(irisStruggleInterventionService).handleDecision(job, update);
        inOrder.verify(pyrisJobService).releaseStruggleInFlightMarker("t", 3L, 42L);    // marker freed only AFTER handleDecision (jobId, userId, exerciseId)
    }

    @Test
    void nonDecisionCallback_keepAlive_holdsMarker() {
        var inProgress = new PyrisStageDTO("Thinking", 10, PyrisStageState.IN_PROGRESS, null, false, null);
        var update = new PyrisStruggleInterventionStatusUpdateDTO(null, null, null, null, List.of(inProgress), List.of(), null, null, null, null, null, null, null, null);

        service.handleStatusUpdate(job, update);

        verify(irisStruggleInterventionService, never()).handleDecision(any(), any());
        verify(pyrisJobService, never()).removeJob(any());
        verify(pyrisJobService, never()).releaseStruggleInFlightMarker(anyString(), anyLong(), anyLong());   // still in flight → marker held
        verify(pyrisJobService).updateJob(job);
    }

    @Test
    void nonDecisionTerminalCallback_releasesMarker() {
        var errorStage = new PyrisStageDTO("Error", 10, PyrisStageState.ERROR, null, false, null);
        var update = new PyrisStruggleInterventionStatusUpdateDTO(null, null, null, null, List.of(errorStage), List.of(), null, null, null, null, null, null, null, null);

        service.handleStatusUpdate(job, update);

        verify(irisStruggleInterventionService, never()).handleDecision(any(), any());
        verify(pyrisJobService).removeJob(job);
        verify(pyrisJobService).releaseStruggleInFlightMarker("t", 3L, 42L);
    }

    @Test
    void nonDecisionCallback_emptyStages_doesNotTerminateNorReleaseMarker() {
        // An empty stages list is vacuously "all terminal"; the !isEmpty() guard must NOT let it drop the job,
        // otherwise the real decision callback would 403 and the intervention would be silently lost.
        var update = new PyrisStruggleInterventionStatusUpdateDTO(null, null, null, null, List.of(), List.of(), null, null, null, null, null, null, null, null);

        service.handleStatusUpdate(job, update);

        verify(irisStruggleInterventionService, never()).handleDecision(any(), any());
        verify(pyrisJobService, never()).removeJob(any());
        verify(pyrisJobService, never()).updateJob(any());
        verify(pyrisJobService, never()).releaseStruggleInFlightMarker(anyString(), anyLong(), anyLong());
    }

    @Test
    void confirmClose_withNullAction_removesJobAndReleasesMarker() {
        // A11 deadlock fix: confirm_close responses carry action=null. The old gate (action != null) would
        // never clear the in-flight marker, deadlocking the slot. The fix routes by job.intent() first.
        // action=null is the real-world response shape for confirm_close.
        var update = new PyrisStruggleInterventionStatusUpdateDTO(null, null, null, null, List.of(), List.of(), null, null, null, true, "Nice work!", "Done", null, null);

        service.handleStatusUpdate(confirmCloseJob, update);

        var inOrder = inOrder(pyrisJobService, irisStruggleInterventionService);
        inOrder.verify(pyrisJobService).removeJob(confirmCloseJob);
        inOrder.verify(irisStruggleInterventionService).handleConfirmClose(eq(confirmCloseJob), any());
        inOrder.verify(pyrisJobService).releaseStruggleInFlightMarker("cc", 3L, 42L);
        verify(irisStruggleInterventionService, never()).handleDecision(any(), any());
    }

    @Test
    void staleCheck_withNullAction_removesJobAndReleasesMarker() {
        // A11 deadlock fix: stale_check responses carry action=null. Same root cause as confirm_close above.
        var update = new PyrisStruggleInterventionStatusUpdateDTO(null, null, null, null, List.of(), List.of(), null, null, null, null, null, null, true, "Still stuck?");

        service.handleStatusUpdate(staleCheckJob, update);

        var inOrder = inOrder(pyrisJobService, irisStruggleInterventionService);
        inOrder.verify(pyrisJobService).removeJob(staleCheckJob);
        inOrder.verify(irisStruggleInterventionService).handleStaleCheck(eq(staleCheckJob), any());
        inOrder.verify(pyrisJobService).releaseStruggleInFlightMarker("sc", 3L, 42L);
        verify(irisStruggleInterventionService, never()).handleDecision(any(), any());
    }

    @Test
    void confirmClose_intermediateFrame_doesNotDispatch_holdsMarker() {
        // Terminal-frame gating fix: the confirm_close terminal frame carries resolved != null. A leading IN_PROGRESS
        // frame (resolved == null) must NOT fire the handler - dispatching early would remove the job so the REAL
        // terminal frame would 403 and the close would be silently lost. Intermediate frame -> keep-alive, marker held.
        var inProgress = new PyrisStageDTO("Thinking", 10, PyrisStageState.IN_PROGRESS, null, false, null);
        var update = new PyrisStruggleInterventionStatusUpdateDTO(null, null, null, null, List.of(inProgress), List.of(), null, null, null, null, null, null, null, null);

        service.handleStatusUpdate(confirmCloseJob, update);

        verify(irisStruggleInterventionService, never()).handleConfirmClose(any(), any());
        verify(pyrisJobService, never()).removeJob(any());
        verify(pyrisJobService, never()).releaseStruggleInFlightMarker(anyString(), anyLong(), anyLong());
        verify(pyrisJobService).updateJob(confirmCloseJob);   // kept alive until the terminal frame arrives
    }

    @Test
    void confirmClose_errorFrame_releasesMarkerWithoutDispatch() {
        // A Pyris ERROR stage with no resolved field is terminal but is not a real close: release the marker (so the
        // slot does not leak) without dispatching the close handler.
        var errorStage = new PyrisStageDTO("Error", 10, PyrisStageState.ERROR, null, false, null);
        var update = new PyrisStruggleInterventionStatusUpdateDTO(null, null, null, null, List.of(errorStage), List.of(), null, null, null, null, null, null, null, null);

        service.handleStatusUpdate(confirmCloseJob, update);

        verify(irisStruggleInterventionService, never()).handleConfirmClose(any(), any());
        verify(pyrisJobService).removeJob(confirmCloseJob);
        verify(pyrisJobService).releaseStruggleInFlightMarker("cc", 3L, 42L);
    }

    @Test
    void confirmClose_terminalFrame_dispatchesExactlyOnce() {
        // The terminal frame (resolved != null) even when accompanied by an in-progress-then-done stage list must
        // dispatch the close handler exactly once.
        var done = new PyrisStageDTO("Done", 10, PyrisStageState.DONE, null, false, null);
        var update = new PyrisStruggleInterventionStatusUpdateDTO(null, null, null, null, List.of(done), List.of(), null, null, null, false, null, null, null, null);

        service.handleStatusUpdate(confirmCloseJob, update);

        verify(irisStruggleInterventionService).handleConfirmClose(eq(confirmCloseJob), any());
        verify(pyrisJobService).removeJob(confirmCloseJob);
        verify(pyrisJobService).releaseStruggleInFlightMarker("cc", 3L, 42L);
    }

    @Test
    void staleCheck_intermediateFrame_doesNotDispatch_holdsMarker() {
        // Same terminal-frame gating as confirm_close: the stale_check terminal frame carries ask != null. A leading
        // IN_PROGRESS frame (ask == null) must NOT fire the handler. Intermediate frame -> keep-alive, marker held.
        var inProgress = new PyrisStageDTO("Thinking", 10, PyrisStageState.IN_PROGRESS, null, false, null);
        var update = new PyrisStruggleInterventionStatusUpdateDTO(null, null, null, null, List.of(inProgress), List.of(), null, null, null, null, null, null, null, null);

        service.handleStatusUpdate(staleCheckJob, update);

        verify(irisStruggleInterventionService, never()).handleStaleCheck(any(), any());
        verify(pyrisJobService, never()).removeJob(any());
        verify(pyrisJobService, never()).releaseStruggleInFlightMarker(anyString(), anyLong(), anyLong());
        verify(pyrisJobService).updateJob(staleCheckJob);
    }

    @Test
    void staleCheck_errorFrame_releasesMarkerWithoutDispatch() {
        var errorStage = new PyrisStageDTO("Error", 10, PyrisStageState.ERROR, null, false, null);
        var update = new PyrisStruggleInterventionStatusUpdateDTO(null, null, null, null, List.of(errorStage), List.of(), null, null, null, null, null, null, null, null);

        service.handleStatusUpdate(staleCheckJob, update);

        verify(irisStruggleInterventionService, never()).handleStaleCheck(any(), any());
        verify(pyrisJobService).removeJob(staleCheckJob);
        verify(pyrisJobService).releaseStruggleInFlightMarker("sc", 3L, 42L);
    }

    @Test
    void staleCheck_terminalFrame_dispatchesExactlyOnce() {
        var done = new PyrisStageDTO("Done", 10, PyrisStageState.DONE, null, false, null);
        var update = new PyrisStruggleInterventionStatusUpdateDTO(null, null, null, null, List.of(done), List.of(), null, null, null, null, null, null, false, null);

        service.handleStatusUpdate(staleCheckJob, update);

        verify(irisStruggleInterventionService).handleStaleCheck(eq(staleCheckJob), any());
        verify(pyrisJobService).removeJob(staleCheckJob);
        verify(pyrisJobService).releaseStruggleInFlightMarker("sc", 3L, 42L);
    }
}
