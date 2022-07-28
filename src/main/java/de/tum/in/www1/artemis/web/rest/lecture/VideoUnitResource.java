package de.tum.in.www1.artemis.web.rest.lecture;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import javax.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.VideoUnit;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.VideoUnitRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

@RestController
@RequestMapping("/api")
public class VideoUnitResource {

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final Logger log = LoggerFactory.getLogger(VideoUnitResource.class);

    private static final String ENTITY_NAME = "videoUnit";

    private final VideoUnitRepository videoUnitRepository;

    private final LectureRepository lectureRepository;

    private final AuthorizationCheckService authorizationCheckService;

    /**
     * Normalizes the provided video Url.
     * @param videoUnit provided video unit
     */
    private void normalizeVideoUrl(VideoUnit videoUnit) {
        // Remove leading and trailing whitespaces
        if (videoUnit.getSource() != null) {
            videoUnit.setSource(videoUnit.getSource().strip());
        }
    }

    /**
     * Validates the provided video Url.
     * @param videoUnit provided video unit
     */
    private void validateVideoUrl(VideoUnit videoUnit) {
        try {
            new URL(videoUnit.getSource());
        }
        catch (MalformedURLException exception) {
            throw new BadRequestException();
        }
    }

    public VideoUnitResource(LectureRepository lectureRepository, AuthorizationCheckService authorizationCheckService, VideoUnitRepository videoUnitRepository) {
        this.lectureRepository = lectureRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.videoUnitRepository = videoUnitRepository;
    }

    /**
     * GET lectures/:lectureId/video-units/:videoUnitId: gets the video unit with the specified id
     *
     * @param videoUnitId the id of the videoUnit to retrieve
     * @param lectureId the id of the lecture to which the unit belongs
     * @return the ResponseEntity with status 200 (OK) and with body the video unit, or with status 404 (Not Found)
     */
    @GetMapping("lectures/{lectureId}/video-units/{videoUnitId}")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<VideoUnit> getVideoUnit(@PathVariable Long videoUnitId, @PathVariable Long lectureId) {
        log.debug("REST request to get VideoUnit : {}", videoUnitId);
        var videoUnit = videoUnitRepository.findByIdElseThrow(videoUnitId);
        if (videoUnit.getLecture() == null || videoUnit.getLecture().getCourse() == null) {
            throw new ConflictException("Lecture unit must be associated to a lecture of a course", "VideoUnit", "lectureOrCourseMissing");
        }
        if (!videoUnit.getLecture().getId().equals(lectureId)) {
            throw new ConflictException("Requested lecture unit is not part of the specified lecture", "VideoUnit", "lectureIdMismatch");
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, videoUnit.getLecture().getCourse(), null);
        return ResponseEntity.ok().body(videoUnit);
    }

    /**
     * PUT /lectures/:lectureId/video-units : Updates an existing video unit .
     *
     * @param lectureId      the id of the lecture to which the video unit belongs to update
     * @param videoUnit the video unit to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated videoUnit
     */
    @PutMapping("/lectures/{lectureId}/video-units")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<VideoUnit> updateVideoUnit(@PathVariable Long lectureId, @RequestBody VideoUnit videoUnit) {
        log.debug("REST request to update an video unit : {}", videoUnit);
        if (videoUnit.getId() == null) {
            throw new BadRequestException();
        }

        if (videoUnit.getLecture() == null || videoUnit.getLecture().getCourse() == null) {
            throw new ConflictException("Lecture unit must be associated to a lecture of a course", "VideoUnit", "lectureOrCourseMissing");
        }

        normalizeVideoUrl(videoUnit);
        validateVideoUrl(videoUnit);

        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, videoUnit.getLecture().getCourse(), null);

        if (!videoUnit.getLecture().getId().equals(lectureId)) {
            throw new ConflictException("Requested lecture unit is not part of the specified lecture", "VideoUnit", "lectureIdMismatch");
        }

        VideoUnit result = videoUnitRepository.save(videoUnit);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, videoUnit.getId().toString())).body(result);
    }

    /**
     * POST /lectures/:lectureId/video-units : creates a new video unit.
     *
     * @param lectureId      the id of the lecture to which the video unit should be added
     * @param videoUnit the video unit that should be created
     * @return the ResponseEntity with status 201 (Created) and with body the new video unit
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/lectures/{lectureId}/video-units")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<VideoUnit> createVideoUnit(@PathVariable Long lectureId, @RequestBody VideoUnit videoUnit) throws URISyntaxException {
        log.debug("REST request to create VideoUnit : {}", videoUnit);
        if (videoUnit.getId() != null) {
            throw new BadRequestException();
        }

        normalizeVideoUrl(videoUnit);
        validateVideoUrl(videoUnit);

        Lecture lecture = lectureRepository.findByIdWithLectureUnitsElseThrow(lectureId);
        if (lecture.getCourse() == null) {
            throw new ConflictException("Specified lecture is not part of a course", "VideoUnit", "courseMissing");
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, lecture.getCourse(), null);

        // persist lecture unit before lecture to prevent "null index column for collection" error
        videoUnit.setLecture(null);
        videoUnit = videoUnitRepository.saveAndFlush(videoUnit);
        videoUnit.setLecture(lecture);
        lecture.addLectureUnit(videoUnit);
        Lecture updatedLecture = lectureRepository.save(lecture);
        VideoUnit persistedVideoUnit = (VideoUnit) updatedLecture.getLectureUnits().get(updatedLecture.getLectureUnits().size() - 1);

        return ResponseEntity.created(new URI("/api/video-units/" + persistedVideoUnit.getId())).body(persistedVideoUnit);
    }

}
