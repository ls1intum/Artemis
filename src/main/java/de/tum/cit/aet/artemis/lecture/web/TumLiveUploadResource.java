package de.tum.cit.aet.artemis.lecture.web;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.FeatureInteraction;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLectureUnit.EnforceAtLeastEditorInLectureUnit;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UsageInteraction;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.dto.TumLiveUploadStartDTO;
import de.tum.cit.aet.artemis.lecture.dto.TumLiveUploadStateDTO;
import de.tum.cit.aet.artemis.lecture.dto.TumLiveUploadedUnitDTO;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentVideoUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.TumLiveUnitUploadRepository;
import de.tum.cit.aet.artemis.lecture.service.TumLiveUploadService;

/**
 * Uploading the video of an attachment video unit to the course's TUM.Live course.
 */
@Conditional(LectureEnabled.class)
@Lazy
@FeatureUsage(UserFeature.LECTURE_VIDEO)
@RestController
@RequestMapping("api/lecture/")
public class TumLiveUploadResource {

    private final AttachmentVideoUnitRepository attachmentVideoUnitRepository;

    private final TumLiveUploadService tumLiveUploadService;

    private final TumLiveUnitUploadRepository tumLiveUnitUploadRepository;

    public TumLiveUploadResource(AttachmentVideoUnitRepository attachmentVideoUnitRepository, TumLiveUploadService tumLiveUploadService,
            TumLiveUnitUploadRepository tumLiveUnitUploadRepository) {
        this.attachmentVideoUnitRepository = attachmentVideoUnitRepository;
        this.tumLiveUploadService = tumLiveUploadService;
        this.tumLiveUnitUploadRepository = tumLiveUnitUploadRepository;
    }

    /**
     * POST lecture-units/:lectureUnitId/tumlive-upload : Starts an upload, or hands out a new URL for the pending one whose transfer has not started.
     *
     * @param lectureUnitId the attachment video unit
     * @return the upload's reference and the one-use URL on TUM.Live to send the file to; 409 if the course is not connected or the pending upload is
     *         already being transferred
     */
    @PostMapping("lecture-units/{lectureUnitId}/tumlive-upload")
    @EnforceAtLeastEditorInLectureUnit
    public ResponseEntity<TumLiveUploadStartDTO> startUpload(@PathVariable long lectureUnitId) {
        return ResponseEntity.ok(tumLiveUploadService.start(findUnit(lectureUnitId)));
    }

    /**
     * GET lecture-units/:lectureUnitId/tumlive-upload : The unit's TUM.Live upload state.
     *
     * @param lectureUnitId the attachment video unit
     * @return the state
     */
    @GetMapping("lecture-units/{lectureUnitId}/tumlive-upload")
    @EnforceAtLeastEditorInLectureUnit
    @UsageInteraction(FeatureInteraction.AUTOMATIC)
    public ResponseEntity<TumLiveUploadStateDTO> getUploadState(@PathVariable long lectureUnitId) {
        return ResponseEntity.ok(tumLiveUploadService.getState(findUnit(lectureUnitId)));
    }

    /**
     * DELETE lecture-units/:lectureUnitId/tumlive-upload?ref= : Gives up the pending upload with this reference, for example after its transfer failed. Does
     * nothing if it is no longer pending; never touches the video the unit shows.
     *
     * @param lectureUnitId the attachment video unit
     * @param ref           the upload's reference
     * @return 204, also if the upload was no longer pending
     */
    @DeleteMapping("lecture-units/{lectureUnitId}/tumlive-upload")
    @EnforceAtLeastEditorInLectureUnit
    public ResponseEntity<Void> discardUpload(@PathVariable long lectureUnitId, @RequestParam String ref) {
        tumLiveUploadService.discard(findUnit(lectureUnitId), ref);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET courses/:courseId/tumlive-uploads : The units of a course that show a video Artemis uploaded to TUM.Live, so deleting them asks about the recording.
     *
     * @param courseId the course
     * @return the units and their lectures
     */
    @GetMapping("courses/{courseId}/tumlive-uploads")
    @EnforceAtLeastEditorInCourse
    @UsageInteraction(FeatureInteraction.AUTOMATIC)
    public ResponseEntity<List<TumLiveUploadedUnitDTO>> getUploadedUnits(@PathVariable long courseId) {
        return ResponseEntity.ok(tumLiveUnitUploadRepository.findUploadedUnitsByCourseId(courseId));
    }

    private AttachmentVideoUnit findUnit(long lectureUnitId) {
        return attachmentVideoUnitRepository.findWithLectureAndCourseAndAttachmentById(lectureUnitId)
                .orElseThrow(() -> new EntityNotFoundException("AttachmentVideoUnit", lectureUnitId));
    }
}
