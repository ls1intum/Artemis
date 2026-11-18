package de.tum.cit.aet.artemis.lecture.web;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLectureUnit.EnforceAtLeastStudentInLectureUnit;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentVideoUnitRepository;
import de.tum.cit.aet.artemis.videosource.domain.VideoSourceType;
import de.tum.cit.aet.artemis.videosource.service.ResolvedVideo;
import de.tum.cit.aet.artemis.videosource.service.VideoSourceResolverService;

/**
 * Hands the video player the playlist of a lecture unit's TUM Live video.
 */
@Conditional(LectureEnabled.class)
@Lazy
@FeatureUsage(UserFeature.LECTURE_VIDEO)
@RestController
@RequestMapping("api/lecture/")
public class AttachmentVideoUnitPlaylistResource {

    private final AttachmentVideoUnitRepository attachmentVideoUnitRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final VideoSourceResolverService videoSourceResolverService;

    public AttachmentVideoUnitPlaylistResource(AttachmentVideoUnitRepository attachmentVideoUnitRepository, AuthorizationCheckService authorizationCheckService,
            VideoSourceResolverService videoSourceResolverService) {
        this.attachmentVideoUnitRepository = attachmentVideoUnitRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.videoSourceResolverService = videoSourceResolverService;
    }

    /**
     * GET lecture-units/:lectureUnitId/playlist : The HLS playlist of the unit's TUM Live video. Through the course's TUM.Live connection this also covers
     * lectures of a course that is not public; the playlist is signed and valid for seven hours, so it is resolved again on every request.
     *
     * @param lectureUnitId the attachment video unit
     * @return 200 with the playlist URL, or 404 when the unit has no TUM Live video with a playlist that can be handed out now
     */
    @GetMapping("lecture-units/{lectureUnitId}/playlist")
    @EnforceAtLeastStudentInLectureUnit
    public ResponseEntity<String> getPlaylist(@PathVariable long lectureUnitId) {
        AttachmentVideoUnit unit = attachmentVideoUnitRepository.findWithLectureAndCourseAndAttachmentById(lectureUnitId)
                .orElseThrow(() -> new EntityNotFoundException("AttachmentVideoUnit", lectureUnitId));
        // The role check only covers the course; a student must not get the video of a unit that is not released yet
        if (!authorizationCheckService.isAllowedToSeeLectureUnit(unit, null)) {
            throw new AccessForbiddenException("AttachmentVideoUnit", lectureUnitId);
        }
        ResolvedVideo resolved = videoSourceResolverService.resolve(unit.getVideoSource(), unit.getLecture().getCourse().getId());
        if (resolved.type() != VideoSourceType.TUM_LIVE) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(resolved.url());
    }
}
