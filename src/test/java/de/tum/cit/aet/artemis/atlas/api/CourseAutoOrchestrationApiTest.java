package de.tum.cit.aet.artemis.atlas.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.atlas.service.ContentChangeAccumulatorService;

/**
 * Unit tests for the {@link CourseAutoOrchestrationApi} facade: it must forward the disable-time flush to
 * the accumulator. Loading the configuration is intentionally not part of this facade — see its javadoc.
 */
class CourseAutoOrchestrationApiTest {

    private ContentChangeAccumulatorService contentChangeAccumulatorService;

    private CourseAutoOrchestrationApi api;

    @BeforeEach
    void setUp() {
        contentChangeAccumulatorService = mock(ContentChangeAccumulatorService.class);
        api = new CourseAutoOrchestrationApi(contentChangeAccumulatorService);
    }

    @Test
    void flushBufferedContentChanges_delegatesToAccumulator() {
        api.flushBufferedContentChanges(99L);

        verify(contentChangeAccumulatorService).flush(99L);
    }
}
