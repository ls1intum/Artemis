package de.tum.in.www1.artemis.lecture.web.rest;

import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.VideoUnit;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.VideoUnitRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Optional;

import static de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException.NOT_ALLOWED;

@RestController
@RequestMapping("api/")
public class VideoUnitResource {

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final Logger log = LoggerFactory.getLogger(VideoUnitResource.class);

    private static final String ENTITY_NAME = "videoUnit";

    private final VideoUnitRepository videoUnitRepository;

    private final LectureRepository lectureRepository;

    private final AuthorizationCheckService authorizationCheckService;

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
        Optional<VideoUnit> optionalVideoUnit = videoUnitRepository.findById(videoUnitId);
        if (optionalVideoUnit.isEmpty()) {
            throw new EntityNotFoundException("There isn't such video unit");

        }
        VideoUnit videoUnit = optionalVideoUnit.get();
        if (videoUnit.getLecture() == null || videoUnit.getLecture().getCourse() == null) {
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }
        if (!videoUnit.getLecture().getId().equals(lectureId)) {
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, videoUnit.getLecture().getCourse(), null);
        return ResponseEntity.ok().body(videoUnit);
    }

    /**
     * PUT lectures/:lectureId/video-units : Updates an existing video unit .
     *
     * @param lectureId      the id of the lecture to which the video unit belongs to update
     * @param videoUnit the video unit to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated videoUnit
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("lectures/{lectureId}/video-units")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<VideoUnit> updateVideoUnit(@PathVariable Long lectureId, @RequestBody VideoUnit videoUnit) throws URISyntaxException {
        log.debug("REST request to update an video unit : {}", videoUnit);
        if (videoUnit.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }

        if (videoUnit.getLecture() == null || videoUnit.getLecture().getCourse() == null) {
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }

        // Validate the URL
        try {
            new URL(videoUnit.getSource());
        }
        catch (MalformedURLException exception) {
            throw new BadRequestAlertException("The URL is not valid", ENTITY_NAME, "invalidurl");
        }

        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, videoUnit.getLecture().getCourse(), null);

        if (!videoUnit.getLecture().getId().equals(lectureId)) {
            return new ResponseEntity<>(HttpStatus.CONFLICT);
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
    @PostMapping("lectures/{lectureId}/video-units")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<VideoUnit> createVideoUnit(@PathVariable Long lectureId, @RequestBody VideoUnit videoUnit) throws URISyntaxException {
        log.debug("REST request to create VideoUnit : {}", videoUnit);
        if (videoUnit.getId() != null) {
            throw new BadRequestAlertException("A new text unit cannot have an id", ENTITY_NAME, "idexists");
        }

        // Validate the URL
        try {
            new URL(videoUnit.getSource());
        }
        catch (MalformedURLException exception) {
            throw new BadRequestAlertException("The URL is not valid", ENTITY_NAME, "invalidurl");
        }

        Optional<Lecture> lectureOptional = lectureRepository.findByIdWithPostsAndLectureUnitsAndLearningGoals(lectureId);
        if (lectureOptional.isEmpty()) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        Lecture lecture = lectureOptional.get();
        if (lecture.getCourse() == null) {
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, lecture.getCourse(), null);

        // persist lecture unit before lecture to prevent "null index column for collection" error
        videoUnit.setLecture(null);
        VideoUnit savedVideoUnit = videoUnitRepository.saveAndFlush(videoUnit);
        savedVideoUnit.setLecture(lecture);
        lecture.addLectureUnit(savedVideoUnit);
        Lecture updatedLecture = lectureRepository.save(lecture);
        VideoUnit persistedVideoUnit = (VideoUnit) updatedLecture.getLectureUnits().get(updatedLecture.getLectureUnits().size() - 1);

        return ResponseEntity.created(new URI("/api/video-units/" + persistedVideoUnit.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, "")).body(persistedVideoUnit);

    }

}
