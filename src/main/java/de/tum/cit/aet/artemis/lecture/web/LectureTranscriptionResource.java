package de.tum.cit.aet.artemis.lecture.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.ManualConfig;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.VideoUnit;
import de.tum.cit.aet.artemis.lecture.dto.LectureTranscriptionDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;
import de.tum.cit.aet.artemis.lecture.service.LectureService;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/lecture/")
public class LectureTranscriptionResource {

    private static final String ENTITY_NAME = "lecture transcription";

    private static final Logger log = LoggerFactory.getLogger(LectureTranscriptionResource.class);

    private final LectureTranscriptionRepository lectureTranscriptionRepository;

    private final LectureUnitRepository lectureUnitRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authCheckService;

    private final LectureRepository lectureRepository;

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final LectureService lectureService;

    public LectureTranscriptionResource(LectureTranscriptionRepository transcriptionRepository, LectureUnitRepository lectureUnitRepository,
            AuthorizationCheckService authCheckService, UserRepository userRepository, LectureRepository lectureRepository, LectureService lectureService) {
        this.lectureTranscriptionRepository = transcriptionRepository;
        this.lectureUnitRepository = lectureUnitRepository;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.lectureRepository = lectureRepository;
        this.lectureService = lectureService;
    }

    /**
     * POST /transcription : Create a new transcription.
     *
     * @param lectureId        The id of the lecture
     * @param lectureUnitId    The id of the lecture unit
     * @param transcriptionDTO The transcription object to create
     * @return The ResponseEntity with status 201 (Created) and with body the new transcription, or with status 400 (Bad Request) if invalid lectureId or lectureUnitId are given
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping(value = "{lectureId}/lecture-unit/{lectureUnitId}/transcription")
    @EnforceAdmin
    @ManualConfig
    public ResponseEntity<LectureTranscription> createLectureTranscription(@Valid @RequestBody LectureTranscriptionDTO transcriptionDTO, @PathVariable Long lectureId,
            @PathVariable Long lectureUnitId) throws URISyntaxException {
        LectureUnit lectureUnit = lectureUnitRepository.findByIdElseThrow(lectureUnitId);
        if (!lectureUnit.getLecture().getId().equals(lectureId)) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "artemisApp.iris.ingestionAlert.wrongLectureError", "lectureDoesNotMatchCourse"))
                    .body(null);
        }

        var existingTranscription = lectureTranscriptionRepository.findByLectureUnit_Id(lectureUnitId);
        existingTranscription.ifPresent(lectureTranscription -> lectureTranscriptionRepository.deleteById(lectureTranscription.getId()));

        LectureTranscription lectureTranscription = new LectureTranscription(transcriptionDTO.language(), transcriptionDTO.segments(), lectureUnit);

        LectureTranscription result = lectureTranscriptionRepository.save(lectureTranscription);

        return ResponseEntity.created(new URI("/api/lecture/" + lectureId + "/transcriptions/" + result.getId())).body(result);
    }

    /**
     * POST lecture/{lectureId}/lecture-unit/{lectureUnitId}/ingest-transcription
     * This endpoint is for starting the ingestion of all lectures or only one lecture when triggered in Artemis.
     *
     * @param lectureId     The id of the lecture of the transcription
     * @param lectureUnitId The id of the lectureUnit that should be ingested
     * @return the ResponseEntity with status 200 (OK) and a message success or null if the operation failed
     */
    @Profile(PROFILE_IRIS)
    @PutMapping("{lectureId}/lecture-unit/{lectureUnitId}/ingest-transcription")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> ingestTranscriptions(@PathVariable Long lectureId, @PathVariable Long lectureUnitId) {
        Lecture lecture = lectureRepository.findByIdElseThrow(lectureId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkIsAllowedToSeeLectureElseThrow(lecture, user);
        Course course = lecture.getCourse();
        authCheckService.checkIsAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course.getId());
        LectureUnit lectureUnit = lectureUnitRepository.findById(lectureUnitId).orElseThrow();
        if (!lectureUnit.getLecture().getId().equals(lectureId)) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createAlert(applicationName, "artemisApp.iris.ingestionAlert.transcriptionIngestionError", "lectureUnitDoesNotMatchLecture")).body(null);
        }
        Optional<LectureTranscription> transcription = lectureTranscriptionRepository.findByLectureUnit_Id(lectureUnitId);
        if (transcription.isEmpty()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "artemisApp.iris.ingestionAlert.transcriptionIngestionError", "noTranscription"))
                    .body(null);
        }
        if (!(lectureUnit instanceof VideoUnit)) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createAlert(applicationName, "artemisApp.iris.ingestionAlert.transcriptionIngestionError", "lectureUnitIsNotAVideoUnit")).body(null);
        }
        LectureTranscription transcriptionToIngest = transcription.get();
        lectureService.ingestTranscriptionInPyris(transcriptionToIngest, course, lecture, (VideoUnit) lectureUnit);
        return ResponseEntity.ok().build();
    }

    /**
     * DELETE /lecture/:lectureId/lecture-unit/:lectureUnitId : delete the "id" lecture transcription.
     *
     * @param lectureId     the id of the lecture containing the lecture transcription
     * @param lectureUnitId the id of the lecture unit containing the lecture transcription
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("{lectureId}/lecture-unit/{lectureUnitId}/transcription")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> deleteLectureTranscription(@PathVariable Long lectureId, @PathVariable Long lectureUnitId) {
        LectureUnit lectureUnit = lectureUnitRepository.findById(lectureUnitId).orElseThrow();
        if (!lectureUnit.getLecture().getId().equals(lectureId)) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createAlert(applicationName, "artemisApp.iris.ingestionAlert.transcriptionIngestionError", "lectureUnitDoesNotMatchLecture")).body(null);
        }

        Course course = lectureUnit.getLecture().getCourse();
        authCheckService.checkIsAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course.getId());

        Optional<LectureTranscription> lectureTranscription = lectureTranscriptionRepository.findByLectureUnit_Id(lectureUnitId);
        if (lectureTranscription.isEmpty()) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createAlert(applicationName, "artemisApp.iris.ingestionAlert.transcriptionIngestionError", "noTranscriptionForId")).body(null);
        }
        log.debug("REST request to delete Lecture Transcription : {}", lectureTranscription.get().getId());
        lectureService.deleteLectureTranscriptionInPyris(lectureTranscription.get());
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, lectureTranscription.get().getId().toString())).build();
    }
}
