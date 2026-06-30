package de.tum.cit.aet.artemis.iris.struggle;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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

    // jobId "t", courseId 7, exerciseId 42, userId 3
    private final StruggleInterventionJob job = new StruggleInterventionJob("t", 7L, 42L, 3L, null, null, null, null);

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
        var update = new PyrisStruggleInterventionStatusUpdateDTO("hint", "active", 0.8, "FM", List.of(), List.of(), null, null, null);

        service.handleStatusUpdate(job, update);

        var inOrder = inOrder(pyrisJobService, irisStruggleInterventionService);
        inOrder.verify(pyrisJobService).removeJob(job);                                 // remove the JOB-MAP entry FIRST so the trailing duplicate 403s
        inOrder.verify(irisStruggleInterventionService).handleDecision(job, update);
        inOrder.verify(pyrisJobService).releaseStruggleInFlightMarker("t", 3L, 42L);    // marker freed only AFTER handleDecision (jobId, userId, exerciseId)
    }

    @Test
    void nonDecisionCallback_keepAlive_holdsMarker() {
        var inProgress = new PyrisStageDTO("Thinking", 10, PyrisStageState.IN_PROGRESS, null, false, null);
        var update = new PyrisStruggleInterventionStatusUpdateDTO(null, null, null, null, List.of(inProgress), List.of(), null, null, null);

        service.handleStatusUpdate(job, update);

        verify(irisStruggleInterventionService, never()).handleDecision(any(), any());
        verify(pyrisJobService, never()).removeJob(any());
        verify(pyrisJobService, never()).releaseStruggleInFlightMarker(anyString(), anyLong(), anyLong());   // still in flight → marker held
        verify(pyrisJobService).updateJob(job);
    }

    @Test
    void nonDecisionTerminalCallback_releasesMarker() {
        var errorStage = new PyrisStageDTO("Error", 10, PyrisStageState.ERROR, null, false, null);
        var update = new PyrisStruggleInterventionStatusUpdateDTO(null, null, null, null, List.of(errorStage), List.of(), null, null, null);

        service.handleStatusUpdate(job, update);

        verify(irisStruggleInterventionService, never()).handleDecision(any(), any());
        verify(pyrisJobService).removeJob(job);
        verify(pyrisJobService).releaseStruggleInFlightMarker("t", 3L, 42L);
    }

    @Test
    void nonDecisionCallback_emptyStages_doesNotTerminateNorReleaseMarker() {
        // An empty stages list is vacuously "all terminal"; the !isEmpty() guard must NOT let it drop the job,
        // otherwise the real decision callback would 403 and the intervention would be silently lost.
        var update = new PyrisStruggleInterventionStatusUpdateDTO(null, null, null, null, List.of(), List.of(), null, null, null);

        service.handleStatusUpdate(job, update);

        verify(irisStruggleInterventionService, never()).handleDecision(any(), any());
        verify(pyrisJobService, never()).removeJob(any());
        verify(pyrisJobService, never()).updateJob(any());
        verify(pyrisJobService, never()).releaseStruggleInFlightMarker(anyString(), anyLong(), anyLong());
    }
}
