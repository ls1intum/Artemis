package de.tum.cit.aet.artemis.videosource.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.videosource.config.GocastEnabled;
import de.tum.cit.aet.artemis.videosource.service.GocastBindingService;

/**
 * API for TUM.Live course connections, used by other modules.
 */
@Controller
@Lazy
@Conditional(GocastEnabled.class)
public class GocastBindingApi implements AbstractApi {

    private static final Logger log = LoggerFactory.getLogger(GocastBindingApi.class);

    private final GocastBindingService gocastBindingService;

    public GocastBindingApi(GocastBindingService gocastBindingService) {
        this.gocastBindingService = gocastBindingService;
    }

    /**
     * Disconnects a course before it is deleted, so TUM.Live does not keep access that Artemis can no longer revoke. A failure does not stop the deletion; the
     * remaining access can still be revoked in the TUM.Live course settings.
     *
     * @param courseId the Artemis course that is being deleted
     */
    public void disconnectBeforeCourseDeletion(long courseId) {
        try {
            gocastBindingService.unlink(courseId);
        }
        catch (RuntimeException exception) {
            log.warn("Could not revoke the TUM.Live access of deleted course {}; revoke it in the TUM.Live course settings", courseId, exception);
        }
    }
}
