package de.tum.cit.aet.artemis.iris.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.iris.dto.IrisCommandAckDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.IrisCommandCoordinationService;

/**
 * Unit tests for {@link IrisCommandWebsocketController}. The controller is the trust boundary for client acks, so the focus is on rejecting payloads that the coordination service
 * cannot handle (a null correlation id would fail the correlation-id lookup in the Hazelcast topic listener on every node).
 */
@ExtendWith(MockitoExtension.class)
class IrisCommandWebsocketControllerTest {

    @Mock
    private IrisCommandCoordinationService coordinationService;

    @Mock
    private Principal principal;

    @InjectMocks
    private IrisCommandWebsocketController controller;

    @Test
    void acknowledgeCommand_forwardsWellFormedAck() {
        var ack = new IrisCommandAckDTO(UUID.fromString("00000000-0000-0000-0000-000000000001").toString(), true);
        when(principal.getName()).thenReturn("student1");

        controller.acknowledgeCommand(ack, principal);

        verify(coordinationService).handleAck(eq(ack), eq("student1"));
    }

    @Test
    void acknowledgeCommand_withoutCorrelationIdIsDropped() {
        controller.acknowledgeCommand(new IrisCommandAckDTO(null, true), principal);

        verifyNoInteractions(coordinationService);
    }

    @Test
    void acknowledgeCommand_withNullPayloadIsDropped() {
        // An empty or null STOMP body deserializes to a null payload; it must not be dereferenced.
        controller.acknowledgeCommand(null, principal);

        verifyNoInteractions(coordinationService);
    }

}
