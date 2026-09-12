package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.iris.service.AutonomousTutorService;
import de.tum.cit.aet.artemis.iris.service.IrisCompetencyGenerationService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ChatJob;
import de.tum.cit.aet.artemis.iris.service.session.IrisChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisStruggleInterventionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisStruggleTriggerService;
import de.tum.cit.aet.artemis.iris.service.session.IrisTutorSuggestionSessionService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisWebsocketService;

class PyrisStatusUpdateServiceChatTest {

    private PyrisJobService pyrisJobService;

    private IrisChatSessionService irisChatSessionService;

    private PyrisStatusUpdateService service;

    @BeforeEach
    void setUp() {
        pyrisJobService = mock(PyrisJobService.class);
        irisChatSessionService = mock(IrisChatSessionService.class);
        service = new PyrisStatusUpdateService(pyrisJobService, irisChatSessionService, mock(IrisCompetencyGenerationService.class), mock(IrisTutorSuggestionSessionService.class),
                mock(AutonomousTutorService.class), Optional.empty(), mock(IrisWebsocketService.class), mock(IrisStruggleInterventionService.class),
                mock(IrisStruggleTriggerService.class));
    }

    @Test
    void partialChatStatusUpdateIsRelayedWithoutUpdatingJob() {
        var job = new ChatJob("run-1", 1L, 2L, 3L, null, null, null);
        var statusUpdate = new PyrisChatStatusUpdateDTO(null, PyrisRunState.RUNNING, null, null, null, null, null, null, "partial", 4, null, null);

        service.handleStatusUpdate(job, statusUpdate);

        verify(irisChatSessionService).handlePartialStatusUpdate(job, statusUpdate);
        verify(irisChatSessionService, never()).handleStatusUpdate(job, statusUpdate);
        verifyNoInteractions(pyrisJobService);
    }

    @Test
    void nonPartialChatStatusUpdateUsesNormalResultPath() {
        var job = new ChatJob("run-1", 1L, 2L, 3L, null, null, null);
        var statusUpdate = new PyrisChatStatusUpdateDTO("result", PyrisRunState.FINISHED, null, null, null, null, null, null, null, null, null, null);
        when(irisChatSessionService.handleStatusUpdate(job, statusUpdate)).thenReturn(job);

        service.handleStatusUpdate(job, statusUpdate);

        verify(irisChatSessionService).handleStatusUpdate(job, statusUpdate);
        verify(irisChatSessionService, never()).handlePartialStatusUpdate(job, statusUpdate);
        verify(pyrisJobService).removeJob(job);
    }
}
