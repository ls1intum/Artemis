package de.tum.cit.aet.artemis.iris.service.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.dto.IrisChatWebsocketDTO;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;

class IrisChatWebsocketServiceTest {

    private IrisWebsocketService websocketService;

    private IrisRateLimitService rateLimitService;

    private UserRepository userRepository;

    private IrisChatWebsocketService irisChatWebsocketService;

    @BeforeEach
    void setUp() {
        websocketService = mock(IrisWebsocketService.class);
        rateLimitService = mock(IrisRateLimitService.class);
        userRepository = mock(UserRepository.class);
        irisChatWebsocketService = new IrisChatWebsocketService(websocketService, rateLimitService, userRepository);
    }

    /**
     * Partial updates are emitted many times per streamed answer and never change the user's quota. Resolving the rate limit information can run the (potentially expensive)
     * LLM-response-count query, so the streaming hot path must not touch {@link IrisRateLimitService}. The final MESSAGE / status update is responsible for refreshing it.
     */
    @Test
    void partialUpdateDoesNotComputeRateLimitInformation() {
        var user = new User();
        user.setLogin("iris-student");
        var session = new IrisChatSession();
        session.setId(42L);
        session.setUserId(7L);
        when(userRepository.findByIdElseThrow(anyLong())).thenReturn(user);

        irisChatWebsocketService.sendPartialUpdate(session, "partial text", 3, "run-1");

        verifyNoInteractions(rateLimitService);

        var payloadCaptor = ArgumentCaptor.forClass(IrisChatWebsocketDTO.class);
        verify(websocketService).send(eq("iris-student"), eq("42"), payloadCaptor.capture());
        var payload = payloadCaptor.getValue();
        assertThat(payload.type()).isEqualTo(IrisChatWebsocketDTO.IrisWebsocketMessageType.PARTIAL);
        assertThat(payload.rateLimitInfo()).isNull();
        assertThat(payload.partialResult()).isEqualTo("partial text");
        assertThat(payload.partialSeq()).isEqualTo(3);
        assertThat(payload.runId()).isEqualTo("run-1");
    }
}
