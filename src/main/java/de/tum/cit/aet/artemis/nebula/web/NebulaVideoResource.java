package de.tum.cit.aet.artemis.nebula.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLecture.EnforceAtLeastStudentInLecture;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.nebula.config.NebulaEnabled;
import de.tum.cit.aet.artemis.nebula.dto.LectureVideoDTO;
import de.tum.cit.aet.artemis.nebula.service.NebulaVideoService;

/**
 * REST controller for managing lecture videos through Nebula Video Storage.
 */
@Profile(PROFILE_CORE)
@Conditional(NebulaEnabled.class)
@Lazy
@RestController
@RequestMapping("api/nebula/")
public class NebulaVideoResource {

    private static final Logger log = LoggerFactory.getLogger(NebulaVideoResource.class);

    private static final String ENTITY_NAME = "nebulaVideo";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final LectureRepository lectureRepository;

    private final NebulaVideoService nebulaVideoService;

    private final AuthorizationCheckService authCheckService;

    public NebulaVideoResource(LectureRepository lectureRepository, NebulaVideoService nebulaVideoService, AuthorizationCheckService authCheckService) {
        this.lectureRepository = lectureRepository;
        this.nebulaVideoService = nebulaVideoService;
        this.authCheckService = authCheckService;
    }

    /**
     * POST /lectures/:lectureId/video : Upload a video for a lecture.
     *
     * @param lectureId the ID of the lecture
     * @param file      the video file to upload
     * @return the ResponseEntity with status 201 (Created) and with body the video metadata
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("lectures/{lectureId}/video")
    public ResponseEntity<LectureVideoDTO> uploadLectureVideo(@PathVariable Long lectureId, @RequestParam("file") MultipartFile file) throws URISyntaxException {
        log.info("============================================");
        log.info("NEBULA VIDEO RESOURCE - UPLOAD REQUEST RECEIVED");
        log.info("Lecture ID: {}", lectureId);
        log.info("File: {}", file != null ? file.getOriginalFilename() : "NULL");
        log.info("File size: {} bytes", file != null ? file.getSize() : 0);
        log.info("File empty: {}", file != null ? file.isEmpty() : "N/A");
        log.info("============================================");

        if (file == null || file.isEmpty()) {
            log.error("Video upload failed for lecture {}: File is empty or null", lectureId);
            throw new BadRequestAlertException("Video file is required", ENTITY_NAME, "fileRequired");
        }

        Lecture lecture = lectureRepository.findByIdElseThrow(lectureId);
        Course course = lecture.getCourse();
        if (course == null) {
            log.error("Video upload failed for lecture {}: Lecture is not associated with a course", lectureId);
            throw new BadRequestAlertException("Lecture is not associated with a course", ENTITY_NAME, "courseRequired");
        }

        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);

        try {
            log.info("Calling nebulaVideoService.uploadLectureVideo()...");
            LectureVideoDTO videoDTO = nebulaVideoService.uploadLectureVideo(lecture, file);
            log.info("Video uploaded successfully for lecture {}: video ID {}", lectureId, videoDTO.videoId());

            return ResponseEntity.created(new URI("/api/nebula/lectures/" + lectureId + "/video"))
                    .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, lectureId.toString())).body(videoDTO);
        }
        catch (Exception e) {
            log.error("============================================");
            log.error("NEBULA VIDEO RESOURCE - UPLOAD FAILED");
            log.error("Exception type: {}", e.getClass().getName());
            log.error("Exception message: {}", e.getMessage());
            log.error("Full stack trace:");
            log.error("", e);
            log.error("============================================");
            throw e;
        }
    }

    /**
     * GET /lectures/:lectureId/video : Get the video metadata for a lecture.
     *
     * @param lectureId the ID of the lecture
     * @return the ResponseEntity with status 200 (OK) and with body the video metadata, or with status 404 (Not Found)
     */
    @GetMapping("lectures/{lectureId}/video")
    @EnforceAtLeastStudentInLecture
    public ResponseEntity<LectureVideoDTO> getLectureVideo(@PathVariable Long lectureId) {
        log.debug("REST request to get video for Lecture : {}", lectureId);

        Optional<LectureVideoDTO> videoDTO = nebulaVideoService.getLectureVideo(lectureId);

        return videoDTO.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * GET /lectures/:lectureId/video/stream-url : Get the HLS streaming URL for a lecture video.
     *
     * @param lectureId the ID of the lecture
     * @return the ResponseEntity with status 200 (OK) and with body the streaming URL
     */
    @GetMapping("lectures/{lectureId}/video/stream-url")
    @EnforceAtLeastStudentInLecture
    public ResponseEntity<String> getVideoStreamUrl(@PathVariable Long lectureId) {
        log.debug("REST request to get video stream URL for Lecture : {}", lectureId);

        String streamUrl = nebulaVideoService.getVideoStreamingUrl(lectureId);

        return ResponseEntity.ok(streamUrl);
    }

    /**
     * DELETE /lectures/:lectureId/video : Delete the video for a lecture.
     *
     * @param lectureId the ID of the lecture
     * @return the ResponseEntity with status 204 (No Content)
     */
    @DeleteMapping("lectures/{lectureId}/video")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Void> deleteLectureVideo(@PathVariable Long lectureId) {
        log.debug("REST request to delete video for Lecture : {}", lectureId);

        Lecture lecture = lectureRepository.findByIdElseThrow(lectureId);
        Course course = lecture.getCourse();
        if (course == null) {
            throw new BadRequestAlertException("Lecture is not associated with a course", ENTITY_NAME, "courseRequired");
        }

        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);

        nebulaVideoService.deleteLectureVideo(lectureId);

        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, lectureId.toString())).build();
    }
}
