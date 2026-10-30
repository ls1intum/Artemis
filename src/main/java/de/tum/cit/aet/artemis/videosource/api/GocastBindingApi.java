package de.tum.cit.aet.artemis.videosource.api;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.videosource.config.GocastEnabled;
import de.tum.cit.aet.artemis.videosource.service.GocastBindingService;
import de.tum.cit.aet.artemis.videosource.service.GocastRecordingService;
import de.tum.cit.aet.artemis.videosource.service.TumLivePlaylistLookup;

/**
 * API for TUM.Live course connections, used by other modules.
 */
@Controller
@Lazy
@Conditional(GocastEnabled.class)
public class GocastBindingApi implements AbstractApi {

    private static final Logger log = LoggerFactory.getLogger(GocastBindingApi.class);

    private final GocastBindingService gocastBindingService;

    private final GocastRecordingService gocastRecordingService;

    public GocastBindingApi(GocastBindingService gocastBindingService, GocastRecordingService gocastRecordingService) {
        this.gocastBindingService = gocastBindingService;
        this.gocastRecordingService = gocastRecordingService;
    }

    /**
     * Looks up the playlist of a TUM Live watch page through the TUM.Live connection of the Artemis course the video belongs to.
     *
     * @param courseId    the Artemis course of the lecture unit
     * @param videoSource the unit's video URL
     * @return the lookup, or empty when the connection does not cover this video and the public lookup applies
     */
    public Optional<TumLivePlaylistLookup> lookUpConnectedPlaylist(long courseId, String videoSource) {
        return gocastRecordingService.lookUpConnectedPlaylist(courseId, videoSource);
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
