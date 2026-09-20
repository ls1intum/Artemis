package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
 * {@link PyrisStatusUpdateService#handleStatusUpdate(StruggleInterventionJob, PyrisStruggleInterventionStatusUpdateDTO)}:
 * which frame claims the run, which keeps it alive, and in what order the job and the marker are released.
 */
class PyrisStatusUpdateStruggleTest {

    private PyrisJobService pyrisJobService;

    private IrisStruggleInterventionService irisStruggleInterventionService;

    private IrisStruggleTriggerService irisStruggleTriggerService;

    private PyrisStatusUpdateService service;

    // jobId "t", courseId 7, exerciseId 42, userId 3 (decide / legacy intent)
    private final StruggleInterventionJob job = new StruggleInterventionJob("t", 7L, 42L, 3L, null, null, null, null, null);

    // confirm_close jobs: intent carries the routing key; action is null on the response (deadlock guard)
    private final StruggleInterventionJob confirmCloseJob = new StruggleInterventionJob("cc", 7L, 42L, 3L, "confirm_close", "ep-cc", "progress", null, null);

    @BeforeEach
    void setUp() {
        pyrisJobService = mock(PyrisJobService.class);
        irisStruggleInterventionService = mock(IrisStruggleInterventionService.class);
        irisStruggleTriggerService = mock(IrisStruggleTriggerService.class);

        service = new PyrisStatusUpdateService(pyrisJobService, mock(IrisChatSessionService.class), mock(IrisCompetencyGenerationService.class),
                mock(IrisTutorSuggestionSessionService.class), mock(AutonomousTutorService.class), Optional.<ProcessingStateCallbackApi>empty(), mock(IrisWebsocketService.class),
                irisStruggleInterventionService, irisStruggleTriggerService);

        // The handler claims the callback under the job lock and re-reads the map entry, so the mock has to model
        // both: run the supplier inline, and hand the job back by id.
        when(pyrisJobService.runWithJobLock(anyString(), any())).thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        when(pyrisJobService.getJob("t")).thenReturn(job);
        when(pyrisJobService.getJob("cc")).thenReturn(confirmCloseJob);
    }

    @Test
    void everyFrameOfAClaimedCallbackIsAccountedFor() {
        // Recording runs before routing, so every frame is accounted for whichever branch claims it.
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
        // Routing removes the job and hands the frame to a handler that may throw, and the spend Pyris already
        // reported has to be recorded either way.
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
    void decisionCallback_refreshesMarkerThenRemovesJobThenDispatchesThenReleasesMarker() {
        var update = new PyrisStruggleInterventionStatusUpdateDTO("hint", "active", 0.8, "FM", PyrisRunState.FINISHED, null, List.of(), null, null, null, null, null, null);

        service.handleStatusUpdate(job, update);

        var inOrder = inOrder(pyrisJobService, irisStruggleInterventionService, irisStruggleTriggerService);
        // The refresh talks to the distributed store, so it goes first: a failure leaves the job in the map and
        // Pyris can retry instead of being 403'd on a dropped credential.
        inOrder.verify(pyrisJobService).refreshStruggleInFlightMarker("t", 3L, 42L);    // the handler runs on a full marker TTL, not on the run's remainder
        inOrder.verify(pyrisJobService).removeJob(job);                                 // then remove the JOB-MAP entry so the trailing duplicate 403s
        inOrder.verify(irisStruggleInterventionService).handleDecision(job, update);
        inOrder.verify(pyrisJobService).releaseStruggleInFlightMarker("t", 3L, 42L);    // marker freed only AFTER handleDecision (jobId, userId, exerciseId)
    }

    @Test
    void decisionCallback_whenTheMarkerRefreshFails_leavesTheJobRetriable() {
        // Nothing terminal happens before the re-stamp succeeds, or a retry 403s on a credential already gone.
        var update = new PyrisStruggleInterventionStatusUpdateDTO("hint", "active", 0.8, null, PyrisRunState.FINISHED, null, List.of(), null, null, null, null, null, null);
        doThrow(new CannotAcquireLockException("distributed store unavailable")).when(pyrisJobService).refreshStruggleInFlightMarker("t", 3L, 42L);

        assertThatThrownBy(() -> service.handleStatusUpdate(job, update)).isInstanceOf(CannotAcquireLockException.class);

        verify(pyrisJobService, never()).removeJob(job);
        verify(irisStruggleInterventionService, never()).handleDecision(any(), any());
        verify(pyrisJobService, never()).releaseStruggleInFlightMarker(anyString(), anyLong(), anyLong());
    }

    @Test
    void decisionCallback_whenHandleDecisionThrows_stillCompletesClientAndReleasesMarker() {
        // handleDecision completes its own deliberate drops, but an unexpected failure escapes after the job was
        // removed, and without the dispatcher completing the client its decide would hang until timeout.
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
        // The resource reads the job before the handler runs, so a concurrent duplicate enters with the same
        // object. Under the lock the re-read finds nothing, which stops a second persist and push.
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
        // Holding the marker is not enough: without the refresh it expires on the original TTL and a long run
        // loses the reservation it is still using.
        verify(pyrisJobService).refreshStruggleInFlightMarker("t", 3L, 42L);
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
        // Deliberately not resolveRunState, which maps a missing run state to FAILED: a frame without one must not
        // drop the job, or the real decision callback 403s.
        var update = new PyrisStruggleInterventionStatusUpdateDTO(null, null, null, null, null, null, List.of(), null, null, null, null, null, null);

        service.handleStatusUpdate(job, update);

        verify(irisStruggleInterventionService, never()).handleDecision(any(), any());
        verify(pyrisJobService, never()).removeJob(any());
        verify(pyrisJobService, never()).updateJob(any());
        verify(pyrisJobService, never()).releaseStruggleInFlightMarker(anyString(), anyLong(), anyLong());
    }

    @Test
    void confirmClose_withNullAction_removesJobAndReleasesMarker() {
        // action=null is the real response shape for confirm_close, so gating on it would deadlock the slot.
        var update = new PyrisStruggleInterventionStatusUpdateDTO(null, null, null, null, null, null, List.of(), null, null, null, true, "Nice work!", "Done");

        service.handleStatusUpdate(confirmCloseJob, update);

        var inOrder = inOrder(pyrisJobService, irisStruggleInterventionService, irisStruggleTriggerService);
        inOrder.verify(pyrisJobService).refreshStruggleInFlightMarker("cc", 3L, 42L);
        inOrder.verify(pyrisJobService).removeJob(confirmCloseJob);
        inOrder.verify(irisStruggleInterventionService).handleConfirmClose(eq(confirmCloseJob), any());
        inOrder.verify(pyrisJobService).releaseStruggleInFlightMarker("cc", 3L, 42L);
        verify(irisStruggleInterventionService, never()).handleDecision(any(), any());
    }

    @Test
    void confirmClose_intermediateFrame_doesNotDispatch_holdsMarker() {
        // Terminal-frame gating fix: the confirm_close terminal frame carries resolved != null. A leading RUNNING
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
        // A Pyris FAILED run state with no resolved field is terminal but is not a real close: release the marker (so the
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
