package de.tum.cit.aet.artemis.lecture;

import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.lecture.service.LectureTranscriptionService;
import de.tum.cit.aet.artemis.nebula.service.NebulaConnectionService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Base class for lecture transcription tests.
 * Uses the SAME bean overrides for ALL lecture transcription tests to keep one Spring context
 * and avoid multiple server starts.
 * Enables Nebula only for these specific tests (does not affect other independent tests).
 */
@TestPropertySource(properties = { "artemis.nebula.enabled=true" })
public abstract class AbstractLectureTranscriptionIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    // Shared spy for all lecture transcription tests to ensure same context
    @MockitoSpyBean
    protected LectureTranscriptionService lectureTranscriptionService;

    // Mock NebulaConnectionService to prevent real bean initialization when Nebula is enabled
    @MockitoBean
    protected NebulaConnectionService nebulaConnectionService;

    // Mock RestTemplate for Nebula API calls - directly mocked with @MockitoBean
    @MockitoBean(name = "nebulaRestTemplate")
    protected RestTemplate nebulaRestTemplate;
}
