package de.tum.cit.aet.artemis.iris.struggle;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;

import de.tum.cit.aet.artemis.admin.domain.LLMRequest;
import de.tum.cit.aet.artemis.iris.service.AutonomousTutorService;
import de.tum.cit.aet.artemis.iris.service.IrisCompetencyGenerationService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisStatusUpdateService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleInterventionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.StruggleInterventionJob;
import de.tum.cit.aet.artemis.iris.service.session.IrisChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisStruggleInterventionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisStruggleTriggerService;
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
 * <li>non-decision keep-alive ({@code action == null}, run state {@code RUNNING}): job updated, marker held;</li>
 * <li>non-decision terminal ({@code action == null}, run state {@code FAILED}): job removed, marker released;</li>
 * <li>non-decision without a run state: the null guard holds the job for the real decision callback.</li>
 * </ol>
 */
class PyrisStatusUpdateStruggleTest {

    private PyrisJobService pyrisJobService;

    private IrisStruggleInterventionService irisStruggleInterventionService;

    private IrisStruggleTriggerService irisStruggleTriggerService;

    private PyrisStatusUpdateService service;

    // jobId "t", courseId 7, exerciseId 42, userId 3 (decide / legacy intent)
    private final StruggleInterventionJob job = new StruggleInterventionJob("t", 7L, 42L, 3L, null, null, null, null, null);

    // A11 mode jobs: intent carries the routing key; action is null on the response (deadlock guard)
    private final StruggleInterventionJob confirmCloseJob = new StruggleInterventionJob("cc", 7L, 42L, 3L, "confirm_close", "ep-cc", "progress", null, null);

    @BeforeEach
    void setUp() {
        pyrisJobService = mock(PyrisJobService.class);
        irisStruggleInterventionService = mock(IrisStruggleInterventionService.class);
        irisStruggleTriggerService = mock(IrisStruggleTriggerService.class);

        service = new PyrisStatusUpdateService(pyrisJobService, mock(IrisChatSessionService.class), mock(IrisCompetencyGenerationService.class),
                mock(IrisTutorSuggestionSessionService.class), mock(AutonomousTutorService.class), Optional.<ProcessingStateCallbackApi>empty(), mock(IrisWebsocketService.class),
                irisStruggleInterventionService, irisStruggleTriggerService);

        // The struggle handler claims the callback under the job lock: it runs the body inside runWithJobLock and
        // re-reads the map entry, dropping the callback when the job is already gone. Both are collaborator calls,
        // so the mock has to model them - run the supplier inline, and hand the job back by id.
        when(pyrisJobService.runWithJobLock(anyString(), any())).thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        when(pyrisJobService.getJob("t")).thenReturn(job);
        when(pyrisJobService.getJob("cc")).thenReturn(confirmCloseJob);
    }

    @Test
    void everyFrameOfAClaimedCallbackIsAccountedFor() {
        // The pipeline's LLM spend used to be invisible: the callback carried tokens and nothing read them. Recording
        // runs before the frame is routed, so a decision, a keep-alive, a failure and a close are all accounted for,
        // whichever branch below goes on to claim them.
        var tokens = List.of(new LLMRequest("gpt-4", 100, 0.5f, 40, 1.5f, "struggle-intervention"));
        var decision = new PyrisStruggleInterventionStatusUpdateDTO("hint", "active", 0.8, null, PyrisRunState.FINISHED, null, tokens, null, null, null, null, null, null);
        var keepAlive = new PyrisStruggleInterventionStatusUpdateDTO(null, null, null, null, PyrisRunState.RUNNING, null, tokens, null, null, null, null, null, null);
        var failed = new PyrisStruggleInterventionStatusUpdateDTO(null, null, null, null, PyrisRunState.FAILED, null, tokens, null, null, null, null, null, null);
        var close = new PyrisStruggleInterventionStatusUpdateDTO(null, null, null, null, PyrisRunState.FINISHED, null, tokens, null, null, null, true, null, null);

        service.handleStatusUpdate(job, decision);
        service.handleStatusUpdate(job, keepAlive);
        service.handleStatusUpdate(job, failed);
        service.handleStatusUpdate(confirmCloseJob, close);

        verify(irisStruggleInterventionService).recordTokenUsage(job, decision);
        verify(irisStruggleInterventionService).recordTokenUsage(job, keepAlive);
        verify(irisStruggleInterventionService).recordTokenUsage(job, failed);
        verify(irisStruggleInterventionService).recordTokenUsage(confirmCloseJob, close);
    }

    @Test
    void accountingHappensBeforeTheFrameIsRouted() {
        // Ordering is the whole reason the call sits in the dispatcher: routing removes the job and hands the frame
        // to a handler that may throw, and spend already reported by Pyris must be recorded either way.
        var tokens = List.of(new LLMRequest("gpt-4", 100, 0.5f, 40, 1.5f, "struggle-intervention"));
        var update = new PyrisStruggleInterventionStatusUpdateDTO("hint", "active", 0.8, null, PyrisRunState.FINISHED, null, tokens, null, null, null, null, null, null);

        service.handleStatusUpdate(job, update);

        var inOrder = inOrder(pyrisJobService, irisStruggleInterventionService, irisStruggleTriggerService);
        inOrder.verify(irisStruggleInterventionService).recordTokenUsage(job, update);
        inOrder.verify(pyrisJobService).removeJob(job);
        inOrder.verify(irisStruggleInterventionService).handleDecision(job, update);
    }

    @Test
    void aCallbackWhoseJobAnotherThreadAlreadyClaimedRecordsNothing() {
        // The job is gone, so another callback owns this run's side effects, including the spend it already recorded.
        when(pyrisJobService.getJob("t")).thenReturn(null);
        var tokens = List.of(new LLMRequest("gpt-4", 100, 0.5f, 40, 1.5f, "struggle-intervention"));
        var update = new PyrisStruggleInterventionStatusUpdateDTO("hint", "active", 0.8, null, PyrisRunState.FINISHED, null, tokens, null, null, null, null, null, null);

        service.handleStatusUpdate(job, update);

        verify(irisStruggleInterventionService, never()).recordTokenUsage(any(), any());
    }

    @Test
    void decisionCallback_removesJobThenDispatchesThenReleasesMarker() {
        var update = new PyrisStruggleInterventionStatusUpdateDTO("hint", "active", 0.8, "FM", PyrisRunState.FINISHED, null, List.of(), null, null, null, null, null, null);

        service.handleStatusUpdate(job, update);

        var inOrder = inOrder(pyrisJobService, irisStruggleInterventionService, irisStruggleTriggerService);
        inOrder.verify(pyrisJobService).removeJob(job);                                 // remove the JOB-MAP entry FIRST so the trailing duplicate 403s
        inOrder.verify(irisStruggleInterventionService).handleDecision(job, update);
        inOrder.verify(pyrisJobService).releaseStruggleInFlightMarker("t", 3L, 42L);    // marker freed only AFTER handleDecision (jobId, userId, exerciseId)
    }

    @Test
    void decisionCallback_whenHandleDecisionThrows_stillCompletesClientAndReleasesMarker() {
        // handleDecision emits its own silent frame on every deliberate drop, but an unexpected failure (e.g. a
        // lock-timeout DataAccessException while recording the ambient offer) escapes after the job was already
        // removed. Without the dispatcher completing the client, its in-flight decide would hang until timeout.
        var update = new PyrisStruggleInterventionStatusUpdateDTO("hint", "ambient", 0.8, null, PyrisRunState.FINISHED, null, List.of(), null, null, null, null, null, null);
        doThrow(new CannotAcquireLockException("deadlock")).when(irisStruggleInterventionService).handleDecision(job, update);

        service.handleStatusUpdate(job, update);

        var inOrder = inOrder(pyrisJobService, irisStruggleInterventionService, irisStruggleTriggerService);
        inOrder.verify(pyrisJobService).removeJob(job);
        inOrder.verify(irisStruggleInterventionService).handleDecision(job, update);
        inOrder.verify(irisStruggleTriggerService).emitTerminalCompletion(job);    // client completed despite the failure
        inOrder.verify(pyrisJobService).releaseStruggleInFlightMarker("t", 3L, 42L);    // marker still freed
    }

    @Test
    void confirmCloseCallback_whenHandlerThrows_stillCompletesClientAndReleasesMarker() {
        var update = new PyrisStruggleInterventionStatusUpdateDTO(null, null, null, null, PyrisRunState.FINISHED, null, List.of(), null, null, null, true, null, null);
        doThrow(new CannotAcquireLockException("deadlock")).when(irisStruggleInterventionService).handleConfirmClose(confirmCloseJob, update);

        service.handleStatusUpdate(confirmCloseJob, update);

        var inOrder = inOrder(pyrisJobService, irisStruggleInterventionService, irisStruggleTriggerService);
        inOrder.verify(pyrisJobService).removeJob(confirmCloseJob);
        inOrder.verify(irisStruggleInterventionService).handleConfirmClose(confirmCloseJob, update);
        inOrder.verify(irisStruggleTriggerService).emitTerminalCompletion(confirmCloseJob);
        inOrder.verify(pyrisJobService).releaseStruggleInFlightMarker("cc", 3L, 42L);
    }

    @Test
    void duplicateCallback_whoseJobIsAlreadyClaimed_isDropped() {
        // The resource authenticates a callback by reading the job BEFORE the handler runs, so a genuinely
        // concurrent duplicate can enter with the same job object. Under the lock the re-read finds nothing,
        // which is what stops the decision from being persisted and pushed twice.
        when(pyrisJobService.getJob("t")).thenReturn(null);
        var update = new PyrisStruggleInterventionStatusUpdateDTO("hint", "active", 0.8, "FM", PyrisRunState.FINISHED, null, List.of(), null, null, null, null, null, null);

        service.handleStatusUpdate(job, update);

        verify(irisStruggleInterventionService, never()).handleDecision(any(), any());
        verify(pyrisJobService, never()).removeJob(any());
        verify(pyrisJobService, never()).releaseStruggleInFlightMarker(anyString(), anyLong(), anyLong());
    }

    @Test
    void nonDecisionCallback_keepAlive_holdsMarker() {
        var update = new PyrisStruggleInterventionStatusUpdateDTO(null, null, null, null, PyrisRunState.RUNNING, null, List.of(), null, null, null, null, null, null);

        service.handleStatusUpdate(job, update);

        verify(irisStruggleInterventionService, never()).handleDecision(any(), any());
        verify(pyrisJobService, never()).removeJob(any());
        verify(pyrisJobService, never()).releaseStruggleInFlightMarker(anyString(), anyLong(), anyLong());   // still in flight → marker held
        verify(pyrisJobService).updateJob(job);
    }

    @Test
    void nonDecisionTerminalCallback_releasesMarker() {
        var update = new PyrisStruggleInterventionStatusUpdateDTO(null, null, null, null, PyrisRunState.FAILED, null, List.of(), null, null, null, null, null, null);

        service.handleStatusUpdate(job, update);

        verify(irisStruggleInterventionService, never()).handleDecision(any(), any());
        verify(pyrisJobService).removeJob(job);
        // The run ended with no decision, so the client's in-flight decide only clears via the completion frame.
        verify(irisStruggleTriggerService).emitTerminalCompletion(job);
        verify(pyrisJobService).releaseStruggleInFlightMarker("t", 3L, 42L);
    }

    @Test
    void nonDecisionCallback_missingRunState_doesNotTerminateNorReleaseMarker() {
        // A frame without a run state must NOT drop the job (this path deliberately does not use
        // resolveRunState, which maps a missing run state to FAILED), otherwise the real decision
        // callback would 403 and the intervention would be silently lost.
        var update = new PyrisStruggleInterventionStatusUpdateDTO(null, null, null, null, null, null, List.of(), null, null, null, null, null, null);

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
        var update = new PyrisStruggleInterventionStatusUpdateDTO(null, null, null, null, null, null, List.of(), null, null, null, true, "Nice work!", "Done");

        service.handleStatusUpdate(confirmCloseJob, update);

        var inOrder = inOrder(pyrisJobService, irisStruggleInterventionService, irisStruggleTriggerService);
        inOrder.verify(pyrisJobService).removeJob(confirmCloseJob);
        inOrder.verify(irisStruggleInterventionService).handleConfirmClose(eq(confirmCloseJob), any());
        inOrder.verify(pyrisJobService).releaseStruggleInFlightMarker("cc", 3L, 42L);
        verify(irisStruggleInterventionService, never()).handleDecision(any(), any());
    }

    @Test
    void confirmClose_intermediateFrame_doesNotDispatch_holdsMarker() {
        // Terminal-frame gating fix: the confirm_close terminal frame carries resolved != null. A leading IN_PROGRESS
        // frame (resolved == null) must NOT fire the handler - dispatching early would remove the job so the REAL
        // terminal frame would 403 and the close would be silently lost. Intermediate frame -> keep-alive, marker held.
        var update = new PyrisStruggleInterventionStatusUpdateDTO(null, null, null, null, PyrisRunState.RUNNING, null, List.of(), null, null, null, null, null, null);

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
        var update = new PyrisStruggleInterventionStatusUpdateDTO(null, null, null, null, PyrisRunState.FAILED, null, List.of(), null, null, null, null, null, null);

        service.handleStatusUpdate(confirmCloseJob, update);

        verify(irisStruggleInterventionService, never()).handleConfirmClose(any(), any());
        verify(pyrisJobService).removeJob(confirmCloseJob);
        // Same guarantee on the close mode: a failed run completes the client rather than leaving it in flight.
        verify(irisStruggleTriggerService).emitTerminalCompletion(confirmCloseJob);
        verify(pyrisJobService).releaseStruggleInFlightMarker("cc", 3L, 42L);
    }

    @Test
    void confirmClose_terminalFrame_dispatchesExactlyOnce() {
        // The terminal frame (resolved != null) even when accompanied by an in-progress-then-done stage list must
        // dispatch the close handler exactly once.
        var update = new PyrisStruggleInterventionStatusUpdateDTO(null, null, null, null, PyrisRunState.FINISHED, null, List.of(), null, null, null, false, null, null);

        service.handleStatusUpdate(confirmCloseJob, update);

        verify(irisStruggleInterventionService).handleConfirmClose(eq(confirmCloseJob), any());
        verify(pyrisJobService).removeJob(confirmCloseJob);
        verify(pyrisJobService).releaseStruggleInFlightMarker("cc", 3L, 42L);
    }
}
