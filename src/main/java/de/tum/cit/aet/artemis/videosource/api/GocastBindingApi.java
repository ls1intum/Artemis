package de.tum.cit.aet.artemis.videosource.api;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.videosource.config.GocastEnabled;
import de.tum.cit.aet.artemis.videosource.dto.GocastRecordingDTO;
import de.tum.cit.aet.artemis.videosource.dto.GocastUploadConnectionDTO;
import de.tum.cit.aet.artemis.videosource.dto.GocastUploadTargetDTO;
import de.tum.cit.aet.artemis.videosource.service.GocastBindingService;
import de.tum.cit.aet.artemis.videosource.service.GocastIntegrationException;
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
     * The active TUM.Live connection of a course, as uploads need it.
     *
     * @param courseId the Artemis course
     * @return the connection, or empty if the course is not connected
     */
    public Optional<GocastUploadConnectionDTO> findUploadConnection(long courseId) {
        return gocastRecordingService.findUploadConnection(courseId);
    }

    /**
     * Creates the TUM.Live lecture for one upload, or renews the upload URL of a reference whose upload has not started.
     *
     * @param grantId     the grant of the course
     * @param externalRef the reference chosen for this upload
     * @param title       the lecture's title in TUM.Live
     * @return the lecture and the absolute one-use upload URL
     * @throws GocastIntegrationException with TUM.Live's status if it refuses or cannot be reached
     */
    public GocastUploadTargetDTO createUpload(long grantId, String externalRef, String title) {
        return gocastRecordingService.createUpload(grantId, externalRef, title);
    }

    /**
     * The state of an uploaded lecture's recording.
     *
     * @param grantId  the grant the upload was made through
     * @param streamId the TUM.Live lecture
     * @return its state
     * @throws GocastIntegrationException with TUM.Live's status if it refuses or cannot be reached
     */
    public GocastRecordingDTO getRecording(long grantId, long streamId) {
        return gocastRecordingService.getRecording(grantId, streamId);
    }

    /**
     * Deletes an uploaded lecture in TUM.Live and closes its reference; repeating it succeeds.
     *
     * @param grantId     the grant the upload was made through
     * @param externalRef the upload's reference
     * @throws GocastIntegrationException with TUM.Live's status if it refuses or cannot be reached
     */
    public void deleteUpload(long grantId, String externalRef) {
        gocastRecordingService.deleteUpload(grantId, externalRef);
    }

    /**
     * The TUM.Live watch page of a lecture, as a unit stores it.
     *
     * @param courseSlug the TUM.Live course slug
     * @param streamId   the TUM.Live lecture
     * @return the watch page URL
     */
    public String watchUrl(String courseSlug, long streamId) {
        return gocastRecordingService.watchUrl(courseSlug, streamId);
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
