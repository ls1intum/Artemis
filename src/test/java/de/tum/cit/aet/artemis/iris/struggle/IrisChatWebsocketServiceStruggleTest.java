package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.iris.AbstractIrisIntegrationTest;
import de.tum.cit.aet.artemis.iris.dto.StruggleInterventionEventDTO;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;

class IrisChatWebsocketServiceStruggleTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "struggleevent";

    @Autowired
    private IrisChatWebsocketService irisChatWebsocketService;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
    }

    @Test
    void sendsStruggleEventToPerUserTopic() {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        irisChatWebsocketService.sendStruggleEvent(user, new StruggleInterventionEventDTO(42, "ambient", "Re-check the logic.", null, 0.7));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(websocketMessagingService, timeout(2000)).sendMessageToUser(eq(user.getLogin()), eq("/topic/iris/struggle-intervention"), payload.capture());
        var event = (StruggleInterventionEventDTO) payload.getValue();
        assertThat(event.action()).isEqualTo("ambient");
        assertThat(event.message()).contains("logic");
        assertThat(event.confidence()).isEqualTo(0.7);
    }
}
