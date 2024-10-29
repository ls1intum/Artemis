package de.tum.cit.aet.artemis.lecture.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import jakarta.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyProgressService;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.VideoUnit;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.VideoUnitRepository;
import de.tum.cit.aet.artemis.lecture.service.LectureUnitService;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class VideoUnitResource {

    private static final Logger log = LoggerFactory.getLogger(VideoUnitResource.class);

    private static final String ENTITY_NAME = "videoUnit";

    private final VideoUnitRepository videoUnitRepository;

    private final LectureRepository lectureRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final CompetencyProgressService competencyProgressService;

    private final LectureUnitService lectureUnitService;

    public VideoUnitResource(LectureRepository lectureRepository, AuthorizationCheckService authorizationCheckService, VideoUnitRepository videoUnitRepository,
            CompetencyProgressService competencyProgressService, LectureUnitService lectureUnitService) {
        this.lectureRepository = lectureRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.videoUnitRepository = videoUnitRepository;
        this.competencyProgressService = competencyProgressService;
        this.lectureUnitService = lectureUnitService;
    }

    /**
     * GET lectures/:lectureId/video-units/:videoUnitId: gets the video unit with the specified id
     *
     * @param videoUnitId the id of the videoUnit to retrieve
     * @param lectureId   the id of the lecture to which the unit belongs
     * @return the ResponseEntity with status 200 (OK) and with body the video unit, or with status 404 (Not Found)
     */
    @GetMapping("lectures/{lectureId}/video-units/{videoUnitId}")
    @EnforceAtLeastEditor
    public ResponseEntity<VideoUnit> getVideoUnit(@PathVariable Long videoUnitId, @PathVariable Long lectureId) {
        log.debug("REST request to get VideoUnit : {}", videoUnitId);
        var videoUnit = videoUnitRepository.findByIdElseThrow(videoUnitId);
        checkVideoUnitCourseAndLecture(videoUnit, lectureId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, videoUnit.getLecture().getCourse(), null);
        return ResponseEntity.ok().body(videoUnit);
    }

    /**
     * PUT /lectures/:lectureId/video-units : Updates an existing video unit .
     *
     * @param lectureId the id of the lecture to which the video unit belongs to update
     * @param videoUnit the video unit to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated videoUnit
     */
    @PutMapping("lectures/{lectureId}/video-units")
    @EnforceAtLeastEditor
    public ResponseEntity<VideoUnit> updateVideoUnit(@PathVariable Long lectureId, @RequestBody VideoUnit videoUnit) {
        log.debug("REST request to update an video unit : {}", videoUnit);
        if (videoUnit.getId() == null) {
            throw new BadRequestException();
        }
        var existingVideoUnit = videoUnitRepository.findByIdWithCompetenciesElseThrow(videoUnit.getId());

        checkVideoUnitCourseAndLecture(existingVideoUnit, lectureId);
        normalizeVideoUrl(videoUnit);
        lectureUnitService.validateUrlStringAndReturnUrl(videoUnit.getSource());
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, videoUnit.getLecture().getCourse(), null);

        VideoUnit result = videoUnitRepository.save(videoUnit);

        competencyProgressService.updateProgressForUpdatedLearningObjectAsync(existingVideoUnit, Optional.of(videoUnit));

        return ResponseEntity.ok(result);
    }

    /**
     * POST /lectures/:lectureId/video-units : creates a new video unit.
     *
     * @param lectureId the id of the lecture to which the video unit should be added
     * @param videoUnit the video unit that should be created
     * @return the ResponseEntity with status 201 (Created) and with body the new video unit
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("lectures/{lectureId}/video-units")
    @EnforceAtLeastEditor
    public ResponseEntity<VideoUnit> createVideoUnit(@PathVariable Long lectureId, @RequestBody VideoUnit videoUnit) throws URISyntaxException {
        log.debug("REST request to create VideoUnit : {}", videoUnit);
        if (videoUnit.getId() != null) {
            throw new BadRequestException();
        }

        normalizeVideoUrl(videoUnit);
        lectureUnitService.validateUrlStringAndReturnUrl(videoUnit.getSource());

        Lecture lecture = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lectureId);
        if (lecture.getCourse() == null) {
            throw new BadRequestAlertException("Specified lecture is not part of a course", ENTITY_NAME, "courseMissing");
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, lecture.getCourse(), null);

        // persist lecture unit before lecture to prevent "null index column for collection" error
        videoUnit.setLecture(null);
        videoUnit = videoUnitRepository.saveAndFlush(videoUnit);
        videoUnit.setLecture(lecture);
        lecture.addLectureUnit(videoUnit);
        Lecture updatedLecture = lectureRepository.save(lecture);
        VideoUnit persistedVideoUnit = (VideoUnit) updatedLecture.getLectureUnits().getLast();

        competencyProgressService.updateProgressByLearningObjectAsync(persistedVideoUnit);

        return ResponseEntity.created(new URI("/api/video-units/" + persistedVideoUnit.getId())).body(persistedVideoUnit);
    }

    /**
     * Checks that the video unit belongs to the specified lecture.
     *
     * @param videoUnit The video unit to check
     * @param lectureId The id of the lecture to check against
     */
    private void checkVideoUnitCourseAndLecture(VideoUnit videoUnit, Long lectureId) {
        if (videoUnit.getLecture() == null || videoUnit.getLecture().getCourse() == null) {
            throw new BadRequestAlertException("Lecture unit must be associated to a lecture of a course", ENTITY_NAME, "lectureOrCourseMissing");
        }
        if (!videoUnit.getLecture().getId().equals(lectureId)) {
            throw new BadRequestAlertException("Requested lecture unit is not part of the specified lecture", ENTITY_NAME, "lectureIdMismatch");
        }
    }

    /**
     * Normalizes the provided video Url.
     *
     * @param videoUnit provided video unit
     */
    private void normalizeVideoUrl(VideoUnit videoUnit) {
        // Remove leading and trailing whitespaces
        if (videoUnit.getSource() != null) {
            videoUnit.setSource(videoUnit.getSource().strip());
        }
    }
}
