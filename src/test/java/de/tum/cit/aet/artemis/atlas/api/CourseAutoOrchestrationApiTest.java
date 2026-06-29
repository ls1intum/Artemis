package de.tum.cit.aet.artemis.atlas.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.atlas.domain.competency.CourseAutoOrchestrationConfiguration;
import de.tum.cit.aet.artemis.atlas.repository.CourseAutoOrchestrationConfigurationRepository;
import de.tum.cit.aet.artemis.atlas.service.ContentChangeAccumulatorService;

/**
 * Unit tests for the {@link CourseAutoOrchestrationApi} facade: it must load the managed configuration
 * via the dedicated repository query and forward the disable-time flush to the accumulator.
 */
class CourseAutoOrchestrationApiTest {

    private CourseAutoOrchestrationConfigurationRepository configurationRepository;

    private ContentChangeAccumulatorService contentChangeAccumulatorService;

    private CourseAutoOrchestrationApi api;

    @BeforeEach
    void setUp() {
        configurationRepository = mock(CourseAutoOrchestrationConfigurationRepository.class);
        contentChangeAccumulatorService = mock(ContentChangeAccumulatorService.class);
        api = new CourseAutoOrchestrationApi(configurationRepository, contentChangeAccumulatorService);
    }

    @Test
    void findConfiguration_loadsManagedEntityViaDedicatedQuery() {
        var configuration = new CourseAutoOrchestrationConfiguration();
        when(configurationRepository.findByCourseId(42L)).thenReturn(Optional.of(configuration));

        Optional<CourseAutoOrchestrationConfiguration> result = api.findConfiguration(42L);

        assertThat(result).containsSame(configuration);
        verify(configurationRepository).findByCourseId(42L);
        verifyNoInteractions(contentChangeAccumulatorService);
    }

    @Test
    void findConfiguration_returnsEmptyWhenNoConfigurationRow() {
        when(configurationRepository.findByCourseId(7L)).thenReturn(Optional.empty());

        assertThat(api.findConfiguration(7L)).isEmpty();
    }

    @Test
    void flushBufferedContentChanges_delegatesToAccumulator() {
        api.flushBufferedContentChanges(99L);

        verify(contentChangeAccumulatorService).flush(99L);
        verifyNoInteractions(configurationRepository);
    }
}
