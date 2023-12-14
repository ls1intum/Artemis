package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.science.ScienceEventType;
import de.tum.in.www1.artemis.repository.science.ScienceEventRepository;
import de.tum.in.www1.artemis.web.rest.dto.science.ScienceEventDTO;

public class ScienceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "scienceintegration";

    @Autowired
    private ScienceEventRepository scienceEventRepository;

    private void sendPutRequest(ScienceEventDTO event) throws Exception {
        request.put("/api/science", event, HttpStatus.OK);
    }

    @ParameterizedTest
    @EnumSource(ScienceEventType.class)
    @WithMockUser()
    void testLogEventOfType(ScienceEventType type) throws Exception {
        final var event = new ScienceEventDTO(type, null);
        sendPutRequest(event);
        final var loggedEvents = scienceEventRepository.findAllByType(type);
        assertThat(loggedEvents).hasSize(1);
        final var loggedEvent = loggedEvents.stream().findFirst().get();
        final var principal = SecurityContextHolder.getContext().getAuthentication().getName();
        assertThat(loggedEvent.getIdentity()).isEqualTo(principal);
    }
}
