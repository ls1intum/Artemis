package de.tum.in.www1.artemis.science;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.science.ScienceEventType;
import de.tum.in.www1.artemis.repository.science.ScienceEventRepository;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggleService;
import de.tum.in.www1.artemis.web.rest.dto.science.ScienceEventDTO;

class ScienceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private ScienceEventRepository scienceEventRepository;

    @Autowired
    private FeatureToggleService featureToggleService;

    @BeforeEach
    void enableFeatureToggle() {
        featureToggleService.enableFeature(Feature.Science);
    }

    @AfterEach
    void disableFeatureToggle() {
        featureToggleService.disableFeature(Feature.Science);
    }

    private void sendPutRequest(ScienceEventDTO event) throws Exception {
        request.put("/api/science", event, HttpStatus.OK);
    }

    @ParameterizedTest
    @EnumSource(ScienceEventType.class)
    @WithMockUser()
    void testLogEventOfType(ScienceEventType type) throws Exception {
        final var event = new ScienceEventDTO(type, 3L);
        sendPutRequest(event);
        final var loggedEvents = scienceEventRepository.findAllByType(type);
        assertThat(loggedEvents).hasSize(1);
        final var loggedEvent = loggedEvents.stream().findFirst().get();
        final var principal = SecurityContextHolder.getContext().getAuthentication().getName();
        assertThat(loggedEvent.getIdentity()).isEqualTo(principal);
        assertThat(loggedEvent.getType()).isEqualTo(type);
        assertThat(loggedEvent.getResourceId()).isEqualTo(event.resourceId());
    }
}
