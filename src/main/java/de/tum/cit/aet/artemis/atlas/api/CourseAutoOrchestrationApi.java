package de.tum.cit.aet.artemis.atlas.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.service.ContentChangeAccumulatorService;

/**
 * API facade for a course's auto-orchestration configuration. Keeps the {@code course} module's only
 * {@code atlas} touchpoint behind {@code *.api}: the course update flow flushes buffered content changes
 * when auto-orchestration is disabled.
 * <p>
 * Reading and writing the configuration itself deliberately does <em>not</em> go through this facade. This
 * bean is conditional on the Atlas module, whereas the settings must be preserved across course updates
 * regardless of whether Atlas is active. They therefore live on the (unconditional) {@code CourseConfiguration}
 * and are read and written by the course update flow directly.
 */
@Controller
@Conditional(AtlasEnabled.class)
@Lazy
public class CourseAutoOrchestrationApi extends AbstractAtlasApi {

    private final ContentChangeAccumulatorService contentChangeAccumulatorService;

    public CourseAutoOrchestrationApi(ContentChangeAccumulatorService contentChangeAccumulatorService) {
        this.contentChangeAccumulatorService = contentChangeAccumulatorService;
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
