package de.tum.cit.aet.artemis.atlas.api;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseAutoOrchestrationConfiguration;
import de.tum.cit.aet.artemis.atlas.repository.CourseAutoOrchestrationConfigurationRepository;
import de.tum.cit.aet.artemis.atlas.service.ContentChangeAccumulatorService;

/**
 * API facade for a course's auto-orchestration configuration. Keeps the {@code course} module's only
 * {@code atlas} touchpoint behind {@code *.api}: the course update flow loads the managed configuration
 * (so it can be mutated in place rather than orphaning the existing row) and flushes buffered content
 * changes when auto-orchestration is disabled.
 */
@Controller
@Conditional(AtlasEnabled.class)
@Lazy
public class CourseAutoOrchestrationApi extends AbstractAtlasApi {

    private final CourseAutoOrchestrationConfigurationRepository courseAutoOrchestrationConfigurationRepository;

    private final ContentChangeAccumulatorService contentChangeAccumulatorService;

    public CourseAutoOrchestrationApi(CourseAutoOrchestrationConfigurationRepository courseAutoOrchestrationConfigurationRepository,
            ContentChangeAccumulatorService contentChangeAccumulatorService) {
        this.courseAutoOrchestrationConfigurationRepository = courseAutoOrchestrationConfigurationRepository;
        this.contentChangeAccumulatorService = contentChangeAccumulatorService;
    }

    /**
     * Loads the managed auto-orchestration configuration of a course so it can be attached to the
     * course being updated and mutated in place by {@code CourseUpdateDTO#applyTo}.
     *
     * @param courseId the course to resolve the configuration for
     * @return the managed configuration, or empty when the course has no configuration row
     */
    public Optional<CourseAutoOrchestrationConfiguration> findConfiguration(long courseId) {
        return courseAutoOrchestrationConfigurationRepository.findByCourseId(courseId);
    }

    /**
     * Drops a course's buffered content changes. Called when a course disables auto-orchestration so a
     * stale batch buffered while it was enabled cannot fire (e.g. on re-enable within the debounce
     * window or a scheduler tick before the change propagates).
     *
     * @param courseId the course whose buffered content changes should be dropped
     */
    public void flushBufferedContentChanges(long courseId) {
        contentChangeAccumulatorService.flush(courseId);
    }
}
