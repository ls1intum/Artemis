package de.tum.cit.aet.artemis.lecture.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.dto.LectureTranscriptionDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class LectureTranscriptionResource {

    private static final Logger log = LoggerFactory.getLogger(LectureTranscriptionResource.class);

    private final LectureTranscriptionRepository lectureTranscriptionRepository;

    private final LectureUnitRepository lectureUnitRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authCheckService;

    public LectureTranscriptionResource(LectureTranscriptionRepository transcriptionRepository, LectureUnitRepository lectureUnitRepository,
            AuthorizationCheckService authCheckService, UserRepository userRepository) {
        this.lectureTranscriptionRepository = transcriptionRepository;
        this.lectureUnitRepository = lectureUnitRepository;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
    }

    /**
     * POST /transcription : Create a new transcription.
     *
     * @param lectureId        The id of the lecture
     * @param lectureUnitId    The id of the lecture unit
     * @param transcriptionDTO The transcription object to create
     * @return The ResponseEntity with status 201 (Created) and with body the new transcription, or with status 400 (Bad Request) if the transcription has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping(value = "lecture/{lectureId}/lecture-unit/{lectureUnitId}/transcriptions")
    @EnforceAtLeastInstructor
    public ResponseEntity<LectureTranscription> createLectureTranscription(@Valid @RequestBody LectureTranscriptionDTO transcriptionDTO, @PathVariable Long lectureId,
            @PathVariable Long lectureUnitId) throws URISyntaxException {
        LectureUnit lectureUnit = lectureUnitRepository.findByIdElseThrow(lectureUnitId);
        if (!lectureUnit.getLecture().getId().equals(lectureId)) {
            throw new BadRequestAlertException("Lecture unit does not belong to the specified lecture", "lectureTranscription", "lectureUnitDoesNotMatchLecture");
        }

        if (lectureUnit.getLectureTranscription() != null) {
            lectureTranscriptionRepository.deleteById(lectureUnit.getLectureTranscription().getId());
            lectureUnit.setLectureTranscription(null);
        }

        LectureTranscription lectureTranscription = new LectureTranscription(transcriptionDTO.language(), transcriptionDTO.segments(), lectureUnit);
        lectureTranscription.setId(null);

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
    @GetMapping("lectures/{lectureId}/transcription/{transcriptionId}")
    @EnforceAtLeastStudent
    public ResponseEntity<LectureTranscription> getLectureTranscriptions(@PathVariable Long lectureId, @PathVariable Long transcriptionId) {
        log.debug("REST request to get transcription {}", transcriptionId);
        LectureTranscription transcription = lectureTranscriptionRepository.findById(transcriptionId).orElseThrow();
        authCheckService.checkIsAllowedToSeeLectureElseThrow(transcription.getLectureUnit().getLecture(), userRepository.getUserWithGroupsAndAuthorities());

        return ResponseEntity.ok(transcription);
    }
}
