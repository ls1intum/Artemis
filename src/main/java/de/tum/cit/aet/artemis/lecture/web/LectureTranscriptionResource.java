package de.tum.cit.aet.artemis.lecture.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
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
    public ResponseEntity<LectureTranscription> createLectureTranscription(@Valid @RequestBody LectureTranscriptionDTO transcriptionDTO, @PathVariable Long lectureId,
            @PathVariable Long lectureUnitId) throws URISyntaxException {
        LectureUnit lectureUnit = lectureUnitRepository.findByIdElseThrow(lectureUnitId);
        if (!lectureUnit.getLecture().getId().equals(lectureId)) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "artemisApp.iris.ingestionAlert.wrongLectureError", "lectureDoesNotMatchCourse"))
                    .body(null);
        }

        if (lectureUnit.getLectureTranscription() != null) {
            lectureTranscriptionRepository.deleteById(lectureUnit.getLectureTranscription().getId());
            lectureUnit.setLectureTranscription(null);
        }

        LectureTranscription lectureTranscription = new LectureTranscription(transcriptionDTO.language(), transcriptionDTO.segments(), lectureUnit);

        LectureTranscription result = lectureTranscriptionRepository.save(lectureTranscription);

        return ResponseEntity.created(new URI("/api/lecture/" + lectureId + "/transcriptions/" + result.getId())).body(result);
    }

    /**
     * GET /lectures/:lectureId/transcriptions/:transcriptionId : get the transcription for the transcriptionId.
     *
     * @param lectureId       the lectureId of the lecture from the transcription
     * @param transcriptionId the transcriptionId of the transcription to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the transcription, or with status 404 (Not Found)
     */
    @GetMapping("{lectureId}/transcription/{transcriptionId}")
    @EnforceAtLeastStudent
    public ResponseEntity<LectureTranscription> getLectureTranscriptions(@PathVariable Long lectureId, @PathVariable Long transcriptionId) {
        log.debug("REST request to get transcription {}", transcriptionId);
        LectureTranscription transcription = lectureTranscriptionRepository.findById(transcriptionId).orElseThrow();
        authCheckService.checkIsAllowedToSeeLectureElseThrow(transcription.getLectureUnit().getLecture(), userRepository.getUserWithGroupsAndAuthorities());

        return ResponseEntity.ok(transcription);
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
